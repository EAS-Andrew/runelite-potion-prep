package com.potionprep;

/**
 * Expected herbs from one planted herb patch, using the OSRS Wiki's
 * chance-to-save formula: each harvest has a chance not to use a "life",
 * compost sets the starting lives (3 + 0..3) and how likely disease is.
 */
final class Yield
{
	/** Chance-to-save at 99 Farming, for every herb. */
	static final int HIGH_SAVE = 80;
	/** Disease chance per growth cycle, out of 128, by compost tier (none, compost, super, ultra). */
	private static final int[] DISEASE = {27, 14, 6, 3};

	private Yield()
	{
	}

	/**
	 * @param lowSave chance-to-save at level 1 (out of 256)
	 * @param bonus   added fraction: magic secateurs 0.10, Farming cape 0.05, diaries
	 */
	static double chanceToSave(int lowSave, int level, double bonus)
	{
		double base = Math.floor(lowSave * (99.0 - level) / 98.0 + HIGH_SAVE * (level - 1.0) / 98.0);
		return Math.floor(base * (1 + bonus) + 1) / 256.0;
	}

	/**
	 * @param compost 0 none, 1 compost, 2 supercompost, 3 ultracompost
	 */
	static double expected(int lowSave, int level, double bonus, int compost, boolean diseaseFree)
	{
		int tier = Math.max(0, Math.min(3, compost));
		double herbs = (3 + tier) / (1 - chanceToSave(lowSave, level, bonus));
		// Disease can strike in each of the 3 growth cycles before the crop is grown.
		double survive = diseaseFree ? 1 : Math.pow(1 - DISEASE[tier] / 128.0, 3);
		return herbs * survive;
	}

	/** Snape grass chance-to-save at level 1 and 99, out of 256 (OSRS Wiki yield calculator). */
	static final int SNAPE_LOW = 148;
	static final int SNAPE_HIGH = 195;

	/**
	 * Expected snape grass from one allotment. Compost adds lives; magic secateurs
	 * multiply the chance-to-save constants by 1.1. Disease rates for allotments
	 * aren't known, so this assumes the crop survives.
	 */
	static double snapeGrass(int level, boolean secateurs, int compost)
	{
		double m = secateurs ? 1.10 : 1.0;
		double save = (1 + Math.floor(SNAPE_LOW * m * (99.0 - level) / 98.0 + SNAPE_HIGH * m * (level - 1.0) / 98.0 + 0.5)) / 256.0;
		save = Math.min(save, 0.99);
		int tier = Math.max(0, Math.min(3, compost));
		return (3 + tier) / (1 - save);
	}

	/**
	 * Expected limpwurt roots from one flower patch: 3, plus a random 0..n bonus
	 * where n grows by one every 10 Farming levels from 11 (up to 9 at 91+).
	 * Magic secateurs improve the bonus by about 10%. Compost does nothing.
	 */
	static double limpwurt(int level, boolean secateurs)
	{
		int maxBonus = Math.max(0, Math.min(9, (level - 1) / 10));
		double bonus = maxBonus / 2.0 * (secateurs ? 1.10 : 1.0);
		return 3 + Math.min(bonus, maxBonus);
	}
}
