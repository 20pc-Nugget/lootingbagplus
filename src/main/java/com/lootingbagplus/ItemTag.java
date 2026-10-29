/*
 * Copyright (c) 2019, Hydrox6 <ikada@protonmail.ch>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.lootingbagplus;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * The short on-icon tags for herbs and seeds, taken verbatim from RuneLite's own Item
 * Identification plugin so the overlays show exactly the same text ("R" for Ranarr, "HAR" for
 * Harralander in the short style; "Ranarr", "Harra" in the medium style). The Item Identification
 * plugin keeps its table private, so the herb and seed rows are reproduced here; every other
 * category in that plugin (logs, ores, potions and so on) is deliberately left out.
 *
 * <p>Look items up by their canonical (un-noted) id, as Item Identification does.
 */
enum ItemTag
{
	//Seeds
	GUAM_SEED(Kind.SEED, "Guam", "G", ItemID.GUAM_SEED),
	MARRENTILL_SEED(Kind.SEED, "Marren", "M", ItemID.MARRENTILL_SEED),
	TARROMIN_SEED(Kind.SEED, "Tarro", "TAR", ItemID.TARROMIN_SEED),
	HARRALANDER_SEED(Kind.SEED, "Harra", "HAR", ItemID.HARRALANDER_SEED),
	RANARR_SEED(Kind.SEED, "Ranarr", "R", ItemID.RANARR_SEED),
	TOADFLAX_SEED(Kind.SEED, "Toad", "TOA", ItemID.TOADFLAX_SEED),
	IRIT_SEED(Kind.SEED, "Irit", "I", ItemID.IRIT_SEED),
	AVANTOE_SEED(Kind.SEED, "Avan", "A", ItemID.AVANTOE_SEED),
	KWUARM_SEED(Kind.SEED, "Kwuarm", "K", ItemID.KWUARM_SEED),
	SNAPDRAGON_SEED(Kind.SEED, "Snap", "S", ItemID.SNAPDRAGON_SEED),
	CADANTINE_SEED(Kind.SEED, "Cadan", "C", ItemID.CADANTINE_SEED),
	LANTADYME_SEED(Kind.SEED, "Lanta", "L", ItemID.LANTADYME_SEED),
	DWARF_WEED_SEED(Kind.SEED, "Dwarf", "D", ItemID.DWARF_WEED_SEED),
	TORSTOL_SEED(Kind.SEED, "Torstol", "TOR", ItemID.TORSTOL_SEED),
	HUASCA_SEED(Kind.SEED, "Huasca", "HUA", ItemID.HUASCA_SEED),

	REDBERRY_SEED(Kind.SEED, "Redberry", "RED", ItemID.REDBERRY_BUSH_SEED),
	CADAVABERRY_SEED(Kind.SEED, "Cadava", "CAD", ItemID.CADAVABERRY_BUSH_SEED),
	DWELLBERRY_SEED(Kind.SEED, "Dwell", "DWEL", ItemID.DWELLBERRY_BUSH_SEED),
	JANGERBERRY_SEED(Kind.SEED, "Janger", "JANG", ItemID.JANGERBERRY_BUSH_SEED),
	WHITEBERRY_SEED(Kind.SEED, "White", "WHIT", ItemID.WHITEBERRY_BUSH_SEED),
	POISON_IVY_SEED(Kind.SEED, "Ivy", "POI", ItemID.POISONIVY_BUSH_SEED),

	GRAPE_SEED(Kind.SEED, "Grape", "GRA", ItemID.GRAPE_SEED),
	MUSHROOM_SPORE(Kind.SEED, "Mushroom", "MUSH", ItemID.MUSHROOM_SEED),
	BELLADONNA_SEED(Kind.SEED, "Bella", "BELL", ItemID.BELLADONNA_SEED),
	SEAWEED_SPORE(Kind.SEED, "Seaweed", "SW", ItemID.SEAWEED_SEED),
	HESPORI_SEED(Kind.SEED, "Hespori", "HES", ItemID.HESPORI_SEED),
	KRONOS_SEED(Kind.SEED, "Kronos", "KRO", ItemID.KRONOS_SEED),
	IASOR_SEED(Kind.SEED, "Iasor", "IA", ItemID.IASOR_SEED),
	ATTAS_SEED(Kind.SEED, "Attas", "AT", ItemID.ATTAS_SEED),
	CACTUS_SEED(Kind.SEED, "Cactus", "CAC", ItemID.CACTUS_SEED),
	POTATO_CACTUS_SEED(Kind.SEED, "P.Cact", "P.CAC", ItemID.POTATO_CACTUS_SEED),

	ACORN(Kind.SEED, "Oak", "OAK", ItemID.ACORN),
	WILLOW_SEED(Kind.SEED, "Willow", "WIL", ItemID.WILLOW_SEED),
	MAPLE_SEED(Kind.SEED, "Maple", "MAP", ItemID.MAPLE_SEED),
	YEW_SEED(Kind.SEED, "Yew", "YEW", ItemID.YEW_SEED),
	MAGIC_SEED(Kind.SEED, "Magic", "MAG", ItemID.MAGIC_TREE_SEED),
	REDWOOD_SEED(Kind.SEED, "Red", "RED", ItemID.REDWOOD_TREE_SEED),
	TEAK_SEED(Kind.SEED, "Teak", "TEAK", ItemID.TEAK_SEED),
	MAHOGANY_SEED(Kind.SEED, "Mahog", "MAH", ItemID.MAHOGANY_SEED),
	CAMPHOR_SEED(Kind.SEED, "Camphor", "CAM", ItemID.CAMPHOR_SEED),
	IRONWOOD_SEED(Kind.SEED, "Iron", "IRON", ItemID.IRONWOOD_SEED),
	ROSEWOOD_SEED(Kind.SEED, "Rose", "ROSE", ItemID.ROSEWOOD_SEED),
	CRYSTAL_ACORN(Kind.SEED, "Crystal", "CRY", ItemID.CRYSTAL_TREE_SEED),
	CELASTRUS_SEED(Kind.SEED, "Celas", "CEL", ItemID.CELASTRUS_TREE_SEED),
	SPIRIT_SEED(Kind.SEED, "Spirit", "SPI", ItemID.SPIRIT_TREE_SEED),
	CALQUAT_SEED(Kind.SEED, "Calquat", "CAL", ItemID.CALQUAT_TREE_SEED),

	APPLE_TREE_SEED(Kind.SEED, "Apple", "APP", ItemID.APPLE_TREE_SEED),
	BANANA_TREE_SEED(Kind.SEED, "Banana", "BAN", ItemID.BANANA_TREE_SEED),
	ORANGE_TREE_SEED(Kind.SEED, "Orange", "ORA", ItemID.ORANGE_TREE_SEED),
	CURRY_TREE_SEED(Kind.SEED, "Curry", "CUR", ItemID.CURRY_TREE_SEED),
	PINEAPPLE_SEED(Kind.SEED, "Pine.A", "PINE", ItemID.PINEAPPLE_TREE_SEED),
	PAPAYA_TREE_SEED(Kind.SEED, "Papaya", "PAPA", ItemID.PAPAYA_TREE_SEED),
	PALM_TREE_SEED(Kind.SEED, "Palm", "PALM", ItemID.PALM_TREE_SEED),
	DRAGONFRIUT_TREE_SEED(Kind.SEED, "Dragon", "DRA", ItemID.DRAGONFRUIT_TREE_SEED),

	POTATO_SEED(Kind.SEED, "Potato", "POT", ItemID.POTATO_SEED),
	ONION_SEED(Kind.SEED, "Onion", "ONI", ItemID.ONION_SEED),
	CABBAGE_SEED(Kind.SEED, "Cabbage", "CAB", ItemID.CABBAGE_SEED),
	TOMATO_SEED(Kind.SEED, "Tomato", "TOM", ItemID.TOMATO_SEED),
	SWEETCORN_SEED(Kind.SEED, "S.Corn", "CORN", ItemID.SWEETCORN_SEED),
	STRAWBERRY_SEED(Kind.SEED, "S.Berry", "STRA", ItemID.STRAWBERRY_SEED),
	WATERMELON_SEED(Kind.SEED, "Melon", "MEL", ItemID.WATERMELON_SEED),
	SNAPE_GRASS_SEED(Kind.SEED, "Snape", "SNA", ItemID.SNAPE_GRASS_SEED),

	MARIGOLD_SEED(Kind.SEED, "Marigo", "MARI", ItemID.MARIGOLD_SEED),
	ROSEMARY_SEED(Kind.SEED, "Rosemar", "ROSE", ItemID.ROSEMARY_SEED),
	NASTURTIUM_SEED(Kind.SEED, "Nastur", "NAS", ItemID.NASTURTIUM_SEED),
	WOAD_SEED(Kind.SEED, "Woad", "WOAD", ItemID.WOAD_SEED),
	LIMPWURT_SEED(Kind.SEED, "Limpwurt", "LIMP", ItemID.LIMPWURT_SEED),
	WHITE_LILY_SEED(Kind.SEED, "Lily", "LILY", ItemID.WHITE_LILY_SEED),

	BARLEY_SEED(Kind.SEED, "Barley", "BAR", ItemID.BARLEY_SEED),
	HAMMERSTONE_SEED(Kind.SEED, "Hammer", "HAMM", ItemID.HAMMERSTONE_HOP_SEED),
	ASGARNIAN_SEED(Kind.SEED, "Asgar", "ASG", ItemID.ASGARNIAN_HOP_SEED),
	JUTE_SEED(Kind.SEED, "Jute", "JUTE", ItemID.JUTE_SEED),
	YANILLIAN_SEED(Kind.SEED, "Yani", "YAN", ItemID.YANILLIAN_HOP_SEED),
	FLAX_SEED(Kind.SEED, "Flax", "FLAX", ItemID.FLAX_SEED),
	KRANDORIAN_SEED(Kind.SEED, "Krand", "KRA", ItemID.KRANDORIAN_HOP_SEED),
	WILDBLOOD_SEED(Kind.SEED, "Wild.B", "WILD", ItemID.WILDBLOOD_HOP_SEED),
	HEMP_SEED(Kind.SEED, "Hemp", "HEMP", ItemID.HEMP_SEED),
	COTTON_SEED(Kind.SEED, "Cotton", "COT", ItemID.COTTON_SEED),

	//Herbs
	GUAM(Kind.HERB, "Guam", "G", ItemID.GUAM_LEAF, ItemID.UNIDENTIFIED_GUAM),
	MARRENTILL(Kind.HERB, "Marren", "M", ItemID.MARENTILL, ItemID.UNIDENTIFIED_MARENTILL),
	TARROMIN(Kind.HERB, "Tarro", "TAR", ItemID.TARROMIN, ItemID.UNIDENTIFIED_TARROMIN),
	HARRALANDER(Kind.HERB, "Harra", "HAR", ItemID.HARRALANDER, ItemID.UNIDENTIFIED_HARRALANDER),
	RANARR(Kind.HERB, "Ranarr", "R", ItemID.RANARR_WEED, ItemID.UNIDENTIFIED_RANARR),
	TOADFLAX(Kind.HERB, "Toad", "TOA", ItemID.TOADFLAX, ItemID.UNIDENTIFIED_TOADFLAX),
	IRIT(Kind.HERB, "Irit", "I", ItemID.IRIT_LEAF, ItemID.UNIDENTIFIED_IRIT),
	AVANTOE(Kind.HERB, "Avan", "A", ItemID.AVANTOE, ItemID.UNIDENTIFIED_AVANTOE),
	KWUARM(Kind.HERB, "Kwuarm", "K", ItemID.KWUARM, ItemID.UNIDENTIFIED_KWUARM),
	SNAPDRAGON(Kind.HERB, "Snap", "S", ItemID.SNAPDRAGON, ItemID.UNIDENTIFIED_SNAPDRAGON),
	CADANTINE(Kind.HERB, "Cadan", "C", ItemID.CADANTINE, ItemID.UNIDENTIFIED_CADANTINE),
	LANTADYME(Kind.HERB, "Lanta", "L", ItemID.LANTADYME, ItemID.UNIDENTIFIED_LANTADYME),
	DWARF_WEED(Kind.HERB, "Dwarf", "D", ItemID.DWARF_WEED, ItemID.UNIDENTIFIED_DWARF_WEED),
	TORSTOL(Kind.HERB, "Torstol", "TOR", ItemID.TORSTOL, ItemID.UNIDENTIFIED_TORSTOL),
	HUASCA(Kind.HERB, "Huasca", "HUA", ItemID.HUASCA, ItemID.UNIDENTIFIED_HUASCA),

	ARDRIGAL(Kind.HERB, "Ardrig", "ARD", ItemID.ARDRIGAL, ItemID.UNIDENTIFIED_ARDRIGAL),
	ROGUES_PURSE(Kind.HERB, "Rogue", "ROG", ItemID.ROGUES_PURSE, ItemID.UNIDENTIFIED_ROGUES_PURSE),
	SITO_FOIL(Kind.HERB, "Sito", "SF", ItemID.SITO_FOIL, ItemID.UNIDENTIFIED_SITO_FOIL),
	SNAKE_WEED(Kind.HERB, "Snake", "SW", ItemID.SNAKE_WEED, ItemID.UNIDENTIFIED_SNAKE_WEED),
	VOLENCIA_MOSS(Kind.HERB, "Volenc", "V", ItemID.VOLENCIA_MOSS, ItemID.UNIDENTIFIED_VOLENCIA_MOSS);

	enum Kind
	{
		SEED,
		HERB
	}

	private final Kind kind;
	private final String mediumName;
	private final String shortName;
	private final int[] itemIds;

	ItemTag(Kind kind, String mediumName, String shortName, int... itemIds)
	{
		this.kind = kind;
		this.mediumName = mediumName;
		this.shortName = shortName;
		this.itemIds = itemIds;
	}

	private static final Map<Integer, ItemTag> BY_ITEM_ID = new HashMap<>();

	static
	{
		for (ItemTag tag : values())
		{
			for (int id : tag.itemIds)
			{
				BY_ITEM_ID.put(id, tag);
			}
		}
	}

	/** @return the tag for a canonical item id, or {@code null} if it is not a herb or seed */
	static ItemTag get(int canonicalItemId)
	{
		return BY_ITEM_ID.get(canonicalItemId);
	}

	boolean isSeed()
	{
		return kind == Kind.SEED;
	}

	String getShortName()
	{
		return shortName;
	}

	String getMediumName()
	{
		return mediumName;
	}

	int[] getItemIds()
	{
		return itemIds.clone();
	}
}
