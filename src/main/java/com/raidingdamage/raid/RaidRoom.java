package com.raidingdamage.raid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.gameval.NpcID;

@Getter
public enum RaidRoom
{
	// Theatre of Blood - identified by region
	MAIDEN(RaidType.TOB, "Maiden", regions(12613), "The Maiden of Sugadinti"),
	BLOAT(RaidType.TOB, "Bloat", regions(13125), "Pestilent Bloat"),
	NYLOCAS(RaidType.TOB, "Nylocas", regions(13122), "Nylocas Vasilias", "Nylocas Prinkipas"),
	SOTETSEG(RaidType.TOB, "Sotetseg", regions(13123, 13379), "Sotetseg"),
	XARPUS(RaidType.TOB, "Xarpus", regions(12612), "Xarpus"),
	VERZIK(RaidType.TOB, "Verzik", regions(12611), "Verzik Vitur"),

	// Tombs of Amascut - identified by region
	CRONDIS(RaidType.TOA, "Path of Crondis", regions(15698)),
	ZEBAK(RaidType.TOA, "Zebak", regions(15700), "Zebak"),
	SCABARAS(RaidType.TOA, "Path of Scabaras", regions(14162)),
	KEPHRI(RaidType.TOA, "Kephri", regions(14164), "Kephri"),
	APMEKEN(RaidType.TOA, "Path of Apmeken", regions(15186)),
	BABA(RaidType.TOA, "Ba-Ba", regions(15188), "Ba-Ba"),
	HET(RaidType.TOA, "Path of Het", regions(14674)),
	AKKHA(RaidType.TOA, "Akkha", regions(14676), "Akkha"),
	WARDENS(RaidType.TOA, "Wardens", regions(15184, 15696), "Obelisk", "Elidinis' Warden", "Tumeken's Warden", "Core"),

	// Chambers of Xeric - the layout is random, so rooms are identified by the NPC being hit
	TEKTON(RaidType.COX, "Tekton", npcs("Tekton"), "Tekton"),
	VASA(RaidType.COX, "Vasa Nistirio", npcs("Vasa Nistirio", "Glowing crystal"), "Vasa Nistirio"),
	VANGUARDS(RaidType.COX, "Vanguards", npcs("Vanguard"), "Vanguard"),
	MUTTADILES(RaidType.COX, "Muttadiles", npcs("Muttadile"), "Muttadile"),
	SHAMANS(RaidType.COX, "Shamans", npcs("Lizardman shaman"), "Lizardman shaman"),
	MYSTICS(RaidType.COX, "Mystics", npcs("Skeletal Mystic"), "Skeletal Mystic"),
	GUARDIANS(RaidType.COX, "Guardians", npcs("Guardian"), "Guardian"),
	ICE_DEMON(RaidType.COX, "Ice Demon", npcs("Ice demon"), "Ice demon"),
	VESPULA(RaidType.COX, "Vespula", npcs("Vespula", "Lux grub", "Vespine soldier", "Abyssal portal"), "Vespula", "Abyssal portal"),
	ROPE(RaidType.COX, "Rope", npcs("Deathly ranger", "Deathly mage"), "Deathly ranger", "Deathly mage"),
	SCAVENGERS(RaidType.COX, "Scavengers", npcs("Scavenger beast"), "Scavenger beast"),
	OLM(RaidType.COX, "Great Olm", npcs("Great Olm"), "Great Olm", "Great Olm Left Claw", "Great Olm Right Claw"),
	COX_OTHER(RaidType.COX, "Other", npcs()),

	// The Fractured Archive - identified by region; bosses by NPC id
	ARCHIVE_GUARDIAN(RaidType.TFA, "Archive Guardian", regions(16471), ids(NpcID.TFA_GUARDIAN, NpcID.TFA_GUARDIAN_UNSHIELDED)),
	EZEKIEL(RaidType.TFA, "Ezekiel", regions(16214), ids(NpcID.TFA_EZEKIEL)),
	BORK(RaidType.TFA, "Bork & Smork", regions(16727), ids(NpcID.TFA_BORK, NpcID.TFA_SMORK)),
	MOIA(RaidType.TFA, "Moia", regions(16216), ids(NpcID.TFA_MOIA)),
	DREADNOTH(RaidType.TFA, "Dreadnoth", regions(16728), ids(NpcID.TFA_JELLY_BOSS)),
	DAGONHAI(RaidType.TFA, "Dagon'hai Elder", regions(16215), ids(NpcID.TFA_DAGONHAI_LEADER, NpcID.TFA_DAGONHAI_LEADER_UNSHIELDED)),
	XARTH(RaidType.TFA, "Xarth", regions(16726), ids(NpcID.TFA_XARTH)),
	OCELLUS(RaidType.TFA, "Ocellus", regions(16472), ids(NpcID.TFA_OCELLUS_P1, NpcID.TFA_OCELLUS_P2, NpcID.TFA_OCELLUS_P3));

	private static final Map<Integer, RaidRoom> BY_REGION = new HashMap<>();

	static
	{
		for (RaidRoom room : values())
		{
			for (int region : room.regions)
			{
				BY_REGION.put(region, room);
			}
		}
	}

	private final RaidType raid;
	private final String displayName;
	@Getter(AccessLevel.NONE)
	private final int[] regions;
	@Getter(AccessLevel.NONE)
	private final String[] npcPrefixes;
	@Getter(AccessLevel.NONE)
	private final Set<String> bosses = new HashSet<>();
	@Getter(AccessLevel.NONE)
	private final Set<Integer> bossIds = new HashSet<>();

	RaidRoom(RaidType raid, String displayName, int[] regions, String... bosses)
	{
		this(raid, displayName, regions, new String[0], bosses);
	}

	RaidRoom(RaidType raid, String displayName, String[] npcPrefixes, String... bosses)
	{
		this(raid, displayName, new int[0], npcPrefixes, bosses);
	}

	RaidRoom(RaidType raid, String displayName, int[] regions, int[] bossIds)
	{
		this(raid, displayName, regions, new String[0], new String[0]);
		for (int id : bossIds)
		{
			this.bossIds.add(id);
		}
	}

	RaidRoom(RaidType raid, String displayName, int[] regions, String[] npcPrefixes, String[] bosses)
	{
		this.raid = raid;
		this.displayName = displayName;
		this.regions = regions;
		this.npcPrefixes = new String[npcPrefixes.length];
		for (int i = 0; i < npcPrefixes.length; i++)
		{
			this.npcPrefixes[i] = npcPrefixes[i].toLowerCase(Locale.ROOT);
		}
		for (String boss : bosses)
		{
			this.bosses.add(boss.toLowerCase(Locale.ROOT));
		}
	}

	public boolean isBoss(int npcId, String npcName)
	{
		return bossIds.contains(npcId) || bosses.contains(npcName.toLowerCase(Locale.ROOT));
	}

	/**
	 * The last room of the raid; its completion message means the raid was completed.
	 */
	public boolean isFinalRoom()
	{
		return this == VERZIK || this == WARDENS || this == OLM || this == OCELLUS;
	}

	public static RaidRoom forRegion(int region)
	{
		return BY_REGION.get(region);
	}

	public static RaidRoom forCoxNpc(String npcName)
	{
		String name = npcName.toLowerCase(Locale.ROOT);
		for (RaidRoom room : values())
		{
			for (String prefix : room.npcPrefixes)
			{
				if (name.startsWith(prefix))
				{
					return room;
				}
			}
		}
		return COX_OTHER;
	}

	public static RaidRoom fromKey(String key)
	{
		try
		{
			return key == null ? null : valueOf(key);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	private static int[] regions(int... regions)
	{
		return regions;
	}

	private static String[] npcs(String... names)
	{
		return names;
	}

	private static int[] ids(int... ids)
	{
		return ids;
	}
}
