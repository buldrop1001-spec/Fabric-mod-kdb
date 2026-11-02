package dev.inputoverlay.gui;

import dev.inputoverlay.config.OverlayConfig;
import dev.inputoverlay.render.OverlayRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

/**
 * Built-in settings screen (vanilla widgets only, so it works without Cloth Config).
 * Panel sits on the right; the live preview is drawn on top of the world on the left.
 */
public class OverlayConfigScreen extends Screen implements OverlayScreen {
    private final Screen parent;
    private int tab = 0; // 0 keyboard, 1 mouse, 2 general, 3 colors
    private record Label(int x, int y, String text) {}
    private final List<Label> labels = new ArrayList<>();

    public OverlayConfigScreen(Screen parent) {
        super(Text.translatable("inputoverlay.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        OverlayConfig c = OverlayConfig.get();
        int pw = tab == 3 ? 320 : 200, px = width - pw - 10, y = 8;

        String[] names = {"Keyboard", "Mouse", "General", "Colors"};
        int tw = pw / 4;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            ButtonWidget b = ButtonWidget.builder(Text.literal(names[i]), btn -> { tab = idx; clearAndInit(); })
                    .dimensions(px + i * tw, y, tw - 2, 18).build();
            b.active = i != tab;
            addDrawableChild(b);
        }
        y += 28;

        if (tab == 0 || tab == 1) {
            OverlayConfig.Block b = tab == 0 ? c.keyboard : c.mouse;
            toggle(px, y, pw, tab == 0 ? "Show keyboard" : "Show mouse", () -> b.enabled, v -> b.enabled = v); y += 22;
            addDrawableChild(ButtonWidget.builder(Text.literal("Anchor: " + b.anchor), btn -> {
                b.anchor = b.anchor.next(); b.offsetX = 0; b.offsetY = 0; clearAndInit();
            }).dimensions(px, y, pw, 18).build()); y += 22;
            labels.add(new Label(px, y + 5, "X")); intField(px + 12, y, 76, () -> b.offsetX, v -> b.offsetX = v);
            labels.add(new Label(px + 100, y + 5, "Y")); intField(px + 112, y, 76, () -> b.offsetY, v -> b.offsetY = v); y += 22;
            slider(px, y, pw, "Scale", 0.25, 4.0, () -> b.scale, v -> b.scale = (float) v, "%.2fx"); y += 22;
            slider(px, y, pw, "Opacity", 0.05, 1.0, () -> b.opacity, v -> b.opacity = (float) v, "%.2f"); y += 22;
            addDrawableChild(ButtonWidget.builder(Text.literal("Edit layout (drag & drop)"),
                    btn -> client.setScreen(new EditLayoutScreen(this))).dimensions(px, y, pw, 18).build()); y += 22;
            if (tab == 1) {
                toggle(px, y, pw, "Scroll animation", () -> c.showScrollAnimation, v -> c.showScrollAnimation = v);
            }
        } else if (tab == 2) {
            toggle(px, y, pw, "Overlay enabled", () -> c.overlayEnabled, v -> c.overlayEnabled = v); y += 20;
            toggle(px, y, pw, "Show in GUIs", () -> c.showInGuis, v -> c.showInGuis = v); y += 20;
            toggle(px, y, pw, "Hide on F1", () -> c.hideOnF1, v -> c.hideOnF1 = v); y += 20;
            toggle(px, y, pw, "Track input in GUIs", () -> c.trackInputsInGuis, v -> c.trackInputsInGuis = v); y += 20;
            toggle(px, y, pw, "Hotkey needs R-Shift", () -> c.requireRightShiftForHotkey, v -> c.requireRightShiftForHotkey = v); y += 20;
            toggle(px, y, pw, "Key press squish", () -> c.keySquish, v -> c.keySquish = v); y += 20;
            slider(px, y, pw, "Press fade", 0, 300, () -> c.fadeInMillis, v -> c.fadeInMillis = (int) Math.round(v), "%.0f ms"); y += 20;slider(px, y, pw, "Release fade", 0, 500, () -> c.fadeOutMillis, v -> c.fadeOutMillis = (int) Math.round(v), "%.0f ms"); y += 20;
            addDrawableChild(ButtonWidget.builder(Text.literal("Reset ALL to defaults"), btn -> {
                OverlayConfig.resetToDefaults(); clearAndInit();
            }).dimensions(px, y, pw, 18).build());
        } else {
            record CE(String name, Supplier<String> get, Consumer<String> set) {}
            CE[] list = {
                    new CE("Key bg", () -> c.keyBackground, v -> c.keyBackground = v),
                    new CE("Key text", () -> c.keyText, v -> c.keyText = v),
                    new CE("Key outline*", () -> c.keyOutline, v -> c.keyOutline = v),
                    new CE("Pressed bg", () -> c.keyPressed, v -> c.keyPressed = v),
                    new CE("Pressed text", () -> c.keyPressedText, v -> c.keyPressedText = v),
                    new CE("Mouse body", () -> c.mouseBody, v -> c.mouseBody = v),
                    new CE("Mouse edge", () -> c.mouseOutline, v -> c.mouseOutline = v),
                    new CE("Wheel glow", () -> c.mouseGlow, v -> c.mouseGlow = v),
                    new CE("Mouse pressed", () -> c.mousePressed, v -> c.mousePressed = v)};
            for (int i = 0; i < list.length; i++) {
                int cx = px + (i / 5) * 162, cy = y + (i % 5) * 22;
                CE e = list[i];
                labels.add(new Label(cx, cy + 5, e.name()));
                TextFieldWidget f = new TextFieldWidget(textRenderer, cx + 72, cy, 80, 18, Text.empty());
                f.setMaxLength(7); f.setText(e.get().get());
                f.setChangedListener(s -> { if (OverlayConfig.isValidHex(s)) { e.set().accept(s.startsWith("#") ? s : "#" + s); c.refreshColors(); } });
                addDrawableChild(f);
            }
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close())
                .dimensions(px, height - 26, pw, 18).build());
    }

    // ---- small widget helpers
    private void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")), btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(Text.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")));
        }).dimensions(x, y, w, 18).build());
    }

    private void intField(int x, int y, int w, IntSupplier get, IntConsumer set) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setMaxLength(6); f.setText(String.valueOf(get.getAsInt()));
        f.setChangedListener(s -> { try { set.accept(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {} });
        addDrawableChild(f);
    }

    private void slider(int x, int y, int w, String label, double min, double max, DoubleSupplier get, DoubleConsumer set, String fmt) {
        addDrawableChild(new SliderWidget(x, y, w, 18, Text.empty(), (get.getAsDouble() - min) / (max - min)) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Text.literal(label + ": " + String.format(fmt, min + (max - min) * value))); }
            @Override protected void applyValue() { set.accept(min + (max - min) * value); }
        });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (Label l : labels) ctx.drawText(textRenderer, l.text(), l.x(), l.y(), 0xFFFFFFFF, true);
        OverlayRenderer.render(ctx, client, true); // live preview
    }

    @Override public void removed() { OverlayConfig.get().save(); }
    @Override public void close() { client.setScreen(parent); }
}
