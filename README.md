# cfx-compat-exposure

ControlFlex ↔ Exposure bridge mod — notifies ControlFlex when the Exposure camera viewfinder overlay enters/leaves the foreground, so stick behavior switches correctly while playing the camera.

[Chinese](README_ZH.md)

> This branch (`1.20.1`) targets **Minecraft 1.20.1 (Forge + Fabric)**. The Minecraft 1.21.1 flavors (Fabric + NeoForge) live on the `1.21.1` branch.

## Why this mod?

Exposure's viewfinder overlay is an interactive overlay that takes over camera controls and hides most of the vanilla HUD. ControlFlex only switches stick (right-stick/GUI) behavior for contexts it knows about — unless something tells it.

Instead of relying on screen detection, this bridge uses the **interactive-context API** of ControlFlex (`IInteractiveContextRegistrar`):

- `notifyOverlayForeground(...)` when the viewfinder overlay opens (player raises the camera to look through it)
- `notifyOverlayBackground(...)` when it closes (looking down, ESC/inventory, camera deactivation, logout)

## What this mod does

| Feature | Implementation |
|---------|----------------|
| **Viewfinder overlay foreground/background** | Mixin into Exposure's `CameraClient.setupViewfinder` / `CameraClient.removeViewfinder` (the two chokepoints of every open/close path), then notifies ControlFlex with the overlay class name. |
| **Paired notifications** | The bridge keeps its own foreground flag: `removeViewfinder` without an active viewfinder never emits a stray `notifyBackground`; switching cameras produces background + foreground in that order. |
| **World-exit safety** | On logging out the bridge resets its state and emits a final `notifyBackground`. |

## How it works

```
Player raises the camera (Exposure CameraItem)
  → LocalPlayer.setActiveExposureCamera → CameraClient.setupViewfinder
    → cfx-compat-exposure Mixin → ControlFlexApi.getInteractiveContextRegistrar().notifyOverlayForeground()
      → ControlFlex switches stick behavior to the interactive-overlay mode

Player lowers the camera / ESC / logout
  → ... → CameraClient.removeViewfinder
    → cfx-compat-exposure Mixin → notifyOverlayBackground()
      → ControlFlex restores normal stick behavior
```

## Requirements

- **ControlFlex** ≥ 0.8.7 (the 0.8.7 API renamed the registrar methods this bridge calls)
- **Exposure** ≥ 1.9.20 (Minecraft 1.20.1, Fabric & Forge 47+)
- Client side only.

## Install

Drop the matching JAR into `mods/` alongside ControlFlex and Exposure.

## Build

```bash
./tools/build-forge.sh          # MC 1.20.1 Forge   → forge/build/libs/
./tools/build-fabric.sh         # MC 1.20.1 Fabric  → fabric/build/libs/
# or: ./gradlew :forge:build :fabric:build
```

Dependencies: the ControlFlex API is resolved from JitPack
(`com.github.ControlFlexMC:control-flex-api:0.8.7`, plain Java and loader-agnostic);
Exposure is resolved from CurseForge Maven (`curse.maven:exposure-871755:*`), same as
cfx-compat-epicfight does for Epic Fight.

## Docs

| Doc | Content |
|-----|---------|
| [Design](docs/en/design.md) | Architecture, hook points, pairing logic |

## License

[MIT](LICENSE)
