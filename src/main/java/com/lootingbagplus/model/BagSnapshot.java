/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.model;

/**
 * An immutable, display-ready picture of the looting bag in the player's arrangement. Built on the
 * client thread and handed to the side panel (Swing thread) and the floating overlay, so neither
 * ever has to touch the client to draw. Slot {@code n} is the item drawn in display slot
 * {@code n}, or {@code null} when that slot is empty.
 */
public final class BagSnapshot
{
	/** Nothing known yet: the bag has never been checked on this character. */
	public static final BagSnapshot UNKNOWN = new BagSnapshot(false, new SlotView[BagLayout.SLOTS], 0, 0, 0);

	private final boolean known;
	private final SlotView[] slots;
	private final int usedSlots;
	private final long geValue;
	private final long haValue;

	public BagSnapshot(boolean known, SlotView[] slots, int usedSlots, long geValue, long haValue)
	{
		this.known = known;
		this.slots = slots.clone();
		this.usedSlots = usedSlots;
		this.geValue = geValue;
		this.haValue = haValue;
	}

	/** False until the bag has been checked at least once on this character. */
	public boolean isKnown()
	{
		return known;
	}

	/** @return the item shown in {@code displaySlot}, or {@code null} if it is empty */
	public SlotView slot(int displaySlot)
	{
		return slots[displaySlot];
	}

	public int getUsedSlots()
	{
		return usedSlots;
	}

	public long getGeValue()
	{
		return geValue;
	}

	public long getHaValue()
	{
		return haValue;
	}

	/** One occupied display slot. */
	public static final class SlotView
	{
		private final int itemId;
		private final int quantity;
		private final String name;
		private final boolean stackable;
		private final long gePrice;
		private final long haPrice;

		public SlotView(int itemId, int quantity, String name, boolean stackable, long gePrice, long haPrice)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.name = name;
			this.stackable = stackable;
			this.gePrice = gePrice;
			this.haPrice = haPrice;
		}

		public int getItemId()
		{
			return itemId;
		}

		public int getQuantity()
		{
			return quantity;
		}

		public String getName()
		{
			return name;
		}

		public boolean isStackable()
		{
			return stackable;
		}

		/** GE price of one item. */
		public long getGePrice()
		{
			return gePrice;
		}

		/** High alchemy value of one item. */
		public long getHaPrice()
		{
			return haPrice;
		}
	}
}
