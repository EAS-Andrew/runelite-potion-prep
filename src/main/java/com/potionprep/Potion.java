package com.potionprep;

import lombok.Getter;

/** The potions offered in the panel, each the root of a recipe tree, roughly by level. */
@Getter
public enum Potion
{
	ATTACK(Catalog.ATTACK),
	ANTIPOISON(Catalog.ANTIPOISON),
	STRENGTH(Catalog.STRENGTH),
	RESTORE(Catalog.RESTORE),
	COMPOST(Catalog.COMPOST),
	ENERGY(Catalog.ENERGY),
	DEFENCE(Catalog.DEFENCE),
	AGILITY(Catalog.AGILITY),
	COMBAT(Catalog.COMBAT),
	PRAYER(Catalog.PRAYER),
	SUPER_ATTACK(Catalog.SUPER_ATTACK),
	SUPERANTIPOISON(Catalog.SUPERANTIPOISON),
	FISHING(Catalog.FISHING),
	SUPER_ENERGY(Catalog.SUPER_ENERGY),
	HUNTER(Catalog.HUNTER),
	GOADING(Catalog.GOADING),
	SUPER_STRENGTH(Catalog.SUPER_STRENGTH),
	HAEMOSTATIC_POULTICE(Catalog.HAEMOSTATIC_POULTICE),
	HAEMOSTATIC_DRESSING(Catalog.HAEMOSTATIC_DRESSING),
	PRAYER_REGENERATION(Catalog.PRAYER_REGEN),
	WEAPON_POISON(Catalog.WEAPON_POISON),
	SUPER_FISHING(Catalog.SUPER_FISHING),
	SUPER_RESTORE(Catalog.SUPER_RESTORE),
	SANFEW_SERUM(Catalog.SANFEW_SERUM),
	SUPER_DEFENCE(Catalog.SUPER_DEFENCE),
	EXTREME_ENERGY(Catalog.EXTREME_ENERGY),
	SUPER_HUNTER(Catalog.SUPER_HUNTER),
	ANTIDOTE_PLUS(Catalog.ANTIDOTE_P),
	WEAPON_POISON_PLUS(Catalog.WEAPON_POISON_P),
	ANTIFIRE(Catalog.ANTIFIRE),
	DIVINE_SUPER_ATTACK(Catalog.DIVINE_SUPER_ATTACK),
	DIVINE_SUPER_STRENGTH(Catalog.DIVINE_SUPER_STRENGTH),
	DIVINE_SUPER_DEFENCE(Catalog.DIVINE_SUPER_DEFENCE),
	RANGING(Catalog.RANGING),
	DIVINE_RANGING(Catalog.DIVINE_RANGING),
	MAGIC(Catalog.MAGIC),
	STAMINA(Catalog.STAMINA),
	ZAMORAK_BREW(Catalog.ZAMORAK_BREW),
	DIVINE_MAGIC(Catalog.DIVINE_MAGIC),
	ANTIDOTE_PLUS_PLUS(Catalog.ANTIDOTE_PP),
	BASTION(Catalog.BASTION),
	BATTLEMAGE(Catalog.BATTLEMAGE),
	SARADOMIN_BREW(Catalog.SARADOMIN_BREW),
	WEAPON_POISON_PLUS_PLUS(Catalog.WEAPON_POISON_PP),
	EXTENDED_ANTIFIRE(Catalog.EXTENDED_ANTIFIRE),
	ANCIENT_BREW(Catalog.ANCIENT_BREW),
	EXTENDED_STAMINA(Catalog.EXTENDED_STAMINA),
	DIVINE_BASTION(Catalog.DIVINE_BASTION),
	DIVINE_BATTLEMAGE(Catalog.DIVINE_BATTLEMAGE),
	ANTI_VENOM(Catalog.ANTI_VENOM),
	MENAPHITE_REMEDY(Catalog.MENAPHITE_REMEDY),
	ARMADYL_BREW(Catalog.ARMADYL_BREW),
	SUPER_COMBAT(Catalog.SUPER_COMBAT),
	FORGOTTEN_BREW(Catalog.FORGOTTEN_BREW),
	SUPER_ANTIFIRE(Catalog.SUPER_ANTIFIRE),
	ANTI_VENOM_PLUS(Catalog.ANTI_VENOM_PLUS),
	EXTENDED_ANTI_VENOM_PLUS(Catalog.EXTENDED_ANTI_VENOM_PLUS),
	DIVINE_SUPER_COMBAT(Catalog.DIVINE_SUPER_COMBAT),
	EXTENDED_SUPER_ANTIFIRE(Catalog.EXTENDED_SUPER_ANTIFIRE);

	private final Node product;

	Potion(Node product)
	{
		this.product = product;
	}

	public String getName()
	{
		return product.getName();
	}

	public int getLevel()
	{
		return product.getRecipe().getLevel();
	}

	/** Made a dose at a time, so each potion keeps its base potion's dose. */
	public boolean isPerDose()
	{
		return product.isDosed() && product.getRecipe().isPerDose();
	}

	/**
	 * Kept for callers: {@link PotionPlan#makeable} and the plan's counts are
	 * already in potions, so this is the identity.
	 */
	public int toPotions(int potions)
	{
		return potions;
	}

	/**
	 * What one potion is, e.g. "Prayer potion(3)", "Super combat(4)". Potions
	 * made a dose at a time take their base's dose, so they get no suffix;
	 * {@link PotionPlan#getLabel} has the dose once it is known.
	 */
	public String potionName()
	{
		if (!product.isDosed() || isPerDose())
		{
			return getName();
		}
		return getName() + "(" + Math.min(4, product.getRecipe().getYield()) + ")";
	}

	@Override
	public String toString()
	{
		return getName() + " (" + getLevel() + ")";
	}
}
