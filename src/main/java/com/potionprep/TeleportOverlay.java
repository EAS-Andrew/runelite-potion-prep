package com.potionprep;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Points at the teleport the way Quest Helper does: the tab it's in
 * (inventory, equipment or Magic) while another is open, then the spell
 * itself in the spellbook. Items are outlined by {@link RunItemOverlay}.
 */
class TeleportOverlay extends Overlay
{
	private static final Color HIGHLIGHT = new Color(0x00C8FF);
	/** Side panel tabs: inventory, equipment and Magic. */
	static final int INVENTORY = 3;
	static final int EQUIPMENT = 4;
	static final int MAGIC = 6;
	/** Each tab's button in every window layout (fixed, resizable, resizable bottom bar). */
	private static final java.util.Map<Integer, int[]> TABS = new java.util.HashMap<>();

	static
	{
		TABS.put(INVENTORY, new int[]{InterfaceID.Toplevel.STONE3, InterfaceID.ToplevelOsrsStretch.STONE3, InterfaceID.ToplevelPreEoc.STONE3});
		TABS.put(EQUIPMENT, new int[]{InterfaceID.Toplevel.STONE4, InterfaceID.ToplevelOsrsStretch.STONE4, InterfaceID.ToplevelPreEoc.STONE4});
		TABS.put(MAGIC, new int[]{InterfaceID.Toplevel.STONE6, InterfaceID.ToplevelOsrsStretch.STONE6, InterfaceID.ToplevelPreEoc.STONE6});
	}

	private final Client client;
	/** Spell to point at, as the spellbook names it (e.g. "Ardougne Teleport"), or null. */
	private volatile String spell;
	/** Where the spell was last found, so the spellbook isn't searched every frame. */
	private int spellWidget = -1;
	private String foundFor;

	@Inject
	TeleportOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/** The tab the teleport is used from (inventory, equipment or Magic), -1 for none. */
	private volatile int tab = -1;

	/**
	 * @param spell spell to point at, or null
	 * @param tab   tab the teleport is in, pointed at while another tab is open; -1 for none
	 */
	void set(String spell, int tab)
	{
		this.spell = spell;
		this.tab = tab;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		String want = spell;
		int needTab = tab;
		if (needTab != -1 && client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL) != needTab)
		{
			// The teleport's tab isn't open: point at its button.
			foundFor = null;
			for (int id : TABS.getOrDefault(needTab, new int[0]))
			{
				Widget button = client.getWidget(id);
				if (button != null && !button.isHidden())
				{
					outline(g, button.getBounds());
					break;
				}
			}
			return null;
		}
		if (want == null)
		{
			// An item: the item outline takes it from here.
			return null;
		}
		Widget list = client.getWidget(InterfaceID.MagicSpellbook.SPELLLAYER);
		if (list != null && !list.isHidden())
		{
			Widget w = find(list, want);
			if (w != null && !w.isHidden())
			{
				outline(g, w.getBounds());
			}
			return null;
		}
		return null;
	}

	private Widget find(Widget list, String want)
	{
		if (want.equals(foundFor))
		{
			Widget w = client.getWidget(spellWidget);
			if (w != null && want.equalsIgnoreCase(Text.removeTags(w.getName())))
			{
				return w;
			}
		}
		Widget[] spells = list.getStaticChildren();
		if (spells != null)
		{
			for (Widget w : spells)
			{
				if (want.equalsIgnoreCase(Text.removeTags(w.getName())))
				{
					spellWidget = w.getId();
					foundFor = want;
					return w;
				}
			}
		}
		return null;
	}

	private static void outline(Graphics2D g, Rectangle r)
	{
		if (r == null || r.width <= 0)
		{
			return;
		}
		g.setStroke(new BasicStroke(2));
		g.setColor(new Color(0, 200, 255, 40));
		g.fill(r);
		g.setColor(HIGHLIGHT);
		g.draw(r);
	}
}
