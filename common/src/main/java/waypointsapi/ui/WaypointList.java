package waypointsapi.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import waypointsapi.waypoint.Waypoint;

/** The rows of one kind's tab of the list: its waypoints of every dimension, filtered and sorted. */
public final class WaypointList {
    public enum DimensionFilter {
        ALL, CURRENT, OVERWORLD, NETHER, END
    }

    public enum StateFilter {
        ALL,
        /** Shown in the world now. */
        SHOWN,
        UNEXPLORED,
        EXPLORED,
        PINNED
    }

    public enum Sort {
        DISTANCE, NAME, GROUP,
        /** By distance, keeping only the nearest of each group. */
        NEAREST_OF_EACH_GROUP
    }

    /**
     * @param name      as shown (the player's, the mod's or the group's)
     * @param groupName the group's name
     * @param distance  horizontal distance from the player, or infinity in another dimension
     */
    public record Row(Waypoint waypoint, String name, String groupName, double distance) {
        public String key() {
            return waypoint.key();
        }
    }

    /** What the filters are set to. {@code hiddenGroups} are left out unless searched for; {@code shownKeys} are those shown in the world now. */
    public record Filter(String search, DimensionFilter dimension, StateFilter state, Sort sort, Predicate<Identifier> hiddenGroup, Set<String> shownKeys) {
    }

    /** Names a waypoint and a group, for the rows. */
    public interface Names {
        String name(Waypoint waypoint);

        String groupName(Waypoint waypoint);
    }

    private WaypointList() {
    }

    public static List<Row> rows(Collection<Waypoint> waypoints, ResourceKey<Level> current, Vec3 player, Names names) {
        List<Row> rows = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            rows.add(new Row(waypoint, names.name(waypoint), names.groupName(waypoint), distance(waypoint, current, player)));
        }
        return rows;
    }

    public static List<Row> filter(List<Row> rows, Filter filter, ResourceKey<Level> current) {
        String needle = filter.search().strip().toLowerCase(Locale.ROOT);
        List<Row> kept = new ArrayList<>();
        for (Row row : rows) {
            boolean searchedFor = !needle.isEmpty() && (row.name().toLowerCase(Locale.ROOT).contains(needle)
                    || row.groupName().toLowerCase(Locale.ROOT).contains(needle) || row.waypoint().group().toString().contains(needle));
            if (!needle.isEmpty() && !searchedFor) continue;
            // A hidden group is left out, unless it was searched for.
            if (filter.hiddenGroup().test(row.waypoint().group()) && !searchedFor) continue;
            if (!matchesDimension(row.waypoint().dimension(), filter.dimension(), current)) continue;
            if (!matchesState(row, filter.state(), filter.shownKeys())) continue;
            kept.add(row);
        }
        Comparator<Row> order = switch (filter.sort()) {
            case DISTANCE, NEAREST_OF_EACH_GROUP -> Comparator.comparingDouble(Row::distance);
            case NAME -> Comparator.comparing((Row row) -> row.name().toLowerCase(Locale.ROOT));
            case GROUP -> Comparator.comparing((Row row) -> row.groupName().toLowerCase(Locale.ROOT));
        };
        kept.sort(order.thenComparingDouble(Row::distance).thenComparing(Row::key));
        if (filter.sort() == Sort.NEAREST_OF_EACH_GROUP) {
            Set<Identifier> seen = new HashSet<>();
            kept.removeIf(row -> !seen.add(row.waypoint().group()));
        }
        return kept;
    }

    private static boolean matchesDimension(ResourceKey<Level> dimension, DimensionFilter filter, ResourceKey<Level> current) {
        return switch (filter) {
            case ALL -> true;
            case CURRENT -> dimension == current;
            case OVERWORLD -> dimension == Level.OVERWORLD;
            case NETHER -> dimension == Level.NETHER;
            case END -> dimension == Level.END;
        };
    }

    private static boolean matchesState(Row row, StateFilter filter, Set<String> shownKeys) {
        return switch (filter) {
            case ALL -> true;
            case SHOWN -> shownKeys.contains(row.key());
            case UNEXPLORED -> !row.waypoint().explored();
            case EXPLORED -> row.waypoint().explored();
            case PINNED -> row.waypoint().show() == Waypoint.Show.PINNED;
        };
    }

    private static double distance(Waypoint waypoint, ResourceKey<Level> current, Vec3 player) {
        if (waypoint.dimension() != current) return Double.POSITIVE_INFINITY;
        double dx = waypoint.pos().getX() + 0.5 - player.x;
        double dz = waypoint.pos().getZ() + 0.5 - player.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
