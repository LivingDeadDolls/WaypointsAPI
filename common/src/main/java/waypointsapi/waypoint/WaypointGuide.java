package waypointsapi.waypoint;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import waypointsapi.kind.Kinds;

/**
 * Guides the player to one waypoint: pins it (so it is shown, with an arrow at the screen's edge when out of view)
 * and says in the chat where it is. One at a time; the pin it put on is taken off again when the player gets there
 * (the waypoint becomes explored), when another one is guided to, or when the player leaves the world.
 */
public final class WaypointGuide {
    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    /** The waypoint guided to, and how it was shown before the guide pinned it. */
    public record Target(Waypoint waypoint, Waypoint.Show before) {
    }

    private static Target target;

    private WaypointGuide() {
    }

    public static Optional<Target> target() {
        return Optional.ofNullable(target);
    }

    /** The nearest of the waypoints in the player's dimension that are not explored and not set never to show. */
    public static Optional<Waypoint> nearest(Collection<Waypoint> waypoints, Vec3 player, Predicate<Waypoint> wanted) {
        return waypoints.stream()
                .filter(waypoint -> !waypoint.explored() && waypoint.show() != Waypoint.Show.HIDDEN && wanted.test(waypoint))
                .min(Comparator.comparingDouble((Waypoint waypoint) -> distanceSquared(waypoint, player)).thenComparing(Waypoint::key));
    }

    /** Guides to the nearest of {@code waypoints} (all in the player's dimension), or says there is none. True if it guided. */
    public static boolean guideNearest(Minecraft mc, Collection<Waypoint> waypoints, Component what) {
        if (mc.player == null) return false;
        Optional<Waypoint> nearest = nearest(waypoints, mc.player.position(), waypoint -> true);
        if (nearest.isPresent()) {
            guide(mc, nearest.get());
            return true;
        }
        mc.player.sendSystemMessage(Component.translatable("waypointsapi.guide.none", what));
        return false;
    }

    /** Guides to a waypoint: pins it and says where it is. */
    public static void guide(Minecraft mc, Waypoint waypoint) {
        release();
        Waypoint current = WaypointStore.current(waypoint).orElse(waypoint);
        if (current.show() != Waypoint.Show.PINNED) WaypointStore.setShow(current, Waypoint.Show.PINNED);
        target = new Target(current, current.show());
        if (mc.player != null) mc.player.sendSystemMessage(message(current, mc.player.position()));
    }

    /** What the chat says when guiding: the name, how far, which way, and where. */
    public static Component message(Waypoint waypoint, Vec3 player) {
        double dx = waypoint.pos().getX() + 0.5 - player.x;
        double dz = waypoint.pos().getZ() + 0.5 - player.z;
        return Component.translatable("waypointsapi.guide.to", Kinds.name(waypoint), Math.round(Math.sqrt(dx * dx + dz * dz)),
                Component.translatable("waypointsapi.direction." + direction(dx, dz)), waypoint.pos().getX(), waypoint.pos().getZ());
    }

    /** One of the eight compass points, from the player towards an offset: north is -z, east is +x. */
    public static String direction(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        return DIRECTIONS[Math.floorMod(Math.round(degrees / 45.0), 8)];
    }

    /** A waypoint has just become explored: if it was the one guided to, the guide ends and says so. */
    static void onExplored(Minecraft mc, Waypoint waypoint) {
        if (target == null || !target.waypoint().key().equals(waypoint.key())) return;
        release();
        if (mc.player != null) mc.player.sendSystemMessage(Component.translatable("waypointsapi.guide.arrived", Kinds.name(waypoint)));
    }

    /** Ends the guide, taking off the pin it put on (one the player changed since is left as it is). */
    public static void release() {
        Target ending = target;
        target = null;
        if (ending == null || ending.before() == Waypoint.Show.PINNED) return;
        WaypointStore.current(ending.waypoint())
                .filter(waypoint -> waypoint.show() == Waypoint.Show.PINNED)
                .ifPresent(waypoint -> WaypointStore.setShow(waypoint, ending.before()));
    }

    private static double distanceSquared(Waypoint waypoint, Vec3 player) {
        double dx = waypoint.pos().getX() + 0.5 - player.x;
        double dz = waypoint.pos().getZ() + 0.5 - player.z;
        return dx * dx + dz * dz;
    }
}
