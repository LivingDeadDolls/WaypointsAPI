package waypointsapi.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A place to show as a waypoint, for {@link WaypointScope#put}: made with {@link #builder}. Immutable. Later versions
 * may add methods to the builder, never change the ones there are.
 */
public final class Place {
    private final String id;
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final Optional<Component> name;
    private final Optional<Identifier> group;
    private final List<BoundingBox> areas;
    private final int exploreRadius;

    private Place(Builder builder) {
        id = builder.id;
        dimension = builder.dimension;
        pos = builder.pos;
        name = Optional.ofNullable(builder.name);
        group = Optional.ofNullable(builder.group);
        areas = builder.areas;
        exploreRadius = builder.exploreRadius;
    }

    /**
     * @param id        stable for the same place, unique within the kind and the dimension, e.g. {@code minecraft:villages@15,8}
     * @param dimension where the place is
     * @param pos       where the beam stands, and where the list measures the distance to
     */
    public static Builder builder(String id, ResourceKey<Level> dimension, BlockPos pos) {
        return new Builder(id, dimension, pos);
    }

    public String id() {
        return id;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    /** The name given; empty for the group's name. */
    public Optional<Component> name() {
        return name;
    }

    /** The group given; empty for the kind's id. */
    public Optional<Identifier> group() {
        return group;
    }

    public List<BoundingBox> areas() {
        return areas;
    }

    public int exploreRadius() {
        return exploreRadius;
    }

    public static final class Builder {
        private final String id;
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;
        private Component name;
        private Identifier group;
        private List<BoundingBox> areas = List.of();
        private int exploreRadius = 8;

        private Builder(String id, ResourceKey<Level> dimension, BlockPos pos) {
            this.id = Objects.requireNonNull(id, "id");
            if (id.isEmpty()) throw new IllegalArgumentException("id must not be empty");
            this.dimension = Objects.requireNonNull(dimension, "dimension");
            this.pos = Objects.requireNonNull(pos, "pos").immutable();
        }

        /** The waypoint's name until the player gives it one. The group's name if not set. */
        public Builder name(Component name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        /**
         * The group within the kind (a structure set, a mob): the list filters and sorts by it, it has its own color,
         * and the player can hide a group. The kind's id if not set.
         */
        public Builder group(Identifier group) {
            this.group = Objects.requireNonNull(group, "group");
            return this;
        }

        /**
         * The blocks the place takes up, both corners included. The waypoint becomes explored once the player is inside
         * one of them, and its label is drawn a little above the highest. None if not set.
         */
        public Builder areas(List<BoundingBox> areas) {
            this.areas = List.copyOf(areas);
            return this;
        }

        /**
         * Without {@link #areas}: the waypoint becomes explored once the player is this close to {@code pos} (in blocks,
         * straight-line). 0 for never by itself (only by {@link WaypointScope#markExplored} or by hand). 8 if not set.
         */
        public Builder exploreRadius(int blocks) {
            if (blocks < 0) throw new IllegalArgumentException("exploreRadius must not be negative: " + blocks);
            this.exploreRadius = blocks;
            return this;
        }

        public Place build() {
            return new Place(this);
        }
    }
}
