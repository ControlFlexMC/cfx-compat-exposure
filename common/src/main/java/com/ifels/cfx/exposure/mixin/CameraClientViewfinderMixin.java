package com.ifels.cfx.exposure.mixin;

import com.ifels.cfx.exposure.CfxExposureContextBridge;
import io.github.mortuusars.exposure.client.camera.CameraClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the two chokepoints of Exposure's viewfinder lifecycle.
 *
 * <ul>
 *   <li>{@code CameraClient.setupViewfinder(Camera)} — viewfinder created (enters foreground).
 *       This method calls {@code removeViewfinder()} first to replace an old viewfinder,
 *       so a camera-switch receives background then foreground notifications in that order.</li>
 *   <li>{@code CameraClient.removeViewfinder()} — viewfinder closed (returns to background).
 *       Every Exposure exit path (ESC/inventory, selfie switch, per-tick camera-inactive
 *       check, logout) eventually reaches here.</li>
 * </ul>
 *
 * <p>Both methods are {@code public static} and client-only (all platform metadata
 * declare a client environment). Exposure is third-party code (not a vanilla class),
 * so the mixin does not remap. The className notified to ControlFlex is resolved at
 * runtime from the active overlay instance by
 * {@link CfxExposureContextBridge#resolveOverlayClassName()} (not a hardcoded name).</p>
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
