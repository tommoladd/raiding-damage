package com.raidingdamage.ui;

import com.raidingdamage.damage.DamageBreakdown;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Map;
import javax.swing.JComponent;

/**
 * Thin stacked bar showing everyone's share of a raid at a glance.
 */
class ShareBar extends JComponent
{
	private final DamageBreakdown breakdown;
	private final Map<String, Color> colors;

	ShareBar(DamageBreakdown breakdown, Map<String, Color> colors)
	{
		this.breakdown = breakdown;
		this.colors = colors;
		setPreferredSize(new Dimension(0, 3));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		int w = getWidth();
		int h = getHeight();
		g.setColor(Palette.BAR_BACKGROUND);
		g.fillRect(0, 0, w, h);

		if (breakdown.getTotal() == 0)
		{
			return;
		}

		// Local player first, so "your" segment always starts at the left edge
		int x = 0;
		for (int pass = 0; pass < 2; pass++)
		{
			for (DamageBreakdown.Entry entry : breakdown.getEntries())
			{
				if (entry.isLocal() != (pass == 0))
				{
					continue;
				}
				int width = (int) Math.round(entry.getShare() * w);
				g.setColor(Palette.of(entry, colors));
				g.fillRect(x, 0, Math.min(width, w - x), h);
				x += width;
			}
		}
	}
}
