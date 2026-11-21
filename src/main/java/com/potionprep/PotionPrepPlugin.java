package com.potionprep;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Filters the bank to one potion's ingredients and lines them up in a single
 * column, using the core Bank Tags plugin's tag + layout API. Nothing is
 * moved server-side: the layout is purely how the client draws the bank.
 */
@Slf4j
@PluginDescriptor(
	name = "Potion Prep",
	description = "Filter the bank to a potion's ingredients and see what you need to clean, crush or gather",
	tags = {"herblore", "herb", "herbs", "potion", "potions", "pots", "bank", "banktags", "layout", "tab", "unf", "unfinished", "secondary", "secondaries", "grimy", "clean", "crush", "prayer", "stamina", "brew", "antifire", "antivenom", "combat", "skilling", "planner", "calculator", "ironman"}
)
@PluginDependency(BankTagsPlugin.class)
public class PotionPrepPlugin extends Plugin
{
	static final String TAG = "potion prep";
	private static final int BANK_COLUMNS = 8;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ConfigManager configManager;
	@Inject private ItemManager itemManager;
	@Inject private PotionPrepConfig config;
	@Inject private TagManager tagManager;
	@Inject private LayoutManager layoutManager;
	@Inject private BankTagsService bankTagsService;

	private PotionPrepPanel panel;
	private NavigationButton navButton;

	private Potion selected;
	private final Set<Integer> trackedIds = new HashSet<>();

	// Last observed bank. Null means "not seen this session", which is different from empty.
	private Map<Integer, Integer> bankCounts;
	private long bankObservedAt;
	// Potion storage, read directly (see PotionStorageReader). Keyed by
	// 1-dose item id. Kept after the bank closes, like the bank snapshot.
	private Map<Integer, Integer> storageCounts = new HashMap<>();
	private boolean readStorage;
	private Set<Integer> storageVarps;
	// Dose rows collapsed to one slot because that potion is only in storage.
	private Set<Integer> collapsed = new HashSet<>();

	// The filter only applies while the panel is open and the toggle is on.
	private volatile boolean panelOpen;
	// Our tab has been shown during this bank visit, so a later bank build
	// without it means the player clicked away.
	private boolean shownThisVisit;
	private final Map<Integer, Integer> invCounts = new HashMap<>();
	private int herbloreLevel;   // real
	private int boostedLevel;
	// Whether the current layout includes the botanical pie row.
	private boolean layoutHasPie;
	private final Set<Integer> wornIds = new HashSet<>();
	/** Real item ids that have a bank placeholder. */
	private final Set<Integer> bankPlaceholders = new HashSet<>();
	private List<int[]> gearRows = new ArrayList<>();

	@Provides
	PotionPrepConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PotionPrepConfig.class);
	}

	@Override
	protected void startUp()
	{
		tagManager.registerTag(TAG, itemId -> trackedIds.contains(itemId));

		panel = new PotionPrepPanel(this, itemManager);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Potion Prep")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		Potion saved = parse(config.potion());
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				herbloreLevel = client.getRealSkillLevel(Skill.HERBLORE);
				boostedLevel = client.getBoostedSkillLevel(Skill.HERBLORE);
				readInventory(client.getItemContainer(InventoryID.INV));
				readBank(client.getItemContainer(InventoryID.BANK));
			}
			select(saved != null ? saved : Potion.PRAYER, false);
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(() ->
		{
			if (TAG.equals(bankTagsService.getActiveTag()))
			{
				bankTagsService.closeBankTag();
			}
			tagManager.unregisterTag(TAG);
			layoutManager.removeLayout(TAG);
		});
		clientToolbar.removeNavigation(navButton);
		panel = null;
		selected = null;
		bankCounts = null;
		storageCounts = new HashMap<>();
		collapsed = new HashSet<>();
		invCounts.clear();
	}

	// --- Called from the panel (EDT) ---

	void selectFromPanel(Potion potion)
	{
		clientThread.invokeLater(() -> select(potion, true));
	}

	/**
	 * The panel's "Show in bank" toggle. While on, every bank opened shows
	 * the selected potion's layout; turning it off restores the normal bank.
	 */
	void setShowInBank(boolean on)
	{
		configManager.setConfiguration(PotionPrepConfig.GROUP, "showInBank", on);
		clientThread.invokeLater(() ->
		{
			applyFilter();
			pushToPanel(null);
		});
	}

	/** The panel was shown or hidden in the sidebar. */
	void setPanelOpen(boolean open)
	{
		panelOpen = open;
		clientThread.invokeLater(this::applyFilter);
	}

	// --- Client thread ---

	private boolean pieNeeded()
	{
		return selected != null && PotionPlan.needsPie(selected, herbloreLevel, boostedLevel);
	}

	private boolean shouldFilter()
	{
		return config.showInBank() && panelOpen && selected != null;
	}

	/** Open or close our tab to match {@link #shouldFilter()}, if the bank is open. */
	private void applyFilter()
	{
		if (!isBankOpen())
		{
			return;
		}
		if (shouldFilter())
		{
			openTag();
		}
		else
		{
			closeOurTag();
		}
	}

	private void closeOurTag()
	{
		if (TAG.equals(bankTagsService.getActiveTag()))
		{
			// We are closing it, so the next bank build must not read as
			// "the player clicked away".
			shownThisVisit = false;
			bankTagsService.closeBankTag();
		}
	}

	private void select(Potion potion, boolean persist)
	{
		selected = potion;
		trackedIds.clear();
		trackedIds.addAll(PotionPlan.layoutItems(potion));
		layoutHasPie = pieNeeded();
		if (layoutHasPie)
		{
			Arrays.stream(PotionPlan.PIE_IDS).forEach(trackedIds::add);
		}
		if (persist)
		{
			configManager.setConfiguration(PotionPrepConfig.GROUP, "potion", potion.name());
		}
		collapsed = computeCollapsed();
		gearRows = gearRows();
		trackGear();
		saveLayout();
		if (TAG.equals(bankTagsService.getActiveTag()) && isBankOpen())
		{
			openTag();
		}
		pushToPanel(null);
	}

	private List<int[]> gearRows()
	{
		return Gear.rows(
			id -> bankCounts != null && bankCounts.getOrDefault(id, 0) > 0,
			id -> wornIds.contains(id) || invCounts.containsKey(id) || bankPlaceholders.contains(id));
	}

	private void trackGear()
	{
		for (int[] row : gearRows)
		{
			for (int id : row)
			{
				if (id != -1)
				{
					trackedIds.add(id);
				}
			}
		}
	}

	/** Recompute which gear to show; re-save and reopen the layout if it changed. */
	private void refreshGear()
	{
		if (selected == null)
		{
			return;
		}
		List<int[]> now = gearRows();
		if (sameRows(now, gearRows))
		{
			return;
		}
		gearRows = now;
		trackedIds.removeAll(Gear.allIds());
		trackGear();
		saveLayout();
		if (TAG.equals(bankTagsService.getActiveTag()) && isBankOpen())
		{
			openTag();
		}
	}

	private static boolean sameRows(List<int[]> a, List<int[]> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			if (!Arrays.equals(a.get(i), b.get(i)))
			{
				return false;
			}
		}
		return true;
	}

	private void saveLayout()
	{
		if (selected == null)
		{
			return;
		}
		int col = Math.max(1, Math.min(BANK_COLUMNS, config.column())) - 1;
		List<int[]> rows = new ArrayList<>(PotionPlan.layoutRows(selected));
		if (layoutHasPie)
		{
			// Eating the pie is the first task.
			rows.add(0, PotionPlan.PIE_IDS);
		}
		Layout layout = new Layout(TAG);
		List<boolean[]> occupied = new ArrayList<>();
		for (int row = 0; row < rows.size(); row++)
		{
			int[] ids = collapse(rows.get(row));
			// Each action row ends at the chosen column and runs leftwards,
			// or rightwards when there isn't room on the left.
			int start = Math.max(0, Math.min(col - (ids.length - 1), BANK_COLUMNS - ids.length));
			boolean[] used = new boolean[BANK_COLUMNS];
			for (int i = 0; i < ids.length; i++)
			{
				layout.setItemAtPos(ids[i], row * BANK_COLUMNS + start + i);
				used[start + i] = true;
			}
			occupied.add(used);
		}
		// Herblore gear sits on the left like the equipment tab, below any
		// rows too wide to leave room for it.
		int g = Gear.startRow(occupied, gearRows);
		for (int r = 0; r < gearRows.size(); r++)
		{
			for (int c = 0; c < Gear.WIDTH; c++)
			{
				int id = gearRows.get(r)[c];
				if (id != -1)
				{
					layout.setItemAtPos(id, (g + r) * BANK_COLUMNS + c);
				}
			}
		}
		layoutManager.saveLayout(layout);
	}

	/**
	 * Bank Tags matches every dose slot of a potion that is only in potion
	 * storage to the same stored potion, so (1)-(4) would show four copies.
	 * For those potions keep just the 4-dose slot.
	 */
	private int[] collapse(int[] ids)
	{
		if (collapsed.isEmpty())
		{
			return ids;
		}
		return Arrays.stream(ids).filter(id -> !collapsed.contains(id)).toArray();
	}

	/** 1-3 dose ids of potions in this layout that are in storage but not the bank. */
	private Set<Integer> computeCollapsed()
	{
		Set<Integer> out = new HashSet<>();
		if (selected == null)
		{
			return out;
		}
		for (Node n : PotionPlan.ingredients(selected))
		{
			if (!n.isDosed() || storageCounts.getOrDefault(n.getDoseIds()[0], 0) <= 0)
			{
				continue;
			}
			boolean inBank = false;
			for (int id : n.getDoseIds())
			{
				inBank |= bankCounts != null && bankCounts.containsKey(id);
			}
			if (!inBank)
			{
				for (int d = 0; d < 3; d++)
				{
					out.add(n.getDoseIds()[d]);
				}
			}
		}
		return out;
	}

	/** Recompute storage-only collapsing; re-save and reopen the layout if it changed. */
	private void refreshCollapsed()
	{
		Set<Integer> now = computeCollapsed();
		if (!now.equals(collapsed))
		{
			collapsed = now;
			saveLayout();
			if (TAG.equals(bankTagsService.getActiveTag()) && isBankOpen())
			{
				openTag();
			}
		}
	}

	private void openTag()
	{
		// No OPTION_ALLOW_MODIFICATIONS: dragging items in this view must not
		// rewrite the layout we generate.
		bankTagsService.openBankTag(TAG, 0);
	}

	private boolean isBankOpen()
	{
		Widget w = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return w != null && !w.isHidden();
	}

	// Runs after Bank Tags (priority -1), the same hook Inventory Setups uses.
	@Subscribe(priority = -1)
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != ScriptID.BANKMAIN_INIT)
		{
			return;
		}
		shownThisVisit = false;
		if (shouldFilter())
		{
			openTag();
		}
		else if (TAG.equals(bankTagsService.getActiveTag()))
		{
			// Bank Tags remembers the last tab; don't come back to ours
			// unless the filter is on.
			bankTagsService.closeBankTag();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}
		readStorage = true;

		boolean ours = TAG.equals(bankTagsService.getActiveTag());
		if (ours)
		{
			shownThisVisit = true;
		}
		else if (shownThisVisit && config.showInBank())
		{
			// The player clicked another tab (or Bank Tags' close) while our
			// view was up: treat that as turning the filter off.
			shownThisVisit = false;
			configManager.setConfiguration(PotionPrepConfig.GROUP, "showInBank", false);
			pushToPanel(null);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varp = event.getVarpId();
		if (varp == VarPlayerID.POTIONSTORE_BASE_VAR_1 || varp == VarPlayerID.POTIONSTORE_BASE_VAR_2
			|| varp == VarPlayerID.POTIONSTORE_BASE_VAR_3 || varp == VarPlayerID.POTIONSTORE_BASE_VAR_4
			|| varp == VarPlayerID.POTIONSTORE_BASE_VAR_5 || varp == VarPlayerID.POTIONSTORE_BASE_VAR_6
			|| storageVarps != null && storageVarps.contains(varp))
		{
			readStorage = true;
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		// Scripts can't run from inside another script, so storage is read
		// here, on the tick after the bank builds or storage changes.
		if (!readStorage || !isBankOpen())
		{
			return;
		}
		readStorage = false;

		if (storageVarps == null)
		{
			Widget w = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
			int[] trigger = w != null ? w.getVarTransmitTrigger() : null;
			if (trigger != null)
			{
				storageVarps = new HashSet<>();
				Arrays.stream(trigger).forEach(storageVarps::add);
			}
		}

		Map<Integer, Integer> now = PotionStorageReader.read(client);
		if (!now.equals(storageCounts))
		{
			storageCounts = now;
			refreshCollapsed();
			pushToPanel(null);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			shownThisVisit = false;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK)
		{
			readBank(event.getItemContainer());
			refreshCollapsed();
			refreshGear();
			pushToPanel(null);
		}
		else if (event.getContainerId() == InventoryID.WORN)
		{
			wornIds.clear();
			wornIds.addAll(count(event.getItemContainer()).keySet());
			refreshGear();
		}
		else if (event.getContainerId() == InventoryID.INV)
		{
			readInventory(event.getItemContainer());
			refreshGear();
			pushToPanel(null);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.HERBLORE
			&& (event.getLevel() != herbloreLevel || event.getBoostedLevel() != boostedLevel))
		{
			herbloreLevel = event.getLevel();
			boostedLevel = event.getBoostedLevel();
			// Eating a pie (or it wearing off) changes whether the pie row is needed.
			if (selected != null && pieNeeded() != layoutHasPie)
			{
				select(selected, false);
			}
			pushToPanel(null);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			if (event.getGameState() == GameState.LOGIN_SCREEN)
			{
				// Different account may log in next.
				bankCounts = null;
				storageCounts = new HashMap<>();
				collapsed = new HashSet<>();
				wornIds.clear();
				bankPlaceholders.clear();
				herbloreLevel = 0;
				boostedLevel = 0;
			}
			invCounts.clear();
			pushToPanel(null);
		}
		else if (event.getGameState() == GameState.LOGGED_IN && herbloreLevel == 0)
		{
			clientThread.invokeLater(() ->
			{
				herbloreLevel = client.getRealSkillLevel(Skill.HERBLORE);
				boostedLevel = client.getBoostedSkillLevel(Skill.HERBLORE);
				if (selected != null && pieNeeded() != layoutHasPie)
				{
					select(selected, false);
				}
				pushToPanel(null);
			});
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (PotionPrepConfig.GROUP.equals(event.getGroup()) && "column".equals(event.getKey()))
		{
			clientThread.invokeLater(() ->
			{
				saveLayout();
				if (TAG.equals(bankTagsService.getActiveTag()) && isBankOpen())
				{
					openTag();
				}
			});
		}
	}

	private void readBank(ItemContainer container)
	{
		if (container == null)
		{
			return;
		}
		bankCounts = count(container);
		bankPlaceholders.clear();
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() == 0)
			{
				ItemComposition c = itemManager.getItemComposition(item.getId());
				if (c.getPlaceholderTemplateId() != -1)
				{
					bankPlaceholders.add(c.getPlaceholderId());
				}
			}
		}
		bankObservedAt = System.currentTimeMillis();
	}

	private void readInventory(ItemContainer container)
	{
		invCounts.clear();
		if (container != null)
		{
			invCounts.putAll(count(container));
		}
	}

	private static Map<Integer, Integer> count(ItemContainer container)
	{
		Map<Integer, Integer> out = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				out.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return out;
	}

	private Map<Integer, Integer> combinedCounts()
	{
		Map<Integer, Integer> all = new HashMap<>();
		if (bankCounts != null)
		{
			all.putAll(bankCounts);
		}
		storageCounts.forEach((id, qty) -> all.merge(id, qty, Integer::sum));
		invCounts.forEach((id, qty) -> all.merge(id, qty, Integer::sum));
		return all;
	}

	private void pushToPanel(String notice)
	{
		if (panel == null || selected == null)
		{
			return;
		}
		Map<Integer, Integer> counts = combinedCounts();
		int boosted = boostedLevel > 0 ? boostedLevel : herbloreLevel;
		PotionPlan plan = PotionPlan.compute(selected, counts,
			PotionPlan.effectiveLevel(selected, herbloreLevel, boosted), PotionPlan.needsPie(selected, herbloreLevel, boosted));
		Map<Potion, Integer> makeable = new EnumMap<>(Potion.class);
		Map<Potion, Double> xp = new EnumMap<>(Potion.class);
		Set<Potion> pie = EnumSet.noneOf(Potion.class);
		for (Potion p : Potion.values())
		{
			int eff = PotionPlan.effectiveLevel(p, herbloreLevel, boosted);
			makeable.put(p, PotionPlan.makeable(p, counts, eff));
			xp.put(p, PotionPlan.xp(p, counts, eff));
			if (PotionPlan.needsPie(p, herbloreLevel, boosted))
			{
				pie.add(p);
			}
		}
		int lvl = boosted;
		String status = notice != null ? notice
			: bankCounts == null ? "Open your bank to scan it"
			: "Bank seen " + ago(bankObservedAt);
		PotionPrepPanel p = panel;
		boolean showing = config.showInBank();
		SwingUtilities.invokeLater(() -> p.update(plan, status, makeable, xp, lvl, pie, showing));
	}

	private static String ago(long at)
	{
		long s = (System.currentTimeMillis() - at) / 1000;
		if (s < 60)
		{
			return "just now";
		}
		long m = s / 60;
		return m < 60 ? m + " min ago" : (m / 60) + " h ago";
	}

	private static Potion parse(String name)
	{
		try
		{
			return name == null || name.isEmpty() ? null : Potion.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}
}
