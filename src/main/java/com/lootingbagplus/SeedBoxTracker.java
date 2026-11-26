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
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/**
 * Keeps the seed box picture live between syncs.
 *
 * <p>The game tells the player in chat whenever seeds go into or come out of the seed box ("Stored
 * 50 x Ranarr seed in your seed box.", "Emptied 12 x Potato seed to your inventory."), so those
 * messages are followed. The real contents are read when the seed box window opens (an empty box
 * included), and a real container update always replaces what was tracked. Changes are ignored until
 * the box has been seen once, since a partial list would be misleading.
 *
 * <p>Client thread only.
 */
@Slf4j
@Singleton
public class SeedBoxTracker
{
	/** Seed box (13639) and open seed box (24481). */
	private static final int SEED_BOX = 13639;
	private static final int SEED_BOX_OPEN = 24481;

	private final Client client;
	private final ItemManager itemManager;
	private final SeedBoxState state;

	/** Seed item names (lower case) to item ids, built on first use from the herb and seed table. */
	private Map<String, Integer> seedIdsByName;

	/** Tick the seed box window opened, so its contents can be read once they have loaded. */
	private int viewedTick = -1;

	/**
	 * Tick the game last sent the real seed box contents. Those already include any seeds the chat
	 * messages announce, so a message from the same tick must not be added on top of them.
	 */
	private int containerUpdateTick = -1;

	@Inject
	public SeedBoxTracker(Client client, ItemManager itemManager, SeedBoxState state)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.state = state;
	}

	/** Client thread. Works out whether a seed box is already in the inventory. */
	public void startUp()
	{
		updateCarried(client.getItemContainer(InventoryID.INV));
	}

	/** Client thread. */
	public void shutDown()
	{
		viewedTick = -1;
		containerUpdateTick = -1;
	}

	/** Client thread. Tick counts restart on login, and nothing is carried at the login screen. */
	public void onLoggedOut()
	{
		viewedTick = -1;
		containerUpdateTick = -1;
		state.setCarried(false);
	}

	/** Client thread, when the inventory container changes. */
	public void onInventoryChanged(ItemContainer inventory)
	{
		updateCarried(inventory);
	}

	/** Client thread, when the game sends the real seed box contents. */
	public void onContainerUpdated()
	{
		containerUpdateTick = client.getTickCount();
	}

	private void updateCarried(ItemContainer inventory)
	{
		boolean carried = false;
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() == SEED_BOX || item.getId() == SEED_BOX_OPEN)
				{
					carried = true;
					break;
				}
			}
		}
		state.setCarried(carried);
	}

	/** Client thread, for every chat message. */
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

		Integer itemId = seedId(change.getSeedName());
		if (itemId == null)
		{
			log.debug("Seed box message for an unknown seed: {}", change.getSeedName());
			return;
		}

		// The real container update for this change already holds the new total.
		if (containerUpdateTick == client.getTickCount())
		{
			log.debug("Ignoring seed box message already covered by the container update");
			return;
		}

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
		if (viewedTick == -1 || client.getTickCount() <= viewedTick)
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
		if (seedIdsByName == null)
		{
			Map<String, Integer> byName = new HashMap<>();
			for (ItemTag tag : ItemTag.values())
			{
				if (!tag.isSeed())
				{
					continue;
				}
				for (int id : tag.getItemIds())
				{
					byName.putIfAbsent(itemManager.getItemComposition(id).getName().toLowerCase(Locale.ROOT), id);
				}
			}

			// The seed box also holds gout tubers, which Item Identification does not tag.
			byName.put("gout tuber", ItemID.VILLAGE_RARE_TUBER);
			seedIdsByName = byName;
		}

		return seedIdsByName.get(lowerCaseName);
	}
}
