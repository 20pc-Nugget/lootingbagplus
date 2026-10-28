/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.model;

import java.util.Arrays;

/**
 * The result of laying a {@link BagContents} out on a {@link BagLayout}: which display slot each
 * container item lands in, and the inverse. The non {@code -1} entries are a permutation, so no
 * display slot is used twice.
 */
public final class Arrangement
{
	private final int[] displaySlotOf;
	private final int[] containerIndexAt;
	private final boolean layoutChanged;

	Arrangement(int[] displaySlotOf, int[] containerIndexAt, boolean layoutChanged)
	{
		this.displaySlotOf = displaySlotOf.clone();
		this.containerIndexAt = containerIndexAt.clone();
		this.layoutChanged = layoutChanged;
	}

	/**
	 * @return the display slot the item at container index {@code containerIndex} is drawn in, or
	 * {@code -1} for an empty container slot
	 */
	public int displaySlotOf(int containerIndex)
	{
		return displaySlotOf[containerIndex];
	}

	/**
	 * @return the container index of the item drawn in {@code displaySlot}, or {@code -1} if that
	 * display slot is empty
	 */
	public int containerIndexAt(int displaySlot)
	{
		return containerIndexAt[displaySlot];
	}

	/**
	 * @return true if arranging had to drop a stale reservation from the layout to fit every item
	 * in, so the layout needs saving
	 */
	public boolean isLayoutChanged()
	{
		return layoutChanged;
	}

	@Override
	public String toString()
	{
		return "Arrangement" + Arrays.toString(displaySlotOf);
	}
}
