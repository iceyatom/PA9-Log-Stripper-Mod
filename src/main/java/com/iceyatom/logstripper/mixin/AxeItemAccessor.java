package com.iceyatom.logstripper.mixin;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes {@link AxeItem}'s protected static {@code STRIPPABLES} block-to-block map so the
 * strippable-log item registry (FR-13) can be seeded from the exact mapping vanilla uses for
 * right-click stripping, including any entries other mods have injected into it.
 */
@Mixin(AxeItem.class)
public interface AxeItemAccessor {
	@Accessor("STRIPPABLES")
	static Map<Block, Block> logstripper$getStrippables() {
		throw new AssertionError("mixin accessor not applied");
	}
}
