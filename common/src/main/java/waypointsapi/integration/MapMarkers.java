package waypointsapi.integration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.display.WaypointColors;
import waypointsapi.display.WaypointDisplay;
import waypointsapi.display.WaypointRenderer;
import waypointsapi.kind.Kinds;
import waypointsapi.waypoint.Waypoint;
import waypointsapi.waypoint.WaypointStore;

/**
 * What map mods are given: the waypoints shown by the rules of the world display (kinds and groups shown, each
 * waypoint's own setting) but at any distance, each in its own dimension. The map mod only draws them; nothing is saved
 * on its side.
 */
public final class MapMarkers {
    /**
     * @param id       stable for the same waypoint: changes of the rest replace the marker
     * @param pos      where it is drawn
     * @param color    opaque RGB as ARGB
     * @param label    drawn next to the dot
     * @param title    shown when the mouse is over it
     * @param initial  the group's letter, for map mods that draw one (empty for none)
     * @param icon     the group's item, for map mods that draw one, if its mod gives one
     */
    public record Marker(String id, ResourceKey<Level> dimension, BlockPos pos, int color, String label, String title, String initial,
                         Optional<Item> icon, Waypoint waypoint) {
    }

    private MapMarkers() {
    }

    /** The markers for now, around a player in a dimension; none without a server and seed. */
    public static List<Marker> current(ResourceKey<Level> currentDimension) {
        if (WaypointStore.scope().isEmpty()) return List.of();
        WaypointsApiConfig config = WaypointsApiConfig.get();
        Set<ResourceKey<Level>> dimensions = new LinkedHashSet<>(List.of(Level.OVERWORLD, Level.NETHER, Level.END));
        dimensions.add(currentDimension);
        List<Marker> markers = new ArrayList<>();
        for (Kinds.Kind kind : Kinds.all()) {
            for (ResourceKey<Level> dimension : dimensions) {
                for (Waypoint waypoint : WaypointStore.all(kind.id(), dimension)) marker(waypoint, config).ifPresent(markers::add);
            }
        }
        return markers;
    }

    /**
     * A waypoint's marker, unless the world display would never show it: set never to show, or (when not pinned) of a
     * kind or group turned off, or explored while explored ones are not shown.
     */
    public static Optional<Marker> marker(Waypoint waypoint, WaypointsApiConfig config) {
        if (!WaypointDisplay.drawn(waypoint, WaypointRenderer.rules(config))) return Optional.empty();
        BlockPos pos = waypoint.pos();
        String label = Kinds.name(waypoint);
        String title = Component.translatable("waypointsapi.map.title", Kinds.groupName(waypoint.kind(), waypoint.group()), pos.getX(), pos.getZ()).getString();
        if (waypoint.explored()) title += " - " + Component.translatable("waypointsapi.map.explored").getString();
        return Optional.of(new Marker(waypoint.key(), waypoint.dimension(), pos, WaypointColors.of(waypoint.kind(), waypoint.group()), label, title,
                Kinds.initial(waypoint.kind(), waypoint.group()), Kinds.groupIcon(waypoint.kind(), waypoint.group()), waypoint));
    }
}
