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
	 * Read and written by the side panel's checkbox and the hotkeys as well as the config panel, so
	 * every control always agrees.
	 */
	String KEY_SHOW_OVERLAY = "showOverlay";
	String KEY_SHOW_SEED_BOX = "showSeedBoxOverlay";

	/** Keys that change what the side panel displays. */
	String KEY_SHOW_VALUE = "showValue";
	String KEY_VALUE_TYPE = "valueType";

	@ConfigSection(
		name = "Floating overlays",
		description = "The movable looting bag and seed box viewers drawn over the game",
		position = 0
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Value",
		description = "How values are worked out for the side panel and the overlay tooltips",
		position = 1
	)
	String valueSection = "value";

	@ConfigItem(
		keyName = KEY_SHOW_OVERLAY,
		name = "Show looting bag overlay",
		description = "Show the floating looting bag viewer. Hold Alt and drag it to move it.",
		position = 0,
		section = overlaySection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SHOW_SEED_BOX,
		name = "Show seed box overlay",
		description = "Show the floating seed box viewer. Hold Alt and drag it to move it.",
		position = 1,
		section = overlaySection
	)
	default boolean showSeedBoxOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hotkeyMode",
		name = "Hotkeys",
		description = "One hotkey that shows and hides both overlays together, or a separate hotkey for each.",
		position = 2,
		section = overlaySection
	)
	default HotkeyMode hotkeyMode()
	{
		return HotkeyMode.SAME_HOTKEY;
	}

	@ConfigItem(
		keyName = "toggleKeybind",
		name = "Toggle keybind",
		description = "With one hotkey for both: shows or hides both overlays. With separate hotkeys: shows or hides the looting bag overlay.",
		position = 3,
		section = overlaySection
	)
	default Keybind toggleKeybind()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "seedBoxToggleKeybind",
		name = "Seed box keybind",
		description = "Shows or hides the seed box overlay. Only used with separate hotkeys.",
		position = 4,
		section = overlaySection
	)
	default Keybind seedBoxToggleKeybind()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "hideWhenBagViewOpen",
		name = "Hide while bag window is open",
		description = "Hide the floating looting bag overlay while the game's own looting bag window is open.",
		position = 5,
		section = overlaySection
	)
	default boolean hideWhenBagViewOpen()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showItemLabels",
		name = "Show herb and seed labels",
		description = "Draw the Item Identification tag (for example R for Ranarr) over each herb and seed in the overlays. Other items get no label.",
		position = 6,
		section = overlaySection
	)
	default boolean showItemLabels()
	{
		return true;
	}

	@ConfigItem(
		keyName = "labelStyle",
		name = "Label style",
		description = "Short (R, HAR) or medium (Ranarr, Harra) tags, as in the Item Identification plugin.",
		position = 7,
		section = overlaySection
	)
	default LabelStyle labelStyle()
	{
		return LabelStyle.SHORT;
	}

	@ConfigItem(
		keyName = "showTooltip",
		name = "Show hover tooltip",
		description = "Show an item's full name, quantity and value when you hover over it in an overlay.",
		position = 8,
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
}
