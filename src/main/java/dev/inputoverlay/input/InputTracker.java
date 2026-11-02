package dev.inputoverlay.input;

import dev.inputoverlay.config.OverlayConfig;
import dev.inputoverlay.gui.OverlayScreen;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;

/**
 * Polls raw GLFW state once per frame (no mixins needed, so keys Minecraft doesn't use are tracked too).
 * Scroll can't be polled, so a chained GLFW scroll callback is installed (the previous one still runs).
 */
public final class InputTracker {
    private InputTracker() {}

    public static float dt = 0.016f;                       // seconds since last update
    public static final float[] button = new float[3];     // fade 0..1: left, right, middle
    public static float scrollLevel = 0, scrollPhase = 0;  // scroll animation
    public static int scrollDir = 1;                       // +1 up, -1 down

    private static boolean init, blocked;
    private static long window, lastNanos;
    private static volatile double scrollAccum;

    public static void update(MinecraftClient mc) {
        long now = System.nanoTime();
        float d = lastNanos == 0 ? 0.016f : (now - lastNanos) / 1e9f;
        if (init && d < 0.0005f) return;           // already updated this frame
        lastNanos = now;
        dt = Math.min(d, 0.1f);
        window = mc.getWindow().getHandle();
        if (!init) installScrollHook();

        OverlayConfig cfg = OverlayConfig.get();
        blocked = mc.currentScreen != null && !cfg.trackInputsInGuis && !(mc.currentScreen instanceof OverlayScreen);

        for (int i = 0; i < 3; i++)
            button[i] = approach(button[i], !blocked && GLFW.glfwGetMouseButton(window, i) == GLFW.GLFW_PRESS);

        double s = scrollAccum; scrollAccum = 0;
        if (blocked) s = 0;
        if (s != 0) { scrollDir = s > 0 ? 1 : -1; scrollLevel = 1f; scrollPhase += scrollDir * 3f; }
        else scrollLevel = Math.max(0f, scrollLevel - dt / 0.3f);
        scrollPhase += scrollDir * scrollLevel * dt * 30f;
        init = true;
    }

    public static boolean isKeyDown(int code) {
        return !blocked && code > 0 && GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
    }

    /** Moves a fade level toward 1 (down) or 0 (up) over the configured fade time. */
    public static float approach(float cur, boolean down) {
        OverlayConfig c = OverlayConfig.get();
        float step = dt / Math.max(0.001f, (down ? c.fadeInMillis : c.fadeOutMillis) / 1000f);
        return down ? Math.min(1f, cur + step) : Math.max(0f, cur - step);
    }

    private static void installScrollHook() {
        final GLFWScrollCallback[] prev = new GLFWScrollCallback[1];
        prev[0] = GLFW.glfwSetScrollCallback(window, (w, x, y) -> {
            scrollAccum += y;
            if (prev[0] != null) prev[0].invoke(w, x, y);
        });
    }
}
