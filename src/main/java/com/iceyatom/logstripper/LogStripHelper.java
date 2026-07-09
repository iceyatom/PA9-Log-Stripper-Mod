package com.iceyatom.logstripper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * All scanning, conversion, and durability logic for the strip operation (Section 7.3).
 * No rendering or networking dependencies; runs on the logical server only, except for the
 * read-only helpers ({@link #findAxe}, {@link #countStrippableLogs}, {@link #remainingUses})
 * which the client also uses to compute button state and the tooltip preview.
 */
public final class LogStripHelper {
	/** Player inventory slots 0-35: hotbar 0-8, then main inventory 9-35 (FR-09). */
	public static final int SCAN_SLOTS = 36;

	public enum AxeLocation {
		MAIN_HAND, OFF_HAND, INVENTORY
	}

	/** A located axe. {@code slot} is only meaningful for {@link AxeLocation#INVENTORY}. */
	public record AxeRef(ItemStack stack, AxeLocation location, int slot) {
	}

	/** Outcome summary (Section 7.2). */
	public record StripResult(int logsConverted, boolean axeBroken) {
		public static final StripResult NOTHING = new StripResult(0, false);
	}

	/**
	 * Locates the axe to use per FR-05 priority: main hand, off hand, hotbar in slot order,
	 * then main inventory in slot order. With HIGHEST_DURABILITY, the usable candidate with
	 * the most remaining uses wins (priority order breaks ties). Returns null if no usable
	 * axe exists (FR-07).
	 */
	public static AxeRef findAxe(Player player, LogStripperConfig.AxeSelectionStrategy strategy) {
		AxeRef best = null;
		int bestUses = -1;

		ItemStack main = player.getMainHandItem();
		if (isUsableAxe(main)) {
			AxeRef ref = new AxeRef(main, AxeLocation.MAIN_HAND, -1);
			if (strategy == LogStripperConfig.AxeSelectionStrategy.MAIN_HAND_FIRST) {
				return ref;
			}
			best = ref;
			bestUses = remainingUses(main);
		}

		ItemStack off = player.getOffhandItem();
		if (isUsableAxe(off)) {
			AxeRef ref = new AxeRef(off, AxeLocation.OFF_HAND, -1);
			if (strategy == LogStripperConfig.AxeSelectionStrategy.MAIN_HAND_FIRST) {
				return ref;
			}
			if (remainingUses(off) > bestUses) {
				best = ref;
				bestUses = remainingUses(off);
			}
		}

		Inventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		for (int slot = 0; slot < SCAN_SLOTS; slot++) {
			if (slot == selected) {
				continue; // already considered as main hand
			}
			ItemStack stack = inventory.getItem(slot);
			if (!isUsableAxe(stack)) {
				continue;
			}
			if (strategy == LogStripperConfig.AxeSelectionStrategy.MAIN_HAND_FIRST) {
				return new AxeRef(stack, AxeLocation.INVENTORY, slot);
			}
			if (remainingUses(stack) > bestUses) {
				best = new AxeRef(stack, AxeLocation.INVENTORY, slot);
				bestUses = remainingUses(stack);
			}
		}
		return best;
	}

	public static boolean isUsableAxe(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof AxeItem && remainingUses(stack) > 0;
	}

	/** Remaining durability uses; Integer.MAX_VALUE for undamageable (unbreakable) axes. */
	public static int remainingUses(ItemStack stack) {
		if (!stack.isDamageableItem()) {
			return Integer.MAX_VALUE;
		}
		return stack.getMaxDamage() - stack.getDamageValue();
	}

	/**
	 * Level of the Unbreaking enchantment on the stack, or 0 if absent. Used only to phrase the
	 * tooltip preview: with Unbreaking, durability is consumed probabilistically, so the raw
	 * remaining-uses count is a guaranteed floor rather than an exact strip count.
	 */
	public static int unbreakingLevel(ItemStack stack) {
		for (var entry : stack.getEnchantments().entrySet()) {
			if (entry.getKey().is(Enchantments.UNBREAKING)) {
				return entry.getIntValue();
			}
		}
		return 0;
	}

	/** Total count of strippable logs across slots 0-35 (used for FR-02/FR-03). */
	public static int countStrippableLogs(Inventory inventory) {
		int total = 0;
		for (int slot = 0; slot < SCAN_SLOTS; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && StrippableRegistry.isStrippable(stack.getItem())) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/**
	 * Runs one full strip operation for the player (FR-09 to FR-16). Any exception aborts the
	 * operation with a logged warning; every per-log step leaves the inventory consistent, so
	 * an abort can never duplicate or destroy items (NFR-03).
	 */
	public static StripResult stripInventory(ServerPlayer player) {
		try {
			return runStrip(player);
		} catch (Exception e) {
			LogStripper.LOGGER.warn("LogStripper: strip operation aborted", e);
			return StripResult.NOTHING;
		}
	}

	private static StripResult runStrip(ServerPlayer player) {
		LogStripperConfig config = LogStripperConfig.get();
		if (!config.enabled) {
			return StripResult.NOTHING;
		}

		AxeRef axeRef = findAxe(player, config.axeSelectionStrategy);
		if (axeRef == null) {
			return StripResult.NOTHING;
		}

		ItemStack axe = axeRef.stack();
		ServerLevel level = player.level();
		Inventory inventory = player.getInventory();
		// FR-15: creative mode or an unbreakable axe converts everything with no durability cost.
		boolean free = player.hasInfiniteMaterials() || !axe.isDamageableItem();
		// When set, stop before spending the axe's final durability point so it is never
		// consumed (config preserve_axe). Only meaningful for a damageable axe being charged.
		boolean preserveAxe = config.preserveAxe && !free;

		int convertedTotal = 0;
		boolean broke = false;
		boolean stop = false;

		for (int slot = 0; slot < SCAN_SLOTS && !stop; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Item stripped = StrippableRegistry.getStripped(stack.getItem());
			if (stripped == null) {
				continue;
			}
			// Don't consume the axe's own slot twice if a mod ever maps an axe as strippable.
			if (stack == axe) {
				continue;
			}

			int count = stack.getCount();
			int convertible = 0;
			if (free) {
				convertible = count;
			} else {
				// FR-11: one vanilla per-use damage roll per log, so Unbreaking behaves
				// exactly as it would right-clicking placed logs.
				for (int i = 0; i < count; i++) {
					// Leave the axe on its last durability point rather than breaking it.
					if (preserveAxe && remainingUses(axe) <= 1) {
						stop = true;
						break;
					}
					axe.hurtAndBreak(1, level, player, item -> onAxeBroken(player, axeRef));
					convertible++;
					if (axe.isEmpty()) {
						broke = true; // FR-14: stop at the point of breakage
						stop = true;
						break;
					}
				}
			}

			if (convertible > 0) {
				stack.shrink(convertible);
				if (stack.isEmpty()) {
					inventory.setItem(slot, ItemStack.EMPTY);
				}
				insertStripped(player, inventory, stripped, convertible);
				convertedTotal += convertible;
				if (config.debugLogging) {
					LogStripper.LOGGER.info("LogStripper: slot {} converted {} -> {} x{}",
							slot, stack.getItem(), stripped, convertible);
				}
			}
		}

		if (convertedTotal > 0 && config.playSoundOnComplete) {
			// FR-16: exactly one strip sound per operation.
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.AXE_STRIP, SoundSource.PLAYERS, 1.0F, 1.0F);
		}

		// FR-17 / SYNC-02: push resulting slot states out through the normal menu sync now.
		player.containerMenu.broadcastChanges();

		if (config.debugLogging) {
			LogStripper.LOGGER.info("LogStripper: operation complete, {} logs converted, axe broken: {}",
					convertedTotal, broke);
		}
		return new StripResult(convertedTotal, broke);
	}

	/**
	 * Inserts stripped logs, merging into existing stacks of the same item first, then using
	 * empty slots - including the source slot if the conversion emptied it (FR-12). If the
	 * inventory is somehow completely full, the surplus drops at the player's feet rather
	 * than being destroyed.
	 */
	private static void insertStripped(ServerPlayer player, Inventory inventory, Item stripped, int amount) {
		int remaining = amount;

		// Pass 1: top up existing stacks of the stripped item.
		for (int slot = 0; slot < SCAN_SLOTS && remaining > 0; slot++) {
			ItemStack existing = inventory.getItem(slot);
			if (!existing.isEmpty() && existing.getItem() == stripped
					&& existing.getCount() < existing.getMaxStackSize()) {
				int room = existing.getMaxStackSize() - existing.getCount();
				int moved = Math.min(room, remaining);
				existing.grow(moved);
				remaining -= moved;
			}
		}

		// Pass 2: fill empty slots.
		for (int slot = 0; slot < SCAN_SLOTS && remaining > 0; slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				ItemStack fresh = new ItemStack(stripped, Math.min(remaining, new ItemStack(stripped).getMaxStackSize()));
				inventory.setItem(slot, fresh);
				remaining -= fresh.getCount();
			}
		}

		if (remaining > 0) {
			player.drop(new ItemStack(stripped, remaining), false);
		}
	}

	/**
	 * FR-14: vanilla {@code hurtAndBreak} has already removed the stack and fired the break
	 * event by the time this runs; equipment slots additionally get the vanilla break
	 * animation/sound, and inventory axes get the item-break sound.
	 */
	private static void onAxeBroken(ServerPlayer player, AxeRef axeRef) {
		switch (axeRef.location()) {
			case MAIN_HAND -> player.onEquippedItemBroken(axeRef.stack().getItem(), EquipmentSlot.MAINHAND);
			case OFF_HAND -> player.onEquippedItemBroken(axeRef.stack().getItem(), EquipmentSlot.OFFHAND);
			case INVENTORY -> player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 0.8F + player.getRandom().nextFloat() * 0.4F);
		}
	}

	private LogStripHelper() {
	}
}
