# Control Flex × Exposure

A compatibility bridge that tells ControlFlex when you look through an Exposure camera, so stick behavior switches correctly in the viewfinder.

---

## What This Does

Without this mod, ControlFlex **does not know the Exposure viewfinder is open** — even with both mods installed. The viewfinder is an interactive overlay: it takes over look controls and hides most of the vanilla HUD, but it is not a screen, so ControlFlex cannot detect it the usual way.

This bridge hooks the viewfinder open/close paths and notifies ControlFlex through its interactive-context API.

**The result:** raising the camera switches to overlay stick behavior; lowering it, pressing ESC, or logging out restores normal sticks.

---

## Features

- 📷 **Viewfinder-aware stick switching** — ControlFlex treats the Exposure viewfinder as an interactive overlay for as long as you are looking through the camera.
- 🔁 **Paired open/close** — every foreground notification has a matching background; a stray close with no active viewfinder is ignored.
- 🔄 **Camera switching** — swapping cameras emits background then foreground in that order, so stick mode does not get stuck.
- 🚪 **Logout safety** — if you disconnect while the viewfinder is still up, the bridge sends a final background notification and resets its state.
- ⌨️ **Safe without ControlFlex** — if ControlFlex is missing or not ready, the bridge no-ops instead of crashing.

---

## Dependencies (Required)

| Mod | Version |
|-----|---------|
| [ControlFlex](https://www.curseforge.com/minecraft/mc-mods/controlflex) | ≥ 0.8.7 |
| [Exposure](https://www.curseforge.com/minecraft/mc-mods/exposure) | ≥ 1.9.18 on Minecraft 1.21.1; ≥ 1.9.20 on Minecraft 1.20.1 |

---

## Compatibility

- ✅ Works in singleplayer and multiplayer (client-side only).
- This bridge does **one thing**: viewfinder foreground/background. It does not add key configs, in-game guides, or extra Exposure features.

---

## How It Works

```
Player raises the camera (Exposure)
                ↓
         cfx-compat-exposure (viewfinder open/close)
                ↓
         ControlFlex interactive-context API
                ↓
         Overlay stick behavior while looking through the viewfinder
```

A Mixin on Exposure's `CameraClient.setupViewfinder` / `CameraClient.removeViewfinder` (the two chokepoints of every open/close path) notifies ControlFlex with the viewfinder overlay class name. A small pairing flag prevents duplicate or unmatched background notifications.

---

## Notes

- This mod is **client-side only** — it does not need to be installed on servers.
- Make sure ControlFlex detects your controller before entering a world.
- Install the JAR that matches your loader and Minecraft version (Fabric / Forge / NeoForge).
