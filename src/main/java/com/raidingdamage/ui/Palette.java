package com.raidingdamage.ui;

import com.raidingdamage.damage.DamageBreakdown;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Bar colours, dark enough for white text to stay readable on top.
 */
final class Palette
{
	static final Color YOU = new Color(0xB86A0C);
	static final Color OTHERS = new Color(0x4A4A4A);
	static final Color BAR_BACKGROUND = new Color(0x232323);
	private static final Color[] PARTY = {
		new Color(0x2F6DB5),
		new Color(0x3D8C4A),
		new Color(0x7B4DB5),
		new Color(0x2A8C8C),
		new Color(0xB5476F),
		new Color(0x8C7A2A),
		new Color(0x5A6E8C),
	};

	private Palette()
	{
	}

	/**
	 * Colour per player, assigned in order of raid damage so it stays the same in every room of a raid.
	 */
	static Map<String, Color> assign(DamageBreakdown raid)
	{
		Map<String, Color> colors = new HashMap<>();
		int next = 0;
		for (DamageBreakdown.Entry entry : raid.getEntries())
		{
			if (entry.isLocal())
			{
				colors.put(entry.getName(), YOU);
			}
			else if (!entry.isOthers())
			{
				colors.put(entry.getName(), PARTY[next++ % PARTY.length]);
			}
		}
		return colors;
	}

	static Color of(DamageBreakdown.Entry entry, Map<String, Color> colors)
	{
		if (entry.isOthers())
		{
			return OTHERS;
		}
		if (entry.isLocal())
		{
			return YOU;
		}
		return colors.getOrDefault(entry.getName(), PARTY[PARTY.length - 1]);
	}
}
