# 方案设计

## 目标

做一个 ControlFlex x Exposure 兼容模组，只有一个兼容点：**Exposure 取景框 overlay 进入前台/后台时，通过 ControlFlex 的交互上下文 API（`IInteractiveContextRegistrar`）通知 ControlFlex**，使 ControlFlex 在玩家举起相机取景时切换摇杆行为。不包含任何其他功能（无 compat JSON、无 guide、无插件 SPI）。

## 平台

| 子项目 | MC | Loader | Exposure（编译） | ControlFlex |
|--------|----|--------|------------------------|-------------|
| `forge` | 1.20.1 | Forge 47.4.4 | 1.9.21（forge） | 0.8.7+ |
| `fabric` | 1.20.1 | Fabric Loader 0.15.11 + Fabric API 0.92.2 | 1.9.20（fabric） | 0.8.7+ |

> 本分支（`1.20.1`）构建 1.20.1 的两个 loader；MC 1.21.1 版本（Fabric + NeoForge）见 `1.21.1` 分支。Exposure 1.9.21 未发布 1.20.1 Fabric 版本，Fabric 侧以 1.9.20（该平台最新）为准；运行时两个 loader 均要求 Exposure ≥ 1.9.20。

两个 loader 共享同一份纯 Java 源码（`common/src/main/java`）；平台差异仅在构建脚本与元数据（mods.toml / fabric.mod.json、mixins.json 的 `compatibilityLevel`、pack.mcmeta 的 pack_format）。

## 为什么挂这两个方法

Exposure 1.9.x（1.20.1 与 1.21.1 结构一致）中取景框的生命周期完全汇聚于 `CameraClient` 两个静态方法：

- `CameraClient.setupViewfinder(Camera)` —— 取景框创建（进入前台）。所有打开路径（相机激活、自拍切换）经由 `LocalPlayer.setActiveExposureCamera` → `CameraClient.setupViewfinder` 触发。
- `CameraClient.removeViewfinder()` —— 取景框关闭（返回后台）。`LocalPlayer.removeActiveExposureCamera`、ESC/背包键（先 `deactivate` 触发 remove）、每 tick 的相机失效检测、登出等所有退出路径都最终汇聚到这里。

选择它们而不是 `Viewfinder`/`ViewfinderOverlay` 的构造器或 render：setup/remove 是**所有**进出路径的唯一汇聚点，且不会被每帧调用，通知时机精确、无重复。

## 架构

```
Exposure (Mixin 目标)
  CameraClient.setupViewfinder ──RETURN──> [Mixin] onViewfinderSetup()
  CameraClient.removeViewfinder  ──RETURN──> [Mixin] onViewfinderRemove()
                         │                          │
                         ▼                          ▼
              CfxExposureContextBridge（配对状态 + null 判空）
                         │  ControlFlexApi.getInteractiveContextRegistrar()
                         ▼
              ControlFlex InteractiveContextRegistry
                notifyOverlayForeground("...ViewfinderOverlay") / notifyOverlayBackground(...)
```

## 组件

- **`CfxExposureContextBridge`**（common）：静态桥接层，持有 `viewfinderForeground` 配对标志；所有 API 调用判空（ControlFlex 未安装/未就绪时 `getInteractiveContextRegistrar()` 返回 null）；`onWorldExit()` 在登出时补发后台通知并重置标志。
- **`CameraClientViewfinderMixin`**（common）：`@Inject` 两个方法的 `RETURN`。`remap = false`（Exposure 是第三方类，无需原版映射）。目标类：`io.github.mortuusars.exposure.client.camera.CameraClient`。
- **`CfxExposureMod`**（每个 loader 一份）：`@Mod` 入口；客户端启动时校验 Mixin 目标类可加载；订阅登出事件 → `bridge.onWorldExit()`。
  - Forge：`@Mod(id)` + `FMLJavaModLoadingContext` + `MinecraftForge.EVENT_BUS` + `ClientPlayerNetworkEvent.LoggingOut`
  - Fabric：`ClientModInitializer` + `ClientPlayConnectionEvents.DISCONNECT`

## 配对规则

| 场景 | 序列 |
|------|------|
| 举起相机 | `notifyOverlayForeground` |
| 放下相机 / ESC / 停用 | `notifyOverlayBackground` |
| 换相机（setupViewfinder 开头会先调 removeViewfinder） | `notifyOverlayBackground` → `notifyOverlayForeground` |
| removeViewfinder 但本就无取景框 | 无通知（标志保护） |
| 登出时取景框仍在前台 | 补发一次 `notifyOverlayBackground`，重置标志 |

ControlFlex 侧在 phase 退出时也会自动清理上下文，登出兜底为双保险。

## 通知的上下文标识

`notifyOverlayForeground/notifyOverlayBackground` 的 className 参数传**运行时解析**的取景框 overlay 真实类名 —— 从 Exposure 当前活跃 `Viewfinder` 实例取 `overlay()` 对象后调用 `getClass().getName()`，不依赖硬编码字符串（对混淆/重映射差异及 ViewfinderRegistry 注册的自定义取景框同样生效）；仅在实例不可得时回退默认类名：

```
io.github.mortuusars.exposure.client.camera.viewfinder.ViewfinderOverlay
```

这也是 ControlFlex compat 配置中定位该交互上下文的 key。前台与配对的后台通知使用同一 className（桥接层记录）。

## 依赖

- ControlFlex API 0.8.7：JitPack（`com.github.ControlFlexMC:control-flex-api:0.8.7`，API 部分为纯 Java，与 loader 无关），compileOnly。注意：0.8.7 将 `IInteractiveContextRegistrar` 的方法由 `notifyForeground`/`notifyBackground` 改名为 `notifyOverlayForeground`/`notifyOverlayBackground`，因此运行时要求 ControlFlex ≥ 0.8.7。
- Exposure：CurseForge Maven（`curse.maven:exposure-871755:<fileId>`，与 cfx-compat-epicfight 对 Epic Fight 的依赖方式一致），compileOnly。
- 运行时元数据：两个 loader 均声明 `controlflex [0.8.7,)`、`exposure [1.9.20,)`，均为 required、CLIENT。

## 构建

参考 cfx-compat-epicfight 的分阶段配置：

- `forge/`：`net.neoforged.moddev.legacyforge` 2.0.141 + `mixin {}` DSL + refmap + `MixinConfigs` manifest 属性（Forge 1.20.1 需要）+ `reobfJar`。
- `fabric/`：`fabric-loom` 1.6-SNAPSHOT（与 ControlFlex 一致）+ mojmap + `loom.mixin.defaultRefmapName`；`fabric.mod.json` 通过 processResources 展开占位符。

## 验证

- `./gradlew :forge:build :fabric:build` 产出两个 jar。
- jar 内容检查：mixins.json / mods.toml 占位符展开、Forge 侧 manifest `MixinConfigs`、class 文件齐全。
- 运行时：客户端日志出现 `Exposure viewfinder bridge active (mixin verification passed)`；举起/放下相机时 ControlFlex 行为切换正确（手动测试）。
