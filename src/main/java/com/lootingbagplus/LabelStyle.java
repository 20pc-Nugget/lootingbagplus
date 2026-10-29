/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus;

/** Which of Item Identification's two tag lengths to draw over herbs and seeds. */
public enum LabelStyle
{
	SHORT("Short"),
	MEDIUM("Medium");

	private final String displayName;

	LabelStyle(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
