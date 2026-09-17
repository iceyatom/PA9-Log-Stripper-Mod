package com.iceyatom.logstripper;

import com.iceyatom.logstripper.mixin.MatchingBlocksPredicateAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.MatchingBlocksPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.CopyPropertiesProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item-level log -> stripped-log mapping (FR-13). Seeded from vanilla's data-driven
 * {@code minecraft:axe} block transformer (strip entries only, block level), which also picks
 * up entries datapacks or other mods have added there. That data lives in a synced dynamic
 * registry, so the mapping is (re)built whenever registries become available: on server start,
 * after a datapack reload, and on the client when joining a world. Other mods can additionally
 * register pairs directly via {@link #register} without touching LogStripHelper internals (NFR-11).
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

	/** Converts vanilla's axe strip transformer (block level) into the item mapping. */
	public static synchronized void rebuild(RegistryAccess registries) {
		Map<Item, Item> built = new LinkedHashMap<>();
		registries.lookupOrThrow(Registries.BLOCK_TRANSFORMER).get(BlockTransformers.AXE).ifPresent(axe -> {
			for (BlockTransformer.BlockTransformData data : axe.value().transforms()) {
				// The axe transformer also scrapes and de-waxes copper; keep only stripping.
				if (!data.sound().is(SoundEvents.AXE_STRIP.key())
						|| !(data.blockStateProvider().value() instanceof RuleBasedStateProvider rules)) {
					continue;
				}
				for (RuleBasedStateProvider.Rule rule : rules.rules()) {
					Block stripped = targetBlock(rule.then().value());
					if (stripped == null || !(rule.ifTrue() instanceof MatchingBlocksPredicate matching)) {
						continue;
					}
					Item strippedItem = stripped.asItem();
					for (Holder<Block> log : ((MatchingBlocksPredicateAccessor) matching).logstripper$getBlocks()) {
						Item logItem = log.value().asItem();
						if (logItem != Items.AIR && strippedItem != Items.AIR) {
							built.putIfAbsent(logItem, strippedItem);
						}
					}
				}
			}
		});
		built.putAll(EXTRA);
		strippables = built;
		LogStripper.LOGGER.info("LogStripper registered {} strippable log pairs", built.size());
	}

	private static Block targetBlock(BlockStateProvider provider) {
		if (provider instanceof CopyPropertiesProvider copy) {
			provider = copy.source().value();
		}
		return provider instanceof SimpleStateProvider simple ? simple.state().getBlock() : null;
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
