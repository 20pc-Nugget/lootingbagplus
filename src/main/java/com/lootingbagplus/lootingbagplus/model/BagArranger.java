/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * The placement rules (pinned ids claim their slot first, everything else fills the lowest free
 * unreserved slot in container order) reproduce the behaviour of Looting Bag Organizer,
 * Copyright (c) 2026, robrichardson13, BSD 2-Clause. See LICENSE.
 */
package com.lootingbagplus.model;

import java.util.Arrays;

/**
 * Pure placement logic shared by the in-game bag window, the side panel and the floating overlay,
 * so all three always show the same arrangement.
 */
public final class BagArranger
{
	private static final int SLOTS = BagLayout.SLOTS;

	private BagArranger()
	{
	}

	/**
	 * Resolves each container slot to a display slot.
	 *
	 * <p>Pass 1 places pinned ids: for each layout slot in ascending order that pins an item id,
	 * the first unclaimed container copy of that id (ascending container index) claims it. Pass 2
	 * places every remaining container item, in ascending container index order, into the lowest
	 * display slot that is neither taken nor reserved by a pin still waiting for its item. If every
	 * remaining slot is reserved, the lowest reserved slot is used instead and that pin is dropped
	 * from {@code layout} ({@link Arrangement#isLayoutChanged()} reports it).
	 *
	 * <p>This mutates {@code layout} only in that last case.
	 */
	public static Arrangement arrange(BagLayout layout, BagContents contents)
	{
		int[] displaySlotOf = new int[SLOTS];
		int[] containerIndexAt = new int[SLOTS];
		Arrays.fill(displaySlotOf, -1);
		Arrays.fill(containerIndexAt, -1);

		boolean[] reserved = new boolean[SLOTS];
		boolean[] claimed = new boolean[SLOTS];
		boolean layoutChanged = false;

		for (int slot = 0; slot < SLOTS; slot++)
		{
			reserved[slot] = layout.isPinned(slot);
		}

		// Pass 1: pinned ids claim the first unclaimed container copy of their id.
		for (int slot = 0; slot < SLOTS; slot++)
		{
			if (!reserved[slot])
			{
				continue;
			}
			int pinnedId = layout.pinAt(slot);
			for (int i = 0; i < SLOTS; i++)
			{
				if (!claimed[i] && !contents.isEmpty(i) && contents.idAt(i) == pinnedId)
				{
					claimed[i] = true;
					displaySlotOf[i] = slot;
					containerIndexAt[slot] = i;
					break;
				}
			}
		}

		// Pass 2: everything else, in container order.
		for (int i = 0; i < SLOTS; i++)
		{
			if (contents.isEmpty(i) || claimed[i])
			{
				continue;
			}

			int target = firstFree(containerIndexAt, reserved, false);
			if (target == -1)
			{
				target = firstFree(containerIndexAt, reserved, true);
				if (target != -1)
				{
					layout.clearPin(target);
					reserved[target] = false;
					layoutChanged = true;
				}
			}

			if (target != -1)
			{
				claimed[i] = true;
				displaySlotOf[i] = target;
				containerIndexAt[target] = i;
			}
		}

		return new Arrangement(displaySlotOf, containerIndexAt, layoutChanged);
	}

	/**
	 * Applies a drag from {@code source} to {@code target} (both display slots) to {@code layout}.
	 *
	 * <p>A drag is always a deliberate placement. The first edit turns the bag from auto-arranged
	 * to player-arranged, so every currently displayed item is pinned to the slot it is showing in
	 * before the two slots are swapped. Without that, the slot the dragged item vacated would stay
	 * unreserved and the next {@link #arrange} would slide every later item forward to fill it.
	 *
	 * @return true if the layout changed; false for a drop outside the grid, a drop on the source
	 * slot, or a drag between two slots that are both genuinely empty
	 */
	public static boolean commitMove(BagLayout layout, Arrangement current, BagContents contents, int source, int target)
	{
		if (source < 0 || source >= SLOTS || target < 0 || target >= SLOTS || source == target)
		{
			return false;
		}

		if (isGenuinelyEmpty(layout, current, source) && isGenuinelyEmpty(layout, current, target))
		{
			return false;
		}

		for (int slot = 0; slot < SLOTS; slot++)
		{
			if (layout.isPinned(slot))
			{
				continue;
			}
			int containerIndex = current.containerIndexAt(slot);
			if (containerIndex >= 0)
			{
				layout.setPin(slot, contents.idAt(containerIndex));
			}
		}

		layout.swap(source, target);
		return true;
	}

	/** A slot with no pin and nothing currently displayed in it. */
	private static boolean isGenuinelyEmpty(BagLayout layout, Arrangement current, int slot)
	{
		return !layout.isPinned(slot) && current.containerIndexAt(slot) < 0;
	}

	private static int firstFree(int[] containerIndexAt, boolean[] reserved, boolean allowReserved)
	{
		for (int slot = 0; slot < SLOTS; slot++)
		{
			if (containerIndexAt[slot] != -1)
			{
				continue;
			}
			if (!allowReserved && reserved[slot])
			{
				continue;
			}
			return slot;
		}
		return -1;
	}
}
