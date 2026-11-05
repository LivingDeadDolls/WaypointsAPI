package waypointsapi.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * How a kind's waypoints are shown, kept and saved by the mod that registered the kind (in its own config, on its own
 * settings screen): given with {@link KindDefinition.Builder#settings}. Waypoints API reads it whenever it draws, and
 * writes the groups' visibility when the player switches it from the list.
 * <p>
 * Everything is called on the client thread. The getters are called every frame: return values held in memory, and
 * read them anew each time (from the config's current instance, which a reload replaces) instead of keeping them.
 * Never read files or do slow work in them. Implemented by the mod; later versions may add methods with defaults.
 */
public interface KindSettings {
    /**
     * The player's choice of whether the kind's waypoints are shown in the world and on map mods (pinned ones are
     * anyway). Only the choice: a switch of the mod's own that also hides them goes in {@link #active()}, so that the
     * settings screen shows and keeps the choice.
     */
    boolean shown();

    void setShown(boolean shown);

    /**
     * Whether the mod gives its places now: false hides the kind's waypoints as {@link #shown()} false does (pinned ones
     * are shown anyway), e.g. while the mod's own switch for finding them is off. True if not overridden.
     */
    default boolean active() {
        return true;
    }

    /** How far (horizontally, in blocks of the waypoint's own dimension) the kind's waypoints are shown. */
    int displayDistance();

    void setDisplayDistance(int blocks);

    int defaultDisplayDistance();

    /** The largest {@link #displayDistance()} the settings screen offers. */
    default int maxDisplayDistance() {
        return 8192;
    }

    /** How many of the kind's waypoints are shown at most, the nearest. */
    int maxShown();

    void setMaxShown(int count);

    int defaultMaxShown();

    /** Whether the group's waypoints are shown (pinned ones are anyway). True for a group never set. */
    boolean groupShown(Identifier group);

    void setGroupShown(Identifier group, boolean shown);

    default boolean defaultGroupShown(Identifier group) {
        return true;
    }

    /**
     * The group's color: the one the player chose, else {@link #defaultGroupColor}. Opaque RGB (the alpha bits
     * ignored), or -1 for one Waypoints API picks from the group's id (the same on every run).
     */
    int groupColor(Identifier group);

    /** Sets the player's color for the group: opaque RGB, or -1 to go back to the default. */
    void setGroupColor(Identifier group, int rgb);

    /** The group's color unless the player chose one: opaque RGB, or -1 for one picked from the group's id. */
    default int defaultGroupColor(Identifier group) {
        return -1;
    }

    /**
     * Whether only the nearest waypoint of the group is shown in the world: for groups with many places close together,
     * whose beams would crowd the view. The others are still listed, and shown when pinned.
     */
    boolean groupNearestOnly(Identifier group);

    void setGroupNearestOnly(Identifier group, boolean nearestOnly);

    default boolean defaultGroupNearestOnly(Identifier group) {
        return false;
    }

    /**
     * The groups the mod knows of, for the settings and the list's filter of groups, besides those of saved waypoints
     * (which Waypoints API adds): ones with settings of their own, or all the mod can find. None if not overridden.
     */
    default Collection<Identifier> groups() {
        return List.of();
    }

    /** Writes the settings to the mod's file. Called once when the player leaves a screen of Waypoints API that changed them. */
    void save();

    /**
     * The mod's settings screen, opened from the list's filter of groups (for players without Mod Menu). None if not
     * overridden.
     */
    default Optional<Screen> settingsScreen(Screen parent) {
        return Optional.empty();
    }
}
