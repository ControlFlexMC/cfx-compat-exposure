package com.ifels.cfx.exposure;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * cfx-compat-exposure — ControlFlex x Exposure compat mod (MC 1.20.1 Fabric).
 *
 * <p>Single compat point: when the viewfinder overlay enters/leaves the foreground,
 * notify ControlFlex through {@code IInteractiveContextRegistrar}
 * (see {@link CfxExposureContextBridge} and {@code CameraClientViewfinderMixin}).</p>
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
