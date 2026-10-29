package com.raidingdamage.raid;

import com.raidingdamage.RaidingDamageConfig;
import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum RaidType
{
	// Extra regions are parts of the raid with no combat room, e.g. lobbies and reward rooms
	TOB("Theatre of Blood", "ToB", ItemID.SCYTHE_OF_VITUR, new Color(0xB33A3A), new int[]{12867}),
	TOA("Tombs of Amascut", "ToA", ItemID.TUMEKENS_SHADOW, new Color(0xD9A43A), new int[]{13454, 14160, 14672}),
	COX("Chambers of Xeric", "CoX", ItemID.TWISTED_BOW, new Color(0x4E9A6B), new int[0]),
	TFA("The Fractured Archive", "TFA", ItemID.TFA_CAPE_T5, new Color(0x6A5BD6), new int[]{16474});

	private final String displayName;
	private final String shortName;
	private final int iconItemId;
	private final Color color;
	private final int[] extraRegions;

	public boolean isTracked(RaidingDamageConfig config)
	{
		switch (this)
		{
			case TOB:
				return config.trackTob();
			case TOA:
				return config.trackToa();
			case COX:
				return config.trackCox();
			case TFA:
				return config.trackTfa();
			default:
				return false;
		}
	}

	/**
	 * The raid a (template) region belongs to, or null. CoX is instanced on a random layout,
	 * so it can't be detected by region.
	 */
	public static RaidType forRegion(int region)
	{
		RaidRoom room = RaidRoom.forRegion(region);
		if (room != null)
		{
			return room.getRaid();
		}

		for (RaidType type : values())
		{
			for (int extra : type.extraRegions)
			{
				if (extra == region)
				{
					return type;
				}
			}
		}
		return null;
	}
}
