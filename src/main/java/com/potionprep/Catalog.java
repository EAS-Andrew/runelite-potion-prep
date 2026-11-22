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
				.madeBy(Recipe.mix(unfLevel, 1, "Make %d " + lower(shortName) + " (unf)", in(clean, 1), in(VIAL, 1)).fromGrimy());
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
	static final Herb HUASCA = new Herb("Huasca", ItemID.UNIDENTIFIED_HUASCA, ItemID.HUASCA, ItemID.HUASCAVIAL, 58, 58, 11.8);

	static final Node VIAL_OF_BLOOD = Node.item("Vial of blood", ItemID.VIAL_BLOOD, "Blood sacs, or the GE");
	// The Herblore cape takes a grimy cadantine here too.
	static final Node CADANTINE_BLOOD_UNF = Node.item("Cadantine blood potion (unf)", ItemID.CADANTINE_BLOODVIAL, null)
		.madeBy(Recipe.mix(80, 1, "Make %d cadantine blood (unf)", in(CADANTINE.clean, 1), in(VIAL_OF_BLOOD, 1)).fromGrimy());

	// --- Sailing corals: added to a vial as they are, no cleaning ---

	private static Node coralUnf(String name, int unfId, String coralName, int coralId, int level)
	{
		Node coral = Node.item(coralName, coralId, "Coral nurseries (Sailing), or the GE");
		return Node.item(name + " potion (unf)", unfId, null)
			.madeBy(Recipe.mix(level, 1, "Make %d " + lower(name) + " (unf)", in(coral, 1), in(VIAL, 1)));
	}

	static final Node ELKHORN_UNF = coralUnf("Elkhorn", ItemID.ELKHORNVIAL, "Elkhorn coral", ItemID.CORAL_ELKHORN, 56);
	static final Node PILLAR_UNF = coralUnf("Pillar", ItemID.PILLARVIAL, "Pillar coral", ItemID.CORAL_PILLAR, 62);
	static final Node UMBRAL_UNF = coralUnf("Umbral", ItemID.UMBRALVIAL, "Umbral coral", ItemID.CORAL_UMBRAL, 89);

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
	static final Node ALDARIUM = Node.item("Aldarium", ItemID.ALDARIUM, "Mastering Mixology, or the GE");
	static final Node VOLCANIC_ASH = Node.item("Volcanic ash", ItemID.FOSSIL_VOLCANIC_ASH, "Fossil Island ash piles, or the GE");
	static final Node YEW_ROOTS = Node.item("Yew roots", ItemID.YEW_ROOTS, "Yew tree farming, or the GE");
	static final Node SNAKE_WEED = Node.item("Snake weed", ItemID.SNAKE_WEED, "Karamja jungle, or the GE")
		.madeBy(Recipe.clean(Node.item("Grimy snake weed", ItemID.UNIDENTIFIED_SNAKE_WEED, "Karamja jungle, or the GE"), 3).xp(2.5));
	static final Node NAIL_BEAST_NAILS = Node.item("Nail beast nails", ItemID.NAIL_BEAST_NAIL, "Nail beasts, or the GE");
	static final Node NIHIL_DUST = crushed("Nihil dust", ItemID.NIHIL_DUST, "Nihil shard", ItemID.NIHIL_SHARD, 1, "Ancient Prison (Nex), or the GE");
	static final Node ANCIENT_ESSENCE = Node.item("Ancient essence", ItemID.ANCIENT_ESSENCE, "Crush ancient icons or venator shards, or the GE");
	static final Node LILY_OF_THE_SANDS = Node.item("Lily of the Sands", ItemID.LILY_OF_THE_SANDS, "Tombs of Amascut, or the GE");
	static final Node ARAXYTE_VENOM_SAC = Node.item("Araxyte venom sac", ItemID.ARAXYTE_VENOM_SACK, "Araxxor, or the GE");
	// One shard crushes to 10 dust; divine potions take one dust per dose.
	static final Node CRYSTAL_DUST = crushed("Crystal dust", ItemID.PRIF_CRYSTAL_SHARD_CRUSHED, "Crystal shard", ItemID.PRIF_CRYSTAL_SHARD, 10, "Prifddinas");
	// Sailing secondaries. Pastes and fish parts come from several raw items, so only the result is tracked.
	static final Node SQUID_PASTE = Node.item("Squid paste", ItemID.SQUID_PASTE, "Grind raw swordtip or jumbo squid, or the GE");
	static final Node COTTON_YARN = Node.item("Cotton yarn", ItemID.COTTON_YARN, "Spin cotton bolls, or the GE");
	static final Node HADDOCK_EYE = Node.item("Haddock eye", ItemID.HADDOCK_EYE, "Cut a raw haddock with a knife, or the GE");
	static final Node CRAB_PASTE = Node.item("Crab paste", ItemID.CRAB_PASTE, "Grind a red or blue crab, or the GE");
	static final Node YELLOW_FIN = Node.item("Yellow fin", ItemID.YELLOW_FIN, "Cut raw yellowfin with a knife, or the GE");
	static final Node MARLIN_SCALES = Node.item("Marlin scales", ItemID.MARLIN_SCALES, "Cut raw marlin with a knife, or the GE");
	static final Node RAINBOW_CRAB_PASTE = Node.item("Rainbow crab paste", ItemID.RAINBOW_CRAB_PASTE, "Grind a rainbow crab, or the GE");

	// --- Potions ---

	/** unf + secondary = one 3-dose potion. */
	private static Node standard(String name, int level, double xp, Herb herb, Node secondary, int d1, int d2, int d3, int d4)
	{
		return standard(name, level, xp, herb.unf, secondary, d1, d2, d3, d4);
	}

	private static Node standard(String name, int level, double xp, Node unf, Node secondary, int d1, int d2, int d3, int d4)
	{
		return Node.dosed(name, d1, d2, d3, d4)
			.madeBy(Recipe.mix(level, 3, "Mix %d " + plural(lower(name)), in(unf, 1), in(secondary, 1)).xp(xp));
	}

	/** A potion finished a dose at a time: {@code perDose} of the secondary per dose. */
	private static Node dosewise(String name, int level, double xp, Node base, Node secondary, int perDose, String step, int d1, int d2, int d3, int d4)
	{
		return Node.dosed(name, d1, d2, d3, d4)
			.madeBy(Recipe.mix(level, 1, step, in(base, 1), in(secondary, perDose)).xp(xp));
	}

	/** Divine potions: one crystal dust per dose. */
	private static Node divine(String name, int level, Node base, int d1, int d2, int d3, int d4)
	{
		return dosewise(name, level, 0.5, base, CRYSTAL_DUST, 1, "Add dust to %d doses", d1, d2, d3, d4);
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
	static final Node CACTUS_SPINE = Node.item("Cactus spine", ItemID.CACTUS_SPINE, "Cactus farming, or the GE");
	static final Node WEAPON_POISON_P_UNF = Node.item("Weapon poison+ (unf)", ItemID.UNFINISHED_WEAPON_POISON_, null)
		.madeBy(Recipe.mix(73, 1, "Make %d weapon poison+ (unf)", in(COCONUT_MILK, 1), in(CACTUS_SPINE, 1)));
	static final Node WEAPON_POISON_P = Node.item("Weapon poison(+)", ItemID.WEAPON_POISON_, null)
		.madeBy(Recipe.mix(73, 1, "Mix %d weapon poison(+)", in(WEAPON_POISON_P_UNF, 1), in(RED_SPIDERS_EGGS, 1)).xp(190));
	static final Node CAVE_NIGHTSHADE = Node.item("Cave nightshade", ItemID.NIGHTSHADE, "Skavid caves spawns, or the GE");
	// The stackable nightshade from belladonna patches works the same.
	static final Node NIGHTSHADE = Node.item("Nightshade", ItemID.STACKABLE_NIGHTSHADE, "Belladonna patches, or the GE");
	static final Node POISON_IVY_BERRIES = Node.item("Poison ivy berries", ItemID.POISONIVY_BERRIES, "Poison ivy bushes, or the GE");
	static final Node WEAPON_POISON_PP_UNF = Node.item("Weapon poison++ (unf)", ItemID.UNFINISHED_WEAPON_POISON__, null)
		.madeBy(Recipe.mix(82, 1, "Make %d weapon poison++ (unf)", in(COCONUT_MILK, 1), Recipe.inAny(CAVE_NIGHTSHADE, 1, NIGHTSHADE)));
	static final Node WEAPON_POISON_PP = Node.item("Weapon poison(++)", ItemID.WEAPON_POISON__, null)
		.madeBy(Recipe.mix(82, 1, "Mix %d weapon poison(++)", in(WEAPON_POISON_PP_UNF, 1), in(POISON_IVY_BERRIES, 1)).xp(190));
	static final Node SUPER_RESTORE = standard("Super restore", 63, 142.5, SNAPDRAGON, RED_SPIDERS_EGGS, ItemID._1DOSE2RESTORE, ItemID._2DOSE2RESTORE, ItemID._3DOSE2RESTORE, ItemID._4DOSE2RESTORE);
	static final Node SUPER_DEFENCE = standard("Super defence", 66, 150, CADANTINE, WHITE_BERRIES, ItemID._1DOSE2DEFENSE, ItemID._2DOSE2DEFENSE, ItemID._3DOSE2DEFENSE, ItemID._4DOSE2DEFENSE);
	static final Node ANTIFIRE = standard("Antifire potion", 69, 157.5, LANTADYME, DRAGON_SCALE_DUST, ItemID._1DOSE1ANTIDRAGON, ItemID._2DOSE1ANTIDRAGON, ItemID._3DOSE1ANTIDRAGON, ItemID._4DOSE1ANTIDRAGON);
	static final Node RANGING = standard("Ranging potion", 72, 162.5, DWARF_WEED, WINE_OF_ZAMORAK, ItemID._1DOSERANGERSPOTION, ItemID._2DOSERANGERSPOTION, ItemID._3DOSERANGERSPOTION, ItemID._4DOSERANGERSPOTION);
	static final Node MAGIC = standard("Magic potion", 76, 172.5, LANTADYME, POTATO_CACTUS, ItemID._1DOSE1MAGIC, ItemID._2DOSE1MAGIC, ItemID._3DOSE1MAGIC, ItemID._4DOSE1MAGIC);
	static final Node ZAMORAK_BREW = standard("Zamorak brew", 78, 175, TORSTOL, JANGERBERRIES, ItemID._1DOSEPOTIONOFZAMORAK, ItemID._2DOSEPOTIONOFZAMORAK, ItemID._3DOSEPOTIONOFZAMORAK, ItemID._4DOSEPOTIONOFZAMORAK);
	static final Node SARADOMIN_BREW = standard("Saradomin brew", 81, 180, TOADFLAX, CRUSHED_NEST, ItemID._1DOSEPOTIONOFSARADOMIN, ItemID._2DOSEPOTIONOFSARADOMIN, ItemID._3DOSEPOTIONOFSARADOMIN, ItemID._4DOSEPOTIONOFSARADOMIN);

	static final Node COMPOST = standard("Compost potion", 22, 60, HARRALANDER, VOLCANIC_ASH, ItemID.SUPERCOMPOST_POTION_1, ItemID.SUPERCOMPOST_POTION_2, ItemID.SUPERCOMPOST_POTION_3, ItemID.SUPERCOMPOST_POTION_4);
	static final Node GOADING = standard("Goading potion", 54, 132, HARRALANDER, ALDARIUM, ItemID._1DOSEGOADING, ItemID._2DOSEGOADING, ItemID._3DOSEGOADING, ItemID._4DOSEGOADING);
	static final Node PRAYER_REGEN = standard("Prayer regeneration potion", 58, 132, HUASCA, ALDARIUM, ItemID._1DOSE1PRAYER_REGENERATION, ItemID._2DOSE1PRAYER_REGENERATION, ItemID._3DOSE1PRAYER_REGENERATION, ItemID._4DOSE1PRAYER_REGENERATION);
	static final Node SUPER_FISHING = standard("Super fishing potion", 62, 140.5, PILLAR_UNF, HADDOCK_EYE, ItemID._1DOSE2FISHERSPOTION, ItemID._2DOSE2FISHERSPOTION, ItemID._3DOSE2FISHERSPOTION, ItemID._4DOSE2FISHERSPOTION);
	static final Node SUPER_HUNTER = standard("Super hunter potion", 67, 154, PILLAR_UNF, CRAB_PASTE, ItemID._1DOSE2HUNTING, ItemID._2DOSE2HUNTING, ItemID._3DOSE2HUNTING, ItemID._4DOSE2HUNTING);
	static final Node BASTION = standard("Bastion potion", 80, 155, CADANTINE_BLOOD_UNF, WINE_OF_ZAMORAK, ItemID._1DOSEBASTION, ItemID._2DOSEBASTION, ItemID._3DOSEBASTION, ItemID._4DOSEBASTION);
	static final Node BATTLEMAGE = standard("Battlemage potion", 80, 155, CADANTINE_BLOOD_UNF, POTATO_CACTUS, ItemID._1DOSEBATTLEMAGE, ItemID._2DOSEBATTLEMAGE, ItemID._3DOSEBATTLEMAGE, ItemID._4DOSEBATTLEMAGE);
	static final Node ANCIENT_BREW = standard("Ancient brew", 85, 190, DWARF_WEED, NIHIL_DUST, ItemID._1DOSEANCIENTBREW, ItemID._2DOSEANCIENTBREW, ItemID._3DOSEANCIENTBREW, ItemID._4DOSEANCIENTBREW);
	static final Node MENAPHITE_REMEDY = standard("Menaphite remedy", 88, 200, DWARF_WEED, LILY_OF_THE_SANDS, ItemID._1DOSESTATRENEWAL, ItemID._2DOSESTATRENEWAL, ItemID._3DOSESTATRENEWAL, ItemID._4DOSESTATRENEWAL);
	static final Node ARMADYL_BREW = standard("Armadyl brew", 89, 205, UMBRAL_UNF, RAINBOW_CRAB_PASTE, ItemID._1DOSEARMADYLBREW, ItemID._2DOSEARMADYLBREW, ItemID._3DOSEARMADYLBREW, ItemID._4DOSEARMADYLBREW);

	// --- Multi-step potions ---

	/** One amylase crystal per dose of super energy. */
	static final Node STAMINA = dosewise("Stamina potion", 77, 25.5, SUPER_ENERGY, AMYLASE, 1, "Add amylase to %d doses",
		ItemID._1DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._4DOSESTAMINA);

	static final Node EXTENDED_STAMINA = dosewise("Extended stamina potion", 85, 27.5, STAMINA, MARLIN_SCALES, 1, "Add scales to %d doses",
		ItemID._1DOSE2STAMINA, ItemID._2DOSE2STAMINA, ItemID._3DOSE2STAMINA, ItemID._4DOSE2STAMINA);

	static final Node EXTREME_ENERGY = dosewise("Extreme energy potion", 66, 21, SUPER_ENERGY, YELLOW_FIN, 1, "Add yellow fins to %d doses",
		ItemID._1DOSE3ENERGY, ItemID._2DOSE3ENERGY, ItemID._3DOSE3ENERGY, ItemID._4DOSE3ENERGY);

	/** One lava scale shard per dose of antifire. */
	static final Node EXTENDED_ANTIFIRE = dosewise("Extended antifire", 84, 27.5, ANTIFIRE, LAVA_SHARD, 1, "Add shards to %d doses",
		ItemID._1DOSE2ANTIDRAGON, ItemID._2DOSE2ANTIDRAGON, ItemID._3DOSE2ANTIDRAGON, ItemID._4DOSE2ANTIDRAGON);

	/** Crushed superior dragon bones + antifire(4) = super antifire(4). */
	static final Node SUPER_ANTIFIRE = Node.dosed("Super antifire", ItemID._1DOSE3ANTIDRAGON, ItemID._2DOSE3ANTIDRAGON, ItemID._3DOSE3ANTIDRAGON, ItemID._4DOSE3ANTIDRAGON)
		.madeBy(Recipe.mix(92, 4, "Mix %d super antifires", in(ANTIFIRE, 4), in(CRUSHED_SUPERIOR_BONES, 1)).xp(130));

	/** Shards on super antifire, or crushed superior bones on extended antifire(4). */
	static final Node EXTENDED_SUPER_ANTIFIRE = dosewise("Extended super antifire", 98, 40, SUPER_ANTIFIRE, LAVA_SHARD, 1, "Add shards to %d doses",
		ItemID._1DOSE4ANTIDRAGON, ItemID._2DOSE4ANTIDRAGON, ItemID._3DOSE4ANTIDRAGON, ItemID._4DOSE4ANTIDRAGON)
		.orMadeBy(Recipe.mix(98, 4, "Add bones to %d ext. antifires", in(EXTENDED_ANTIFIRE, 4), in(CRUSHED_SUPERIOR_BONES, 1)).xp(180));

	/** Torstol (or torstol (unf)) + super attack(4) + super strength(4) + super defence(4). */
	static final Node SUPER_COMBAT = Node.dosed("Super combat", ItemID._1DOSE2COMBAT, ItemID._2DOSE2COMBAT, ItemID._3DOSE2COMBAT, ItemID._4DOSE2COMBAT)
		.madeBy(Recipe.mix(90, 4, "Mix %d super combats",
			in(SUPER_ATTACK, 4), in(SUPER_STRENGTH, 4), in(SUPER_DEFENCE, 4), Recipe.inAny(TORSTOL.clean, 1, TORSTOL.unf)).xp(150));

	static final Node ANTIDOTE_P_UNF = Node.item("Antidote+ (unf)", ItemID.UNFINISHED_ANTIDOTE_, null)
		.madeBy(Recipe.mix(68, 1, "Make %d antidote+ (unf)", in(TOADFLAX.clean, 1), in(COCONUT_MILK, 1)));

	/** Antidote+ and antidote++ come out as 4-dose potions. */
	static final Node ANTIDOTE_P = Node.dosed("Antidote+", ItemID.ANTIDOTE_1, ItemID.ANTIDOTE_2, ItemID.ANTIDOTE_3, ItemID.ANTIDOTE_4)
		.madeBy(Recipe.mix(68, 4, "Mix %d antidote+", in(ANTIDOTE_P_UNF, 1), in(YEW_ROOTS, 1)).xp(155));

	static final Node ANTIDOTE_PP_UNF = Node.item("Antidote++ (unf)", ItemID.UNFINISHED_ANTIDOTE__, null)
		.madeBy(Recipe.mix(79, 1, "Make %d antidote++ (unf)", in(IRIT.clean, 1), in(COCONUT_MILK, 1)));

	static final Node ANTIDOTE_PP = Node.dosed("Antidote++", ItemID.ANTIDOTE__1, ItemID.ANTIDOTE__2, ItemID.ANTIDOTE__3, ItemID.ANTIDOTE__4)
		.madeBy(Recipe.mix(79, 4, "Mix %d antidote++", in(ANTIDOTE_PP_UNF, 1), in(MAGIC_ROOTS, 1)).xp(177.5));

	/** Five Zulrah's scales per dose of antidote++. */
	static final Node ANTI_VENOM = dosewise("Anti-venom", 87, 30, ANTIDOTE_PP, ZULRAH_SCALES, 5, "Add scales to %d doses",
		ItemID.ANTIVENOM1, ItemID.ANTIVENOM2, ItemID.ANTIVENOM3, ItemID.ANTIVENOM4);

	static final Node ANTI_VENOM_PLUS = Node.dosed("Anti-venom+", ItemID.ANTIVENOM_1, ItemID.ANTIVENOM_2, ItemID.ANTIVENOM_3, ItemID.ANTIVENOM_4)
		.madeBy(Recipe.mix(94, 4, "Mix %d anti-venom+", in(ANTI_VENOM, 4), Recipe.inAny(TORSTOL.clean, 1, TORSTOL.unf)).xp(125));

	static final Node EXTENDED_ANTI_VENOM_PLUS = dosewise("Extended anti-venom+", 94, 20, ANTI_VENOM_PLUS, ARAXYTE_VENOM_SAC, 1, "Add venom sacs to %d doses",
		ItemID.EXTENDED_ANTIVENOM_1, ItemID.EXTENDED_ANTIVENOM_2, ItemID.EXTENDED_ANTIVENOM_3, ItemID.EXTENDED_ANTIVENOM_4);

	/** Twenty ancient essence per dose of ancient brew. */
	static final Node FORGOTTEN_BREW = dosewise("Forgotten brew", 91, 36, ANCIENT_BREW, ANCIENT_ESSENCE, 20, "Add essence to %d doses",
		ItemID._1DOSEFORGOTTENBREW, ItemID._2DOSEFORGOTTENBREW, ItemID._3DOSEFORGOTTENBREW, ItemID._4DOSEFORGOTTENBREW);

	// Sanfew serum: three secondaries, one per potion whatever its dose, so it is planned as 4-dose potions.
	static final Node SANFEW_STEP_1 = Node.dosed("Mixture - step 1", ItemID.SANFEW_SALVE_STEP_1_1_DOSE, ItemID.SANFEW_SALVE_STEP_1_2_DOSE, ItemID.SANFEW_SALVE_STEP_1_3_DOSE, ItemID.SANFEW_SALVE_STEP_1_4_DOSE)
		.madeBy(Recipe.mix(65, 4, "Add horn dust to %d restores", in(SUPER_RESTORE, 4), in(UNICORN_HORN_DUST, 1)).xp(57));
	static final Node SANFEW_STEP_2 = Node.dosed("Mixture - step 2", ItemID.SANFEW_SALVE_STEP_2_1_DOSE, ItemID.SANFEW_SALVE_STEP_2_2_DOSE, ItemID.SANFEW_SALVE_STEP_2_3_DOSE, ItemID.SANFEW_SALVE_STEP_2_4_DOSE)
		.madeBy(Recipe.mix(65, 4, "Add snake weed to %d mixtures", in(SANFEW_STEP_1, 4), in(SNAKE_WEED, 1)).xp(63));
	static final Node SANFEW_SERUM = Node.dosed("Sanfew serum", ItemID.SANFEW_SALVE_1_DOSE, ItemID.SANFEW_SALVE_2_DOSE, ItemID.SANFEW_SALVE_3_DOSE, ItemID.SANFEW_SALVE_4_DOSE)
		.madeBy(Recipe.mix(65, 4, "Mix %d sanfew serums", in(SANFEW_STEP_2, 4), in(NAIL_BEAST_NAILS, 1)).xp(72));

	static final Node HAEMOSTATIC_POULTICE = Node.item("Haemostatic poultice", ItemID.HAEMOSTATIC_POULTICE, null)
		.madeBy(Recipe.mix(56, 1, "Mix %d poultices", in(ELKHORN_UNF, 1), in(SQUID_PASTE, 1)).xp(27));
	static final Node HAEMOSTATIC_DRESSING = Node.dosed("Haemostatic dressing", ItemID._1DOSEHAEMOSTATICDRESSING, ItemID._2DOSEHAEMOSTATICDRESSING, ItemID._3DOSEHAEMOSTATICDRESSING, ItemID._4DOSEHAEMOSTATICDRESSING)
		.madeBy(Recipe.mix(56, 3, "Mix %d dressings", in(HAEMOSTATIC_POULTICE, 1), in(COTTON_YARN, 1)).xp(100));

	// --- Divine potions ---

	static final Node DIVINE_SUPER_ATTACK = divine("Divine super attack", 70, SUPER_ATTACK, ItemID._1DOSEDIVINEATTACK, ItemID._2DOSEDIVINEATTACK, ItemID._3DOSEDIVINEATTACK, ItemID._4DOSEDIVINEATTACK);
	static final Node DIVINE_SUPER_STRENGTH = divine("Divine super strength", 70, SUPER_STRENGTH, ItemID._1DOSEDIVINESTRENGTH, ItemID._2DOSEDIVINESTRENGTH, ItemID._3DOSEDIVINESTRENGTH, ItemID._4DOSEDIVINESTRENGTH);
	static final Node DIVINE_SUPER_DEFENCE = divine("Divine super defence", 70, SUPER_DEFENCE, ItemID._1DOSEDIVINEDEFENCE, ItemID._2DOSEDIVINEDEFENCE, ItemID._3DOSEDIVINEDEFENCE, ItemID._4DOSEDIVINEDEFENCE);
	static final Node DIVINE_RANGING = divine("Divine ranging potion", 74, RANGING, ItemID._1DOSEDIVINERANGE, ItemID._2DOSEDIVINERANGE, ItemID._3DOSEDIVINERANGE, ItemID._4DOSEDIVINERANGE);
	static final Node DIVINE_MAGIC = divine("Divine magic potion", 78, MAGIC, ItemID._1DOSEDIVINEMAGIC, ItemID._2DOSEDIVINEMAGIC, ItemID._3DOSEDIVINEMAGIC, ItemID._4DOSEDIVINEMAGIC);
	static final Node DIVINE_BASTION = divine("Divine bastion potion", 86, BASTION, ItemID._1DOSEDIVINEBASTION, ItemID._2DOSEDIVINEBASTION, ItemID._3DOSEDIVINEBASTION, ItemID._4DOSEDIVINEBASTION);
	static final Node DIVINE_BATTLEMAGE = divine("Divine battlemage potion", 86, BATTLEMAGE, ItemID._1DOSEDIVINEBATTLEMAGE, ItemID._2DOSEDIVINEBATTLEMAGE, ItemID._3DOSEDIVINEBATTLEMAGE, ItemID._4DOSEDIVINEBATTLEMAGE);
	static final Node DIVINE_SUPER_COMBAT = divine("Divine super combat", 97, SUPER_COMBAT, ItemID._1DOSEDIVINECOMBAT, ItemID._2DOSEDIVINECOMBAT, ItemID._3DOSEDIVINECOMBAT, ItemID._4DOSEDIVINECOMBAT);
}
