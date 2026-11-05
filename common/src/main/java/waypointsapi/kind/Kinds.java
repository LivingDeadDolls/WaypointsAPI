package waypointsapi.kind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import waypointsapi.Constants;
import waypointsapi.api.KindDefinition;
import waypointsapi.api.KindSettings;
import waypointsapi.api.Place;
import waypointsapi.api.WaypointKind;
import waypointsapi.api.WaypointRegistry;
import waypointsapi.api.WaypointScope;
import waypointsapi.api.WaypointsPlugin;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.display.WaypointDisplay;
import waypointsapi.display.WaypointRenderer;
import waypointsapi.waypoint.Waypoint;
import waypointsapi.waypoint.WaypointStore;

/** The kinds other mods registered through {@link WaypointsPlugin}, in the order they were registered. */
public final class Kinds {
    private static final List<Kind> KINDS = new CopyOnWriteArrayList<>();

    /** A registered kind, as Waypoints API keeps it. */
    public static final class Kind implements WaypointKind {
        private final KindDefinition definition;
        private final String modId;
        private final List<Runnable> scopeListeners = new CopyOnWriteArrayList<>();
        private volatile boolean settingsFailed;

        private Kind(KindDefinition definition, String modId) {
            this.definition = definition;
            this.modId = modId;
        }

        @Override
        public Identifier id() {
            return definition.id();
        }

        public KindDefinition definition() {
            return definition;
        }

        /** The mod that registered it. */
        public String modId() {
            return modId;
        }

        public Component name() {
            return definition.name();
        }

        @Override
        public Optional<WaypointScope> scope() {
            return WaypointStore.scope().map(scope -> new KindScope(this, scope));
        }

        @Override
        public void addScopeListener(Runnable listener) {
            scopeListeners.add(listener);
        }

        @Override
        public Set<Identifier> seenGroups() {
            return WaypointStore.seenGroups(id());
        }

        @Override
        public boolean isDrawn(ResourceKey<Level> dimension, String id) {
            return drawn(dimension, id, WaypointRenderer.rules(WaypointsApiConfig.get()));
        }

        @Override
        public boolean isDrawnIgnoringExplored(ResourceKey<Level> dimension, String id) {
            WaypointDisplay.Rules rules = WaypointRenderer.rules(WaypointsApiConfig.get());
            return drawn(dimension, id, new WaypointDisplay.Rules() {
                @Override
                public boolean kindShown(Identifier kind) {
                    return rules.kindShown(kind);
                }

                @Override
                public boolean groupShown(Identifier kind, Identifier group) {
                    return rules.groupShown(kind, group);
                }

                @Override
                public double maxDistance(Identifier kind) {
                    return rules.maxDistance(kind);
                }

                @Override
                public int maxShown(Identifier kind) {
                    return rules.maxShown(kind);
                }

                @Override
                public boolean nearestOnly(Identifier kind, Identifier group) {
                    return rules.nearestOnly(kind, group);
                }

                @Override
                public boolean showExplored() {
                    return true;
                }

                @Override
                public boolean showOtherDimension() {
                    return rules.showOtherDimension();
                }
            });
        }

        private boolean drawn(ResourceKey<Level> dimension, String id, WaypointDisplay.Rules rules) {
            return WaypointStore.get(id(), dimension, id).filter(waypoint -> WaypointDisplay.drawn(waypoint, rules)).isPresent();
        }

        /** The kind's settings, from its mod: {@code fallback} when the mod fails (logged once). */
        public <T> T setting(Function<KindSettings, T> read, T fallback) {
            try {
                return read.apply(definition.settings());
            } catch (RuntimeException | LinkageError e) {
                if (!settingsFailed) {
                    settingsFailed = true;
                    Constants.LOG.error("The settings of the kind {} ({}) failed", id(), modId, e);
                }
                return fallback;
            }
        }

        /** Changes the kind's settings in its mod; false (logged) when the mod fails. */
        public boolean changeSetting(Consumer<KindSettings> change) {
            try {
                change.accept(definition.settings());
                return true;
            } catch (RuntimeException | LinkageError e) {
                Constants.LOG.error("The kind {} ({}) could not change its settings", id(), modId, e);
                return false;
            }
        }

        /** The groups for settings and filters: the mod's, and those of saved waypoints, by name. */
        public List<Identifier> groups() {
            Set<Identifier> groups = new LinkedHashSet<>(setting(KindSettings::groups, List.of()));
            groups.addAll(seenGroups());
            List<Identifier> sorted = new ArrayList<>(groups);
            sorted.sort(Comparator.comparing(group -> groupName(id(), group).getString()));
            return sorted;
        }
    }

    /** A kind's view of one server and seed of the store. */
    private record KindScope(Kind kind, WaypointStore.Scope scope) implements WaypointScope {
        @Override
        public String server() {
            return scope.server();
        }

        @Override
        public long seed() {
            return scope.seed();
        }

        @Override
        public boolean isCurrent() {
            return WaypointStore.isCurrent(scope);
        }

        @Override
        public void put(Place place) {
            putAll(List.of(place));
        }

        @Override
        public void putAll(Collection<Place> places) {
            WaypointStore.put(scope, kind.id(), places);
        }

        @Override
        public void remove(ResourceKey<Level> dimension, String id) {
            WaypointStore.remove(scope, kind.id(), dimension, id);
        }

        @Override
        public void markExplored(ResourceKey<Level> dimension, String id) {
            WaypointStore.setExplored(scope, kind.id(), dimension, id, true);
        }
    }

    private Kinds() {
    }

    /** Lets a plugin register its kinds; one that throws is logged and skipped (with what it registered before throwing kept). */
    public static void load(String modId, WaypointsPlugin plugin) {
        try {
            plugin.register(definition -> register(modId, definition));
        } catch (RuntimeException | LinkageError e) {
            Constants.LOG.error("The waypoints plugin of {} failed to register", modId, e);
        }
    }

    static synchronized Kind register(String modId, KindDefinition definition) {
        if (get(definition.id()).isPresent()) {
            throw new IllegalArgumentException("A kind of waypoints with the id " + definition.id() + " is already registered");
        }
        Kind kind = new Kind(definition, modId);
        KINDS.add(kind);
        Constants.LOG.info("Waypoint kind {} registered by {}", definition.id(), modId);
        return kind;
    }

    public static List<Kind> all() {
        return List.copyOf(KINDS);
    }

    public static Optional<Kind> get(Identifier id) {
        for (Kind kind : KINDS) {
            if (kind.id().equals(id)) return Optional.of(kind);
        }
        return Optional.empty();
    }

    /** The name of a waypoint as shown: the player's, the mod's, or its group's. */
    public static String name(Waypoint waypoint) {
        if (waypoint.customName().isPresent()) return waypoint.customName().get();
        if (waypoint.defaultName().isPresent()) return waypoint.defaultName().get().getString();
        return groupName(waypoint.kind(), waypoint.group()).getString();
    }

    /** The name the waypoint has when the player gives it none. */
    public static Component defaultName(Waypoint waypoint) {
        return waypoint.defaultName().orElseGet(() -> groupName(waypoint.kind(), waypoint.group()));
    }

    /** A group's name from its kind; the id as text when the kind is not registered or its mod fails. */
    public static Component groupName(Identifier kind, Identifier group) {
        Optional<Kind> registered = get(kind);
        if (registered.isPresent()) {
            try {
                return registered.get().definition().groupName(group);
            } catch (RuntimeException | LinkageError e) {
                Constants.LOG.warn("The kind {} could not name the group {}: {}", kind, group, e.toString());
            }
        }
        return Component.literal(group.toString());
    }

    /**
     * The letter in a group's diamond: the first letter or digit of the one its kind's mod gives, else of the group's
     * name (upper case); empty when there is none.
     */
    public static String initial(Identifier kind, Identifier group) {
        Optional<Kind> registered = get(kind);
        if (registered.isPresent()) {
            try {
                String given = initialOf(registered.get().definition().groupInitial(group).orElse(""));
                if (!given.isEmpty()) return given;
            } catch (RuntimeException | LinkageError e) {
                Constants.LOG.warn("The kind {} could not give the letter of the group {}: {}", kind, group, e.toString());
            }
        }
        return initialOf(groupName(kind, group).getString());
    }

    /** The first letter or digit of a text, in upper case; empty when it has none. */
    public static String initialOf(String text) {
        for (int i = 0; i < text.length(); i += Character.charCount(text.codePointAt(i))) {
            int c = text.codePointAt(i);
            if (Character.isLetterOrDigit(c)) return new String(Character.toChars(Character.toUpperCase(c)));
        }
        return "";
    }

    /** The item a group's mod gives it, if any; none when the kind is not registered or its mod fails. */
    public static Optional<Item> groupIcon(Identifier kind, Identifier group) {
        Optional<Kind> registered = get(kind);
        if (registered.isEmpty()) return Optional.empty();
        try {
            return registered.get().definition().groupIcon(group);
        } catch (RuntimeException | LinkageError e) {
            Constants.LOG.warn("The kind {} could not give the icon of the group {}: {}", kind, group, e.toString());
            return Optional.empty();
        }
    }

    /** The item for a group in the list: its own, else its kind's; a compass for a kind no mod registered. */
    public static Item icon(Identifier kind, Identifier group) {
        return groupIcon(kind, group).or(() -> get(kind).map(registered -> registered.definition().icon())).orElse(Items.COMPASS);
    }

    /** Whether the kind's waypoints are shown: the player's choice and the mod giving them; false for a kind no mod registered (its mod removed). */
    public static boolean shown(Identifier kind) {
        return get(kind).map(registered -> registered.setting(settings -> settings.shown() && settings.active(), true)).orElse(false);
    }

    public static boolean groupShown(Identifier kind, Identifier group) {
        return get(kind).map(registered -> registered.setting(settings -> settings.groupShown(group), true)).orElse(false);
    }

    /** Horizontal blocks; 0 for a kind no mod registered. */
    public static int distance(Identifier kind) {
        return get(kind).map(registered -> registered.setting(KindSettings::displayDistance, 1024)).orElse(0);
    }

    public static int maxShown(Identifier kind) {
        return get(kind).map(registered -> registered.setting(KindSettings::maxShown, 16)).orElse(0);
    }

    /** The group's own setting for showing only its nearest waypoint. */
    public static boolean nearestOnly(Identifier kind, Identifier group) {
        return get(kind).map(registered -> registered.setting(settings -> settings.groupNearestOnly(group), false)).orElse(false);
    }

    /** The group's color from its kind: opaque RGB, or -1 for one picked from the group's id. */
    public static int color(Identifier kind, Identifier group) {
        return get(kind).map(registered -> registered.setting(settings -> settings.groupColor(group), -1)).orElse(-1);
    }

    /** Runs every kind's scope listeners (the client thread, right after the store changed its scope). */
    public static void scopeChanged() {
        List<Kind> kinds = new ArrayList<>(KINDS);
        for (Kind kind : kinds) {
            for (Runnable listener : kind.scopeListeners) {
                try {
                    listener.run();
                } catch (RuntimeException | LinkageError e) {
                    Constants.LOG.error("A scope listener of the kind {} ({}) failed", kind.id(), kind.modId(), e);
                }
            }
        }
    }
}
