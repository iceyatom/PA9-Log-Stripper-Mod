package com.iceyatom.logstripper;

import com.iceyatom.logstripper.mixin.AxeItemAccessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item-level log -> stripped-log mapping (FR-13). Seeded at mod init from vanilla
 * {@code AxeItem.STRIPPABLES} (block level), which also picks up entries other mods have
 * added there. Other mods can additionally register pairs directly via {@link #register}
 * without touching LogStripHelper internals (NFR-11).
 */
public final class StrippableRegistry {
	private static final Map<Item, Item> STRIPPABLES = new LinkedHashMap<>();

	/** Called once from mod init; converts vanilla's block mapping into an item mapping. */
	static void bootstrap() {
		for (Map.Entry<Block, Block> entry : AxeItemAccessor.logstripper$getStrippables().entrySet()) {
			Item log = entry.getKey().asItem();
			Item stripped = entry.getValue().asItem();
			if (log != Items.AIR && stripped != Items.AIR) {
				STRIPPABLES.putIfAbsent(log, stripped);
			}
		}
		LogStripper.LOGGER.info("LogStripper registered {} strippable log pairs", STRIPPABLES.size());
	}

	/**
	 * Public API for other mods: register an additional log -> stripped-log item pair.
	 * Call during or after common mod initialization.
	 */
	public static void register(Item log, Item stripped) {
		if (log == null || stripped == null || log == Items.AIR || stripped == Items.AIR) {
			throw new IllegalArgumentException("log and stripped items must be non-null and non-air");
		}
		STRIPPABLES.put(log, stripped);
	}

	public static boolean isStrippable(Item item) {
		return STRIPPABLES.containsKey(item);
	}

	/** @return the stripped equivalent, or null if the item is not a registered strippable log. */
	public static Item getStripped(Item item) {
		return STRIPPABLES.get(item);
	}

	public static Map<Item, Item> view() {
		return Collections.unmodifiableMap(STRIPPABLES);
	}

	private StrippableRegistry() {
	}
}
