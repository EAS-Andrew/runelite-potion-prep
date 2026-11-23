package com.potionprep;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Clickable grid of potion icons. Each tile shows how many you can make
 * with what you have, or the Herblore level you still need.
 */
class PotionGrid extends JPanel
{
	private static final int COLUMNS = 5;
	private static final Color GOOD = new Color(0x5FBF5F);
	private static final Color BAD = new Color(0xE05A5A);
	private static final Color PIE = new Color(0xFFB000);

	private final Map<Potion, Tile> tiles = new EnumMap<>(Potion.class);
	private final Consumer<Potion> onSelect;
	private Potion selected;
	private String filter = "";

	PotionGrid(ItemManager itemManager, Consumer<Potion> onSelect)
	{
		super(new GridLayout(0, COLUMNS, 3, 3));
		this.onSelect = onSelect;
		setOpaque(false);
		for (Potion p : Potion.values())
		{
			Tile t = new Tile(p);
			itemManager.getImage(p.getProduct().getId()).addTo(t.icon);
			tiles.put(p, t);
		}
		layoutTiles();
	}

	void setFilter(String text)
	{
		String f = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
		if (!f.equals(filter))
		{
			filter = f;
			layoutTiles();
		}
	}

	/** First tile matching the filter, for Enter in the search box. */
	Potion firstVisible()
	{
		for (Potion p : Potion.values())
		{
			if (matches(p))
			{
				return p;
			}
		}
		return null;
	}

	void setSelected(Potion potion)
	{
		selected = potion;
		tiles.values().forEach(Tile::paintState);
	}

	/**
	 * @param boostedLevel current Herblore level, 0 if unknown
	 * @param pie          potions you can only make with a botanical pie
	 */
	void setStats(Map<Potion, Integer> makeable, Map<Potion, Double> xp, int boostedLevel, Set<Potion> pie)
	{
		for (Tile t : tiles.values())
		{
			int lvl = t.potion.getLevel();
			t.pie = pie.contains(t.potion);
			if (!t.pie && boostedLevel > 0 && boostedLevel < lvl)
			{
				t.count.setText("Lv " + lvl);
				t.count.setForeground(BAD);
				t.makeable = -1;
			}
			else
			{
				int n = t.potion.toPotions(makeable.getOrDefault(t.potion, 0));
				t.count.setText(QuantityFormatter.quantityToStackSize(n));
				t.count.setForeground(t.pie ? PIE : n > 0 ? GOOD : ColorScheme.MEDIUM_GRAY_COLOR);
				t.makeable = n;
				t.xp = xp.getOrDefault(t.potion, 0.0);
			}
			t.updateTooltip();
		}
	}

	private boolean matches(Potion p)
	{
		return filter.isEmpty() || p.getName().toLowerCase(Locale.ROOT).contains(filter);
	}

	private void layoutTiles()
	{
		removeAll();
		for (Potion p : Potion.values())
		{
			if (matches(p))
			{
				add(tiles.get(p));
			}
		}
		revalidate();
		repaint();
	}

	private class Tile extends JPanel
	{
		final Potion potion;
		final JLabel icon = new JLabel();
		final JLabel count = new JLabel(" ");
		int makeable;
		double xp;
		boolean pie;
		boolean hover;

		Tile(Potion potion)
		{
			super(new BorderLayout());
			this.potion = potion;
			icon.setHorizontalAlignment(SwingConstants.CENTER);
			count.setHorizontalAlignment(SwingConstants.CENTER);
			count.setFont(FontManager.getRunescapeSmallFont());
			count.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			add(icon, BorderLayout.CENTER);
			add(count, BorderLayout.SOUTH);
			updateTooltip();
			paintState();

			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					// On press: a click that moves a pixel isn't a "click" to Swing.
					if (javax.swing.SwingUtilities.isLeftMouseButton(e))
					{
						onSelect.accept(potion);
					}
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					paintState();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					paintState();
				}
			});
		}

		void updateTooltip()
		{
			String line = makeable < 0 ? "Needs " + potion.getLevel() + " Herblore<br>or "
				+ (potion.getLevel() - PotionPlan.PIE_BOOST) + " with a botanical pie"
				: "Level " + potion.getLevel() + " &middot; can make " + makeable + " &times; " + potion.potionName()
				+ (xp > 0 ? "<br>" + String.format(Locale.US, "%,d", (long) xp) + " xp" : "")
				+ (pie ? "<br>with a botanical pie (+" + PotionPlan.PIE_BOOST + ")" : "");
			setToolTipText("<html>" + potion.getName() + "<br>" + line + "</html>");
		}

		void paintState()
		{
			boolean sel = potion == selected;
			setBackground(sel ? ColorScheme.DARK_GRAY_HOVER_COLOR
				: hover ? ColorScheme.MEDIUM_GRAY_COLOR
				: ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createLineBorder(sel ? ColorScheme.BRAND_ORANGE : getBackground(), 1));
		}
	}
}
