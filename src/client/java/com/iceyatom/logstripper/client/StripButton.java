package com.iceyatom.logstripper.client;

import com.iceyatom.logstripper.LogStripHelper;
import com.iceyatom.logstripper.LogStripperConfig;
import com.iceyatom.logstripper.StripRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The 16x16 strip button. Instead of a static sprite it draws the axe item that would actually
 * be used (FR-05 selection), so the icon reflects the chosen tool - stone, iron, diamond, etc.
 * Rendering goes through the standard {@link GuiGraphicsExtractor#item} path, which keeps it safe
 * under both the OpenGL and Vulkan backends. State is re-evaluated from slot-update events via
 * {@link #refreshState()} (FR-04/NFR-02), not by polling every frame.
 */
public class StripButton extends AbstractButton {
	public static final int SIZE = 16;
	/** Client-side debounce window between accepted clicks, in milliseconds (NFR-05). */
	private static final long DEBOUNCE_MS = 400;
	/** Shown, dimmed, when no usable axe is available so the button still reads as "strip". */
	private static final ItemStack FALLBACK_AXE = new ItemStack(Items.IRON_AXE);

	private final LocalPlayer player;
	private long lastClickMillis;
	/** The axe icon currently drawn on the button; never empty (falls back to a generic axe). */
	private ItemStack iconStack = FALLBACK_AXE;

	public StripButton(int x, int y, LocalPlayer player) {
		super(x, y, SIZE, SIZE, Component.translatable("logstripper.button.narration"));
		this.player = player;
		refreshState();
	}

	@Override
	public void onPress(InputWithModifiers input) {
		sendStripRequest();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();
		// Subtle highlight behind the icon on hover, only when the button can actually act.
		if (isHovered() && this.active) {
			extractor.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0x40FFFFFF);
		}
		extractor.item(iconStack, x, y);
		// Dim the icon when there is no axe or nothing to strip.
		if (!this.active) {
			extractor.fill(x, y, x + SIZE, y + SIZE, 0x99000000);
		}
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

	/** Recomputes active/inactive state, the axe icon, and the preview tooltip from the local mirror. */
	public void refreshState() {
		LogStripperConfig config = LogStripperConfig.get();
		LogStripHelper.AxeRef axe = LogStripHelper.findAxe(player, config.axeSelectionStrategy);
		int logs = LogStripHelper.countStrippableLogs(player.getInventory());

		this.iconStack = axe != null ? axe.stack() : FALLBACK_AXE;
		this.active = axe != null && logs > 0;

		if (!config.showTooltipPreview) {
			setTooltip(null);
			return;
		}

		if (axe == null) {
			setTooltip(Tooltip.create(Component.translatable("logstripper.tooltip.no_axe")));
			return;
		}
		if (logs == 0) {
			setTooltip(Tooltip.create(Component.translatable("logstripper.tooltip.no_logs")));
			return;
		}

		// Target axe, then one row per log type in the order they will be stripped.
		MutableComponent tooltip = Component.translatable("logstripper.tooltip.title")
				.append(Component.literal("\n"))
				.append(Component.translatable("logstripper.tooltip.axe", axe.stack().getItemName()));
		for (LogStripHelper.LogGroup group : LogStripHelper.strippableBreakdown(player.getInventory())) {
			ItemStack representative = new ItemStack(group.item());
			tooltip.append(Component.literal("\n"))
					.append(Component.translatable("logstripper.tooltip.log_row",
							representative.getItemName(),
							Component.literal(formatCount(group.count(), representative.getMaxStackSize()))));
		}
		setTooltip(Tooltip.create(tooltip));
	}

	/** Formats a count as a stack breakdown, e.g. 178 with stack size 64 -> "2 * 64 + 50 (178)". */
	private static String formatCount(int count, int maxStackSize) {
		int fullStacks = count / maxStackSize;
		int remainder = count % maxStackSize;
		if (fullStacks == 0) {
			return String.valueOf(remainder);
		}
		StringBuilder sb = new StringBuilder();
		sb.append(fullStacks).append(" * ").append(maxStackSize);
		if (remainder > 0) {
			sb.append(" + ").append(remainder);
		}
		return sb.append(" (").append(count).append(")").toString();
	}
}
