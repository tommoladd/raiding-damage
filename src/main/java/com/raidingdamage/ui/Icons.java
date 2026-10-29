package com.raidingdamage.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;

/**
 * Icons drawn in code, so there are no image resources to ship.
 */
public final class Icons
{
	static final Color GOLD = new Color(0xFFD24A);

	static final ImageIcon STAR = new ImageIcon(star(10));
	static final ImageIcon EXPANDED = new ImageIcon(chevron(true));
	static final ImageIcon COLLAPSED = new ImageIcon(chevron(false));

	private Icons()
	{
	}

	public static BufferedImage navIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		// One bar per raid, in its colour
		g.setColor(new Color(0xB33A3A));
		g.fillRect(0, 8, 3, 7);
		g.setColor(new Color(0xD9A43A));
		g.fillRect(4, 2, 3, 13);
		g.setColor(new Color(0x4E9A6B));
		g.fillRect(8, 5, 3, 10);
		g.setColor(new Color(0x6A5BD6));
		g.fillRect(12, 10, 3, 5);
		g.dispose();
		return image;
	}

	static void paintStar(Graphics2D g, int x, int y, int size)
	{
		Polygon star = new Polygon();
		double cx = x + size / 2.0;
		double cy = y + size / 2.0 + size * 0.05;
		double outer = size / 2.0;
		double inner = outer * 0.45;
		for (int i = 0; i < 10; i++)
		{
			double r = i % 2 == 0 ? outer : inner;
			double angle = Math.PI / 5 * i - Math.PI / 2;
			star.addPoint((int) Math.round(cx + Math.cos(angle) * r), (int) Math.round(cy + Math.sin(angle) * r));
		}
		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(GOLD);
		g.fillPolygon(star);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
	}

	private static BufferedImage star(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		paintStar(g, 0, 0, size);
		g.dispose();
		return image;
	}

	private static BufferedImage chevron(boolean expanded)
	{
		BufferedImage image = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0x9A9A9A));
		Polygon p = new Polygon();
		if (expanded)
		{
			p.addPoint(1, 2);
			p.addPoint(8, 2);
			p.addPoint(4, 7);
		}
		else
		{
			p.addPoint(2, 1);
			p.addPoint(7, 4);
			p.addPoint(2, 8);
		}
		g.fillPolygon(p);
		g.dispose();
		return image;
	}
}
