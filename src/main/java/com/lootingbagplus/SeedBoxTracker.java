/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * The idea of following the seed box through the game's own messages comes from Dude, Where's My
 * Stuff, Copyright 2022 Thource, BSD 2-Clause. See LICENSE.
 */
package com.lootingbagplus;

import com.lootingbagplus.model.SeedBoxMessages;
import com.lootingbagplus.model.StoredItems;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/**
 * Keeps the seed box picture live between syncs, without ever needing the box to be opened again.
 *
 * <p>The game does not tell the client what is in the seed box unless its window is open, so the
 * changes the player makes are followed locally:
 * <ul>
 *   <li><b>Fill</b> (or a seed used on the box): the seeds that leave the inventory went in;</li>
 *   <li><b>Empty</b>: the seeds that arrive in the inventory came out;</li>
 *   <li>a seed picked up off the ground while the box is open, when it did not land in the
 *   inventory, went in;</li>
 *   <li>a seed planted on a patch: the game uses the box's seeds first and keeps the one in the
 *   inventory, so whatever the patch needed beyond what left the inventory came out of the box
 *   (the planting is recognised by the Farming experience it gives);</li>
 *   <li>the real contents, read whenever the window is open (an empty box included).</li>
 * </ul>
 * The game's chat messages are only a fallback for changes none of the above saw. A real container
 * update always replaces what was tracked. Changes are ignored until the box has been seen once,
 * since a partial list would be misleading; after that the contents are remembered per character.
 *
 * <p>Client thread only.
 */
@Slf4j
@Singleton
public class SeedBoxTracker
{
	/** How long to wait for a Fill or Empty to show up in the inventory. */
	private static final int OP_TIMEOUT_TICKS = 4;
	private static final int PICKUP_TIMEOUT_TICKS = 50;
	private static final int PLANT_TIMEOUT_TICKS = 30;

	/** Chat messages this close to a change we already applied are the same change. */
	private static final int CHAT_DEDUPE_TICKS = 2;

	private enum Op
	{
		NONE,
		FILL,
		EMPTY
	}

	private final Client client;
	private final ItemManager itemManager;
	private final SeedBoxState state;

	/** Seed item names (lower case) to item ids, built on first use from the herb and seed table. */
	private Map<String, Integer> seedIdsByName;
	private Set<Integer> seedItemIds;

	private Map<Integer, Integer> lastInventory = new HashMap<>();

	/** Tick the seed box window opened, so its contents can be read once they have loaded. */
	private int viewedTick = -1;

	/**
	 * Tick the game last sent the real seed box contents. Those already include any change made
	 * that tick, so it must not be applied again on top of them.
	 */
	private int containerUpdateTick = -1;

	/** A Fill or Empty the player just chose, waiting to show up in the inventory. */
	private Op pendingOp = Op.NONE;
	private int pendingOpTick;

	/** A seed clicked on the ground while the box is open, waiting to see where it ends up. */
	private PendingPickup pendingPickup;

	/** A seed used on a patch, waiting for the Farming experience that says it was planted. */
	private PendingPlant pendingPlant;
	private int lastFarmingXp = -1;

	/** Tick we last applied a change ourselves, so the matching chat message is not applied too. */
	private int lastChangeTick = -100;

	@Inject
	public SeedBoxTracker(Client client, ItemManager itemManager, SeedBoxState state)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.state = state;
	}

	/** Client thread. Takes a baseline of the inventory and works out whether a box is carried. */
	public void startUp()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		lastInventory = countItems(inventory);
		updateCarried(lastInventory);
		clearPending();
		lastFarmingXp = client.getGameState() == GameState.LOGGED_IN ? client.getSkillExperience(Skill.FARMING) : -1;
	}

	/** Client thread. */
	public void shutDown()
	{
		lastInventory = new HashMap<>();
		containerUpdateTick = -1;
		clearPending();
	}

	/** Client thread. Tick counts restart on login, and nothing is carried at the login screen. */
	public void onLoggedOut()
	{
		lastInventory = new HashMap<>();
		containerUpdateTick = -1;
		lastChangeTick = -100;
		lastFarmingXp = -1;
		clearPending();
		state.setCarried(false);
	}

	/** Client thread, when the game sends the real seed box contents. */
	public void onContainerUpdated()
	{
		containerUpdateTick = client.getTickCount();
	}

	/** Client thread, when the inventory container changes. */
	public void onInventoryChanged(ItemContainer inventory)
	{
		Map<Integer, Integer> now = countItems(inventory);
		int tick = client.getTickCount();

		updateCarried(now);

		if (pendingOp != Op.NONE)
		{
			boolean applied = false;
			for (int id : seedItemIds())
			{
				int before = lastInventory.getOrDefault(id, 0);
				int after = now.getOrDefault(id, 0);

				if (pendingOp == Op.FILL && before > after)
				{
					applied = true;
					if (containerUpdateTick != tick)
					{
						log.debug("Seed box filled with {} x {}", before - after, id);
						state.addItem(id, before - after);
					}
				}
				else if (pendingOp == Op.EMPTY && after > before)
				{
					applied = true;
					if (containerUpdateTick != tick)
					{
						log.debug("Seed box emptied of {} x {}", after - before, id);
						state.removeItem(id, after - before);
					}
				}
			}

			if (applied)
			{
				pendingOp = Op.NONE;
				lastChangeTick = tick;
			}
		}

		lastInventory = now;
	}

	/** Client thread, for every menu click. */
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		int tick = client.getTickCount();

		// Any other click means the player gave up on planting before it happened.
		if (pendingPlant != null && pendingPlant.plantedTick == -1 && action != MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			pendingPlant = null;
		}

		// A seed used on an object (a patch). Using the seed box itself on a patch plants the first
		// seed of that patch's type, which cannot be told from here, so only seeds are followed.
		if (action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			Widget selected = client.getSelectedWidget();
			int selectedId = selected == null ? -1 : selected.getItemId();
			if (seedItemIds().contains(selectedId))
			{
				pendingPlant = new PendingPlant(selectedId, tick, lastInventory.getOrDefault(selectedId, 0));
			}
			return;
		}

		if (action == MenuAction.WIDGET_TARGET_ON_WIDGET)
		{
			Widget selected = client.getSelectedWidget();
			Widget target = event.getWidget();
			if (selected != null && target != null
				&& (isSeedBox(selected.getItemId()) || isSeedBox(target.getItemId())))
			{
				pendingOp = Op.FILL;
				pendingOpTick = tick;
			}
			return;
		}

		if (isSeedBox(event.getItemId()))
		{
			String option = event.getMenuOption();
			if ("Fill".equals(option))
			{
				pendingOp = Op.FILL;
				pendingOpTick = tick;
			}
			else if ("Empty".equals(option))
			{
				pendingOp = Op.EMPTY;
				pendingOpTick = tick;
			}
			return;
		}

		if (!isGroundItemAction(action) || !"Take".equals(event.getMenuOption()) || !isBoxOpen()
			|| !seedItemIds().contains(event.getId()))
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
			tick,
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

	/** Client thread. Planting a seed gives Farming experience, which is how it is recognised. */
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.FARMING)
		{
			return;
		}

		int xp = event.getXp();
		boolean gained = lastFarmingXp >= 0 && xp > lastFarmingXp;
		lastFarmingXp = xp;

		PendingPlant plant = pendingPlant;
		if (gained && plant != null && plant.plantedTick == -1)
		{
			plant.plantedTick = client.getTickCount();
		}
	}

	/** Client thread, for every chat message. A fallback for changes nothing above saw. */
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		SeedBoxMessages.Change change = SeedBoxMessages.parse(event.getMessage());
		if (change == null)
		{
			return;
		}

		int tick = client.getTickCount();

		// The inventory (or pickup) tracking handles this change, so the message is the same one.
		if (pendingOp != Op.NONE || pendingPickup != null || pendingPlant != null || Math.abs(tick - lastChangeTick) <= CHAT_DEDUPE_TICKS)
		{
			log.debug("Ignoring seed box message already handled by inventory tracking");
			return;
		}

		// The real container update for this change already holds the new total.
		if (containerUpdateTick == tick)
		{
			log.debug("Ignoring seed box message already covered by the container update");
			return;
		}

		Integer itemId = seedId(change.getSeedName());
		if (itemId == null)
		{
			log.debug("Seed box message for an unknown seed: {}", change.getSeedName());
			return;
		}

		lastChangeTick = tick;
		if (change.isAdded())
		{
			state.addItem(itemId, change.getQuantity());
		}
		else
		{
			state.removeItem(itemId, change.getQuantity());
		}
	}

	/** Client thread, when the seed box window loads. */
	public void onViewOpened()
	{
		viewedTick = client.getTickCount();
	}

	/** Client thread, when the seed box window closes. */
	public void onViewClosed()
	{
		viewedTick = -1;
	}

	/** Client thread, once per game tick. */
	public void onGameTick()
	{
		int tick = client.getTickCount();

		if (pendingOp != Op.NONE && tick - pendingOpTick > OP_TIMEOUT_TICKS)
		{
			pendingOp = Op.NONE;
		}

		resolvePickup(tick);
		resolvePlant(tick);

		if (viewedTick == -1 || tick <= viewedTick)
		{
			return;
		}
		viewedTick = -1;

		ItemContainer container = client.getItemContainer(InventoryID.SEED_BOX);
		if (container != null)
		{
			state.updateContents(SeedBoxState.contentsOf(container));
		}
		else if (isWindowEmpty())
		{
			// An empty box may send no container at all; the open window showing no seeds says so.
			state.updateContents(StoredItems.empty());
		}
	}

	private void resolvePickup(int tick)
	{
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

		// Give the inventory a tick to update. A seed that did not show up there went in the box.
		if (tick > pickup.despawnTick)
		{
			int gained = lastInventory.getOrDefault(pickup.itemId, 0) - pickup.inventoryCountBefore;
			if (gained <= 0 && isBoxOpen() && containerUpdateTick < pickup.despawnTick)
			{
				log.debug("Picked up {} x {} into the seed box", pickup.quantity, pickup.itemId);
				state.addItem(pickup.itemId, pickup.quantity);
				lastChangeTick = tick;
			}
			pendingPickup = null;
		}
	}

	private void resolvePlant(int tick)
	{
		PendingPlant plant = pendingPlant;
		if (plant == null)
		{
			return;
		}

		if (plant.plantedTick == -1)
		{
			if (tick - plant.clickTick > PLANT_TIMEOUT_TICKS)
			{
				pendingPlant = null;
			}
			return;
		}

		// Give the inventory a tick to update. The game takes the box's seeds first, so whatever the
		// patch needed beyond what left the inventory came out of the box.
		if (tick > plant.plantedTick)
		{
			int fromInventory = Math.max(0, plant.inventoryBefore - lastInventory.getOrDefault(plant.itemId, 0));
			int fromBox = seedsRequired(plant.itemId) - fromInventory;
			if (fromBox > 0 && containerUpdateTick < plant.plantedTick)
			{
				log.debug("Planted {}: {} came out of the seed box", plant.itemId, fromBox);
				state.removeItem(plant.itemId, fromBox);
			}
			lastChangeTick = tick;
			pendingPlant = null;
		}
	}

	/** How many seeds a patch takes to plant this seed. */
	private static int seedsRequired(int itemId)
	{
		ItemTag tag = ItemTag.get(itemId);
		if (tag == null)
		{
			return 1;
		}

		switch (tag.name())
		{
			// Allotments
			case "POTATO_SEED":
			case "ONION_SEED":
			case "CABBAGE_SEED":
			case "TOMATO_SEED":
			case "SWEETCORN_SEED":
			case "STRAWBERRY_SEED":
			case "WATERMELON_SEED":
			case "SNAPE_GRASS_SEED":
			// Fibre crops
			case "JUTE_SEED":
			case "FLAX_SEED":
			case "HEMP_SEED":
			case "COTTON_SEED":
				return 3;
			// Hops
			case "BARLEY_SEED":
			case "HAMMERSTONE_SEED":
			case "ASGARNIAN_SEED":
			case "YANILLIAN_SEED":
			case "KRANDORIAN_SEED":
			case "WILDBLOOD_SEED":
				return 4;
			default:
				return 1;
		}
	}

	private void updateCarried(Map<Integer, Integer> inventory)
	{
		state.setCarried(inventory.containsKey(ItemID.SEED_BOX) || inventory.containsKey(ItemID.SEED_BOX_OPEN));
	}

	/** True when the open seed box (the one that stores picked-up seeds) is in the inventory. */
	private boolean isBoxOpen()
	{
		return lastInventory.containsKey(ItemID.SEED_BOX_OPEN);
	}

	private static boolean isSeedBox(int itemId)
	{
		return itemId == ItemID.SEED_BOX || itemId == ItemID.SEED_BOX_OPEN;
	}

	private void clearPending()
	{
		pendingOp = Op.NONE;
		pendingPickup = null;
		pendingPlant = null;
		viewedTick = -1;
	}

	private boolean isWindowEmpty()
	{
		Widget layer = client.getWidget(InterfaceID.HosidiusSeedbox.SEED_LAYER);
		if (layer == null)
		{
			return false;
		}

		Widget[] children = layer.getChildren();
		if (children == null)
		{
			return false;
		}

		for (Widget child : children)
		{
			if (child != null && child.getItemId() != -1)
			{
				return false;
			}
		}
		return true;
	}

	private Integer seedId(String lowerCaseName)
	{
		ensureSeedIds();
		return seedIdsByName.get(lowerCaseName);
	}

	private Set<Integer> seedItemIds()
	{
		ensureSeedIds();
		return seedItemIds;
	}

	private void ensureSeedIds()
	{
		if (seedIdsByName != null)
		{
			return;
		}

		Map<String, Integer> byName = new HashMap<>();
		Set<Integer> ids = new HashSet<>();
		for (ItemTag tag : ItemTag.values())
		{
			if (!tag.isSeed())
			{
				continue;
			}
			for (int id : tag.getItemIds())
			{
				ids.add(id);
				byName.putIfAbsent(itemManager.getItemComposition(id).getName().toLowerCase(Locale.ROOT), id);
			}
		}

		// The seed box also holds gout tubers, which Item Identification does not tag.
		byName.put("gout tuber", ItemID.VILLAGE_RARE_TUBER);
		ids.add(ItemID.VILLAGE_RARE_TUBER);

		seedIdsByName = byName;
		seedItemIds = ids;
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

	private static final class PendingPlant
	{
		private final int itemId;
		private final int clickTick;
		private final int inventoryBefore;
		private int plantedTick = -1;

		PendingPlant(int itemId, int clickTick, int inventoryBefore)
		{
			this.itemId = itemId;
			this.clickTick = clickTick;
			this.inventoryBefore = inventoryBefore;
		}
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
