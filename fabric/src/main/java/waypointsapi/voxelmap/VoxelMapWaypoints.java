package waypointsapi.voxelmap;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.WaypointManager;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import java.util.List;
import java.util.TreeSet;
import waypointsapi.Constants;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.kind.Kinds;
import waypointsapi.display.WaypointColors;
import waypointsapi.integration.MapIntegration;
import waypointsapi.waypoint.DimensionProjection;

/**
 * Adds waypoints to VoxelMap's waypoints when asked from the list. VoxelMap has no API and keeps one list of waypoints,
 * saved as soon as one is added, with no waypoints only for its world map: so it gets no automatic marks, only the
 * waypoints the player adds. These are VoxelMap's own classes: anything that goes wrong turns the integration off.
 */
public final class VoxelMapWaypoints {
    /** VoxelMap's icon for the waypoints added. */
    private static final String ICON = "point";

    private static boolean broken;

    private VoxelMapWaypoints() {
    }

    /** Called once when VoxelMap is installed. */
    public static void start() {
        MapIntegration.register(MapIntegration.Mod.VOXELMAP, new MapIntegration.Target() {
            @Override
            public String name() {
                return "VoxelMap";
            }

            @Override
            public boolean enabled() {
                return !broken && WaypointsApiConfig.get().mapVoxelMap;
            }

            @Override
            public boolean addWaypoint(waypointsapi.waypoint.Waypoint waypoint) {
                try {
                    return VoxelMapWaypoints.addWaypoint(waypoint);
                } catch (RuntimeException | LinkageError e) {
                    broken = true;
                    Constants.LOG.error("VoxelMap is not as expected; the VoxelMap integration is off for this session", e);
                    return false;
                }
            }
        });
    }

    /** Adds a waypoint to VoxelMap's waypoints (which saves them), or updates the one added before for it. */
    static boolean addWaypoint(waypointsapi.waypoint.Waypoint waypoint) {
        VoxelMap voxelMap = VoxelConstants.getVoxelMapInstance();
        WaypointManager manager = voxelMap.getWaypointManager();
        DimensionContainer dimension = voxelMap.getDimensionManager().getDimensionContainerByIdentifier(waypoint.dimension().identifier());
        if (manager == null || dimension == null) return false;
        // VoxelMap keeps a waypoint's x and z in the Overworld's scale: a Nether one at 8 times its coordinates.
        double scale = DimensionProjection.scale(waypoint.dimension());
        // Cut toward zero, as VoxelMap does with its own.
        int x = (int) (waypoint.pos().getX() * scale);
        int z = (int) (waypoint.pos().getZ() * scale);
        int y = waypoint.pos().getY();
        String name = Kinds.name(waypoint);
        int rgb = WaypointColors.of(waypoint.kind(), waypoint.group());
        float red = ((rgb >> 16) & 0xFF) / 255f;
        float green = ((rgb >> 8) & 0xFF) / 255f;
        float blue = (rgb & 0xFF) / 255f;
        for (Waypoint existing : manager.getWaypoints()) {
            // The one added before for this waypoint: at its place, in its dimension, with its name (VoxelMap keeps no data
            // of other mods). One of the player's own at the same place keeps its name: a new one is added next to it.
            if (existing.x == x && existing.z == z && existing.y == y && existing.dimensions.contains(dimension) && name.equals(existing.name)) {
                existing.red = red;
                existing.green = green;
                existing.blue = blue;
                manager.saveWaypoints();
                return true;
            }
        }
        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        dimensions.add(dimension);
        manager.addWaypoint(new Waypoint(name, x, z, y, true, red, green, blue, ICON, manager.getCurrentSubworldDescriptor(false), dimensions));
        return true;
    }

    /** VoxelMap's waypoints now (tests). */
    public static List<Waypoint> all() {
        return List.copyOf(VoxelConstants.getVoxelMapInstance().getWaypointManager().getWaypoints());
    }
}
