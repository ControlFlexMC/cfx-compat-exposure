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
 * cfx-compat-exposure —— ControlFlex x Exposure 兼容模组（MC 1.20.1 Forge）。
 *
 * <p>唯一兼容点：取景框 overlay 进入前台/后台时通过
 * ControlFlex 的 {@code IInteractiveContextRegistrar} 通知 ControlFlex
 * （实现见 {@link CfxExposureContextBridge} 与
 * {@code CameraClientViewfinderMixin}）。</p>
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
        // 登出时取景框必然已退出前台：重置配对状态并补发后台通知。
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
