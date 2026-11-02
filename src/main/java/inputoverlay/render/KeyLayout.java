package dev.inputoverlay.render;

import dev.inputoverlay.config.OverlayConfig;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Keyboard layout. Positions/widths are in "pitch" units (1.0 = one normal key incl. gap).
 * Numbers were measured from the reference image. Edit here, or use "customKeys" in the JSON config.
 */
public final class KeyLayout {
    private KeyLayout() {}

    public static final int PITCH = 20; // px per key pitch (key = PITCH - GAP)
    public static final int GAP = 2;

    public static final class KeyDef {
        public final String label;
        public final int[] codes;
        public final float x, y, w;
        public float level; // 0..1 pressed fade
        KeyDef(String label, float x, float y, float w, int... codes) {
            this.label = label; this.x = x; this.y = y; this.w = w; this.codes = codes;
        }
    }

    private static List<KeyDef> cache;
    private static OverlayConfig owner;
    private static int width, height;

    public static List<KeyDef> get() {
        OverlayConfig c = OverlayConfig.get();
        if (cache == null || owner != c) build(c);
        return cache;
    }
    public static int width() { get(); return width; }
    public static int height() { get(); return height; }

    private static void build(OverlayConfig c) {
        List<KeyDef> l = new ArrayList<>();
        if (!c.customKeys.isEmpty()) {
            for (OverlayConfig.CustomKey k : c.customKeys) l.add(new KeyDef(k.label, k.x, k.y, Math.max(0.5f, k.w), k.key));
        } else {
            defaults(l);
        }
        float maxX = 1, maxY = 0;
        for (KeyDef k : l) { maxX = Math.max(maxX, k.x + k.w); maxY = Math.max(maxY, k.y); }
        width = Math.round(maxX * PITCH) - GAP;
        height = Math.round((maxY + 1) * PITCH) - GAP;
        cache = l; owner = c;
    }

    private static void letters(List<KeyDef> l, float y, float startX, int... codes) {
        for (int i = 0; i < codes.length; i++) l.add(new KeyDef(String.valueOf((char) codes[i]), startX + i, y, 1f, codes[i]));
    }

    private static void defaults(List<KeyDef> l) {
        letters(l, 0, 1.00f, GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4, GLFW_KEY_5);
        l.add(new KeyDef("tab", 0, 1, 1.55f, GLFW_KEY_TAB));
        letters(l, 1, 1.55f, GLFW_KEY_Q, GLFW_KEY_W, GLFW_KEY_E, GLFW_KEY_R, GLFW_KEY_T);
        l.add(new KeyDef("caps\nlk", 0, 2, 1.82f, GLFW_KEY_CAPS_LOCK));
        letters(l, 2, 1.82f, GLFW_KEY_A, GLFW_KEY_S, GLFW_KEY_D, GLFW_KEY_F, GLFW_KEY_G);
        l.add(new KeyDef("shift", 0, 3, 2.29f, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT));
        letters(l, 3, 2.29f, GLFW_KEY_Z, GLFW_KEY_X, GLFW_KEY_C, GLFW_KEY_V);
        l.add(new KeyDef("ctrl", 0, 4, 1.55f, GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL));
        l.add(new KeyDef("alt", 2.5f, 4, 1.5f, GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT));
        l.add(new KeyDef("", 4.05f, 4, 2.7f, GLFW_KEY_SPACE));
    }
}
