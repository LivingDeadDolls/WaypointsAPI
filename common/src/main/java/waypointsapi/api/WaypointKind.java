package waypointsapi.api;

import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** A registered kind, returned by {@link WaypointRegistry#register}. Implemented by Waypoints API (later versions may add methods). */
public interface WaypointKind {
    /** The id it was registered with. */
    Identifier id();

    /**
     * The waypoints of this kind for the server and seed the client is on now, to give places to. Empty while not in a
     * world, and while the seed of the world is not known (Seed Engine API's {@code currentSeed()}). Any thread.
     */
    Optional<WaypointScope> scope();

    /**
     * Runs {@code listener} whenever {@link #scope()} changes: on joining and leaving a world, and when the seed
     * becomes known or changes. Runs on the client thread, right after the change; keep it quick. Never removed.
     */
    void addScopeListener(Runnable listener);

    /**
     * The groups of this kind's saved waypoints: of the world the client is in, and of every other server and seed
     * saved before (read from the files, so also while not in a world). For a settings screen; slow, so not every
     * frame. Client thread.
     */
    Set<Identifier> seenGroups();

    /**
     * Whether the waypoint is one Waypoints API draws now, distance and count aside: one of the current scope, not set
     * never to show, and (unless pinned) of a kind and group shown, and not explored while explored ones are hidden.
     * For a mod that draws more of its own at the same places. Client thread.
     */
    boolean isDrawn(ResourceKey<Level> dimension, String id);

    /**
     * {@link #isDrawn} as if explored waypoints were shown: for what a mod draws of its own that is still wanted once
     * the player has been there (a frame seen through walls near the place). Client thread.
     */
    boolean isDrawnIgnoringExplored(ResourceKey<Level> dimension, String id);
}
