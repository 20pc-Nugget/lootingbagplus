/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * One item cell of the floating overlay: the item sprite (which already carries the game's own
 * quantity text in its top-left corner) with an optional short name label over its bottom-left
 * corner (an Item Identification tag). Remembers where it was drawn so the overlay can tell which cell the mouse is over.
 */
class BagItemComponent implements LayoutableRenderableEntity
{
	private static final Color LABEL_COLOR = Color.WHITE;
	private static final Color LABEL_SHADOW = Color.BLACK;

	private final BufferedImage image;
	private final String label;
	private final Rectangle bounds = new Rectangle();
	private Point preferredLocation = new Point();

	/**
	 * @param label short text drawn over the bottom-left of the sprite, or {@code null} for none
	 */
	BagItemComponent(BufferedImage image, String label)
	{
		this.image = image;
		this.label = label;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		graphics.drawImage(image, preferredLocation.x, preferredLocation.y, null);
		Dimension size = new Dimension(image.getWidth(), image.getHeight());

		if (label != null && !label.isEmpty())
		{
			drawLabel(graphics, size);
		}

		bounds.setLocation(preferredLocation);
		bounds.setSize(size);
		return size;
	}

	private void drawLabel(Graphics2D graphics, Dimension size)
	{
		Font oldFont = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeSmallFont());

		String text = label;
		// Same spot as the Item Identification plugin: bottom-left of the cell.
		int x = preferredLocation.x - 1;
		int y = preferredLocation.y + size.height - 1;
		graphics.setColor(LABEL_SHADOW);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(LABEL_COLOR);
		graphics.drawString(text, x, y);

		graphics.setFont(oldFont);
	}

	/** Where this cell was last drawn, relative to the overlay's top-left corner. */
	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point position)
	{
		this.preferredLocation = position;
	}

	@Override
	public void setPreferredSize(Dimension dimension)
	{
		// The sprite's own size is used.
	}
}
