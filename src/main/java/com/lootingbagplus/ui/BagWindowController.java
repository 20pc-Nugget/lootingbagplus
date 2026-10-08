/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * The technique of re-positioning the dynamic children of the looting bag grid after the game
 * rebuilds it (clientscript 497) was first worked out by Looting Bag Organizer, Copyright (c)
 * 2026, robrichardson13, BSD 2-Clause. See LICENSE.
 */
package com.lootingbagplus.ui;

import com.lootingbagplus.BagState;
import com.lootingbagplus.model.Arrangement;
import com.lootingbagplus.model.BagGeometry;
import com.lootingbagplus.model.BagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;

/**
 * Owns the game's looting bag "Check" window on the client thread: re-applies the player's
 * arrangement to it and runs drag and drop.
 *
 * <p>The game builds the item grid with one dynamic child per container slot, then rebuilds it
 * from scratch (resetting every position) whenever the container is transmitted. So after every
 * rebuild this class reads container 516, asks {@link BagState} where each item belongs and
 * re-positions the existing children with {@code setOriginalX/Y} + {@code revalidate()}. No
 * widget is created, no item is moved in the container and no game action is sent: the
 * arrangement is purely cosmetic.
 *
 * <p>Threading: everything here is client thread only, except what is published through
 * {@code volatile} fields for the AWT input listener ({@link #isViewOpen()},
 * {@link #getGridBounds()}) and {@link #post(Runnable)}, which hands work from the AWT thread to
 * the client thread.
 */
@Slf4j
@Singleton
public class BagWindowController
{
	/** The "Add to bag" deposit flow is the same interface; only its title tells them apart. */
	private static final String DEPOSIT_TITLE = "add to bag";

	/** The game's own title, restored on shutdown so a disabled plugin leaves no trace. */
	private static final String BASE_TITLE = "Looting bag";

	private static final int SLOTS = BagLayout.SLOTS;
	private static final Rectangle NO_BOUNDS = new Rectangle();

	private final Client client;
	private final BagState state;
	private final ConcurrentLinkedQueue<Runnable> actions = new ConcurrentLinkedQueue<>();

	/** The position the game gave each cell, kept per widget instance so shutdown can undo us. */
	private final Widget[] capturedChildren = new Widget[SLOTS];
	private final int[] capturedX = new int[SLOTS];
	private final int[] capturedY = new int[SLOTS];

	private boolean started;
	private boolean dirty;

	private boolean dragging;
	private int dragSource = -1;
	private Point dragPoint;
	private int hoverSlot = -1;

	private volatile boolean viewOpen;
	private volatile Rectangle gridBounds = NO_BOUNDS;

	@Inject
	public BagWindowController(Client client, BagState state)
	{
		this.client = client;
		this.state = state;
	}

	// ---- lifecycle ----------------------------------------------------------------------------

	/** Client thread. */
	public void startUp()
	{
		started = true;
		dirty = true;
	}

	/** Client thread. Puts the game's own cell order and title back. */
	public void shutDown()
	{
		started = false;
		actions.clear();
		restoreGameOrder();
	}

	/** Ask for the arrangement to be re-applied on the next frame. Cheap and idempotent. */
	public void markDirty()
	{
		dirty = true;
	}

	// ---- action queue -------------------------------------------------------------------------

	/** Any thread (in practice AWT). Runs on the client thread at the top of the next frame. */
	public void post(Runnable action)
	{
		if (action != null)
		{
			actions.add(action);
		}
	}

	private void drainActions()
	{
		Runnable action;
		while ((action = actions.poll()) != null)
		{
			try
			{
				action.run();
			}
			catch (RuntimeException e)
			{
				// A failing input action must never break the frame, or it would recur forever.
				log.warn("Looting bag action failed", e);
			}
		}
	}

	// ---- the client-thread pass ---------------------------------------------------------------

	/** Client thread, once per frame from {@code ClientTick}. */
	public void tick()
	{
		drainActions();

		if (!started)
		{
			return;
		}

		Widget items = client.getWidget(InterfaceID.WildernessLootingbag.ITEMS);
		boolean open = items != null && !items.isHidden() && !isDepositFlow();

		if (open)
		{
			gridBounds = boundsOf(items);
			if (dirty && applyLayout(items))
			{
				// Only a pass that actually re-positioned something consumes the flag. One that
				// bailed out (container or children not ready yet) has to be retried.
				dirty = false;
			}
		}
		else
		{
			gridBounds = NO_BOUNDS;
			cancelDrag();
		}

		viewOpen = open;
	}

	/** True while the Check window (not the "Add to bag" deposit flow) is on screen. */
	public boolean isViewOpen()
	{
		return viewOpen;
	}

	/** Canvas bounds of the item grid, or an empty rectangle while the window is closed. */
	public Rectangle getGridBounds()
	{
		return new Rectangle(gridBounds);
	}

	private boolean applyLayout(Widget items)
	{
		ItemContainer container = client.getItemContainer(InventoryID.LOOTING_BAG);
		if (container == null)
		{
			return false;
		}

		Widget[] children = items.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return false;
		}

		state.updateContents(BagState.contentsOf(container));
		Arrangement arrangement = state.getArrangement();
		if (arrangement == null)
		{
			return false;
		}

		for (int i = 0; i < children.length && i < SLOTS; i++)
		{
			Widget child = children[i];
			if (child == null)
			{
				continue;
			}

			captureGameOrder(i, child);

			if (child.isSelfHidden())
			{
				// An empty cell: hidden at 0x0 by the game. Left exactly as it is.
				continue;
			}

			int slot = arrangement.displaySlotOf(i);
			if (slot < 0 || slot >= SLOTS)
			{
				continue;
			}

			child.setOriginalX(BagGeometry.x(slot));
			child.setOriginalY(BagGeometry.y(slot));
			child.revalidate();
		}

		setTitle(BASE_TITLE + " (" + state.getSnapshot().getUsedSlots() + "/" + SLOTS + ")");
		return true;
	}

	/**
	 * Client thread. Puts every visible cell back where the game had it: the position captured
	 * before we touched it, or the game's own arithmetic when there is no capture. Safe when the
	 * window is already gone.
	 */
	private void restoreGameOrder()
	{
		viewOpen = false;
		gridBounds = NO_BOUNDS;
		cancelDrag();

		Widget items = client.getWidget(InterfaceID.WildernessLootingbag.ITEMS);
		Widget[] children = items == null ? null : items.getDynamicChildren();
		if (children != null)
		{
			for (int i = 0; i < children.length && i < SLOTS; i++)
			{
				Widget child = children[i];
				if (child == null || child.isSelfHidden())
				{
					continue;
				}

				boolean captured = capturedChildren[i] == child;
				child.setOriginalX(captured ? capturedX[i] : BagGeometry.x(i));
				child.setOriginalY(captured ? capturedY[i] : BagGeometry.y(i));
				child.revalidate();
			}
		}

		for (int i = 0; i < SLOTS; i++)
		{
			capturedChildren[i] = null;
		}

		Widget title = client.getWidget(InterfaceID.WildernessLootingbag.TITLE);
		String text = title == null ? null : title.getText();
		if (text != null && !text.toLowerCase().contains(DEPOSIT_TITLE))
		{
			// Never relabel the deposit dialog: we did not write that title.
			title.setText(BASE_TITLE);
		}
	}

	private void captureGameOrder(int index, Widget child)
	{
		if (capturedChildren[index] == child)
		{
			return;
		}

		capturedChildren[index] = child;
		capturedX[index] = child.getOriginalX();
		capturedY[index] = child.getOriginalY();
	}

	private boolean isDepositFlow()
	{
		Widget title = client.getWidget(InterfaceID.WildernessLootingbag.TITLE);
		String text = title == null ? null : title.getText();
		return text != null && text.toLowerCase().contains(DEPOSIT_TITLE);
	}

	private void setTitle(String text)
	{
		Widget title = client.getWidget(InterfaceID.WildernessLootingbag.TITLE);
		if (title != null)
		{
			title.setText(text);
		}
	}

	private static Rectangle boundsOf(Widget widget)
	{
		Rectangle bounds = widget.getBounds();
		return bounds == null ? NO_BOUNDS : bounds;
	}

	// ---- drag and drop (client thread) --------------------------------------------------------

	/** Starts a drag from a display slot. The source cell keeps rendering in place. */
	public void beginDrag(int sourceSlot)
	{
		dragging = true;
		dragSource = sourceSlot;
		dragPoint = null;
		hoverSlot = sourceSlot;
	}

	/** Tracks the cursor (game canvas coordinates) while dragging. */
	public void updateDrag(Point canvas)
	{
		if (!dragging)
		{
			return;
		}

		dragPoint = canvas;
		Rectangle bounds = gridBounds;
		hoverSlot = BagGeometry.slotAt(bounds, canvas.x, canvas.y);
	}

	/**
	 * Resolves a drop. {@code targetSlot == -1} (outside the grid) or the source slot cancels;
	 * otherwise the two slots swap, which is a plain move when the target was empty.
	 */
	public void endDrag(int targetSlot)
	{
		if (!dragging)
		{
			return;
		}

		int source = dragSource;
		cancelDrag();
		state.commitMove(source, targetSlot);
	}

	/** Drops any in-flight drag without touching the arrangement. */
	public void cancelDrag()
	{
		dragging = false;
		dragSource = -1;
		dragPoint = null;
		hoverSlot = -1;
	}

	public boolean isDragging()
	{
		return dragging;
	}

	public int getDragSource()
	{
		return dragSource;
	}

	public Point getDragPoint()
	{
		return dragPoint;
	}

	public int getHoverSlot()
	{
		return hoverSlot;
	}
}
