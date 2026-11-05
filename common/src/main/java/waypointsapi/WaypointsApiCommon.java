package waypointsapi;

import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.platform.Services;
import waypointsapi.waypoint.WaypointSync;

/** Loader-independent startup, called from the loader's client entrypoint. */
public final class WaypointsApiCommon {
    private WaypointsApiCommon() {
    }

    /** On the client thread, before the other mods' plugins are loaded. */
    public static void initClient() {
        Constants.LOG.info("{} loaded on {}", Constants.MOD_NAME, Services.PLATFORM.getPlatformName());
        WaypointsApiConfig.HANDLER.load();
        WaypointSync.init();
    }
}
