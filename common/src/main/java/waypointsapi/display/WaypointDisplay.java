package waypointsapi.display;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import waypointsapi.waypoint.DimensionProjection;
import waypointsapi.waypoint.Waypoint;

/** Picks the waypoints to show: the dimension's own ones and ones projected from the other dimension, nearest first. */
public final class WaypointDisplay {
    /**
     * A waypoint as shown in the current dimension.
     *
     * @param projected set for a waypoint of another dimension shown at scaled coordinates
     * @param x         where it is shown (block center), in the current dimension
     * @param z         where it is shown (block center), in the current dimension
     * @param distance  horizontal distance from the player
     */
    public record Shown(Waypoint waypoint, Optional<DimensionProjection.Projected> projected, double x, double z, double distance) {
        public boolean isProjected() {
            return projected.isPresent();
        }
    }

    /** What the settings say about showing waypoints. */
    public interface Rules {
        boolean kindShown(Identifier kind);

        boolean groupShown(Identifier kind, Identifier group);

        /** Horizontal blocks. */
        double maxDistance(Identifier kind);

        int maxShown(Identifier kind);

        /** Whether only the nearest waypoint of the group is shown. */
        boolean nearestOnly(Identifier kind, Identifier group);

        boolean showExplored();

        /** Whether the other dimension's waypoints are shown (scaled); pinned ones are anyway. */
        boolean showOtherDimension();
    }

    private WaypointDisplay() {
    }

    /**
     * Per kind: the nearest within its distance, at most its count (of a group the settings keep to its nearest, only
     * that one; a projected one counts as a group of its own). Besides those, every pinned waypoint, however far and
     * whatever its kind or group; one set never to show never is. Explored ones only when the settings say so.
     *
     * @param own   the current dimension's waypoints
     * @param other the waypoints of the dimension projected into this one (empty if none)
     */
    public static List<Shown> select(ResourceKey<Level> dimension, Vec3 player, List<Waypoint> own, List<DimensionProjection.Projected> other, Rules rules) {
        Map<Identifier, List<Shown>> byKind = new LinkedHashMap<>();
        List<Shown> picked = new ArrayList<>();
        for (Waypoint waypoint : own) {
            add(byKind, picked, new Shown(waypoint, Optional.empty(), waypoint.pos().getX() + 0.5, waypoint.pos().getZ() + 0.5, 0), player, rules);
        }
        for (DimensionProjection.Projected projected : other) {
            if (!rules.showOtherDimension() && projected.source().show() != Waypoint.Show.PINNED) continue;
            add(byKind, picked, new Shown(projected.source(), Optional.of(projected), projected.x(), projected.z(), 0), player, rules);
        }
        for (Map.Entry<Identifier, List<Shown>> entry : byKind.entrySet()) {
            List<Shown> shown = entry.getValue();
            shown.sort(Comparator.comparingDouble(Shown::distance));
            Set<String> seen = new HashSet<>();
            shown.removeIf(s -> rules.nearestOnly(s.waypoint().kind(), s.waypoint().group())
                    && !seen.add(s.waypoint().group() + (s.isProjected() ? "|projected" : "")));
            picked.addAll(shown.subList(0, Math.min(shown.size(), Math.max(0, rules.maxShown(entry.getKey())))));
        }
        picked.sort(Comparator.comparingDouble(Shown::distance).thenComparing(s -> s.waypoint().key()));
        return picked;
    }

    /**
     * Whether the world shows the waypoint, distance and count aside: never one set never to show, always a pinned one,
     * else one of a kind and group shown that is not explored (unless explored ones are shown).
     */
    public static boolean drawn(Waypoint waypoint, Rules rules) {
        if (waypoint.show() == Waypoint.Show.HIDDEN) return false;
        if (waypoint.show() == Waypoint.Show.PINNED) return true;
        if (!rules.kindShown(waypoint.kind()) || !rules.groupShown(waypoint.kind(), waypoint.group())) return false;
        return rules.showExplored() || !waypoint.explored();
    }

    private static void add(Map<Identifier, List<Shown>> byKind, List<Shown> pinned, Shown unmeasured, Vec3 player, Rules rules) {
        Waypoint waypoint = unmeasured.waypoint();
        if (!drawn(waypoint, rules)) return;
        double dx = unmeasured.x() - player.x;
        double dz = unmeasured.z() - player.z;
        Shown shown = new Shown(waypoint, unmeasured.projected(), unmeasured.x(), unmeasured.z(), Math.sqrt(dx * dx + dz * dz));
        if (waypoint.show() == Waypoint.Show.PINNED) {
            pinned.add(shown);
            return;
        }
        if (shown.distance() > rules.maxDistance(waypoint.kind())) return;
        byKind.computeIfAbsent(waypoint.kind(), kind -> new ArrayList<>()).add(shown);
    }
}
