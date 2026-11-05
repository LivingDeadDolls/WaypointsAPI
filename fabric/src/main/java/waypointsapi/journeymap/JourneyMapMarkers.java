package waypointsapi.journeymap;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.IOverlayListener;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import waypointsapi.Constants;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.kind.Kinds;
import waypointsapi.integration.MapIntegration;
import waypointsapi.integration.MapMarkers;
import waypointsapi.ui.Screens;
import waypointsapi.ui.WaypointListScreen;

/**
 * Keeps JourneyMap's full map showing {@link MapMarkers}: a dot in the marker's color (or the group's item with a small
 * one on its corner) with its name, added, replaced
 * and removed as the markers change. Clicking a dot opens the list at its waypoint. Also adds waypoints to
 * JourneyMap's own waypoints when asked from the list.
 */
public final class JourneyMapMarkers {
    /** How often the markers are compared with what JourneyMap shows (ticks). */
    private static final int UPDATE_TICKS = 10;
    /**
     * The dot: white where the marker's color goes (each pixel is multiplied by it), so a resource pack can draw
     * another shape. The one in the jar is a 14 pixel circle with a dark ring.
     */
    public static final Identifier DOT = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/map/dot.png");
    /** How far from the point the label is drawn, so it does not cover the dot. */
    private static final int LABEL_OFFSET = 15;
    /** The same for a label drawn twice as large. */
    private static final int WIDE_LABEL_OFFSET = 22;
    /** Letters from here on (Hangul, kana, kanji and the like) are drawn from glyphs with twice the pixels. */
    private static final int WIDE_LETTERS = 0x1100;
    /** The side of a group's item on the map (pixels). */
    private static final int ITEM_SIZE = 16;
    /** The custom data key of the JourneyMap waypoints added from the list: the waypoint's key. */
    private static final String WAYPOINT_KEY = "waypointsapi_id";

    private static IClientAPI api;
    private static final Map<String, Shown> SHOWN = new HashMap<>();
    private static int ticks;

    private record Shown(MapMarkers.Marker marker, MarkerOverlay overlay) {
    }

    private JourneyMapMarkers() {
    }

    static void start(IClientAPI clientApi) {
        api = clientApi;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (++ticks < UPDATE_TICKS) return;
            ticks = 0;
            update(mc);
        });
        MapIntegration.register(MapIntegration.Mod.JOURNEYMAP, new MapIntegration.Target() {
            @Override
            public String name() {
                return "JourneyMap";
            }

            @Override
            public boolean enabled() {
                return WaypointsApiConfig.get().mapJourneyMap;
            }

            @Override
            public boolean addWaypoint(waypointsapi.waypoint.Waypoint waypoint) {
                return JourneyMapMarkers.addWaypoint(waypoint);
            }
        });
    }

    /** Brings JourneyMap in line with the markers for now. Runs on the client thread. */
    static void update(Minecraft mc) {
        if (api == null) return;
        List<MapMarkers.Marker> markers = mc.level == null || !WaypointsApiConfig.get().mapJourneyMap ? List.of() : MapMarkers.current(mc.level.dimension());
        if (dotTexturePacks != null && !dotTexturePacks.equals(packs(mc))) {
            // Other resource packs: every dot drawn again from their texture.
            SHOWN.values().forEach(JourneyMapMarkers::remove);
            SHOWN.clear();
        }
        Set<String> wanted = new HashSet<>();
        for (MapMarkers.Marker marker : markers) {
            wanted.add(marker.id());
            Shown old = SHOWN.get(marker.id());
            if (old != null && old.marker().equals(marker)) continue;
            if (old != null) remove(old);
            MarkerOverlay overlay = overlay(marker);
            try {
                api.show(overlay);
                SHOWN.put(marker.id(), new Shown(marker, overlay));
            } catch (Exception e) {
                SHOWN.remove(marker.id());
                overlay.getIcon().getImage().close();
                Constants.LOG.warn("JourneyMap did not take the marker {}: {}", marker.id(), e.toString());
            }
        }
        SHOWN.entrySet().removeIf(entry -> {
            if (wanted.contains(entry.getKey())) return false;
            remove(entry.getValue());
            return true;
        });
    }

    /** Takes a marker off the map and frees its dot (JourneyMap keeps a copy of it, made when the marker was shown). */
    private static void remove(Shown shown) {
        api.remove(shown.overlay());
        com.mojang.blaze3d.platform.NativeImage image = shown.overlay().getIcon().getImage();
        if (image != null) image.close();
    }

    private static MarkerOverlay overlay(MapMarkers.Marker marker) {
        MapImage icon = new MapImage(marker.icon().flatMap(JourneyMapMarkers::itemTexture).map(item -> withDot(item, marker.color()))
                .orElseGet(() -> dot(marker.color())));
        icon.centerAnchors();
        MarkerOverlay overlay = new MarkerOverlay(Constants.MOD_ID, marker.pos(), icon);
        overlay.setDimension(marker.dimension());
        overlay.setLabel(marker.label());
        overlay.setTitle(marker.title());
        overlay.setActiveUIs(Context.UI.Fullscreen);
        // The label is drawn centered on the point: moved off the dot so its background does not cover it. JourneyMap
        // draws a font pixel as one screen pixel, which squeezes Japanese (and other wide) letters out of shape: a label
        // with them is drawn twice as large, so each of their pixels gets its own. No dark box behind (the boxes of nearby
        // marks cover each other): a shadow under the letters instead.
        boolean wide = marker.label().chars().anyMatch(c -> c >= WIDE_LETTERS);
        overlay.setTextProperties(new TextProperties().setColor(marker.color() & 0xFFFFFF).setScale(wide ? 2f : 1f)
                .setBackgroundOpacity(0f).setFontShadow(true)
                .setOffsetY(wide ? WIDE_LABEL_OFFSET : LABEL_OFFSET).setActiveUIs(Context.UI.Fullscreen));
        overlay.setOverlayListener(new IOverlayListener() {
            @Override
            public boolean onMouseClick(UIState state, Point2D.Double mousePosition, BlockPos blockPosition, int button, boolean doubleClick) {
                if (button != 0) return false;
                Minecraft mc = Minecraft.getInstance();
                Screens.open(WaypointListScreen.at(mc.screen, marker.waypoint()));
                return true;
            }
        });
        return overlay;
    }

    /** The dot texture tinted with a color: each pixel's red, green and blue multiplied by the color's. */
    private static NativeImage dot(int argb) {
        NativeImage texture = dotTexture();
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, texture.getWidth(), texture.getHeight(), true);
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                int pixel = texture.getPixel(x, y);
                int r = ((pixel >> 16) & 0xFF) * ((argb >> 16) & 0xFF) / 255;
                int g = ((pixel >> 8) & 0xFF) * ((argb >> 8) & 0xFF) / 255;
                int b = (pixel & 0xFF) * (argb & 0xFF) / 255;
                image.setPixel(x, y, (pixel & 0xFF000000) | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    /** An item's 16 pixel texture with the dot, half its size, on the lower right corner. */
    private static NativeImage withDot(NativeImage item, int argb) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, ITEM_SIZE, ITEM_SIZE, true);
        image.copyFrom(item);
        try (NativeImage dot = dot(argb)) {
            int size = dot.getWidth() / 2;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int pixel = dot.getPixel(x * 2, y * 2);
                    if (pixel >>> 24 != 0) image.setPixel(ITEM_SIZE - size + x, ITEM_SIZE - size + y, pixel);
                }
            }
        }
        return image;
    }

    /**
     * An item's texture of the resource packs in use, as 16 pixels square (the first frame of an animated one): its
     * {@code textures/item/} image, else its {@code textures/block/} one. None for an item drawn from a model alone.
     */
    private static Optional<NativeImage> itemTexture(Item item) {
        Minecraft mc = Minecraft.getInstance();
        List<String> packs = packs(mc);
        if (!packs.equals(itemTexturePacks)) {
            ITEM_TEXTURES.values().forEach(texture -> texture.ifPresent(NativeImage::close));
            ITEM_TEXTURES.clear();
            itemTexturePacks = packs;
        }
        return ITEM_TEXTURES.computeIfAbsent(item, JourneyMapMarkers::readItemTexture);
    }

    private static Optional<NativeImage> readItemTexture(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        for (String folder : List.of("textures/item/", "textures/block/")) {
            Identifier file = id.withPath(folder + id.getPath() + ".png");
            Optional<net.minecraft.server.packs.resources.Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
            if (resource.isEmpty()) continue;
            try (java.io.InputStream in = resource.get().open(); NativeImage read = NativeImage.read(in)) {
                NativeImage square = new NativeImage(NativeImage.Format.RGBA, ITEM_SIZE, ITEM_SIZE, true);
                int side = read.getWidth();
                for (int y = 0; y < ITEM_SIZE; y++) {
                    for (int x = 0; x < ITEM_SIZE; x++) square.setPixel(x, y, read.getPixel(x * side / ITEM_SIZE, y * side / ITEM_SIZE));
                }
                return Optional.of(square);
            } catch (java.io.IOException e) {
                Constants.LOG.warn("Could not read {}", file, e);
            }
        }
        return Optional.empty();
    }

    private static final Map<Item, Optional<NativeImage>> ITEM_TEXTURES = new HashMap<>();
    private static List<String> itemTexturePacks;

    /** The dot texture of the resource packs in use, read again when they change; a white square when it cannot be read. */
    private static NativeImage dotTexture() {
        Minecraft mc = Minecraft.getInstance();
        List<String> packs = packs(mc);
        if (dotTexture != null && packs.equals(dotTexturePacks)) return dotTexture;
        if (dotTexture != null) dotTexture.close();
        dotTexturePacks = packs;
        try (java.io.InputStream in = mc.getResourceManager().open(DOT)) {
            dotTexture = NativeImage.read(in);
        } catch (java.io.IOException e) {
            Constants.LOG.warn("Could not read {}; using a square", DOT, e);
            dotTexture = new NativeImage(NativeImage.Format.RGBA, 8, 8, true);
            dotTexture.fillRect(0, 0, 8, 8, 0xFFFFFFFF);
        }
        return dotTexture;
    }

    /** The resource packs loaded now (after a reload has finished, unlike the ones selected). */
    private static List<String> packs(Minecraft mc) {
        return mc.getResourceManager().listPacks().map(pack -> pack.packId()).toList();
    }

    private static NativeImage dotTexture;
    private static List<String> dotTexturePacks;

    /** Adds a waypoint to JourneyMap's waypoints, or updates the one added before for it. */
    static boolean addWaypoint(waypointsapi.waypoint.Waypoint waypoint) {
        if (api == null) return false;
        try {
            BlockPos pos = waypoint.pos();
            String name = Kinds.name(waypoint);
            Optional<? extends Waypoint> existing = api.getWaypoints(Constants.MOD_ID).stream()
                    .filter(added -> waypoint.key().equals(added.getCustomData(WAYPOINT_KEY))
                            && waypoint.dimension().identifier().toString().equals(added.getPrimaryDimension()))
                    .findFirst();
            Waypoint target = existing.isPresent() ? existing.get() : WaypointFactory.createWaypoint(Constants.MOD_ID, pos, name, waypoint.dimension(), true);
            target.setName(name);
            target.setBlockPos(pos);
            target.setColor(waypointsapi.display.WaypointColors.of(waypoint.kind(), waypoint.group()) & 0xFFFFFF);
            target.setCustomData(WAYPOINT_KEY, waypoint.key());
            api.addWaypoint(Constants.MOD_ID, target);
            return true;
        } catch (RuntimeException e) {
            Constants.LOG.warn("Could not add {} to JourneyMap's waypoints", waypoint.id(), e);
            return false;
        }
    }

    /** What JourneyMap shows for this mod now, by marker id (tests). */
    public static Map<String, MarkerOverlay> overlays() {
        Map<String, MarkerOverlay> overlays = new HashMap<>();
        SHOWN.forEach((id, shown) -> overlays.put(id, shown.overlay()));
        return overlays;
    }

    /** JourneyMap's client API, once JourneyMap has started this integration (tests). */
    public static Optional<IClientAPI> api() {
        return Optional.ofNullable(api);
    }
}
