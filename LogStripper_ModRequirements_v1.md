# LogStripper
### Fabric Mod — Software Requirements Specification
**Version 1.0 · July 8, 2026 · Minecraft Java Edition 26.2**

## 1. Overview

LogStripper is a Fabric mod for Minecraft Java Edition 26.2 that adds a bulk axe-stripping action to the player
inventory screen. Vanilla log stripping requires placing each log in the world, right-clicking it with an axe, then
re-mining the result — a slow, tedious process for large quantities. LogStripper adds a single icon/button to the
inventory UI that, when clicked, strips every eligible log stack currently in the player's inventory using the
durability of an equipped axe, deducting durability exactly as if each log had been stripped individually in the
world (including Unbreaking's damage-avoidance chance).

The mod is intended for singleplayer use. Because inventory mutation must remain authoritative on the server
(the integrated singleplayer server, in this case), the action is implemented as a client-triggered, server-resolved
operation rather than a purely client-side one.

## 2. Scope & Target Environment

| | |
|---|---|
| **Minecraft** | Java Edition 26.2 |
| **Fabric Loader** | 0.19.3 or newer |
| **Fabric API** | 0.154.2+26.2 |
| **JDK** | Java 25 (required by MC 26.1.x and later for compilation and the Gradle toolchain) |
| **Gradle** | 9.5.1+ on PATH for initial wrapper generation; `gradlew` self-contained afterward |
| **Loom** | Fabric Loom 1.17 |
| **Mappings** | Official Mojang mappings. Yarn mappings are not published for Minecraft 26.1 and later — Loom no longer remaps Minecraft or mods, so all class/method names in this spec must be verified against the official (unmapped) names for 26.2, not legacy Yarn names. |
| **IDE** | IntelliJ IDEA 2025.3+ recommended for correct mixin resolution |
| **Side** | Client + common. The button and its rendering are client-only, but the actual inventory mutation runs on the logical server (integrated server in singleplayer), so a minimal server-side component is required. |
| **Target use case** | Singleplayer worlds. Dedicated-server / multiplayer support is not a goal of v1.0 (see Section 10). |
| **Optional Dep.** | Mod Menu, for configuration UI |

## 3. Project Structure & Build Setup

### 3.1 Source Sets

Standard Fabric Loom client/common split:

- **`common`** — the strippable-item registry, the conversion/durability logic (`LogStripHelper`), and the
  custom payload record and its server-side handler. All of this must be able to run without any client classes
  present, since it executes on the logical server.
- **`client`** — the `HandledScreen` mixin that injects the button, the button widget class itself, tooltip
  rendering, and the code that decides button visibility/state each frame.

### 3.2 Access Widener

Only add entries if a compile-time access error is actually hit — do not pre-emptively widen classes. Likely
candidates to check first:

- `HandledScreen` — the field holding the widget/button list, if not already exposed.
- `PlayerInventory` — direct slot-list access, if the iteration helper needs it instead of going through the
  existing public accessors.

### 3.3 Build Commands (Windows / macOS / Linux)

```
# 1. Unzip and enter the project folder.
cd log-stripper

# 2. Confirm JDK 25 is active.
java -version   # must report 25

# 3. Generate the Gradle wrapper (once only).
gradle wrapper --gradle-version 9.5.1

# 4. Build.
gradlew.bat build   # Windows
./gradlew build     # macOS / Linux

# Output:
# build/libs/log-stripper-1.0.0.jar          <- install this
# build/libs/log-stripper-1.0.0-sources.jar  <- ignore
```

On subsequent rebuilds after source changes, step 3 is not needed — run `gradlew build` directly.

### 3.4 Install

- Install Fabric Loader 0.19.3+ for Minecraft 26.2 via the Fabric installer.
- Place Fabric API 0.154.2+26.2 in `.minecraft/mods/`.
- Place `build/libs/log-stripper-1.0.0.jar` in `.minecraft/mods/`.
- Launch the `fabric-loader-26.2` profile. No server-side install is needed for singleplayer, since the mod's
  server-side handler runs inside the integrated server automatically.

## 4. Functional Requirements

### 4.1 Button Rendering & State

| Req. ID | Requirement |
|---|---|
| FR-01 | The mod SHALL inject a single icon/button widget into the player inventory screen (`InventoryScreen`) and into any `HandledScreen` subtype that renders the player's own inventory grid (e.g. chest, shulker box, barrel screens), positioned so it does not overlap existing vanilla widgets. |
| FR-02 | The button SHALL render in one of three visual states: **inactive/greyed** (no eligible axe or no strippable logs present), **active** (an eligible axe and at least one strippable log stack are both present), and **hovered** (active state plus a tooltip). |
| FR-03 | On hover while active, the button SHALL display a tooltip stating which axe will be used, its remaining durability, and the number of logs it will strip in this operation (`min(remaining durability, total strippable logs)`), matching Section 4.3's outcome. |
| FR-04 | The button SHALL re-evaluate its state every time the screen's inventory contents change (slot update), not only on screen open, so it reflects mid-session changes (e.g. picking up an axe from a chest). |

### 4.2 Axe Selection

| Req. ID | Requirement |
|---|---|
| FR-05 | The mod SHALL select the axe to use in this priority order: (1) main hand, (2) off hand, (3) first axe found scanning the hotbar in slot order, (4) first axe found scanning the main inventory in slot order. |
| FR-06 | Only one axe SHALL be used per click. Combining durability across multiple axes in a single operation is out of scope for v1.0 (see Section 10). |
| FR-07 | An axe with 0 remaining durability, or no axe present anywhere in the accessible inventory, SHALL result in the inactive/greyed button state (FR-02). |

### 4.3 Strip Operation

| Req. ID | Requirement |
|---|---|
| FR-08 | On click, the client SHALL send a custom C2S payload requesting a strip operation. The payload SHALL carry no inventory data — the server independently re-derives the axe and target stacks from that player's actual inventory state, to avoid any client-authoritative outcome. |
| FR-09 | The server SHALL scan the player's hotbar and main inventory (slots 0–35) in that order for item stacks whose item is a registered strippable log (see FR-13), skipping the off-hand and armor slots as strip targets. |
| FR-10 | The server SHALL convert individual logs to their stripped equivalent one at a time, processing stacks in the scan order from FR-09, until either the axe reaches 0 durability or no strippable logs remain. |
| FR-11 | Each individual log conversion SHALL cost exactly 1 durability point on the axe, subject to the same Unbreaking damage-avoidance roll vanilla uses for tool damage — i.e. the mod SHALL NOT unconditionally decrement durability by the count of logs converted; it must roll per-use exactly as right-clicking a placed log would. |
| FR-12 | If a stack has more logs than the axe has remaining durability-uses for, the stack SHALL be split: the convertible portion becomes a stripped-log stack (merged into an existing stripped stack of the same type if one exists and has room), and the remainder stays as unstripped logs in place. |
| FR-13 | The strippable-log mapping (log → stripped log) SHALL be built at mod init from vanilla's known pairs and SHALL be extensible so other mods' logs can register into the same mapping, mirroring the block-level mapping vanilla `AxeItem` already uses. |
| FR-14 | If the axe reaches 0 durability during the operation, it SHALL break (fire the normal item-break event, sound, and removal from the slot) exactly as vanilla tool breakage does, and the operation SHALL stop at that point. |
| FR-15 | If the axe has the `Unbreakable` component, or the player is in creative mode, no durability SHALL be deducted and the operation SHALL convert all eligible logs in one pass. |
| FR-16 | On completion, the server SHALL play the vanilla axe-strip sound (`ITEM_AXE_STRIP`) exactly once for the operation, regardless of how many individual logs were converted. |
| FR-17 | Updated slot contents (log stacks, stripped-log stacks, axe durability/removal) SHALL reach the client via standard `ScreenHandler` slot-update packets — no custom response payload is required for state sync. |

## 5. Non-Functional Requirements

### 5.1 Performance

| Req. ID | Requirement |
|---|---|
| NFR-01 | The inventory scan and strip operation SHALL complete in under 5 ms on typical hardware for a full 36-slot inventory of maximum stack sizes. No async threading is required. |
| NFR-02 | The button's per-frame state check (FR-04) SHALL add negligible overhead to screen rendering and SHALL NOT re-scan the full inventory every frame — it should react to slot-update events rather than polling. |

### 5.2 Reliability & Safety

| Req. ID | Requirement |
|---|---|
| NFR-03 | If any exception occurs during scanning or conversion, the server SHALL catch it, log a warning, leave the inventory in its last-known-consistent state, and abort the operation — never leaving partially-converted, duplicated, or destroyed items. |
| NFR-04 | The mod SHALL NOT modify any slot not directly involved in the strip operation — no reorganizing or compacting as a side effect. |
| NFR-05 | A single button click SHALL result in at most one strip operation. Rapid repeated clicks SHALL be debounced client-side and SHALL NOT queue multiple server-side operations. |

### 5.3 Compatibility

| Req. ID | Requirement |
|---|---|
| NFR-06 | The mod SHALL function in both survival and creative game modes. |
| NFR-07 | Because the strip operation uses a custom payload handled server-side (FR-08), it requires the mod to be present wherever the logical server runs. For singleplayer this is automatic (integrated server); dedicated multiplayer servers would need the mod installed server-side, which is out of scope for v1.0 (Section 10). |
| NFR-08 | The mod SHALL be compatible with other inventory-management mods that add their own `HandledScreen` widgets, provided they do not occupy the same screen-space region (FR-01). |

### 5.4 Maintainability

| Req. ID | Requirement |
|---|---|
| NFR-09 | All mixins SHALL be annotated with Javadoc explaining what is being intercepted and why. |
| NFR-10 | The strip/conversion logic SHALL be isolated in a dedicated, non-mixin utility class (`LogStripHelper`) in the `common` source set, decoupled from rendering and networking code, to allow unit testing without a running game instance. |
| NFR-11 | The strippable-log registry (FR-13) SHALL be isolated from the conversion logic so other mods can register additional pairs via a public API without touching `LogStripHelper` internals. |

## 6. Configuration Options (`logstripper.toml`)

| Option Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | Boolean | `true` | Master toggle — disables the button and all mod behavior when false. |
| `axe_selection_strategy` | Enum | `MAIN_HAND_FIRST` | `MAIN_HAND_FIRST` \| `HIGHEST_DURABILITY` — controls which axe is chosen when more than one is available (FR-05). |
| `show_tooltip_preview` | Boolean | `true` | Show the pre-click outcome tooltip (FR-03). |
| `play_sound_on_complete` | Boolean | `true` | Play the axe-strip sound once per operation (FR-16). |
| `highlight_changed_slots` | Boolean | `true` | Briefly flash slots that changed as a result of the operation. |
| `highlight_duration_ticks` | Integer | `20` | How long the slot-flash effect lasts, in game ticks. Range: 5–100. |
| `debug_logging` | Boolean | `false` | Write verbose scan/conversion diagnostics to the Fabric log. Development use only. |

## 7. Mixin & Networking Architecture (Implementation Guidance)

Class and method names below use official Mojang mappings and must be verified against the 26.2 mappings at
build time.

### 7.1 Button Injection Mixin

- **Target class:** `net.minecraft.client.gui.screen.ingame.HandledScreen` (covers `InventoryScreen` and
  container screens that share the player inventory grid)
- **Target method:** `init()`
- **Injection point:** `@Inject` at `TAIL` — after vanilla has laid out its own widgets, so the button's position
  can be computed relative to the existing grid.
- **Logic:** Instantiate and register the button widget via `addDrawableChild`, wired to a click handler that
  sends the strip-request payload. Subscribe the button to inventory slot-update events for FR-04.
- **Mixin constraint:** Target only methods declared on `HandledScreen` itself, not subclass-specific overrides,
  so the same injection covers all qualifying container screens without per-screen mixins.

### 7.2 Networking

- Register a `CustomPayload` (e.g. `StripRequestPayload`, no fields required per FR-08) via
  `PayloadTypeRegistry.playC2S()`.
- Register a `ServerPlayNetworking` receiver for that payload that invokes `LogStripHelper.stripInventory(
  ServerPlayerEntity player)`.
- `LogStripHelper.stripInventory` performs FR-09 through FR-16 and returns a result summary (logs converted,
  axe broken y/n) that can optionally be used for a HUD/log message — it does not need to be sent back to the
  client as a separate payload, since slot updates already convey the outcome (FR-17).

### 7.3 LogStripHelper (Non-Mixin Utility)

Encapsulates all scanning, conversion, and durability logic — no rendering or network dependencies:

- `stripInventory(ServerPlayerEntity player) → StripResult`
- Iterates slots 0–35 via `PlayerInventory`, locating stacks whose item is present in the strippable-log
  registry (FR-13).
- Resolves the axe per FR-05/FR-06.
- Converts logs one at a time, calling the vanilla per-use damage/Unbreaking-roll method on the axe stack for
  each conversion (FR-11).
- Handles stack splitting and merging (FR-12).
- Stops on 0 durability or exhausted supply; triggers axe breakage (FR-14) if applicable.
- Pure Java/common-code class — unit-testable without a running game instance (NFR-10).

## 8. Server Synchronization

| Req. ID | Requirement |
|---|---|
| SYNC-01 | The strip operation SHALL be resolved entirely server-side; the client never mutates inventory state directly in response to the button click. |
| SYNC-02 | All resulting slot changes SHALL propagate to the client via the same `ScreenHandler` slot-sync mechanism vanilla already uses for any other server-driven inventory change — no bespoke sync packet is needed. |
| SYNC-03 | The client SHALL treat the operation as fire-and-forget after sending the request; it SHALL NOT predict or pre-apply the outcome locally, to avoid divergence if the server-side scan produces a different result than the client's tooltip preview (e.g. due to a race with another concurrent inventory change). |

## 9. Suggested Mod Metadata (`fabric.mod.json`)

```json
{
  "schemaVersion": 1,
  "id": "logstripper",
  "version": "1.0.0",
  "name": "LogStripper",
  "description": "Bulk-strip logs directly from your inventory using an equipped axe's durability.",
  "authors": ["<Your Name>"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": ["com.yourname.logstripper.LogStripper"],
    "client": ["com.yourname.logstripper.LogStripperClient"]
  },
  "mixins": ["logstripper.mixins.json"],
  "depends": {
    "fabricloader": ">=0.19.3",
    "fabric-api": ">=0.154.2",
    "minecraft": "~26.2"
  },
  "suggests": {
    "modmenu": "*"
  }
}
```

## 10. Out of Scope

- Combining durability across multiple axes in a single operation.
- Dedicated / third-party multiplayer server support (v1.0 targets singleplayer only, per NFR-07).
- Stripping logs held in shulker boxes or other containers without first moving them into the direct
  hotbar/main-inventory slots.
- A standalone screen for browsing or reordering the strip queue before confirming.
- Support for modded axes that do not implement vanilla's standard tool-damage/Unbreaking pathway.
- Forge, NeoForge, Quilt, or other mod loaders.

## 11. Acceptance Criteria

All scenarios below must pass in an MC 26.2 client with Fabric Loader 0.19.3+ and Fabric API 0.154.2+26.2
installed before the mod is considered complete.

| TC ID | Name | Setup | Expected Result |
|---|---|---|---|
| TC-01 | Button inactive — no axe | No axe anywhere in inventory. Open inventory screen. | Button renders greyed out; click has no effect. |
| TC-02 | Button inactive — no logs | Axe in hand, no strippable logs in inventory. | Button renders greyed out. |
| TC-03 | Full-stack strip | Axe with ample durability; one stack of 10 oak logs. Click button. | All 10 become stripped oak logs; axe durability reduced (minus any Unbreaking saves). |
| TC-04 | Partial-stack split | Axe with 4 durability-uses remaining (Unbreaking-adjusted expected value); stack of 10 logs. Click button. | Approximately 4 logs converted, remainder stay as unstripped logs in the same/adjacent slot; axe at 0 durability or broken. |
| TC-05 | Axe breaks mid-operation | Axe with 1 durability remaining, no Unbreaking; multiple log stacks present. Click button. | Exactly 1 log converted; axe breaks (removed from slot, break sound plays); operation halts. |
| TC-06 | Multiple stack types, slot order | Two different log types in separate slots, limited durability. Click button. | Earlier-slot stack is processed first per FR-09/FR-10. |
| TC-07 | Stripped-stack merge | Player already has a partial stack of stripped oak logs elsewhere in inventory. Click button on unstripped oak logs. | Newly stripped logs merge into the existing stack up to max stack size before creating a new stack. |
| TC-08 | Off-hand axe fallback | No axe in main hand or hotbar; axe present in off-hand. Click button. | Off-hand axe is used per FR-05 priority. |
| TC-09 | Creative / unbreakable axe | Axe has the Unbreakable component, or player is in creative mode. Click button. | All eligible logs converted; axe durability unchanged. |
| TC-10 | Tooltip accuracy | Hover button before clicking. | Displayed axe, durability, and log count match the actual post-click outcome (barring Unbreaking's inherent randomness). |
| TC-11 | Works in chest screen | Open a chest (player inventory grid visible at bottom). Click button. | Operates identically to the standalone inventory screen. |
| TC-12 | Debounce | Rapidly double-click the button. | Only one strip operation executes; no double-conversion or duplication. |

## 12. Risks & Mitigations

| Risk | Level | Description | Mitigation |
|---|---|---|---|
| Official-mappings churn | MEDIUM | 26.2 uses official Mojang mappings with no Yarn equivalent published; class/method names may shift between 26.2 patch releases. | Verify all mixin targets against the exact 26.2 mappings at build time; pin the tested Minecraft/Loom version combination in the README. |
| Vulkan/OpenGL backend transition | LOW | 26.2 allows switching rendering backends; custom widget rendering that bypasses the Blaze3D API could break under Vulkan. | Render the button exclusively through standard `DrawContext`/widget APIs, never raw OpenGL calls. |
| Unbreaking roll mismatch | MEDIUM | If the mod's durability-deduction call doesn't exactly match vanilla's internal tool-damage method, expected uses-per-durability-point could drift from player expectations. | Reuse vanilla's existing per-use damage/Unbreaking method directly rather than reimplementing the probability math. |
| Server requirement misunderstanding | LOW | Because the operation is payload-based (Section 7.2/8), players might assume it works on any vanilla server; it will not without the mod installed server-side. | State singleplayer-only scope clearly in the mod description and README (NFR-07). |
| Third-party log compatibility | LOW | Modded logs not registered into the strippable-log mapping (FR-13) will be silently ignored. | Expose a public registration API and document it for other mod authors. |

---
*LogStripper · Software Requirements Specification · v1.0 · Minecraft 26.2 / Fabric Loader 0.19.3*
