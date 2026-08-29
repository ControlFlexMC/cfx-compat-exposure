package com.ifels.cfx.exposure;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * cfx-compat-exposure —— ControlFlex x Exposure 兼容模组（MC 1.20.1 Fabric）。
 *
 * <p>唯一兼容点：取景框 overlay 进入前台/后台时通过
 * ControlFlex 的 {@code IInteractiveContextRegistrar} 通知 ControlFlex
 * （实现见 {@link CfxExposureContextBridge} 与
 * {@code CameraClientViewfinderMixin}）。</p>
 */
@Environment(EnvType.CLIENT)
public class CfxExposureMod implements ClientModInitializer {

    public static final String MOD_ID = "cfx_compat_exposure";
    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-exposure");

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                CfxExposureContextBridge.onWorldExit());
        verifyMixinApplied();
    }

    private void verifyMixinApplied() {
        try {
            Class.forName("io.github.mortuusars.exposure.client.camera.CameraClient");
            LOGGER.info("Exposure viewfinder bridge active (mixin verification passed; overlay class: {})",
                    CfxExposureContextBridge.resolveOverlayClassName());
        } catch (ClassNotFoundException e) {
            LOGGER.error("Exposure viewfinder mixin verification FAILED: {}", e.getMessage());
        }
    }
}
