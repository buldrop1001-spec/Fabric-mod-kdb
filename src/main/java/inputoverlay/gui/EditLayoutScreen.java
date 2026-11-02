package dev.inputoverlay.gui;

import dev.inputoverlay.config.OverlayConfig;
import dev.inputoverlay.render.KeyLayout;
import dev.inputoverlay.render.OverlayRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Drag-and-drop edit mode. Dragging is done by polling the left mouse button each frame
 * (instead of overriding mouseClicked/Dragged) so it survives GUI-input API changes between versions.
 */
public class EditLayoutScreen extends Screen implements OverlayScreen {
    private final Screen parent;
    private ButtonWidget done, reset;
    private int drag = 0; // 0 none, 1 keyboard, 2 mouse
    private int grabX, grabY;
    private boolean wasDown;

    public EditLayoutScreen(Screen parent) {
        super(Text.literal("Edit overlay layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        done = addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 105, height - 28, 100, 20).build());
        reset = addDrawableChild(ButtonWidget.builder(Text.literal("Reset positions"), b -> {
            OverlayConfig c = OverlayConfig.get();
            c.keyboard.anchor = OverlayConfig.Anchor.TOP_LEFT; c.keyboard.offsetX = 10; c.keyboard.offsetY = 10;
            c.mouse.anchor = OverlayConfig.Anchor.TOP_LEFT; c.mouse.offsetX = 150; c.mouse.offsetY = 8;
        }).dimensions(width / 2 + 5, height - 28, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        OverlayRenderer.render(ctx, client, true);

        OverlayConfig c = OverlayConfig.get();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int[] kb = OverlayRenderer.rect(c.keyboard, KeyLayout.width(), KeyLayout.height(), sw, sh);
        int[] ms = OverlayRenderer.rect(c.mouse, OverlayRenderer.MW, OverlayRenderer.MH, sw, sh);

        boolean down = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !wasDown && !done.isMouseOver(mx, my) && !reset.isMouseOver(mx, my)) {
            if (inside(ms, mx, my) && c.mouse.enabled) { drag = 2; grabX = mx - ms[0]; grabY = my - ms[1]; }
            else if (inside(kb, mx, my) && c.keyboard.enabled) { drag = 1; grabX = mx - kb[0]; grabY = my - kb[1]; }
        }
        if (down && drag != 0) {
            OverlayConfig.Block b = drag == 1 ? c.keyboard : c.mouse;
            int[] r = drag == 1 ? kb : ms;
            int nx = Math.max(-(r[2] - 8), Math.min(sw - 8, mx - grabX));
            int ny = Math.max(-(r[3] - 8), Math.min(sh - 8, my - grabY));
            b.offsetX = nx - b.anchor.baseX(sw, r[2]);
            b.offsetY = ny - b.anchor.baseY(sh, r[3]);
        }
        if (!down && wasDown && drag != 0) { drag = 0; c.save(); }
        wasDown = down;

        if (c.keyboard.enabled) ctx.drawBorder(kb[0] - 2, kb[1] - 2, kb[2] + 4, kb[3] + 4, drag == 1 ? 0xFFFFFF55 : (inside(kb, mx, my) ? 0xFFFFFFFF : 0x88FFFFFF));
        if (c.mouse.enabled) ctx.drawBorder(ms[0] - 2, ms[1] - 2, ms[2] + 4, ms[3] + 4, drag == 2 ? 0xFFFFFF55 : (inside(ms, mx, my) ? 0xFFFFFFFF : 0x88FFFFFF));
        String hint = "Drag the keyboard or mouse block. Press Esc / Done when finished.";
        ctx.drawText(textRenderer, hint, (sw - textRenderer.getWidth(hint)) / 2, 12, 0xFFFFFFFF, true);
    }

    private static boolean inside(int[] r, int x, int y) { return x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3]; }

    @Override public void removed() { OverlayConfig.get().save(); }
    @Override public void close() { client.setScreen(parent); }
                                            }
