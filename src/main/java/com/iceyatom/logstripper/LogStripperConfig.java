package com.iceyatom.logstripper;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Loads {@code config/logstripper.toml}. The file only uses simple {@code key = value}
 * pairs, so it is parsed by hand rather than pulling in a TOML library.
 */
public final class LogStripperConfig {
	public enum AxeSelectionStrategy {
		MAIN_HAND_FIRST,
		HIGHEST_DURABILITY
	}

	public boolean enabled = true;
	public AxeSelectionStrategy axeSelectionStrategy = AxeSelectionStrategy.MAIN_HAND_FIRST;
	public boolean preserveAxe = true;
	public boolean showTooltipPreview = true;
	public boolean playSoundOnComplete = true;
	public boolean highlightChangedSlots = true;
	public int highlightDurationTicks = 20;
	public boolean debugLogging = false;

	private static final LogStripperConfig INSTANCE = new LogStripperConfig();

	public static LogStripperConfig get() {
		return INSTANCE;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("logstripper.toml");
		if (!Files.exists(path)) {
			INSTANCE.writeDefault(path);
			return;
		}
		try {
			for (String line : Files.readAllLines(path)) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
					continue;
				}
				int eq = trimmed.indexOf('=');
				if (eq < 0) {
					continue;
				}
				String key = trimmed.substring(0, eq).trim();
				String value = trimmed.substring(eq + 1).trim();
				int comment = value.indexOf('#');
				if (comment >= 0) {
					value = value.substring(0, comment).trim();
				}
				value = value.replace("\"", "").replace("'", "");
				INSTANCE.apply(key, value);
			}
		} catch (IOException e) {
			LogStripper.LOGGER.warn("Could not read logstripper.toml, using defaults", e);
		}
		INSTANCE.highlightDurationTicks = Math.max(5, Math.min(100, INSTANCE.highlightDurationTicks));
	}

	private void apply(String key, String value) {
		try {
			switch (key) {
				case "enabled" -> enabled = Boolean.parseBoolean(value);
				case "axe_selection_strategy" ->
						axeSelectionStrategy = AxeSelectionStrategy.valueOf(value.toUpperCase(Locale.ROOT));
				case "preserve_axe" -> preserveAxe = Boolean.parseBoolean(value);
				case "show_tooltip_preview" -> showTooltipPreview = Boolean.parseBoolean(value);
				case "play_sound_on_complete" -> playSoundOnComplete = Boolean.parseBoolean(value);
				case "highlight_changed_slots" -> highlightChangedSlots = Boolean.parseBoolean(value);
				case "highlight_duration_ticks" -> highlightDurationTicks = Integer.parseInt(value);
				case "debug_logging" -> debugLogging = Boolean.parseBoolean(value);
				default -> LogStripper.LOGGER.warn("Unknown logstripper.toml option: {}", key);
			}
		} catch (IllegalArgumentException e) {
			LogStripper.LOGGER.warn("Invalid value '{}' for logstripper.toml option '{}'", value, key);
		}
	}

	private void writeDefault(Path path) {
		List<String> lines = List.of(
				"# LogStripper configuration",
				"",
				"# Master toggle - disables the button and all mod behavior when false.",
				"enabled = true",
				"",
				"# MAIN_HAND_FIRST | HIGHEST_DURABILITY - which axe is chosen when more than one is available.",
				"axe_selection_strategy = \"MAIN_HAND_FIRST\"",
				"",
				"# Stop before spending the axe's final durability point so it is never broken by stripping.",
				"preserve_axe = true",
				"",
				"# Show the pre-click outcome tooltip.",
				"show_tooltip_preview = true",
				"",
				"# Play the axe-strip sound once per operation.",
				"play_sound_on_complete = true",
				"",
				"# Briefly flash slots that changed as a result of the operation.",
				"highlight_changed_slots = true",
				"",
				"# How long the slot-flash effect lasts, in game ticks. Range: 5-100.",
				"highlight_duration_ticks = 20",
				"",
				"# Write verbose scan/conversion diagnostics to the Fabric log. Development use only.",
				"debug_logging = false"
		);
		try {
			Files.write(path, lines);
		} catch (IOException e) {
			LogStripper.LOGGER.warn("Could not write default logstripper.toml", e);
		}
	}

	private LogStripperConfig() {
	}
}
