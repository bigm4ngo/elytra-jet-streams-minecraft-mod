package dev.jetstreams.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.jetstreams.client.gui.JetStreamsConfigScreen;

/**
 * ModMenu integration. Loaded by Fabric only when ModMenu is installed
 * (declared under the {@code modmenu} entrypoint), so the compile-only dependency
 * is never a runtime requirement.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return JetStreamsConfigScreen::new;
    }
}
