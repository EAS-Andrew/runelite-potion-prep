package com.potionprep;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/** An infobox warning of missing items: the first one's icon, how many, and the list on hover. */
class MissingBox extends InfoBox
{
	private final int count;
	private final String tooltip;

	MissingBox(BufferedImage image, Plugin plugin, int count, String tooltip)
	{
		super(image, plugin);
		this.count = count;
		this.tooltip = tooltip;
	}

	@Override
	public String getText()
	{
		return count > 1 ? String.valueOf(count) : "!";
	}

	@Override
	public Color getTextColor()
	{
		return new Color(255, 80, 80);
	}

	@Override
	public String getTooltip()
	{
		return tooltip;
	}
}
