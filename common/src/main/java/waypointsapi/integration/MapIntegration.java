package waypointsapi.integration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import waypointsapi.waypoint.Waypoint;

/** The map mods that waypoints can be added to as their own waypoints, each registered by its integration when loaded. */
public final class MapIntegration {
    /** A map mod's side of the integration. */
    public interface Target {
        /** The map mod's name, for messages. */
        String name();

        /** Whether the integration is turned on in the settings. */
        boolean enabled();

        /** Adds the waypoint to the map mod's waypoints (or updates the one added before). False if it could not. */
        boolean addWaypoint(Waypoint waypoint);
    }

    /** The map mods with an integration. */
    public enum Mod {
        JOURNEYMAP, XAERO, VOXELMAP
    }

    private record Registered(Mod mod, Target target) {
    }

    private static final List<Registered> TARGETS = new CopyOnWriteArrayList<>();

    private MapIntegration() {
    }

    public static void register(Mod mod, Target target) {
        TARGETS.add(new Registered(mod, target));
    }

    /** Whether a map mod's integration is loaded (turned on or not). */
    public static boolean loaded(Mod mod) {
        return TARGETS.stream().anyMatch(registered -> registered.mod() == mod);
    }

    /** The map mods to add waypoints to: those loaded and turned on. */
    public static List<Target> targets() {
        return TARGETS.stream().map(Registered::target).filter(Target::enabled).toList();
    }
}
