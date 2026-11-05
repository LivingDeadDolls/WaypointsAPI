package waypointsapi.waypoint;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import waypointsapi.api.Place;

/**
 * A place of a kind that the player can see, name and go to. Immutable; a change makes a new one.
 *
 * @param kind          the kind it belongs to
 * @param id            unique within the kind and the dimension
 * @param pos           where the beam stands
 * @param group         the group within the kind (the kind's id when the mod gave none)
 * @param defaultName   the name the mod gave; empty for the group's name
 * @param areas         the blocks it takes up; inside one, it becomes explored
 * @param exploreRadius without areas: this close to {@code pos}, it becomes explored (0 for never by itself)
 * @param customName    the name the player gave it
 * @param show          whether it is shown in the world: by the usual rules, always (pinned), or never
 * @param explored      whether the player has been there (or marked it so by hand)
 */
public record Waypoint(
        Identifier kind,
        String id,
        ResourceKey<Level> dimension,
        BlockPos pos,
        Identifier group,
        Optional<Component> defaultName,
        List<BoundingBox> areas,
        int exploreRadius,
        Optional<String> customName,
        Show show,
        boolean explored
) {
    /** Whether a waypoint is shown in the world. */
    public enum Show {
        /** By the usual rules: its kind and group shown, near enough, among the nearest. */
        AUTO,
        /** Always, however far and whatever its group: a place the player is heading for. */
        PINNED,
        /** Never. */
        HIDDEN
    }

    /** A new waypoint from a mod's place: nothing set by the player yet. */
    public static Waypoint of(Identifier kind, Place place) {
        return new Waypoint(kind, place.id(), place.dimension(), place.pos(), place.group().orElse(kind), place.name(), place.areas(),
                place.exploreRadius(), Optional.empty(), Show.AUTO, false);
    }

    /** This waypoint with what the mod gives replaced by a newer place of the same id, and what the player set kept. */
    public Waypoint updatedFrom(Place place) {
        return new Waypoint(kind, id, dimension, place.pos(), place.group().orElse(kind), place.name(), place.areas(), place.exploreRadius(),
                customName, show, explored);
    }

    public Waypoint withName(Optional<String> name) {
        return new Waypoint(kind, id, dimension, pos, group, defaultName, areas, exploreRadius, name, show, explored);
    }

    public Waypoint withShow(Show value) {
        return new Waypoint(kind, id, dimension, pos, group, defaultName, areas, exploreRadius, customName, value, explored);
    }

    public Waypoint withExplored(boolean value) {
        return new Waypoint(kind, id, dimension, pos, group, defaultName, areas, exploreRadius, customName, show, value);
    }

    /** Unique among all waypoints: the kind, the dimension and the id. */
    public String key() {
        return kind + "|" + dimension.identifier() + "|" + id;
    }

    /**
     * Whether a player at this position has got there: inside one of the areas (the block the position is in), or
     * without areas, within the explore radius of {@code pos} (to the middle of its block).
     */
    public boolean reachedBy(double x, double y, double z) {
        return reachedBy(x, y, z, false);
    }

    /**
     * {@link #reachedBy(double, double, double)}, or for a place without a height of its own ({@code heightless}) by x
     * and z alone: inside an area seen from above, or within the explore radius along the ground.
     */
    public boolean reachedBy(double x, double y, double z, boolean heightless) {
        if (!areas.isEmpty()) {
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            for (BoundingBox area : areas) {
                boolean inHeight = heightless || by >= area.minY() && by <= area.maxY();
                if (bx >= area.minX() && bx <= area.maxX() && inHeight && bz >= area.minZ() && bz <= area.maxZ()) return true;
            }
            return false;
        }
        if (exploreRadius <= 0) return false;
        double dx = pos.getX() + 0.5 - x;
        double dy = heightless ? 0 : pos.getY() + 0.5 - y;
        double dz = pos.getZ() + 0.5 - z;
        return dx * dx + dy * dy + dz * dz <= (double) exploreRadius * exploreRadius;
    }

    /** Where the label is drawn: a little above the highest area, or above {@code pos}. */
    public double labelY() {
        return topY() + 2.0;
    }

    /** The lowest block it takes up: the bottom of its lowest area, or {@code pos} without areas. */
    public int bottomY() {
        if (areas.isEmpty()) return pos.getY();
        int bottom = Integer.MAX_VALUE;
        for (BoundingBox area : areas) bottom = Math.min(bottom, area.minY());
        return bottom;
    }

    /** The highest block it takes up: the top of its highest area, or {@code pos} (and never below it). */
    public int topY() {
        int top = pos.getY();
        for (BoundingBox area : areas) top = Math.max(top, area.maxY());
        return top;
    }

    /**
     * How many blocks it is above a height (negative: below): to its bottom block from a height under it, to its top
     * block from one over it, and 0 from the blocks between.
     */
    public int heightAbove(double y) {
        int block = (int) Math.floor(y);
        if (block < bottomY()) return bottomY() - block;
        if (block > topY()) return topY() - block;
        return 0;
    }
}
