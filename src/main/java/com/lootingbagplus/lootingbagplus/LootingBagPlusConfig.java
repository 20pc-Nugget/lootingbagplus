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

	/** Switches the separate seed box overlay on and off. */
	String KEY_SHOW_SEED_BOX = "showSeedBoxOverlay";

	@ConfigSection(
		name = "Floating overlay",
		description = "The movable looting bag viewer drawn over the game",
		position = 0
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Value",
		description = "How values are worked out for the side panel and the overlay tooltip",
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
		keyName = "showItemLabels",
		name = "Show herb and seed labels",
		description = "Draw a short name (for example R for Ranarr, A for Avantoe) over each herb and seed in the overlays. Other items get no label.",
		position = 3,
		section = overlaySection
	)
	default boolean showItemLabels()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTooltip",
		name = "Show hover tooltip",
		description = "Show an item's full name, quantity and value when you hover over it in an overlay.",
		position = 4,
		section = overlaySection
	)
	default boolean showTooltip()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SHOW_VALUE,
		name = "Show total value",
		description = "Show the bag's total value in the side panel, and item values in tooltips.",
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
		description = "Price used for the side panel's total and the item tooltips.",
		position = 1,
		section = valueSection
	)
	default PriceType valueType()
	{
		return PriceType.GRAND_EXCHANGE;
	}

	@ConfigSection(
		name = "Seed box overlay",
		description = "A separate movable viewer for your seed box",
		position = 2
	)
	String seedBoxSection = "seedBox";

	@ConfigItem(
		keyName = KEY_SHOW_SEED_BOX,
		name = "Show seed box overlay",
		description = "Show a floating viewer for your seed box. Open or check the seed box once to sync it. Hold Alt and drag it to move it.",
		position = 0,
		section = seedBoxSection
	)
	default boolean showSeedBoxOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "seedBoxToggleKeybind",
		name = "Toggle keybind",
		description = "Binds a key (combination) to show or hide the seed box overlay.",
		position = 1,
		section = seedBoxSection
	)
	default Keybind seedBoxToggleKeybind()
	{
		return Keybind.NOT_SET;
	}
}
