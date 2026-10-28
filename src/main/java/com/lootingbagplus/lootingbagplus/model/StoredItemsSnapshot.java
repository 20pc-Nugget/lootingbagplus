/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.model;

import com.lootingbagplus.model.BagSnapshot.SlotView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable, display-ready picture of a storage container such as the seed box: its items in
 * order, ready to draw without touching the client.
 */
public final class StoredItemsSnapshot
{
	/** Nothing known yet: the container has never been seen on this character. */
	public static final StoredItemsSnapshot UNKNOWN = new StoredItemsSnapshot(false, new ArrayList<>(), 0, 0);

	private final boolean known;
	private final List<SlotView> items;
	private final long geValue;
	private final long haValue;

	public StoredItemsSnapshot(boolean known, List<SlotView> items, long geValue, long haValue)
	{
		this.known = known;
		this.items = Collections.unmodifiableList(new ArrayList<>(items));
		this.geValue = geValue;
		this.haValue = haValue;
	}

	/** False until the container has been seen at least once on this character. */
	public boolean isKnown()
	{
		return known;
	}

	public List<SlotView> getItems()
	{
		return items;
	}

	public long getGeValue()
	{
		return geValue;
	}

	public long getHaValue()
	{
		return haValue;
	}
}
