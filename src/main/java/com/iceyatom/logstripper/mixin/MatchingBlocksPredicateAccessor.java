package com.iceyatom.logstripper.mixin;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.MatchingBlocksPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link MatchingBlocksPredicate}'s private block set. Since 26.3, axe stripping is
 * data-driven through the {@code minecraft:axe} block transformer, whose rules match source
 * blocks with this predicate; reading it lets the strippable-log item registry (FR-13) be
 * seeded from the exact mapping vanilla uses for right-click stripping, including any entries
 * datapacks or other mods have added to it.
 */
@Mixin(MatchingBlocksPredicate.class)
public interface MatchingBlocksPredicateAccessor {
	@Accessor("blocks")
	HolderSet<Block> logstripper$getBlocks();
}
