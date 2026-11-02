package dev.inputoverlay;

import dev.inputoverlay.config.OverlayConfig;
import dev.inputoverlay.gui.OverlayConfigScreen;
import dev.inputoverlay.gui.OverlayScreen;
import dev.inputoverlay.render.OverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Entry point: registers config, hotkeys, and the HUD/screen render hooks. */
public class InputOverlayClient implements ClientModInitializer {
    public static final String MOD_ID = "inputoverlay";
    private static KeyBinding openKey, toggleKey;

    @Override
    public void onInitializeClient() {
        OverlayConfig.load();

        final String cat = "key.category.inputoverlay.main"; // 1.21.4: category is a plain translation key
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inputoverlay.open_settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, cat));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inputoverlay.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        // In-game HUD (drawn only when no screen is open)
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen == null) OverlayRenderer.render(ctx, mc, false);
        });

        // Over other GUIs (inventory, chat, pause menu, ...)
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof OverlayScreen) return;
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                if (OverlayConfig.get().showInGuis) OverlayRenderer.render(ctx, mc, false);
            });
        });
    }

    private void tick(MinecraftClient mc) {
        while (openKey.wasPressed()) {
            OverlayConfig c = OverlayConfig.get();
            boolean rShift = mc.getWindow() != null
                    && GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (!c.requireRightShiftForHotkey || rShift) mc.setScreen(new OverlayConfigScreen(mc.currentScreen));
        }
        while (toggleKey.wasPressed()) {
            OverlayConfig c = OverlayConfig.get();
            c.overlayEnabled = !c.overlayEnabled;
            c.save();
        }
    }
}
