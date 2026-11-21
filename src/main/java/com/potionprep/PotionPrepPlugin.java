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
	@Inject private net.runelite.client.eventbus.EventBus eventBus;
	@Inject private net.runelite.client.plugins.PluginManager pluginManager;
	/** Where Shortest Path was last asked to route to, or null. */
	private net.runelite.api.coords.WorldPoint pathTarget;
	/** Where the player was last tick, to spot teleports. */
	private net.runelite.api.coords.WorldPoint lastPos;
	@Inject private RunItemOverlay runItemOverlay;
	private PathOverlay scenePath;
	private PathOverlay minimapPath;
	private NavOverlay navOverlay;
	@Inject private net.runelite.client.game.SpriteManager spriteManager;
	@Inject private net.runelite.client.ui.overlay.worldmap.WorldMapPointManager worldMapPoints;
	private net.runelite.client.ui.overlay.worldmap.WorldMapPoint mapMarker;
	/** Herb patch objects in the loaded scene, to walk right up to them. */
	private final Set<net.runelite.api.GameObject> herbPatches = new HashSet<>();
	/** Tiles of the walk being shown (empty if none). */
	private List<int[]> walk = Collections.emptyList();
	private static final Set<Integer> HERB_PATCH_OBJECTS = new HashSet<>(Arrays.asList(
		ObjectID.FARMING_HERB_PATCH_1, ObjectID.FARMING_HERB_PATCH_2, ObjectID.FARMING_HERB_PATCH_3,
		ObjectID.FARMING_HERB_PATCH_4, ObjectID.FARMING_HERB_PATCH_5, ObjectID.FARMING_HERB_PATCH_6,
		ObjectID.MYARM_HERBPATCH));

	/** The herb run being guided, or null. */
	private HerbRun run;
	/** At the run's current stop (near its patch). */
	private boolean atStop;

	@Override
	protected void startUp()
	{
		overlayManager.add(withdrawOverlay);
		overlayManager.add(runItemOverlay);
		scenePath = new PathOverlay(client, false);
		minimapPath = new PathOverlay(client, true);
		overlayManager.add(scenePath);
		overlayManager.add(minimapPath);
		navOverlay = new NavOverlay(client, spriteManager);
		overlayManager.add(navOverlay);
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
				ItemContainer worn = client.getItemContainer(InventoryID.WORN);
				if (worn != null)
				{
					count(worn).keySet().forEach(id -> wornIds.add(itemManager.canonicalize(id)));
				}
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
		overlayManager.remove(withdrawOverlay);
		overlayManager.remove(runItemOverlay);
		overlayManager.remove(scenePath);
		overlayManager.remove(minimapPath);
		overlayManager.remove(navOverlay);
		clientThread.invokeLater(this::endRun);
		panel = null;
		selected = null;
		lastLayout = null;
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
		showPath(null);
		scenePath.setOutline(null);
		navOverlay.set(null);
		mark(null, null);
		routeTo(null);
	}

	/** Where the player is versus the current stop: patch state, route, arrow and highlights. */
	private void updateRunGuide()
	{
		if (run == null)
		{
			return;
		}
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
			FarmPlan.Settings fs = farmSettings();
			Map<HerbRun.PlotType, Integer> seeds = new java.util.EnumMap<>(HerbRun.PlotType.class);
			seeds.put(HerbRun.PlotType.HERB, invCounts.getOrDefault(fs.getHerb().getSeedId(), 0));
			seeds.put(HerbRun.PlotType.FLOWER, invCounts.getOrDefault(fs.getFlower().getSeedId(), 0));
			seeds.put(HerbRun.PlotType.ALLOTMENT, invCounts.getOrDefault(fs.getAllotment().getSeedId(), 0));
			if (run.setSeedsCarried(seeds))
			{
				stop = run.current();
				atStop = stop != null && PatchPlaces.near(stop.getPoint(), me.getWorldLocation());
			}
		}
		if (atStop && !hasCompostCarried() && run.setCompostCarried(false))
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

		Set<Integer> highlight = new HashSet<>();
		if (stop != null && !atStop && stop.getItemId() != -1)
		{
			highlight.add(stop.getItemId());
		}
		else if (stop != null)
		{
			PatchStatus.Task task = stop.hasHerb() ? stop.getPlots().get(0).getTask() : null;
			if (task == PatchStatus.Task.CLEAR)
			{
				highlight.add(ItemID.SPADE);
			}
			else if (task == PatchStatus.Task.RAKE)
			{
				highlight.add(ItemID.RAKE);
			}
			// Seeds and compost aren't highlighted: you know where they are.
		}
		runItemOverlay.set(highlight);

		// Once the patch is in the loaded area, draw the walk there on the ground and minimap;
		// a minimap pin/arrow and a world map marker always point to it.
		showPath(stop);
		navOverlay.set(stop != null ? stop.getPoint() : null);
		mark(stop != null ? stop.getPoint() : null, stop != null ? stop.getName() : null);
		// Further away, Shortest Path (if installed) routes there with the teleports
		// you've enabled in it; the hint arrow marks the patch once you're close.
		routeTo(stop != null && !atStop ? stop.getPoint() : null);
		// At the stop, outline the herb patch itself (no hint arrow: that's the game's).
		scenePath.setOutline(atStop && stop.hasHerb() ? herbPatchNear(stop.getPoint()) : null);
	}

	/** Compost in your inventory: buckets, or a bottomless bucket with some in it. */
	private boolean hasCompostCarried()
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
	 * Walk from the player to the stop, drawn on the ground and minimap. Ends
	 * beside the herb patch object when it's loaded, else near the stop's spot.
	 */
	private void showPath(HerbRun.Stop stop)
	{
		List<int[]> tiles = Collections.emptyList();
		net.runelite.api.Player me = client.getLocalPlayer();
		net.runelite.api.WorldView wv = client.getTopLevelWorldView();
		if (stop != null && me != null && wv != null && !wv.isInstance() && stop.getPoint().getPlane() == wv.getPlane())
		{
			net.runelite.api.CollisionData[] maps = wv.getCollisionMaps();
			net.runelite.api.coords.LocalPoint from = me.getLocalLocation();
			if (from != null && maps != null && maps[wv.getPlane()] != null)
			{
				int[][] flags = maps[wv.getPlane()].getFlags();
				net.runelite.api.GameObject patch = stop.hasHerb() ? herbPatchNear(stop.getPoint()) : null;
				if (patch != null)
				{
					net.runelite.api.Point min = patch.getSceneMinLocation();
					net.runelite.api.Point max = patch.getSceneMaxLocation();
					tiles = WalkPath.find(flags, from.getSceneX(), from.getSceneY(), min.getX(), min.getY(), max.getX(), max.getY());
				}
				else
				{
					net.runelite.api.coords.LocalPoint to = net.runelite.api.coords.LocalPoint.fromWorld(wv, stop.getPoint());
					if (to != null)
					{
						tiles = WalkPath.find(flags, from.getSceneX(), from.getSceneY(), to.getSceneX(), to.getSceneY(), 2);
					}
				}
			}
		}
		walk = tiles;
		scenePath.set(tiles);
		minimapPath.set(tiles);
	}

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
						if (o != null && HERB_PATCH_OBJECTS.contains(o.getId()))
						{
							herbPatches.add(o);
						}
					}
				}
			}
		}
	}

	/** The loaded herb patch object closest to a point, within 15 tiles; null if none. */
	private net.runelite.api.GameObject herbPatchNear(net.runelite.api.coords.WorldPoint at)
	{
		net.runelite.api.GameObject best = null;
		int bestDist = 16;
		for (net.runelite.api.GameObject o : herbPatches)
		{
			int d = o.getWorldLocation().distanceTo2D(at);
			if (o.getPlane() == at.getPlane() && d < bestDist)
			{
				best = o;
				bestDist = d;
			}
		}
		return best;
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

	/** "north-east, 120 tiles" from the player to a point. */
	private String direction(net.runelite.api.coords.WorldPoint to)
	{
		net.runelite.api.Player me = client.getLocalPlayer();
		if (me == null || to == null)
		{
			return "";
		}
		int dx = to.getX() - me.getWorldLocation().getX();
		int dy = to.getY() - me.getWorldLocation().getY();
		String ns = dy > Math.abs(dx) / 2 ? "north" : -dy > Math.abs(dx) / 2 ? "south" : "";
		String ew = dx > Math.abs(dy) / 2 ? "east" : -dx > Math.abs(dy) / 2 ? "west" : "";
		String dir = ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
		return dir + ", " + Math.max(Math.abs(dx), Math.abs(dy)) + " tiles";
	}

	/** Ask Shortest Path for a route (or to clear it), the way other plugins drive it. */
	private void routeTo(net.runelite.api.coords.WorldPoint target)
	{
		if (java.util.Objects.equals(target, pathTarget))
		{
			return;
		}
		pathTarget = target;
		if (target == null)
		{
			eventBus.post(new net.runelite.client.events.PluginMessage("shortestpath", "clear"));
			return;
		}
		Map<String, Object> data = new HashMap<>();
		data.put("target", target);
		eventBus.post(new net.runelite.client.events.PluginMessage("shortestpath", "path", data));
	}

	/** Shortest Path is installed and on. */
	private boolean shortestPathOn()
	{
		for (Plugin other : pluginManager.getPlugins())
		{
			if ("Shortest Path".equals(other.getName()) && pluginManager.isPluginEnabled(other))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Lines for the panel: "Patch|state|detail;detail" per stop. The current
	 * stop's details are the teleport (on the way) or each plot's steps (there).
	 */
	private String[] runRows()
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
				if (!atStop)
				{
					if (walk.size() > 1)
					{
						detail.append("Follow the path");
					}
					else if (st.getTeleport() == null)
					{
						detail.append("Walk " + direction(st.getPoint()));
					}
					else
					{
						String how = Farm.how(st.getTeleport(), st.getPatch());
						detail.append(st.getTeleport().getName()).append(how.isEmpty() ? "" : ": " + how);
					}
				}
				else
				{
					for (HerbRun.Plot p : st.getPlots())
					{
						detail.append(detail.length() > 0 ? ";" : "").append(p.getType().getLabel()).append(": ")
							.append(HerbRun.status(p, run.isCompost()));
					}
				}
			}
			rows[i] = st.getName() + "|" + state + "|" + detail;
		}
		// Last row: a note for the panel footer.
		rows[stops.size()] = run.finished() ? "Run done in " + FarmPlan.minutes((System.currentTimeMillis() - run.getStartedAt()) / 1000)
			: shortestPathOn() ? "" : "Install the Shortest Path plugin for a route to each patch";
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
			rows = farmPlan.getLayoutRows();
			updateWithdrawOverlay();
			for (int[] row : rows)
			{
				Arrays.stream(row).forEach(trackedIds::add);
			}
			int[] saved = savedGear(true);
			gearRows = saved != null ? Gear.fromSlots(saved) : new ArrayList<>();
			trackGear();
		}
		else
		{
			withdrawOverlay.set(false, Collections.emptyMap(), Collections.emptyMap());
			trackedIds.addAll(PotionPlan.layoutItems(selected));
			layoutHasPie = pieNeeded();
			rows = new ArrayList<>(PotionPlan.layoutRows(selected));
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
				shown.add(collapse(row));
			}
			rows = shown;
			int[] saved = savedGear(false);
			gearRows = saved != null ? Gear.fromSlots(saved) : gearRows();
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
		if (config.spiritHosidius())
		{
			spirits.add("HOSIDIUS");
		}
		return AccountReader.read(client, quests, fairyRings, portals(), wornIds, pouch).toBuilder()
			.spiritTrees(spirits)
			.spiritHouse(config.spiritHouse())
			.seedVault(seedVault)
			.whistleCharges(savedCharges("whistleCharges"))
			.xericCharges(savedCharges("xericCharges"))
			.patchStatus(PatchStatus.read(configManager, java.time.Instant.now().getEpochSecond()))
			.skipGrowing(config.skipGrowing())
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
	private final Set<String> housePortals = new HashSet<>();

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		int id = event.getGameObject().getId();
		if (id == ObjectID.POH_EXIT_PORTAL)
		{
			inHouse = true;
		}
		if (HERB_PATCH_OBJECTS.contains(id))
		{
			herbPatches.add(event.getGameObject());
		}
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
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
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
		if (run != null && atStop && run.onMessage(msg))
		{
			updateRunGuide();
			pushToPanel(null);
		}
		java.util.regex.Matcher m = WHISTLE.matcher(msg);
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
			.patches(patches).overrides(overrides).build();
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
				break;
			}
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
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
		else if (event.getContainerId() == InventoryID.SEED_VAULT)
		{
			seedVault = count(event.getItemContainer());
			farmDirty = true;
		}
		else if (event.getContainerId() == InventoryID.INV)
		{
			readInventory(event.getItemContainer());
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
			housePortals.clear();
			herbPatches.clear();
		}
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
				farmingLevel = 0;
				quests = null;
				fairyRings = null;
				seedVault = new HashMap<>();
				ownHouseEntry = null;
				endRun();
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
			|| "scanHouse".equals(event.getKey()) || "skipGrowing".equals(event.getKey())))
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
		boolean farm = farmMode();
		FarmPlan.Settings fs = farmSettings();
		FarmPlan fp = FarmPlan.compute(fs, counts, wornIds, account());
		String[] harvest = harvestNote(fp, counts, boosted);
		String[] rows = runRows();
		boolean potionsGear = savedGear(false) != null;
		boolean farmGear = savedGear(true) != null;
		Map<Integer, Integer> seeds = new HashMap<>(counts);
		seedVault.forEach((id, n) -> seeds.merge(id, n, Integer::sum));
		int farming = farmingLevel;
		SwingUtilities.invokeLater(() ->
		{
			p.update(plan, status, makeable, xp, lvl, pie, showing);
			p.updateFarm(fp, fs, status, showing, farm, harvest, seeds, farming);
			p.setGearSaved(potionsGear, farmGear);
			p.setRun(rows);
		});
	}

	/**
	 * {headline, detail} for the herb run's expected harvest, or null. The
	 * detail is what it adds to potions: the selected potion if it uses this
	 * herb, else the best potion you can make with it.
	 */
	private String[] harvestNote(FarmPlan fp, Map<Integer, Integer> counts, int herblore)
	{
		int herbs = (int) Math.round(fp.getExpectedHerbs());
		int snape = (int) Math.round(fp.getExpectedSnape());
		int roots = (int) Math.round(fp.getExpectedLimpwurt());
		if (herbs <= 0 && snape <= 0 && roots <= 0)
		{
			return null;
		}
		int grimy = fp.getHerb().getGrimyId();
		List<String> parts = new ArrayList<>();
		if (herbs > 0)
		{
			parts.add(herbs + " grimy " + Catalog.lower(fp.getHerb().getName()));
		}
		if (snape > 0)
		{
			parts.add(snape + " snape grass");
		}
		if (roots > 0)
		{
			parts.add(roots + " limpwurt roots");
		}
		String head = "About " + String.join(", ", parts);

		Map<Integer, Integer> after = new HashMap<>(counts);
		after.merge(grimy, herbs, Integer::sum);
		after.merge(ItemID.SNAPE_GRASS, snape, Integer::sum);
		after.merge(ItemID.LIMPWURT_ROOT, roots, Integer::sum);
		Set<Integer> grown = new HashSet<>(Arrays.asList(grimy, ItemID.SNAPE_GRASS, ItemID.LIMPWURT_ROOT));
		int lvl = herblore > 0 ? herblore : 0;

		// The selected potion if it uses any of it, else the one this run adds the most to.
		Potion target = null;
		if (selected != null && !Collections.disjoint(PotionPlan.layoutItems(selected), grown))
		{
			target = selected;
		}
		else
		{
			int bestGain = 0;
			for (Potion p : Potion.values())
			{
				if (Collections.disjoint(PotionPlan.layoutItems(p), grown) || herblore > 0 && p.getLevel() > herblore)
				{
					continue;
				}
				int gain = p.toPotions(PotionPlan.makeable(p, after, lvl)) - p.toPotions(PotionPlan.makeable(p, counts, lvl));
				if (gain > bestGain || gain == bestGain && gain > 0 && p.getLevel() > target.getLevel())
				{
					bestGain = gain;
					target = p;
				}
			}
		}
		if (target == null)
		{
			return new String[]{head, null};
		}
		int more = target.toPotions(PotionPlan.makeable(target, after, lvl)) - target.toPotions(PotionPlan.makeable(target, counts, lvl));
		return new String[]{head, more > 0 ? "+" + more + " " + target.potionName() + " with what you have" : null};
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
