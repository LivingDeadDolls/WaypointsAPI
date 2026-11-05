package waypointsapi.api;

/**
 * Given to {@link WaypointsPlugin#register}; implemented by Waypoints API (later versions may add methods). Use it
 * only inside {@code register}.
 */
public interface WaypointRegistry {
    /**
     * Adds a kind of waypoints: a tab of the list, with its own settings. Kinds are shown in the order they were
     * registered (the order the mods were loaded in).
     *
     * @return the kind's handle, to give its places through
     * @throws IllegalArgumentException when a kind with the same id was registered before
     */
    WaypointKind register(KindDefinition kind);
}
