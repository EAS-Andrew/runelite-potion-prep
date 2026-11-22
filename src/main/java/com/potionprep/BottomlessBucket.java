package com.potionprep;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * Uses left in the bottomless compost bucket and what it holds. The game
 * only shows these in chat (Check, filling, using it) and, for a bucket
 * stored with a tool leprechaun, in the leprechaun's varbits. The plugin
 * saves the result per account so it survives logging out.
 *
 * Type: 0 unknown, 1 compost, 2 supercompost, 3 ultracompost.
 */
@Getter
final class BottomlessBucket
{
	private static final String TYPES = "(ultracompost|supercompost|compost)";
	private static final String COUNT = "([\\d,]+|one|a single)";
	static final Pattern CHECK = Pattern.compile(
		"Your bottomless compost bucket is currently holding " + COUNT + " uses? of " + TYPES);
	static final Pattern EMPTY = Pattern.compile(
		"Your (?:bottomless )?compost bucket (?:is currently empty|has run out)");
	static final Pattern FILL_TYPE = Pattern.compile(
		"You fill your bottomless compost bucket with (?:a single bucket|[\\d,]+ buckets) of " + TYPES);
	static final Pattern FILL_TOTAL = Pattern.compile(
		"Your bottomless compost bucket now contains a total of " + COUNT + " uses?");
	/** Sent after a use on some accounts: "Your bottomless compost bucket has 12 uses of ultracompost remaining." */
	static final Pattern LEFT = Pattern.compile(
		"Your bottomless compost bucket has " + COUNT + " uses? (?:of " + TYPES + " )?(?:left|remaining)");
	/** As in RuneLite's CompostTracker. */
	static final Pattern TREATED = Pattern.compile("You treat the .+ with (ultra|super|)compost");

	/** Uses left, -1 if not known. */
	private int uses;
	private int type;
	/** A use seen this tick, applied at the end of it unless the game also said how many are left. */
	private boolean pendingUse;

	BottomlessBucket(int uses, int type)
	{
		this.uses = uses;
		this.type = type;
	}

	/**
	 * @param bucketUsed the bucket was the item used on the patch (null if not known)
	 * @return true if the uses or type changed
	 */
	boolean onMessage(String msg, Boolean bucketUsed)
	{
		Matcher m = CHECK.matcher(msg);
		if (m.find())
		{
			pendingUse = false;
			return set(count(m.group(1)), type(m.group(2)));
		}
		if (EMPTY.matcher(msg).find())
		{
			pendingUse = false;
			return set(0, type);
		}
		m = LEFT.matcher(msg);
		if (m.find())
		{
			pendingUse = false;
			return set(count(m.group(1)), m.group(2) != null ? type(m.group(2)) : type);
		}
		m = FILL_TYPE.matcher(msg);
		if (m.find())
		{
			return set(uses, type(m.group(1)));
		}
		m = FILL_TOTAL.matcher(msg);
		if (m.find())
		{
			return set(count(m.group(1)), type);
		}
		m = TREATED.matcher(msg);
		if (m.find() && Boolean.TRUE.equals(bucketUsed) && uses > 0)
		{
			pendingUse = true;
		}
		return false;
	}

	private static final Pattern DIALOG_USES = Pattern.compile("([\\d,]+) uses");

	/**
	 * The item box shown when you Check the bucket, e.g. "Your bottomless
	 * compost bucket is currently holding 1,204 uses of ultracompost."
	 *
	 * @return true if the uses or type changed
	 */
	boolean onDialog(String text)
	{
		String t = text.replace("<br>", " ");
		if (!t.contains("compost bucket"))
		{
			return false;
		}
		if (t.contains("currently empty") || t.startsWith("You discard"))
		{
			return set(0, type);
		}
		Matcher m = DIALOG_USES.matcher(t);
		int n;
		if (m.find())
		{
			n = count(m.group(1));
		}
		else if (t.contains("one use"))
		{
			n = 1;
		}
		else
		{
			return false;
		}
		return set(n, t.contains("ultracompost") ? 3 : t.contains("supercompost") ? 2 : 1);
	}

	/** End of a game tick: count a use the game didn't report a total for. */
	boolean onTick()
	{
		if (!pendingUse)
		{
			return false;
		}
		pendingUse = false;
		return set(uses - 1, type);
	}

	/**
	 * The leprechaun's bucket varbits: 0 none stored, 1 stored empty, 2-4
	 * compost to ultracompost. Taking it out zeroes them, which says
	 * nothing about the bucket, so that's ignored.
	 */
	boolean onLeprechaun(int storedType, int storedUses)
	{
		if (storedType == 1)
		{
			return set(0, type);
		}
		if (storedType >= 2 && storedUses > 0)
		{
			return set(storedUses, storedType - 1);
		}
		return false;
	}

	/** The filled bucket turned into the empty one: no uses left. */
	boolean onInventory(boolean empty, boolean filled)
	{
		return empty && !filled && uses != 0 && set(0, type);
	}

	private boolean set(int newUses, int newType)
	{
		boolean changed = newUses != uses || newType != type;
		uses = Math.max(newUses, 0);
		type = newType;
		return changed;
	}

	private static int count(String s)
	{
		return s.equals("one") || s.equals("a single") ? 1 : Integer.parseInt(s.replace(",", ""));
	}

	private static int type(String s)
	{
		return s.startsWith("ultra") ? 3 : s.startsWith("super") ? 2 : 1;
	}
}
