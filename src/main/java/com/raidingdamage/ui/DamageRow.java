package com.raidingdamage.ui;

import com.raidingdamage.Format;
import com.raidingdamage.damage.DamageBreakdown;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import net.runelite.client.ui.FontManager;

/**
 * One player's damage: a bar scaled against the top damage, with name, damage and share drawn over it.
 */
class DamageRow extends JComponent
{
	private static final int HEIGHT = 14;
	private static final int PAD = 4;
	private static final int STAR_SIZE = 9;

	private final String name;
	private final String value;
	private final boolean top;
	private final double fill;
	private final Color color;

	DamageRow(DamageBreakdown.Entry entry, int maxDamage, Color color)
	{
		int players = entry.getPlayerCount();
		boolean group = entry.isOthers() && players > 1;
		this.name = entry.isLocal() ? "You"
			: group ? entry.getName() + " (avg " + Math.round(entry.getShare() / players * 100) + "%)"
			: entry.getName();
		this.value = Format.number(entry.getDamage()) + "  " + Format.percent(entry.getShare());
		this.top = entry.isTop();
		this.fill = maxDamage <= 0 ? 0 : (double) entry.getDamage() / maxDamage;
		this.color = color;

		setFont(FontManager.getRunescapeSmallFont());
		setPreferredSize(new Dimension(0, HEIGHT));

		String tooltip = (entry.isLocal() ? "You (" + entry.getName() + ")" : entry.getName())
			+ ": " + Format.number(entry.getDamage()) + " damage, " + Format.percent(entry.getShare());
		if (group)
		{
			tooltip = String.join(", ", entry.getMembers()) + ": " + Format.number(entry.getDamage()) + " damage between them, "
				+ Format.percent(entry.getShare()) + " - about " + Format.number(entry.getDamage() / players)
				+ " (" + Format.percent(entry.getShare() / players) + ") each";
		}
		else if (entry.isOthers())
		{
			tooltip += " - players not sharing damage through a RuneLite party";
		}
		else if (entry.isInferred())
		{
			tooltip += " - every hit that wasn't yours or a party member's";
		}
		if (top)
		{
			tooltip += " - top damage";
		}
		setToolTipText(tooltip);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		int w = getWidth();
		int h = getHeight();

		g.setColor(Palette.BAR_BACKGROUND);
		g.fillRect(0, 0, w, h);
		g.setColor(color);
		g.fillRect(0, 0, (int) Math.round(w * fill), h);

		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;

		int valueWidth = fm.stringWidth(value);
		int valueX = w - PAD - valueWidth;
		int nameSpace = valueX - PAD * 2 - (top ? STAR_SIZE + 3 : 0);
		String shownName = fit(name, fm, nameSpace);

		drawShadowed(g, shownName, PAD, baseline);
		drawShadowed(g, value, valueX, baseline);
		if (top)
		{
			Icons.paintStar(g, PAD + fm.stringWidth(shownName) + 3, (h - STAR_SIZE) / 2, STAR_SIZE);
		}
		g.dispose();
	}

	private static void drawShadowed(Graphics2D g, String text, int x, int y)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(Color.WHITE);
		g.drawString(text, x, y);
	}

	private static String fit(String text, FontMetrics fm, int width)
	{
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		for (int len = text.length() - 1; len > 0; len--)
		{
			String cut = text.substring(0, len) + "..";
			if (fm.stringWidth(cut) <= width)
			{
				return cut;
			}
		}
		return "";
	}
}
