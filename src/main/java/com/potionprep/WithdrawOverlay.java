package com.potionprep;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * Like Quest Helper's bank tab: in the herb run's bank view, each item shows
 * how many the run needs, green when you have enough, red when short.
 */
class WithdrawOverlay extends WidgetItemOverlay
{
	private static final Color ENOUGH = new Color(0x5FBF5F);
	private static final Color SHORT = new Color(0xE05A5A);

	/** Item id to amount needed; set on the client thread, read while drawing. */
	private volatile Map<Integer, Integer> need = Collections.emptyMap();
	/** Item id to amount you have (bank, inventory, rune pouch). */
	private volatile Map<Integer, Integer> have = Collections.emptyMap();
	private volatile boolean active;

	@Inject
	WithdrawOverlay()
	{
		showOnBank();
	}

	void set(boolean active, Map<Integer, Integer> need, Map<Integer, Integer> have)
	{
		this.need = need;
		this.have = have;
		this.active = active;
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		if (!active)
		{
			return;
		}
		Integer n = need.get(itemId);
		if (n == null || n <= 0)
		{
			return;
		}
		Rectangle b = item.getCanvasBounds();
		String text = String.valueOf(n);
		g.setFont(FontManager.getRunescapeSmallFont());
		int w = g.getFontMetrics().stringWidth(text);
		TextComponent t = new TextComponent();
		t.setText(text);
		t.setColor(have.getOrDefault(itemId, 0) >= n ? ENOUGH : SHORT);
		// Bottom right, clear of the stack size in the top left.
		t.setPosition(new java.awt.Point(b.x + b.width - w - 1, b.y + b.height - 1));
		t.render(g);
	}
}
