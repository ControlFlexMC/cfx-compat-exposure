package com.ifels.cfx.exposure;

import com.ifels.controlflex.api.ControlFlexApi;
import com.ifels.controlflex.api.IInteractiveContextRegistrar;
import io.github.mortuusars.exposure.client.camera.CameraClient;
import io.github.mortuusars.exposure.client.camera.viewfinder.Viewfinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Viewfinder overlay foreground/background bridge.
 *
 * <p>Triggered by {@code CameraClientViewfinderMixin} at the end of Exposure's
 * {@code CameraClient.setupViewfinder(Camera)} (enters foreground) and
 * {@code CameraClient.removeViewfinder()} (returns to background), declaring
 * the viewfinder overlay as a ControlFlex interactive context
 * ({@link IInteractiveContextRegistrar}).</p>
 *
 * <p>The className used in notifications is <b>not a hardcoded string</b>:
 * the overlay instance is taken from Exposure's active {@link Viewfinder}
 * and {@code getClass().getName()} yields the class that actually exists in
 * that jar (works across obfuscation/remapping and custom viewfinders from
 * {@code ViewfinderRegistry}). Falls back to the default class name only when
 * no instance is available at runtime.</p>
 *
 * <p>When ControlFlex is missing or not yet initialized,
 * {@link ControlFlexApi#getInteractiveContextRegistrar()} returns {@code null}
 * — every call site must null-check.</p>
 *
 * <p>Thread safety: called only from the client main thread (mixin callbacks
 * and loader events all satisfy this).</p>
 */
public final class CfxExposureContextBridge {

    /** Fallback class name: the standard viewfinder overlay (used only when no instance can be resolved). */
    public static final String DEFAULT_OVERLAY_CLASS =
            "io.github.mortuusars.exposure.client.camera.viewfinder.ViewfinderOverlay";

    private static final Logger LOGGER = LogManager.getLogger("cfx-compat-exposure");

    /** Runtime class name of the overlay currently in the foreground; {@code null} = not in foreground. */
    private static String foregroundClassName;

    private CfxExposureContextBridge() {
    }

    /**
     * Resolve the real runtime class name of the viewfinder overlay.
     *
     * <p>Reads the overlay instance from Exposure's active {@link Viewfinder};
     * any failure (no active viewfinder, missing custom implementation, etc.)
     * falls back to {@link #DEFAULT_OVERLAY_CLASS}.</p>
     */
    public static String resolveOverlayClassName() {
        try {
            Viewfinder viewfinder = CameraClient.viewfinder();
            if (viewfinder != null && viewfinder.overlay() != null) {
                return viewfinder.overlay().getClass().getName();
            }
        } catch (Throwable ignored) {
            // Fall back to the default class name
        }
        return DEFAULT_OVERLAY_CLASS;
    }

    /**
     * Viewfinder overlay entered the foreground (camera activated, looking through the viewfinder).
     *
     * <p>Exposure's {@code setupViewfinder} always calls {@code removeViewfinder()} first
     * to replace an existing viewfinder, so a camera-switch receives
     * {@link #onViewfinderBackground()} then this notification — pairing is correct.</p>
     *
     * @param overlayClassName runtime class name of the overlay entering the foreground
     *                         (from {@link #resolveOverlayClassName()})
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
     * Viewfinder overlay returned to the background (viewfinder closed, camera deactivated, logout).
     *
     * <p>Sends the paired background notification with the same className recorded on enter.
     * Every exit path (ESC/inventory, selfie switch, per-tick camera-inactive check, logout)
     * funnels through Exposure's {@code CameraClient.removeViewfinder()}, which the mixin
     * uses to trigger this method.</p>
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
     * Logout / leave world: the viewfinder always leaves with it, so emit a background
     * notification and reset pairing state.
     * (ControlFlex also auto-clears on phase exit; this is belt-and-braces.)
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
