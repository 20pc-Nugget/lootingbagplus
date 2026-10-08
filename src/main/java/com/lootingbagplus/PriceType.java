/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus;

/** Which price the side panel uses for the bag's total value. */
public enum PriceType
{
	GRAND_EXCHANGE("Grand Exchange"),
	HIGH_ALCHEMY("High alchemy");

	private final String displayName;

	PriceType(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
