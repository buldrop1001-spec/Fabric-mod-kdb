package dev.inputoverlay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * All user settings. Saved as JSON in .minecraft/config/inputoverlay.json.
 * A missing or corrupt file never crashes: it is backed up and replaced with defaults.
 * Colors are "#RRGGBB" strings in JSON and cached as ints (transient) for rendering.
 */
public class OverlayConfig {
    private static final Logger LOG = LoggerFactory.getLogger("InputOverlay");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static OverlayConfig instance = new OverlayConfig();

    public static OverlayConfig get() { return instance; }

    /** 3x3 anchor presets; the block position = anchor point + offset (GUI-scaled pixels). */
    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;
        public int baseX(int screenW, int blockW) { int c = ordinal() % 3; return c == 0 ? 0 : c == 1 ? (screenW - blockW) / 2 : screenW - blockW; }
        public int baseY(int screenH, int blockH) { int r = ordinal() / 3; return r == 0 ? 0 : r == 1 ? (screenH - blockH) / 2 : screenH - blockH; }
        public Anchor next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /** Independent settings for one block (keyboard or mouse). */
    public static class Block {
        public boolean enabled = true;
        public Anchor anchor = Anchor.TOP_LEFT;
        public int offsetX = 0, offsetY = 0;
        public float scale = 1.0f;     // 0.25 .. 4
        public float opacity = 1.0f;   // 0.05 .. 1
        Block(int x, int y) { offsetX = x; offsetY = y; }
    }

    /** Optional custom key. x/y/w are in key-pitch units (1 = one normal key). */
    public static class CustomKey {
        public String label = "?";
        public int key = -1;           // GLFW key code, e.g. 69 = E
        public float x = 0, y = 0, w = 1;
    }

    public Block keyboard = new Block(10, 10);
    public Block mouse = new Block(150, 8);

    // Toggles
    public boolean overlayEnabled = true;
    public boolean showScrollAnimation = true;
    public boolean showInGuis = true;
    public boolean hideOnF1 = true;
    public boolean trackInputsInGuis = true;       // read real GLFW state while a GUI is open
    public boolean requireRightShiftForHotkey = true; // settings hotkey = Right Shift + (rebindable key)
    public int fadeInMillis = 70;     // how long a key takes to fully light up
    public int fadeOutMillis = 180;   // how long it takes to fade back (slower = smoother)
    public boolean keySquish = true;  // pressed keys gently shrink and sink, like a real keycap

    // Colors (#RRGGBB)
    public String keyBackground = "#000000";
    public String keyText = "#FFFFFF";
    public String keyOutline = "#000000"; // same as keyBackground = no outline
    public String keyPressed = "#FFFFFF";
    public String keyPressedText = "#000000";
    public String mouseBody = "#1A1A1A";
    public String mouseOutline = "#3A3A3A";
    public String mouseGlow = "#2F6BFF";
    public String mouseIndicator = "#FFFFFF";
    public String mousePressed = "#FFFFFF";

    /** Leave empty for the default layout. Fill to replace the keyboard with your own keys. */
    public List<CustomKey> customKeys = new ArrayList<>();

    // Parsed color cache
    public transient int cKeyBg, cKeyText, cKeyOutline, cKeyPressed, cKeyPressedText,
            cMouseBody, cMouseOutline, cMouseGlow, cMouseIndicator, cMousePressed;public static int parseRgb(String s, int def) {
        try {
            if (s == null) return def;
            s = s.trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() != 6) return def;
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception e) { return def; }
    }

    public static boolean isValidHex(String s) { return s != null && s.matches("#?[0-9A-Fa-f]{6}"); }

    public void refreshColors() {
        cKeyBg = parseRgb(keyBackground, 0x000000);
        cKeyText = parseRgb(keyText, 0xFFFFFF);
        cKeyOutline = parseRgb(keyOutline, 0x000000);
        cKeyPressed = parseRgb(keyPressed, 0xFFFFFF);
        cKeyPressedText = parseRgb(keyPressedText, 0x000000);
        cMouseBody = parseRgb(mouseBody, 0x1A1A1A);
        cMouseOutline = parseRgb(mouseOutline, 0x3A3A3A);
        cMouseGlow = parseRgb(mouseGlow, 0x2F6BFF);
        cMouseIndicator = parseRgb(mouseIndicator, 0xFFFFFF);
        cMousePressed = parseRgb(mousePressed, 0xFFFFFF);
    }

    /** Repairs null/out-of-range values after loading. */
    private void sanitize() {
        if (keyboard == null) keyboard = new Block(10, 10);
        if (mouse == null) mouse = new Block(150, 8);
        for (Block b : new Block[]{keyboard, mouse}) {
            if (b.anchor == null) b.anchor = Anchor.TOP_LEFT;
            b.scale = Math.max(0.25f, Math.min(4f, b.scale));
            b.opacity = Math.max(0.05f, Math.min(1f, b.opacity));
        }
        fadeInMillis = Math.max(0, Math.min(1000, fadeInMillis));
        fadeOutMillis = Math.max(0, Math.min(1000, fadeOutMillis));
        if (customKeys == null) customKeys = new ArrayList<>();
        customKeys.removeIf(k -> k == null || k.label == null);
        refreshColors();
    }

    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("inputoverlay.json"); }

    public static void load() {
        Path p = path();
        OverlayConfig loaded = null;
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                loaded = GSON.fromJson(r, OverlayConfig.class);
                if (loaded == null) throw new JsonSyntaxException("empty file");
            } catch (Exception e) {
                LOG.warn("Config unreadable ({}), resetting to defaults", e.toString());
                try { Files.move(p, p.resolveSibling("inputoverlay.json.bak"), StandardCopyOption.REPLACE_EXISTING); } catch (Exception ignored) {}
                loaded = null;
            }
        }
        instance = loaded != null ? loaded : new OverlayConfig();
        instance.sanitize();
        instance.save();
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(path())) {
            GSON.toJson(this, w);
        } catch (Exception e) { LOG.warn("Could not save config: {}", e.toString()); }
    }

    public static void resetToDefaults() {
        instance = new OverlayConfig();
        instance.sanitize();
        instance.save();
    }
}
