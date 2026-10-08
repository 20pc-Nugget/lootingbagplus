/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * Layout and structure follow RuneLite's own Inventory Viewer overlay, Copyright (c) 2018,
 * Adam <Adam@sigterm.info> and RuneLite contributors, BSD 2-Clause. See LICENSE.
 */
package com.lootingbagplus.ui;

import com.lootingbagplus.BagState;
import com.lootingbagplus.LootingBagPlusConfig;
import com.lootingbagplus.model.BagGeometry;
import com.lootingbagplus.model.BagSnapshot;
import com.lootingbagplus.model.BagSnapshot.SlotView;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Constants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;

/**
 * The floating looting bag viewer: a movable panel drawn over the game that shows the bag in the
 * player's own arrangement, laid out like RuneLite's Inventory Viewer (4 columns by 7 rows).
 * Hold Alt and drag it to move it, like any other overlay.
 */
@Singleton
public class LootingBagOverlay extends OverlayPanel
{
	private static final ImageComponent PLACEHOLDER_IMAGE = new ImageComponent(
		new BufferedImage(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, BufferedImage.TYPE_4BYTE_ABGR));

	private final ItemManager itemManager;
	private final BagState state;
	private final BagWindowController controller;
	private final LootingBagPlusConfig config;

	@Inject
	public LootingBagOverlay(ItemManager itemManager, BagState state, BagWindowController controller, LootingBagPlusConfig config)
	{
		this.itemManager = itemManager;
		this.state = state;
		this.controller = controller;
		this.config = config;

		setPosition(OverlayPosition.BOTTOM_RIGHT);
		panelComponent.setWrap(true);
		panelComponent.setGap(new Point(6, 4));
		panelComponent.setPreferredSize(new Dimension(BagGeometry.COLS * (Constants.ITEM_SPRITE_WIDTH + 6), 0));
		panelComponent.setOrientation(ComponentOrientation.HORIZONTAL);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		if (config.hideWhenBagViewOpen() && controller.isViewOpen())
		{
			return null;
		}

		BagSnapshot snapshot = state.getSnapshot();
		if (!snapshot.isKnown())
		{
			// Never checked on this character: nothing honest to show yet.
			return null;
		}

		for (int slot = 0; slot < BagGeometry.SLOTS; slot++)
		{
			SlotView view = snapshot.slot(slot);
			if (view != null)
			{
				BufferedImage image = itemManager.getImage(view.getItemId(), view.getQuantity(), view.isStackable());
				if (image != null)
				{
					panelComponent.getChildren().add(new ImageComponent(image));
					continue;
				}
			}

			// A placeholder keeps every item aligned and the panel from resizing.
			panelComponent.getChildren().add(PLACEHOLDER_IMAGE);
		}

		return super.render(graphics);
	}
}
