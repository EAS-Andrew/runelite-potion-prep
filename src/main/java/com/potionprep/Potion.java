package com.potionprep;

import lombok.Getter;

/** The potions offered in the panel, each the root of a recipe tree. */
@Getter
public enum Potion
{
	ATTACK(Catalog.ATTACK),
	ANTIPOISON(Catalog.ANTIPOISON),
	STRENGTH(Catalog.STRENGTH),
	RESTORE(Catalog.RESTORE),
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
	SUPER_STRENGTH(Catalog.SUPER_STRENGTH),
	WEAPON_POISON(Catalog.WEAPON_POISON),
	SUPER_RESTORE(Catalog.SUPER_RESTORE),
	SUPER_DEFENCE(Catalog.SUPER_DEFENCE),
	ANTIFIRE(Catalog.ANTIFIRE),
	RANGING(Catalog.RANGING),
	MAGIC(Catalog.MAGIC),
	STAMINA(Catalog.STAMINA),
	ZAMORAK_BREW(Catalog.ZAMORAK_BREW),
	ANTIDOTE_PLUS_PLUS(Catalog.ANTIDOTE_PP),
	SARADOMIN_BREW(Catalog.SARADOMIN_BREW),
	EXTENDED_ANTIFIRE(Catalog.EXTENDED_ANTIFIRE),
	ANTI_VENOM(Catalog.ANTI_VENOM),
	SUPER_COMBAT(Catalog.SUPER_COMBAT),
	SUPER_ANTIFIRE(Catalog.SUPER_ANTIFIRE),
	ANTI_VENOM_PLUS(Catalog.ANTI_VENOM_PLUS),
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

	/**
	 * Final crafts per displayed potion. Dose-by-dose recipes (stamina,
	 * extended antifire, anti-venom) are shown as 4-dose potions.
	 */
	public int craftsPerPotion()
	{
		return product.isDosed() && product.getRecipe().getYield() == 1 ? 4 : 1;
	}

	/** Potions you can make from this many final crafts. */
	public int toPotions(int crafts)
	{
		return crafts / craftsPerPotion();
	}

	/** What one displayed potion is, e.g. "Stamina potion(4)", "Prayer potion(3)". */
	public String potionName()
	{
		if (!product.isDosed())
		{
			return getName();
		}
		return getName() + (product.getRecipe().getYield() == 3 ? "(3)" : "(4)");
	}

	@Override
	public String toString()
	{
		return getName() + " (" + getLevel() + ")";
	}
}
