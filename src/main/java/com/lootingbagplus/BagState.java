/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus;

import com.lootingbagplus.model.Arrangement;
import com.lootingbagplus.model.BagArranger;
import com.lootingbagplus.model.BagContents;
import com.lootingbagplus.model.BagLayout;
import com.lootingbagplus.model.BagSnapshot;
import com.lootingbagplus.model.BagSnapshot.SlotView;
import java.util.Objects;
import java.util.function.Consumer;
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
 * The single source of truth for the looting bag: what it holds (as last reported by the game),
 * how the player has arranged it, and the display-ready {@link BagSnapshot} built from the two.
 * The in-game window, the side panel and the floating overlay all read from here, so they can
 * never disagree about the arrangement.
 *
 * <p>Everything is client thread only except {@link #getSnapshot()}, which is a volatile read
 * safe from any thread. Both the arrangement and the last known contents are stored per RuneScape
 * profile (so they follow the character and sync with a RuneLite account) and written at most once
 * a second.
 *
 * <p>The game only transmits the bag's contents when the player checks it, uses items on it or
 * picks things up into it, so the contents are cached here and shown from the cache between
 * those moments.
 */
@Slf4j
@Singleton
public class BagState
{
	private static final String KEY_LAYOUT = "layout";
	private static final String KEY_CONTENTS = "contents";
	private static final long SAVE_INTERVAL_MS = 1000L;

	/**
	 * Item prices load asynchronously after login and refresh every half hour, with no event to
	 * tell us. Re-pricing the snapshot this often keeps the totals current at negligible cost.
	 */
	private static final long PRICE_REFRESH_MS = 15_000L;

	private final ItemManager itemManager;
	private final ConfigManager configManager;

	private String profileKey;
	private BagLayout layout = new BagLayout();
	private BagContents contents;
	private Arrangement arrangement;

	private boolean layoutDirty;
	private boolean contentsDirty;
	private long lastSaveMs;
	private long lastPriceRefreshMs;

	private volatile BagSnapshot snapshot = BagSnapshot.UNKNOWN;
	private volatile Consumer<BagSnapshot> listener = s ->
	{
	};

	@Inject
	public BagState(ItemManager itemManager, ConfigManager configManager)
	{
		this.itemManager = itemManager;
		this.configManager = configManager;
	}

	/**
	 * Called (client thread) every time the published snapshot changes, so the owner can refresh
	 * the side panel and re-apply the arrangement to the game window.
	 */
	public void setListener(Consumer<BagSnapshot> listener)
	{
		this.listener = listener == null ? s ->
		{
		} : listener;
	}

	/** Any thread. */
	public BagSnapshot getSnapshot()
	{
		return snapshot;
	}

	/** Client thread. {@code null} until the bag has been seen on this character. */
	public Arrangement getArrangement()
	{
		return arrangement;
	}

	/** Converts the game's looting bag container (516) into our own pure value type. */
	public static BagContents contentsOf(ItemContainer container)
	{
		Item[] items = container.getItems();
		int[] ids = new int[BagLayout.SLOTS];
		int[] quantities = new int[BagLayout.SLOTS];
		for (int i = 0; i < BagLayout.SLOTS; i++)
		{
			Item item = items != null && i < items.length ? items[i] : null;
			ids[i] = item == null ? -1 : item.getId();
			quantities[i] = item == null ? 0 : item.getQuantity();
		}
		return BagContents.of(ids, quantities);
	}

	// ---- profile and persistence ------------------------------------------------------------

	/**
	 * Client thread. Saves the outgoing profile and loads the layout and last known contents of
	 * the new one. A repeat of the current key is a no-op, so it is safe to call from several
	 * events.
	 */
	public void onProfileChanged(String newProfileKey)
	{
		if (Objects.equals(newProfileKey, profileKey))
		{
			return;
		}

		flushNow();

		// Contents that arrived before the profile key resolved are newer than anything stored.
		BagContents early = profileKey == null ? contents : null;

		profileKey = newProfileKey;
		if (newProfileKey == null)
		{
			layout = new BagLayout();
			contents = null;
		}
		else
		{
			layout = BagLayout.decode(configManager.getConfiguration(LootingBagPlusConfig.GROUP, newProfileKey, KEY_LAYOUT));
			contents = BagContents.decode(configManager.getConfiguration(LootingBagPlusConfig.GROUP, newProfileKey, KEY_CONTENTS));
		}

		layoutDirty = false;
		contentsDirty = false;
		if (early != null && newProfileKey != null)
		{
			contents = early;
			contentsDirty = true;
		}
		lastSaveMs = System.currentTimeMillis();

		log.debug("Loaded looting bag state for profile {}: layout={}, contentsKnown={}", newProfileKey, layout, contents != null);
		recompute();
	}

	/** Client thread, once per frame. Writes pending changes and keeps prices current. */
	public void tick()
	{
		long now = System.currentTimeMillis();

		if ((layoutDirty || contentsDirty) && profileKey != null && now - lastSaveMs >= SAVE_INTERVAL_MS)
		{
			save();
			lastSaveMs = now;
		}

		if (contents != null && now - lastPriceRefreshMs >= PRICE_REFRESH_MS)
		{
			lastPriceRefreshMs = now;
			BagSnapshot fresh = buildSnapshot();
			BagSnapshot old = snapshot;
			if (fresh.getGeValue() != old.getGeValue() || fresh.getHaValue() != old.getHaValue())
			{
				publish(fresh);
			}
		}
	}

	/** Client thread. Writes any pending change immediately. */
	public void flushNow()
	{
		if ((layoutDirty || contentsDirty) && profileKey != null)
		{
			save();
			lastSaveMs = System.currentTimeMillis();
		}
	}

	private void save()
	{
		if (layoutDirty)
		{
			if (layout.isEmpty())
			{
				configManager.unsetConfiguration(LootingBagPlusConfig.GROUP, profileKey, KEY_LAYOUT);
			}
			else
			{
				configManager.setConfiguration(LootingBagPlusConfig.GROUP, profileKey, KEY_LAYOUT, layout.encode());
			}
			layoutDirty = false;
		}

		if (contentsDirty)
		{
			if (contents != null)
			{
				configManager.setConfiguration(LootingBagPlusConfig.GROUP, profileKey, KEY_CONTENTS, contents.encode());
			}
			contentsDirty = false;
		}

		log.debug("Saved looting bag state for profile {}", profileKey);
	}

	// ---- changes ------------------------------------------------------------------------------

	/** Client thread. Records what the game says the bag now holds. */
	public void updateContents(BagContents newContents)
	{
		if (newContents.equals(contents))
		{
			return;
		}

		contents = newContents;
		contentsDirty = true;
		recompute();
	}

	/**
	 * Client thread. Applies a drag between two display slots.
	 *
	 * @return true if the arrangement changed
	 */
	public boolean commitMove(int sourceSlot, int targetSlot)
	{
		if (contents == null || arrangement == null)
		{
			return false;
		}

		boolean changed = BagArranger.commitMove(layout, arrangement, contents, sourceSlot, targetSlot);
		log.debug("Move {} -> {} changed layout: {} (now {})", sourceSlot, targetSlot, changed, layout);
		if (changed)
		{
			layoutDirty = true;
			recompute();
		}
		return changed;
	}

	/** Client thread. Forgets every pinned slot, returning the bag to the game's own order. */
	public void resetLayout()
	{
		if (layout.isEmpty())
		{
			return;
		}

		layout.clear();
		layoutDirty = true;
		recompute();
	}

	// ---- snapshot -----------------------------------------------------------------------------

	private void recompute()
	{
		if (contents == null)
		{
			arrangement = null;
			publish(BagSnapshot.UNKNOWN);
			return;
		}

		arrangement = BagArranger.arrange(layout, contents);
		if (arrangement.isLayoutChanged())
		{
			layoutDirty = true;
		}
		publish(buildSnapshot());
	}

	private void publish(BagSnapshot newSnapshot)
	{
		snapshot = newSnapshot;
		listener.accept(newSnapshot);
	}

	private BagSnapshot buildSnapshot()
	{
		SlotView[] views = new SlotView[BagLayout.SLOTS];
		long geValue = 0;
		long haValue = 0;
		int used = 0;

		for (int slot = 0; slot < BagLayout.SLOTS; slot++)
		{
			int containerIndex = arrangement.containerIndexAt(slot);
			if (containerIndex < 0)
			{
				continue;
			}

			int id = contents.idAt(containerIndex);
			int quantity = contents.quantityAt(containerIndex);

			// The bag can hold noted ids; name and alchemy value live on the un-noted item.
			ItemComposition composition = itemManager.getItemComposition(id);
			ItemComposition base = itemManager.getItemComposition(itemManager.canonicalize(id));
			long gePrice = itemManager.getItemPrice(id);
			long haPrice = id == ItemID.COINS ? 1 : base.getHaPrice();

			views[slot] = new SlotView(id, quantity, composition.getName(), composition.isStackable(), gePrice, haPrice);
			geValue += gePrice * quantity;
			haValue += haPrice * quantity;
			used++;
		}

		return new BagSnapshot(true, views, used, geValue, haValue);
	}
}
