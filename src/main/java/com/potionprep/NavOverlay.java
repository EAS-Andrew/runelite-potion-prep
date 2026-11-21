package com.potionprep;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Minimap marker for the run's next patch: a pin when it's on the minimap,
 * otherwise an arrow at the minimap's edge pointing the way.
 */
class NavOverlay extends Overlay
{
	static final Color FILL = new Color(30, 147, 255);
	static final Color EDGE = new Color(22, 43, 83);

	/** The game's own farming patch map icon and yellow guide arrow, so it looks native. */
	static final int FARMING_ICON = 1501; // SpriteID.MAP_ICON_FARMING_PATCH
	private static final int GUIDE_ARROW = 441; // SpriteID.MINIMAP_GUIDE_ARROW_YELLOW (points down)

	private final Client client;
	private final net.runelite.client.game.SpriteManager sprites;
	private volatile WorldPoint target;
	/** The guide arrow turned to 64 directions, made once the sprite has loaded. */
	private BufferedImage[] arrows;

	NavOverlay(Client client, net.runelite.client.game.SpriteManager sprites)
	{
		this.client = client;
		this.sprites = sprites;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	void set(WorldPoint target)
	{
		this.target = target;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		WorldPoint dest = target;
		if (dest == null || client.getLocalPlayer() == null)
		{
			return null;
		}
		Widget minimap = client.getWidget(!client.isResized() ? InterfaceID.Toplevel.MINIMAP
			: client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1 ? InterfaceID.ToplevelPreEoc.MINIMAP
			: InterfaceID.ToplevelOsrsStretch.MINIMAP);
		if (minimap == null || minimap.isHidden())
		{
			return null;
		}
		int radius = Math.min(minimap.getWidth(), minimap.getHeight()) / 2 - 19;
		if (radius <= 0)
		{
			return null;
		}
		WorldPoint me = client.getLocalPlayer().getWorldLocation();
		double dx = dest.getX() - me.getX();
		double dy = dest.getY() - me.getY();
		// Rotate into minimap space (it turns with the camera) and scale by its zoom.
		double angle = (client.getCameraYawTarget() & 0x3fff) * Math.PI / 8192;
		double zoom = client.getMinimapZoom();
		double x = (Math.cos(angle) * dx + Math.sin(angle) * dy) * zoom;
		double y = (Math.sin(angle) * dx - Math.cos(angle) * dy) * zoom;
		double len = Math.hypot(x, y);
		boolean inside = len <= radius;
		if (!inside)
		{
			x *= radius / len;
			y *= radius / len;
		}
		net.runelite.api.Point origin = minimap.getCanvasLocation();
		int cx = origin.getX() + minimap.getWidth() / 2 + (int) Math.round(x);
		int cy = origin.getY() + minimap.getHeight() / 2 + (int) Math.round(y);
		BufferedImage img = inside ? sprites.getSprite(FARMING_ICON, 0) : gameArrow(Math.atan2(y, x));
		if (img == null)
		{
			// Sprites not loaded yet: fall back to our own art for this frame.
			img = inside ? PIN : arrow(Math.atan2(y, x));
		}
		g.drawImage(img, cx - img.getWidth() / 2, cy - img.getHeight() / 2, null);
		return null;
	}

	static final Color HIGHLIGHT = new Color(135, 237, 255);
	static final BufferedImage PIN = pin();
	/** Arrows pre-drawn in 64 directions, so nothing is drawn per frame. */
	private static final BufferedImage[] ARROWS = new BufferedImage[64];

	static
	{
		for (int i = 0; i < ARROWS.length; i++)
		{
			ARROWS[i] = drawArrow(i * Math.PI * 2 / ARROWS.length);
		}
	}

	/** The game's yellow guide arrow, turned to point along {@code angle}. */
	private BufferedImage gameArrow(double angle)
	{
		if (arrows == null)
		{
			BufferedImage base = sprites.getSprite(GUIDE_ARROW, 0);
			if (base == null)
			{
				return null;
			}
			arrows = new BufferedImage[64];
			int size = Math.max(base.getWidth(), base.getHeight()) + 4;
			for (int i = 0; i < arrows.length; i++)
			{
				BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = img.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.translate(size / 2.0, size / 2.0);
				// The sprite points down (+y); turn it to point along the angle.
				g.rotate(i * Math.PI * 2 / arrows.length - Math.PI / 2);
				g.drawImage(base, -base.getWidth() / 2, -base.getHeight() / 2, null);
				g.dispose();
				arrows[i] = img;
			}
		}
		return arrows[Math.floorMod((int) Math.round(angle * arrows.length / (2 * Math.PI)), arrows.length)];
	}

	private static BufferedImage arrow(double angle)
	{
		return ARROWS[Math.floorMod((int) Math.round(angle * ARROWS.length / (2 * Math.PI)), ARROWS.length)];
	}

	private static BufferedImage pin()
	{
		BufferedImage img = new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(EDGE);
		g.fillOval(3, 3, 22, 22);
		g.setColor(FILL);
		g.fillOval(5, 5, 18, 18);
		g.setColor(HIGHLIGHT);
		g.setStroke(new BasicStroke(2));
		g.drawOval(5, 5, 18, 18);
		g.setColor(Color.WHITE);
		g.fillOval(11, 11, 6, 6);
		g.dispose();
		return img;
	}

	/** Arrowhead: thick dark outline, blue fill, light inner edge. */
	private static BufferedImage drawArrow(double angle)
	{
		BufferedImage img = new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.translate(14, 14);
		g.rotate(angle);
		Path2D shape = new Path2D.Double();
		shape.moveTo(11, 0);
		shape.lineTo(-9, -9);
		shape.lineTo(-4, 0);
		shape.lineTo(-9, 9);
		shape.closePath();
		g.setColor(EDGE);
		g.setStroke(new BasicStroke(4));
		g.draw(shape);
		g.setColor(FILL);
		g.fill(shape);
		g.setColor(HIGHLIGHT);
		g.setStroke(new BasicStroke(1.5f));
		g.draw(shape);
		g.dispose();
		return img;
	}
}
