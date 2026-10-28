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
import com.lootingbagplus.PriceType;
import com.lootingbagplus.model.BagGeometry;
import com.lootingbagplus.model.BagSnapshot;
import com.lootingbagplus.model.BagSnapshot.SlotView;
import com.lootingbagplus.model.ItemLabels;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * The floating looting bag viewer: a movable panel drawn over the game that shows the bag in the
 * player's own arrangement, laid out like RuneLite's Inventory Viewer (4 columns by 7 rows).
 * Hold Alt and drag it to move it, like any other overlay.
 *
 * <p>Herbs and seeds carry a short name label over their icon, and hovering any item shows a tooltip
 * with its full name, quantity and value.
 */
@Singleton
public class LootingBagOverlay extends OverlayPanel
{
	private static final ImageComponent PLACEHOLDER_IMAGE = new ImageComponent(
		new BufferedImage(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, BufferedImage.TYPE_4BYTE_ABGR));

	private final Client client;
	private final ItemManager itemManager;
	private final TooltipManager tooltipManager;
	private final BagState state;
	private final BagWindowController controller;
	private final LootingBagPlusConfig config;

	/** The cells drawn this frame by display slot, {@code null} where nothing item-like was drawn. */
	private final BagItemComponent[] cells = new BagItemComponent[BagGeometry.SLOTS];

	/** Labels depend only on the snapshot, so they are worked out once per snapshot, not per frame. */
	private BagSnapshot labelledSnapshot;
	private String[] labels = new String[BagGeometry.SLOTS];

	@Inject
	public LootingBagOverlay(Client client, ItemManager itemManager, TooltipManager tooltipManager, BagState state,
		BagWindowController controller, LootingBagPlusConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.tooltipManager = tooltipManager;
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

		String[] slotLabels = config.showItemLabels() ? labelsFor(snapshot) : null;

		for (int slot = 0; slot < BagGeometry.SLOTS; slot++)
		{
			cells[slot] = null;

			SlotView view = snapshot.slot(slot);
			if (view != null)
			{
				BufferedImage image = itemManager.getImage(view.getItemId(), view.getQuantity(), view.isStackable());
				if (image != null)
				{
					BagItemComponent cell = new BagItemComponent(image, slotLabels == null ? null : slotLabels[slot]);
					cells[slot] = cell;
					panelComponent.getChildren().add(cell);
					continue;
				}
			}

			// A placeholder keeps every item aligned and the panel from resizing.
			panelComponent.getChildren().add(PLACEHOLDER_IMAGE);
		}

		Dimension size = super.render(graphics);
		showHoverTooltip(snapshot);
		return size;
	}

	private String[] labelsFor(BagSnapshot snapshot)
	{
		if (snapshot != labelledSnapshot)
		{
			String[] names = new String[BagGeometry.SLOTS];
			for (int slot = 0; slot < BagGeometry.SLOTS; slot++)
			{
				SlotView view = snapshot.slot(slot);
				names[slot] = view == null ? null : view.getName();
			}
			labels = ItemLabels.labelsFor(names);
			labelledSnapshot = snapshot;
		}
		return labels;
	}

	/**
	 * Shows the full name, quantity and value of the item under the mouse. Called after the panel
	 * has rendered, so every cell's bounds are from this frame; the overlay's own bounds already
	 * hold its on-screen position for this frame, which turns them into canvas coordinates.
	 */
	private void showHoverTooltip(BagSnapshot snapshot)
	{
		if (!config.showTooltip() || client.isMenuOpen())
		{
			return;
		}

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return;
		}

		Point origin = getBounds().getLocation();
		for (int slot = 0; slot < BagGeometry.SLOTS; slot++)
		{
			BagItemComponent cell = cells[slot];
			SlotView view = snapshot.slot(slot);
			if (cell == null || view == null)
			{
				continue;
			}

			Rectangle local = cell.getBounds();
			Rectangle onCanvas = new Rectangle(origin.x + local.x, origin.y + local.y, local.width, local.height);
			if (onCanvas.contains(mouse.getX(), mouse.getY()))
			{
				tooltipManager.add(new Tooltip(tooltipText(view)));
				return;
			}
		}
	}

	private String tooltipText(SlotView view)
	{
		StringBuilder sb = new StringBuilder(view.getName());
		if (view.getQuantity() > 1)
		{
			sb.append(" x ").append(QuantityFormatter.formatNumber(view.getQuantity()));
		}

		if (config.showValue())
		{
			long unit = config.valueType() == PriceType.HIGH_ALCHEMY ? view.getHaPrice() : view.getGePrice();
			sb.append("</br>").append(QuantityFormatter.formatNumber(unit * view.getQuantity())).append(" gp");
		}
		return sb.toString();
	}
}
