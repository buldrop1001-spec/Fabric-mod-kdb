package dev.inputoverlay.render;

import dev.inputoverlay.config.OverlayConfig;
import dev.inputoverlay.input.InputTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws both blocks with plain DrawContext.fill() calls (no textures).
 * Key labels use Minecraft's own default font via TextRenderer.
 */
public final class OverlayRenderer {
    private OverlayRenderer() {}

    /** Mouse local size in px (before scale). */
    public static final int MW = 54, MH = 96;

    // Mouse silhouette (ergonomic, right-handed): {t = 0 top..1 bottom, x = 0..1 of MW}.
    // Values are smoothed with cosine interpolation; the top and bottom are then rounded off
    // with elliptical caps (CAP_TOP / CAP_BOTTOM = how much of the height is rounded).
    private static final float[][] LEFT = {
            {0f, .14f}, {.20f, .02f}, {.45f, 0f}, {.75f, .03f}, {1f, .12f}};
    // Right edge is an exact mirror of the left one, so the mouse is perfectly even.
    private static final float[][] RIGHT = {
            {0f, .86f}, {.20f, .98f}, {.45f, 1f}, {.75f, .97f}, {1f, .88f}};
    private static final float CAP_TOP = 0.17f, CAP_BOTTOM = 0.15f;

    /** @return {x, y, w, h} of a block on screen in GUI-scaled px. */
    public static int[] rect(OverlayConfig.Block b, int bw, int bh, int sw, int sh) {
        int w = Math.round(bw * b.scale), h = Math.round(bh * b.scale);
        return new int[]{b.anchor.baseX(sw, w) + b.offsetX, b.anchor.baseY(sh, h) + b.offsetY, w, h};
    }

    /** @param force true for the settings/edit screens (ignores master toggle and F1). */
    public static void render(DrawContext ctx, MinecraftClient mc, boolean force) {
        OverlayConfig c = OverlayConfig.get();
        if (!force && (!c.overlayEnabled || (c.hideOnF1 && mc.options.hudHidden))) return;
        InputTracker.update(mc);
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        if (c.keyboard.enabled) {
            int[] r = rect(c.keyboard, KeyLayout.width(), KeyLayout.height(), sw, sh);
            var m = ctx.getMatrices();
            m.push(); m.translate(r[0], r[1], 0f); m.scale(c.keyboard.scale, c.keyboard.scale, 1f);
            drawKeyboard(ctx, mc.textRenderer, c, c.keyboard.opacity);
            m.pop();
        }
        if (c.mouse.enabled) {
            int[] r = rect(c.mouse, MW, MH, sw, sh);
            var m = ctx.getMatrices();
            m.push(); m.translate(r[0], r[1], 0f); m.scale(c.mouse.scale, c.mouse.scale, 1f);
            drawMouse(ctx, c, c.mouse.opacity);
            m.pop();
        }
    }

    // ---------------------------------------------------------------- keyboard
    private static void drawKeyboard(DrawContext ctx, TextRenderer tr, OverlayConfig c, float op) {
        final int P = KeyLayout.PITCH, G = KeyLayout.GAP;
        // If the outline color equals the key background, no outline is drawn at all (default).
        final boolean outlined = c.cKeyOutline != c.cKeyBg;
        for (KeyLayout.KeyDef k : KeyLayout.get()) {
            boolean down = false;
            for (int code : k.codes) if (InputTracker.isKeyDown(code)) { down = true; break; }
            k.level = InputTracker.approach(k.level, down);
            float lv = k.level;
            float t = lv * lv * lv * (lv * (lv * 6f - 15f) + 10f); // smootherstep: gentle start AND end

            int x0 = Math.round(k.x * P), y0 = Math.round(k.y * P);
            int x1 = Math.round((k.x + k.w) * P) - G, y1 = y0 + P - G;
            // press animation: the key shrinks ~8% and sinks half a pixel, eased with t
            var mat = ctx.getMatrices();
            float sq = c.keySquish ? 1f - 0.08f * t : 1f;float kcx = (x0 + x1) / 2f, kcy = (y0 + y1) / 2f + (c.keySquish ? 0.5f * t : 0f);
            mat.push(); mat.translate(kcx, kcy, 0f); mat.scale(sq, sq, 1f); mat.translate(-(x0 + x1) / 2f, -(y0 + y1) / 2f, 0f);
            int bg = argb(lerp(c.cKeyBg, c.cKeyPressed, t), op);
            if (outlined) {
                ctx.fill(x0, y0, x1, y1, argb(c.cKeyOutline, op));
                ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, bg);
            } else {
                ctx.fill(x0, y0, x1, y1, bg);
            }

            if (!k.label.isEmpty()) {
                int col = argb(lerp(c.cKeyText, c.cKeyPressedText, t), op);
                String[] lines = k.label.split("\n");
                int lh = tr.fontHeight;
                int ty = y0 + ((y1 - y0) - (lines.length * lh - 1)) / 2 + 1;
                for (String line : lines) {
                    ctx.drawText(tr, line, x0 + ((x1 - x0) - tr.getWidth(line)) / 2, ty, col, false);
                    ty += lh;
                }
            }
            mat.pop();
        }
    }

    // ---------------------------------------------------------------- mouse
    private static float curve(float[][] p, float t) {
        for (int i = 1; i < p.length; i++) {
            if (t <= p[i][0]) {
                float f = (t - p[i - 1][0]) / (p[i][0] - p[i - 1][0]);
                f = (1f - (float) Math.cos(f * Math.PI)) * 0.5f; // smooth between control points
                return p[i - 1][1] + (p[i][1] - p[i - 1][1]) * f;
            }
        }
        return p[p.length - 1][1];
    }

    /** Left/right edge of the mouse in px at height fraction t (with rounded top and bottom). */
    private static float[] edges(float t) {
        float l = curve(LEFT, t), r = curve(RIGHT, t);
        float mid = (l + r) / 2f, half = (r - l) / 2f, cap = 1f;
        if (t < CAP_TOP) { float u = 1f - t / CAP_TOP; cap = (float) Math.sqrt(1f - u * u); }
        else if (t > 1f - CAP_BOTTOM) { float u = (t - (1f - CAP_BOTTOM)) / CAP_BOTTOM; cap = (float) Math.sqrt(1f - u * u); }
        return new float[]{(mid - half * cap) * MW, (mid + half * cap) * MW};
    }

    /** Horizontal span with anti-aliased (partially transparent) end pixels, so curves look smooth. */
    private static void span(DrawContext ctx, int y, float a, float b, int rgb, float alpha) {
        if (b <= a) return;
        int ia = (int) Math.ceil(a), ib = (int) Math.floor(b);
        if (ib < ia) { ctx.fill((int) Math.floor(a), y, (int) Math.floor(a) + 1, y + 1, argb(rgb, alpha * (b - a))); return; }
        if (ib > ia) ctx.fill(ia, y, ib, y + 1, argb(rgb, alpha));
        float ca = ia - a, cb = b - ib;
        if (ca > 0.02f) ctx.fill(ia - 1, y, ia, y + 1, argb(rgb, alpha * ca));
        if (cb > 0.02f) ctx.fill(ib, y, ib + 1, y + 1, argb(rgb, alpha * cb));
    }

    private static void drawMouse(DrawContext ctx, OverlayConfig c, float op) {
        final int cx = MW / 2;
        final int seamY = Math.round(MH * 0.33f);
        float l = InputTracker.button[0], r = InputTracker.button[1], m = InputTracker.button[2];

        // flat body with a 1px rim; no texture
        for (int y = 0; y < MH; y++) {
            float[] e = edges((y + 0.5f) / MH);
            span(ctx, y, e[0] - 1f, e[1] + 1f, c.cMouseOutline, op);
            span(ctx, y, e[0], e[1], c.cMouseBody, op);
            if (y < seamY) { // pressed button highlight
                if (l > 0.01f) span(ctx, y, e[0], cx - 1, c.cMousePressed, op * l * 0.85f);
                if (r > 0.01f) span(ctx, y, cx + 1, e[1], c.cMousePressed, op * r * 0.85f);
            }
        }

        // thin seams between buttons and body
        float[] es = edges((seamY + 0.5f) / MH);
        span(ctx, seamY, es[0], es[1], 0x000000, op * 0.7f);
        ctx.fill(cx - 1, 0, cx + 1, seamY, argb(0x000000, op * 0.7f));// scroll wheel with blue glow
        int wy0 = Math.round(MH * 0.07f), wh = Math.round(MH * 0.17f);
        ctx.fill(cx - 4, wy0 - 1, cx + 4, wy0 + wh + 1, argb(c.cMouseGlow, op * 0.30f));
        ctx.fill(cx - 3, wy0, cx + 3, wy0 + wh, argb(0x050505, op));
        int off = Math.floorMod((int) Math.floor(InputTracker.scrollPhase), wh);
        for (int i = 0; i * 3 < wh; i++) {
            int ny = wy0 + (i * 3 + off) % wh;
            ctx.fill(cx - 3, ny, cx + 3, ny + 1, argb(c.cMouseGlow, op * 0.9f));
        }
        if (m > 0.01f) ctx.fill(cx - 3, wy0, cx + 3, wy0 + wh, argb(c.cMousePressed, op * m * 0.9f));
        if (c.showScrollAnimation && InputTracker.scrollLevel > 0.01f) {
            int col = argb(c.cMousePressed, op * InputTracker.scrollLevel);
            if (InputTracker.scrollDir > 0) { int ay = wy0 - 6; for (int k = 0; k < 4; k++) ctx.fill(cx - k, ay + k, cx + k + 1, ay + k + 1, col); }
            else { int ay = wy0 + wh + 3; for (int k = 0; k < 4; k++) ctx.fill(cx - (3 - k), ay + k, cx + (3 - k) + 1, ay + k + 1, col); }
        }
    }

    // ---------------------------------------------------------------- helpers
    public static int argb(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
