/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * The slot-pinning model (a sparse list of item ids reserved per display slot) is modelled on
 * the approach of Looting Bag Organizer, Copyright (c) 2026, robrichardson13, BSD 2-Clause.
 * See LICENSE.
 */
package com.lootingbagplus.model;

import java.util.Arrays;

/**
 * The player's chosen arrangement of the looting bag: which item id is pinned to which of the 28
 * display slots. A pin reserves a slot for an item id; slots with no pin are filled automatically
 * (see {@link BagArranger}).
 *
 * <p>Pure data with no RuneLite imports. Persisted as a short comma separated string by
 * {@code BagState}.
 */
public final class BagLayout
{
	public static final int SLOTS = 28;

	/** Marker for "no pin" in the backing array. */
	private static final int FREE = -1;

	private final int[] pins = new int[SLOTS];

	public BagLayout()
	{
		Arrays.fill(pins, FREE);
	}

	/**
	 * @return the item id pinned to {@code slot}, or {@code -1} if the slot has no pin
	 */
	public int pinAt(int slot)
	{
		checkSlot(slot);
		return pins[slot];
	}

	public boolean isPinned(int slot)
	{
		return pinAt(slot) != FREE;
	}

	/**
	 * Pins {@code itemId} to {@code slot}. An id that is not positive frees the slot instead.
	 */
	public void setPin(int slot, int itemId)
	{
		checkSlot(slot);
		pins[slot] = itemId > 0 ? itemId : FREE;
	}

	public void clearPin(int slot)
	{
		checkSlot(slot);
		pins[slot] = FREE;
	}

	/**
	 * Swaps the pins of two slots. Moving an item into an empty slot is a swap with a free pin.
	 */
	public void swap(int a, int b)
	{
		checkSlot(a);
		checkSlot(b);
		int tmp = pins[a];
		pins[a] = pins[b];
		pins[b] = tmp;
	}

	public boolean isEmpty()
	{
		for (int pin : pins)
		{
			if (pin != FREE)
			{
				return false;
			}
		}
		return true;
	}

	public void clear()
	{
		Arrays.fill(pins, FREE);
	}

	public BagLayout copy()
	{
		BagLayout copy = new BagLayout();
		System.arraycopy(pins, 0, copy.pins, 0, SLOTS);
		return copy;
	}

	/**
	 * Compact text form: item ids separated by commas, an empty token for a free slot, trailing
	 * free slots omitted. An all-free layout encodes to the empty string.
	 */
	public String encode()
	{
		int last = SLOTS - 1;
		while (last >= 0 && pins[last] == FREE)
		{
			last--;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i <= last; i++)
		{
			if (i > 0)
			{
				sb.append(',');
			}
			if (pins[i] != FREE)
			{
				sb.append(pins[i]);
			}
		}
		return sb.toString();
	}

	/**
	 * Inverse of {@link #encode()}. Anything unreadable (null, garbage, too many tokens) is
	 * repaired rather than thrown: unparseable tokens become free slots and extra tokens are
	 * dropped, so a damaged config value can never break the render loop.
	 */
	public static BagLayout decode(String text)
	{
		BagLayout layout = new BagLayout();
		if (text == null || text.isEmpty())
		{
			return layout;
		}

		String[] tokens = text.split(",", -1);
		for (int i = 0; i < tokens.length && i < SLOTS; i++)
		{
			String token = tokens[i].trim();
			if (token.isEmpty())
			{
				continue;
			}
			try
			{
				layout.setPin(i, Integer.parseInt(token));
			}
			catch (NumberFormatException e)
			{
				// leave the slot free
			}
		}
		return layout;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof BagLayout && Arrays.equals(pins, ((BagLayout) o).pins);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(pins);
	}

	@Override
	public String toString()
	{
		return "BagLayout[" + encode() + "]";
	}

	private static void checkSlot(int slot)
	{
		if (slot < 0 || slot >= SLOTS)
		{
			throw new IndexOutOfBoundsException("slot " + slot + " out of range [0, " + SLOTS + ")");
		}
	}
}
