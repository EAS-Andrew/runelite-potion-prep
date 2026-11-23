package com.potionprep;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;
import java.util.Set;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JToggleButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.QuantityFormatter;

class PotionPrepPanel extends PluginPanel
{
	private static final Color GOOD = new Color(0x5FBF5F);
	private static final Color ACTION = new Color(0xFFB000);
	private static final Color BAD = new Color(0xE05A5A);

	private final PotionPrepPlugin plugin;
	private final ItemManager itemManager;

	private final IconTextField search = new IconTextField();
	private final PotionGrid grid;
	private final JLabel selectedLabel = new JLabel();
	private final JLabel makeableLabel = new JLabel();
	private final JLabel makeableSub = new JLabel();
	private final JPanel rowsPanel = new JPanel();
	private final JPanel stepsPanel = new JPanel();
	private final JLabel statusLabel = new JLabel();
	private final JToggleButton showToggle = new JToggleButton("Show in bank");
	private final JToggleButton potionsMode = new JToggleButton("Potions");
	private final JToggleButton farmMode = new JToggleButton("Herb run");
	private final FarmView farmView;
	private final GearButtons potionGear;
	private final JPanel potionsView;
	private final Folds potionFolds;
	private final Folds.Section potionSection;
	private final Folds.Section ingredientsSection;
	private final Folds.Section stepsSection;
	private final Folds.Section gearSection;

	PotionPrepPanel(PotionPrepPlugin plugin, ItemManager itemManager)
	{
		// Wrapped: RuneLite puts the panel in a vertical scroll pane at the
		// standard sidebar size, so a long panel scrolls instead of forcing
		// the client window taller.
		super(true);
		this.plugin = plugin;
		this.itemManager = itemManager;
		// Picking a potion folds the grid away: it's the biggest thing in the panel.
		this.grid = new PotionGrid(itemManager, p ->
		{
			plugin.selectFromPanel(p);
			foldGrid();
		});

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		potionFolds = new Folds(plugin, content, "potionOpen", "ingredients,todo", null, "");
		potionSection = potionFolds.add("potion", "Potion");
		potionSection.header.setToolTipText("Click to pick a different potion");

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.setToolTipText("Search potions");
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				grid.setFilter(search.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				grid.setFilter(search.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				grid.setFilter(search.getText());
			}
		});
		search.addActionListener(e ->
		{
			Potion first = grid.firstVisible();
			if (first != null)
			{
				plugin.selectFromPanel(first);
				potionFolds.setOpen("potion", false);
			}
		});
		search.addClearListener(() -> grid.setFilter(""));
		potionSection.body.add(stretch(search));
		potionSection.body.add(Box.createVerticalStrut(6));
		potionSection.body.add(stretch(grid));
		potionSection.body.add(Box.createVerticalStrut(6));
		content.add(Box.createVerticalStrut(6));

		JPanel summary = new JPanel();
		summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
		summary.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		summary.setBorder(new EmptyBorder(6, 8, 6, 8));
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		selectedLabel.setFont(FontManager.getRunescapeBoldFont());
		selectedLabel.setForeground(Color.WHITE);
		makeableLabel.setFont(FontManager.getRunescapeBoldFont());
		top.add(selectedLabel, BorderLayout.CENTER);
		top.add(makeableLabel, BorderLayout.EAST);
		top.setAlignmentX(LEFT_ALIGNMENT);
		summary.add(top);
		makeableSub.setFont(FontManager.getRunescapeSmallFont());
		makeableSub.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		summary.add(left(makeableSub));
		content.add(stretch(summary));
		content.add(Box.createVerticalStrut(6));

		showToggle.setFocusable(false);
		showToggle.setToolTipText("While on, the bank opens filtered to this potion");
		showToggle.addActionListener(e -> plugin.setShowInBank(showToggle.isSelected()));
		content.add(stretch(showToggle));
		content.add(Box.createVerticalStrut(10));

		ingredientsSection = potionFolds.add("ingredients", "Ingredients");
		rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
		rowsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Added as is, not stretch(): its rows change, so its height must too.
		rowsPanel.setAlignmentX(LEFT_ALIGNMENT);
		ingredientsSection.body.add(rowsPanel);
		ingredientsSection.body.add(Box.createVerticalStrut(10));

		stepsSection = potionFolds.add("todo", "To do");
		stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.Y_AXIS));
		stepsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Added as is, not stretch(): its rows change, so its height must too.
		stepsPanel.setAlignmentX(LEFT_ALIGNMENT);
		stepsSection.body.add(stepsPanel);
		stepsSection.body.add(Box.createVerticalStrut(10));

		gearSection = potionFolds.add("gear", "Gear");
		potionGear = new GearButtons(plugin, false);
		gearSection.body.add(stretch(potionGear));
		gearSection.body.add(Box.createVerticalStrut(6));
		potionFolds.apply();

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.add(left(statusLabel));

		farmView = new FarmView(plugin, itemManager);
		potionsView = content;

		JPanel modes = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
		modes.setOpaque(false);
		potionsMode.setFocusable(false);
		farmMode.setFocusable(false);
		javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
		group.add(potionsMode);
		group.add(farmMode);
		potionsMode.setSelected(true);
		potionsMode.addActionListener(e -> plugin.setFarmMode(false));
		farmMode.addActionListener(e -> plugin.setFarmMode(true));
		modes.add(potionsMode);
		modes.add(farmMode);

		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(ColorScheme.DARK_GRAY_COLOR);
		root.add(stretch(modes));
		root.add(Box.createVerticalStrut(8));
		root.add(potionsView);
		root.add(farmView);
		farmView.setVisible(false);
		potionsView.setAlignmentX(LEFT_ALIGNMENT);
		farmView.setAlignmentX(LEFT_ALIGNMENT);
		add(root, BorderLayout.NORTH);
	}

	void setRun(String[] rows)
	{
		farmView.setRun(rows);
	}

	void setGearSaved(boolean potions, boolean farm)
	{
		potionGear.setSaved(potions);
		gearSection.summary.setText(potions ? "saved" : "not saved");
		farmView.setGearSaved(farm);
	}

	void updateFarm(FarmPlan plan, FarmPlan.Settings settings, String status, boolean showingInBank, boolean farm, String[] harvest,
		Map<Integer, Integer> seedCounts, int farmingLevel)
	{
		potionsMode.setSelected(!farm);
		farmMode.setSelected(farm);
		potionsView.setVisible(!farm);
		farmView.setVisible(farm);
		farmView.update(plan, settings, status, showingInBank, harvest, seedCounts, farmingLevel);
	}

	void update(PotionPlan plan, String status, Map<Potion, Integer> makeable, Map<Potion, Double> xp, int boostedLevel, Set<Potion> pie, boolean showingInBank)
	{
		showToggle.setSelected(showingInBank);
		grid.setSelected(plan.getPotion());
		grid.setStats(makeable, xp, boostedLevel, pie);
		Potion potion = plan.getPotion();
		// Counts are whole potions; made-a-dose-at-a-time potions keep their base's dose.
		int n = plan.getMakeable();
		int now = plan.getMakeableNow();
		// Long names (Prayer regeneration potion) wrap rather than push the count off.
		selectedLabel.setText("<html><body style='width:120px'>" + plan.getLabel() + "</body></html>");
		selectedLabel.setToolTipText("Level " + potion.getLevel());
		makeableLabel.setText(QuantityFormatter.formatNumber(n));
		makeableLabel.setForeground(n > 0 ? GOOD : BAD);
		makeableLabel.setToolTipText(plan.getDoseMix() != null ? "<html>Potions you can make<br>" + plan.getDoseMix() + "</html>" : "Potions you can make");
		makeableSub.setText(n == 0 ? "None makeable"
			: now == n ? "All ready"
			: QuantityFormatter.formatNumber(now) + " ready &middot; +" + QuantityFormatter.formatNumber(n - now) + " after prep");
		makeableSub.setText(wrap(makeableSub.getText()));

		rowsPanel.removeAll();
		for (PotionPlan.Row row : plan.getRows())
		{
			rowsPanel.add(ingredientRow(row));
			rowsPanel.add(Box.createVerticalStrut(2));
		}

		stepsPanel.removeAll();
		for (PotionPlan.Step step : plan.getSteps())
		{
			JLabel l = new JLabel(wrap(bullet(step.getSeverity()) + step.getText()));
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(color(step.getSeverity()));
			l.setBorder(new EmptyBorder(2, 0, 2, 0));
			l.setToolTipText(step.getHint());
			stepsPanel.add(left(l));
		}

		potionSection.summary.setText(potion.getName());
		potionSection.summary.setForeground(Color.WHITE);
		ingredientsSection.summary.setText(plan.getRows().size() + (plan.getRows().size() == 1 ? " item" : " items"));
		int warnings = 0;
		for (PotionPlan.Step step : plan.getSteps())
		{
			warnings += step.getSeverity() == PotionPlan.Severity.WARNING ? 1 : 0;
		}
		stepsSection.summary.setText(warnings > 0 ? warnings + (warnings == 1 ? " warning" : " warnings")
			: plan.getSteps().isEmpty() ? "ready" : plan.getSteps().size() + " to do");
		stepsSection.summary.setForeground(warnings > 0 ? BAD : plan.getSteps().isEmpty() ? GOOD : ACTION);

		statusLabel.setText(wrap(status));
		revalidate();
		repaint();
	}

	private JPanel ingredientRow(PotionPlan.Row row)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(new EmptyBorder(2, 4, 2, 8));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		itemManager.getImage(row.getItemId()).addTo(icon);
		p.add(icon, BorderLayout.WEST);

		JLabel name = new JLabel(row.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(row.getHave() > 0 ? Color.WHITE : ColorScheme.MEDIUM_GRAY_COLOR);
		p.add(name, BorderLayout.CENTER);

		JLabel qty = new JLabel(QuantityFormatter.quantityToStackSize(row.getHave()) + (row.isDoses() ? " doses" : ""));
		if (row.isDoses())
		{
			qty.setToolTipText("All doses (1) to (4) combined");
		}
		qty.setFont(FontManager.getRunescapeSmallFont());
		qty.setForeground(row.getHave() > 0 ? GOOD : ColorScheme.MEDIUM_GRAY_COLOR);
		p.add(qty, BorderLayout.EAST);
		return p;
	}

	private void foldGrid()
	{
		potionFolds.setOpen("potion", false);
	}

	private static String bullet(PotionPlan.Severity s)
	{
		switch (s)
		{
			case WARNING:
				// Plain ASCII: the RuneScape font has no bullet glyphs.
				return "! ";
			default:
				return "";
		}
	}

	private static Color color(PotionPlan.Severity s)
	{
		switch (s)
		{
			case WARNING:
				return BAD;
			default:
				return ACTION;
		}
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
		return p;
	}

	@Override
	public void onActivate()
	{
		plugin.setPanelOpen(true);
	}

	@Override
	public void onDeactivate()
	{
		plugin.setPanelOpen(false);
	}
}
