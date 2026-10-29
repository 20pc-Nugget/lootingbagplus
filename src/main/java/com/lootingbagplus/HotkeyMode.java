/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus;

/** Whether one key shows and hides both overlays together, or each overlay has its own key. */
public enum HotkeyMode
{
	SAME_HOTKEY("One hotkey for both"),
	SEPARATE_HOTKEYS("Separate hotkeys");

	private final String displayName;

	HotkeyMode(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
