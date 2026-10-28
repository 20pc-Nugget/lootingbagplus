/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.model;

import java.util.Arrays;

/**
 * The items a storage container (the seed box) last reported, with empty slots dropped, in the
 * game's own order. Pure data with no RuneLite imports.
 */
public final class StoredItems
{
	/** More than any storage container we read can hold; a safety cap, not a game rule. */
	public static final int MAX_ITEMS = 64;

	private static final String EMPTY_TOKEN = "empty";

	private final int[] ids;
	private final int[] quantities;

	private StoredItems(int[] ids, int[] quantities)
	{
		this.ids = ids;
		this.quantities = quantities;
	}

	public static StoredItems empty()
	{
		return new StoredItems(new int[0], new int[0]);
	}

	/**
	 * @param ids        container item ids; entries that are not positive are empty slots
	 * @param quantities matching quantities; entries that are not positive are skipped
	 */
	public static StoredItems of(int[] ids, int[] quantities)
	{
		int[] keptIds = new int[Math.min(ids.length, MAX_ITEMS)];
		int[] keptQuantities = new int[keptIds.length];
		int count = 0;
		for (int i = 0; i < ids.length && count < MAX_ITEMS; i++)
		{
			int quantity = i < quantities.length ? quantities[i] : 0;
			if (ids[i] > 0 && quantity > 0)
			{
				keptIds[count] = ids[i];
				keptQuantities[count] = quantity;
				count++;
			}
		}
		return new StoredItems(Arrays.copyOf(keptIds, count), Arrays.copyOf(keptQuantities, count));
	}

	public int size()
	{
		return ids.length;
	}

	public int idAt(int index)
	{
		return ids[index];
	}

	public int quantityAt(int index)
	{
		return quantities[index];
	}

	/** Text form for config: {@code id:quantity} pairs separated by commas, or {@code empty}. */
	public String encode()
	{
		if (ids.length == 0)
		{
			return EMPTY_TOKEN;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ids.length; i++)
		{
			if (i > 0)
			{
				sb.append(',');
			}
			sb.append(ids[i]).append(':').append(quantities[i]);
		}
		return sb.toString();
	}

	/**
	 * Inverse of {@link #encode()}.
	 *
	 * @return the items, or {@code null} when nothing usable is stored (never seen, or damaged), so
	 * the caller can tell "unknown" apart from "known empty"
	 */
	public static StoredItems decode(String text)
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

		String[] pairs = trimmed.split(",");
		if (pairs.length > MAX_ITEMS)
		{
			return null;
		}

		int[] ids = new int[pairs.length];
		int[] quantities = new int[pairs.length];
		for (int i = 0; i < pairs.length; i++)
		{
			String[] parts = pairs[i].split(":");
			if (parts.length != 2)
			{
				return null;
			}
			try
			{
				ids[i] = Integer.parseInt(parts[0].trim());
				quantities[i] = Integer.parseInt(parts[1].trim());
			}
			catch (NumberFormatException e)
			{
				return null;
			}
			if (ids[i] <= 0 || quantities[i] <= 0)
			{
				return null;
			}
		}
		return new StoredItems(ids, quantities);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof StoredItems))
		{
			return false;
		}
		StoredItems other = (StoredItems) o;
		return Arrays.equals(ids, other.ids) && Arrays.equals(quantities, other.quantities);
	}

	@Override
	public int hashCode()
	{
		return 31 * Arrays.hashCode(ids) + Arrays.hashCode(quantities);
	}
}
