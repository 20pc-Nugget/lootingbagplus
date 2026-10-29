/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * The set of game messages recognised here follows the seed box tracking of Dude, Where's My Stuff,
 * Copyright 2022 Thource, BSD 2-Clause. See LICENSE.
 */
package com.lootingbagplus.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the game messages that report seeds going into or out of the seed box, so the seed box
 * overlay can stay live without the box being opened. Pure logic with no RuneLite imports.
 */
public final class SeedBoxMessages
{
	private static final Pattern[] ADDITIONS = {
		Pattern.compile("Stored (?<count>[\\d,]+) x (?<seed>.+) in your seed box\\."),
		Pattern.compile("You put (?<count>[\\d,]+) x (?<seed>.+) straight into your open seed box\\."),
		Pattern.compile("The following stolen loot gets added to your seed box: (?<seed>.+) x (?<count>[\\d,]+)\\."),
		Pattern.compile("You put the stolen (?<seed>.+) into your seed box\\."),
	};

	private static final Pattern[] REMOVALS = {
		Pattern.compile("Emptied (?<count>[\\d,]+) x (?<seed>.+) to your inventory\\."),
		Pattern.compile("(?:A|An|(?<count>[\\d,]+)) (?<seed>.+) (?:was|were) taken from your seed box\\."),
	};

	private SeedBoxMessages()
	{
	}

	/** One recognised seed box message. */
	public static final class Change
	{
		private final String seedName;
		private final int quantity;
		private final boolean added;

		Change(String seedName, int quantity, boolean added)
		{
			this.seedName = seedName;
			this.quantity = quantity;
			this.added = added;
		}

		/** The seed's name as written in the message, lower case. */
		public String getSeedName()
		{
			return seedName;
		}

		public int getQuantity()
		{
			return quantity;
		}

		/** True if the seeds went into the box, false if they came out. */
		public boolean isAdded()
		{
			return added;
		}
	}

	/**
	 * @return what the message reports, or {@code null} if it is not about the seed box
	 */
	public static Change parse(String message)
	{
		if (message == null)
		{
			return null;
		}

		String text = message.replaceAll("<[^>]*>", "").replace('\u00A0', ' ').trim();

		Change change = match(text, ADDITIONS, true);
		return change != null ? change : match(text, REMOVALS, false);
	}

	private static Change match(String text, Pattern[] patterns, boolean added)
	{
		for (Pattern pattern : patterns)
		{
			Matcher matcher = pattern.matcher(text);
			if (!matcher.matches())
			{
				continue;
			}

			int quantity = 1;
			if (pattern.pattern().contains("(?<count>") && countGroupPresent(matcher))
			{
				try
				{
					quantity = Integer.parseInt(matcher.group("count").replace(",", ""));
				}
				catch (NumberFormatException e)
				{
					return null;
				}
			}

			if (quantity <= 0)
			{
				return null;
			}

			return new Change(matcher.group("seed").trim().toLowerCase(Locale.ROOT), quantity, added);
		}
		return null;
	}

	/** The count group is optional in one pattern ("A Ranarr seed was taken..."). */
	private static boolean countGroupPresent(Matcher matcher)
	{
		return matcher.group("count") != null;
	}
}
