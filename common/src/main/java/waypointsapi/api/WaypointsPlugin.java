package waypointsapi.api;

/**
 * What another mod implements to show its places as waypoints. Register the implementation under the
 * {@code waypointsapi} entrypoint in fabric.mod.json (client side). Waypoints API creates it once when the client
 * starts and calls {@link #register} once, on the client thread, before any world is joined. A plugin that throws is
 * logged with its mod's id and skipped; the other plugins still run.
 * <p>
 * This interface never gets another method, so a plugin built against any version of Waypoints API keeps working with
 * later ones.
 */
public interface WaypointsPlugin {
    /** Adds this mod's kinds of waypoints with {@link WaypointRegistry#register}. */
    void register(WaypointRegistry registry);
}
