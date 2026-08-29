# cfx-compat-exposure

ControlFlex ↔ Exposure 桥接模组 — 在 Exposure 取景框 overlay 进入前台/后台时通知 ControlFlex，使摇杆行为在举着相机时正确切换。

[English](README.md)

## 为什么需要这个模组？

Exposure 的取景框 overlay 是一个交互式覆盖层：举起相机时它会接管相机控制并隐藏大部分原版 HUD。ControlFlex 只有在知道某个交互上下文存在时，才会切换摇杆（右摇杆/GUI）行为 —— 且这类 overlay 没有屏级 GUI，无法靠屏幕检测识别。

本桥接模组使用 ControlFlex 的交互上下文 API（`IInteractiveContextRegistrar`）：

- 取景框 overlay 进入前台（举起相机取景）→ `notifyOverlayForeground(...)`
- 返回后台（放下相机、ESC/背包、停用相机、登出）→ `notifyOverlayBackground(...)`

## 这个模组做了什么？

| 功能 | 实现方式 |
|------|----------|
| **取景框前台/后台通知** | Mixin 挂到 Exposure 的 `CameraClient.setupViewfinder` / `CameraClient.removeViewfinder`（所有打开/关闭路径的唯一汇聚点），以 overlay 类名通知 ControlFlex。 |
| **通知配对** | 桥接层自持前台状态标志：无取景框时的 `removeViewfinder` 不会发出多余的后台通知；切换相机会按 background → foreground 顺序通知。 |
| **登出兜底** | 登出时重置状态并补发一次 `notifyBackground`。 |

## 工作原理

```
玩家举起相机（Exposure CameraItem）
  → LocalPlayer.setActiveExposureCamera → CameraClient.setupViewfinder
    → cfx-compat-exposure Mixin → ControlFlexApi.getInteractiveContextRegistrar().notifyOverlayForeground()
      → ControlFlex 切换为交互式 overlay 摇杆行为

玩家放下相机 / ESC / 登出
  → ... → CameraClient.removeViewfinder
    → cfx-compat-exposure Mixin → notifyOverlayBackground()
      → ControlFlex 恢复常规摇杆行为
```

## 前置模组

- **ControlFlex** ≥ 0.8.7（0.8.7 的 API 改名了本桥接调用的 registrar 方法）
- **Exposure**：
  - 1.9.20+（Minecraft 1.20.1，Fabric 与 Forge 47+）
  - 1.9.18+（Minecraft 1.21.1，NeoForge 21.1+）
- 仅客户端。

## 安装

将与版本对应的 JAR 放入 `mods/`，与 ControlFlex 和 Exposure 并列。

## 构建

```bash
./tools/build-forge.sh          # MC 1.20.1 Forge   → forge/build/libs/
./tools/build-fabric.sh         # MC 1.20.1 Fabric  → fabric/build/libs/
./tools/build-neoforge.sh       # MC 1.21.1 NeoForge → neoforge/build/libs/
# 或：./gradlew :forge:build :fabric:build :neoforge:build
```

依赖说明：ControlFlex API 经 JitPack 解析（`com.github.ControlFlexMC:control-flex-api:0.8.7`，纯 Java、与 loader 无关）；Exposure 走 CurseForge Maven（`curse.maven:exposure-871755:*`），与 cfx-compat-epicfight 对 Epic Fight 的依赖方式一致。

> 1.21.1 Fabric 版本规划中，暂未构建。

## 文档

| 文档 | 内容 |
|------|------|
| [方案设计](docs/zh/design.md) | 架构、挂载点、配对逻辑 |

## 许可证

[MIT](LICENSE)
