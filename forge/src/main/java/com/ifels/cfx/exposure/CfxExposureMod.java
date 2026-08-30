package com.ifels.cfx.exposure;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * cfx-compat-exposure — ControlFlex x Exposure compat mod (MC 1.20.1 Forge).
 *
 * <p>Single compat point: when the viewfinder overlay enters/leaves the foreground,
 * notify ControlFlex through {@code IInteractiveContextRegistrar}
 * (see {@link CfxExposureContextBridge} and {@code CameraClientViewfinderMixin}).</p>
 */
@Mod(CfxExposureMod.MOD_ID)
public class CfxExposureMod {

    public static final String MOD_ID = "cfx_compat_exposure";
    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-exposure");

    public CfxExposureMod() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.register(this);
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
