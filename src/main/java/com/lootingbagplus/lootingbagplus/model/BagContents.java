/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.model;

import java.util.Arrays;

/**
 * What the looting bag held the last time the game told us (container 516), in the game's own
 * container order. Pure data with no RuneLite imports.
 *
 * <p>An id that is not positive means an empty container slot.
 */
public final class BagContents
{
	public static final int SLOTS = BagLayout.SLOTS;

	private static final String EMPTY_TOKEN = "empty";

	private final int[] ids = new int[SLOTS];
	private final int[] quantities = new int[SLOTS];

	private BagContents()
	{
		Arrays.fill(ids, -1);
	}

	public static BagContents empty()
	{
		return new BagContents();
	}

	/**
	 * @param ids        container item ids, {@code <= 0} for an empty slot; entries beyond
	 *                   {@link #SLOTS} are ignored
	 * @param quantities matching quantities; missing entries are treated as 0
	 */
	public static BagContents of(int[] ids, int[] quantities)
	{
		BagContents contents = new BagContents();
		for (int i = 0; i < SLOTS && i < ids.length; i++)
		{
			int quantity = i < quantities.length ? quantities[i] : 0;
			if (ids[i] > 0 && quantity > 0)
			{
				contents.ids[i] = ids[i];
				contents.quantities[i] = quantity;
			}
		}
		return contents;
	}

	public int idAt(int index)
	{
		return ids[index];
	}

	public int quantityAt(int index)
	{
		return quantities[index];
	}

	public boolean isEmpty(int index)
	{
		return ids[index] <= 0;
	}

	public int usedSlots()
	{
		int used = 0;
		for (int id : ids)
		{
			if (id > 0)
			{
				used++;
			}
		}
		return used;
	}

	/**
	 * Text form for config: {@code index:id:quantity} triples separated by commas, or the literal
	 * {@code empty} for a bag that is known to be empty.
	 */
	public String encode()
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < SLOTS; i++)
		{
			if (ids[i] <= 0)
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(i).append(':').append(ids[i]).append(':').append(quantities[i]);
		}
		return sb.length() == 0 ? EMPTY_TOKEN : sb.toString();
	}

	/**
	 * Inverse of {@link #encode()}.
	 *
	 * @return the contents, or {@code null} when nothing usable is stored (never synced, or the
	 * stored value is damaged), so the caller can tell "unknown" apart from "known empty"
	 */
	public static BagContents decode(String text)
	{
		if (text == null)
		{
			return null;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}
		if (trimmed.equals(EMPTY_TOKEN))
		{
			return empty();
		}

		BagContents contents = new BagContents();
		for (String triple : trimmed.split(","))
		{
			String[] parts = triple.split(":");
			if (parts.length != 3)
			{
				return null;
			}
			try
			{
				int index = Integer.parseInt(parts[0].trim());
				int id = Integer.parseInt(parts[1].trim());
				int quantity = Integer.parseInt(parts[2].trim());
				if (index < 0 || index >= SLOTS || id <= 0 || quantity <= 0)
				{
					return null;
				}
				contents.ids[index] = id;
				contents.quantities[index] = quantity;
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}
		return contents;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof BagContents))
		{
			return false;
		}
		BagContents other = (BagContents) o;
		return Arrays.equals(ids, other.ids) && Arrays.equals(quantities, other.quantities);
	}

	@Override
	public int hashCode()
	{
		return 31 * Arrays.hashCode(ids) + Arrays.hashCode(quantities);
	}
}
