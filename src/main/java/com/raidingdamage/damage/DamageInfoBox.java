package com.raidingdamage.damage;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * Shows a pre-formatted value. Text is computed by the tracker when damage changes, not per frame.
 */
class DamageInfoBox extends InfoBox
{
	enum Kind
	{
		ROOM_DAMAGE,
		ROOM_SHARE,
		RAID_DAMAGE,
	}

	@Getter
	private final Kind kind;
	@Setter
	private volatile String text = "";
	@Setter
	private volatile Color textColor = Color.WHITE;

	DamageInfoBox(Kind kind, BufferedImage image, Plugin plugin)
	{
		super(image, plugin);
		this.kind = kind;
	}

	@Override
	public String getText()
	{
		return text;
	}

	@Override
	public Color getTextColor()
	{
		return textColor;
	}

	@Override
	public String getName()
	{
		return super.getName() + "_" + kind.name();
	}
}
