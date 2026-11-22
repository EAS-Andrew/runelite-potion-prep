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

	/**
	 * A patch object's model points, flattened to the ground so a tall crop
	 * doesn't make the outline plant-shaped. Added to {@code pts}.
	 */
	private void groundPoints(net.runelite.api.GameObject o, List<int[]> pts)
	{
		WorldView wv = client.getTopLevelWorldView();
		net.runelite.api.Renderable r = o.getRenderable();
		net.runelite.api.Model m = r instanceof net.runelite.api.Model ? (net.runelite.api.Model) r : r != null ? r.getModel() : null;
		LocalPoint lp = o.getLocalLocation();
		if (wv == null || m == null || lp == null)
		{
			return;
		}
		int n = m.getVerticesCount();
		float[] flat = new float[n];
		int[] xs = new int[n];
		int[] ys = new int[n];
		int height = Perspective.getTileHeight(client, lp, o.getPlane());
		Perspective.modelToCanvas(client, wv, n, lp.getX(), lp.getY(), height, o.getModelOrientation(),
			m.getVerticesX(), m.getVerticesZ(), flat, xs, ys);
		for (int i = 0; i < n; i++)
		{
			if (xs[i] != Integer.MIN_VALUE && ys[i] != Integer.MIN_VALUE)
			{
				pts.add(new int[]{xs[i], ys[i]});
			}
		}
	}

	/**
	 * Objects of the same id that touch (within a tile of each other) are one
	 * patch: an allotment can be made of several pieces.
	 */
	private static List<List<net.runelite.api.GameObject>> groups(List<net.runelite.api.GameObject> objects)
	{
		List<List<net.runelite.api.GameObject>> out = new java.util.ArrayList<>();
		for (net.runelite.api.GameObject o : objects)
		{
			List<net.runelite.api.GameObject> into = null;
			for (java.util.Iterator<List<net.runelite.api.GameObject>> it = out.iterator(); it.hasNext(); )
			{
				List<net.runelite.api.GameObject> g = it.next();
				if (g.stream().anyMatch(x -> x.getPlane() == o.getPlane() && touches(x, o)))
				{
					if (into == null)
					{
						into = g;
					}
					else
					{
						// o joins two groups: merge them.
						into.addAll(g);
						it.remove();
					}
				}
			}
			if (into == null)
			{
				into = new java.util.ArrayList<>();
				out.add(into);
			}
			into.add(o);
		}
		return out;
	}

	private static boolean touches(net.runelite.api.GameObject a, net.runelite.api.GameObject b)
	{
		Point a0 = a.getSceneMinLocation();
		Point a1 = a.getSceneMaxLocation();
		Point b0 = b.getSceneMinLocation();
		Point b1 = b.getSceneMaxLocation();
		// Pieces of one patch share an id; a neighbouring different patch doesn't join.
		return a.getId() == b.getId() && a0 != null && a1 != null && b0 != null && b1 != null
			&& b0.getX() <= a1.getX() + 1 && a0.getX() <= b1.getX() + 1
			&& b0.getY() <= a1.getY() + 1 && a0.getY() <= b1.getY() + 1;
	}

	/** Convex hull of screen points (monotone chain), or null if too few. */
	static java.awt.Polygon hull(List<int[]> pts)
	{
		if (pts.size() < 3)
		{
			return null;
		}
		pts.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
		int[][] h = new int[pts.size() * 2][];
		int k = 0;
		for (int[] p : pts)
		{
			while (k >= 2 && cross(h[k - 2], h[k - 1], p) <= 0)
			{
				k--;
			}
			h[k++] = p;
		}
		for (int i = pts.size() - 2, lower = k + 1; i >= 0; i--)
		{
			int[] p = pts.get(i);
			while (k >= lower && cross(h[k - 2], h[k - 1], p) <= 0)
			{
				k--;
			}
			h[k++] = p;
		}
		java.awt.Polygon poly = new java.awt.Polygon();
		for (int i = 0; i < k - 1; i++)
		{
			poly.addPoint(h[i][0], h[i][1]);
		}
		return poly.npoints >= 3 ? poly : null;
	}

	private static long cross(int[] o, int[] a, int[] b)
	{
		return (long) (a[0] - o[0]) * (b[1] - o[1]) - (long) (a[1] - o[1]) * (b[0] - o[0]);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		for (List<net.runelite.api.GameObject> group : minimap ? Collections.<List<net.runelite.api.GameObject>>emptyList() : groups(outline))
		{
			// One smooth shape per patch, on the ground: a growing crop's model is the plant, not the patch.
			List<int[]> pts = new java.util.ArrayList<>();
			for (net.runelite.api.GameObject o : group)
			{
				groundPoints(o, pts);
			}
			java.awt.Shape hull = hull(pts);
			if (hull == null && !group.isEmpty())
			{
				// No model to flatten: the game's own outline of the object is better than nothing.
				hull = group.get(0).getConvexHull();
			}
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
