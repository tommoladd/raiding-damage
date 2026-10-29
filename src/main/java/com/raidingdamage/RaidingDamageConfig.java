package com.raidingdamage;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(RaidingDamageConfig.GROUP)
public interface RaidingDamageConfig extends Config
{
	String GROUP = "raiding-damage";

	@ConfigSection(
		name = "Damage tracker",
		description = "Per-room damage tracking for ToB, ToA, CoX and TFA",
		position = 0
	)
	String damageSection = "damageSection";

	@ConfigSection(
		name = "Damage infoboxes",
		description = "Live damage infoboxes shown while in a raid",
		position = 1
	)
	String infoboxSection = "infoboxSection";

	@ConfigItem(
		keyName = "trackTob",
		name = "Theatre of Blood",
		description = "Track damage in the Theatre of Blood",
		section = damageSection,
		position = 0
	)
	default boolean trackTob()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackToa",
		name = "Tombs of Amascut",
		description = "Track damage in the Tombs of Amascut",
		section = damageSection,
		position = 1
	)
	default boolean trackToa()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackCox",
		name = "Chambers of Xeric",
		description = "Track damage in the Chambers of Xeric",
		section = damageSection,
		position = 2
	)
	default boolean trackCox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackTfa",
		name = "The Fractured Archive",
		description = "Track damage in The Fractured Archive",
		section = damageSection,
		position = 3
	)
	default boolean trackTfa()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareWithParty",
		name = "Share with party",
		description = "Send your damage to your RuneLite party, so everyone using this plugin can see exactly who did what."
			+ " Without a party, other players' damage is grouped together as 'Others'.",
		section = damageSection,
		position = 4
	)
	default boolean shareWithParty()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bossDamageOnly",
		name = "Boss damage only",
		description = "Count only damage dealt to the room's boss in chat summaries and infoboxes."
			+ " The side panel has its own toggle.",
		section = damageSection,
		position = 5
	)
	default boolean bossDamageOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "roomChatSummary",
		name = "Room summary in chat",
		description = "Post your damage and share in the chatbox when a room is completed",
		section = damageSection,
		position = 6
	)
	default boolean roomChatSummary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "raidChatSummary",
		name = "Raid summary in chat",
		description = "Post your total damage and share in the chatbox when you leave a raid",
		section = damageSection,
		position = 7
	)
	default boolean raidChatSummary()
	{
		return true;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "historySize",
		name = "Raids to keep",
		description = "How many completed raids to keep in the side panel history",
		section = damageSection,
		position = 8
	)
	default int historySize()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "roomDamageInfobox",
		name = "Room damage",
		description = "Show your damage in the current room",
		section = infoboxSection,
		position = 0
	)
	default boolean roomDamageInfobox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "roomShareInfobox",
		name = "Room share",
		description = "Show your percentage of the current room's damage",
		section = infoboxSection,
		position = 1
	)
	default boolean roomShareInfobox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "raidDamageInfobox",
		name = "Raid damage",
		description = "Show your total damage across the whole raid",
		section = infoboxSection,
		position = 2
	)
	default boolean raidDamageInfobox()
	{
		return false;
	}
}
