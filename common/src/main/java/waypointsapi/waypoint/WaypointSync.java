package waypointsapi.waypoint;

import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import seedengine.api.SeedEngineApi;
import waypointsapi.kind.Kinds;

/**
 * Keeps {@link WaypointStore} on the current server and seed (from Seed Engine API), tells the kinds when that
 * changes, and saves now and then.
 */
public final class WaypointSync {
    private static final int SAVE_INTERVAL_TICKS = 100;
    private static int ticks;

    private WaypointSync() {
    }

    /** Call once at startup, on the client thread. */
    public static void init() {
        SeedEngineApi.addSeedListener(() -> update(Minecraft.getInstance()));
    }

    public static void tick(Minecraft mc) {
        update(mc);
        if (++ticks >= SAVE_INTERVAL_TICKS) {
            ticks = 0;
            WaypointStore.saveIfDirty();
        }
    }

    /** Leaving a world: write everything out and let go of the scope. */
    public static void onLeave() {
        WaypointGuide.release();
        if (WaypointStore.setScope(null)) Kinds.scopeChanged();
    }

    private static void update(Minecraft mc) {
        WaypointStore.Scope scope = null;
        OptionalLong seed = SeedEngineApi.currentSeed();
        if (mc.level != null && seed.isPresent()) scope = new WaypointStore.Scope(ServerKey.of(mc), seed.getAsLong());
        // Another seed or server: the guide's pin is taken off in the waypoints it was put on.
        if (!WaypointStore.scope().equals(java.util.Optional.ofNullable(scope))) WaypointGuide.release();
        if (WaypointStore.setScope(scope)) Kinds.scopeChanged();
    }
}
