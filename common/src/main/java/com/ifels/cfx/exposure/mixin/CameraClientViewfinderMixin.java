package com.ifels.cfx.exposure.mixin;

import com.ifels.cfx.exposure.CfxExposureContextBridge;
import io.github.mortuusars.exposure.client.camera.CameraClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 挂在 Exposure 取景框生命周期的两个汇聚点上。
 *
 * <ul>
 *   <li>{@code CameraClient.setupViewfinder(Camera)} —— 取景框创建（进入前台）。
 *       该方法开头会先调用 {@code removeViewfinder()} 以替换旧取景框，
 *       因此“换相机”场景会依次收到 background + foreground 两条通知。</li>
 *   <li>{@code CameraClient.removeViewfinder()} —— 取景框关闭（返回后台）。
 *       Exposure 的所有退出路径（ESC/背包键、自拍切换、相机失效的 tick 检测、登出）
 *       最终都会走到这里。</li>
 * </ul>
 *
 * <p>两个方法均为 {@code public static}，且只在客户端运行（各平台元数据均声明
 * 客户端）。Exposure 为第三方模组代码（非原版类），Mixin 无需 remap。
 * 通知 ControlFlex 时使用的 className 由 {@link CfxExposureContextBridge#resolveOverlayClassName()}
 * 从活跃 overlay 实例实时解析（不依赖硬编码类名）。</p>
 */
@Mixin(value = CameraClient.class, remap = false)
public abstract class CameraClientViewfinderMixin {

    @Inject(method = "setupViewfinder", at = @At("RETURN"), remap = false)
    private static void cfxExposure$onViewfinderSetup(CallbackInfo ci) {
        CfxExposureContextBridge.onViewfinderForeground(
                CfxExposureContextBridge.resolveOverlayClassName());
    }

    @Inject(method = "removeViewfinder", at = @At("RETURN"), remap = false)
    private static void cfxExposure$onViewfinderRemove(CallbackInfo ci) {
        CfxExposureContextBridge.onViewfinderBackground();
    }
}
