package waypointsapi.xaero;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import waypointsapi.Constants;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.kind.Kinds;
import waypointsapi.display.WaypointColors;
import waypointsapi.integration.MapIntegration;
import waypointsapi.integration.MapMarkers;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.WaypointVisibilityType;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

/**
 * Keeps Xaero's World Map showing {@link MapMarkers}, as waypoints of Xaero's Minimap (which the World Map reads)
 * given through its third-party waypoints, the way Xaero's own Waystones support does: one set per dimension, for the
 * World Map only, not saved. Xaero has no public API: these are its own classes, so anything that goes wrong turns
 * the integration off for the session and leaves the game running. Xaero's waypoints come in 17 colors: the nearest
 * one to the kind's color is used. Also adds waypoints to Xaero's own waypoints when asked from the list.
 */
public final class XaeroMarkers {
    /** How often the markers are compared with what Xaero shows (ticks). */
    private static final int UPDATE_TICKS = 10;
    /** Where the waypoints come from, as Xaero groups third-party waypoints. */
    public static final Identifier ORIGIN = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "waypoints");

    /** The markers given to Xaero, by dimension: where they went and which, by id, for the session they were given to. */
    private static final Map<ResourceKey<Level>, Given> SHOWN = new HashMap<>();
    private static MinimapSession shownSession;
    private static int ticks;
    private static boolean broken;

    /** Markers given to Xaero for a dimension: the set of waypoints they went to, and the markers by id. */
    private record Given(ThirdPartyWaypoints target, Map<String, MapMarkers.Marker> markers) {
    }

    private XaeroMarkers() {
    }

    /** Called once when Xaero's Minimap is installed. */
    public static void start() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (broken || ++ticks < UPDATE_TICKS) return;
            ticks = 0;
            try {
                update(mc);
            } catch (RuntimeException | LinkageError e) {
                broken = true;
                Constants.LOG.error("Xaero's Minimap is not as expected; the Xaero integration is off for this session", e);
            }
        });
        MapIntegration.register(MapIntegration.Mod.XAERO, new MapIntegration.Target() {
            @Override
            public String name() {
                return "Xaero's Minimap";
            }

            @Override
            public boolean enabled() {
                return !broken && WaypointsApiConfig.get().mapXaero;
            }

            @Override
            public boolean addWaypoint(waypointsapi.waypoint.Waypoint waypoint) {
                try {
                    return XaeroMarkers.addWaypoint(waypoint);
                } catch (RuntimeException | LinkageError e) {
                    Constants.LOG.warn("Could not add {} to Xaero's waypoints", waypoint.id(), e);
                    return false;
                }
            }
        });
    }

    /** Brings Xaero in line with the markers for now. Runs on the client thread. */
    static void update(Minecraft mc) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session != shownSession) {
            // Another world (or none): what was given went with the old session.
            SHOWN.clear();
            shownSession = session;
        }
        if (session == null) return;
        List<MapMarkers.Marker> markers = mc.level == null || !WaypointsApiConfig.get().mapXaero ? List.of() : MapMarkers.current(mc.level.dimension());
        Map<ResourceKey<Level>, Map<String, MapMarkers.Marker>> wanted = new HashMap<>();
        for (MapMarkers.Marker marker : markers) wanted.computeIfAbsent(marker.dimension(), d -> new HashMap<>()).put(marker.id(), marker);
        Set<ResourceKey<Level>> dimensions = new HashSet<>(SHOWN.keySet());
        dimensions.addAll(wanted.keySet());
        for (ResourceKey<Level> dimension : dimensions) {
            Map<String, MapMarkers.Marker> want = wanted.getOrDefault(dimension, Map.of());
            ThirdPartyWaypoints waypoints = thirdParty(session, dimension);
            Given given = SHOWN.get(dimension);
            if (given == null || given.target() != waypoints) {
                // First time, or Xaero now keeps the dimension's waypoints elsewhere (its world was told apart later): all given
                // again there, and none left behind.
                if (given != null) given.target().clear();
                given = new Given(waypoints, new HashMap<>());
                SHOWN.put(dimension, given);
            }
            Map<String, MapMarkers.Marker> shown = given.markers();
            if (want.equals(shown)) continue;
            for (String id : List.copyOf(shown.keySet())) {
                if (!want.containsKey(id)) {
                    waypoints.remove(id);
                    shown.remove(id);
                }
            }
            for (MapMarkers.Marker marker : want.values()) {
                if (marker.equals(shown.get(marker.id()))) continue;
                waypoints.add(marker.id(), waypoint(marker));
                shown.put(marker.id(), marker);
            }
        }
    }

    /** Waypoints API's third-party waypoints of a dimension of the session's world. */
    static ThirdPartyWaypoints thirdParty(MinimapSession session, ResourceKey<Level> dimension) {
        ThirdPartyWaypoints waypoints = container(session, dimension).getThirdPartyWaypointManager().get(ORIGIN);
        if (!waypoints.hasEnabledStateGetter()) waypoints.setEnabledStateGetter(() -> !broken && WaypointsApiConfig.get().mapXaero);
        return waypoints;
    }

    private static MinimapWorldContainer container(MinimapSession session, ResourceKey<Level> dimension) {
        MinimapWorldRootContainer root = session.getWorldManager().getAutoRootContainer();
        return root.addSubContainer(root.getPath().resolve(session.getDimensionHelper().getDimensionDirectoryName(dimension)));
    }

    /** A marker as a waypoint for the World Map only, not saved: the kind's nearest color and the group's letter. */
    private static Waypoint waypoint(MapMarkers.Marker marker) {
        Waypoint waypoint = new Waypoint(marker.pos().getX(), marker.pos().getY(), marker.pos().getZ(), marker.label(), initials(marker.initial(), marker.label()),
                nearest(marker.color()), WaypointPurpose.NORMAL, true, true);
        waypoint.setVisibility(WaypointVisibilityType.WORLD_MAP_LOCAL);
        return waypoint;
    }

    /** The group's letter, as in its diamond; else the name's first letter. */
    private static String initials(String initial, String name) {
        if (!initial.isEmpty()) return initial;
        return name.isEmpty() ? "?" : new String(Character.toChars(name.codePointAt(0))).toUpperCase(java.util.Locale.ROOT);
    }

    /** Xaero's color nearest to an RGB color. */
    static WaypointColor nearest(int argb) {
        WaypointColor best = WaypointColor.WHITE;
        double bestDistance = Double.MAX_VALUE;
        for (WaypointColor color : WaypointColor.values()) {
            int hex = color.getHex();
            double dr = ((hex >> 16) & 0xFF) - ((argb >> 16) & 0xFF);
            double dg = ((hex >> 8) & 0xFF) - ((argb >> 8) & 0xFF);
            double db = (hex & 0xFF) - (argb & 0xFF);
            double distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = color;
            }
        }
        return best;
    }

    /** Adds a waypoint to Xaero's waypoints (the dimension's current set) and saves them, or updates the one added before. */
    static boolean addWaypoint(waypointsapi.waypoint.Waypoint waypoint) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return false;
        MinimapWorldContainer container = container(session, waypoint.dimension());
        MinimapWorld world = container.getFirstWorld();
        if (world == null) world = container.addWorld("waypoints");
        WaypointSet set = world.getCurrentWaypointSet();
        if (set == null) return false;
        int x = waypoint.pos().getX();
        int y = waypoint.pos().getY();
        int z = waypoint.pos().getZ();
        String name = Kinds.name(waypoint);
        for (Waypoint existing : set.getWaypoints()) {
            // The one added before for this waypoint: at its place with its name (Xaero keeps no data of other mods with a
            // waypoint). One of the player's own at the same place keeps its name: a new one is added next to it.
            if (existing.getX() == x && existing.getY() == y && existing.getZ() == z && !existing.isTemporary() && existing.getName().equals(name)) {
                existing.setWaypointColor(nearest(WaypointColors.of(waypoint.kind(), waypoint.group())));
                save(session, world);
                return true;
            }
        }
        set.add(new Waypoint(x, y, z, name, initials(Kinds.initial(waypoint.kind(), waypoint.group()), name), nearest(WaypointColors.of(waypoint.kind(), waypoint.group())), WaypointPurpose.NORMAL, false, true));
        save(session, world);
        return true;
    }

    private static void save(MinimapSession session, MinimapWorld world) {
        try {
            session.getWorldManagerIO().saveWorld(world);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Waypoints API's waypoints on Xaero's World Map of a dimension now, by id (tests). */
    public static Map<String, Waypoint> shown(ResourceKey<Level> dimension) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return Map.of();
        return Map.copyOf(thirdParty(session, dimension).getWaypoints());
    }

    /** Xaero's own (saved) waypoints of a dimension's current set (tests). */
    public static List<Waypoint> saved(ResourceKey<Level> dimension) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return List.of();
        MinimapWorld world = container(session, dimension).getFirstWorld();
        if (world == null || world.getCurrentWaypointSet() == null) return List.of();
        List<Waypoint> waypoints = new java.util.ArrayList<>();
        world.getCurrentWaypointSet().getWaypoints().forEach(waypoints::add);
        return waypoints;
    }
}
