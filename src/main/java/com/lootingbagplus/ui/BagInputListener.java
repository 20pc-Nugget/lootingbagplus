/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * The approach to coordinate spaces under stretched mode (latching, at press time, whether events
 * arrive before or after RuneLite's own mouse translation) comes from Looting Bag Organizer,
 * Copyright (c) 2026, robrichardson13, BSD 2-Clause. See LICENSE.
 */
package com.lootingbagplus.ui;

import com.lootingbagplus.BagState;
import com.lootingbagplus.model.BagGeometry;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;

/**
 * Mouse and Escape-key input for the looting bag grid. Runs on the AWT event thread and never
 * touches client state directly: it decides what to do from the volatile values
 * {@link BagWindowController} publishes, and posts every mutation to run on the client thread.
 *
 * <p>Registered at mouse-listener position 0, ahead of the game's own handling, so a consumed
 * left press never reaches the client. Nothing is written into the world, so nothing triggers the
 * modal close that would otherwise dismiss the window mid-drag. Right clicks and mouse moves are
 * never consumed, so Examine and the value tooltip keep working. Alt is respected so Alt-dragging
 * other RuneLite overlays across the bag keeps working.
 *
 * <h2>Coordinate space</h2>
 *
 * <p>Position 0 is also where RuneLite's stretched-mode plugin inserts its mouse translator, and
 * which of the two runs first is not under our control. When the translator runs first the event
 * is already in game-canvas space; when it runs second the event carries raw stretched pixels.
 * Widget bounds are always game space, so the space is measured once per gesture instead of
 * assumed: at press time {@link Client#getMouseCanvasPosition()} is provably fresh (an unconsumed
 * move always precedes a press), so whichever of the raw or translated event position is closer to
 * it tells us which space we were handed, and that is latched for the rest of the gesture. The
 * scale itself is read from the client on every event, exactly as the translator computes it.
 *
 * <p>The tracked canvas position is never used after the press: we consume drag events, and the
 * client only advances that position for events that come back unconsumed.
 */
@Singleton
public class BagInputListener implements MouseListener, KeyListener
{
	private static final int DRAG_SLOP = 4;
	private static final double[] NO_STRETCH = {1.0d, 1.0d};

	private final BagWindowController controller;
	private final BagState state;
	private final Client client;

	// AWT thread only. Armed by a left press on an occupied slot; active once the drag has moved
	// past the slop, at which point beginDrag has been posted.
	private boolean dragArmed;
	private boolean dragActive;
	private int sourceSlot = -1;
	private Point pressPoint;

	/** True when events reach us before the stretched-mode translator has run. Latched per press. */
	private boolean eventsArePreTranslate;

	/** True while a press we consumed is outstanding, so its release is consumed too. */
	private boolean pressConsumed;

	/**
	 * AWT delivers pressed, released, then clicked, so {@link #pressConsumed} is already clear by
	 * the time the click arrives. An unconsumed click would reach the game and could dismiss the
	 * window, so the click that follows a consumed press and release is swallowed as well.
	 */
	private boolean clickConsumed;

	@Inject
	public BagInputListener(BagWindowController controller, BagState state, Client client)
	{
		this.controller = controller;
		this.state = state;
		this.client = client;
	}

	// ---- coordinate helpers -------------------------------------------------------------------

	private Point canvasPoint()
	{
		net.runelite.api.Point p = client.getMouseCanvasPosition();
		return p == null ? new Point(-1, -1) : new Point(p.getX(), p.getY());
	}

	/** Stretched-canvas to game-canvas scale, computed the way RuneLite's own translator does. */
	private double[] stretchScale()
	{
		if (!client.isStretchedEnabled())
		{
			return NO_STRETCH;
		}

		Dimension stretched = client.getStretchedDimensions();
		Dimension real = client.getRealDimensions();
		if (stretched == null || real == null
			|| stretched.width <= 0 || stretched.height <= 0
			|| real.getWidth() <= 0 || real.getHeight() <= 0)
		{
			return NO_STRETCH;
		}

		return new double[]{stretched.width / real.getWidth(), stretched.height / real.getHeight()};
	}

	private static Point translate(MouseEvent e, double[] scale)
	{
		return new Point((int) (e.getX() / scale[0]), (int) (e.getY() / scale[1]));
	}

	private static int manhattan(Point a, Point b)
	{
		return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
	}

	/** Game-space position of a drag or release, using the orientation latched at press. */
	private Point gamePoint(MouseEvent e)
	{
		return eventsArePreTranslate ? translate(e, stretchScale()) : new Point(e.getX(), e.getY());
	}

	private boolean occupied(int slot)
	{
		return slot >= 0 && slot < BagGeometry.SLOTS && state.getSnapshot().slot(slot) != null;
	}

	private void clearDragState()
	{
		dragArmed = false;
		dragActive = false;
		sourceSlot = -1;
		pressPoint = null;
	}

	private static MouseEvent consume(MouseEvent e)
	{
		e.consume();
		return e;
	}

	// ---- mouse --------------------------------------------------------------------------------

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		// Never consumed: hover tooltips stay untouched, and it is the unconsumed move stream that
		// keeps the client's tracked pointer position fresh for the next press.
		return e;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		pressConsumed = false;
		clickConsumed = false;
		clearDragState();

		Point canvas = canvasPoint();
		Point translated = translate(e, stretchScale());
		Point raw = new Point(e.getX(), e.getY());
		eventsArePreTranslate = manhattan(translated, canvas) < manhattan(raw, canvas);
		Point game = eventsArePreTranslate ? translated : raw;

		boolean insideGrid = controller.isViewOpen() && !e.isAltDown() && controller.getGridBounds().contains(canvas);
		if (!SwingUtilities.isLeftMouseButton(e) || !insideGrid)
		{
			return e;
		}

		Rectangle bounds = controller.getGridBounds();
		int slot = BagGeometry.slotAt(bounds, canvas.x, canvas.y);

		pressConsumed = true;
		if (occupied(slot))
		{
			dragArmed = true;
			sourceSlot = slot;
			// Baseline for the slop, in the same space every later event resolves into.
			pressPoint = game;
		}

		return consume(e);
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		if (!dragArmed || pressPoint == null)
		{
			// Not our gesture (outside the grid, alt held, right button, or an empty-slot press):
			// consumed only if the press that started it was.
			return pressConsumed ? consume(e) : e;
		}

		Point game = gamePoint(e);

		if (!dragActive)
		{
			if (Math.abs(game.x - pressPoint.x) < DRAG_SLOP && Math.abs(game.y - pressPoint.y) < DRAG_SLOP)
			{
				return consume(e);
			}

			dragActive = true;
			final int source = sourceSlot;
			controller.post(() -> controller.beginDrag(source));
		}

		controller.post(() -> controller.updateDrag(game));
		return consume(e);
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		boolean consumed = pressConsumed;

		if (dragActive)
		{
			Point game = gamePoint(e);
			final int target = BagGeometry.slotAt(controller.getGridBounds(), game.x, game.y);
			controller.post(() -> controller.endDrag(target));
		}

		clearDragState();
		clickConsumed = consumed;
		pressConsumed = false;

		return consumed ? consume(e) : e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		boolean consumed = clickConsumed;
		clickConsumed = false;
		return consumed ? consume(e) : e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		return e;
	}

	// ---- keyboard -----------------------------------------------------------------------------

	@Override
	public void keyTyped(KeyEvent e)
	{
		// Nothing of ours is typed into.
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (e.getKeyCode() != KeyEvent.VK_ESCAPE || !dragArmed)
		{
			// Not dragging: Escape is left to the game, which closes the window as normal.
			return;
		}

		controller.post(controller::cancelDrag);
		clearDragState();
		pressConsumed = false;
		clickConsumed = false;
		e.consume();
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		// The cancel already happened on keyPressed.
	}

	@Override
	public void focusLost()
	{
		if (dragArmed)
		{
			controller.post(controller::cancelDrag);
		}
		clearDragState();
		pressConsumed = false;
		clickConsumed = false;
	}
}
