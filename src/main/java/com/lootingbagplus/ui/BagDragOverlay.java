/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.ui;

import com.lootingbagplus.BagState;
import com.lootingbagplus.model.BagGeometry;
import com.lootingbagplus.model.BagSnapshot.SlotView;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The drag ghost: the dragged item's image following the cursor, plus an outline around the cell
 * it would land in. Draws nothing unless a drag is in progress. The source cell keeps rendering in
 * place while the ghost is shown.
 */
@Singleton
public class BagDragOverlay extends Overlay
{
	private static final float GHOST_ALPHA = 0.6f;
	private static final Color HIGHLIGHT_COLOR = new Color(255, 255, 255, 160);

	private final ItemManager itemManager;
	private final BagWindowController controller;
	private final BagState state;

	@Inject
	public BagDragOverlay(ItemManager itemManager, BagWindowController controller, BagState state)
	{
		this.itemManager = itemManager;
		this.controller = controller;
		this.state = state;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!controller.isViewOpen() || !controller.isDragging())
		{
			return null;
		}

		int hoverSlot = controller.getHoverSlot();
		if (hoverSlot >= 0)
		{
			Rectangle cell = BagGeometry.cell(hoverSlot, controller.getGridBounds());
			graphics.setColor(HIGHLIGHT_COLOR);
			graphics.drawRect(cell.x, cell.y, cell.width - 1, cell.height - 1);
		}

		Point cursor = controller.getDragPoint();
		int source = controller.getDragSource();
		if (cursor != null && source >= 0 && source < BagGeometry.SLOTS)
		{
			drawGhost(graphics, state.getSnapshot().slot(source), cursor);
		}

		return null;
	}

	/** Draws the dragged item's image centred on the cursor at reduced alpha. */
	private void drawGhost(Graphics2D graphics, SlotView view, Point cursor)
	{
		if (view == null)
		{
			return;
		}

		BufferedImage image = itemManager.getImage(view.getItemId(), view.getQuantity(), view.isStackable());
		if (image == null)
		{
			return;
		}

		Composite old = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, GHOST_ALPHA));
		graphics.drawImage(image, cursor.x - image.getWidth() / 2, cursor.y - image.getHeight() / 2, null);
		graphics.setComposite(old);
	}
}
