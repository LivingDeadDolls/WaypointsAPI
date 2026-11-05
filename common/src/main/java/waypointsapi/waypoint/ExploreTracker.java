package waypointsapi.waypoint;

import net.minecraft.client.Minecraft;
import waypointsapi.display.ExploredToast;
import waypointsapi.kind.Kinds;

/**
 * Marks a waypoint explored once the player gets there: inside one of its areas, or near enough to it (its kind
 * decides). Says so with a toast.
 */
public final class ExploreTracker {
    /** How often the player's place is checked (ticks). */
    private static final int CHECK_TICKS = 10;
    private static int ticks;

    private ExploreTracker() {
    }

    public static void tick(Minecraft mc) {
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        if (mc.player == null || mc.level == null || WaypointStore.scope().isEmpty()) return;
        for (Kinds.Kind kind : Kinds.all()) {
            boolean heightless = kind.definition().heightless();
            for (Waypoint waypoint : WaypointStore.all(kind.id(), mc.level.dimension())) {
                if (waypoint.explored() || !waypoint.reachedBy(mc.player.getX(), mc.player.getY(), mc.player.getZ(), heightless)) continue;
                WaypointStore.setExplored(waypoint, true);
                Waypoint explored = waypoint.withExplored(true);
                WaypointGuide.onExplored(mc, explored);
                mc.gui.toastManager().addToast(new ExploredToast(explored));
            }
        }
    }
}
