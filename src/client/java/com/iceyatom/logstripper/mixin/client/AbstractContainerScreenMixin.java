package com.iceyatom.logstripper.mixin.client;

import com.iceyatom.logstripper.LogStripperConfig;
import com.iceyatom.logstripper.client.StripButton;
import com.iceyatom.logstripper.client.StripFlashTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the strip button into the standard survival player inventory ({@code InventoryScreen})
 * only - not chests, barrels, shulker boxes, or the creative screen. It mixes into
 * {@link AbstractContainerScreen} (targeting only methods declared there, Section 7.1) but the
 * {@code init} handler bails out unless the concrete screen is an {@code InventoryScreen}.
 *
 * <p>{@code init} (TAIL): after vanilla lays out its widgets, adds the button at the
 * top-right of the container panel and registers a {@link ContainerListener} so the button
 * re-evaluates its state on every slot update rather than polling per frame (FR-04/NFR-02).
 *
 * <p>{@code removed} (TAIL): detaches the listener; the inventory menu outlives its screen,
 * so a stale listener would otherwise accumulate per screen open.
 *
 * <p>{@code extractRenderState} (TAIL): draws the optional fading highlight over slots that
 * changed as a result of a strip operation (config {@code highlight_changed_slots}), using
 * only the standard extractor fill API so it stays backend-agnostic.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
	@Shadow protected int leftPos;
	@Shadow protected int topPos;
	@Shadow @Final protected int imageWidth;
	@Shadow @Final protected AbstractContainerMenu menu;

	@Unique private StripButton logstripper$button;
	@Unique private ContainerListener logstripper$listener;

	protected AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init()V", at = @At("TAIL"))
	private void logstripper$addStripButton(CallbackInfo ci) {
		if (!LogStripperConfig.get().enabled) {
			return;
		}
		// Only the standard survival inventory gets the button - not chests, other containers,
		// or the creative screen (which is not an InventoryScreen).
		if (!((Object) this instanceof InventoryScreen)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}

		logstripper$button = new StripButton(
				this.leftPos + this.imageWidth - StripButton.SIZE - 5,
				this.topPos + 5,
				minecraft.player);
		this.addRenderableWidget(logstripper$button);

		// init() also runs on window resize; never stack a second listener on the menu.
		if (logstripper$listener != null) {
			this.menu.removeSlotListener(logstripper$listener);
		}
		logstripper$listener = new ContainerListener() {
			@Override
			public void slotChanged(AbstractContainerMenu containerMenu, int slotIndex, ItemStack stack) {
				if (logstripper$button != null) {
					logstripper$button.refreshState();
				}
				StripFlashTracker.onSlotChanged(slotIndex);
			}

			@Override
			public void dataChanged(AbstractContainerMenu containerMenu, int dataIndex, int value) {
			}
		};
		this.menu.addSlotListener(logstripper$listener);
	}

	@Inject(method = "removed()V", at = @At("TAIL"))
	private void logstripper$removed(CallbackInfo ci) {
		if (logstripper$listener != null) {
			this.menu.removeSlotListener(logstripper$listener);
			logstripper$listener = null;
		}
		logstripper$button = null;
		StripFlashTracker.clear();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void logstripper$drawSlotFlashes(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		if (!LogStripperConfig.get().highlightChangedSlots || !StripFlashTracker.hasFlashes()) {
			return;
		}
		for (int i = 0; i < this.menu.slots.size(); i++) {
			float alpha = StripFlashTracker.flashAlpha(i);
			if (alpha <= 0.0F) {
				continue;
			}
			Slot slot = this.menu.slots.get(i);
			int x = this.leftPos + slot.x;
			int y = this.topPos + slot.y;
			int a = (int) (alpha * 150.0F);
			extractor.fill(x, y, x + 16, y + 16, (a << 24) | 0xFFFFFF);
		}
	}
}
