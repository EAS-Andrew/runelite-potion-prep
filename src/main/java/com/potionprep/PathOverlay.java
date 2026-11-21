package com.potionprep;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Draws the walk to the run's next patch on the ground, or on the minimap. */
class PathOverlay extends Overlay
{
	private static final Color LINE = new Color(0, 200, 255, 220);

	private final Client client;
	private final boolean minimap;
	/** Scene tiles {x, y}; set on the client thread. */
	private volatile List<int[]> tiles = Collections.emptyList();

	PathOverlay(Client client, boolean minimap)
	{
		this.client = client;
		this.minimap = minimap;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(minimap ? OverlayLayer.ABOVE_WIDGETS : OverlayLayer.ABOVE_SCENE);
	}

	/** Object to outline (the herb patch, once you're there), or null. */
	private volatile net.runelite.api.GameObject outline;

	void set(List<int[]> tiles)
	{
		this.tiles = tiles;
	}

	void setOutline(net.runelite.api.GameObject object)
	{
		this.outline = object;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		net.runelite.api.GameObject o = outline;
		if (!minimap && o != null)
		{
			java.awt.Shape hull = o.getConvexHull();
			if (hull != null)
			{
				g.setStroke(new BasicStroke(2));
				g.setColor(new Color(0, 200, 255, 40));
				g.fill(hull);
				g.setColor(LINE);
				g.draw(hull);
			}
		}
		List<int[]> path = tiles;
		if (path.size() < 2)
		{
			return null;
		}
		WorldView wv = client.getTopLevelWorldView();
		g.setColor(LINE);
		g.setStroke(new BasicStroke(minimap ? 2 : 3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Point prev = null;
		for (int[] t : path)
		{
			LocalPoint lp = LocalPoint.fromScene(t[0], t[1], wv);
			Point p = minimap ? Perspective.localToMinimap(client, lp) : Perspective.localToCanvas(client, lp, wv.getPlane());
			if (p != null && prev != null)
			{
				g.drawLine(prev.getX(), prev.getY(), p.getX(), p.getY());
			}
			prev = p;
		}
		return null;
	}
}
