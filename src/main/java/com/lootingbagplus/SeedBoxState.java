/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus;

import com.lootingbagplus.model.BagSnapshot.SlotView;
import com.lootingbagplus.model.StoredItems;
import com.lootingbagplus.model.StoredItemsSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/**
 * Remembers what the seed box last held and builds the display-ready snapshot for the seed box
 * overlay. The seed box has no arrangement to customise, so this is a simpler sibling of
 * {@link BagState}: the contents are cached per RuneScape profile (written at most once a second)
 * and shown from the cache between the moments the game transmits them.
 *
 * <p>Client thread only, except {@link #getSnapshot()}, which is a volatile read safe from any
 * thread.
 */
@Slf4j
@Singleton
public class SeedBoxState
{
	private static final String KEY_CONTENTS = "seedbox";
	private static final long SAVE_INTERVAL_MS = 1000L;

	/** Prices load asynchronously after login with no event to tell us, so re-price now and then. */
	private static final long PRICE_REFRESH_MS = 15_000L;

	private final ItemManager itemManager;
	private final ConfigManager configManager;

	private String profileKey;
	private StoredItems contents;
	private boolean dirty;
	private long lastSaveMs;
	private long lastPriceRefreshMs;

	private volatile StoredItemsSnapshot snapshot = StoredItemsSnapshot.UNKNOWN;

	@Inject
	public SeedBoxState(ItemManager itemManager, ConfigManager configManager)
	{
		this.itemManager = itemManager;
		this.configManager = configManager;
	}

	/** Any thread. */
	public StoredItemsSnapshot getSnapshot()
	{
		return snapshot;
	}

	/** Converts the game's seed box container into our own pure value type. */
	public static StoredItems contentsOf(ItemContainer container)
	{
		Item[] items = container.getItems();
		int length = items == null ? 0 : items.length;
		int[] ids = new int[length];
		int[] quantities = new int[length];
		for (int i = 0; i < length; i++)
		{
			Item item = items[i];
			ids[i] = item == null ? -1 : item.getId();
			quantities[i] = item == null ? 0 : item.getQuantity();
		}
		return StoredItems.of(ids, quantities);
	}

	/** Client thread. Saves the outgoing profile and loads the new one. A repeat key is a no-op. */
	public void onProfileChanged(String newProfileKey)
	{
		if (Objects.equals(newProfileKey, profileKey))
		{
			return;
		}

		flushNow();

		// Contents that arrived before the profile key resolved are newer than anything stored.
		StoredItems early = profileKey == null ? contents : null;

		profileKey = newProfileKey;
		contents = newProfileKey == null
			? null
			: StoredItems.decode(configManager.getConfiguration(LootingBagPlusConfig.GROUP, newProfileKey, KEY_CONTENTS));

		dirty = false;
		if (early != null && newProfileKey != null)
		{
			contents = early;
			dirty = true;
		}
		lastSaveMs = System.currentTimeMillis();

		rebuild();
	}

	/** Client thread. Records what the game says the seed box now holds. */
	public void updateContents(StoredItems newContents)
	{
		log.debug("Seed box contents now {} item types", newContents.size());
		if (newContents.equals(contents))
		{
			return;
		}

		contents = newContents;
		dirty = true;
		rebuild();
	}

	/**
	 * Client thread. Adds seeds the player has just stored, so the picture stays live between
	 * syncs. Ignored until the box has been seen once; the next real container update replaces it.
	 */
	public void addItem(int itemId, int quantity)
	{
		if (contents != null)
		{
			updateContents(contents.withAdded(itemId, quantity));
		}
	}

	/** Client thread. Takes seeds out after the game reports them leaving the box. */
	public void removeItem(int itemId, int quantity)
	{
		if (contents != null)
		{
			updateContents(contents.withRemoved(itemId, quantity));
		}
	}

	/** Client thread, once per frame. Writes pending changes and keeps prices current. */
	public void tick()
	{
		long now = System.currentTimeMillis();

		if (dirty && profileKey != null && now - lastSaveMs >= SAVE_INTERVAL_MS)
		{
			save();
			lastSaveMs = now;
		}

		if (contents != null && now - lastPriceRefreshMs >= PRICE_REFRESH_MS)
		{
			lastPriceRefreshMs = now;
			StoredItemsSnapshot fresh = buildSnapshot();
			StoredItemsSnapshot old = snapshot;
			if (fresh.getGeValue() != old.getGeValue() || fresh.getHaValue() != old.getHaValue())
			{
				snapshot = fresh;
			}
		}
	}

	/** Client thread. Writes any pending change immediately. */
	public void flushNow()
	{
		if (dirty && profileKey != null)
		{
			save();
			lastSaveMs = System.currentTimeMillis();
		}
	}

	private void save()
	{
		if (contents != null)
		{
			configManager.setConfiguration(LootingBagPlusConfig.GROUP, profileKey, KEY_CONTENTS, contents.encode());
		}
		dirty = false;
	}

	private void rebuild()
	{
		snapshot = contents == null ? StoredItemsSnapshot.UNKNOWN : buildSnapshot();
	}

	private StoredItemsSnapshot buildSnapshot()
	{
		List<SlotView> views = new ArrayList<>();
		long geValue = 0;
		long haValue = 0;

		for (int i = 0; i < contents.size(); i++)
		{
			int id = contents.idAt(i);
			int quantity = contents.quantityAt(i);

			ItemComposition composition = itemManager.getItemComposition(id);
			ItemComposition base = itemManager.getItemComposition(itemManager.canonicalize(id));
			long gePrice = itemManager.getItemPrice(id);
			long haPrice = id == ItemID.COINS ? 1 : base.getHaPrice();

			ItemTag tag = ItemTag.get(itemManager.canonicalize(id));
			views.add(new SlotView(id, quantity, composition.getName(), composition.isStackable(), gePrice, haPrice,
				tag == null ? null : tag.getShortName(), tag == null ? null : tag.getMediumName()));
			geValue += gePrice * quantity;
			haValue += haPrice * quantity;
		}

		return new StoredItemsSnapshot(true, views, geValue, haValue);
	}
}
