# LogStripper

Bulk-strip logs directly from your inventory using an equipped axe's durability.

A small icon button appears in the top-right of your inventory (and chest/barrel/shulker
screens). When you have a usable axe and strippable logs, click it: every eligible log in
your hotbar and main inventory is converted to its stripped variant, costing 1 axe
durability per log with vanilla's normal Unbreaking damage-avoidance rolls. Hovering the
button previews which axe will be used, its remaining durability, and how many logs will be
stripped.

**Singleplayer-focused.** The operation is resolved on the logical server, so it works
out of the box in singleplayer (integrated server). On a dedicated multiplayer server the
mod would need to be installed server-side as well — not a supported v1.0 scenario.

## Tested versions (pinned)

| | |
|---|---|
| Minecraft | Java Edition 26.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.154.2+26.2 |
| Fabric Loom | 1.17-SNAPSHOT |
| JDK | 25 (Temurin 25.0.3 tested) |
| Gradle wrapper | 9.5.1 |

Official Mojang mappings (no Yarn for 26.x). Class names were verified against the 26.2
jars at build time; patch releases of 26.2 may shift names.

## Build

```
cd log-stripper
java -version        # must report 25
gradlew.bat build    # Windows       (./gradlew build on macOS/Linux)
```

Output: `build/libs/logstripper - 26.2 - 1.0.0.jar`

(The Gradle wrapper is already generated in this repo. If regenerating it from scratch,
note that Loom 1.17 requires Gradle 9.5+.)

## Install

1. Install Fabric Loader 0.19.3+ for Minecraft 26.2 via the Fabric installer.
2. Put `fabric-api-0.154.2+26.2.jar` in `.minecraft/mods/`.
3. Put `logstripper - 26.2 - 1.0.0.jar` in `.minecraft/mods/`.
4. Launch the `fabric-loader-26.2` profile.

## Behavior details

- **Axe selection**: main hand → off hand → hotbar in slot order → main inventory in slot
  order (configurable to highest-durability-first).
- **Processing order**: hotbar slots 0-8, then main inventory 9-35. Off-hand and armor are
  never strip targets.
- Stripped logs merge into existing stripped stacks first, then fill empty slots. If the
  inventory is completely full, surplus drops at your feet (never destroyed).
- The axe breaks exactly like vanilla (break sound/animation) if it runs out mid-operation,
  and the operation stops there.
- Creative mode or an Unbreakable axe strips everything at no durability cost.
- One axe-strip sound plays per operation; changed slots flash briefly (both configurable).

## Configuration — `config/logstripper.toml`

Generated with defaults on first launch:

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Master toggle — hides the button and disables all behavior. |
| `axe_selection_strategy` | `MAIN_HAND_FIRST` | Or `HIGHEST_DURABILITY`. |
| `show_tooltip_preview` | `true` | Hover tooltip with axe/durability/log-count preview. |
| `play_sound_on_complete` | `true` | One axe-strip sound per operation. |
| `highlight_changed_slots` | `true` | Brief flash on slots changed by the operation. |
| `highlight_duration_ticks` | `20` | Flash length in ticks (5–100). |
| `debug_logging` | `false` | Verbose scan/conversion diagnostics in the log. |

## API for other mods

Vanilla's `AxeItem` block-strip map is read automatically at init, so logs registered there
by other mods are picked up for free. Mods whose logs aren't in that map can register the
item pair directly during common init:

```java
com.iceyatom.logstripper.StrippableRegistry.register(MY_LOG_ITEM, MY_STRIPPED_LOG_ITEM);
```

## License

MIT
