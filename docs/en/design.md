# Design

## Goal

A ControlFlex × Exposure bridge mod with exactly one compatibility point: when the Exposure viewfinder overlay enters/leaves the foreground, notify ControlFlex through its interactive-context API (`IInteractiveContextRegistrar`) so ControlFlex switches stick behavior while the player is looking through the camera. Nothing else — no compat JSON, no guide, no plugin SPI.

## Platforms

| Subproject | MC | Loader | Exposure (compile) | ControlFlex |
|------------|----|--------|----------------------------|-------------|
| `forge` | 1.20.1 | Forge 47.4.4 | 1.9.21 (forge) | 0.8.7+ |
| `fabric` | 1.20.1 | Fabric Loader 0.15.11 + Fabric API 0.92.2 | 1.9.20 (fabric) | 0.8.7+ |
| `neoforge` | 1.21.1 | NeoForge 21.1.209 | 1.9.18 (neoforge) | 0.8.6.3+ |

> Note: Exposure 1.9.21 has no 1.20.1 Fabric release, so the Fabric side compiles against 1.9.20 (the latest of that platform); at runtime both 1.20.1 loaders accept Exposure ≥ 1.9.20. 1.21.1 Fabric is planned, not built yet.

All loaders share the same loader-agnostic Java sources (`common/src/main/java`); platform differences live only in build scripts and metadata (mods.toml / neoforge.mods.toml / fabric.mod.json, mixins.json `compatibilityLevel`, pack.mcmeta `pack_format`).

## Why these two methods

In Exposure 1.9.x (identical structure on 1.20.1 and 1.21.1) the viewfinder lifecycle funnels through two static methods of `CameraClient`:

- `CameraClient.setupViewfinder(Camera)` — viewfinder created (enters foreground). Every open path (camera activation, selfie switch) reaches it via `LocalPlayer.setActiveExposureCamera`.
- `CameraClient.removeViewfinder()` — viewfinder closed (returns to background). Every exit path (looking down, ESC/inventory via `deactivate`, per-tick camera-inactive check, logout) funnels through it.

These were chosen over `Viewfinder`/`ViewfinderOverlay` constructors or render: setup/remove are the single chokepoint for all enter/leave paths and are not called per-frame, so notifications are exact and never duplicated.

## Architecture

```
Exposure (mixin targets)
  CameraClient.setupViewfinder ──RETURN──> [Mixin] onViewfinderSetup()
  CameraClient.removeViewfinder  ──RETURN──> [Mixin] onViewfinderRemove()
                         │                          │
                         ▼                          ▼
              CfxExposureContextBridge (pairing flag + null checks)
                         │  ControlFlexApi.getInteractiveContextRegistrar()
                         ▼
              ControlFlex InteractiveContextRegistry
                notifyOverlayForeground("...ViewfinderOverlay") / notifyOverlayBackground(...)
```

## Components

- **`CfxExposureContextBridge`** (common): static bridge holding the `viewfinderForeground` pairing flag; every API call null-checks (returns null when ControlFlex is missing/not ready); `onWorldExit()` emits a final background notification and resets the flag on logout.
- **`CameraClientViewfinderMixin`** (common): `@Inject` at `RETURN` of both methods; `remap = false` (Exposure is a third-party class, no vanilla mapping needed). Target: `io.github.mortuusars.exposure.client.camera.CameraClient`.
- **`CfxExposureMod`** (one per loader): `@Mod` entry; verifies mixin target classes load at client setup; subscribes to the logout event → `bridge.onWorldExit()`.
  - Forge: `@Mod(id)` + `FMLJavaModLoadingContext` + `MinecraftForge.EVENT_BUS` + `ClientPlayerNetworkEvent.LoggingOut`
  - NeoForge: `@Mod(value, dist = CLIENT)` + `IEventBus` + `NeoForge.EVENT_BUS` + `neoforge...ClientPlayerNetworkEvent.LoggingOut`

## Pairing rules

| Scenario | Sequence |
|----------|----------|
| Raise the camera | `notifyOverlayForeground` |
| Lower it / ESC / deactivate | `notifyOverlayBackground` |
| Switch cameras (setupViewfinder calls removeViewfinder first) | `notifyOverlayBackground` → `notifyOverlayForeground` |
| removeViewfinder with no active viewfinder | no notification (flag guard) |
| Logout while viewfinder is still active | one extra `notifyOverlayBackground`, flag reset |

ControlFlex also auto-clears interactive contexts on phase exit, so the logout fallback is belt-and-braces.

## Context identifier

The `className` argument passed to `notifyOverlayForeground/notifyOverlayBackground` is the **runtime-resolved** overlay class name — taken from `overlay().getClass().getName()` on Exposure's active `Viewfinder` instance, not from a hard-coded string (robust against obfuscation/remapping differences and custom viewfinders registered via ViewfinderRegistry); it falls back to the default class name only if no instance is available:

```
io.github.mortuusars.exposure.client.camera.viewfinder.ViewfinderOverlay
```

This is also the key ControlFlex compat configs use to locate the interactive context. The paired background notification reuses the same className (tracked by the bridge).

## Dependencies

- ControlFlex API 0.8.7: JitPack (`com.github.ControlFlexMC:control-flex-api:0.8.7`), compileOnly — the API part is plain Java, loader-agnostic. Note: 0.8.7 renamed `IInteractiveContextRegistrar` methods from `notifyForeground`/`notifyBackground` to `notifyOverlayForeground`/`notifyOverlayBackground`, which is why the runtime requirement is ControlFlex ≥ 0.8.7.
- Exposure: CurseForge Maven (`curse.maven:exposure-871755:<fileId>`), compileOnly — same approach as cfx-compat-epicfight uses for Epic Fight.
- Runtime metadata: 1.20.1 (forge + fabric) requires `controlflex [0.8.7,)` and `exposure [1.9.20,)`; neoforge keeps `controlflex [0.8.6.3,)` / `exposure [1.9.18,)` — both required, CLIENT.

## Build

Mirrors cfx-compat-epicfight's per-loaders configuration:

- `forge/`: `net.neoforged.moddev.legacyforge` 2.0.141 + `mixin {}` DSL + refmap + `MixinConfigs` manifest attribute (required by Forge 1.20.1) + `reobfJar`.
- `fabric/`: `fabric-loom` 1.6-SNAPSHOT (same as ControlFlex) + mojmap + `loom.mixin.defaultRefmapName`; `fabric.mod.json` placeholders expanded via processResources.
- `neoforge/`: `net.neoforged.moddev` 2.0.141 with only the Mixin annotation processor (moddev provides no mixin DSL there; every mixin is `remap=false`, so no refmap is needed); the mixin config is declared via `[[mixins]]` in neoforge.mods.toml.

## Verification

- `./gradlew :forge:build :neoforge:build` produces both JARs.
- JAR content checks: mixins.json / mods.toml placeholders expanded, `MixinConfigs` manifest attribute on the Forge side, classes present.
- Runtime: client log shows `Exposure viewfinder bridge active (mixin verification passed)`; raising/lowering the camera switches ControlFlex behavior (manual test).
