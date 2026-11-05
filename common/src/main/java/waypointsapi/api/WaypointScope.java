package waypointsapi.api;

import java.util.Collection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The waypoints of one kind for one server and seed, from {@link WaypointKind#scope()}. What is put here is saved
 * (in config/waypointsapi/waypoints) and is there again on the next visit. Any thread. Implemented by Waypoints API
 * (later versions may add methods).
 * <p>
 * Keep the scope that was current when work for it started (a calculation from the seed, say) and give its results
 * to that scope: once the client is on another server or seed, it is no longer current and takes nothing, so results
 * worked out for one world never land in another.
 */
public interface WaypointScope {
    /** The server, as Waypoints API names it for its files (e.g. {@code play.example.net_25565}, {@code sp_<world folder>}). */
    String server();

    /** The seed of the world. */
    long seed();

    /** Whether the client is still on this server and seed. When false, the methods below do nothing. */
    boolean isCurrent();

    /**
     * Adds a place as a waypoint. If this kind already has a waypoint with the same id in that dimension, what the mod
     * gives (position, name, group, areas, explore radius) is replaced, and what the player set (the name they gave it,
     * pinned or never shown, explored) is kept.
     */
    void put(Place place);

    /** {@link #put} for each place. */
    void putAll(Collection<Place> places);

    /** Drops a waypoint (the place turned out not to be there), with what the player set on it. Nothing if there is none. */
    void remove(ResourceKey<Level> dimension, String id);

    /** Marks a waypoint explored, as when the player gets there. Nothing if there is none. */
    void markExplored(ResourceKey<Level> dimension, String id);
}
