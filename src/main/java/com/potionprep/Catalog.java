package com.potionprep;

import static com.potionprep.Recipe.in;
import net.runelite.api.gameval.ItemID;

/** The recipe trees. Nodes are shared, so one super energy node feeds both Super energy and Stamina. */
final class Catalog
{
	private Catalog()
	{
	}

	static String lower(String s)
	{
		return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

	static String plural(String s)
	{
		return s.endsWith("y") ? s.substring(0, s.length() - 1) + "ies" : s + "s";
	}

	// --- Tools and bases ---

	static final Node PESTLE = Node.item("Pestle and mortar", ItemID.PESTLE_AND_MORTAR, "General stores");
	static final Node VIAL = Node.item("Vial of water", ItemID.VIAL_WATER, "Fill vials at a sink or fountain, or the GE");
	static final Node COCONUT_MILK = Node.item("Coconut milk", ItemID.VIAL_COCONUT_MILK, "Smash a coconut and fill a vial, or the GE");

	// --- Herbs: grimy -> clean -> unf ---

	static final class Herb
	{
		final Node grimy;
		final Node clean;
		final Node unf;

		Herb(String name, int grimyId, int cleanId, int unfId, int cleanLevel, int unfLevel, double cleanXp)
		{
			grimy = Node.item("Grimy " + lower(name), grimyId, "Farming, drops, or the GE");
			clean = Node.item(name, cleanId, "Farming, drops, or the GE").madeBy(Recipe.clean(grimy, cleanLevel).xp(cleanXp));
			// In game the unf drops "leaf"/"weed": Guam potion (unf), Ranarr potion (unf).
			String shortName = name.replace(" leaf", "").replace("Ranarr weed", "Ranarr");
			unf = Node.item(shortName + " potion (unf)", unfId, null)
				.madeBy(Recipe.mix(unfLevel, 1, "Make %d " + lower(shortName) + " (unf)", in(clean, 1), in(VIAL, 1)));
		}
	}

	static final Herb GUAM = new Herb("Guam leaf", ItemID.UNIDENTIFIED_GUAM, ItemID.GUAM_LEAF, ItemID.GUAMVIAL, 3, 3, 2.5);
	static final Herb MARRENTILL = new Herb("Marrentill", ItemID.UNIDENTIFIED_MARENTILL, ItemID.MARENTILL, ItemID.MARRENTILLVIAL, 5, 5, 3.8);
	static final Herb TARROMIN = new Herb("Tarromin", ItemID.UNIDENTIFIED_TARROMIN, ItemID.TARROMIN, ItemID.TARROMINVIAL, 11, 12, 5);
	static final Herb HARRALANDER = new Herb("Harralander", ItemID.UNIDENTIFIED_HARRALANDER, ItemID.HARRALANDER, ItemID.HARRALANDERVIAL, 20, 22, 6.3);
	static final Herb RANARR = new Herb("Ranarr weed", ItemID.UNIDENTIFIED_RANARR, ItemID.RANARR_WEED, ItemID.RANARRVIAL, 25, 30, 7.5);
	static final Herb TOADFLAX = new Herb("Toadflax", ItemID.UNIDENTIFIED_TOADFLAX, ItemID.TOADFLAX, ItemID.TOADFLAXVIAL, 30, 34, 8);
	static final Herb IRIT = new Herb("Irit leaf", ItemID.UNIDENTIFIED_IRIT, ItemID.IRIT_LEAF, ItemID.IRITVIAL, 40, 45, 8.8);
	static final Herb AVANTOE = new Herb("Avantoe", ItemID.UNIDENTIFIED_AVANTOE, ItemID.AVANTOE, ItemID.AVANTOEVIAL, 48, 50, 10);
	static final Herb KWUARM = new Herb("Kwuarm", ItemID.UNIDENTIFIED_KWUARM, ItemID.KWUARM, ItemID.KWUARMVIAL, 54, 55, 11.3);
	static final Herb SNAPDRAGON = new Herb("Snapdragon", ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.SNAPDRAGON, ItemID.SNAPDRAGONVIAL, 59, 63, 11.8);
	static final Herb CADANTINE = new Herb("Cadantine", ItemID.UNIDENTIFIED_CADANTINE, ItemID.CADANTINE, ItemID.CADANTINEVIAL, 65, 66, 12.5);
	static final Herb LANTADYME = new Herb("Lantadyme", ItemID.UNIDENTIFIED_LANTADYME, ItemID.LANTADYME, ItemID.LANTADYMEVIAL, 67, 69, 13.1);
	static final Herb DWARF_WEED = new Herb("Dwarf weed", ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.DWARF_WEED, ItemID.DWARFWEEDVIAL, 70, 72, 13.8);
	static final Herb TORSTOL = new Herb("Torstol", ItemID.UNIDENTIFIED_TORSTOL, ItemID.TORSTOL, ItemID.TORSTOLVIAL, 75, 78, 15);

	// --- Secondaries ---

	private static Node crushed(String name, int id, String rawName, int rawId, int yield, String source)
	{
		Node raw = Node.item(rawName, rawId, source);
		return Node.item(name, id, source).madeBy(Recipe.crush(raw, yield, PESTLE));
	}

	static final Node EYE_OF_NEWT = Node.item("Eye of newt", ItemID.EYE_OF_NEWT, "Herblore shops or the GE");
	static final Node UNICORN_HORN_DUST = crushed("Unicorn horn dust", ItemID.UNICORN_HORN_DUST, "Unicorn horn", ItemID.UNICORN_HORN, 1, "Unicorns, or the GE");
	static final Node LIMPWURT_ROOT = Node.item("Limpwurt root", ItemID.LIMPWURT_ROOT, "Hill giants, farming, or the GE");
	static final Node RED_SPIDERS_EGGS = Node.item("Red spiders' eggs", ItemID.RED_SPIDERS_EGGS, "Red spiders, spawns, or the GE");
	static final Node CHOCOLATE_DUST = crushed("Chocolate dust", ItemID.CHOCOLATE_DUST, "Chocolate bar", ItemID.CHOCOLATE_BAR, 1, "Food shops or the GE");
	static final Node WHITE_BERRIES = Node.item("White berries", ItemID.WHITE_BERRIES, "Whiteberry bush, or the GE");
	static final Node TOADS_LEGS = Node.item("Toad's legs", ItemID.TOADS_LEGS, "Gnome shops or the GE");
	static final Node GOAT_HORN_DUST = crushed("Goat horn dust", ItemID.GROUND_DESERT_GOAT_HORN, "Desert goat horn", ItemID.DESERT_GOAT_HORN, 1, "Desert goats, or the GE");
	static final Node SNAPE_GRASS = Node.item("Snape grass", ItemID.SNAPE_GRASS, "Spawns, farming, or the GE");
	static final Node MORT_MYRE_FUNGUS = Node.item("Mort myre fungus", ItemID.MORTMYREMUSHROOM, "Cast Bloom in Mort Myre, or the GE");
	static final Node KEBBIT_TEETH_DUST = crushed("Kebbit teeth dust", ItemID.HUNTINGBEAST_SABRETEETH_DUST, "Kebbit teeth", ItemID.HUNTINGBEAST_SABRETEETH, 1, "Sabre-toothed kebbits, or the GE");
	static final Node DRAGON_SCALE_DUST = crushed("Dragon scale dust", ItemID.DRAGON_SCALE_DUST, "Blue dragon scale", ItemID.BLUE_DRAGON_SCALE, 1, "Blue dragons, or the GE");
	static final Node WINE_OF_ZAMORAK = Node.item("Wine of Zamorak", ItemID.WINE_OF_ZAMORAK, "Telegrab at the Chaos Temple, or the GE");
	static final Node POTATO_CACTUS = Node.item("Potato cactus", ItemID.CACTUS_POTATO, "Kalphite Lair spawns, or the GE");
	static final Node JANGERBERRIES = Node.item("Jangerberries", ItemID.JANGERBERRIES, "Jangerberry bush, or the GE");
	static final Node CRUSHED_NEST = crushed("Crushed nest", ItemID.CRUSHED_BIRD_NEST, "Bird nest", ItemID.BIRD_NEST_EMPTY, 1, "Woodcutting, birdhouses, or the GE");
	static final Node AMYLASE = Node.item("Amylase crystal", ItemID.AMYLASE, "Grace's shop (marks of grace), or the GE");
	// Crushing a lava scale gives 3-6 shards; plan with the minimum.
	static final Node LAVA_SHARD = crushed("Lava scale shard", ItemID.LAVA_SHARD, "Lava scale", ItemID.LAVA_SCALE, 3, "Lava dragons, or the GE");
	static final Node CRUSHED_SUPERIOR_BONES = crushed("Crushed superior dragon bones", ItemID.CRUSHED_DRAGON_BONES, "Superior dragon bones", ItemID.DRAGON_BONES_SUPERIOR, 1, "Wyrms, drakes, hydras, or the GE");
	static final Node MAGIC_ROOTS = Node.item("Magic roots", ItemID.MAGIC_ROOTS, "Magic tree farming, or the GE");
	static final Node ZULRAH_SCALES = Node.item("Zulrah's scales", ItemID.SNAKEBOSS_SCALE, "Zulrah, or the GE");

	// --- Potions ---

	/** unf + secondary = one 3-dose potion. */
	private static Node standard(String name, int level, double xp, Herb herb, Node secondary, int d1, int d2, int d3, int d4)
	{
		return Node.dosed(name, d1, d2, d3, d4)
			.madeBy(Recipe.mix(level, 3, "Mix %d " + plural(lower(name)), in(herb.unf, 1), in(secondary, 1)).xp(xp));
	}

	static final Node ATTACK = standard("Attack potion", 3, 25, GUAM, EYE_OF_NEWT, ItemID._1DOSE1ATTACK, ItemID._2DOSE1ATTACK, ItemID._3DOSE1ATTACK, ItemID._4DOSE1ATTACK);
	static final Node ANTIPOISON = standard("Antipoison", 5, 37.5, MARRENTILL, UNICORN_HORN_DUST, ItemID._1DOSEANTIPOISON, ItemID._2DOSEANTIPOISON, ItemID._3DOSEANTIPOISON, ItemID._4DOSEANTIPOISON);
	static final Node STRENGTH = standard("Strength potion", 12, 50, TARROMIN, LIMPWURT_ROOT, ItemID._1DOSE1STRENGTH, ItemID._2DOSE1STRENGTH, ItemID._3DOSE1STRENGTH, ItemID.STRENGTH4);
	static final Node RESTORE = standard("Restore potion", 22, 62.5, HARRALANDER, RED_SPIDERS_EGGS, ItemID._1DOSESTATRESTORE, ItemID._2DOSESTATRESTORE, ItemID._3DOSESTATRESTORE, ItemID._4DOSESTATRESTORE);
	static final Node ENERGY = standard("Energy potion", 26, 67.5, HARRALANDER, CHOCOLATE_DUST, ItemID._1DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._4DOSE1ENERGY);
	static final Node DEFENCE = standard("Defence potion", 30, 75, RANARR, WHITE_BERRIES, ItemID._1DOSE1DEFENSE, ItemID._2DOSE1DEFENSE, ItemID._3DOSE1DEFENSE, ItemID._4DOSE1DEFENSE);
	static final Node AGILITY = standard("Agility potion", 34, 80, TOADFLAX, TOADS_LEGS, ItemID._1DOSE1AGILITY, ItemID._2DOSE1AGILITY, ItemID._3DOSE1AGILITY, ItemID._4DOSE1AGILITY);
	static final Node COMBAT = standard("Combat potion", 36, 84, HARRALANDER, GOAT_HORN_DUST, ItemID._1DOSECOMBAT, ItemID._2DOSECOMBAT, ItemID._3DOSECOMBAT, ItemID._4DOSECOMBAT);
	static final Node PRAYER = standard("Prayer potion", 38, 87.5, RANARR, SNAPE_GRASS, ItemID._1DOSEPRAYERRESTORE, ItemID._2DOSEPRAYERRESTORE, ItemID._3DOSEPRAYERRESTORE, ItemID._4DOSEPRAYERRESTORE);
	static final Node SUPER_ATTACK = standard("Super attack", 45, 100, IRIT, EYE_OF_NEWT, ItemID._1DOSE2ATTACK, ItemID._2DOSE2ATTACK, ItemID._3DOSE2ATTACK, ItemID._4DOSE2ATTACK);
	static final Node SUPERANTIPOISON = standard("Superantipoison", 48, 106.3, IRIT, UNICORN_HORN_DUST, ItemID._1DOSE2ANTIPOISON, ItemID._2DOSE2ANTIPOISON, ItemID._3DOSE2ANTIPOISON, ItemID._4DOSE2ANTIPOISON);
	static final Node FISHING = standard("Fishing potion", 50, 112.5, AVANTOE, SNAPE_GRASS, ItemID._1DOSEFISHERSPOTION, ItemID._2DOSEFISHERSPOTION, ItemID._3DOSEFISHERSPOTION, ItemID._4DOSEFISHERSPOTION);
	static final Node SUPER_ENERGY = standard("Super energy", 52, 117.5, AVANTOE, MORT_MYRE_FUNGUS, ItemID._1DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._4DOSE2ENERGY);
	static final Node HUNTER = standard("Hunter potion", 53, 120, AVANTOE, KEBBIT_TEETH_DUST, ItemID._1DOSEHUNTING, ItemID._2DOSEHUNTING, ItemID._3DOSEHUNTING, ItemID._4DOSEHUNTING);
	static final Node SUPER_STRENGTH = standard("Super strength", 55, 125, KWUARM, LIMPWURT_ROOT, ItemID._1DOSE2STRENGTH, ItemID._2DOSE2STRENGTH, ItemID._3DOSE2STRENGTH, ItemID._4DOSE2STRENGTH);
	static final Node WEAPON_POISON = Node.item("Weapon poison", ItemID.WEAPON_POISON, null)
		.madeBy(Recipe.mix(60, 1, "Mix %d weapon poison", in(KWUARM.unf, 1), in(DRAGON_SCALE_DUST, 1)).xp(137.5));
	static final Node SUPER_RESTORE = standard("Super restore", 63, 142.5, SNAPDRAGON, RED_SPIDERS_EGGS, ItemID._1DOSE2RESTORE, ItemID._2DOSE2RESTORE, ItemID._3DOSE2RESTORE, ItemID._4DOSE2RESTORE);
	static final Node SUPER_DEFENCE = standard("Super defence", 66, 150, CADANTINE, WHITE_BERRIES, ItemID._1DOSE2DEFENSE, ItemID._2DOSE2DEFENSE, ItemID._3DOSE2DEFENSE, ItemID._4DOSE2DEFENSE);
	static final Node ANTIFIRE = standard("Antifire potion", 69, 157.5, LANTADYME, DRAGON_SCALE_DUST, ItemID._1DOSE1ANTIDRAGON, ItemID._2DOSE1ANTIDRAGON, ItemID._3DOSE1ANTIDRAGON, ItemID._4DOSE1ANTIDRAGON);
	static final Node RANGING = standard("Ranging potion", 72, 162.5, DWARF_WEED, WINE_OF_ZAMORAK, ItemID._1DOSERANGERSPOTION, ItemID._2DOSERANGERSPOTION, ItemID._3DOSERANGERSPOTION, ItemID._4DOSERANGERSPOTION);
	static final Node MAGIC = standard("Magic potion", 76, 172.5, LANTADYME, POTATO_CACTUS, ItemID._1DOSE1MAGIC, ItemID._2DOSE1MAGIC, ItemID._3DOSE1MAGIC, ItemID._4DOSE1MAGIC);
	static final Node ZAMORAK_BREW = standard("Zamorak brew", 78, 175, TORSTOL, JANGERBERRIES, ItemID._1DOSEPOTIONOFZAMORAK, ItemID._2DOSEPOTIONOFZAMORAK, ItemID._3DOSEPOTIONOFZAMORAK, ItemID._4DOSEPOTIONOFZAMORAK);
	static final Node SARADOMIN_BREW = standard("Saradomin brew", 81, 180, TOADFLAX, CRUSHED_NEST, ItemID._1DOSEPOTIONOFSARADOMIN, ItemID._2DOSEPOTIONOFSARADOMIN, ItemID._3DOSEPOTIONOFSARADOMIN, ItemID._4DOSEPOTIONOFSARADOMIN);

	// --- Multi-step potions ---

	/** One amylase crystal per dose of super energy. */
	static final Node STAMINA = Node.dosed("Stamina potion", ItemID._1DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._4DOSESTAMINA)
		.madeBy(Recipe.mix(77, 1, "Add amylase to %d doses", in(SUPER_ENERGY, 1), in(AMYLASE, 1)).xp(25.5));

	/** One lava scale shard per dose of antifire. */
	static final Node EXTENDED_ANTIFIRE = Node.dosed("Extended antifire", ItemID._1DOSE2ANTIDRAGON, ItemID._2DOSE2ANTIDRAGON, ItemID._3DOSE2ANTIDRAGON, ItemID._4DOSE2ANTIDRAGON)
		.madeBy(Recipe.mix(84, 1, "Add shards to %d doses", in(ANTIFIRE, 1), in(LAVA_SHARD, 1)).xp(27.5));

	/** Crushed superior dragon bones + antifire(4) = super antifire(4). */
	static final Node SUPER_ANTIFIRE = Node.dosed("Super antifire", ItemID._1DOSE3ANTIDRAGON, ItemID._2DOSE3ANTIDRAGON, ItemID._3DOSE3ANTIDRAGON, ItemID._4DOSE3ANTIDRAGON)
		.madeBy(Recipe.mix(92, 4, "Mix %d super antifires", in(ANTIFIRE, 4), in(CRUSHED_SUPERIOR_BONES, 1)).xp(130));

	static final Node EXTENDED_SUPER_ANTIFIRE = Node.dosed("Extended super antifire", ItemID._1DOSE4ANTIDRAGON, ItemID._2DOSE4ANTIDRAGON, ItemID._3DOSE4ANTIDRAGON, ItemID._4DOSE4ANTIDRAGON)
		.madeBy(Recipe.mix(98, 1, "Add shards to %d doses", in(SUPER_ANTIFIRE, 1), in(LAVA_SHARD, 1)).xp(40));

	/** Torstol + super attack(4) + super strength(4) + super defence(4). */
	static final Node SUPER_COMBAT = Node.dosed("Super combat", ItemID._1DOSE2COMBAT, ItemID._2DOSE2COMBAT, ItemID._3DOSE2COMBAT, ItemID._4DOSE2COMBAT)
		.madeBy(Recipe.mix(90, 4, "Mix %d super combats",
			in(SUPER_ATTACK, 4), in(SUPER_STRENGTH, 4), in(SUPER_DEFENCE, 4), in(TORSTOL.clean, 1)).xp(150));

	static final Node ANTIDOTE_PP_UNF = Node.item("Antidote++ (unf)", ItemID.UNFINISHED_ANTIDOTE__, null)
		.madeBy(Recipe.mix(79, 1, "Make %d antidote++ (unf)", in(IRIT.clean, 1), in(COCONUT_MILK, 1)));

	static final Node ANTIDOTE_PP = Node.dosed("Antidote++", ItemID.ANTIDOTE__1, ItemID.ANTIDOTE__2, ItemID.ANTIDOTE__3, ItemID.ANTIDOTE__4)
		.madeBy(Recipe.mix(79, 3, "Mix %d antidote++", in(ANTIDOTE_PP_UNF, 1), in(MAGIC_ROOTS, 1)).xp(177.5));

	/** Five Zulrah's scales per dose of antidote++. */
	static final Node ANTI_VENOM = Node.dosed("Anti-venom", ItemID.ANTIVENOM1, ItemID.ANTIVENOM2, ItemID.ANTIVENOM3, ItemID.ANTIVENOM4)
		.madeBy(Recipe.mix(87, 1, "Add scales to %d doses", in(ANTIDOTE_PP, 1), in(ZULRAH_SCALES, 5)).xp(30));

	static final Node ANTI_VENOM_PLUS = Node.dosed("Anti-venom+", ItemID.ANTIVENOM_1, ItemID.ANTIVENOM_2, ItemID.ANTIVENOM_3, ItemID.ANTIVENOM_4)
		.madeBy(Recipe.mix(94, 4, "Mix %d anti-venom+", in(ANTI_VENOM, 4), in(TORSTOL.clean, 1)).xp(125));
}
