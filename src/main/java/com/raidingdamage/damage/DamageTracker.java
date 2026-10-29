package com.raidingdamage.damage;

import com.google.common.base.Strings;
import com.raidingdamage.Format;
import com.raidingdamage.RaidingDamageConfig;
import com.raidingdamage.raid.RaidRoom;
import com.raidingdamage.raid.RaidType;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;

/**
 * Tracks damage dealt to NPCs, per room, while in a raid. All state is owned by the client thread;
 * the side panel only ever receives copies.
 */
@Slf4j
@Singleton
public class DamageTracker
{
	// Ticks outside a raid before it's considered over; covers teleports and loading screens
	private static final int END_GRACE_TICKS = 5;
	// Delay room summaries slightly so party members' final hits arrive first
	private static final int SUMMARY_DELAY_TICKS = 2;
	// ToB "Wave '...' complete! Duration: ...", ToA/TFA "Challenge complete: ...", CoX "... complete! Duration: ..."
	private static final Pattern ROOM_COMPLETE = Pattern.compile(
		"^challenge complete\\b|\\bcomplete\\b.*\\bduration\\b", Pattern.CASE_INSENSITIVE);
	// Kill count on completion, e.g. "Your completed Theatre of Blood: Hard Mode count is: 12."
	private static final Pattern RAID_COMPLETE = Pattern.compile(
		"your completed (theatre of blood|tombs of amascut|chambers of xeric|the fractured archive|fractured archive)\\b"
			+ "|your raid is complete", Pattern.CASE_INSENSITIVE);
	private static final Color TOP_COLOR = new Color(0xFFD24A);

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private RaidingDamageConfig config;
	@Inject
	private PartyService partyService;
	@Inject
	private WSClient wsClient;
	@Inject
	private InfoBoxManager infoBoxManager;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ChatMessageManager chatMessageManager;
	@Inject
	private DamageHistory history;

	private Plugin plugin;
	private Consumer<RaidRecord> liveListener;

	private RaidRecord live;
	// ToB/ToA room the player is standing in
	private RaidRoom regionRoom;
	// Room most recently damaged; what the infoboxes show
	private RaidRoom activeRoom;
	private int ticksOutsideRaid;
	private boolean completed;
	// Room the local player last died in
	private RaidRoom deathRoom;
	private boolean dirty;
	// Other members of the raid party right now
	private final Set<String> team = new LinkedHashSet<>();
	private final Set<RaidRoom> unsentRooms = EnumSet.noneOf(RaidRoom.class);
	private final Map<RaidRoom, Integer> pendingSummaries = new EnumMap<>(RaidRoom.class);
	private final Map<DamageInfoBox.Kind, DamageInfoBox> infoBoxes = new EnumMap<>(DamageInfoBox.Kind.class);

	public void startUp(Plugin plugin, Consumer<RaidRecord> liveListener)
	{
		this.plugin = plugin;
		this.liveListener = liveListener;
		reset();
		wsClient.registerMessage(RaidDamageUpdate.class);
	}

	public void shutDown()
	{
		wsClient.unregisterMessage(RaidDamageUpdate.class);
		clientThread.invoke(() ->
		{
			if (live != null)
			{
				endRaid(false);
			}
			removeInfoBoxes();
			reset();
		});
	}

	private void reset()
	{
		live = null;
		regionRoom = null;
		activeRoom = null;
		ticksOutsideRaid = 0;
		completed = false;
		deathRoom = null;
		dirty = false;
		team.clear();
		unsentRooms.clear();
		pendingSummaries.clear();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		RaidType raid = currentRaid();
		if (raid != null)
		{
			ticksOutsideRaid = 0;
			if (live == null || live.getRaid() != raid)
			{
				if (live != null)
				{
					endRaid(true);
				}
				startRaid(raid);
			}

			updateTeam();

			if (raid != RaidType.COX)
			{
				RaidRoom room = roomAt(client.getLocalPlayer().getLocalLocation());
				if (room != regionRoom)
				{
					if (regionRoom != null)
					{
						completeRoom(regionRoom);
					}
					regionRoom = room;
				}
			}
		}
		else if (live != null && ++ticksOutsideRaid >= END_GRACE_TICKS)
		{
			endRaid(true);
		}

		if (live != null)
		{
			sendDueSummaries(false);
			broadcast();
		}

		if (dirty)
		{
			dirty = false;
			publish();
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (live == null)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		boolean mine = hitsplat.isMine();
		if (!mine && !hitsplat.isOthers())
		{
			// Heals, poison, venom etc. can't be attributed to a player
			return;
		}

		Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		String npcName = actor.getName();
		if (npcName == null)
		{
			return;
		}

		RaidRoom room = live.getRaid() == RaidType.COX ? RaidRoom.forCoxNpc(npcName) : roomAt(actor.getLocalLocation());
		if (room == null || room.getRaid() != live.getRaid())
		{
			room = regionRoom;
		}
		if (room == null)
		{
			return;
		}

		if (activeRoom == null)
		{
			activeRoom = room;
		}
		else if (room != activeRoom && room != RaidRoom.COX_OTHER)
		{
			if (live.getRaid() == RaidType.COX)
			{
				// CoX rooms have no region boundaries; moving on to another room's NPCs ends the previous one
				completeRoom(activeRoom);
			}
			activeRoom = room;
		}

		boolean boss = room.isBoss(((NPC) actor).getId(), npcName);
		int amount = hitsplat.getAmount();
		RoomRecord record = live.getOrCreateRoom(room);
		record.recordHit(amount, boss, client.getTickCount());
		record.getTeam().addAll(team);
		if (mine)
		{
			String name = localName();
			if (name != null)
			{
				record.addPlayerDamage(name, amount, boss);
				unsentRooms.add(room);
			}
		}
		dirty = true;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (live == null)
		{
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		if (RAID_COMPLETE.matcher(message).find())
		{
			completed = true;
		}
		if (!ROOM_COMPLETE.matcher(message).find())
		{
			return;
		}

		RaidRoom room = currentRoom();
		if (room != null)
		{
			// Backup for the kill count message: finishing the last boss finishes the raid
			completed |= room.isFinalRoom();
			completeRoom(room);
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (live != null && event.getActor() == client.getLocalPlayer())
		{
			deathRoom = currentRoom();
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		// CoX has no party name list, but only the raid party can be in the instance
		if (live != null && live.getRaid() == RaidType.COX)
		{
			addTeammate(event.getPlayer());
		}
	}

	private void updateTeam()
	{
		switch (live.getRaid())
		{
			case TOB:
				readPartyNames(VarClientID.TOB_CLIENT_NAME0, VarClientID.TOB_CLIENT_NAME4);
				break;
			case TOA:
				readPartyNames(VarClientID.TOA_CLIENT_NAME0, VarClientID.TOA_CLIENT_NAME7);
				break;
			case TFA:
				readPartyNames(VarClientID.TFA_CLIENT_NAME0, VarClientID.TFA_CLIENT_NAME7);
				break;
			default:
				break;
		}
	}

	private void readPartyNames(int first, int last)
	{
		// The party list drops players who leave, so later rooms don't count them
		Set<String> current = new LinkedHashSet<>();
		for (int id = first; id <= last; id++)
		{
			String name = teammateName(client.getVarcStrValue(id));
			if (name != null)
			{
				current.add(name);
			}
		}
		if (!current.equals(team))
		{
			team.clear();
			team.addAll(current);
			// The raid keeps everyone who took part
			live.getTeam().addAll(current);
			dirty = true;
		}
	}

	private void addTeammate(Player player)
	{
		if (player == null || player == client.getLocalPlayer())
		{
			return;
		}
		String name = teammateName(player.getName());
		if (name != null && team.add(name))
		{
			live.getTeam().add(name);
			dirty = true;
		}
	}

	private String teammateName(String rawName)
	{
		if (Strings.isNullOrEmpty(rawName))
		{
			return null;
		}
		String name = Text.sanitize(rawName);
		return name.isEmpty() || name.equals(live.getLocalName()) ? null : name;
	}

	private RaidRoom currentRoom()
	{
		return live.getRaid() == RaidType.COX ? activeRoom : regionRoom;
	}

	@Subscribe
	public void onRaidDamageUpdate(RaidDamageUpdate update)
	{
		// Party messages arrive on the websocket thread
		clientThread.invokeLater(() -> applyPartyUpdate(update));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (RaidingDamageConfig.GROUP.equals(event.getGroup()))
		{
			clientThread.invokeLater(() ->
			{
				syncInfoBoxes();
				dirty = true;
			});
		}
	}

	private void applyPartyUpdate(RaidDamageUpdate update)
	{
		if (live == null)
		{
			return;
		}

		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == update.getMemberId())
		{
			return;
		}

		PartyMember member = partyService.getMemberById(update.getMemberId());
		RaidRoom room = RaidRoom.fromKey(update.getRoom());
		if (member == null || Strings.isNullOrEmpty(member.getDisplayName()) || room == null || room.getRaid() != live.getRaid())
		{
			return;
		}

		String name = Text.sanitize(member.getDisplayName());
		if (name.equals(live.getLocalName()))
		{
			return;
		}

		live.getOrCreateRoom(room).setPlayerDamage(name, update.getDamage(), update.getBossDamage());
		dirty = true;
	}

	private RaidType currentRaid()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}

		RaidType raid;
		if (client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1)
		{
			raid = RaidType.COX;
		}
		else
		{
			raid = RaidType.forRegion(WorldPoint.fromLocalInstance(client, player.getLocalLocation()).getRegionID());
		}
		if (raid == null && isVisible(InterfaceID.TfaHud.UNIVERSE))
		{
			// Between TFA rooms the region isn't a known room, but the raid HUD is still up
			raid = RaidType.TFA;
		}
		return raid != null && raid.isTracked(config) ? raid : null;
	}

	private boolean isVisible(int componentId)
	{
		Widget widget = client.getWidget(componentId);
		return widget != null && !widget.isHidden();
	}

	private RaidRoom roomAt(LocalPoint location)
	{
		if (location == null)
		{
			return null;
		}
		return RaidRoom.forRegion(WorldPoint.fromLocalInstance(client, location).getRegionID());
	}

	private String localName()
	{
		if (live.getLocalName() == null && client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			// Sanitized so it matches party list and RuneLite party names, which may differ in spacing
			live.setLocalName(Text.sanitize(client.getLocalPlayer().getName()));
		}
		return live.getLocalName();
	}

	private void startRaid(RaidType raid)
	{
		reset();
		live = new RaidRecord(raid, System.currentTimeMillis());
		localName();
		if (raid == RaidType.COX)
		{
			// Players already loaded when the raid starts won't get a spawn event
			for (Player player : client.getTopLevelWorldView().players())
			{
				addTeammate(player);
			}
		}
		log.debug("Started tracking {}", raid);
		dirty = true;
	}

	private void endRaid(boolean announce)
	{
		RaidRoom endRoom = currentRoom();
		if (endRoom == null && !live.getRooms().isEmpty())
		{
			endRoom = live.getRooms().get(live.getRooms().size() - 1).getRoom();
		}
		live.setEndRoom(endRoom);
		if (announce)
		{
			// Without announce the plugin is shutting down mid-raid, so the outcome isn't known
			live.setOutcome(completed ? RaidOutcome.COMPLETED
				: deathRoom != null && deathRoom == endRoom ? RaidOutcome.WIPED
				: RaidOutcome.LEFT);
		}

		for (RoomRecord room : live.getRooms())
		{
			if (!room.isComplete())
			{
				room.setComplete(true);
				if (announce && config.roomChatSummary())
				{
					pendingSummaries.put(room.getRoom(), 0);
				}
			}
		}
		if (announce)
		{
			sendDueSummaries(true);
			if (config.raidChatSummary())
			{
				sendRaidSummary();
			}
		}

		live.setEndTime(System.currentTimeMillis());
		if (!live.getRooms().isEmpty())
		{
			history.add(live.copy(), config.historySize());
		}
		log.debug("Finished tracking {} ({} rooms)", live.getRaid(), live.getRooms().size());

		reset();
		removeInfoBoxes();
		dirty = true;
	}

	private void completeRoom(RaidRoom room)
	{
		RoomRecord record = live == null ? null : live.findRoom(room);
		if (record == null || record.isComplete())
		{
			return;
		}

		record.setComplete(true);
		if (config.roomChatSummary())
		{
			pendingSummaries.put(room, client.getTickCount() + SUMMARY_DELAY_TICKS);
		}
		dirty = true;
	}

	private void broadcast()
	{
		if (unsentRooms.isEmpty())
		{
			return;
		}

		if (config.shareWithParty() && partyService.isInParty() && live.getLocalName() != null)
		{
			for (RaidRoom room : unsentRooms)
			{
				RoomRecord record = live.findRoom(room);
				PlayerDamage mine = record == null ? null : record.getPlayers().get(live.getLocalName());
				if (mine != null)
				{
					partyService.send(new RaidDamageUpdate(room.name(), mine.getDamage(), mine.getBossDamage()));
				}
			}
		}
		unsentRooms.clear();
	}

	private void sendDueSummaries(boolean all)
	{
		int tick = client.getTickCount();
		Iterator<Map.Entry<RaidRoom, Integer>> it = pendingSummaries.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<RaidRoom, Integer> entry = it.next();
			if (all || entry.getValue() <= tick)
			{
				it.remove();
				RoomRecord record = live.findRoom(entry.getKey());
				if (record != null)
				{
					sendRoomSummary(record);
				}
			}
		}
	}

	private void sendRoomSummary(RoomRecord room)
	{
		DamageBreakdown breakdown = DamageBreakdown.ofRoom(room, live.getLocalName(), config.bossDamageOnly());
		DamageBreakdown.Entry me = breakdown.getLocal();
		if (me == null || breakdown.getTotal() == 0)
		{
			return;
		}

		ChatMessageBuilder message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(room.getRoom().getDisplayName())
			.append(ChatColorType.NORMAL)
			.append(" - you dealt ")
			.append(ChatColorType.HIGHLIGHT)
			.append(Format.number(me.getDamage()) + " (" + Format.percent(me.getShare()) + ")")
			.append(ChatColorType.NORMAL)
			.append(" of " + Format.number(breakdown.getTotal()) + " in " + Format.ticks(room.getTicks()));
		appendStanding(message, breakdown);
		queueChat(message);
	}

	private void sendRaidSummary()
	{
		DamageBreakdown breakdown = DamageBreakdown.ofRaid(live, config.bossDamageOnly());
		DamageBreakdown.Entry me = breakdown.getLocal();
		if (me == null || breakdown.getTotal() == 0)
		{
			return;
		}

		String ending = "";
		if (live.getOutcome() != null && live.getOutcome() != RaidOutcome.COMPLETED)
		{
			ending = " (" + live.getOutcome().getDisplayName().toLowerCase()
				+ (live.getEndRoom() == null ? "" : " at " + live.getEndRoom().getDisplayName()) + ")";
		}

		ChatMessageBuilder message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(live.getRaid().getDisplayName() + ending)
			.append(ChatColorType.NORMAL)
			.append(" - you dealt ")
			.append(ChatColorType.HIGHLIGHT)
			.append(Format.number(me.getDamage()) + " (" + Format.percent(me.getShare()) + ")")
			.append(ChatColorType.NORMAL)
			.append(" of " + Format.number(breakdown.getTotal()) + " across " + live.getRooms().size() + " rooms");
		appendStanding(message, breakdown);
		queueChat(message);
	}

	private static void appendStanding(ChatMessageBuilder message, DamageBreakdown breakdown)
	{
		DamageBreakdown.Entry me = breakdown.getLocal();
		if (me.isTop())
		{
			message.append(ChatColorType.NORMAL).append(" - ").append(TOP_COLOR, "top damage!");
		}
		else if (breakdown.getNamedCount() > 1)
		{
			message.append(ChatColorType.NORMAL).append(" - #" + breakdown.getLocalRank() + " of " + breakdown.getNamedCount());
		}

		DamageBreakdown.Entry others = breakdown.getOthers();
		if (others != null && others.getPlayerCount() > 1)
		{
			message.append(ChatColorType.NORMAL)
				.append(" - " + others.getPlayerCount() + " others avg " + Format.number(others.getDamage() / others.getPlayerCount()));
		}
	}

	private void queueChat(ChatMessageBuilder message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message.build())
			.build());
	}

	private void publish()
	{
		syncInfoBoxes();
		updateInfoBoxes();
		liveListener.accept(live == null ? null : live.copy());
	}

	private void syncInfoBoxes()
	{
		boolean show = live != null && activeRoom != null;
		syncInfoBox(DamageInfoBox.Kind.ROOM_DAMAGE, show && config.roomDamageInfobox());
		syncInfoBox(DamageInfoBox.Kind.ROOM_SHARE, show && config.roomShareInfobox());
		syncInfoBox(DamageInfoBox.Kind.RAID_DAMAGE, show && config.raidDamageInfobox());
	}

	private void syncInfoBox(DamageInfoBox.Kind kind, boolean show)
	{
		DamageInfoBox box = infoBoxes.get(kind);
		if (show && box == null)
		{
			BufferedImage image = itemManager.getImage(live.getRaid().getIconItemId());
			box = new DamageInfoBox(kind, image, plugin);
			infoBoxes.put(kind, box);
			infoBoxManager.addInfoBox(box);
		}
		else if (!show && box != null)
		{
			infoBoxManager.removeInfoBox(box);
			infoBoxes.remove(kind);
		}
	}

	private void removeInfoBoxes()
	{
		for (DamageInfoBox box : infoBoxes.values())
		{
			infoBoxManager.removeInfoBox(box);
		}
		infoBoxes.clear();
	}

	private void updateInfoBoxes()
	{
		if (infoBoxes.isEmpty() || live == null || activeRoom == null)
		{
			return;
		}

		boolean bossOnly = config.bossDamageOnly();
		RoomRecord room = live.findRoom(activeRoom);
		if (room == null)
		{
			return;
		}

		DamageBreakdown roomBreakdown = DamageBreakdown.ofRoom(room, live.getLocalName(), bossOnly);
		DamageBreakdown.Entry roomMe = roomBreakdown.getLocal();
		String roomTooltip = tooltip(activeRoom.getDisplayName() + (bossOnly ? " (boss)" : ""), roomBreakdown);
		Color roomColor = roomMe != null && roomMe.isTop() ? TOP_COLOR : Color.WHITE;

		DamageInfoBox box = infoBoxes.get(DamageInfoBox.Kind.ROOM_DAMAGE);
		if (box != null)
		{
			box.setText(Format.compact(roomMe == null ? 0 : roomMe.getDamage()));
			box.setTextColor(roomColor);
			box.setTooltip(roomTooltip);
		}

		box = infoBoxes.get(DamageInfoBox.Kind.ROOM_SHARE);
		if (box != null)
		{
			box.setText(roomMe == null ? "0%" : Math.round(roomMe.getShare() * 100) + "%");
			box.setTextColor(roomColor);
			box.setTooltip(roomTooltip);
		}

		box = infoBoxes.get(DamageInfoBox.Kind.RAID_DAMAGE);
		if (box != null)
		{
			DamageBreakdown raidBreakdown = DamageBreakdown.ofRaid(live, bossOnly);
			DamageBreakdown.Entry raidMe = raidBreakdown.getLocal();
			box.setText(Format.compact(raidMe == null ? 0 : raidMe.getDamage()));
			box.setTextColor(raidMe != null && raidMe.isTop() ? TOP_COLOR : Color.WHITE);
			box.setTooltip(tooltip(live.getRaid().getDisplayName() + (bossOnly ? " (boss)" : ""), raidBreakdown));
		}
	}

	private static String tooltip(String title, DamageBreakdown breakdown)
	{
		List<String> lines = new ArrayList<>();
		lines.add(title);
		for (DamageBreakdown.Entry entry : breakdown.getEntries())
		{
			String name = entry.isLocal() ? "You" : entry.getName();
			lines.add(name + ": " + Format.number(entry.getDamage()) + " (" + Format.percent(entry.getShare()) + ")");
		}
		lines.add("Total: " + Format.number(breakdown.getTotal()));
		return String.join("</br>", lines);
	}
}
