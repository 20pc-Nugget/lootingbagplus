/*
 * Copyright (c) 2026, Looting Bag Plus contributors
 * All rights reserved.
 *
 * See LICENSE.
 */
package com.lootingbagplus.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Short on-icon labels for herbs and seeds only ("R" for Ranarr, "A" for Avantoe), so a glance at
 * the overlay tells them apart. Every other item gets no label. Pure logic with no RuneLite
 * imports.
 *
 * <p>Each herb or seed starts with the first letter of its name. When two different ones would get
 * the same label, both are spelled out one more letter at a time until they differ (Harralander and
 * Huasca become "Ha" and "Hu"). Grimy and clean herbs and the matching seed of the same plant
 * share a label.
 */
public final class ItemLabels
{
	/** Herb names as the game spells them, lower case, without the "Grimy " prefix. */
	private static final Set<String> HERBS = new HashSet<>(Arrays.asList(
		"guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed", "toadflax", "irit leaf",
		"avantoe", "kwuarm", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol", "huasca"));

	private static final int MAX_ROUNDS = 16;

	private ItemLabels()
	{
	}

	/** True for grimy and clean herbs. */
	public static boolean isHerb(String name)
	{
		return name != null && HERBS.contains(baseName(name));
	}

	/**
	 * True for seeds: anything named "... seed" (Ranarr seed, Potato seed, Yew seed) and the oak
	 * tree's "Acorn". Plural stacks such as "Mithril seeds" are not farming seeds and do not match.
	 */
	public static boolean isSeed(String name)
	{
		if (name == null)
		{
			return false;
		}
		String lower = name.trim().toLowerCase(Locale.ROOT);
		return lower.endsWith(" seed") || lower.equals("acorn");
	}

	/** True for everything that gets a label: herbs and seeds. */
	public static boolean isLabelled(String name)
	{
		return isHerb(name) || isSeed(name);
	}

	/**
	 * @param names item names by display slot; {@code null} for an empty slot
	 * @return labels in the same positions; {@code null} for empty slots and for anything that is
	 * not a herb or a seed
	 */
	public static String[] labelsFor(String[] names)
	{
		String[] labels = new String[names.length];

		// One entry per distinct plant, so grimy and clean herbs and the seed share a label.
		Map<String, String> nameByKey = new LinkedHashMap<>();
		String[] keys = new String[names.length];
		for (int i = 0; i < names.length; i++)
		{
			if (isLabelled(names[i]))
			{
				keys[i] = shortName(names[i]);
				nameByKey.putIfAbsent(keys[i], keys[i]);
			}
		}

		Map<String, Integer> letters = new HashMap<>();
		for (String key : nameByKey.keySet())
		{
			letters.put(key, 1);
		}

		Map<String, String> labelByKey = new HashMap<>();
		for (int round = 0; round < MAX_ROUNDS; round++)
		{
			Map<String, List<String>> keysByLabel = new LinkedHashMap<>();
			for (String key : nameByKey.keySet())
			{
				String label = label(key, letters.get(key));
				labelByKey.put(key, label);
				keysByLabel.computeIfAbsent(label, l -> new ArrayList<>()).add(key);
			}

			boolean lengthened = false;
			for (List<String> clash : keysByLabel.values())
			{
				if (clash.size() < 2)
				{
					continue;
				}
				for (String key : clash)
				{
					if (letters.get(key) < key.length())
					{
						letters.put(key, letters.get(key) + 1);
						lengthened = true;
					}
				}
			}

			if (!lengthened)
			{
				break;
			}
		}

		for (int i = 0; i < names.length; i++)
		{
			if (keys[i] != null)
			{
				labels[i] = labelByKey.get(keys[i]);
			}
		}
		return labels;
	}

	/** Lower case name without a "Grimy " prefix. */
	private static String baseName(String name)
	{
		String lower = name.trim().toLowerCase(Locale.ROOT);
		return lower.startsWith("grimy ") ? lower.substring("grimy ".length()) : lower;
	}

	/** The first word of the name: "ranarr weed" and "ranarr seed" both become "ranarr". */
	private static String shortName(String itemName)
	{
		return baseName(itemName).split(" ")[0];
	}

	private static String label(String shortName, int letters)
	{
		int count = Math.min(letters, shortName.length());
		return Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1, count);
	}
}
