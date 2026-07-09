package com.iceyatom.logstripper.client;

import com.iceyatom.logstripper.LogStripHelper;
import com.iceyatom.logstripper.LogStripperConfig;
import com.iceyatom.logstripper.StripRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The 16x16 strip button. Rendering is entirely vanilla ({@link ImageButton} +
 * {@link WidgetSprites}), which keeps it safe under both the OpenGL and Vulkan backends.
 * State is re-evaluated from slot-update events via {@link #refreshState()} (FR-04/NFR-02),
 * not by polling every frame.
 */
public class StripButton extends ImageButton {
	public static final int SIZE = 16;
	/** Client-side debounce window between accepted clicks, in milliseconds (NFR-05). */
	private static final long DEBOUNCE_MS = 400;

	private static final WidgetSprites SPRITES = new WidgetSprites(
			Identifier.fromNamespaceAndPath("logstripper", "strip_button"),
			Identifier.fromNamespaceAndPath("logstripper", "strip_button_disabled"),
			Identifier.fromNamespaceAndPath("logstripper", "strip_button_highlighted"));

	private final LocalPlayer player;
	private long lastClickMillis;

	public StripButton(int x, int y, LocalPlayer player) {
		super(x, y, SIZE, SIZE, SPRITES,
				button -> ((StripButton) button).sendStripRequest(),
				Component.translatable("logstripper.button.narration"));
		this.player = player;
		refreshState();
	}

	private void sendStripRequest() {
		long now = System.currentTimeMillis();
		if (now - lastClickMillis < DEBOUNCE_MS) {
			return;
		}
		lastClickMillis = now;
		// SYNC-03: fire-and-forget; the server's slot updates deliver the outcome.
		ClientPlayNetworking.send(StripRequestPayload.INSTANCE);
		StripFlashTracker.onStripRequested();
	}

	/** Recomputes active/inactive state and the preview tooltip from the local inventory mirror. */
	public void refreshState() {
		LogStripperConfig config = LogStripperConfig.get();
		LogStripHelper.AxeRef axe = LogStripHelper.findAxe(player, config.axeSelectionStrategy);
		int logs = LogStripHelper.countStrippableLogs(player.getInventory());

		this.active = axe != null && logs > 0;

		if (!config.showTooltipPreview) {
			setTooltip(null);
			return;
		}

		if (axe == null) {
			setTooltip(Tooltip.create(Component.translatable("logstripper.tooltip.no_axe")));
		} else if (logs == 0) {
			setTooltip(Tooltip.create(Component.translatable("logstripper.tooltip.no_logs")));
		} else {
			int uses = LogStripHelper.remainingUses(axe.stack());
			boolean free = player.hasInfiniteMaterials() || uses == Integer.MAX_VALUE;
			int willStrip = free ? logs : Math.min(uses, logs);
			Component durability = free
					? Component.translatable("logstripper.tooltip.unlimited")
					: Component.literal(String.valueOf(uses));
			setTooltip(Tooltip.create(
					Component.translatable("logstripper.tooltip.preview",
							axe.stack().getItemName(), durability, willStrip)));
		}
	}
}
