package com.potionprep;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Headings you click to fold their section away, each with a one-line
 * summary beside it. Which are open is saved, separately for during a run.
 */
class Folds
{
	/** A heading and the body it folds. */
	static final class Section
	{
		final String key;
		final JPanel body = new JPanel();
		final JLabel title = new JLabel();
		final JLabel summary = new JLabel();
		final JPanel header = new JPanel(new BorderLayout(4, 0));

		Section(String key)
		{
			this.key = key;
		}
	}

	private final PotionPrepPlugin plugin;
	private final JPanel container;
	private final String idleKey;
	private final String idleDefault;
	private final String runKey;
	private final String runDefault;
	private final List<Section> sections = new ArrayList<>();
	private boolean running;

	Folds(PotionPrepPlugin plugin, JPanel container, String idleKey, String idleDefault, String runKey, String runDefault)
	{
		this.plugin = plugin;
		this.container = container;
		this.idleKey = idleKey;
		this.idleDefault = idleDefault;
		this.runKey = runKey;
		this.runDefault = runDefault;
	}

	/** Adds a heading and an empty body to the container. */
	Section add(String key, String name)
	{
		Section sec = new Section(key);
		sec.title.setText(name);
		sec.title.setFont(FontManager.getRunescapeSmallFont());
		sec.title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sec.summary.setFont(FontManager.getRunescapeSmallFont());
		sec.summary.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		// In the middle, right-aligned: a long summary is cut short rather than pushing the title.
		sec.summary.setHorizontalAlignment(SwingConstants.RIGHT);
		sec.header.setOpaque(false);
		sec.header.setBorder(new EmptyBorder(3, 0, 4, 0));
		sec.header.add(sec.title, BorderLayout.WEST);
		sec.header.add(sec.summary, BorderLayout.CENTER);
		sec.header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		sec.header.setToolTipText("Click to show or hide");
		sec.header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					setOpen(sec.key, !isOpen(sec.key));
				}
			}
		});
		sec.header.setAlignmentX(Component.LEFT_ALIGNMENT);
		sec.header.setMaximumSize(new Dimension(Integer.MAX_VALUE, sec.header.getPreferredSize().height));
		sec.body.setLayout(new BoxLayout(sec.body, BoxLayout.Y_AXIS));
		sec.body.setOpaque(false);
		sec.body.setAlignmentX(Component.LEFT_ALIGNMENT);
		container.add(sec.header);
		container.add(sec.body);
		sections.add(sec);
		return sec;
	}

	boolean isOpen(String key)
	{
		return openSet().contains(key);
	}

	/** Opens or folds a section, and remembers it for the current state. */
	void setOpen(String key, boolean open)
	{
		Set<String> set = openSet();
		if (open ? set.add(key) : set.remove(key))
		{
			plugin.setPanelSetting(running ? runKey : idleKey, String.join(",", set));
		}
		apply();
	}

	/** A run started or ended: fold to that state's sections. */
	void setRunning(boolean running)
	{
		if (running != this.running)
		{
			this.running = running;
			apply();
		}
	}

	private Set<String> openSet()
	{
		String key = running ? runKey : idleKey;
		String v = key != null ? plugin.getPanelSetting(key) : null;
		Set<String> out = new LinkedHashSet<>();
		for (String k : (v != null ? v : running ? runDefault : idleDefault).split(","))
		{
			if (!k.isEmpty())
			{
				out.add(k);
			}
		}
		return out;
	}

	void apply()
	{
		Set<String> open = openSet();
		for (Section sec : sections)
		{
			boolean on = open.contains(sec.key);
			sec.body.setVisible(on);
			sec.title.setIcon(new Arrow(on));
		}
		container.revalidate();
		container.repaint();
	}

	/** A small triangle: pointing down when open, right when folded. */
	private static final class Arrow implements Icon
	{
		private final boolean open;

		Arrow(boolean open)
		{
			this.open = open;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			Polygon t = open
				? new Polygon(new int[]{x, x + 8, x + 4}, new int[]{y + 2, y + 2, y + 7}, 3)
				: new Polygon(new int[]{x + 2, x + 7, x + 2}, new int[]{y, y + 4, y + 8}, 3);
			g2.fill(t);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 9;
		}

		@Override
		public int getIconHeight()
		{
			return 9;
		}
	}
}
