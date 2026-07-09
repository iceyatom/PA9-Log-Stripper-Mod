package com.iceyatom.logstripper.client;

import com.iceyatom.logstripper.LogStripperConfig;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side bookkeeping for the optional slot-flash effect (config
 * {@code highlight_changed_slots}). After a strip request is sent, slot updates arriving
 * within a short window are recorded here and rendered as a brief fading highlight.
 */
public final class StripFlashTracker {
	/** How long after a click incoming slot changes count as strip results. */
	private static final long WATCH_WINDOW_MS = 2000;

	private static long watchUntilMillis;
	/** menu slot index -> flash expiry timestamp (ms). */
	private static final Map<Integer, Long> FLASHES = new ConcurrentHashMap<>();

	public static void onStripRequested() {
		if (LogStripperConfig.get().highlightChangedSlots) {
			watchUntilMillis = System.currentTimeMillis() + WATCH_WINDOW_MS;
		}
	}

	public static void onSlotChanged(int menuSlotIndex) {
		long now = System.currentTimeMillis();
		if (now < watchUntilMillis) {
			FLASHES.put(menuSlotIndex, now + LogStripperConfig.get().highlightDurationTicks * 50L);
		}
	}

	/** @return flash alpha 0..1 for the slot, or 0 if it is not flashing. */
	public static float flashAlpha(int menuSlotIndex) {
		Long expiry = FLASHES.get(menuSlotIndex);
		if (expiry == null) {
			return 0.0F;
		}
		long now = System.currentTimeMillis();
		if (now >= expiry) {
			FLASHES.remove(menuSlotIndex);
			return 0.0F;
		}
		long duration = LogStripperConfig.get().highlightDurationTicks * 50L;
		return (expiry - now) / (float) duration;
	}

	public static boolean hasFlashes() {
		if (FLASHES.isEmpty()) {
			return false;
		}
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Integer, Long>> it = FLASHES.entrySet().iterator(); it.hasNext(); ) {
			if (now >= it.next().getValue()) {
				it.remove();
			}
		}
		return !FLASHES.isEmpty();
	}

	public static void clear() {
		FLASHES.clear();
		watchUntilMillis = 0;
	}

	private StripFlashTracker() {
	}
}
