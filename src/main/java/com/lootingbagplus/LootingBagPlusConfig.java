/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(LootingBagPlusConfig.GROUP)
public interface LootingBagPlusConfig extends Config
{
	String GROUP = "lootingbagplus";

	/**
	 * Read and written by the side panel's checkbox and the toggle hotkey as well as the config
	 * panel, so all three controls always agree.
	 */
	String KEY_SHOW_OVERLAY = "showOverlay";

	/** Keys that change what the side panel displays. */
	String KEY_SHOW_VALUE = "showValue";
	String KEY_VALUE_TYPE = "valueType";

	@ConfigSection(
		name = "Floating overlay",
		description = "The movable looting bag viewer drawn over the game",
		position = 0
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Value",
		description = "How the bag's total value is worked out",
		position = 1
	)
	String valueSection = "value";

	@ConfigItem(
		keyName = KEY_SHOW_OVERLAY,
		name = "Show overlay",
		description = "Show the floating looting bag viewer. Hold Alt and drag it to move it.",
		position = 0,
		section = overlaySection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "toggleKeybind",
		name = "Toggle keybind",
		description = "Binds a key (combination) to show or hide the floating overlay.",
		position = 1,
		section = overlaySection
	)
	default Keybind toggleKeybind()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "hideWhenBagViewOpen",
		name = "Hide while bag window is open",
		description = "Hide the floating overlay while the game's own looting bag window is open.",
		position = 2,
		section = overlaySection
	)
	default boolean hideWhenBagViewOpen()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHOW_VALUE,
		name = "Show total value",
		description = "Show the bag's total value in the side panel.",
		position = 0,
		section = valueSection
	)
	default boolean showValue()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_VALUE_TYPE,
		name = "Value type",
		description = "Price used for the side panel's total and item tooltips.",
		position = 1,
		section = valueSection
	)
	default PriceType valueType()
	{
		return PriceType.GRAND_EXCHANGE;
	}
}
