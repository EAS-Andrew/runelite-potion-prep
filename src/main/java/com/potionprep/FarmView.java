package com.potionprep;

import com.potionprep.Farm.AllotmentSeed;
import com.potionprep.Farm.FlowerSeed;
import com.potionprep.Farm.HerbSeed;
import com.potionprep.Farm.Patch;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

/** The "Herb run" side of the panel. */
class FarmView extends JPanel
{
	private static final Color GOOD = new Color(0x5FBF5F);
	private static final Color ACTION = new Color(0xFFB000);
	private static final Color BAD = new Color(0xE05A5A);

	private final PotionPrepPlugin plugin;
	private final ItemManager itemManager;

	private final JComboBox<HerbSeed> herb = new JComboBox<>(HerbSeed.values());
	private final JComboBox<FlowerSeed> flower = new JComboBox<>(FlowerSeed.values());
	private final JComboBox<AllotmentSeed> allotment = new JComboBox<>(AllotmentSeed.values());
	private final JToggleButton showToggle = new JToggleButton("Show in bank");
	private final Map<Patch, JCheckBox> patchBoxes = new EnumMap<>(Patch.class);
	private final Map<Patch, JLabel> patchTeleports = new EnumMap<>(Patch.class);
	private final Map<Patch, JLabel> patchStates = new EnumMap<>(Patch.class);
	/** Patch rows in run order; the player drags them by the grip to reorder. */
	private final JPanel patchList = new JPanel();
	private final Map<Patch, JPanel> patchRows = new EnumMap<>(Patch.class);
	private java.util.List<Patch> viewOrder = new java.util.ArrayList<>(java.util.Arrays.asList(Patch.values()));
	/** The patch being dragged, or null. */
	private Patch dragging;
	private final JPanel suppliesPanel = new JPanel();
	private final JPanel stepsPanel = new JPanel();
	private final JLabel statusLabel = new JLabel();
	private final JPanel harvestBox = new JPanel();
	private final javax.swing.JButton startRun = new javax.swing.JButton("Start run");
	private final javax.swing.JButton skipStop = new javax.swing.JButton("Skip patch");
	private final javax.swing.JButton stopRun = new javax.swing.JButton("Stop run");
	private final JPanel runRows = new JPanel();
	private JPanel runButtonsPanel;
	final GearButtons gearButtons;
	private final JLabel harvestLabel = new JLabel();
	private final JLabel harvestSub = new JLabel();
	/** Seeds you have (bank, inventory, seed vault) and your Farming level, for the dropdowns. */
	private Map<Integer, Integer> seedCounts = java.util.Collections.emptyMap();
	/** Patches on the run of each kind, to colour seed counts by whether they're enough. */
	private int herbsOnRun;
	private int flowersOnRun;
	private int allotmentsOnRun;
	private int farmingLevel;
	/** Set while the plugin pushes state, so listeners don't echo it back. */
	private boolean updating;

	FarmView(PotionPrepPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(left(heading("Seeds")));
		add(stretch(combo(herb, "Herb seed", () -> plugin.setFarmSetting("farmHerb", ((HerbSeed) herb.getSelectedItem()).name()))));
		add(Box.createVerticalStrut(4));
		add(stretch(combo(flower, "Flower seed, one per flower patch",
			() -> plugin.setFarmSetting("farmFlower", ((FlowerSeed) flower.getSelectedItem()).name()))));
		add(Box.createVerticalStrut(4));
		add(stretch(combo(allotment, "Allotment seed, three per allotment",
			() -> plugin.setFarmSetting("farmAllotment", ((AllotmentSeed) allotment.getSelectedItem()).name()))));
		add(Box.createVerticalStrut(6));

		harvestBox.setLayout(new BoxLayout(harvestBox, BoxLayout.Y_AXIS));
		harvestBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		harvestBox.setBorder(new EmptyBorder(6, 8, 6, 8));
		harvestLabel.setFont(FontManager.getRunescapeBoldFont());
		harvestLabel.setForeground(GOOD);
		harvestLabel.setToolTipText("Expected from this run's plantings: Farming level, compost, magic secateurs, "
			+ "Farming cape, diaries and the chance of disease");
		harvestSub.setFont(FontManager.getRunescapeSmallFont());
		harvestSub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		harvestBox.add(left(harvestLabel));
		harvestBox.add(left(harvestSub));
		harvestBox.setVisible(false);
		// Not stretch(): its text changes, so its height must too.
		harvestBox.setAlignmentX(LEFT_ALIGNMENT);
		add(harvestBox);
		add(Box.createVerticalStrut(6));

		showToggle.setFocusable(false);
		showToggle.setToolTipText("While on, the bank opens filtered to your herb run");
		showToggle.addActionListener(e -> plugin.setShowInBank(showToggle.isSelected()));
		add(stretch(showToggle));
		add(Box.createVerticalStrut(4));
		gearButtons = new GearButtons(plugin, true);
		add(stretch(gearButtons));
		add(Box.createVerticalStrut(10));

		// Guided run: Start, then Skip / Stop while running, and each stop's progress.
		JPanel runButtons = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
		runButtons.setOpaque(false);
		startRun.setFocusable(false);
		skipStop.setFocusable(false);
		stopRun.setFocusable(false);
		startRun.setToolTipText("Guide the run step by step, like Quest Helper");
		skipStop.setToolTipText("Move on to the next patch");
		startRun.addActionListener(e -> plugin.startRun());
		skipStop.addActionListener(e -> plugin.skipStop());
		stopRun.addActionListener(e -> plugin.stopRun());
		runButtons.add(skipStop);
		runButtons.add(stopRun);
		add(stretch(startRun));
		add(stretch(runButtons));
		runRows.setLayout(new BoxLayout(runRows, BoxLayout.Y_AXIS));
		runRows.setOpaque(false);
		runRows.setAlignmentX(LEFT_ALIGNMENT);
		add(runRows);
		runButtonsPanel = runButtons;
		setRun(null);
		add(Box.createVerticalStrut(10));

		add(left(heading("Patches")));
		patchList.setLayout(new BoxLayout(patchList, BoxLayout.Y_AXIS));
		patchList.setOpaque(false);
		patchList.setAlignmentX(LEFT_ALIGNMENT);
		for (Patch p : Patch.values())
		{
			patchRows.put(p, stretch(patchRow(p)));
		}
		layoutPatches();
		add(patchList);
		add(Box.createVerticalStrut(8));

		add(left(heading("Supplies")));
		suppliesPanel.setLayout(new BoxLayout(suppliesPanel, BoxLayout.Y_AXIS));
		suppliesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(stretch(suppliesPanel));
		add(Box.createVerticalStrut(10));

		add(left(heading("To do")));
		stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.Y_AXIS));
		stepsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(stretch(stepsPanel));
		add(Box.createVerticalStrut(10));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(left(statusLabel));
	}

	void update(FarmPlan plan, FarmPlan.Settings settings, String status, boolean showingInBank, String[] harvest,
		Map<Integer, Integer> seedCounts, int farmingLevel)
	{
		this.seedCounts = seedCounts;
		if (dragging == null && !settings.getOrder().equals(viewOrder))
		{
			viewOrder = new java.util.ArrayList<>(settings.getOrder());
			layoutPatches();
		}
		herbsOnRun = 0;
		flowersOnRun = 0;
		allotmentsOnRun = 0;
		for (FarmPlan.Stop st : plan.getStops())
		{
			if (!st.isLocked() && !st.isGrowing())
			{
				herbsOnRun++;
				flowersOnRun += st.getPatch().isFlower() ? 1 : 0;
				allotmentsOnRun += st.getPatch().getAllotments();
			}
		}
		this.farmingLevel = farmingLevel;
		herb.repaint();
		flower.repaint();
		allotment.repaint();
		harvestBox.setVisible(harvest != null);
		if (harvest != null)
		{
			harvestLabel.setText(wrap(harvest[0]));
			harvestSub.setText(harvest[1] != null ? wrap(harvest[1]) : "");
			harvestSub.setVisible(harvest[1] != null);
		}
		updating = true;
		try
		{
			herb.setSelectedItem(settings.getHerb());
			flower.setSelectedItem(settings.getFlower());
			allotment.setSelectedItem(settings.getAllotment());
			showToggle.setSelected(showingInBank);
			for (Patch p : Patch.values())
			{
				patchBoxes.get(p).setSelected(settings.getPatches().contains(p));
				JLabel t = patchTeleports.get(p);
				t.setText(" ");
				t.setToolTipText(null);
				patchStates.get(p).setText(" ");
				patchStates.get(p).setToolTipText(null);
			}
		}
		finally
		{
			updating = false;
		}

		long now = System.currentTimeMillis() / 1000;
		for (FarmPlan.Stop stop : plan.getStops())
		{
			showState(patchStates.get(stop.getPatch()), stop, now);
			JLabel t = patchTeleports.get(stop.getPatch());
			if (stop.isLocked())
			{
				t.setText(stop.getLockReason());
				t.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				t.setToolTipText("Skipped until then");
				continue;
			}
			if (stop.getTeleport() == null)
			{
				continue;
			}
			t.setText(stop.getTeleport().getName() + (stop.isManual() ? "*" : ""));
			t.setForeground(stop.isOwned() ? Color.WHITE : BAD);
			t.setToolTipText("<html>" + (stop.isOwned() ? "" : stop.getProblem() + "<br>")
				+ (stop.getPatch().getTeleports().length > 1
				? "Click to change" + (stop.isManual() ? " (* chosen by you)" : " (best one you can use)")
				: "Only option") + "</html>");
		}

		suppliesPanel.removeAll();
		for (FarmPlan.Supply s : plan.getSupplies())
		{
			suppliesPanel.add(supplyRow(s));
			suppliesPanel.add(Box.createVerticalStrut(2));
		}

		stepsPanel.removeAll();
		for (PotionPlan.Step step : plan.getSteps())
		{
			boolean warn = step.getSeverity() == PotionPlan.Severity.WARNING;
			JLabel l = new JLabel(wrap((warn ? "! " : "") + step.getText()));
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(warn ? BAD : ACTION);
			l.setBorder(new EmptyBorder(2, 0, 2, 0));
			l.setToolTipText(step.getHint());
			stepsPanel.add(left(l));
		}
		if (plan.getSteps().isEmpty())
		{
			JLabel l = new JLabel("Ready to go");
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(GOOD);
			stepsPanel.add(left(l));
		}

		statusLabel.setText(wrap(status));
		revalidate();
		repaint();
	}

	/**
	 * @param rows "Stop|state|detail;detail" per stop (state Done / Now / empty), then one footer
	 *             note; null when no run
	 */
	void setRun(String[] rows)
	{
		boolean running = rows != null;
		startRun.setVisible(!running);
		runButtonsPanel.getParent().setVisible(running);
		runRows.removeAll();
		if (running)
		{
			for (int i = 0; i < rows.length - 1; i++)
			{
				String[] parts = rows[i].split("\\|", -1);
				boolean now = parts[1].equals("Now");
				JPanel r = new JPanel(new BorderLayout());
				r.setBackground(now ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
				r.setBorder(new EmptyBorder(2, 6, 2, 6));
				r.setAlignmentX(LEFT_ALIGNMENT);
				JLabel name = new JLabel(parts[0]);
				name.setFont(now ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
				name.setForeground(parts[1].equals("Done") ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE);
				JLabel state = new JLabel(parts[1]);
				state.setFont(FontManager.getRunescapeSmallFont());
				state.setForeground(parts[1].equals("Done") ? GOOD : ACTION);
				r.add(name, BorderLayout.WEST);
				r.add(state, BorderLayout.EAST);
				if (now && !parts[2].isEmpty())
				{
					// What to do: the teleport on the way, or each plot's steps at the patch.
					JPanel steps = new JPanel();
					steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
					steps.setOpaque(false);
					for (String line : parts[2].split(";"))
					{
						// "Herb: Pick" is a plot and its status; anything else is the way there.
						int colon = line.indexOf(": ");
						boolean plot = false;
						for (HerbRun.PlotType t : HerbRun.PlotType.values())
						{
							plot |= line.startsWith(t.getLabel() + ": ");
						}
						JPanel l = new JPanel(new BorderLayout());
						l.setOpaque(false);
						JLabel left = new JLabel(plot ? line.substring(0, colon) : wrap(line));
						left.setFont(FontManager.getRunescapeSmallFont());
						left.setForeground(plot ? ColorScheme.LIGHT_GRAY_COLOR : ACTION);
						l.add(left, BorderLayout.WEST);
						if (plot)
						{
							String status = line.substring(colon + 2);
							JLabel right = new JLabel(status);
							right.setFont(FontManager.getRunescapeSmallFont());
							right.setForeground(status.equals("Done") ? GOOD : ACTION);
							l.add(right, BorderLayout.EAST);
						}
						steps.add(l);
					}
					r.add(steps, BorderLayout.SOUTH);
				}
				r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
				runRows.add(r);
				runRows.add(Box.createVerticalStrut(1));
			}
			if (rows.length == 1)
			{
				JLabel l = new JLabel("Nothing ready to plant");
				l.setFont(FontManager.getRunescapeSmallFont());
				runRows.add(l);
			}
			String note = rows[rows.length - 1];
			if (!note.isEmpty())
			{
				JLabel l = new JLabel(wrap(note));
				l.setFont(FontManager.getRunescapeSmallFont());
				l.setForeground(note.startsWith("Run done") ? GOOD : ColorScheme.LIGHT_GRAY_COLOR);
				l.setBorder(new EmptyBorder(4, 0, 0, 0));
				runRows.add(l);
			}
		}
		revalidate();
		repaint();
	}

	/** Patch state from Time Tracking: ready, growing (with time left), diseased, dead or empty. */
	private static void showState(JLabel l, FarmPlan.Stop stop, long now)
	{
		PatchStatus st = stop.getStatus();
		if (st == null || stop.isLocked())
		{
			return;
		}
		switch (st.getState())
		{
			case READY:
				l.setText("Ready");
				l.setForeground(GOOD);
				break;
			case GROWING:
				l.setText(FarmPlan.minutes(st.getReadyAt() - now));
				l.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				l.setToolTipText("Still growing" + (stop.isGrowing() ? ", left out of this run" : ""));
				break;
			case DISEASED:
				l.setText("Diseased");
				l.setForeground(ACTION);
				break;
			case DEAD:
				l.setText("Dead");
				l.setForeground(BAD);
				break;
			case EMPTY:
				l.setText("Empty");
				l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				break;
			default:
				break;
		}
	}

	private void layoutPatches()
	{
		patchList.removeAll();
		for (Patch p : viewOrder)
		{
			patchList.add(patchRows.get(p));
			patchList.add(Box.createVerticalStrut(2));
		}
		patchList.revalidate();
		patchList.repaint();
	}

	/** Move a patch to a place in the run order (as shown), live while dragging. */
	private void movePatch(Patch p, int to)
	{
		int from = viewOrder.indexOf(p);
		to = Math.max(0, Math.min(viewOrder.size() - 1, to));
		if (from == to)
		{
			return;
		}
		viewOrder.remove(p);
		viewOrder.add(to, p);
		layoutPatches();
	}

	/** Six dots: the handle you drag a patch row by. */
	private static javax.swing.Icon grip()
	{
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(7, 11, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		for (int x = 0; x < 2; x++)
		{
			for (int y = 0; y < 3; y++)
			{
				g.fillRect(x * 4, y * 4, 3, 3);
			}
		}
		g.dispose();
		return new javax.swing.ImageIcon(img);
	}

	private JPanel patchRow(Patch p)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 2, 2, 6));

		JCheckBox box = new JCheckBox(p.getName());
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setOpaque(false);
		box.setFocusable(false);
		box.setToolTipText(describe(p));
		box.addActionListener(e ->
		{
			if (!updating)
			{
				plugin.setPatchEnabled(p, box.isSelected());
			}
		});
		patchBoxes.put(p, box);
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(box, BorderLayout.CENTER);
		JLabel handle = new JLabel(grip());
		handle.setBorder(new EmptyBorder(0, 0, 0, 2));
		handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		handle.setToolTipText("Drag to change the run order");
		MouseAdapter drag = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				dragging = p;
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				java.awt.Point at = javax.swing.SwingUtilities.convertPoint(handle, e.getPoint(), patchList);
				int to = viewOrder.size() - 1;
				for (int i = 0; i < viewOrder.size(); i++)
				{
					java.awt.Rectangle r = patchRows.get(viewOrder.get(i)).getBounds();
					if (at.y < r.y + r.height / 2)
					{
						to = i;
						break;
					}
				}
				movePatch(p, to);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				dragging = null;
				plugin.setPatchOrder(new java.util.ArrayList<>(viewOrder));
			}
		};
		handle.addMouseListener(drag);
		handle.addMouseMotionListener(drag);
		top.add(handle, BorderLayout.WEST);
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem up = new javax.swing.JMenuItem("Move up");
		up.addActionListener(e ->
		{
			movePatch(p, viewOrder.indexOf(p) - 1);
			plugin.setPatchOrder(new java.util.ArrayList<>(viewOrder));
		});
		javax.swing.JMenuItem down = new javax.swing.JMenuItem("Move down");
		down.addActionListener(e ->
		{
			movePatch(p, viewOrder.indexOf(p) + 1);
			plugin.setPatchOrder(new java.util.ArrayList<>(viewOrder));
		});
		menu.add(up);
		menu.add(down);
		box.setComponentPopupMenu(menu);
		handle.setComponentPopupMenu(menu);
		JLabel state = new JLabel(" ");
		state.setFont(FontManager.getRunescapeSmallFont());
		patchStates.put(p, state);
		top.add(state, BorderLayout.EAST);
		row.add(top, BorderLayout.NORTH);

		JLabel tele = new JLabel(" ");
		tele.setFont(FontManager.getRunescapeSmallFont());
		if (p.getTeleports().length > 1)
		{
			tele.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tele.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					plugin.cycleTeleport(p);
				}
			});
		}
		// Teleport names are long, so they go on their own line under the patch.
		tele.setHorizontalAlignment(SwingConstants.LEFT);
		tele.setBorder(new EmptyBorder(0, 31, 2, 0));
		patchTeleports.put(p, tele);
		row.add(tele, BorderLayout.SOUTH);
		return row;
	}

	private static String describe(Patch p)
	{
		StringBuilder sb = new StringBuilder("Herb");
		if (p.isFlower())
		{
			sb.append(", flower");
		}
		if (p.getAllotments() > 0)
		{
			sb.append(", ").append(p.getAllotments()).append(p.getAllotments() == 1 ? " allotment" : " allotments");
		}
		return sb.toString();
	}

	private JPanel supplyRow(FarmPlan.Supply s)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(new EmptyBorder(2, 4, 2, 8));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		itemManager.getImage(s.getItemId()).addTo(icon);
		p.add(icon, BorderLayout.WEST);

		JLabel name = new JLabel(s.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(s.getHave() > 0 ? Color.WHITE : ColorScheme.MEDIUM_GRAY_COLOR);
		p.add(name, BorderLayout.CENTER);

		boolean enough = s.getHave() >= s.getNeed();
		String text = s.getNeed() > 1
			? QuantityFormatter.quantityToStackSize(s.getHave()) + " / " + s.getNeed()
			: enough ? "Have" : "Missing";
		JLabel qty = new JLabel(text);
		qty.setFont(FontManager.getRunescapeSmallFont());
		qty.setForeground(enough ? GOOD : BAD);
		qty.setToolTipText(s.getNeed() > 1 ? "Have / need for this run" : null);
		p.add(qty, BorderLayout.EAST);
		return p;
	}

	private <T> JPanel combo(JComboBox<T> box, String tooltip, Runnable onChange)
	{
		box.setFocusable(false);
		box.setRenderer(new SeedRenderer());
		box.setToolTipText(tooltip);
		box.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 25));
		box.addActionListener(e ->
		{
			if (!updating)
			{
				onChange.run();
			}
		});
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(box, BorderLayout.CENTER);
		return p;
	}

	/** "Ranarr  918" with the count of seeds you have, or "Lv 85" in red if your Farming is too low. */
	private class SeedRenderer extends javax.swing.DefaultListCellRenderer
	{
		@Override
		public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
			boolean selected, boolean focused)
		{
			JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
			if (!(value instanceof Farm.Seed) || ((Farm.Seed) value).getSeedId() == -1)
			{
				return l;
			}
			Farm.Seed seed = (Farm.Seed) value;
			String right;
			String color;
			String tip = "Needs " + seed.getLevel() + " Farming";
			if (farmingLevel > 0 && farmingLevel < seed.getLevel())
			{
				right = "Lv " + seed.getLevel();
				color = "#E05A5A";
			}
			else
			{
				int n = seedCounts.getOrDefault(seed.getSeedId(), 0);
				right = QuantityFormatter.quantityToStackSize(n);
				// Red: not enough to plant one patch. Orange: not enough for every patch on the run.
				int per = seed instanceof Farm.AllotmentSeed ? Farm.SEEDS_PER_ALLOTMENT : 1;
				int need = seed instanceof Farm.AllotmentSeed ? allotmentsOnRun * Farm.SEEDS_PER_ALLOTMENT
					: seed instanceof Farm.FlowerSeed ? flowersOnRun : herbsOnRun;
				color = n == 0 ? "#808080" : n < per ? "#E05A5A" : n < need ? "#FFB000" : "#5FBF5F";
				if (need > 0)
				{
					tip += ". " + need + " for every patch on the run"
						+ (per > 1 ? ", " + per + " per allotment" : "");
				}
			}
			l.setText("<html>" + seed.getName() + "&nbsp;&nbsp;<font color='" + color + "'>" + right + "</font></html>");
			l.setToolTipText(tip);
			return l;
		}
	}

	private static JLabel heading(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return l;
	}

	private static String wrap(String text)
	{
		return "<html><body style='width:165px'>" + text + "</body></html>";
	}

	private static JPanel left(java.awt.Component c)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(c, BorderLayout.WEST);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 400));
		return p;
	}

	private static JPanel stretch(java.awt.Component c)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(c, BorderLayout.CENTER);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		return p;
	}
}
