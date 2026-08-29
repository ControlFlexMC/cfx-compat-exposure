package com.ifels.cfx.exposure;

import com.ifels.controlflex.api.ControlFlexApi;
import com.ifels.controlflex.api.IInteractiveContextRegistrar;
import io.github.mortuusars.exposure.client.camera.CameraClient;
import io.github.mortuusars.exposure.client.camera.viewfinder.Viewfinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 取景框 overlay 前台/后台桥接。
 *
 * <p>由 {@code CameraClientViewfinderMixin} 在 Exposure 的
 * {@code CameraClient.setupViewfinder(Camera)}（进入前台）与
 * {@code CameraClient.removeViewfinder()}（返回后台）方法结尾处触发，
 * 把取景框 overlay 声明为 ControlFlex 的交互上下文
 * （{@link IInteractiveContextRegistrar}）。</p>
 *
 * <p>通知用的 className <b>不依赖硬编码字符串</b>：先取 Exposure 当前活跃
 * {@link Viewfinder} 的 overlay 实例，用 {@code getClass().getName()} 得到该 jar
 * 中真实存在的类名（对混淆/重映射差异以及 {@code ViewfinderRegistry} 注册的
 * 自定义取景框同样生效）；仅当运行时拿不到实例时才回退到默认类名。</p>
 *
 * <p>ControlFlex 未安装或尚未初始化时，{@link ControlFlexApi#getInteractiveContextRegistrar()}
 * 返回 {@code null} —— 所有调用点都必须判空。</p>
 *
 * <p>线程安全：仅从客户端主线程调用（Mixin 回调与各 loader 事件均满足）。</p>
 */
public final class CfxExposureContextBridge {

    /** 兜底类名：标准取景框 overlay（仅在运行时无法解析实例时使用）。 */
    public static final String DEFAULT_OVERLAY_CLASS =
            "io.github.mortuusars.exposure.client.camera.viewfinder.ViewfinderOverlay";

    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-exposure");

    /** 当前处于前台的 overlay 真实运行时类名；{@code null} = 不在前台。 */
    private static String foregroundClassName;

    private CfxExposureContextBridge() {
    }

    /**
     * 解析取景框 overlay 的真实运行时类名。
     *
     * <p>从 Exposure 的活跃 {@link Viewfinder} 取 overlay 实例并读取其运行时类名；
     * 任何一步失败（无活跃取景框、自定义实现缺失等）都回退 {@link #DEFAULT_OVERLAY_CLASS}。</p>
     */
    public static String resolveOverlayClassName() {
        try {
            Viewfinder viewfinder = CameraClient.viewfinder();
            if (viewfinder != null && viewfinder.overlay() != null) {
                return viewfinder.overlay().getClass().getName();
            }
        } catch (Throwable ignored) {
            // 回退到默认类名
        }
        return DEFAULT_OVERLAY_CLASS;
    }

    /**
     * 取景框 overlay 进入前台（相机激活、举起取景框）。
     *
     * <p>Exposure 的 {@code setupViewfinder} 开头总是先调用 {@code removeViewfinder()}
     * 以替换旧取景框，因此“换相机”场景会先收到 {@link #onViewfinderBackground()}
     * 再收到本通知 —— 配对语义正确。</p>
     *
     * @param overlayClassName 本次进入前台的 overlay 真实运行时类名
     *                         （由 {@link #resolveOverlayClassName()} 解析）
     */
    public static synchronized void onViewfinderForeground(String overlayClassName) {
        if (foregroundClassName != null) {
            return;
        }
        foregroundClassName = overlayClassName;
        IInteractiveContextRegistrar registrar = ControlFlexApi.getInteractiveContextRegistrar();
        if (registrar != null) {
            registrar.notifyOverlayForeground(overlayClassName);
            LOGGER.debug("Viewfinder overlay entered foreground (class {}) -> notified ControlFlex",
                    overlayClassName);
        } else {
            LOGGER.warn("Viewfinder overlay entered foreground but ControlFlex registrar is not available");
        }
    }

    /**
     * 取景框 overlay 返回后台（关闭取景框、停用相机、登出）。
     *
     * <p>使用进入前台时记录的同名 className 发送配对的后台通知。
     * 所有退出路径（ESC/背包键、自拍切换、相机失效的 tick 检测、登出）都会汇聚到
     * Exposure 的 {@code CameraClient.removeViewfinder()}，由 Mixin 触发本方法。</p>
     */
    public static synchronized void onViewfinderBackground() {
        if (foregroundClassName == null) {
            return;
        }
        String className = foregroundClassName;
        foregroundClassName = null;
        IInteractiveContextRegistrar registrar = ControlFlexApi.getInteractiveContextRegistrar();
        if (registrar != null) {
            registrar.notifyOverlayBackground(className);
            LOGGER.debug("Viewfinder overlay left foreground (class {}) -> notified ControlFlex",
                    className);
        }
    }

    /**
     * 登出/离开世界：取景框必然随之退出，补发一次后台通知，并重置配对状态。
     * （ControlFlex 在 phase 退出时也会自动清理，这里是双保险。）
     */
    public static synchronized void onWorldExit() {
        if (foregroundClassName != null) {
            String className = foregroundClassName;
            foregroundClassName = null;
            IInteractiveContextRegistrar registrar = ControlFlexApi.getInteractiveContextRegistrar();
            if (registrar != null) {
                registrar.notifyOverlayBackground(className);
                LOGGER.debug("World exit with active viewfinder (class {}) -> notified ControlFlex",
                        className);
            }
        }
    }
}
