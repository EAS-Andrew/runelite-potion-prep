package com.potionprep;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
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
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
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
	tags = {"herblore", "potion", "bank", "herb", "secondary"}
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
	private final Map<Integer, Integer> storageCounts = new HashMap<>();
	private final Map<Integer, Integer> invCounts = new HashMap<>();
	private int herbloreLevel;

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
		storageCounts.clear();
		invCounts.clear();
	}

	// --- Called from the panel (EDT) ---

	void selectFromPanel(Potion potion)
	{
		clientThread.invokeLater(() -> select(potion, true));
	}

	void showInBank()
	{
		clientThread.invokeLater(() ->
		{
			if (isBankOpen())
			{
				openTag();
			}
			else
			{
				pushToPanel("Open your bank first");
			}
		});
	}

	void closeInBank()
	{
		clientThread.invokeLater(() ->
		{
			if (TAG.equals(bankTagsService.getActiveTag()))
			{
				bankTagsService.closeBankTag();
			}
		});
	}

	// --- Client thread ---

	private void select(Potion potion, boolean persist)
	{
		selected = potion;
		trackedIds.clear();
		trackedIds.addAll(PotionPlan.layoutItems(potion));
		if (persist)
		{
			configManager.setConfiguration(PotionPrepConfig.GROUP, "potion", potion.name());
		}
		saveLayout();
		if (TAG.equals(bankTagsService.getActiveTag()) && isBankOpen())
		{
			openTag();
		}
		pushToPanel(null);
	}

	private void saveLayout()
	{
		if (selected == null)
		{
			return;
		}
		int col = Math.max(1, Math.min(BANK_COLUMNS, config.column())) - 1;
		List<int[]> rows = PotionPlan.layoutRows(selected);
		Layout layout = new Layout(TAG);
		for (int row = 0; row < rows.size(); row++)
		{
			int[] ids = rows.get(row);
			// Each action row ends at the chosen column and runs leftwards,
			// or rightwards when there isn't room on the left.
			int start = Math.max(0, Math.min(col - (ids.length - 1), BANK_COLUMNS - ids.length));
			for (int i = 0; i < ids.length; i++)
			{
				layout.setItemAtPos(ids[i], row * BANK_COLUMNS + start + i);
			}
		}
		layoutManager.saveLayout(layout);
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

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && config.autoOpen() && selected != null)
		{
			// Let the bank finish building before switching tab.
			clientThread.invokeLater(this::openTag);
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING || !TAG.equals(bankTagsService.getActiveTag()))
		{
			return;
		}

		// Unf potions and vials of water can sit in potion storage, which is
		// not part of the bank item container. The Bank Tags layout draws
		// them with their real quantity, so read it back off the widgets.
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (items == null)
		{
			return;
		}
		Map<Integer, Integer> seen = new HashMap<>();
		Widget[] children = items.getChildren();
		if (children != null)
		{
			for (Widget c : children)
			{
				// Layout placeholders (items you don't have) are drawn faded with
				// quantity Integer.MAX_VALUE, so skip anything faded.
				if (c == null || c.isHidden() || c.getOpacity() > 0 || c.getItemQuantity() == Integer.MAX_VALUE
					|| !trackedIds.contains(c.getItemId()))
				{
					continue;
				}
				seen.merge(c.getItemId(), c.getItemQuantity(), Math::max);
			}
		}
		// The layout shows the bank quantity when there is one, otherwise the
		// potion storage quantity. Keep only the storage-only ones so they can
		// be added to the bank counts without double counting.
		storageCounts.clear();
		seen.forEach((id, qty) ->
		{
			if (qty > 0 && (bankCounts == null || !bankCounts.containsKey(id)))
			{
				storageCounts.put(id, qty);
			}
		});
		pushToPanel(null);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK)
		{
			readBank(event.getItemContainer());
			pushToPanel(null);
		}
		else if (event.getContainerId() == InventoryID.INV)
		{
			readInventory(event.getItemContainer());
			pushToPanel(null);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.HERBLORE && event.getLevel() != herbloreLevel)
		{
			herbloreLevel = event.getLevel();
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
				storageCounts.clear();
				herbloreLevel = 0;
			}
			invCounts.clear();
			pushToPanel(null);
		}
		else if (event.getGameState() == GameState.LOGGED_IN && herbloreLevel == 0)
		{
			clientThread.invokeLater(() ->
			{
				herbloreLevel = client.getRealSkillLevel(Skill.HERBLORE);
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
		PotionPlan plan = PotionPlan.compute(selected, counts, herbloreLevel);
		Map<Potion, Integer> makeable = new EnumMap<>(Potion.class);
		for (Potion p : Potion.values())
		{
			makeable.put(p, PotionPlan.makeable(p, counts, herbloreLevel));
		}
		int lvl = herbloreLevel;
		String status = notice != null ? notice
			: bankCounts == null ? "Open your bank to scan it"
			: "Bank seen " + ago(bankObservedAt);
		PotionPrepPanel p = panel;
		SwingUtilities.invokeLater(() -> p.update(plan, status, makeable, lvl));
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
