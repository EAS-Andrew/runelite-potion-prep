package com.potionprep;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines what to use next, the way Quest Helper does: the teleport on the
 * way (in your inventory, equipment or bank), then seeds and compost at the patch.
 */
class RunItemOverlay extends WidgetItemOverlay
{
	private static final Color HIGHLIGHT = new Color(0x00C8FF);

	private final ItemManager itemManager;
	private volatile Set<Integer> ids = Collections.emptySet();

	@Inject
	RunItemOverlay(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		showOnInventory();
		showOnEquipment();
		showOnBank();
	}

	void set(Set<Integer> ids)
	{
		this.ids = ids;
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		if (!ids.contains(itemId))
		{
			return;
		}
		Rectangle b = item.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, item.getQuantity(), HIGHLIGHT);
		g.drawImage(outline, b.x, b.y, null);
	}
}
