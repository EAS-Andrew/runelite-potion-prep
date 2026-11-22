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

	/** Objects to outline (the patches still to do, once you're there). */
	private volatile List<net.runelite.api.GameObject> outline = java.util.Collections.emptyList();

	void set(List<int[]> tiles)
	{
		this.tiles = tiles;
	}

	void setOutline(List<net.runelite.api.GameObject> objects)
	{
		this.outline = objects;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		for (net.runelite.api.GameObject o : minimap ? java.util.Collections.<net.runelite.api.GameObject>emptyList() : outline)
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
		// Start under the player as they move, not at the tile they're stepping to,
		// and draw only the corners so straight runs are one clean line.
		net.runelite.api.Player me = client.getLocalPlayer();
		LocalPoint here = me != null ? me.getLocalLocation() : null;
		java.awt.geom.Path2D.Float line = new java.awt.geom.Path2D.Float();
		boolean open = false;
		for (int i = 0; i < path.size(); i++)
		{
			int[] t = path.get(i);
			boolean corner = i == 0 || i == path.size() - 1
				|| t[0] - path.get(i - 1)[0] != path.get(i + 1)[0] - t[0]
				|| t[1] - path.get(i - 1)[1] != path.get(i + 1)[1] - t[1];
			if (!corner)
			{
				continue;
			}
			LocalPoint lp = i == 0 && here != null ? here : LocalPoint.fromScene(t[0], t[1], wv);
			Point p = minimap ? Perspective.localToMinimap(client, lp) : Perspective.localToCanvas(client, lp, wv.getPlane());
			if (p == null)
			{
				open = false;
				continue;
			}
			if (open)
			{
				line.lineTo(p.getX(), p.getY());
			}
			else
			{
				line.moveTo(p.getX(), p.getY());
				open = true;
			}
		}
		g.draw(line);
		return null;
	}
}
