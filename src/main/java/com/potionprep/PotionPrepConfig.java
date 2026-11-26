package com.potionprep;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(PotionPrepConfig.GROUP)
public interface PotionPrepConfig extends Config
{
	String GROUP = "potionprep";

	@Range(min = 1, max = 8)
	@ConfigItem(
		keyName = "column",
		name = "Bank column",
		description = "Which bank column the ingredients go in. 8 is the right-most column.",
		position = 1
	)
	default int column()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "missingInfobox",
		name = "Missing items infobox",
		description = "An infobox warning of what you're missing: herb run items not carried before the first patch, or Herblore gear you own but aren't wearing while prepping a potion.",
		position = 5
	)
	default boolean missingInfobox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInBank",
		name = "",
		description = "",
		hidden = true
	)
	default boolean showInBank()
	{
		return false;
	}

	@ConfigItem(
		keyName = "potion",
		name = "",
		description = "",
		hidden = true
	)
	default String potion()
	{
		return "";
	}

	@ConfigItem(
		keyName = "mode",
		name = "",
		description = "",
		hidden = true
	)
	default String mode()
	{
		return "";
	}

	@ConfigItem(
		keyName = "farmHerb",
		name = "",
		description = "",
		hidden = true
	)
	default String farmHerb()
	{
		return "";
	}

	@ConfigItem(
		keyName = "farmFlower",
		name = "",
		description = "",
		hidden = true
	)
	default String farmFlower()
	{
		return "";
	}

	@ConfigItem(
		keyName = "farmAllotment",
		name = "",
		description = "",
		hidden = true
	)
	default String farmAllotment()
	{
		return "";
	}

	@ConfigItem(
		keyName = "farmPatches",
		name = "",
		description = "",
		hidden = true
	)
	default String farmPatches()
	{
		return "";
	}

	@ConfigItem(
		keyName = "farmOrder",
		name = "",
		description = "",
		hidden = true
	)
	default String farmOrder()
	{
		return "";
	}

	@ConfigItem(
		keyName = "farmTeleports",
		name = "",
		description = "",
		hidden = true
	)
	default String farmTeleports()
	{
		return "";
	}

	@ConfigItem(
		keyName = "skipGrowing",
		name = "Skip growing patches",
		description = "Leave herb patches that are still growing out of the run. Needs RuneLite's Time Tracking plugin.",
		position = 2
	)
	default boolean skipGrowing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "saveRunes",
		name = "Save runes",
		description = "Pick teleports that cost no runes (items, tablets, fairy rings, house portals) before spells.",
		position = 4
	)
	default boolean saveRunes()
	{
		return false;
	}

	@ConfigSection(
		name = "Herb run travel",
		description = "Things the plugin can't see from the game. Portals in your house are also picked up when you visit it.",
		position = 10,
		closedByDefault = true
	)
	String travelSection = "travel";

	@ConfigItem(
		keyName = "scanHouse",
		name = "Learn portals from house",
		description = "Remember portal chamber portals seen in your house. Only houses entered by your own portal, house tab, spell or cape count.",
		section = travelSection,
		position = 0
	)
	default boolean scanHouse()
	{
		return true;
	}

	@ConfigItem(
		keyName = "portal_FALADOR",
		name = "House: Falador",
		description = "Your house has a Falador portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 1
	)
	default boolean portalFalador()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_CAMELOT",
		name = "House: Camelot",
		description = "Your house has a Camelot portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 2
	)
	default boolean portalCamelot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_ARDOUGNE",
		name = "House: Ardougne",
		description = "Your house has a Ardougne portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 3
	)
	default boolean portalArdougne()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_FISHING_GUILD",
		name = "House: Fishing Guild",
		description = "Your house has a Fishing Guild portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 4
	)
	default boolean portalFishingGuild()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_CATHERBY",
		name = "House: Catherby",
		description = "Your house has a Catherby portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 5
	)
	default boolean portalCatherby()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_FENKENSTRAIN",
		name = "House: Fenkenstrain's Castle",
		description = "Your house has a Fenkenstrain's Castle portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 6
	)
	default boolean portalFenkenstrain()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_TROLLHEIM",
		name = "House: Trollheim",
		description = "Your house has a Trollheim portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 7
	)
	default boolean portalTrollheim()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_TROLL_STRONGHOLD",
		name = "House: Troll Stronghold",
		description = "Your house has a Troll Stronghold portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 8
	)
	default boolean portalTrollStronghold()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_WEISS",
		name = "House: Weiss",
		description = "Your house has a Weiss portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 9
	)
	default boolean portalWeiss()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_HARMONY",
		name = "House: Harmony Island",
		description = "Your house has a Harmony Island portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 10
	)
	default boolean portalHarmony()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_CIVITAS",
		name = "House: Civitas illa Fortis",
		description = "Your house has a Civitas illa Fortis portal (portal chamber or portal nexus)",
		section = travelSection,
		position = 11
	)
	default boolean portalCivitas()
	{
		return false;
	}

	@ConfigItem(
		keyName = "portal_XERIC",
		name = "House: mounted Xeric's",
		description = "Your house has a mounted Xeric's talisman (Xeric's Glade for Hosidius)",
		section = travelSection,
		position = 12
	)
	default boolean portalXeric()
	{
		return false;
	}

	@ConfigItem(
		keyName = "spiritGuild",
		name = "Spirit tree: Farming Guild",
		description = "You've grown a spirit tree in the Farming Guild",
		section = travelSection,
		position = 12
	)
	default boolean spiritGuild()
	{
		return false;
	}

	@ConfigItem(
		keyName = "spiritHosidius",
		name = "Spirit tree: Hosidius",
		description = "You've grown a spirit tree at Hosidius",
		section = travelSection,
		position = 13
	)
	default boolean spiritHosidius()
	{
		return false;
	}

	@ConfigItem(
		keyName = "spiritPortSarim",
		name = "Spirit tree: Port Sarim",
		description = "You've grown a spirit tree at Port Sarim (just south of the Falador farm)",
		section = travelSection,
		position = 13
	)
	default boolean spiritPortSarim()
	{
		return false;
	}

	@ConfigItem(
		keyName = "spiritHouse",
		name = "Spirit tree in house",
		description = "Your house has a spirit tree (or spirit tree and fairy ring)",
		section = travelSection,
		position = 14
	)
	default boolean spiritHouse()
	{
		return false;
	}

	@ConfigItem(
		keyName = "fairyHouse",
		name = "Fairy ring in house",
		description = "Your house has a fairy ring (or spirit tree and fairy ring). Picked up on a visit to your house too.",
		section = travelSection,
		position = 15
	)
	default boolean fairyHouse()
	{
		return false;
	}
}
