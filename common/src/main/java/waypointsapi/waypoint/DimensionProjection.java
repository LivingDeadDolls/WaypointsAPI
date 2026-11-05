package waypointsapi.waypoint;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Shows waypoints of the Overworld in the Nether at 1/8 of their coordinates, and Nether waypoints in the Overworld
 * at 8 times theirs, for planning portals. The ratio is the dimension types' {@code coordinate_scale}, as the game counts
 * it, so a data pack that changes it is followed. The End is never projected.
 */
public final class DimensionProjection {
    private static final double VANILLA_NETHER_SCALE = 8.0;

    /** Where a waypoint of another dimension shows up, with its original position kept for the label. */
    public record Projected(Waypoint source, double x, double z) {
        public ResourceKey<Level> from() {
            return source.dimension();
        }

        public int originalX() {
            return source.pos().getX();
        }

        public int originalZ() {
            return source.pos().getZ();
        }
    }

    private DimensionProjection() {
    }

    public static Optional<Projected> project(Waypoint waypoint, ResourceKey<Level> target) {
        return factor(waypoint.dimension(), target, scale(waypoint.dimension()), scale(target)).map(f -> at(waypoint, f));
    }

    /** A waypoint shown at its block's middle times the factor. */
    public static Projected at(Waypoint waypoint, double factor) {
        return new Projected(waypoint, (waypoint.pos().getX() + 0.5) * factor, (waypoint.pos().getZ() + 0.5) * factor);
    }

    /** The dimension whose waypoints are projected into {@code target}, if any. */
    public static Optional<ResourceKey<Level>> sourceFor(ResourceKey<Level> target) {
        if (target == Level.NETHER) return Optional.of(Level.OVERWORLD);
        if (target == Level.OVERWORLD) return Optional.of(Level.NETHER);
        return Optional.empty();
    }

    /** How a position of {@code from} scales into {@code to}, given their scales; empty unless they are the Overworld and the Nether. */
    public static Optional<Double> factor(ResourceKey<Level> from, ResourceKey<Level> to, double fromScale, double toScale) {
        boolean pair = (from == Level.OVERWORLD && to == Level.NETHER) || (from == Level.NETHER && to == Level.OVERWORLD);
        return pair ? Optional.of(fromScale / toScale) : Optional.empty();
    }

    /**
     * How many Overworld blocks one block of a dimension stands for: its dimension type's {@code coordinate_scale} (the
     * Nether's 8 unless a data pack changes it). In singleplayer the server's own; otherwise what the server sent.
     */
    public static double scale(ResourceKey<Level> dimension) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        ServerLevel serverLevel = server == null ? null : server.getLevel(dimension);
        if (serverLevel != null) return serverLevel.dimensionType().coordinateScale();
        ClientLevel level = mc.level;
        if (level != null) {
            if (level.dimension() == dimension) return level.dimensionType().coordinateScale();
            Optional<DimensionType> type = level.registryAccess().lookup(Registries.DIMENSION_TYPE)
                    .flatMap(types -> types.getOptional(dimension.identifier()));
            if (type.isPresent()) return type.get().coordinateScale();
        }
        return dimension == Level.NETHER ? VANILLA_NETHER_SCALE : 1.0;
    }
}
