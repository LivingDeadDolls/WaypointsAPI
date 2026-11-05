package waypointsapi;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import waypointsapi.config.WaypointsApiConfig;

/** Opens the settings screen from Mod Menu's config button. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WaypointsApiConfig::createScreen;
    }
}
