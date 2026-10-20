package com.iceyatom.logstripper;

import com.iceyatom.logstripper.mixin.AxeItemAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item-level log -> stripped-log mapping (FR-13). Seeded from vanilla {@code AxeItem.STRIPPABLES}
 * (block level; 1.21.x has no data-driven {@code minecraft:axe} block transformer), which also
 * picks up entries other mods have added there. The mapping is (re)built at the same points as
 * the 26.3 build: on server start, after a datapack reload, and on the client when joining a
 * world. Other mods can additionally register pairs directly via {@link #register} without
 * touching LogStripHelper internals (NFR-11).
 */
public final class StrippableRegistry {
	/** Pairs registered through the public API; always layered over the vanilla-derived pairs. */
	private static final Map<Item, Item> EXTRA = new LinkedHashMap<>();
	/** Effective mapping; swapped whole so client and server threads never see a partial rebuild. */
	private static volatile Map<Item, Item> strippables = Collections.emptyMap();

	/** Called once from mod init; rebuilds the mapping whenever server registries (re)load. */
	static void bootstrap() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> rebuild(server.registryAccess()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				rebuild(server.registryAccess());
			}
		});
	}

	/**
	 * Converts vanilla's axe strip mapping (block level) into the item mapping. {@code registries}
	 * is unused on 1.21.x (the mapping is static there) but kept so call sites match the 26.3 build.
	 */
	public static synchronized void rebuild(RegistryAccess registries) {
		Map<Item, Item> built = new LinkedHashMap<>();
		for (Map.Entry<Block, Block> entry : AxeItemAccessor.logstripper$getStrippables().entrySet()) {
			Item logItem = entry.getKey().asItem();
			Item strippedItem = entry.getValue().asItem();
			if (logItem != Items.AIR && strippedItem != Items.AIR) {
				built.putIfAbsent(logItem, strippedItem);
			}
		}
		built.putAll(EXTRA);
		strippables = built;
		LogStripper.LOGGER.info("LogStripper registered {} strippable log pairs", built.size());
	}

	/**
	 * Public API for other mods: register an additional log -> stripped-log item pair.
	 * Call during or after common mod initialization.
	 */
	public static synchronized void register(Item log, Item stripped) {
		if (log == null || stripped == null || log == Items.AIR || stripped == Items.AIR) {
			throw new IllegalArgumentException("log and stripped items must be non-null and non-air");
		}
		EXTRA.put(log, stripped);
		Map<Item, Item> updated = new LinkedHashMap<>(strippables);
		updated.put(log, stripped);
		strippables = updated;
	}

	public static boolean isStrippable(Item item) {
		return strippables.containsKey(item);
	}

	/** @return the stripped equivalent, or null if the item is not a registered strippable log. */
	public static Item getStripped(Item item) {
		return strippables.get(item);
	}

	public static Map<Item, Item> view() {
		return Collections.unmodifiableMap(strippables);
	}

	private StrippableRegistry() {
	}
}
