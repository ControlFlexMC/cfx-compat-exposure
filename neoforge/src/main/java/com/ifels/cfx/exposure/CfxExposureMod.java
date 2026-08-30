package com.ifels.cfx.exposure;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * cfx-compat-exposure — ControlFlex x Exposure compat mod (MC 1.21.1 NeoForge).
 *
 * <p>Single compat point: when the viewfinder overlay enters/leaves the foreground,
 * notify ControlFlex through {@code IInteractiveContextRegistrar}
 * (see {@link CfxExposureContextBridge} and {@code CameraClientViewfinderMixin}).</p>
 */
@Mod(value = CfxExposureMod.MOD_ID, dist = Dist.CLIENT)
public class CfxExposureMod {

    public static final String MOD_ID = "cfx_compat_exposure";
    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-exposure");

    public CfxExposureMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(this::verifyMixinApplied);
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // On logout the viewfinder has already left the foreground: reset pairing and emit background.
        CfxExposureContextBridge.onWorldExit();
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
