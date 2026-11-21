package com.potionprep;

import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.gameval.ItemID;

/** Static herb-run data: seeds, patches and the teleports to reach them. */
final class Farm
{
	private Farm()
	{
	}

	static final int SEEDS_PER_ALLOTMENT = 3;

	interface Seed
	{
		String getName();

		int getSeedId();

		int getLevel();
	}

	@Getter
	enum HerbSeed implements Seed
	{
		GUAM("Guam", ItemID.GUAM_SEED, 9, 25, ItemID.UNIDENTIFIED_GUAM),
		MARRENTILL("Marrentill", ItemID.MARRENTILL_SEED, 14, 28, ItemID.UNIDENTIFIED_MARENTILL),
		TARROMIN("Tarromin", ItemID.TARROMIN_SEED, 19, 31, ItemID.UNIDENTIFIED_TARROMIN),
		HARRALANDER("Harralander", ItemID.HARRALANDER_SEED, 26, 36, ItemID.UNIDENTIFIED_HARRALANDER),
		RANARR("Ranarr", ItemID.RANARR_SEED, 32, 39, ItemID.UNIDENTIFIED_RANARR),
		TOADFLAX("Toadflax", ItemID.TOADFLAX_SEED, 38, 43, ItemID.UNIDENTIFIED_TOADFLAX),
		IRIT("Irit", ItemID.IRIT_SEED, 44, 46, ItemID.UNIDENTIFIED_IRIT),
		AVANTOE("Avantoe", ItemID.AVANTOE_SEED, 50, 50, ItemID.UNIDENTIFIED_AVANTOE),
		KWUARM("Kwuarm", ItemID.KWUARM_SEED, 56, 54, ItemID.UNIDENTIFIED_KWUARM),
		SNAPDRAGON("Snapdragon", ItemID.SNAPDRAGON_SEED, 62, 57, ItemID.UNIDENTIFIED_SNAPDRAGON),
		CADANTINE("Cadantine", ItemID.CADANTINE_SEED, 67, 60, ItemID.UNIDENTIFIED_CADANTINE),
		LANTADYME("Lantadyme", ItemID.LANTADYME_SEED, 73, 64, ItemID.UNIDENTIFIED_LANTADYME),
		DWARF_WEED("Dwarf weed", ItemID.DWARF_WEED_SEED, 79, 67, ItemID.UNIDENTIFIED_DWARF_WEED),
		TORSTOL("Torstol", ItemID.TORSTOL_SEED, 85, 71, ItemID.UNIDENTIFIED_TORSTOL);

		private final String name;
		private final int seedId;
		private final int level;
		/** Chance-to-save at level 1, out of 256 (80 for every herb at 99). */
		private final int lowSave;
		private final int grimyId;

		HerbSeed(String name, int seedId, int level, int lowSave, int grimyId)
		{
			this.name = name;
			this.seedId = seedId;
			this.level = level;
			this.lowSave = lowSave;
			this.grimyId = grimyId;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Getter
	enum FlowerSeed implements Seed
	{
		NONE("No flowers", -1, 0),
		// Limpwurt root is the only Herblore secondary from a flower patch (strength potions).
		LIMPWURT("Limpwurt", ItemID.LIMPWURT_SEED, 26);

		private final String name;
		private final int seedId;
		private final int level;

		FlowerSeed(String name, int seedId, int level)
		{
			this.name = name;
			this.seedId = seedId;
			this.level = level;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Getter
	enum AllotmentSeed implements Seed
	{
		NONE("No allotments", -1, 0),
		// Snape grass is the only Herblore secondary from an allotment (prayer and fishing potions).
		SNAPE_GRASS("Snape grass", ItemID.SNAPE_GRASS_SEED, 61);

		private final String name;
		private final int seedId;
		private final int level;

		AllotmentSeed(String name, int seedId, int level)
		{
			this.name = name;
			this.seedId = seedId;
			this.level = level;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	/** Runes used by teleport spells. Elemental runes have a bit for staff and combination-rune cover. */
	@Getter
	enum Rune
	{
		LAW("Law rune", ItemID.LAWRUNE, 0),
		AIR("Air rune", ItemID.AIRRUNE, 1),
		WATER("Water rune", ItemID.WATERRUNE, 2),
		EARTH("Earth rune", ItemID.EARTHRUNE, 4),
		FIRE("Fire rune", ItemID.FIRERUNE, 8),
		ASTRAL("Astral rune", ItemID.ASTRALRUNE, 0),
		SOUL("Soul rune", ItemID.SOULRUNE, 0),
		NATURE("Nature rune", ItemID.NATURERUNE, 0);

		private final String name;
		private final int itemId;
		/** Elemental bit, 0 for non-elemental runes. */
		private final int element;

		Rune(String name, int itemId, int element)
		{
			this.name = name;
			this.itemId = itemId;
			this.element = element;
		}
	}

	/** Combination runes: each counts as one of both its elements. */
	static final int[][] COMBO_RUNES = {
		{ItemID.MISTRUNE, 1 | 2}, {ItemID.DUSTRUNE, 1 | 4}, {ItemID.MUDRUNE, 2 | 4},
		{ItemID.SMOKERUNE, 1 | 8}, {ItemID.STEAMRUNE, 2 | 8}, {ItemID.LAVARUNE, 4 | 8},
	};

	static final int[] RUNE_POUCHES = {ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
		ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER};

	/** Spellbook varbit values. */
	static final int STANDARD = 0;
	static final int LUNAR = 2;
	static final int ARCEUUS = 3;

	/** A teleport spell: spellbook, Magic level, quest, and runes per cast. */
	@Getter
	static final class Spell
	{
		private final int book;
		private final int level;
		private final Quest quest;
		private final java.util.Map<Rune, Integer> cost = new java.util.EnumMap<>(Rune.class);

		Spell(int book, int level, Quest quest, Object... cost)
		{
			this.book = book;
			this.level = level;
			this.quest = quest;
			for (int i = 0; i < cost.length; i += 2)
			{
				this.cost.put((Rune) cost[i], (Integer) cost[i + 1]);
			}
		}
	}

	/** A way to reach a patch. Any of {@link #ids} works; earlier ids are preferred. */
	@Getter
	enum Teleport
	{
		FARMING_CAPE("Farming cape", ItemID.SKILLCAPE_FARMING_TRIMMED, ItemID.SKILLCAPE_FARMING, ItemID.SKILLCAPE_MAX),
		SKILLS_NECKLACE("Skills necklace", ItemID.JEWL_NECKLACE_OF_SKILLS_1, ItemID.JEWL_NECKLACE_OF_SKILLS_2,
			ItemID.JEWL_NECKLACE_OF_SKILLS_3, ItemID.JEWL_NECKLACE_OF_SKILLS_4, ItemID.JEWL_NECKLACE_OF_SKILLS_5,
			ItemID.JEWL_NECKLACE_OF_SKILLS_6),
		XERICS_TALISMAN("Xeric's talisman", ItemID.XERIC_TALISMAN),
		HOSIDIUS_TAB("Hosidius teleport", ItemID.NZONE_TELETAB_KOUREND),
		MEMOIRS("Kharedst's memoirs", ItemID.BOOK_OF_THE_DEAD, ItemID.VEOS_KHAREDSTS_MEMOIRS),
		QUETZAL_WHISTLE("Quetzal whistle", ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE, ItemID.HG_QUETZALWHISTLE_PERFECTED,
			ItemID.HG_QUETZALWHISTLE_ENHANCED, ItemID.HG_QUETZALWHISTLE_BASIC),
		FORTIS_TAB("Civitas illa Fortis teleport", ItemID.POH_TABLET_FORTISTELEPORT),
		ARDOUGNE_CLOAK("Ardougne cloak", ItemID.ARDY_CAPE_ELITE, ItemID.ARDY_CAPE_HARD, ItemID.ARDY_CAPE_MEDIUM),
		FISHING_GUILD_TAB("Fishing Guild teleport", ItemID.LUNAR_TABLET_FISHING_GUILD_TELEPORT),
		ARDOUGNE_TAB("Ardougne teleport", ItemID.POH_TABLET_ARDOUGNETELEPORT),
		CATHERBY_TAB("Catherby teleport", ItemID.LUNAR_TABLET_CATHERBY_TELEPORT),
		CAMELOT_TAB("Camelot teleport", ItemID.POH_TABLET_CAMELOTTELEPORT),
		EXPLORERS_RING("Explorer's ring", ItemID.LUMBRIDGE_RING_ELITE, ItemID.LUMBRIDGE_RING_HARD, ItemID.LUMBRIDGE_RING_MEDIUM),
		FALADOR_TAB("Falador teleport", ItemID.POH_TABLET_FALADORTELEPORT),
		GLORY("Amulet of glory", ItemID.AMULET_OF_GLORY_INF, ItemID.AMULET_OF_GLORY_1, ItemID.AMULET_OF_GLORY_2,
			ItemID.AMULET_OF_GLORY_3, ItemID.AMULET_OF_GLORY_4, ItemID.AMULET_OF_GLORY_5, ItemID.AMULET_OF_GLORY_6,
			ItemID.TRAIL_AMULET_OF_GLORY_1, ItemID.TRAIL_AMULET_OF_GLORY_2, ItemID.TRAIL_AMULET_OF_GLORY_3,
			ItemID.TRAIL_AMULET_OF_GLORY_4, ItemID.TRAIL_AMULET_OF_GLORY_5, ItemID.TRAIL_AMULET_OF_GLORY_6),
		ECTOPHIAL("Ectophial", ItemID.ECTOPHIAL),
		FENKENSTRAIN_TAB("Fenkenstrain's Castle teleport", ItemID.TELETAB_FENK),
		STONY_BASALT("Stony basalt", ItemID.STRONGHOLD_TELEPORT_BASALT),
		TROLLHEIM_TAB("Trollheim teleport", ItemID.NZONE_TELETAB_TROLLHEIM),
		ICY_BASALT("Icy basalt", ItemID.WEISS_TELEPORT_BASALT),
		HARMONY_TAB("Harmony Island teleport", ItemID.TELETAB_HARMONY),

		FALADOR_SPELL("Falador Teleport spell", new Spell(STANDARD, 37, null, Rune.LAW, 1, Rune.AIR, 3, Rune.WATER, 1)),
		CAMELOT_SPELL("Camelot Teleport spell", new Spell(STANDARD, 45, null, Rune.LAW, 1, Rune.AIR, 5)),
		ARDOUGNE_SPELL("Ardougne Teleport spell", new Spell(STANDARD, 51, Quest.PLAGUE_CITY, Rune.LAW, 2, Rune.WATER, 2)),
		FORTIS_SPELL("Civitas illa Fortis spell", new Spell(STANDARD, 54, Quest.TWILIGHTS_PROMISE, Rune.LAW, 2, Rune.EARTH, 1, Rune.FIRE, 1)),
		TROLLHEIM_SPELL("Trollheim Teleport spell", new Spell(STANDARD, 61, Quest.EADGARS_RUSE, Rune.LAW, 2, Rune.FIRE, 2)),
		CATHERBY_SPELL("Catherby Teleport spell", new Spell(LUNAR, 87, Quest.LUNAR_DIPLOMACY, Rune.LAW, 3, Rune.ASTRAL, 3, Rune.WATER, 10)),
		FISHING_GUILD_SPELL("Fishing Guild Teleport spell", new Spell(LUNAR, 85, Quest.LUNAR_DIPLOMACY, Rune.LAW, 3, Rune.ASTRAL, 3, Rune.WATER, 10)),
		FENKENSTRAIN_SPELL("Fenkenstrain's Castle spell", new Spell(ARCEUUS, 48, Quest.PRIEST_IN_PERIL, Rune.LAW, 1, Rune.EARTH, 1, Rune.SOUL, 1)),
		HARMONY_SPELL("Harmony Island spell", new Spell(ARCEUUS, 65, Quest.THE_GREAT_BRAIN_ROBBERY, Rune.LAW, 1, Rune.SOUL, 1, Rune.NATURE, 1)),

		// Fairy rings: need Fairytale II started, and a dramen or lunar staff unless Lumbridge elite is done.
		FAIRY_CIR("Fairy ring CIR", Kind.FAIRY, null),
		FAIRY_AKR("Fairy ring AKR", Kind.FAIRY, null),
		FAIRY_AJP("Fairy ring AJP", Kind.FAIRY, null),
		FAIRY_BLR("Fairy ring BLR", Kind.FAIRY, null),
		FAIRY_ALQ("Fairy ring ALQ", Kind.FAIRY, null),

		// Through your house: the item is how you get there.
		HOUSE_HOSIDIUS("House teleport (Hosidius house)", Kind.HOUSE, null),
		PORTAL_FALADOR("House portal: Falador", Kind.PORTAL, "FALADOR"),
		PORTAL_CAMELOT("House portal: Camelot", Kind.PORTAL, "CAMELOT"),
		PORTAL_ARDOUGNE("House portal: Ardougne", Kind.PORTAL, "ARDOUGNE"),
		PORTAL_FISHING_GUILD("House portal: Fishing Guild", Kind.PORTAL, "FISHING_GUILD"),
		PORTAL_CATHERBY("House portal: Catherby", Kind.PORTAL, "CATHERBY"),
		PORTAL_FENKENSTRAIN("House portal: Fenkenstrain's Castle", Kind.PORTAL, "FENKENSTRAIN"),
		PORTAL_TROLLHEIM("House portal: Trollheim", Kind.PORTAL, "TROLLHEIM"),
		PORTAL_TROLL_STRONGHOLD("House portal: Troll Stronghold", Kind.PORTAL, "TROLL_STRONGHOLD"),
		PORTAL_WEISS("House portal: Weiss", Kind.PORTAL, "WEISS"),
		PORTAL_HARMONY("House portal: Harmony Island", Kind.PORTAL, "HARMONY"),
		PORTAL_CIVITAS("House portal: Civitas illa Fortis", Kind.PORTAL, "CIVITAS"),
		PORTAL_XERIC("House: mounted Xeric's talisman", Kind.PORTAL, "XERIC"),

		// Spirit trees you've grown; reached from any spirit tree (or the one in your house).
		SPIRIT_GUILD("Spirit tree (Farming Guild)", Kind.SPIRIT, "GUILD"),
		SPIRIT_HOSIDIUS("Spirit tree (Hosidius)", Kind.SPIRIT, "HOSIDIUS");

		enum Kind
		{
			ITEM, SPELL, FAIRY, HOUSE, PORTAL, SPIRIT
		}

		private final String name;
		private final int[] ids;
		/** Non-null for spells, which use runes instead of an item. */
		private final Spell spell;
		private final Kind kind;
		/** House portal destination, or spirit tree, key. */
		private final String portal;

		Teleport(String name, int... ids)
		{
			this.name = name;
			this.ids = ids;
			this.spell = null;
			this.kind = Kind.ITEM;
			this.portal = null;
		}

		Teleport(String name, Spell spell)
		{
			this.name = name;
			this.ids = new int[0];
			this.spell = spell;
			this.kind = Kind.SPELL;
			this.portal = null;
		}

		Teleport(String name, Kind kind, String portal)
		{
			this.name = name;
			this.ids = kind == Kind.FAIRY ? FAIRY_STAFFS : HOUSE_ENTRY;
			this.spell = null;
			this.kind = kind;
			this.portal = portal;
		}
	}

	/** How to use a teleport for a patch: the option to click, then where to pick. */
	static String how(Teleport t, Patch p)
	{
		switch (t)
		{
			case XERICS_TALISMAN:
				return "Rub > Xeric's Glade";
			case SKILLS_NECKLACE:
				return "Rub > " + (p == Patch.FARMING_GUILD ? "Farming Guild" : p == Patch.ARDOUGNE ? "Fishing Guild" : "Woodcutting Guild");
			case FARMING_CAPE:
				return "Teleport (Farming Guild)";
			case ARDOUGNE_CLOAK:
				return "Farm Teleport";
			case EXPLORERS_RING:
				return "Cabbage-port";
			case GLORY:
				return "Rub > Draynor Village, walk north-west";
			case ECTOPHIAL:
				return "Empty, walk west";
			case MEMOIRS:
				return "Reminisce > Lunch by the Lancalliums";
			case QUETZAL_WHISTLE:
				return "Signal > Hunter Guild, walk north";
			case STONY_BASALT:
			case ICY_BASALT:
				return "Teleport";
			case HOUSE_HOSIDIUS:
				return "Teleport to your house, leave by the portal";
			default:
				break;
		}
		switch (t.getKind())
		{
			case SPELL:
				return "Cast";
			case FAIRY:
				return "Use a fairy ring: " + t.getName().replace("Fairy ring ", "");
			case PORTAL:
				return "Go to your house, use the " + t.getName().replace("House portal: ", "").replace("House: ", "");
			case SPIRIT:
				return "Use a spirit tree";
			default:
				return t.getName().endsWith("teleport") ? "Break" : "";
		}
	}

	/** Dramen or lunar staff, needed for fairy rings without the Lumbridge elite diary. */
	static final int[] FAIRY_STAFFS = {ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF};
	/** Ways into your house, best first. */
	static final int[] HOUSE_ENTRY = {ItemID.SKILLCAPE_MAX, ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED,
		ItemID.SKILLCAPE_CONSTRUCTION, ItemID.POH_TABLET_TELEPORTTOHOUSE};
	/** POH_HOUSE_LOCATION value for Hosidius. */
	static final int HOUSE_IN_HOSIDIUS = 8;

	/** Single-use teleport items; one is needed per trip. */
	static boolean consumable(int itemId)
	{
		switch (itemId)
		{
			case ItemID.NZONE_TELETAB_KOUREND:
			case ItemID.POH_TABLET_FORTISTELEPORT:
			case ItemID.LUNAR_TABLET_FISHING_GUILD_TELEPORT:
			case ItemID.POH_TABLET_ARDOUGNETELEPORT:
			case ItemID.LUNAR_TABLET_CATHERBY_TELEPORT:
			case ItemID.POH_TABLET_CAMELOTTELEPORT:
			case ItemID.POH_TABLET_FALADORTELEPORT:
			case ItemID.TELETAB_FENK:
			case ItemID.NZONE_TELETAB_TROLLHEIM:
			case ItemID.TELETAB_HARMONY:
			case ItemID.POH_TABLET_TELEPORTTOHOUSE:
			case ItemID.WEISS_TELEPORT_BASALT:
			case ItemID.STRONGHOLD_TELEPORT_BASALT:
				return true;
			default:
				return false;
		}
	}

	/** Herb patch locations, in a sensible run order. Teleports are best first. */
	@Getter
	enum Patch
	{
		FARMING_GUILD("Farming Guild", 2, true, 65, null, false,
			Teleport.FARMING_CAPE, Teleport.SKILLS_NECKLACE, Teleport.FAIRY_CIR, Teleport.SPIRIT_GUILD),
		HOSIDIUS("Hosidius", 2, true, 0, null, false,
			Teleport.XERICS_TALISMAN, Teleport.PORTAL_XERIC, Teleport.MEMOIRS, Teleport.HOUSE_HOSIDIUS, Teleport.FAIRY_AKR, Teleport.SPIRIT_HOSIDIUS,
			Teleport.HOSIDIUS_TAB,
			Teleport.SKILLS_NECKLACE),
		CIVITAS("Civitas illa Fortis", 2, true, 0, Quest.CHILDREN_OF_THE_SUN, false,
			Teleport.QUETZAL_WHISTLE, Teleport.FAIRY_AJP, Teleport.PORTAL_CIVITAS, Teleport.FORTIS_SPELL, Teleport.FORTIS_TAB),
		ARDOUGNE("Ardougne", 2, true, 0, null, false,
			Teleport.ARDOUGNE_CLOAK, Teleport.SKILLS_NECKLACE, Teleport.PORTAL_ARDOUGNE, Teleport.PORTAL_FISHING_GUILD,
			Teleport.FISHING_GUILD_SPELL, Teleport.FISHING_GUILD_TAB, Teleport.ARDOUGNE_SPELL, Teleport.ARDOUGNE_TAB,
			Teleport.FAIRY_BLR),
		CATHERBY("Catherby", 2, true, 0, null, false,
			Teleport.PORTAL_CATHERBY, Teleport.CATHERBY_SPELL, Teleport.CATHERBY_TAB, Teleport.PORTAL_CAMELOT,
			Teleport.CAMELOT_SPELL, Teleport.CAMELOT_TAB),
		FALADOR("Falador", 2, true, 0, null, false,
			Teleport.EXPLORERS_RING, Teleport.PORTAL_FALADOR, Teleport.FALADOR_SPELL, Teleport.FALADOR_TAB, Teleport.GLORY),
		MORYTANIA("Port Phasmatys", 2, true, 0, Quest.PRIEST_IN_PERIL, false,
			Teleport.ECTOPHIAL, Teleport.FAIRY_ALQ, Teleport.PORTAL_FENKENSTRAIN, Teleport.FENKENSTRAIN_SPELL,
			Teleport.FENKENSTRAIN_TAB),
		TROLL_STRONGHOLD("Troll Stronghold", 0, false, 0, Quest.MY_ARMS_BIG_ADVENTURE, false,
			Teleport.STONY_BASALT, Teleport.PORTAL_TROLL_STRONGHOLD, Teleport.PORTAL_TROLLHEIM, Teleport.TROLLHEIM_SPELL,
			Teleport.TROLLHEIM_TAB),
		WEISS("Weiss", 0, false, 0, Quest.MAKING_FRIENDS_WITH_MY_ARM, false,
			Teleport.ICY_BASALT, Teleport.PORTAL_WEISS),
		HARMONY("Harmony Island", 1, false, 0, null, true,
			Teleport.PORTAL_HARMONY, Teleport.HARMONY_SPELL, Teleport.HARMONY_TAB);

		private final String name;
		private final int allotments;
		private final boolean flower;
		/** Farming level needed to use the patch, 0 if none. */
		private final int level;
		/** Quest needed to reach the patch, or null. */
		private final Quest quest;
		/** Needs the Morytania elite diary. */
		private final boolean moryElite;
		private final Teleport[] teleports;

		Patch(String name, int allotments, boolean flower, int level, Quest quest, boolean moryElite, Teleport... teleports)
		{
			this.name = name;
			this.allotments = allotments;
			this.flower = flower;
			this.level = level;
			this.quest = quest;
			this.moryElite = moryElite;
			this.teleports = teleports;
		}
	}

	static final int[] TOOLS = {ItemID.RAKE, ItemID.DIBBER, ItemID.SPADE};
	static final int MAGIC_SECATEURS = ItemID.FAIRY_ENCHANTED_SECATEURS;
	static final int BOTTOMLESS_BUCKET = ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
	/** Best first. */
	static final int[] COMPOST = {ItemID.BUCKET_ULTRACOMPOST, ItemID.BUCKET_SUPERCOMPOST, ItemID.BUCKET_COMPOST};
}
