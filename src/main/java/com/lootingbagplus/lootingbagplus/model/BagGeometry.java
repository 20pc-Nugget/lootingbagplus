/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * The grid constants below describe the game's own looting bag interface (clientscript 497) and
 * were first documented by Looting Bag Organizer, Copyright (c) 2026, robrichardson13,
 * BSD 2-Clause. See LICENSE.
 */
package com.lootingbagplus.model;

import java.awt.Rectangle;

/**
 * Pure grid arithmetic for the looting bag's item grid ({@code WildernessLootingbag.ITEMS}):
 * 4 columns by 7 rows, cells 36x32, filled top-left in slot order with an 8px gutter between
 * columns.
 *
 * <pre>
 * x(slot) = (slot % 4) * 44        y(slot) = (slot / 4) * 32
 * </pre>
 *
 * <p>Never hit-tests widget children: the game hides empty cells at 0x0, so every drop target
 * comes from this arithmetic on the canvas bounds of the grid layer instead.
 */
public final class BagGeometry
{
	public static final int COLS = 4;
	public static final int ROWS = 7;
	public static final int SLOTS = COLS * ROWS;
	public static final int CELL_W = 36;
	public static final int CELL_H = 32;
	public static final int PITCH_X = 44;
	public static final int PITCH_Y = 32;

	private BagGeometry()
	{
	}

	/** Grid-local x of a slot's cell. */
	public static int x(int slot)
	{
		return (slot % COLS) * PITCH_X;
	}

	/** Grid-local y of a slot's cell. */
	public static int y(int slot)
	{
		return (slot / COLS) * PITCH_Y;
	}

	/** Canvas rectangle of a slot's cell, given the canvas bounds of the grid layer. */
	public static Rectangle cell(int slot, Rectangle gridBounds)
	{
		return new Rectangle(gridBounds.x + x(slot), gridBounds.y + y(slot), CELL_W, CELL_H);
	}

	/**
	 * The slot whose pitch cell contains the canvas point, or {@code -1} if the point is outside
	 * the 4x7 pitch rectangle. A point in the gutter between columns belongs to the column on its
	 * left, because the divide is by pitch rather than by cell width.
	 */
	public static int slotAt(Rectangle gridBounds, int canvasX, int canvasY)
	{
		int relX = canvasX - gridBounds.x;
		int relY = canvasY - gridBounds.y;

		if (relX < 0 || relY < 0)
		{
			return -1;
		}

		int col = relX / PITCH_X;
		int row = relY / PITCH_Y;

		if (col >= COLS || row >= ROWS)
		{
			return -1;
		}

		return row * COLS + col;
	}
}
