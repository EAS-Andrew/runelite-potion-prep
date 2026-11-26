package com.potionprep;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** A small movable panel warning of what you're missing: a red title, then one item per line. */
class MissingOverlay extends OverlayPanel
{
	private volatile String title;
	private volatile List<String> lines = List.of();

	MissingOverlay()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	void set(String title, List<String> lines)
	{
		this.title = title;
		this.lines = List.copyOf(lines);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<String> shown = lines;
		if (title == null || shown.isEmpty())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text(title).color(new Color(255, 80, 80)).build());
		for (String line : shown)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(line).build());
		}
		return super.render(graphics);
	}
}
