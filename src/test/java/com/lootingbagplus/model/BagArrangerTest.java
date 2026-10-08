/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BagArrangerTest
{
	/** Builds contents from alternating id, quantity pairs placed at container slots 0, 1, 2... */
	private static BagContents bag(int... idQtyPairs)
	{
		int[] ids = new int[BagContents.SLOTS];
		int[] qty = new int[BagContents.SLOTS];
		for (int i = 0; i < ids.length; i++)
		{
			ids[i] = -1;
		}
		for (int i = 0; i * 2 < idQtyPairs.length; i++)
		{
			ids[i] = idQtyPairs[i * 2];
			qty[i] = idQtyPairs[i * 2 + 1];
		}
		return BagContents.of(ids, qty);
	}

	@Test
	public void noPinsKeepsContainerOrder()
	{
		Arrangement a = BagArranger.arrange(new BagLayout(), bag(10, 1, 20, 1, 30, 1));
		assertEquals(0, a.displaySlotOf(0));
		assertEquals(1, a.displaySlotOf(1));
		assertEquals(2, a.displaySlotOf(2));
		assertEquals(-1, a.displaySlotOf(3));
		assertEquals(2, a.containerIndexAt(2));
		assertEquals(-1, a.containerIndexAt(3));
		assertFalse(a.isLayoutChanged());
	}

	@Test
	public void pinnedItemClaimsItsSlotAndOthersFillAround()
	{
		BagLayout layout = new BagLayout();
		layout.setPin(5, 10);
		Arrangement a = BagArranger.arrange(layout, bag(10, 1, 20, 1));
		assertEquals(5, a.displaySlotOf(0));
		assertEquals(0, a.displaySlotOf(1));
	}

	@Test
	public void newItemsAvoidSlotsReservedForAbsentItems()
	{
		BagLayout layout = new BagLayout();
		layout.setPin(0, 999);
		layout.setPin(1, 998);
		Arrangement a = BagArranger.arrange(layout, bag(20, 1));
		assertEquals(2, a.displaySlotOf(0));
		assertEquals(999, layout.pinAt(0));
		assertEquals(998, layout.pinAt(1));
		assertFalse(a.isLayoutChanged());
	}

	@Test
	public void fullBagDropsAStaleReservationToFitEveryItem()
	{
		int[] ids = new int[BagContents.SLOTS];
		int[] qty = new int[BagContents.SLOTS];
		for (int i = 0; i < ids.length; i++)
		{
			ids[i] = 100 + i;
			qty[i] = 1;
		}
		BagLayout layout = new BagLayout();
		layout.setPin(27, 999);

		Arrangement a = BagArranger.arrange(layout, BagContents.of(ids, qty));

		assertTrue(a.isLayoutChanged());
		assertFalse(layout.isPinned(27));
		for (int i = 0; i < BagContents.SLOTS; i++)
		{
			assertTrue("item " + i + " was left unplaced", a.displaySlotOf(i) >= 0);
		}
	}

	@Test
	public void duplicateIdsClaimPinsInSlotOrder()
	{
		BagLayout layout = new BagLayout();
		layout.setPin(3, 10);
		layout.setPin(7, 10);
		Arrangement a = BagArranger.arrange(layout, bag(10, 1, 10, 1));
		assertEquals(3, a.displaySlotOf(0));
		assertEquals(7, a.displaySlotOf(1));
	}

	@Test
	public void firstDragFreezesTheDisplayedArrangementSoNothingSlidesForward()
	{
		BagLayout layout = new BagLayout();
		BagContents contents = bag(10, 1, 20, 1, 30, 1);
		Arrangement before = BagArranger.arrange(layout, contents);

		assertTrue(BagArranger.commitMove(layout, before, contents, 0, 10));

		Arrangement after = BagArranger.arrange(layout, contents);
		assertEquals(10, after.displaySlotOf(0));
		assertEquals(1, after.displaySlotOf(1));
		assertEquals(2, after.displaySlotOf(2));
		assertEquals(-1, after.containerIndexAt(0));
	}

	@Test
	public void dropOnAnOccupiedSlotSwapsTheTwoItems()
	{
		BagLayout layout = new BagLayout();
		BagContents contents = bag(10, 1, 20, 1, 30, 1);
		Arrangement before = BagArranger.arrange(layout, contents);

		assertTrue(BagArranger.commitMove(layout, before, contents, 0, 2));

		Arrangement after = BagArranger.arrange(layout, contents);
		assertEquals(2, after.displaySlotOf(0));
		assertEquals(1, after.displaySlotOf(1));
		assertEquals(0, after.displaySlotOf(2));
	}

	@Test
	public void arrangementSurvivesANewItemBeingAdded()
	{
		BagLayout layout = new BagLayout();
		BagContents contents = bag(10, 1, 20, 1);
		BagArranger.commitMove(layout, BagArranger.arrange(layout, contents), contents, 0, 9);

		BagContents more = bag(10, 1, 20, 1, 30, 1);
		Arrangement a = BagArranger.arrange(layout, more);

		assertEquals(9, a.displaySlotOf(0));
		assertEquals(1, a.displaySlotOf(1));
		assertEquals(0, a.displaySlotOf(2));
	}

	@Test
	public void anItemReturnsToItsPinnedSlotAfterLeavingAndComingBack()
	{
		BagLayout layout = new BagLayout();
		BagContents contents = bag(10, 1, 20, 1);
		BagArranger.commitMove(layout, BagArranger.arrange(layout, contents), contents, 1, 12);

		Arrangement without = BagArranger.arrange(layout, bag(10, 1));
		assertEquals(0, without.displaySlotOf(0));

		Arrangement back = BagArranger.arrange(layout, bag(10, 1, 20, 1));
		assertEquals(12, back.displaySlotOf(1));
	}

	@Test
	public void noOpDragsChangeNothing()
	{
		BagLayout layout = new BagLayout();
		BagContents contents = bag(10, 1);
		Arrangement a = BagArranger.arrange(layout, contents);

		assertFalse(BagArranger.commitMove(layout, a, contents, 0, 0));
		assertFalse(BagArranger.commitMove(layout, a, contents, 0, -1));
		assertFalse(BagArranger.commitMove(layout, a, contents, 5, 6));
		assertTrue(layout.isEmpty());
	}

	@Test
	public void layoutEncodingRoundTrips()
	{
		BagLayout layout = new BagLayout();
		layout.setPin(0, 10);
		layout.setPin(3, 4151);
		layout.setPin(9, 995);
		assertEquals("10,,,4151,,,,,,995", layout.encode());
		assertEquals(layout, BagLayout.decode(layout.encode()));
		assertEquals("", new BagLayout().encode());
	}

	@Test
	public void damagedLayoutTextIsRepairedNotThrown()
	{
		assertTrue(BagLayout.decode(null).isEmpty());
		assertTrue(BagLayout.decode("").isEmpty());
		BagLayout layout = BagLayout.decode("10,abc,-5,30,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,99");
		assertEquals(10, layout.pinAt(0));
		assertFalse(layout.isPinned(1));
		assertFalse(layout.isPinned(2));
		assertEquals(30, layout.pinAt(3));
	}

	@Test
	public void contentsEncodingRoundTripsAndTellsEmptyFromUnknown()
	{
		BagContents contents = bag(995, 1500000, 4151, 1);
		assertEquals(contents, BagContents.decode(contents.encode()));
		assertEquals(2, contents.usedSlots());

		BagContents empty = BagContents.decode(BagContents.empty().encode());
		assertEquals(0, empty.usedSlots());

		assertEquals(null, BagContents.decode(null));
		assertEquals(null, BagContents.decode(""));
		assertEquals(null, BagContents.decode("garbage"));
		assertEquals(null, BagContents.decode("99:10:1"));
	}

	@Test
	public void geometryMapsPointsToSlots()
	{
		java.awt.Rectangle grid = new java.awt.Rectangle(100, 200, 172, 228);
		assertEquals(0, BagGeometry.slotAt(grid, 100, 200));
		assertEquals(1, BagGeometry.slotAt(grid, 100 + 44, 200));
		assertEquals(0, BagGeometry.slotAt(grid, 100 + 40, 200));
		assertEquals(4, BagGeometry.slotAt(grid, 100, 200 + 32));
		assertEquals(27, BagGeometry.slotAt(grid, 100 + 3 * 44, 200 + 6 * 32));
		assertEquals(-1, BagGeometry.slotAt(grid, 99, 200));
		assertEquals(-1, BagGeometry.slotAt(grid, 100 + 4 * 44, 200));
		assertEquals(-1, BagGeometry.slotAt(grid, 100, 200 + 7 * 32));
	}
}
