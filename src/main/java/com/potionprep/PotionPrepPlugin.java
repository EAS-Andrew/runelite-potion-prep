package com.potionprep;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Collections;
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
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.ObjectID;
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
	/** What's in the seed box; counted as carried while the box is in the inventory. */
	private Map<Integer, Integer> seedBox = new HashMap<>();
	private int herbloreLevel;   // real
	private int boostedLevel;
	// Whether the current layout includes the botanical pie row.
	private boolean layoutHasPie;
	private final Set<Integer> wornIds = new HashSet<>();
	/** Real item ids that have a bank placeholder. */
	private final Set<Integer> bankPlaceholders = new HashSet<>();
	private List<int[]> gearRows = new ArrayList<>();
	private int farmingLevel;
	private FarmPlan farmPlan;
	private boolean farmDirty;
	/** Last layout saved, slot by slot, so unchanged layouts aren't re-saved. */
	private int[] lastLayout;

	static final String MODE_FARM = "FARM";
	private static final int[] FARM_VARBITS = java.util.stream.IntStream.concat(
		Arrays.stream(ToolLeprechaun.VARBITS), Arrays.stream(AccountReader.VARBITS)).toArray();
	/** Finished quests, or null until read. */
	private Set<net.runelite.api.Quest> quests;
	private boolean questsDirty;
	/** Seed vault contents, from the last time it was opened. */
	private Map<Integer, Integer> seedVault = new HashMap<>();
	/** How the player last entered a house: true own, false someone else's, null unknown. */
	private Boolean ownHouseEntry;
	private Boolean fairyRings;

	@Provides
	PotionPrepConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PotionPrepConfig.class);
	}

	@Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
	@Inject private WithdrawOverlay withdrawOverlay;
	@Inject private RunItemOverlay runItemOverlay;
	@Inject private TeleportOverlay teleportOverlay;
	/** The option to colour in an open teleport menu, or null. */
	private String menuOption;
	private PathOverlay scenePath;
	private PathOverlay minimapPath;
	private NavOverlay navOverlay;
	@Inject private net.runelite.client.game.SpriteManager spriteManager;
	@Inject private net.runelite.client.ui.overlay.worldmap.WorldMapPointManager worldMapPoints;
	private net.runelite.client.ui.overlay.worldmap.WorldMapPoint mapMarker;
	/** Herb patch objects in the loaded scene, to walk right up to them. */
	private final Map<net.runelite.api.GameObject, HerbRun.PlotType> herbPatches = new HashMap<>();
	/** Tiles of the walk being shown (empty if none). */
	private List<int[]> walk = Collections.emptyList();
	/** "Run done in N min" once a run ends itself; cleared on the next run or bank visit. */
	private String runDone;
	/** Bottomless bucket uses, loaded per account; null until needed. */
	private BottomlessBucket bucket;
	/** Whether the last item used on an object was the bottomless bucket; null if not seen. */
	private Boolean lastUseWasBucket;
	private static final Set<Integer> HERB_PATCH_OBJECTS = new HashSet<>(Arrays.asList(
		ObjectID.FARMING_HERB_PATCH_1, ObjectID.FARMING_HERB_PATCH_2, ObjectID.FARMING_HERB_PATCH_3,
		ObjectID.FARMING_HERB_PATCH_4, ObjectID.FARMING_HERB_PATCH_5, ObjectID.FARMING_HERB_PATCH_6,
		ObjectID.MYARM_HERBPATCH));

	/** Allotment and flower patch objects; planted, they show as the crop, so the name can't be relied on. */
	private static final Set<Integer> ALLOTMENT_OBJECTS = new HashSet<>(Arrays.asList(
		ObjectID.FARMING_VEG_PATCH_1, ObjectID.FARMING_VEG_PATCH_2, ObjectID.FARMING_VEG_PATCH_3,
		ObjectID.FARMING_VEG_PATCH_4, ObjectID.FARMING_VEG_PATCH_5, ObjectID.FARMING_VEG_PATCH_6,
		ObjectID.FARMING_VEG_PATCH_7, ObjectID.FARMING_VEG_PATCH_8, ObjectID.FARMING_VEG_PATCH_9,
		ObjectID.FARMING_VEG_PATCH_10, ObjectID.FARMING_VEG_PATCH_11));
	private static final Set<Integer> FLOWER_OBJECTS = new HashSet<>(Arrays.asList(
		ObjectID.FARMING_FLOWER_PATCH_1, ObjectID.FARMING_FLOWER_PATCH_2, ObjectID.FARMING_FLOWER_PATCH_3,
		ObjectID.FARMING_FLOWER_PATCH_4, ObjectID.FARMING_FLOWER_PATCH_5));

	/** The herb run being guided, or null. */
	private HerbRun run;
	/** At the run's current stop (near its patch). */
	private boolean atStop;

	@Override
	protected void startUp()
	{
		overlayManager.add(withdrawOverlay);
		overlayManager.add(runItemOverlay);
		overlayManager.add(teleportOverlay);
		scenePath = new PathOverlay(client, false);
		minimapPath = new PathOverlay(client, true);
		overlayManager.add(scenePath);
		overlayManager.add(minimapPath);
		navOverlay = new NavOverlay(client, spriteManager);
		overlayManager.add(navOverlay);
		overlayManager.add(missingOverlay);
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
				farmingLevel = client.getRealSkillLevel(Skill.FARMING);
				questsDirty = true;
				loggedInAs = client.getAccountHash();
				ItemContainer worn = client.getItemContainer(InventoryID.WORN);
				wornIds.clear();
				if (worn != null)
				{
					count(worn).keySet().forEach(id -> wornIds.add(itemManager.canonicalize(id)));
				}
				ItemContainer box = client.getItemContainer(InventoryID.SEED_BOX);
				seedBox = box != null ? count(box) : new HashMap<>();
				readInventory(client.getItemContainer(InventoryID.INV));
				readBank(client.getItemContainer(InventoryID.BANK));
				loadAccount();
			}
			select(saved != null ? saved : Potion.PRAYER, false);
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(missingOverlay);
		missingOverlay.set(null, List.of());
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
		overlayManager.remove(withdrawOverlay);
		overlayManager.remove(runItemOverlay);
		overlayManager.remove(teleportOverlay);
		overlayManager.remove(scenePath);
		overlayManager.remove(minimapPath);
		overlayManager.remove(navOverlay);
		panel = null;
		// On the client thread, after anything already queued there, so nothing reads these mid-reset.
		clientThread.invokeLater(() ->
		{
			resetAccount();
			selected = null;
			lastLayout = null;
			invCounts.clear();
			herbPatches.clear();
			rings.clear();
			patchVarbits.clear();
		});
	}

	/**
	 * Remember what the bank, potion storage and seed vault held, for this
	 * account, so the plugin works after a restart without opening the bank.
	 * Opening the bank replaces it with what's really there.
	 */
	private void saveAccount()
	{
		if (bankCounts == null || configManager.getRSProfileKey() == null)
		{
			return;
		}
		configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "savedBank", encode(bankCounts));
		configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "savedStorage", encode(storageCounts));
		Map<Integer, Integer> holders = new HashMap<>();
		bankPlaceholders.forEach(id -> holders.put(id, 0));
		configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "savedPlaceholders", encode(holders));
		configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "savedBankAt", bankObservedAt);
	}

	/** What {@link #saveAccount} remembered, while the bank hasn't been seen this session. */
	private void loadAccount()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		Map<Integer, Integer> vault = decode(configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "savedVault"));
		if (seedVault.isEmpty() && vault != null)
		{
			seedVault = vault;
		}
		Map<Integer, Integer> bank = decode(configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "savedBank"));
		if (bankCounts != null || bank == null)
		{
			farmDirty = true;
			pushToPanel(null);
			return;
		}
		bankCounts = bank;
		Map<Integer, Integer> storage = decode(configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "savedStorage"));
		storageCounts = storage != null ? storage : new HashMap<>();
		Map<Integer, Integer> holders = decode(configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "savedPlaceholders"));
		bankPlaceholders.clear();
		if (holders != null)
		{
			bankPlaceholders.addAll(holders.keySet());
		}
		Long at = configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "savedBankAt", Long.class);
		bankObservedAt = at != null ? at : 0;
		farmDirty = true;
		relayout();
		pushToPanel(null);
	}

	/** "id:qty,id:qty" (empty map: "-"). */
	static String encode(Map<Integer, Integer> counts)
	{
		if (counts.isEmpty())
		{
			return "-";
		}
		StringBuilder sb = new StringBuilder();
		counts.forEach((id, n) -> sb.append(sb.length() > 0 ? "," : "").append(id).append(':').append(n));
		return sb.toString();
	}

	/** The map {@link #encode} wrote, or null if there's nothing (or nothing readable). */
	static Map<Integer, Integer> decode(String v)
	{
		if (v == null || v.isEmpty())
		{
			return null;
		}
		Map<Integer, Integer> out = new HashMap<>();
		if (v.equals("-"))
		{
			return out;
		}
		try
		{
			for (String part : v.split(","))
			{
				String[] kv = part.split(":");
				out.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
			}
		}
		catch (NumberFormatException | ArrayIndexOutOfBoundsException e)
		{
			return null;
		}
		return out;
	}

	/**
	 * Forget everything about the logged-in account: another one may be next
	 * (logout, or the plugin turned off and back on after switching).
	 */
	private void resetAccount()
	{
		bankCounts = null;
		storageCounts = new HashMap<>();
		collapsed = new HashSet<>();
		wornIds.clear();
		bankPlaceholders.clear();
		herbloreLevel = 0;
		boostedLevel = 0;
		farmingLevel = 0;
		quests = null;
		fairyRings = null;
		seedVault = new HashMap<>();
		seedBox = new HashMap<>();
		ownHouseEntry = null;
		bucket = null;
		lastUseWasBucket = null;
		runDone = null;
		panelFarmPlan = null;
		endRun();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(net.runelite.client.events.RuneScapeProfileChanged event)
	{
		// Per-account saved state (the bucket, the last bank seen) is reloaded for the new profile.
		bucket = null;
		seedVault = new HashMap<>();
		clientThread.invokeLater(this::loadAccount);
		farmDirty = true;
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

	void setFarmMode(boolean farm)
	{
		configManager.setConfiguration(PotionPrepConfig.GROUP, "mode", farm ? MODE_FARM : "");
		clientThread.invokeLater(() ->
		{
			relayout();
			pushToPanel(null);
		});
	}

	/** Store a herb-run setting (seed choice) and rebuild. */
	void setFarmSetting(String key, String value)
	{
		configManager.setConfiguration(PotionPrepConfig.GROUP, key, value);
		clientThread.invokeLater(() ->
		{
			relayout();
			pushToPanel(null);
		});
	}

	/** Panel-only state (which sections are open): saved, nothing replanned. */
	void setPanelSetting(String key, String value)
	{
		configManager.setConfiguration(PotionPrepConfig.GROUP, key, value);
	}

	String getPanelSetting(String key)
	{
		return configManager.getConfiguration(PotionPrepConfig.GROUP, key);
	}

	void setPatchEnabled(Farm.Patch patch, boolean on)
	{
		Set<Farm.Patch> patches = EnumSet.noneOf(Farm.Patch.class);
		patches.addAll(farmSettings().getPatches());
		if (on)
		{
			patches.add(patch);
		}
		else
		{
			patches.remove(patch);
		}
		StringBuilder sb = new StringBuilder();
		for (Farm.Patch p : patches)
		{
			sb.append(sb.length() > 0 ? "," : "").append(p.name());
		}
		// "-" keeps an empty selection from reading as "use the defaults".
		setFarmSetting("farmPatches", sb.length() > 0 ? sb.toString() : "-");
	}

	/** Cycle a patch's teleport through its options; landing back on the automatic pick clears the override. */
	void cycleTeleport(Farm.Patch patch)
	{
		clientThread.invokeLater(() ->
		{
			Farm.Teleport[] options = patch.getTeleports();
			if (options.length < 2)
			{
				return;
			}
			Map<Farm.Patch, Farm.Teleport> overrides = new EnumMap<>(Farm.Patch.class);
			overrides.putAll(farmSettings().getOverrides());
			Farm.Teleport auto = computeFarm().getStops().stream().filter(st -> st.getPatch() == patch)
				.map(FarmPlan.Stop::getAuto).findFirst().orElse(null);
			Farm.Teleport shown = overrides.getOrDefault(patch, auto);
			Farm.Teleport next = options[(Arrays.asList(options).indexOf(shown) + 1) % options.length];
			if (next == auto)
			{
				overrides.remove(patch);
			}
			else
			{
				overrides.put(patch, next);
			}
			StringBuilder sb = new StringBuilder();
			overrides.forEach((p, t) -> sb.append(sb.length() > 0 ? "," : "").append(p.name()).append(':').append(t.name()));
			setFarmSetting("farmTeleports", sb.toString());
		});
	}

	/** Remember what's worn now as this mode's gear (herb run or potions). */
	void saveWornGear(boolean farm)
	{
		clientThread.invokeLater(() ->
		{
			ItemContainer worn = client.getItemContainer(InventoryID.WORN);
			if (worn == null)
			{
				return;
			}
			Item[] items = worn.getItems();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < items.length; i++)
			{
				// Some items (e.g. graceful) have a separate worn id that can't be banked.
				sb.append(i > 0 ? "," : "").append(items[i].getId() > 0 ? itemManager.canonicalize(items[i].getId()) : -1);
			}
			configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, gearKey(farm), sb.toString());
			relayout();
			pushToPanel(null);
		});
	}

	void clearGear(boolean farm)
	{
		clientThread.invokeLater(() ->
		{
			configManager.unsetRSProfileConfiguration(PotionPrepConfig.GROUP, gearKey(farm));
			relayout();
			pushToPanel(null);
		});
	}

	private static String gearKey(boolean farm)
	{
		return farm ? "gearFarm" : "gearPotions";
	}

	/** Saved gear per equipment slot, or null if none saved for that mode. */
	private int[] savedGear(boolean farm)
	{
		String v = configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, gearKey(farm));
		if (v == null || v.isEmpty())
		{
			return null;
		}
		try
		{
			// canonicalize: gear saved before worn ids were mapped to bankable ones.
			return Arrays.stream(v.split(",")).mapToInt(Integer::parseInt)
				.map(id -> id > 0 ? itemManager.canonicalize(id) : id).toArray();
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	// --- Guided herb run (Quest Helper style) ---

	void startRun()
	{
		clientThread.invokeLater(() ->
		{
			FarmPlan plan = computeFarm();
			run = HerbRun.from(plan, farmSettings(), System.currentTimeMillis());
			runDone = null;
			scanHerbPatches();
			updateRunGuide();
			pushToPanel(null);
		});
	}

	void skipStop()
	{
		clientThread.invokeLater(() ->
		{
			if (run != null)
			{
				run.skip();
				updateRunGuide();
				pushToPanel(null);
			}
		});
	}

	void stopRun()
	{
		clientThread.invokeLater(() ->
		{
			endRun();
			pushToPanel(null);
		});
	}

	private void endRun()
	{
		run = null;
		atStop = false;
		runItemOverlay.set(Collections.emptySet());
		teleportOverlay.set(null, -1);
		menuOption = null;
		showPath(null);
		scenePath.setOutline(Collections.emptyList());
		scenePath.setNpc(null, null);
		navOverlay.set(null);
		mark(null, null);
	}

	/** Where the player is versus the current stop: patch state, route, arrow and highlights. */
	private void updateRunGuide()
	{
		if (run == null)
		{
			return;
		}
		guide();
		if (run != null && run.finished())
		{
			// Last patch done: the run stops itself.
			runDone = "Run done in " + FarmPlan.minutes((System.currentTimeMillis() - run.getStartedAt()) / 1000)
				+ (run.getPicked() > 0 ? ": picked " + run.getPicked() : "");
			endRun();
			pushToPanel(null);
		}
	}

	private void guide()
	{
		HerbRun.Stop stop = run.current();
		net.runelite.api.Player me = client.getLocalPlayer();
		atStop = stop != null && me != null && PatchPlaces.near(stop.getPoint(), me.getWorldLocation());
		if (run.onPosition(atStop))
		{
			// Left a stop that's as done as it'll get: on to the next.
			stop = run.current();
			atStop = stop != null && me != null && PatchPlaces.near(stop.getPoint(), me.getWorldLocation());
			run.onPosition(atStop);
		}
		if (atStop)
		{
			// The run's own seeds (not the config's, which may have changed), carried loose or in a seed box.
			Map<HerbRun.PlotType, Integer> seeds = new java.util.EnumMap<>(HerbRun.PlotType.class);
			for (HerbRun.PlotType t : HerbRun.PlotType.values())
			{
				seeds.put(t, invCounts.getOrDefault(run.seedId(t), 0));
			}
			if (run.setSeedsCarried(seeds))
			{
				stop = run.current();
				atStop = stop != null && PatchPlaces.near(stop.getPoint(), me.getWorldLocation());
			}
		}
		if (atStop && run.setCompostCarried(hasCompost()))
		{
			stop = run.current();
			atStop = stop != null && PatchPlaces.near(stop.getPoint(), me.getWorldLocation());
		}
		if (atStop && stop.hasHerb())
		{
			// The patch's varbit is only meaningful while you're in its area.
			if (run.onHerbState(PatchStatus.task(client.getVarbitValue(PatchStatus.varbit(stop.getPatch())))))
			{
				// May have moved on to the next stop.
				stop = run.current();
				atStop = stop != null && PatchPlaces.near(stop.getPoint(), me.getWorldLocation());
			}
		}

		if (atStop && stop.getPlots().stream().anyMatch(p -> p.getType() != HerbRun.PlotType.HERB))
		{
			// The flower patch and each allotment, read live like the herb patch.
			int[] side = PatchStatus.sideVarbits(stop.getPatch());
			if (side.length > 0)
			{
				int[] allotVarbits = java.util.Arrays.copyOfRange(side, 1, side.length);
				PatchStatus.Task[] allot = new PatchStatus.Task[allotVarbits.length];
				for (int i = 0; i < allot.length; i++)
				{
					allot[i] = PatchStatus.sideTask(true, client.getVarbitValue(allotVarbits[i]));
				}
				if (run.onSideState(PatchStatus.sideTask(false, client.getVarbitValue(side[0])), allotVarbits, allot))
				{
					stop = run.current();
					atStop = stop != null && PatchPlaces.near(stop.getPoint(), me.getWorldLocation());
				}
			}
		}

		// A jump to near the stop (outside your house, where the portal's still to come) is the
		// teleport done: from there it's a walk, so the teleport needn't be pointed at.
		net.runelite.api.coords.WorldPoint here = me != null ? me.getWorldLocation() : null;
		net.runelite.api.WorldView view = client.getTopLevelWorldView();
		// Coming out of a raid or other instance (not your house) isn't a teleport you were told to take.
		boolean jumped = here != null && lastPos != null && view != null && !view.isInstance()
			&& here.distanceTo2D(lastPos) > TELEPORT_JUMP && (!lastInstance || lastHouse);
		if (stop != null && jumped && here.distanceTo2D(stop.getPoint()) < ARRIVED)
		{
			teleportedTo = stop;
		}
		// Going by fairy ring: first to a ring (your house's, or the Monastery's by Ardougne cloak).
		int cloak = ringCloak(stop);
		boolean fairy = stop != null && stop.getTeleport() != null && stop.getTeleport().getKind() == Farm.Teleport.Kind.FAIRY;
		if (fairy && stop != teleportedTo && (jumped || houseScene && !lastHouse))
		{
			// Jumped (or into your house), but not to the patch: by a ring now, so the way there's done with.
			atRing = stop;
		}
		lastPos = here;
		lastInstance = view != null && view.isInstance();
		lastHouse = houseScene;
		boolean teleported = stop != null && stop == teleportedTo;
		// The ring itself, once you're by one (after the cloak, or one was close anyway).
		net.runelite.api.GameObject ring = null;
		if (fairy && here != null && !teleported && !atStop)
		{
			int best = Integer.MAX_VALUE;
			for (net.runelite.api.GameObject o : rings)
			{
				int d = o.getWorldLocation().distanceTo2D(here);
				if (o.getPlane() == here.getPlane() && d < best)
				{
					best = d;
					ring = o;
				}
			}
			if (ring != null && !(stop == atRing || cloak == -1 || best <= RING_CLOSE))
			{
				ring = null;
			}
		}
		ringFor = ring != null ? stop : null;
		ringObject = ring;
		boolean byCloak = cloak != -1 && stop != atRing && ring == null;

		Set<Integer> highlight = new HashSet<>();
		if (stop != null && !atStop && !teleported && (stop.getItemId() != -1 || byCloak))
		{
			if (stop.getItemId() != -1)
			{
				highlight.add(stop.getItemId());
			}
			if (byCloak && cloak != Farm.RING_SPELL)
			{
				highlight.add(cloak);
			}
		}
		else if (stop != null && atStop)
		{
			// What to use on the patches still to do: spade, rake, plant cure, compost and seeds.
			// Not what a ready patch gives: that's what you'll get, not something to use.
			for (HerbRun.Plot p : stop.getPlots())
			{
				if (!HerbRun.status(p, run.isCompost()).equals("Pick"))
				{
					highlight.addAll(itemsFor(stop, p));
				}
			}
		}
		runItemOverlay.set(highlight);
		// On the way, point at the spell to cast and colour the option to pick in teleport menus.
		boolean travelling = stop != null && !atStop && !teleported && stop.getTeleport() != null;
		String spell = null;
		if (travelling)
		{
			spell = Farm.spellName(stop.getTeleport());
			if (spell == null && (byCloak ? cloak == Farm.RING_SPELL : stop.getItemId() == -1 && FarmPlan.entersHouse(stop.getTeleport())))
			{
				// No cape or tablet: the run planned Teleport to House.
				spell = "Teleport to House";
			}
		}
		int tab = -1;
		if (spell != null)
		{
			tab = TeleportOverlay.MAGIC;
		}
		else if (travelling && (stop.getItemId() != -1 || byCloak))
		{
			int item = itemManager.canonicalize(byCloak ? cloak : stop.getItemId());
			// Worn or carried; in the bank, the bank view shows it instead.
			tab = wornIds.contains(item) ? TeleportOverlay.EQUIPMENT : invCounts.containsKey(item) ? TeleportOverlay.INVENTORY : -1;
		}
		teleportOverlay.set(spell, tab);
		menuOption = travelling && !byCloak ? Farm.menuOption(stop.getTeleport(), stop.getPatch()) : null;

		// Once the patch is in the loaded area, draw the walk there on the ground and minimap;
		// a minimap pin/arrow and a world map marker always point to it.
		net.runelite.api.coords.WorldPoint goal = ring != null ? ring.getWorldLocation() : stop != null ? stop.getPoint() : null;
		navOverlay.set(goal);
		// A house ring's place is inside the instance, not on the world map: mark the patch there instead.
		boolean houseRing = ring != null && view != null && view.isInstance();
		mark(houseRing ? stop.getPoint() : goal, stop == null ? null : ring != null && !houseRing ? "Fairy ring" : stop.getName());
		// At the stop, outline the herb patch itself (no hint arrow: that's the game's).
		// At the stop, outline every patch with something still to do, in whatever order you like.
		// Over each, the icons of what to use on it next.
		List<net.runelite.api.GameObject> outlined = atStop ? toDo(stop) : ring != null ? Collections.singletonList(ring) : Collections.emptyList();
		// The path only gets you there: once you're by a patch to do, its outline takes over.
		boolean arrived = atStop && here != null && outlined.stream()
			.anyMatch(o -> o.getPlane() == here.getPlane() && o.getWorldLocation().distanceTo2D(here) <= PATH_DONE);
		showPath(arrived ? null : stop);
		java.util.function.ToIntFunction<net.runelite.api.GameObject> key = o -> patchVarbits.getOrDefault(o, -o.getId());
		Map<Integer, List<java.awt.image.BufferedImage>> icons = new HashMap<>();
		if (atStop)
		{
			for (net.runelite.api.GameObject o : outlined)
			{
				HerbRun.Plot plot = plotAt(stop, o);
				if (plot != null)
				{
					List<java.awt.image.BufferedImage> images = new ArrayList<>();
					for (int id : itemsFor(stop, plot))
					{
						images.add(itemManager.getImage(id));
					}
					icons.put(key.applyAsInt(o), images);
				}
			}
		}
		scenePath.setOutline(outlined, key, icons);
		// No compost on you but the leprechaun has some: point at them, with the compost to take.
		net.runelite.api.NPC leprechaun = atStop ? leprechaunFor(stop, here) : null;
		scenePath.setNpc(leprechaun, leprechaun != null ? itemManager.getImage(storedCompost()) : null);
	}

	/** The run's plot an outlined patch object is, or null. */
	private HerbRun.Plot plotAt(HerbRun.Stop stop, net.runelite.api.GameObject o)
	{
		HerbRun.PlotType type = typeAt(o, stop.getPatch());
		if (type == null)
		{
			return null;
		}
		Integer v = patchVarbits.get(o);
		HerbRun.Plot any = null;
		for (HerbRun.Plot p : stop.getPlots())
		{
			if (p.getType() != type)
			{
				continue;
			}
			if (type != HerbRun.PlotType.ALLOTMENT || v != null && (p.getVarbit() == v || p.getVarbit() == -1 && p.getShown() == v))
			{
				return p;
			}
			any = any == null && p.getVarbit() == -1 ? p : any;
		}
		return any;
	}

	/** The live varbit of a plot at the stop you're at: the herb patch, flower patch or its allotment; -1 if unknown. */
	private int varbitOf(HerbRun.Stop stop, HerbRun.Plot p)
	{
		if (p.getType() == HerbRun.PlotType.HERB)
		{
			return PatchStatus.varbit(stop.getPatch());
		}
		int[] side = PatchStatus.sideVarbits(stop.getPatch());
		if (p.getType() == HerbRun.PlotType.FLOWER)
		{
			return side.length > 0 ? side[0] : -1;
		}
		return p.getVarbit() != -1 ? p.getVarbit() : p.getShown();
	}

	/**
	 * What to use on a plot next, in order: a spade, rake or plant cure, else compost then seed.
	 * Ready to pick, it's what you'll pick (the grimy herb, snape grass, limpwurt root).
	 */
	private List<Integer> itemsFor(HerbRun.Stop stop, HerbRun.Plot p)
	{
		List<Integer> out = new ArrayList<>();
		switch (HerbRun.status(p, run.isCompost()))
		{
			case "Pick":
			{
				int v = varbitOf(stop, p);
				out.add(v == -1 ? -1 : PatchStatus.picks(p.getType(), client.getVarbitValue(v)));
				break;
			}
			case "Clear":
				out.add(ItemID.SPADE);
				break;
			case "Rake":
				out.add(ItemID.RAKE);
				break;
			case "Cure":
				out.add(ItemID.PLANT_CURE);
				break;
			case "Plant + compost":
				addCompost(out);
				out.add(run.seedId(p.getType()));
				break;
			case "Plant":
				out.add(run.seedId(p.getType()));
				break;
			case "Compost":
				addCompost(out);
				break;
			default:
				break;
		}
		out.removeIf(id -> id == -1);
		return out;
	}

	/** The compost to use: what you carry (the best first, or the bottomless bucket), else the leprechaun's; none for Fertile Soil. */
	private void addCompost(List<Integer> out)
	{
		if (run.isFertileSoil() && client.getVarbitValue(VarbitID.SPELLBOOK) == Farm.LUNAR)
		{
			return;
		}
		for (int id : Farm.COMPOST)
		{
			if (invCounts.getOrDefault(id, 0) > 0)
			{
				out.add(id);
				return;
			}
		}
		if (invCounts.getOrDefault(Farm.BOTTOMLESS_BUCKET, 0) > 0)
		{
			out.add(Farm.BOTTOMLESS_BUCKET);
			return;
		}
		// None on you: what the tool leprechaun has, to take out. With none there either, the run doesn't ask for it.
		int stored = storedCompost();
		if (stored != -1)
		{
			out.add(stored);
		}
	}

	/** The best compost the tool leprechaun has (or the bottomless bucket), -1 if none. */
	private int storedCompost()
	{
		Map<Integer, Integer> stored = ToolLeprechaun.read(client::getVarbitValue);
		for (int id : Farm.COMPOST)
		{
			if (stored.getOrDefault(id, 0) > 0)
			{
				return id;
			}
		}
		return stored.getOrDefault(Farm.BOTTOMLESS_BUCKET, 0) > 0 ? Farm.BOTTOMLESS_BUCKET : -1;
	}

	/** Compost (or the bottomless bucket) in your inventory. */
	private boolean carryingCompost()
	{
		if (invCounts.getOrDefault(Farm.BOTTOMLESS_BUCKET, 0) > 0)
		{
			return true;
		}
		for (int id : Farm.COMPOST)
		{
			if (invCounts.getOrDefault(id, 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The tool leprechaun to take compost from, when a patch here still needs it and
	 * you've none on you; null otherwise or if they're not in sight.
	 */
	private net.runelite.api.NPC leprechaunFor(HerbRun.Stop stop, net.runelite.api.coords.WorldPoint here)
	{
		if (stop == null || here == null || carryingCompost() || storedCompost() == -1
			|| run.isFertileSoil() && client.getVarbitValue(VarbitID.SPELLBOOK) == Farm.LUNAR
			|| stop.getPlots().stream().map(p -> HerbRun.status(p, run.isCompost())).noneMatch(st -> st.endsWith("ompost")))
		{
			return null;
		}
		net.runelite.api.WorldView wv = client.getTopLevelWorldView();
		net.runelite.api.NPC best = null;
		int bestDist = 21;
		for (net.runelite.api.NPC npc : wv == null ? Collections.<net.runelite.api.NPC>emptyList() : wv.npcs())
		{
			int d = npc.getWorldLocation().distanceTo2D(here);
			if ("Tool Leprechaun".equalsIgnoreCase(npc.getName()) && d < bestDist)
			{
				best = npc;
				bestDist = d;
			}
		}
		return best;
	}

	/** Compost to hand: carried, or stored with the tool leprechaun (one at every patch). */
	private boolean hasCompost()
	{
		if (run != null && run.isFertileSoil() && client.getVarbitValue(VarbitID.SPELLBOOK) == Farm.LUNAR)
		{
			// Composting by spell: still on the Lunar spellbook.
			return true;
		}
		if (ToolLeprechaun.hasCompost(ToolLeprechaun.read(client::getVarbitValue)))
		{
			return true;
		}
		if (invCounts.getOrDefault(Farm.BOTTOMLESS_BUCKET, 0) > 0)
		{
			return true;
		}
		for (int id : Farm.COMPOST)
		{
			if (invCounts.getOrDefault(id, 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Walk from the player to the stop, drawn on the ground and minimap. Ends
	 * beside the herb patch object when it's loaded, else near the stop's spot.
	 */
	/** Within this many tiles of a patch to do, the path is put away. */
	private static final int PATH_DONE = 8;

	private void showPath(HerbRun.Stop stop)
	{
		List<int[]> tiles = Collections.emptyList();
		net.runelite.api.Player me = client.getLocalPlayer();
		net.runelite.api.WorldView wv = client.getTopLevelWorldView();
		boolean toRing = stop != null && ringFor == stop && ringObject != null;
		// In an instance (your house) only the ring can be walked to: the patch's coordinates don't apply there.
		if (stop != null && me != null && wv != null && (toRing ? ringObject.getPlane() == wv.getPlane()
			: !wv.isInstance() && stop.getPoint().getPlane() == wv.getPlane()))
		{
			net.runelite.api.CollisionData[] maps = wv.getCollisionMaps();
			net.runelite.api.coords.LocalPoint from = me.getLocalLocation();
			if (from != null && maps != null && maps[wv.getPlane()] != null)
			{
				int[][] flags = maps[wv.getPlane()].getFlags();
				net.runelite.api.GameObject patch = toRing ? ringObject
					: stop.hasHerb() ? herbPatchNear(stop.getPatch(), stop.getPoint()) : nearestToDo(stop, me);
				net.runelite.api.coords.LocalPoint to = patch == null
					? net.runelite.api.coords.LocalPoint.fromWorld(wv, stop.getPoint()) : null;
				// The search is only redone when you, the target or the loaded area moves.
				String key = wv.getBaseX() + "," + wv.getBaseY() + "," + wv.getPlane() + ":" + from.getSceneX() + ","
					+ from.getSceneY() + ">" + (patch != null ? patch.getSceneMinLocation() + "-" + patch.getSceneMaxLocation()
					: to != null ? to.getSceneX() + "," + to.getSceneY() : "none");
				if (key.equals(pathKey))
				{
					return;
				}
				pathKey = key;
				if (patch != null)
				{
					net.runelite.api.Point min = patch.getSceneMinLocation();
					net.runelite.api.Point max = patch.getSceneMaxLocation();
					tiles = WalkPath.find(flags, from.getSceneX(), from.getSceneY(), min.getX(), min.getY(), max.getX(), max.getY());
				}
				else if (to != null)
				{
					tiles = WalkPath.find(flags, from.getSceneX(), from.getSceneY(), to.getSceneX(), to.getSceneY(), 2);
				}
			}
		}
		if (stop == null)
		{
			pathKey = null;
		}
		walk = tiles;
		// On the minimap only: in the scene, the outlines and markers show the way.
		minimapPath.set(tiles);
	}


	/** A run left by logging out, and the account it belongs to. */
	private HerbRun pausedRun;
	private long pausedFor = -1;
	/** Account hash of who's logged in, -1 if not known. */
	private long loggedInAs = -1;

	/** Where the player was last tick, to spot a teleport. */
	private net.runelite.api.coords.WorldPoint lastPos;
	/** Last tick was in an instance, and whether that was a house. */
	private boolean lastInstance, lastHouse;
	/** A ring this close is walked to, not reached by cloak. */
	private static final int RING_CLOSE = 25;
	/** The stop being reached by the fairy ring in view, and that ring; null otherwise. */
	private HerbRun.Stop ringFor;
	private net.runelite.api.GameObject ringObject;

	/** The fairy ring stop whose ring you've reached; the Ardougne cloak is done with. */
	private HerbRun.Stop atRing;

	/** How to reach this stop's fairy ring (an item, or {@link Farm#RING_SPELL}), or -1 (not a fairy ring, or no way). */
	private int ringCloak(HerbRun.Stop stop)
	{
		if (stop == null || stop.getTeleport() == null || stop.getTeleport().getKind() != Farm.Teleport.Kind.FAIRY)
		{
			return -1;
		}
		return stop.getRingWay();
	}

	/** Your house has a fairy ring: set in the config, or seen on a visit. */
	private boolean houseRing()
	{
		return config.fairyHouse() || config.scanHouse()
			&& Boolean.TRUE.equals(configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "pohRing", Boolean.class));
	}

	/** The stop you've teleported to; its teleport highlights are done with. */
	private HerbRun.Stop teleportedTo;
	/** Moving further than this in a tick is a teleport, not a step. */
	private static final int TELEPORT_JUMP = 15;
	/** A teleport landing this close to the stop counts as having got there. */
	private static final int ARRIVED = 200;

	/** The plan last shown in the panel's herb run view. */
	private FarmPlan panelFarmPlan;

	/** Menus teleports open: chat options, the jewellery and talisman list, the portal nexus, the whistle, spirit trees. */
	private static final int[] TELEPORT_MENUS = {InterfaceID.CHATMENU, InterfaceID.MENU, InterfaceID.MENU_NEW,
		InterfaceID.TELENEXUS_TELEPORT, InterfaceID.POH_JEWELLERY_BOX, InterfaceID.QUETZALWHISTLE_MENU};
	private static final int MENU_HIGHLIGHT = 0x00C8FF;

	/** Colour the option the run wants in whichever teleport menu is open (Quest Helper does the same). */
	private void highlightMenuOption()
	{
		String want = menuOption;
		if (want == null)
		{
			return;
		}
		for (int group : TELEPORT_MENUS)
		{
			net.runelite.api.widgets.Widget root = client.getWidget(group, 0);
			if (root != null && !root.isHidden())
			{
				colourOption(root, want.toLowerCase(java.util.Locale.ROOT), 0);
			}
		}
	}

	private void colourOption(net.runelite.api.widgets.Widget w, String want, int depth)
	{
		if (w == null || depth > 6)
		{
			return;
		}
		String text = w.getText();
		if (text != null && !text.isEmpty()
			&& net.runelite.client.util.Text.removeTags(text).toLowerCase(java.util.Locale.ROOT).contains(want))
		{
			w.setTextColor(MENU_HIGHLIGHT);
		}
		for (net.runelite.api.widgets.Widget[] kids : new net.runelite.api.widgets.Widget[][]{
			w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (kids != null)
			{
				for (net.runelite.api.widgets.Widget k : kids)
				{
					colourOption(k, want, depth + 1);
				}
			}
		}
	}

	/** What the drawn path was found for, so it isn't searched again every tick. */
	private String pathKey;

	/** Herb patches already loaded (spawn events only cover ones loaded later). */
	private void scanHerbPatches()
	{
		net.runelite.api.WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}
		net.runelite.api.Tile[][][] tiles = wv.getScene().getTiles();
		for (net.runelite.api.Tile[][] plane : tiles)
		{
			for (net.runelite.api.Tile[] column : plane)
			{
				for (net.runelite.api.Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					for (net.runelite.api.GameObject o : tile.getGameObjects())
					{
						if (o != null)
						{
							track(o);
						}
					}
				}
			}
		}
	}

	/** What each object's own definition names it as a patch, or null (definitions don't change). */
	private final Map<Integer, java.util.Optional<HerbRun.PlotType>> patchIds = new HashMap<>();
	/** Objects that can't be a patch (no impostors, not named like one). */
	private final Set<Integer> notPatches = new HashSet<>();

	private static HerbRun.PlotType patchName(String name)
	{
		if (name == null)
		{
			return null;
		}
		switch (name.toLowerCase(java.util.Locale.ROOT))
		{
			case "herb patch":
				return HerbRun.PlotType.HERB;
			case "flower patch":
				return HerbRun.PlotType.FLOWER;
			case "allotment":
				return HerbRun.PlotType.ALLOTMENT;
			default:
				return null;
		}
	}

	/**
	 * A farming patch the run uses, by object id: a known herb patch id, or
	 * what the game names it (itself or what it currently shows as). Newer
	 * patches like Civitas illa Fortis have no named constant.
	 */
	private HerbRun.PlotType patchType(int id)
	{
		if (HERB_PATCH_OBJECTS.contains(id))
		{
			return HerbRun.PlotType.HERB;
		}
		if (ALLOTMENT_OBJECTS.contains(id))
		{
			return HerbRun.PlotType.ALLOTMENT;
		}
		if (FLOWER_OBJECTS.contains(id))
		{
			return HerbRun.PlotType.FLOWER;
		}
		if (notPatches.contains(id))
		{
			return null;
		}
		net.runelite.api.ObjectComposition def = client.getObjectDefinition(id);
		if (def == null)
		{
			return null;
		}
		if (def.getImpostorIds() == null && patchName(def.getName()) == null)
		{
			// Most objects: a plain name that isn't a patch, never worth a second look.
			notPatches.add(id);
			return null;
		}
		HerbRun.PlotType own = patchIds.computeIfAbsent(id, i -> java.util.Optional.ofNullable(patchName(def.getName())))
			.orElse(null);
		if (own != null)
		{
			return own;
		}
		// What it shows as depends on the patch's varbit, so that isn't cached.
		net.runelite.api.ObjectComposition now = def.getImpostorIds() != null ? def.getImpostor() : null;
		return now != null ? patchName(now.getName()) : null;
	}

	/** The farming varbit each patch object shows (its impostor varbit), for patches found that way. */
	private final Map<net.runelite.api.GameObject, Integer> patchVarbits = new HashMap<>();
	/** The farming varbit of the patch last clicked or used an item on, -1 if none. */
	private int lastPlotVarbit = -1;
	private final Map<Integer, Integer> transmitIds = new HashMap<>();

	/** Remember a patch object: by its farming varbit, or failing that its id or name. */
	/** Fairy rings in the loaded area. */
	private final Set<net.runelite.api.GameObject> rings = new HashSet<>();
	/** Object id to whether it's a fairy ring. */
	private final Map<Integer, Boolean> ringIds = new HashMap<>();

	private void track(net.runelite.api.GameObject o)
	{
		if (ringIds.computeIfAbsent(o.getId(), i ->
		{
			net.runelite.api.ObjectComposition def = client.getObjectDefinition(i);
			return i == ObjectID.POH_FAIRY_RING || i == ObjectID.POH_SPIRIT_RING
				|| def != null && "Fairy ring".equalsIgnoreCase(def.getName());
		}))
		{
			rings.add(o);
		}
		int id = o.getId();
		HerbRun.PlotType type = patchType(id);
		int varbit = transmitIds.computeIfAbsent(id, i ->
		{
			net.runelite.api.ObjectComposition def = notPatches.contains(i) ? null : client.getObjectDefinition(i);
			int v = def != null && def.getImpostorIds() != null ? def.getVarbitId() : -1;
			return v >= VarbitID.FARMING_TRANSMIT_A && v <= VarbitID.FARMING_TRANSMIT_E
				|| v >= VarbitID.FARMING_TRANSMIT_F && v <= VarbitID.FARMING_TRANSMIT_N ? v : -1;
		});
		if (type != null || varbit != -1)
		{
			herbPatches.put(o, type);
			if (varbit != -1)
			{
				patchVarbits.put(o, varbit);
			}
		}
	}

	/**
	 * What an object is at a location: from the varbit it shows when known
	 * (every patch has its own), else from its id or name.
	 */
	private HerbRun.PlotType typeAt(net.runelite.api.GameObject o, Farm.Patch patch)
	{
		Integer v = patchVarbits.get(o);
		if (v == null)
		{
			return herbPatches.get(o);
		}
		if (v == PatchStatus.varbit(patch))
		{
			return HerbRun.PlotType.HERB;
		}
		int[] side = PatchStatus.sideVarbits(patch);
		if (side.length > 0 && v == side[0])
		{
			return HerbRun.PlotType.FLOWER;
		}
		for (int i = 1; i < side.length; i++)
		{
			if (v == side[i])
			{
				return HerbRun.PlotType.ALLOTMENT;
			}
		}
		return null;
	}

	/** The loaded herb patch object closest to a point, within 15 tiles; null if none. */
	private net.runelite.api.GameObject herbPatchNear(Farm.Patch patch, net.runelite.api.coords.WorldPoint at)
	{
		net.runelite.api.GameObject best = null;
		int bestDist = 16;
		for (net.runelite.api.GameObject o : herbPatches.keySet())
		{
			int d = o.getWorldLocation().distanceTo2D(at);
			if (typeAt(o, patch) == HerbRun.PlotType.HERB && o.getPlane() == at.getPlane() && d < bestDist)
			{
				best = o;
				bestDist = d;
			}
		}
		return best;
	}

	/** The closest patch at a stop that still needs doing (for stops without a herb), or null. */
	private net.runelite.api.GameObject nearestToDo(HerbRun.Stop stop, net.runelite.api.Player me)
	{
		net.runelite.api.GameObject best = null;
		int bestDist = Integer.MAX_VALUE;
		for (net.runelite.api.GameObject o : toDo(stop))
		{
			int d = o.getWorldLocation().distanceTo2D(me.getWorldLocation());
			if (d < bestDist)
			{
				best = o;
				bestDist = d;
			}
		}
		return best;
	}

	/** Patches at a stop with something left to do; finished ones drop their outline. */
	private List<net.runelite.api.GameObject> toDo(HerbRun.Stop stop)
	{
		Set<HerbRun.PlotType> left = EnumSet.noneOf(HerbRun.PlotType.class);
		for (HerbRun.Plot p : stop.getPlots())
		{
			if (HerbRun.needsVisit(p, run.isCompost()))
			{
				left.add(p.getType());
			}
		}
		List<net.runelite.api.GameObject> out = new ArrayList<>();
		net.runelite.api.GameObject herb = left.contains(HerbRun.PlotType.HERB) ? herbPatchNear(stop.getPatch(), stop.getPoint()) : null;
		if (herb != null)
		{
			out.add(herb);
		}
		// Allotments one by one: each is done when its own plot is, once you've used it.
		Set<Integer> tied = new HashSet<>();
		boolean untiedLeft = false;
		int untied = 0;
		for (HerbRun.Plot p : stop.getPlots())
		{
			if (p.getType() == HerbRun.PlotType.ALLOTMENT)
			{
				if (p.getVarbit() != -1)
				{
					tied.add(p.getVarbit());
				}
				else
				{
					untied++;
					untiedLeft |= HerbRun.needsVisit(p, run.isCompost());
				}
			}
		}
		int[] side = PatchStatus.sideVarbits(stop.getPatch());
		int freeAllotments = 0;
		for (int i = 1; i < side.length; i++)
		{
			freeAllotments += tied.contains(side[i]) ? 0 : 1;
		}
		for (net.runelite.api.GameObject o : herbPatches.keySet())
		{
			HerbRun.PlotType type = typeAt(o, stop.getPatch());
			if (type == null || type == HerbRun.PlotType.HERB || !left.contains(type) || o.getPlane() != stop.getPoint().getPlane()
				|| o.getWorldLocation().distanceTo2D(stop.getPoint()) > 15)
			{
				continue;
			}
			Integer v = patchVarbits.get(o);
			if (type == HerbRun.PlotType.ALLOTMENT && v != null)
			{
				HerbRun.Plot mine = stop.getPlots().stream().filter(p -> p.getVarbit() == v).findFirst().orElse(null);
				if (mine != null ? !HerbRun.needsVisit(mine, run.isCompost())
					// Not used yet: one of the plots still to do, unless something else grows there and another is free.
					: !untiedLeft || untied < freeAllotments && PatchStatus.sideTask(true, client.getVarbitValue(v)) == PatchStatus.Task.GROWING)
				{
					continue;
				}
			}
			out.add(o);
		}
		return out;
	}

	/** World map marker for the stop (snaps to the map's edge; click to jump there). */
	private void mark(net.runelite.api.coords.WorldPoint at, String name)
	{
		if (mapMarker != null && at != null && at.equals(mapMarker.getWorldPoint()))
		{
			return;
		}
		if (mapMarker != null)
		{
			worldMapPoints.remove(mapMarker);
			mapMarker = null;
		}
		if (at != null)
		{
			java.awt.image.BufferedImage icon = spriteManager.getSprite(NavOverlay.FARMING_ICON, 0);
			mapMarker = net.runelite.client.ui.overlay.worldmap.WorldMapPoint.builder()
				.worldPoint(at).image(icon != null ? icon : NavOverlay.PIN).snapToEdge(true).jumpOnClick(true)
				.name("Herb run: " + name).tooltip("Herb run: " + name).build();
			worldMapPoints.add(mapMarker);
		}
	}

	/** "north-east" from the player to a point, "" when there. */
	private String direction(net.runelite.api.coords.WorldPoint to)
	{
		net.runelite.api.Player me = client.getLocalPlayer();
		net.runelite.api.WorldView wv = client.getTopLevelWorldView();
		if (me == null || to == null || wv == null || wv.isInstance())
		{
			// An instance's coordinates (e.g. your house) aren't the real world's.
			return "";
		}
		int dx = to.getX() - me.getWorldLocation().getX();
		int dy = to.getY() - me.getWorldLocation().getY();
		String ns = dy > Math.abs(dx) / 2 ? "north" : -dy > Math.abs(dx) / 2 ? "south" : "";
		String ew = dx > Math.abs(dy) / 2 ? "east" : -dx > Math.abs(dy) / 2 ? "west" : "";
		String dir = ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
		return dir;
	}

	/**
	 * Lines for the panel: "Patch|state|detail;detail" per stop. The current
	 * stop's details are the teleport (on the way) or each plot's steps (there).
	 */
	private String[] runRows(List<String> missing)
	{
		if (run == null)
		{
			return null;
		}
		List<HerbRun.Stop> stops = run.getStops();
		String[] rows = new String[stops.size() + 1];
		for (int i = 0; i < stops.size(); i++)
		{
			HerbRun.Stop st = stops.get(i);
			String state = i < run.getIndex() ? "Done" : i == run.getIndex() ? "Now" : "";
			StringBuilder detail = new StringBuilder();
			if (i == run.getIndex())
			{
				if (!missing.isEmpty())
				{
					// Left the bank short: say so before the first teleport, not at the patch.
					detail.append("Not carrying: ").append(String.join(", ", missing)).append(";");
				}
				if (!atStop)
				{
					if (st == ringFor)
					{
						detail.append(walk.size() > 1 ? "Follow the path to the fairy ring" : "Walk to the fairy ring")
							.append(", dial ").append(st.getTeleport().getName().replace("Fairy ring ", ""));
					}
					else if (walk.size() > 1)
					{
						detail.append("Follow the path");
					}
					else if (st.getTeleport() == null || st == teleportedTo)
					{
						// No teleport, or already teleported in: it's a walk from here.
						String dir = direction(st.getPoint());
						detail.append(dir.isEmpty() ? "Walk to the patch" : "Walk to the patch, " + dir);
					}
					else if (ringCloak(st) != -1 && st != atRing && st != ringFor)
					{
						detail.append(Farm.viaRing(ringCloak(st), st.getTeleport()));
					}
					else
					{
						String how = Farm.how(st.getTeleport(), st.getPatch());
						detail.append(st.getTeleport().getName()).append(how.isEmpty() ? "" : ": " + how);
					}
				}
				else
				{
					// Dead crops can be brought back with Resurrect Crops (Arceuus, 78 Magic).
					boolean resurrect = client.getVarbitValue(VarbitID.SPELLBOOK) == Farm.ARCEUUS
						&& client.getBoostedSkillLevel(Skill.MAGIC) >= 78;
					for (HerbRun.Plot p : st.getPlots())
					{
						String status = HerbRun.status(p, run.isCompost());
						detail.append(detail.length() > 0 ? ";" : "").append(p.getType().getLabel()).append(": ")
							.append(resurrect && status.equals("Clear") ? "Clear or resurrect" : status);
					}
				}
			}
			rows[i] = st.getName() + "|" + state + "|" + detail;
		}
		// Last row: a note for the panel footer.
		rows[stops.size()] = run.finished() ? "Run done in " + FarmPlan.minutes((System.currentTimeMillis() - run.getStartedAt()) / 1000) : "";
		return rows;
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
		if (persist)
		{
			configManager.setConfiguration(PotionPrepConfig.GROUP, "potion", potion.name());
		}
		relayout();
		pushToPanel(null);
	}

	private boolean farmMode()
	{
		return MODE_FARM.equals(config.mode());
	}

	/**
	 * Rebuild the tracked items and the layout for the current mode. The
	 * layout is only re-saved (and the open tab refreshed) when it changed.
	 */
	private void relayout()
	{
		if (selected == null)
		{
			return;
		}
		trackedIds.clear();
		List<int[]> rows;
		if (farmMode())
		{
			farmPlan = computeFarm();
			updateWithdrawOverlay();
			for (int[] row : farmPlan.getLayoutRows())
			{
				Arrays.stream(row).forEach(trackedIds::add);
			}
			// Your saved gear, plus anything for the run you can wear (secateurs,
			// teleport capes, amulets and rings) in slots it leaves free.
			Gear.Worn worn = Gear.wear(savedGear(true), farmPlan.getLayoutRows(), this::equipSlot);
			rows = worn.rows;
			gearRows = Gear.fromSlots(worn.slots);
			trackGear();
		}
		else
		{
			withdrawOverlay.set(false, Collections.emptyMap(), Collections.emptyMap());
			// What you hold shapes the layout: a Herblore cape skips cleaning, held dust skips crushing.
			Map<Integer, Integer> held = combinedCounts();
			trackedIds.addAll(PotionPlan.layoutItems(selected, held));
			layoutHasPie = pieNeeded();
			rows = new ArrayList<>(PotionPlan.layoutRows(selected, held));
			if (layoutHasPie)
			{
				// Eating the pie is the first task.
				rows.add(0, PotionPlan.PIE_IDS);
				Arrays.stream(PotionPlan.PIE_IDS).forEach(trackedIds::add);
			}
			collapsed = computeCollapsed();
			List<int[]> shown = new ArrayList<>();
			for (int[] row : rows)
			{
				int[] c = collapse(row);
				// A decant row of storage-only potions can collapse to nothing: no blank bank row.
				if (c.length > 0)
				{
					shown.add(c);
				}
			}
			rows = shown;
			// Herblore gear you own; the missing-items infobox says what isn't worn.
			gearRows = gearRows();
			trackGear();
		}

		int[] positions = place(rows);
		if (Arrays.equals(positions, lastLayout))
		{
			return;
		}
		lastLayout = positions;
		Layout layout = new Layout(TAG);
		for (int pos = 0; pos < positions.length; pos++)
		{
			if (positions[pos] != -1)
			{
				layout.setItemAtPos(positions[pos], pos);
			}
		}
		layoutManager.saveLayout(layout);
		if (TAG.equals(bankTagsService.getActiveTag()) && isBankOpen())
		{
			openTag();
		}
	}

	/** @return item id per bank slot, -1 for empty */
	private int[] place(List<int[]> rows)
	{
		int col = Math.max(1, Math.min(BANK_COLUMNS, config.column())) - 1;
		Map<Integer, Integer> slots = new HashMap<>();
		List<boolean[]> occupied = new ArrayList<>();
		for (int row = 0; row < rows.size(); row++)
		{
			int[] ids = rows.get(row);
			// Each action row ends at the chosen column and runs leftwards,
			// or rightwards when there isn't room on the left.
			int start = Math.max(0, Math.min(col - (ids.length - 1), BANK_COLUMNS - ids.length));
			boolean[] used = new boolean[BANK_COLUMNS];
			for (int i = 0; i < ids.length; i++)
			{
				slots.put(row * BANK_COLUMNS + start + i, ids[i]);
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
					slots.put((g + r) * BANK_COLUMNS + c, id);
				}
			}
		}
		int size = slots.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
		int[] out = new int[size];
		Arrays.fill(out, -1);
		slots.forEach((pos, id) -> out[pos] = id);
		return out;
	}

	/** Equipment slot an item is worn in, or -1 if it can't be worn. */
	private int equipSlot(int id)
	{
		net.runelite.client.game.ItemStats stats = itemManager.getItemStats(id);
		return stats != null && stats.isEquipable() && stats.getEquipment() != null ? stats.getEquipment().getSlot() : -1;
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

	// --- Herb run ---

	/**
	 * Amounts still to withdraw for the herb run's bank view: what the run
	 * needs minus what you're already carrying (inventory, and the rune
	 * pouch's runes if the pouch is in your inventory). Done items show nothing.
	 */
	private void updateWithdrawOverlay()
	{
		FarmPlan.Account acct = account();
		Map<Integer, Integer> carried = carried(acct);
		Map<Integer, Integer> available = combinedCounts();
		acct.getPouch().forEach((id, n) -> available.merge(id, n, Integer::sum));

		Map<Integer, Integer> left = new HashMap<>();
		Map<Integer, Integer> canGet = new HashMap<>();
		farmPlan.getWithdraw().forEach((id, need) ->
		{
			int still = need - carried.getOrDefault(id, 0);
			if (still > 0)
			{
				left.put(id, still);
				// Enough in the bank to cover what's left?
				canGet.put(id, available.getOrDefault(id, 0) - carried.getOrDefault(id, 0));
			}
		});
		withdrawOverlay.set(TAG.equals(bankTagsService.getActiveTag()), left, canGet);
	}

	/** Inventory (seed box included), plus the rune pouch's runes when the pouch is in it. */
	private Map<Integer, Integer> carried(FarmPlan.Account acct)
	{
		Map<Integer, Integer> carried = new HashMap<>(invCounts);
		boolean pouchCarried = false;
		for (int id : Farm.RUNE_POUCHES)
		{
			pouchCarried |= invCounts.getOrDefault(id, 0) > 0;
		}
		if (pouchCarried)
		{
			acct.getPouch().forEach((id, n) -> carried.merge(id, n, Integer::sum));
		}
		return carried;
	}

	/**
	 * What the run needs that you're neither carrying nor wearing: counted
	 * things ("3 x Ranarr seed"), tools and teleport items. Empty once you've
	 * reached the first patch, when what's left to need has changed.
	 */
	private List<String> notCarried(FarmPlan fp)
	{
		return notCarried(fp, new ArrayList<>());
	}

	/** @param ids filled with an item for each line, for its icon */
	private List<String> notCarried(FarmPlan fp, List<Integer> ids)
	{
		List<String> out = new ArrayList<>();
		if (run == null || fp == null || run.getIndex() > 0 || run.current() == null || run.current().isVisited() || atStop)
		{
			return out;
		}
		Map<Integer, Integer> carried = carried(account());
		java.util.function.IntPredicate has = id -> carried.getOrDefault(id, 0) > 0 || wornIds.contains(id);
		Map<Integer, String> names = new HashMap<>();
		for (FarmPlan.Supply sup : fp.getSupplies())
		{
			names.putIfAbsent(sup.getItemId(), sup.getName());
		}
		fp.getWithdraw().forEach((id, need) ->
		{
			int still = need - carried.getOrDefault(id, 0);
			if (still > 0)
			{
				String name = names.containsKey(id) ? names.get(id) : itemManager.getItemComposition(id).getName();
				out.add(still + " x " + name);
				ids.add(id);
			}
		});
		Set<Integer> runes = new HashSet<>();
		for (Farm.Rune r : Farm.Rune.values())
		{
			runes.add(r.getItemId());
		}
		for (FarmPlan.Supply sup : fp.getSupplies())
		{
			// Tools and the like: one to bring. Stored ones are fetched at the patch.
			int id = sup.getItemId();
			if (sup.getWhere() == null && sup.getHave() > 0 && !fp.getWithdraw().containsKey(id) && !runes.contains(id)
				&& sup.getNeed() <= 1 && !has.test(id))
			{
				out.add(sup.getName());
				ids.add(id);
			}
		}
		Set<Farm.Teleport> teles = new java.util.LinkedHashSet<>();
		for (FarmPlan.Stop st : fp.getStops())
		{
			if (!st.isLocked() && (!st.isGrowing() || st.isSideOnly()) && st.getProblem() == null && st.getTeleport() != null
				&& st.getItemId() != -1 && !Farm.consumable(st.getItemId()))
			{
				teles.add(st.getTeleport());
			}
		}
		// The cape or cloak to reach a fairy ring (tabs are counted with the withdrawals).
		Set<Integer> ways = new java.util.LinkedHashSet<>();
		for (FarmPlan.Stop st : fp.getStops())
		{
			if (st.getRingWay() >= 0 && !Farm.consumable(st.getRingWay()))
			{
				ways.add(st.getRingWay());
			}
		}
		for (int way : ways)
		{
			int[] kind = Farm.ringWayKind(way);
			if (!(kind == Farm.MONASTERY_CLOAKS && teles.contains(Farm.Teleport.ARDOUGNE_CLOAK)) && Arrays.stream(kind).noneMatch(has))
			{
				out.add(kind == Farm.MONASTERY_CLOAKS ? "Ardougne cloak" : itemManager.getItemComposition(way).getName());
				ids.add(kind.length > 0 ? kind[0] : way);
			}
		}
		for (Farm.Teleport t : teles)
		{
			// Any charge or tier of it will do.
			if (Arrays.stream(t.getIds()).noneMatch(has))
			{
				out.add(t.getName());
				ids.add(t.getIds()[0]);
			}
		}
		return out;
	}

	/** The infobox warning of what's missing: the herb run's items before the first patch, or Herblore gear not worn while prepping. */
	private final MissingOverlay missingOverlay = new MissingOverlay();

	private void updateMissing()
	{
		List<String> lines = new ArrayList<>();
		List<Integer> ids = new ArrayList<>();
		String title = null;
		if (client.getGameState() == GameState.LOGGED_IN && config.missingInfobox())
		{
			if (farmMode() && run != null)
			{
				// The plan's only worked out with the bank; until then, the panel's (or a fresh one).
				if (farmPlan == null)
				{
					farmPlan = panelFarmPlan != null ? panelFarmPlan : computeFarm();
				}
				lines = notCarried(farmPlan, ids);
				title = "Herb run: not carrying";
			}
			else if (!farmMode() && selected != null && panelOpen)
			{
				// The panel's open with a potion picked:
				// Herblore gear you own (in the bank, inventory or a placeholder) but aren't wearing.
				for (int id : Gear.notWorn(i -> bankCounts != null && bankCounts.getOrDefault(i, 0) > 0
					|| invCounts.containsKey(i) || bankPlaceholders.contains(i), wornIds::contains))
				{
					lines.add(itemManager.getItemComposition(id).getName());
				}
				title = "Not wearing";
			}
		}
		missingOverlay.set(title, lines);
	}

	private FarmPlan computeFarm()
	{
		return FarmPlan.compute(farmSettings(), combinedCounts(), wornIds, account());
	}

	private FarmPlan.Account account()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return FarmPlan.Account.builder().build();
		}
		Map<Integer, Integer> counts = combinedCounts();
		boolean pouch = false;
		for (int id : Farm.RUNE_POUCHES)
		{
			pouch |= counts.getOrDefault(id, 0) > 0;
		}
		Set<String> spirits = new HashSet<>();
		if (config.spiritGuild())
		{
			spirits.add("GUILD");
		}
		if (config.spiritPortSarim())
		{
			spirits.add("PORT_SARIM");
		}
		if (config.spiritHosidius())
		{
			spirits.add("HOSIDIUS");
		}
		return AccountReader.read(client, quests, fairyRings, portals(), wornIds, pouch).toBuilder()
			.spiritTrees(spirits)
			.spiritHouse(config.spiritHouse())
			.fairyHouse(houseRing())
			.seedVault(seedVault)
			.bucketUses(Math.max(bucket().getUses(), 0))
			.bucketType(bucket().getType())
			.cloakUses(cloakUsesToday())
			.whistleCharges(savedCharges("whistleCharges"))
			.xericCharges(savedCharges("xericCharges"))
			.patchStatus(PatchStatus.read(configManager, java.time.Instant.now().getEpochSecond()))
			.sidePlots(PatchStatus.readSide(configManager, java.time.Instant.now().getEpochSecond()))
			.skipGrowing(config.skipGrowing())
			.saveRunes(config.saveRunes())
			.now(java.time.Instant.now().getEpochSecond())
			.build();
	}

	/** Portals set in config, plus any seen in the house; null if neither is known. */
	private Set<String> portals()
	{
		Set<String> out = new HashSet<>();
		for (Farm.Teleport t : Farm.Teleport.values())
		{
			if (t.getKind() == Farm.Teleport.Kind.PORTAL
				&& "true".equals(configManager.getConfiguration(PotionPrepConfig.GROUP, "portal_" + t.getPortal())))
			{
				out.add(t.getPortal());
			}
		}
		if (!config.scanHouse())
		{
			return out;
		}
		Set<String> seen = savedPortals();
		if (seen == null)
		{
			return out.isEmpty() ? null : out;
		}
		out.addAll(seen);
		return out;
	}

	// --- House portals: seen by visiting the house, remembered per account ---

	private static final Map<Integer, String> PORTAL_OBJECTS = new HashMap<>();

	static
	{
		for (int id : new int[]{ObjectID.POH_PORTAL_TEAK_FALADOR, ObjectID.POH_PORTAL_MAG_FALADOR, ObjectID.POH_PORTAL_MARBLE_FALADOR})
		{
			PORTAL_OBJECTS.put(id, "FALADOR");
		}
		for (int id : new int[]{ObjectID.POH_PORTAL_TEAK_CAMELOT, ObjectID.POH_PORTAL_MAG_CAMELOT, ObjectID.POH_PORTAL_MARBLE_CAMELOT})
		{
			PORTAL_OBJECTS.put(id, "CAMELOT");
		}
		for (int id : new int[]{ObjectID.POH_PORTAL_TEAK_ARDOUGNE, ObjectID.POH_PORTAL_MAG_ARDOUGNE, ObjectID.POH_PORTAL_MARBLE_ARDOUGNE})
		{
			PORTAL_OBJECTS.put(id, "ARDOUGNE");
		}
		for (int id : new int[]{ObjectID.POH_PORTAL_TEAK_FISHINGGUILD, ObjectID.POH_PORTAL_MAG_FISHINGGUILD,
			ObjectID.POH_PORTAL_MARBLE_FISHINGGUILD})
		{
			PORTAL_OBJECTS.put(id, "FISHING_GUILD");
		}
	}

	/** In a house this scene (its exit portal spawned). */
	private boolean inHouse;
	/** This scene is a house, for as long as it's loaded. */
	private boolean houseScene;
	/** The house in this scene has a fairy ring. */
	private boolean houseHasRing;
	private final Set<String> housePortals = new HashSet<>();

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		int id = event.getGameObject().getId();
		if (id == ObjectID.POH_EXIT_PORTAL)
		{
			inHouse = true;
			houseScene = true;
		}
		if (id == ObjectID.POH_FAIRY_RING || id == ObjectID.POH_SPIRIT_RING)
		{
			houseHasRing = true;
		}
		track(event.getGameObject());
		String portal = PORTAL_OBJECTS.get(id);
		if (portal != null)
		{
			housePortals.add(portal);
		}
	}

	/** Game ticks since the herb run was last refreshed for growth. */
	private int ticksSinceRefresh;

	@Subscribe
	public void onGameObjectDespawned(net.runelite.api.events.GameObjectDespawned event)
	{
		herbPatches.remove(event.getGameObject());
		patchVarbits.remove(event.getGameObject());
		rings.remove(event.getGameObject());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updateMissing();
		Widget box = client.getWidget(net.runelite.api.gameval.InterfaceID.Objectbox.TEXT);
		if (box != null && !box.isHidden() && bucket().onDialog(box.getText()))
		{
			saveBucket();
		}
		if (bucket != null && bucket.onTick())
		{
			saveBucket();
		}
		if (run != null)
		{
			boolean was = atStop;
			updateRunGuide();
			if (was != atStop)
			{
				pushToPanel(null);
			}
		}
		// Patches finish growing while you play: refresh about once a minute.
		if (++ticksSinceRefresh >= 100)
		{
			ticksSinceRefresh = 0;
			farmDirty = true;
		}
		if (!inHouse)
		{
			return;
		}
		// The whole house has loaded by the first tick: remember exactly what's built.
		inHouse = false;
		boolean own = Boolean.TRUE.equals(ownHouseEntry);
		ownHouseEntry = null;
		// Only learn from the player's own house, known from how they got in.
		if (!config.scanHouse() || !own)
		{
			return;
		}
		Set<String> saved = savedPortals();
		if (!Boolean.valueOf(houseHasRing).equals(configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "pohRing", Boolean.class)))
		{
			configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "pohRing", houseHasRing);
			farmDirty = true;
		}
		if (saved == null || !saved.equals(housePortals))
		{
			configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "pohPortals",
				housePortals.isEmpty() ? "-" : String.join(",", housePortals));
			farmDirty = true;
		}
	}

	/**
	 * No variable says whose house you're in, so tell from how you got in:
	 * the house portal's "Home", a house tab, the house spell or a cape's
	 * house teleport mean your own; "Friend's house" means someone else's.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		net.runelite.api.MenuAction action = event.getMenuAction();
		if (action == net.runelite.api.MenuAction.WIDGET_TARGET_ON_GAME_OBJECT || action.name().startsWith("GAME_OBJECT_"))
		{
			// Which patch you're using things on: says which allotment a planting or compost message is about.
			lastPlotVarbit = -1;
			for (Map.Entry<net.runelite.api.GameObject, Integer> e : patchVarbits.entrySet())
			{
				net.runelite.api.GameObject o = e.getKey();
				net.runelite.api.Point at = o.getSceneMinLocation();
				if (o.getId() == event.getId() && at != null && at.getX() == event.getParam0() && at.getY() == event.getParam1())
				{
					lastPlotVarbit = e.getValue();
					break;
				}
			}
		}
		if (event.getMenuAction() == net.runelite.api.MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			// Says whose compost the next "You treat..." message was.
			Widget selected = client.getSelectedWidget();
			lastUseWasBucket = selected != null && selected.getItemId() == Farm.BOTTOMLESS_BUCKET;
		}
		String option = net.runelite.client.util.Text.removeTags(event.getMenuOption()).toLowerCase();
		String target = net.runelite.client.util.Text.removeTags(event.getMenuTarget()).toLowerCase();
		if (option.contains("friend"))
		{
			ownHouseEntry = false;
		}
		else if (option.equals("home") || option.equals("build mode") || option.contains("tele to poh")
			|| ((option.equals("break") || option.equals("cast")) && target.contains("teleport to house")))
		{
			ownHouseEntry = true;
		}
	}

	// --- Teleport charges, from the items' own chat messages ---

	private static final String COUNT = "(no|one|[\\d,]+)";
	/** Sent on Check and after every flight. */
	/** "You have used 2 of your 3 Ardougne Farm teleports for today." (wording per Farm Run Autopilot). */
	private static final java.util.regex.Pattern DAILY_USES = java.util.regex.Pattern.compile(
		"You have used (\\d+) of your (\\d+) (.+) teleports for today");
	private static final java.util.regex.Pattern WHISTLE = java.util.regex.Pattern.compile(
		"Your quetzal whistle has " + COUNT + " charges? remaining\\.");
	private static final java.util.regex.Pattern TALISMAN = java.util.regex.Pattern.compile(
		"(?:The talisman has|Your talisman now has) " + COUNT + " charges?\\.");

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String msg = net.runelite.client.util.Text.removeTags(event.getMessage());
		// Only credit the stop you're at, never one you've walked away from.
		if (run != null && atStop && run.onMessage(msg, lastPlotVarbit))
		{
			updateRunGuide();
			pushToPanel(null);
		}
		Boolean usedBucket = lastUseWasBucket;
		if (usedBucket == null)
		{
			// Not seen: it was the bucket if that's the only compost on you.
			usedBucket = invCounts.getOrDefault(Farm.BOTTOMLESS_BUCKET, 0) > 0
				&& Arrays.stream(Farm.COMPOST).noneMatch(id -> invCounts.getOrDefault(id, 0) > 0);
		}
		if (BottomlessBucket.TREATED.matcher(msg).find())
		{
			lastUseWasBucket = null;
		}
		if (bucket().onMessage(msg, usedBucket))
		{
			saveBucket();
		}
		java.util.regex.Matcher m = DAILY_USES.matcher(msg);
		if (m.find())
		{
			String what = m.group(3).toLowerCase(java.util.Locale.ROOT);
			// The cloak's varbit only keeps one bit of the count, so its uses come from here.
			if (what.contains("farm") || what.contains("ardougne"))
			{
				configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "cloakUses",
					java.time.LocalDate.now(java.time.ZoneOffset.UTC) + ":" + m.group(1));
				farmDirty = true;
			}
			return;
		}
		m = WHISTLE.matcher(msg);
		if (m.find())
		{
			saveCharges("whistleCharges", m.group(1));
			return;
		}
		m = TALISMAN.matcher(msg);
		if (m.find())
		{
			saveCharges("xericCharges", m.group(1));
		}
	}

	private void saveCharges(String key, String count)
	{
		int n = count.equals("no") ? 0 : count.equals("one") ? 1 : Integer.parseInt(count.replace(",", ""));
		configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, key, n);
		farmDirty = true;
	}

	/** The bottomless bucket's tracked uses for this account. */
	private BottomlessBucket bucket()
	{
		if (bucket == null)
		{
			Integer uses = configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "bucketUses", Integer.class);
			Integer type = configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "bucketType", Integer.class);
			BottomlessBucket b = new BottomlessBucket(uses != null ? uses : -1, type != null ? type : 0);
			if (configManager.getRSProfileKey() == null)
			{
				// No account yet: don't keep a blank one for whoever logs in.
				return b;
			}
			bucket = b;
		}
		return bucket;
	}

	private void saveBucket()
	{
		configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "bucketUses", bucket.getUses());
		configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "bucketType", bucket.getType());
		farmDirty = true;
	}

	/** Ardougne cloak farm teleports used today (UTC, when the game resets them), from chat. */
	private int cloakUsesToday()
	{
		String v = configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "cloakUses");
		String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
		if (v == null || !v.startsWith(today + ":"))
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(v.substring(today.length() + 1));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** Charges last reported for an item, -1 if never seen. */
	private int savedCharges(String key)
	{
		Integer n = configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, key, Integer.class);
		return n != null ? n : -1;
	}

	/** Portal destinations from the last house visit, or null if never visited. */
	private Set<String> savedPortals()
	{
		String v = configManager.getRSProfileConfiguration(PotionPrepConfig.GROUP, "pohPortals");
		if (v == null || v.isEmpty())
		{
			return null;
		}
		Set<String> out = new HashSet<>();
		for (String p : v.split(","))
		{
			if (!p.equals("-"))
			{
				out.add(p);
			}
		}
		return out;
	}

	FarmPlan.Settings farmSettings()
	{
		Farm.HerbSeed herb = parseEnum(Farm.HerbSeed.class, config.farmHerb(), Farm.HerbSeed.RANARR);
		Farm.FlowerSeed flower = parseEnum(Farm.FlowerSeed.class, config.farmFlower(), Farm.FlowerSeed.LIMPWURT);
		Farm.AllotmentSeed allotment = parseEnum(Farm.AllotmentSeed.class, config.farmAllotment(), Farm.AllotmentSeed.NONE);
		Set<Farm.Patch> patches;
		if (config.farmPatches().isEmpty())
		{
			patches = FarmPlan.defaultPatches();
		}
		else
		{
			patches = EnumSet.noneOf(Farm.Patch.class);
			for (String name : config.farmPatches().split(","))
			{
				Farm.Patch p = parseEnum(Farm.Patch.class, name, null);
				if (p != null)
				{
					patches.add(p);
				}
			}
		}
		Map<Farm.Patch, Farm.Teleport> overrides = new EnumMap<>(Farm.Patch.class);
		for (String pair : config.farmTeleports().split(","))
		{
			String[] kv = pair.split(":");
			if (kv.length == 2)
			{
				Farm.Patch p = parseEnum(Farm.Patch.class, kv[0], null);
				Farm.Teleport t = parseEnum(Farm.Teleport.class, kv[1], null);
				if (p != null && t != null)
				{
					overrides.put(p, t);
				}
			}
		}
		return FarmPlan.Settings.builder().herb(herb).flower(flower).allotment(allotment)
			.patches(patches).overrides(overrides).order(patchOrder()).build();
	}

	/** The saved run order, with any patch it doesn't mention added in the default order. */
	List<Farm.Patch> patchOrder()
	{
		List<Farm.Patch> order = new ArrayList<>();
		for (String name : config.farmOrder().split(","))
		{
			Farm.Patch p = parseEnum(Farm.Patch.class, name, null);
			if (p != null && !order.contains(p))
			{
				order.add(p);
			}
		}
		for (Farm.Patch p : Farm.Patch.values())
		{
			if (!order.contains(p))
			{
				order.add(p);
			}
		}
		return order;
	}

	/** From the panel (EDT): the player dragged the patches into a new order. */
	void setPatchOrder(List<Farm.Patch> order)
	{
		StringBuilder sb = new StringBuilder();
		for (Farm.Patch p : order)
		{
			sb.append(sb.length() > 0 ? "," : "").append(p.name());
		}
		setFarmSetting("farmOrder", sb.toString());
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback)
	{
		try
		{
			return name == null || name.isEmpty() ? fallback : Enum.valueOf(type, name);
		}
		catch (IllegalArgumentException e)
		{
			return fallback;
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
		// Quests may have been finished since login; re-check on each bank visit.
		questsDirty = true;

		boolean ours = TAG.equals(bankTagsService.getActiveTag());
		if (farmMode() && farmPlan != null)
		{
			updateWithdrawOverlay();
		}
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
		for (int v : FARM_VARBITS)
		{
			if (event.getVarbitId() == v)
			{
				// Leprechaun or bottomless bucket changed; rebuild once this tick.
				farmDirty = true;
				if ((v == VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE || v == VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY)
					&& client.getGameState() == GameState.LOGGED_IN
					&& bucket().onLeprechaun(ToolLeprechaun.storedBucket(client::getVarbitValue),
						ToolLeprechaun.storedBucketUses(client::getVarbitValue)))
				{
					saveBucket();
				}
				break;
			}
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		highlightMenuOption();
		if (questsDirty && client.getGameState() == GameState.LOGGED_IN)
		{
			questsDirty = false;
			quests = AccountReader.quests(client);
			fairyRings = AccountReader.fairyRings(client);
			farmDirty = true;
		}
		if (farmDirty)
		{
			farmDirty = false;
			if (farmMode())
			{
				relayout();
			}
			pushToPanel(null);
		}
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
			relayout();
			pushToPanel(null);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			shownThisVisit = false;
			saveAccount();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK)
		{
			readBank(event.getItemContainer());
			relayout();
			pushToPanel(null);
		}
		else if (event.getContainerId() == InventoryID.WORN)
		{
			wornIds.clear();
			count(event.getItemContainer()).keySet().forEach(id -> wornIds.add(itemManager.canonicalize(id)));
			relayout();
			pushToPanel(null);
		}
		else if (event.getContainerId() == InventoryID.SEED_BOX)
		{
			seedBox = count(event.getItemContainer());
			readInventory(client.getItemContainer(InventoryID.INV));
			relayout();
			pushToPanel(null);
		}
		else if (event.getContainerId() == InventoryID.SEED_VAULT)
		{
			seedVault = count(event.getItemContainer());
			farmDirty = true;
			configManager.setRSProfileConfiguration(PotionPrepConfig.GROUP, "savedVault", encode(seedVault));
		}
		else if (event.getContainerId() == InventoryID.INV)
		{
			readInventory(event.getItemContainer());
			if (bucket().onInventory(invCounts.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET, 0) > 0,
				invCounts.getOrDefault(Farm.BOTTOMLESS_BUCKET, 0) > 0))
			{
				saveBucket();
			}
			relayout();
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
			relayout();
			pushToPanel(null);
		}
		else if (event.getSkill() == Skill.FARMING && event.getLevel() != farmingLevel)
		{
			farmingLevel = event.getLevel();
			relayout();
			pushToPanel(null);
		}
		else if (event.getSkill() == Skill.MAGIC)
		{
			farmDirty = true;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			inHouse = false;
			houseScene = false;
			houseHasRing = false;
			housePortals.clear();
			herbPatches.clear();
			rings.clear();
			patchVarbits.clear();
			pathKey = null;
			walk = Collections.emptyList();
			minimapPath.set(walk);
		}
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			// Logging in somewhere else isn't a teleport.
			lastPos = null;
			if (event.getGameState() == GameState.LOGIN_SCREEN)
			{
				// Logged out with the bank open: still remember it.
				saveAccount();
				// Different account may log in next. A run in progress is kept for this
				// account, so a disconnect or quick relog carries on where it was.
				pausedRun = run;
				pausedFor = loggedInAs;
				resetAccount();
				// Hopping keeps your inventory, so only a logout forgets it.
				invCounts.clear();
			}
			pushToPanel(null);
		}
		else if (event.getGameState() == GameState.LOGGED_IN && herbloreLevel == 0)
		{
			clientThread.invokeLater(() ->
			{
				loggedInAs = client.getAccountHash();
				if (pausedRun != null && pausedFor == loggedInAs && loggedInAs != -1)
				{
					run = pausedRun;
					scanHerbPatches();
				}
				pausedRun = null;
				herbloreLevel = client.getRealSkillLevel(Skill.HERBLORE);
				boostedLevel = client.getBoostedSkillLevel(Skill.HERBLORE);
				farmingLevel = client.getRealSkillLevel(Skill.FARMING);
				questsDirty = true;
				relayout();
				pushToPanel(null);
			});
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Time Tracking saw a patch change, or a travel setting changed: rebuild on the next tick.
		if ("timetracking".equals(event.getGroup())
			|| PotionPrepConfig.GROUP.equals(event.getGroup()) && event.getKey() != null
			&& (event.getKey().startsWith("portal_") || event.getKey().startsWith("spirit")
			|| "scanHouse".equals(event.getKey()) || "fairyHouse".equals(event.getKey()) || "skipGrowing".equals(event.getKey())
			|| "saveRunes".equals(event.getKey())))
		{
			// Travel settings: rebuild on the next tick.
			farmDirty = true;
		}
		if (PotionPrepConfig.GROUP.equals(event.getGroup()) && "column".equals(event.getKey()))
		{
			clientThread.invokeLater(this::relayout);
		}
	}

	private void readBank(ItemContainer container)
	{
		runDone = null;
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
			// Noted items (e.g. noted compost) count as the item itself.
			count(container).forEach((id, n) -> invCounts.merge(itemManager.canonicalize(id), n, Integer::sum));
		}
		if (invCounts.containsKey(ItemID.SEED_BOX) || invCounts.containsKey(ItemID.SEED_BOX_OPEN))
		{
			seedBox.forEach((id, n) -> invCounts.merge(id, n, Integer::sum));
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
		// A worn Herblore cape counts as held: it changes how potions are made.
		for (int id : PotionPlan.CAPE_IDS)
		{
			if (wornIds.contains(id))
			{
				all.merge(id, 1, Integer::sum);
			}
		}
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
		// For a tile's tooltip: what you have for that potion, without picking it.
		int herb = herbloreLevel;
		java.util.function.Function<Potion, PotionPlan> planFor = pt -> PotionPlan.compute(pt, counts,
			PotionPlan.effectiveLevel(pt, herb, boosted), PotionPlan.needsPie(pt, herb, boosted));
		String status = notice != null ? notice
			: runDone != null ? runDone
			: bankCounts == null ? "Open your bank to scan it"
			: "Bank seen " + ago(bankObservedAt);
		PotionPrepPanel p = panel;
		boolean showing = config.showInBank();
		boolean farm = farmMode();
		FarmPlan.Settings fs = farmSettings();
		// The herb run's plan is hidden in potion mode: reuse the last one rather than
		// replanning on every potion made.
		FarmPlan fp = farm || panelFarmPlan == null ? FarmPlan.compute(fs, counts, wornIds, account()) : panelFarmPlan;
		panelFarmPlan = fp;
		String[] rows = runRows(notCarried(fp));
		boolean potionsGear = false;
		boolean farmGear = savedGear(true) != null;
		Map<Integer, Integer> seeds = new HashMap<>(counts);
		seedVault.forEach((id, n) -> seeds.merge(id, n, Integer::sum));
		int farming = farmingLevel;
		SwingUtilities.invokeLater(() ->
		{
			p.update(plan, status, makeable, xp, lvl, pie, showing, planFor);
			p.updateFarm(fp, fs, status, showing, farm, seeds, farming);
			p.setGearSaved(potionsGear, farmGear);
			p.setRun(rows);
		});
	}

	private static String ago(long at)
	{
		long s = (System.currentTimeMillis() - at) / 1000;
		if (s < 60)
		{
			return "just now";
		}
		long m = s / 60;
		return m < 60 ? m + " min ago" : m < 48 * 60 ? (m / 60) + " h ago" : (m / 60 / 24) + " days ago";
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
