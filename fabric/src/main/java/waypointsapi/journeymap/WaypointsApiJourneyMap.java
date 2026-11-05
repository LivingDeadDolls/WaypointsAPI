package waypointsapi.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import waypointsapi.Constants;

/**
 * JourneyMap's entry point ("journeymap" in fabric.mod.json). JourneyMap creates it only when it is installed, so
 * nothing here or in {@link JourneyMapMarkers} loads without it.
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public final class WaypointsApiJourneyMap implements IClientPlugin {
    @Override
    public String getModId() {
        return Constants.MOD_ID;
    }

    @Override
    public void initialize(IClientAPI api) {
        JourneyMapMarkers.start(api);
    }
}
