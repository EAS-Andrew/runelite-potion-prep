package com.potionprep;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Points at a teleport spell, the way Quest Helper does: the spell itself
 * when the spellbook is open, otherwise the Magic tab to open it.
 */
class TeleportOverlay extends Overlay
{
	private static final Color HIGHLIGHT = new Color(0x00C8FF);
	/** The Magic tab, in each window layout (fixed, resizable, resizable bottom bar). */
	private static final int[] MAGIC_TABS = {
		InterfaceID.Toplevel.STONE6, InterfaceID.ToplevelOsrsStretch.STONE6, InterfaceID.ToplevelPreEoc.STONE6};

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

	void set(String spell)
	{
		this.spell = spell;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		String want = spell;
		if (want == null)
		{
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
		foundFor = null;
		for (int id : MAGIC_TABS)
		{
			Widget tab = client.getWidget(id);
			if (tab != null && !tab.isHidden())
			{
				outline(g, tab.getBounds());
				break;
			}
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
