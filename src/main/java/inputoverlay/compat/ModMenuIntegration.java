package dev.inputoverlay.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.inputoverlay.gui.OverlayConfigScreen;

/** Optional: only loaded when Mod Menu is installed. Opens the built-in settings screen. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() { return OverlayConfigScreen::new; }
}
