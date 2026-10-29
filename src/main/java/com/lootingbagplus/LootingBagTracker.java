/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * The signals used here (items used on the bag, pickups while the bag is open, reading the bag
 * when its window opens) follow Looting Bag Viewer, Copyright (c) 2026, javlanoob, and the "Add to
 * bag" deposit tracking of Dude, Where's My Stuff, Copyright 2022 Thource. Both BSD 2-Clause.
 * See LICENSE.
 */
package com.lootingbagplus;

import com.lootingbagplus.model.BagContents;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/**
 * Keeps the looting bag picture live between checks.
 *
 * <p>The game only sends the bag's contents when the bag is checked (and sends nothing at all for
 * an empty bag), so the changes the player makes are followed locally, the way other looting bag
 * plugins do it:
 * <ul>
 *   <li>an item used on the bag, once it leaves the inventory;</li>
 *   <li>items deposited while the "Add to bag" window is open;</li>
 *   <li>an item picked up off the ground while the bag is open, when it did not land in the
 *   inventory;</li>
 *   <li>the real contents, read when the bag's Check window opens (an empty bag counts).</li>
 * </ul>
 * A real container update always replaces what was tracked. Changes are ignored until the bag has
 * been checked once, since a partial list would be misleading. Anything this does not see (for
 * example emptying the bag at a bank) is corrected the next time the bag is checked.
 *
 * <p>Client thread only.
 */
@Slf4j
@Singleton
public class LootingBagTracker
{
	/** How long to wait for a deposit or pickup to land before giving up on it. */
	private static final int USE_TIMEOUT_TICKS = 100;
	private static final int PICKUP_TIMEOUT_TICKS = 50;

	/** The "Add to bag" deposit window is the same interface as Check; only its title differs. */
	private static final String DEPOSIT_TITLE = "add to bag";

	private final Client client;
	private final ItemManager itemManager;
	private final BagState state;

	private Map<Integer, Integer> lastInventory = new HashMap<>();

	/** Tick the Check window opened, so the contents can be read once they have loaded. */
	private int viewedTick = -1;

	/** An item used on the bag, waiting to leave the inventory. */
	private int pendingUseItemId = -1;
	private int pendingUseTick;

	/** An item clicked on the ground while the bag is open, waiting to see where it ends up. */
	private PendingPickup pendingPickup;

	@Inject
	public LootingBagTracker(Client client, ItemManager itemManager, BagState state)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.state = state;
	}

	/** Client thread. Takes a baseline of the inventory. */
	public void startUp()
	{
		lastInventory = countItems(client.getItemContainer(InventoryID.INV));
		clearPending();
	}

	/** Client thread. */
	public void shutDown()
	{
		lastInventory = new HashMap<>();
		clearPending();
	}

	/** Client thread. Tick counts restart on login, so nothing pending may survive a logout. */
	public void onLoggedOut()
	{
		lastInventory = new HashMap<>();
		clearPending();
	}

	/** Client thread, when the inventory container changes. */
	public void onInventoryChanged(ItemContainer inventory)
	{
		Map<Integer, Integer> now = countItems(inventory);

		if (pendingUseItemId != -1)
		{
			int removed = lastInventory.getOrDefault(pendingUseItemId, 0) - now.getOrDefault(pendingUseItemId, 0);
			if (removed > 0)
			{
				log.debug("Deposited {} x {} by using it on the bag", removed, pendingUseItemId);
				addToBag(pendingUseItemId, removed);
				pendingUseItemId = -1;
			}
		}
		else if (isDepositWindowOpen())
		{
			for (Map.Entry<Integer, Integer> entry : lastInventory.entrySet())
			{
				int removed = entry.getValue() - now.getOrDefault(entry.getKey(), 0);
				if (removed > 0 && !isLootingBag(entry.getKey()))
				{
					log.debug("Deposited {} x {} through the Add to bag window", removed, entry.getKey());
					addToBag(entry.getKey(), removed);
				}
			}
		}

		lastInventory = now;
	}

	/** Client thread, when the looting bag window loads. */
	public void onViewOpened()
	{
		viewedTick = client.getTickCount();
	}

	/** Client thread, when the looting bag window closes. */
	public void onViewClosed()
	{
		viewedTick = -1;
	}

	/** Client thread, for every menu click. */
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();

		// Picking an amount in the "How many?" dialog continues the deposit.
		if (action == MenuAction.WIDGET_CONTINUE)
		{
			return;
		}

		// Any other click means the player moved on from a deposit that never happened.
		pendingUseItemId = -1;

		if (action == MenuAction.WIDGET_TARGET_ON_WIDGET)
		{
			Widget selected = client.getSelectedWidget();
			Widget target = event.getWidget();
			if (selected == null || target == null)
			{
				return;
			}

			// Works both ways round: item on bag, or bag on item. Spells have no item id.
			int selectedId = selected.getItemId();
			int targetId = target.getItemId();
			int depositId = isLootingBag(targetId) ? selectedId : isLootingBag(selectedId) ? targetId : -1;
			if (depositId > 0 && !isLootingBag(depositId))
			{
				pendingUseItemId = depositId;
				pendingUseTick = client.getTickCount();
			}
			return;
		}

		if (!isGroundItemAction(action) || !"Take".equals(event.getMenuOption()) || !isBagOpen())
		{
			return;
		}

		WorldView worldView = client.getWorldView(event.getMenuEntry().getWorldViewId());
		if (worldView == null)
		{
			return;
		}

		int itemId = event.getId();
		pendingPickup = new PendingPickup(
			itemId,
			WorldPoint.fromScene(worldView, event.getParam0(), event.getParam1(), worldView.getPlane()),
			client.getTickCount(),
			lastInventory.getOrDefault(itemId, 0));
	}

	/** Client thread, when an item leaves the ground. */
	public void onItemDespawned(ItemDespawned event)
	{
		PendingPickup pickup = pendingPickup;
		if (pickup == null || pickup.despawnTick != -1 || event.getItem().getId() != pickup.itemId)
		{
			return;
		}

		WorldPoint location = event.getTile().getWorldLocation();
		if (!location.equals(pickup.location))
		{
			return;
		}

		// Only count it if we are the one standing on the item, not someone else taking it.
		Player player = client.getLocalPlayer();
		if (player == null || !player.getWorldLocation().equals(location))
		{
			return;
		}

		pickup.quantity = event.getItem().getQuantity();
		pickup.despawnTick = client.getTickCount();
	}

	/** Client thread, once per game tick. */
	public void onGameTick()
	{
		int tick = client.getTickCount();

		// An empty bag sends no container update when checked, so read it once the window loaded.
		if (viewedTick != -1 && tick > viewedTick)
		{
			viewedTick = -1;
			if (!isDepositWindowOpen())
			{
				ItemContainer container = client.getItemContainer(InventoryID.LOOTING_BAG);
				state.updateContents(container == null ? BagContents.empty() : BagState.contentsOf(container));
			}
		}

		if (pendingUseItemId != -1 && tick - pendingUseTick > USE_TIMEOUT_TICKS)
		{
			pendingUseItemId = -1;
		}

		PendingPickup pickup = pendingPickup;
		if (pickup == null)
		{
			return;
		}

		if (pickup.despawnTick == -1)
		{
			if (tick - pickup.clickTick > PICKUP_TIMEOUT_TICKS)
			{
				pendingPickup = null;
			}
			return;
		}

		// Give the inventory a tick to update. If the item did not show up there, it went in the bag
		// (with the "supplies go to inventory" setting, food and potions land in the inventory).
		if (tick > pickup.despawnTick)
		{
			int gained = lastInventory.getOrDefault(pickup.itemId, 0) - pickup.inventoryCountBefore;
			if (gained <= 0 && isBagOpen())
			{
				log.debug("Picked up {} x {} into the bag", pickup.quantity, pickup.itemId);
				addToBag(pickup.itemId, pickup.quantity);
			}
			pendingPickup = null;
		}
	}

	private void addToBag(int itemId, int quantity)
	{
		state.addItem(itemId, quantity, itemManager.getItemComposition(itemId).isStackable());
	}

	private boolean isDepositWindowOpen()
	{
		Widget title = client.getWidget(InterfaceID.WildernessLootingbag.TITLE);
		String text = title == null ? null : title.getText();
		return text != null && text.toLowerCase(Locale.ROOT).contains(DEPOSIT_TITLE);
	}

	private boolean isBagOpen()
	{
		return lastInventory.containsKey(ItemID.LOOTING_BAG_OPEN);
	}

	private void clearPending()
	{
		pendingUseItemId = -1;
		pendingPickup = null;
		viewedTick = -1;
	}

	private static boolean isLootingBag(int itemId)
	{
		return itemId == ItemID.LOOTING_BAG || itemId == ItemID.LOOTING_BAG_OPEN;
	}

	private static boolean isGroundItemAction(MenuAction action)
	{
		switch (action)
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				return true;
			default:
				return false;
		}
	}

	private static Map<Integer, Integer> countItems(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return counts;
	}

	private static final class PendingPickup
	{
		private final int itemId;
		private final WorldPoint location;
		private final int clickTick;
		private final int inventoryCountBefore;
		private int quantity;
		private int despawnTick = -1;

		PendingPickup(int itemId, WorldPoint location, int clickTick, int inventoryCountBefore)
		{
			this.itemId = itemId;
			this.location = location;
			this.clickTick = clickTick;
			this.inventoryCountBefore = inventoryCountBefore;
		}
	}
}
