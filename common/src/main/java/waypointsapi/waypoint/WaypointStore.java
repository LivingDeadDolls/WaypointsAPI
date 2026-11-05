package waypointsapi.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import waypointsapi.Constants;
import waypointsapi.api.Place;
import waypointsapi.platform.Services;

/**
 * The waypoints of the current server and seed, one file per kind and dimension:
 * config/waypointsapi/waypoints/&lt;server&gt;/&lt;seed&gt;/&lt;kind&gt;/&lt;dimension&gt;.json. Files are read when first
 * needed and written by {@link #saveIfDirty()}. Without a server and seed (not in a world, or the seed not known) there
 * are none, and nothing is kept. Safe to use from any thread.
 */
public final class WaypointStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FORMAT = 1;

    /** A server and seed: where waypoints are kept. */
    public record Scope(String server, long seed) {
    }

    private record Key(Identifier kind, ResourceKey<Level> dimension) {
    }

    private static final Map<Key, Map<String, Waypoint>> LOADED = new HashMap<>();
    private static final Set<Key> DIRTY = new HashSet<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static Scope scope;

    private WaypointStore() {
    }

    /** Switches to another server and seed, or to none (saving the previous one first). True if it changed. */
    public static boolean setScope(Scope newScope) {
        synchronized (LOADED) {
            if (Objects.equals(scope, newScope)) return false;
            saveIfDirtyLocked();
            LOADED.clear();
            scope = newScope;
        }
        changed();
        return true;
    }

    public static Optional<Scope> scope() {
        synchronized (LOADED) {
            return Optional.ofNullable(scope);
        }
    }

    public static boolean isCurrent(Scope value) {
        synchronized (LOADED) {
            return scope != null && scope.equals(value);
        }
    }

    /** All waypoints of a kind in a dimension, in the order they were added. */
    public static List<Waypoint> all(Identifier kind, ResourceKey<Level> dimension) {
        synchronized (LOADED) {
            Map<String, Waypoint> waypoints = loaded(new Key(kind, dimension));
            return waypoints == null ? List.of() : List.copyOf(waypoints.values());
        }
    }

    public static Optional<Waypoint> get(Identifier kind, ResourceKey<Level> dimension, String id) {
        synchronized (LOADED) {
            Map<String, Waypoint> waypoints = loaded(new Key(kind, dimension));
            return waypoints == null ? Optional.empty() : Optional.ofNullable(waypoints.get(id));
        }
    }

    /** Adds places of a kind, or updates the ones there keeping what the player set; nothing unless {@code target} is current. */
    public static void put(Scope target, Identifier kind, Collection<Place> places) {
        boolean any = false;
        synchronized (LOADED) {
            if (!target.equals(scope)) return;
            for (Place place : places) {
                Key key = new Key(kind, place.dimension());
                Map<String, Waypoint> waypoints = loaded(key);
                Waypoint old = waypoints.get(place.id());
                Waypoint fresh = old == null ? Waypoint.of(kind, place) : old.updatedFrom(place);
                if (fresh.equals(old)) continue;
                waypoints.put(fresh.id(), fresh);
                DIRTY.add(key);
                any = true;
            }
        }
        if (any) changed();
    }

    /** Drops a waypoint; nothing unless {@code target} is current. */
    public static void remove(Scope target, Identifier kind, ResourceKey<Level> dimension, String id) {
        synchronized (LOADED) {
            if (!target.equals(scope)) return;
            Key key = new Key(kind, dimension);
            if (loaded(key).remove(id) == null) return;
            DIRTY.add(key);
        }
        changed();
    }

    /** Marks a waypoint explored, or takes the mark off; nothing unless {@code target} is current. */
    public static boolean setExplored(Scope target, Identifier kind, ResourceKey<Level> dimension, String id, boolean explored) {
        return update(target, kind, dimension, id, waypoint -> waypoint.withExplored(explored));
    }

    /** Gives a waypoint a name; an empty or blank name takes it off. */
    public static boolean rename(Waypoint waypoint, String name) {
        Optional<String> value = name == null || name.isBlank() ? Optional.empty() : Optional.of(name.strip());
        return update(waypoint, current -> current.withName(value));
    }

    /** Sets whether a waypoint is shown in the world: by the usual rules, always (pinned), or never. */
    public static boolean setShow(Waypoint waypoint, Waypoint.Show show) {
        return update(waypoint, current -> current.withShow(show));
    }

    public static boolean setExplored(Waypoint waypoint, boolean explored) {
        return update(waypoint, current -> current.withExplored(explored));
    }

    /** The waypoint as it is now in the store (the player or a mod may have changed it since it was read). */
    public static Optional<Waypoint> current(Waypoint waypoint) {
        return get(waypoint.kind(), waypoint.dimension(), waypoint.id());
    }

    private static boolean update(Waypoint waypoint, UnaryOperator<Waypoint> change) {
        Scope now = scope().orElse(null);
        return now != null && update(now, waypoint.kind(), waypoint.dimension(), waypoint.id(), change);
    }

    private static boolean update(Scope target, Identifier kind, ResourceKey<Level> dimension, String id, UnaryOperator<Waypoint> change) {
        synchronized (LOADED) {
            if (!target.equals(scope)) return false;
            Key key = new Key(kind, dimension);
            Map<String, Waypoint> waypoints = loaded(key);
            Waypoint old = waypoints.get(id);
            if (old == null) return false;
            Waypoint updated = change.apply(old);
            if (updated.equals(old)) return true;
            waypoints.put(id, updated);
            DIRTY.add(key);
        }
        changed();
        return true;
    }

    /** Writes the changed files. */
    public static void saveIfDirty() {
        synchronized (LOADED) {
            saveIfDirtyLocked();
        }
    }

    /** Forgets what is in memory so the next read comes from disk (saving first). */
    /**
     * The groups of a kind's waypoints: those loaded now, and those in the files of every server and seed (so also while
     * not in a world). Reads files: not for every frame.
     */
    public static Set<Identifier> seenGroups(Identifier kind) {
        Set<Identifier> groups = new LinkedHashSet<>();
        synchronized (LOADED) {
            for (Map.Entry<Key, Map<String, Waypoint>> entry : LOADED.entrySet()) {
                if (entry.getKey().kind().equals(kind)) entry.getValue().values().forEach(waypoint -> groups.add(waypoint.group()));
            }
        }
        Path root = Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID).resolve("waypoints");
        if (!Files.isDirectory(root)) return groups;
        String kindFolder = fileName(kind);
        try (Stream<Path> files = Files.walk(root, 4)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path folder = file.getParent();
                if (folder == null || !folder.getFileName().toString().equals(kindFolder) || !file.toString().endsWith(".json")) continue;
                try {
                    groups.addAll(groupsIn(Files.readString(file, StandardCharsets.UTF_8)));
                } catch (IOException | RuntimeException e) {
                    Constants.LOG.warn("Could not read the groups in {}: {}", file, e.toString());
                }
            }
        } catch (IOException | UncheckedIOException e) {
            Constants.LOG.warn("Could not look through {}: {}", root, e.toString());
        }
        return groups;
    }

    /** The groups of the waypoints in a saved file's text; unreadable waypoints left out. */
    static Set<Identifier> groupsIn(String json) {
        Set<Identifier> groups = new LinkedHashSet<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("waypoints")) return groups;
        for (JsonElement element : root.getAsJsonArray("waypoints")) {
            if (!element.isJsonObject() || !element.getAsJsonObject().has("group")) continue;
            Identifier group = Identifier.tryParse(element.getAsJsonObject().get("group").getAsString());
            if (group != null) groups.add(group);
        }
        return groups;
    }

    public static void reload() {
        synchronized (LOADED) {
            saveIfDirtyLocked();
            LOADED.clear();
        }
        changed();
    }

    /** The file of a kind and dimension for the current scope, or empty without one. */
    public static Optional<Path> file(Identifier kind, ResourceKey<Level> dimension) {
        synchronized (LOADED) {
            return scope == null ? Optional.empty() : Optional.of(file(scope, kind, dimension));
        }
    }

    /** Runs when waypoints change or the scope changes, on the thread that changed them. */
    public static void addListener(Runnable listener) {
        synchronized (LISTENERS) {
            LISTENERS.add(listener);
        }
    }

    private static Path file(Scope in, Identifier kind, ResourceKey<Level> dimension) {
        return Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID).resolve("waypoints").resolve(in.server())
                .resolve(Long.toString(in.seed())).resolve(fileName(kind)).resolve(fileName(dimension.identifier()) + ".json");
    }

    static String fileName(Identifier id) {
        return (id.getNamespace() + "_" + id.getPath()).replaceAll("[^a-z0-9._-]", "_");
    }

    private static Map<String, Waypoint> loaded(Key key) {
        if (scope == null) return null;
        return LOADED.computeIfAbsent(key, WaypointStore::read);
    }

    private static Map<String, Waypoint> read(Key key) {
        Map<String, Waypoint> waypoints = new LinkedHashMap<>();
        Path file = file(scope, key.kind(), key.dimension());
        if (!Files.isRegularFile(file)) return waypoints;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("waypoints")) {
                try {
                    Waypoint waypoint = fromJson(key.kind(), key.dimension(), element.getAsJsonObject());
                    waypoints.put(waypoint.id(), waypoint);
                } catch (RuntimeException e) {
                    Constants.LOG.warn("Skipping an unreadable waypoint in {}: {}", file, e.toString());
                }
            }
        } catch (IOException | RuntimeException e) {
            Constants.LOG.error("Could not read {}", file, e);
        }
        return waypoints;
    }

    private static void saveIfDirtyLocked() {
        for (Key key : List.copyOf(DIRTY)) {
            DIRTY.remove(key);
            Map<String, Waypoint> waypoints = LOADED.get(key);
            if (waypoints == null || scope == null) continue;
            Path file = file(scope, key.kind(), key.dimension());
            JsonObject root = new JsonObject();
            root.addProperty("format", FORMAT);
            JsonArray array = new JsonArray();
            waypoints.values().forEach(waypoint -> array.add(toJson(waypoint)));
            root.add("waypoints", array);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            } catch (IOException e) {
                Constants.LOG.error("Could not save {}", file, e);
            }
        }
    }

    static JsonObject toJson(Waypoint waypoint) {
        JsonObject object = new JsonObject();
        object.addProperty("id", waypoint.id());
        waypoint.customName().ifPresent(name -> object.addProperty("name", name));
        waypoint.defaultName().flatMap(name -> ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, name).result())
                .ifPresent(name -> object.add("default_name", name));
        object.addProperty("group", waypoint.group().toString());
        JsonArray pos = new JsonArray();
        pos.add(waypoint.pos().getX());
        pos.add(waypoint.pos().getY());
        pos.add(waypoint.pos().getZ());
        object.add("pos", pos);
        if (!waypoint.areas().isEmpty()) {
            JsonArray areas = new JsonArray();
            waypoint.areas().forEach(area -> areas.add(areaJson(area)));
            object.add("areas", areas);
        }
        object.addProperty("explore_radius", waypoint.exploreRadius());
        if (waypoint.explored()) object.addProperty("explored", true);
        if (waypoint.show() != Waypoint.Show.AUTO) object.addProperty("show", waypoint.show().name().toLowerCase(Locale.ROOT));
        return object;
    }

    static Waypoint fromJson(Identifier kind, ResourceKey<Level> dimension, JsonObject object) {
        JsonArray pos = object.getAsJsonArray("pos");
        List<BoundingBox> areas = new ArrayList<>();
        if (object.has("areas")) object.getAsJsonArray("areas").forEach(area -> areas.add(area(area.getAsJsonArray())));
        Optional<String> name = object.has("name") ? Optional.of(object.get("name").getAsString()) : Optional.empty();
        Optional<Component> defaultName = object.has("default_name")
                ? ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, object.get("default_name")).result() : Optional.empty();
        Waypoint.Show show = Waypoint.Show.AUTO;
        if (object.has("show")) {
            try {
                show = Waypoint.Show.valueOf(object.get("show").getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // An unknown value from a later version: the usual rules.
            }
        }
        return new Waypoint(kind, object.get("id").getAsString(), dimension,
                new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()),
                Identifier.parse(object.get("group").getAsString()), defaultName, List.copyOf(areas),
                object.has("explore_radius") ? object.get("explore_radius").getAsInt() : 0, name, show,
                object.has("explored") && object.get("explored").getAsBoolean());
    }

    private static JsonArray areaJson(BoundingBox area) {
        JsonArray array = new JsonArray();
        array.add(area.minX());
        array.add(area.minY());
        array.add(area.minZ());
        array.add(area.maxX());
        array.add(area.maxY());
        array.add(area.maxZ());
        return array;
    }

    private static BoundingBox area(JsonArray array) {
        return new BoundingBox(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt(),
                array.get(3).getAsInt(), array.get(4).getAsInt(), array.get(5).getAsInt());
    }

    private static void changed() {
        List<Runnable> copy;
        synchronized (LISTENERS) {
            copy = List.copyOf(LISTENERS);
        }
        for (Runnable listener : copy) {
            try {
                listener.run();
            } catch (RuntimeException | LinkageError e) {
                Constants.LOG.error("A waypoint listener failed", e);
            }
        }
    }
}
