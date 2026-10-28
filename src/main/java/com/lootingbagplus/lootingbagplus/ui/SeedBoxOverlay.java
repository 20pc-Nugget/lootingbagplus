/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * Layout and structure follow RuneLite's own Inventory Viewer overlay, Copyright (c) 2018,
 * Adam <Adam@sigterm.info> and RuneLite contributors, BSD 2-Clause. See LICENSE.
 */
package com.lootingbagplus.ui;

import com.lootingbagplus.LootingBagPlusConfig;
import com.lootingbagplus.PriceType;
import com.lootingbagplus.SeedBoxState;
import com.lootingbagplus.model.BagGeometry;
import com.lootingbagplus.model.BagSnapshot.SlotView;
import com.lootingbagplus.model.ItemLabels;
import com.lootingbagplus.model.StoredItemsSnapshot;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
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
 * The floating seed box viewer: a movable panel like the looting bag viewer, showing the seeds the
 * box last held, four per row, with a short name label over each seed and a hover tooltip (name,
 * quantity, value). Switched on and off
 * separately from the looting bag overlay. Hold Alt and drag it to move it.
 */
@Singleton
public class SeedBoxOverlay extends OverlayPanel
{
	private static final ImageComponent PLACEHOLDER_IMAGE = new ImageComponent(
		new BufferedImage(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, BufferedImage.TYPE_4BYTE_ABGR));

	private final Client client;
	private final ItemManager itemManager;
	private final TooltipManager tooltipManager;
	private final SeedBoxState state;
	private final LootingBagPlusConfig config;

	/** The cells drawn this frame, in item order; {@code null} where only a placeholder was drawn. */
	private final List<BagItemComponent> cells = new ArrayList<>();

	/** Labels depend only on the snapshot, so they are worked out once per snapshot, not per frame. */
	private StoredItemsSnapshot labelledSnapshot;
	private String[] labels = new String[0];

	@Inject
	public SeedBoxOverlay(Client client, ItemManager itemManager, TooltipManager tooltipManager, SeedBoxState state,
		LootingBagPlusConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.tooltipManager = tooltipManager;
		this.state = state;
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
		if (!config.showSeedBoxOverlay())
		{
			return null;
		}

		StoredItemsSnapshot snapshot = state.getSnapshot();
		if (!snapshot.isKnown() || snapshot.getItems().isEmpty())
		{
			// Never seen on this character, or empty: nothing to show.
			return null;
		}

		String[] itemLabels = config.showItemLabels() ? labelsFor(snapshot) : null;

		cells.clear();
		List<SlotView> items = snapshot.getItems();
		for (int i = 0; i < items.size(); i++)
		{
			SlotView view = items.get(i);
			BufferedImage image = itemManager.getImage(view.getItemId(), view.getQuantity(), view.isStackable());
			if (image != null)
			{
				BagItemComponent cell = new BagItemComponent(image, itemLabels == null ? null : itemLabels[i]);
				cells.add(cell);
				panelComponent.getChildren().add(cell);
			}
			else
			{
				cells.add(null);
				panelComponent.getChildren().add(PLACEHOLDER_IMAGE);
			}
		}

		Dimension size = super.render(graphics);
		showHoverTooltip(snapshot);
		return size;
	}

	private String[] labelsFor(StoredItemsSnapshot snapshot)
	{
		if (snapshot != labelledSnapshot)
		{
			List<SlotView> items = snapshot.getItems();
			String[] names = new String[items.size()];
			for (int i = 0; i < names.length; i++)
			{
				names[i] = items.get(i).getName();
			}
			labels = ItemLabels.labelsFor(names);
			labelledSnapshot = snapshot;
		}
		return labels;
	}

	/**
	 * Shows the name, quantity and value of the item under the mouse. Called after the panel has
	 * rendered, so every cell's bounds are from this frame, and the overlay's own bounds already hold
	 * its on-screen position, which turns them into canvas coordinates.
	 */
	private void showHoverTooltip(StoredItemsSnapshot snapshot)
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
		List<SlotView> items = snapshot.getItems();
		for (int i = 0; i < cells.size() && i < items.size(); i++)
		{
			BagItemComponent cell = cells.get(i);
			if (cell == null)
			{
				continue;
			}

			Rectangle local = cell.getBounds();
			Rectangle onCanvas = new Rectangle(origin.x + local.x, origin.y + local.y, local.width, local.height);
			if (onCanvas.contains(mouse.getX(), mouse.getY()))
			{
				tooltipManager.add(new Tooltip(tooltipText(items.get(i))));
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
