package waypointsapi.display;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.kind.Kinds;
import waypointsapi.waypoint.DimensionProjection;
import waypointsapi.waypoint.Waypoint;
import waypointsapi.waypoint.WaypointStore;

/**
 * Draws waypoints in the world with vanilla's gizmos: a beam, a diamond and a label each. They depend on where the camera is, so
 * they are added every frame, from the camera of that frame ({@link #frame}, called while the level renderer collects
 * the frame's gizmos); added once per tick instead, they would trail the camera between ticks and shake while moving.
 * What to show is picked once per tick ({@link #tick}). Both are drawn on top of everything so they can be found
 * through terrain.
 */
public final class WaypointRenderer {
    /** Labels are drawn at most this far from the camera (blocks), so far ones stay readable. */
    public static final double LABEL_DISTANCE = 12.0;
    /** Put before the label of an explored waypoint. */
    public static final String EXPLORED_MARK = "✓";
    private static final float LABEL_SCALE = 0.3f;
    /** The height of a line of labels, in font pixels: labels that would cover each other are put a line apart. */
    private static final int LABEL_LINE = 10;
    /** A label is moved up at most this many lines to get clear of the others. */
    private static final int MAX_LABEL_LINES = 32;
    private static final float BEAM_WIDTH = 3.0f;
    /** The beam is this much wider (and opaque) over the height of the place, so the place's height can be seen on it. */
    private static final float BAND_WIDTH = 9.0f;
    /** The band is at least this tall for its distance (about a degree and a half), so a far one still shows. */
    private static final double MIN_BAND = 0.025;
    private static final double BEAM_SEGMENT = 8.0;
    /** A label is looked at within 10 degrees of the view direction. */
    private static final double LOOKED_AT = Math.cos(Math.toRadians(10));
    private static final int RECORDED_FRAMES = 120;
    /**
     * The diamond marking a place: from its middle to a corner, this many screen pixels at a GUI scale's worth of
     * font pixels (so it keeps its size on the screen, like the labels), with a dark rim this many of them wide.
     */
    private static final double DIAMOND_RADIUS = 6.0;
    /** A diamond with a letter in it is this big, so a wide (Japanese) letter fits too. */
    private static final double LETTERED_DIAMOND_RADIUS = 8.0;
    private static final double DIAMOND_RIM = 1.5;
    /** How far behind its text a label's outline is (blocks). */
    private static final double OUTLINE_BEHIND = 0.1;

    /** One label as drawn in a frame. */
    public record Label(String key, String text, Vec3 position, int color, float scale) {
    }

    /** One diamond as drawn in a frame, with the letter in it (empty for none). */
    public record Marker(String key, Vec3 position, String initial) {
    }

    /** What a frame drew, for tests: the camera, the labels, where each beam stood (x, z), and the diamonds. */
    public record Frame(Vec3 camera, List<Label> labels, List<double[]> beams, List<Marker> markers) {
    }

    private static volatile List<WaypointDisplay.Shown> shown = List.of();
    private static volatile boolean recording;
    private static final ArrayDeque<Frame> FRAMES = new ArrayDeque<>();

    private WaypointRenderer() {
    }

    /** What was picked on the last tick (for the HUD, the list and tests). */
    public static List<WaypointDisplay.Shown> shown() {
        return shown;
    }

    /** Client tick: picks what to show. */
    public static void tick(Minecraft mc) {
        WaypointsApiConfig config = WaypointsApiConfig.get();
        if (mc.player == null || mc.level == null || !config.showWaypoints || WaypointStore.scope().isEmpty()) {
            shown = List.of();
            return;
        }
        ResourceKey<Level> dimension = mc.level.dimension();
        shown = WaypointDisplay.select(dimension, mc.player.position(), own(dimension), projected(dimension), rules(config));
    }

    /** The waypoints of the registered kinds in a dimension. */
    public static List<Waypoint> own(ResourceKey<Level> dimension) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (Kinds.Kind kind : Kinds.all()) waypoints.addAll(WaypointStore.all(kind.id(), dimension));
        return waypoints;
    }

    /** The waypoints of the other dimension (the Overworld or the Nether) as they show up in this one. */
    public static List<DimensionProjection.Projected> projected(ResourceKey<Level> dimension) {
        Optional<ResourceKey<Level>> source = DimensionProjection.sourceFor(dimension);
        if (source.isEmpty()) return List.of();
        List<DimensionProjection.Projected> projected = new ArrayList<>();
        for (Waypoint waypoint : own(source.get())) DimensionProjection.project(waypoint, dimension).ifPresent(projected::add);
        return projected;
    }

    /** The display rules of the settings. */
    public static WaypointDisplay.Rules rules(WaypointsApiConfig config) {
        return new WaypointDisplay.Rules() {
            @Override
            public boolean kindShown(Identifier kind) {
                return Kinds.shown(kind);
            }

            @Override
            public boolean groupShown(Identifier kind, Identifier group) {
                return Kinds.groupShown(kind, group);
            }

            @Override
            public double maxDistance(Identifier kind) {
                return Kinds.distance(kind);
            }

            @Override
            public int maxShown(Identifier kind) {
                return Kinds.maxShown(kind);
            }

            @Override
            public boolean nearestOnly(Identifier kind, Identifier group) {
                return config.nearestOnly(kind, group);
            }

            @Override
            public boolean showExplored() {
                return config.showExplored;
            }

            @Override
            public boolean showOtherDimension() {
                return config.showOtherDimension;
            }
        };
    }

    /** Every frame, while the level renderer collects the frame's gizmos: beams and labels from this frame's camera. */
    public static void frame(Minecraft mc, Vec3 camera, Vec3 forward) {
        List<WaypointDisplay.Shown> picked = shown;
        List<Label> labels = recording ? new ArrayList<>() : null;
        List<double[]> beams = recording ? new ArrayList<>() : null;
        List<Marker> markers = recording ? new ArrayList<>() : null;
        if (picked.isEmpty() || mc.level == null) {
            record(camera, labels, beams, markers);
            return;
        }
        double pixelsPerBlock = pixelsPerBlock(mc);
        // Where the labels drawn so far are on the screen, so the next ones are moved off them.
        List<double[]> taken = new ArrayList<>();
        WaypointsApiConfig config = WaypointsApiConfig.get();
        // Fog hides what is drawn near the far plane: pull far beams in to half the render distance, along the same
        // direction, where there is no fog yet.
        double clear = Math.max(16.0, mc.options.getEffectiveRenderDistance() * 16.0 * 0.5);
        // Only the part of the height near the camera; the rest would be in the fog anyway.
        double bottom = Math.max(mc.level.getMinY(), camera.y - 128);
        double top = Math.min(mc.level.getMaxY() + 1.0, camera.y + 128);
        int labelled = 0;
        // Drawn after every beam, so no beam crosses a diamond.
        List<Runnable> diamonds = new ArrayList<>();
        for (WaypointDisplay.Shown waypoint : picked) {
            int color = WaypointColors.of(waypoint.waypoint().kind(), waypoint.waypoint().group());
            // A waypoint of the other dimension has no height here, nor has one of a heightless kind.
            boolean hasHeight = !waypoint.isProjected() && !Kinds.get(waypoint.waypoint().kind()).map(kind -> kind.definition().heightless()).orElse(false);
            double dx = waypoint.x() - camera.x;
            double dz = waypoint.z() - camera.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            // The diamond: in the middle of the band; at the camera's height when there is no height to show.
            double markerY = hasHeight ? (waypoint.waypoint().bottomY() + waypoint.waypoint().topY() + 1.0) / 2 : camera.y;
            String initial = config.showMarkers && config.showInitials ? Kinds.initial(waypoint.waypoint().kind(), waypoint.waypoint().group()) : "";
            double diamondRadius = initial.isEmpty() ? DIAMOND_RADIUS : LETTERED_DIAMOND_RADIUS;
            if (config.showBeams) {
                int alpha = waypoint.isProjected() ? 0x90 : 0xE0;
                double pull = distance > clear ? clear / distance : 1.0;
                Vec3 at = new Vec3(camera.x + dx * pull, 0, camera.z + dz * pull);
                // Lines are drawn over the diamond's quads, so the beam leaves out the part the diamond covers: around
                // where the line of sight to the diamond meets the (pulled in) beam, as tall as the diamond there.
                double[] gap = null;
                if (config.showMarkers) {
                    double gapY = camera.y + (markerY - camera.y) * pull;
                    double depth = at.add(0, gapY, 0).subtract(camera).dot(forward);
                    if (depth > 0.05) {
                        double half = (diamondRadius + DIAMOND_RIM * Math.sqrt(2)) * screenPixelsPerFontPixel(pixelsPerBlock) * depth / pixelsPerBlock;
                        gap = new double[]{gapY - half, gapY + half};
                    }
                }
                vertical(at, bottom, top, gap, WaypointColors.withAlpha(color, alpha), BEAM_WIDTH, BEAM_SEGMENT);
                if (hasHeight) band(waypoint.waypoint(), camera, at, pull, color, gap);
                if (beams != null) beams.add(new double[]{waypoint.x(), waypoint.z()});
            }
            if (config.showMarkers) {
                Vec3 position = within(camera, new Vec3(dx, markerY - camera.y, dz));
                double radius = diamondRadius * screenPixelsPerFontPixel(pixelsPerBlock);
                place(position, radius, radius, 0, camera, forward, pixelsPerBlock, taken);
                Vec3 at = position;
                diamonds.add(() -> drawDiamond(at, color, diamondRadius, initial, camera, forward, pixelsPerBlock));
                if (markers != null) markers.add(new Marker(waypoint.waypoint().key(), position, initial));
            }
            if (!config.showLabels) continue;
            double anchorY = hasHeight ? waypoint.waypoint().labelY() : camera.y;
            Vec3 toAnchor = new Vec3(dx, anchorY - camera.y, dz);
            double length = toAnchor.length();
            // The nearest few always; others only while looked at, so labels drawn near the camera do not pile up.
            boolean lookedAt = length > 0 && toAnchor.scale(1 / length).dot(forward) >= LOOKED_AT;
            if (labelled >= config.maxLabels && !lookedAt) continue;
            labelled++;
            Vec3 position = within(camera, toAnchor);
            double feet = mc.player != null ? mc.player.getY() : camera.y;
            String text = labelText(waypoint, distance, hasHeight ? waypoint.waypoint().heightAbove(feet) : 0, config);
            position = placeLabel(text, position, camera, forward, pixelsPerBlock, taken, mc.font);
            float scale = labelScale(position, camera, forward, pixelsPerBlock);
            drawLabel(text, position, color, scale, camera, forward);
            if (labels != null) labels.add(new Label(waypoint.waypoint().key(), text, position, color, scale));
        }
        diamonds.forEach(Runnable::run);
        record(camera, labels, beams, markers);
    }

    /** A point in the direction of {@code offset} from the camera, at most {@link #LABEL_DISTANCE} away. */
    private static Vec3 within(Vec3 camera, Vec3 offset) {
        double length = offset.length();
        return length <= LABEL_DISTANCE ? camera.add(offset) : camera.add(offset.scale(LABEL_DISTANCE / length));
    }

    /**
     * The scale of a label that comes out the same size on the screen wherever it is: text faces the view direction,
     * so its size on the screen goes with its depth along the view, not its distance. A pixel of the font is made an
     * even number of screen pixels (at least two): otherwise some font pixels come out 3 screen pixels wide and others
     * 4, and letters and their outline look ragged. Even, because Japanese and other wide letters are drawn from glyphs
     * with twice the pixels in the same space, whose pixels then come out whole too.
     */
    private static float labelScale(Vec3 position, Vec3 camera, Vec3 forward, double pixelsPerBlock) {
        double depth = Math.max(position.subtract(camera).dot(forward), 0.05);
        return (float) (screenPixelsPerFontPixel(pixelsPerBlock) * 16.0 * depth / pixelsPerBlock);
    }

    private static double screenPixelsPerFontPixel(double pixelsPerBlock) {
        return 2.0 * Math.max(1, Math.round(LABEL_SCALE / 64.0 * pixelsPerBlock / 2.0));
    }

    /**
     * Where a label goes so it does not cover the labels placed before it: moved straight up a line at a time until it
     * is clear, keeping its distance from the camera. Labels are put apart on the screen of a camera turned the same
     * way but looking level, so looking up and down leaves them where they are in the world. Each placed label is kept
     * in {@code taken} as its middle and half its width and height on that screen, in pixels.
     */
    private static Vec3 placeLabel(String text, Vec3 position, Vec3 camera, Vec3 forward, double pixelsPerBlock, List<double[]> taken, Font font) {
        double fontPixel = screenPixelsPerFontPixel(pixelsPerBlock);
        return place(position, font.width(text) * fontPixel / 2, LABEL_LINE / 2.0 * fontPixel, LABEL_LINE * fontPixel, camera, forward, pixelsPerBlock, taken);
    }

    /**
     * Keeps the place on the screen of something half this wide and high (pixels) in {@code taken}, moved up {@code step}
     * pixels at a time until clear of the others (never moved when 0: a diamond, which labels then go clear of).
     */
    private static Vec3 place(Vec3 position, double halfWidth, double halfHeight, double step, Vec3 camera, Vec3 forward, double pixelsPerBlock,
                              List<double[]> taken) {
        Vec3 level = new Vec3(forward.x, 0, forward.z);
        if (level.lengthSqr() < 1.0E-6) return position; // looking straight up or down: no level view to go by
        level = level.normalize();
        Vec3 right = level.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 toLabel = position.subtract(camera);
        double depth = toLabel.dot(level);
        if (depth < 0.05) return position; // behind: not on the screen
        double x = toLabel.dot(right) / depth * pixelsPerBlock;
        double y = toLabel.y / depth * pixelsPerBlock;
        double startY = y;
        for (int line = 0; step > 0 && line < MAX_LABEL_LINES && covers(taken, x, y, halfWidth, halfHeight); line++) y += step;
        taken.add(new double[]{x, y, halfWidth, halfHeight});
        if (y == startY) return position;
        Vec3 direction = level.add(right.scale(x / pixelsPerBlock)).add(new Vec3(0, y / pixelsPerBlock, 0)).normalize();
        return camera.add(direction.scale(toLabel.length()));
    }

    private static boolean covers(List<double[]> taken, double x, double y, double halfWidth, double halfHeight) {
        for (double[] other : taken) {
            if (Math.abs(other[0] - x) < other[2] + halfWidth && Math.abs(other[1] - y) < other[3] + halfHeight) return true;
        }
        return false;
    }

    /**
     * A label with an outline: the text drawn half a font pixel off in eight directions in the outline's color (white
     * around a dark color, black around the others: {@link WaypointColors#outlineFor}), then in its color.
     * Gizmos draw the opaque ones first and the translucent ones (alpha below 255) after, so the text, given an alpha
     * of 254, lands on top of its outline.
     */
    private static void drawLabel(String text, Vec3 position, int color, float scale, Vec3 camera, Vec3 forward) {
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(forward).normalize();
        // Some versions sort translucent text by its distance before drawing, which can put an outline copy nearer the
        // camera on top of the text. So the outline sits a little farther along the line of sight, made bigger as much
        // to look the same size, and is behind the text whatever the order.
        Vec3 ray = position.subtract(camera);
        double depth = Math.max(ray.dot(forward), 0.05);
        Vec3 back = ray.lengthSqr() < 1.0E-6 ? forward.scale(OUTLINE_BEHIND) : ray.normalize().scale(OUTLINE_BEHIND);
        float outlineScale = (float) (scale * (depth + back.dot(forward)) / depth);
        Vec3 behind = position.add(back);
        // Half a font pixel: one pixel of the finer glyphs of Japanese and other wide letters.
        double pixel = outlineScale / 32.0;
        TextGizmo.Style outline = TextGizmo.Style.forColorAndCentered(WaypointColors.outlineFor(color)).withScale(outlineScale);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                Gizmos.billboardText(text, behind.add(right.scale(dx * pixel)).add(up.scale(dy * pixel)), outline).setAlwaysOnTop();
            }
        }
        Gizmos.billboardText(text, position, TextGizmo.Style.forColorAndCentered(WaypointColors.withAlpha(color, 0xFE)).withScale(scale)).setAlwaysOnTop();
    }

    /**
     * A diamond facing the camera, a square on its corner in the group's color with a dark rim, the same size on the
     * screen wherever it is. Filled with lines a screen pixel apart rather than a quad: gizmo lines are drawn after quads,
     * so a quad would have the beams across it. The rim is a bigger diamond drawn first, the color one over it. A letter
     * goes in the middle in the rim's color: gizmo text is drawn after the lines of its group (both translucent here).
     */
    private static void drawDiamond(Vec3 position, int color, double radius, String initial, Vec3 camera, Vec3 forward, double pixelsPerBlock) {
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(forward).normalize();
        double depth = Math.max(position.subtract(camera).dot(forward), 0.05);
        double blocksPerPixel = depth / pixelsPerBlock;
        double fontPixel = screenPixelsPerFontPixel(pixelsPerBlock);
        int rim = WaypointColors.outlineFor(color);
        diamond(position, right, up, (radius + DIAMOND_RIM * Math.sqrt(2)) * fontPixel, blocksPerPixel, WaypointColors.withAlpha(rim, 0xFE));
        diamond(position, right, up, radius * fontPixel, blocksPerPixel, WaypointColors.withAlpha(color, 0xFE));
        if (initial.isEmpty()) return;
        // Text hangs down from where it is put, and its width takes in the space after the letter: moved up half a
        // letter's height (7 font pixels) and right half that space, the letter's middle is on the diamond's.
        double letterPixel = fontPixel * blocksPerPixel;
        Vec3 at = position.add(up.scale(3.5 * letterPixel)).add(right.scale(0.5 * letterPixel));
        float scale = (float) (16.0 * letterPixel);
        Gizmos.billboardText(initial, at, TextGizmo.Style.forColorAndCentered(WaypointColors.withAlpha(rim, 0xFE)).withScale(scale)).setAlwaysOnTop();
    }

    /** A filled diamond {@code radius} screen pixels from its middle to a corner, as rows of lines a pixel apart. */
    private static void diamond(Vec3 middle, Vec3 right, Vec3 up, double radius, double blocksPerPixel, int color) {
        for (double y = -radius + 0.5; y < radius; y += 1.0) {
            double half = radius - Math.abs(y);
            Vec3 row = middle.add(up.scale(y * blocksPerPixel));
            Gizmos.line(row.add(right.scale(-half * blocksPerPixel)), row.add(right.scale(half * blocksPerPixel)), color, 1.5f).setAlwaysOnTop();
        }
    }

    /**
     * A vertical line from {@code bottom} to {@code top} at {@code at} (its y unused), leaving out {@code gap} (low, high)
     * if given. As short segments: fog is worked out at the ends of each line, so one long line whose ends are far away
     * would fade out even where it passes close to the camera.
     */
    private static void vertical(Vec3 at, double bottom, double top, double[] gap, int color, float width, double segment) {
        if (gap != null && gap[0] < top && gap[1] > bottom) {
            vertical(at, bottom, gap[0], null, color, width, segment);
            vertical(at, gap[1], top, null, color, width, segment);
            return;
        }
        for (double y = bottom; y < top; y += segment) {
            Gizmos.line(at.add(0, y, 0), at.add(0, Math.min(top, y + segment), 0), color, width).setAlwaysOnTop();
        }
    }

    /**
     * The wide part of a beam, from the bottom of the place to the top of its highest block. A beam pulled in by
     * {@code pull} has its band pulled in towards the camera's height as much, so it is seen in the same direction as
     * the place.
     */
    private static void band(Waypoint waypoint, Vec3 camera, Vec3 at, double pull, int color, double[] gap) {
        double bottom = camera.y + (waypoint.bottomY() - camera.y) * pull;
        double top = camera.y + (waypoint.topY() + 1.0 - camera.y) * pull;
        double middle = (bottom + top) / 2;
        double half = Math.max((top - bottom) / 2, MIN_BAND * Math.hypot(at.x - camera.x, at.z - camera.z) / 2);
        vertical(at, middle - half, middle + half, gap, WaypointColors.withAlpha(color, 0xFF), BAND_WIDTH, Double.MAX_VALUE);
    }

    /** Starts or stops keeping the last frames (tests). Recording clears what was kept. */
    public static void setRecording(boolean value) {
        synchronized (FRAMES) {
            FRAMES.clear();
            recording = value;
        }
    }

    public static List<Frame> recordedFrames() {
        synchronized (FRAMES) {
            return List.copyOf(FRAMES);
        }
    }

    /** How many screen pixels a block one block in front of the camera covers, from the frame's field of view. */
    private static double pixelsPerBlock(Minecraft mc) {
        return mc.getWindow().getHeight() / 2.0 / Math.tan(Math.toRadians(CameraPosition.fov(mc)) / 2.0);
    }

    private static void record(Vec3 camera, List<Label> labels, List<double[]> beams, List<Marker> markers) {
        if (!recording || labels == null) return;
        synchronized (FRAMES) {
            if (FRAMES.size() >= RECORDED_FRAMES) FRAMES.removeFirst();
            FRAMES.addLast(new Frame(camera, labels, beams, markers));
        }
    }

    /**
     * The label of a waypoint at a horizontal distance, made of the parts turned on in the settings.
     *
     * @param heightAbove how far the waypoint is above the player ({@link Waypoint#heightAbove}; below if negative)
     */
    public static String labelText(WaypointDisplay.Shown waypoint, double distance, int heightAbove, WaypointsApiConfig config) {
        List<String> parts = new ArrayList<>();
        Optional<DimensionProjection.Projected> projected = waypoint.projected();
        if (projected.isPresent()) {
            parts.add("[" + Component.translatable("waypointsapi.dimension.short." + projected.get().from().identifier().getPath()).getString() + "]");
        }
        if (waypoint.waypoint().explored()) parts.add(EXPLORED_MARK);
        if (config.labelName) parts.add(Kinds.name(waypoint.waypoint()));
        // A projected waypoint always says where it really is: that is what the projection is for.
        if (config.labelCoordinates || projected.isPresent()) {
            int x = projected.map(DimensionProjection.Projected::originalX).orElse(waypoint.waypoint().pos().getX());
            int z = projected.map(DimensionProjection.Projected::originalZ).orElse(waypoint.waypoint().pos().getZ());
            parts.add("(" + x + ", " + z + ")");
        }
        if (config.labelDistance) parts.add(Math.round(distance) + "m");
        if (config.labelHeight && !projected.isPresent() && heightAbove != 0) parts.add(heightText(heightAbove));
        return String.join(" ", parts);
    }

    /** How far above (↑) or below (↓) the player a waypoint is, like {@code ↓35}. */
    public static String heightText(int heightAbove) {
        return (heightAbove > 0 ? "↑" : "↓") + Math.abs(heightAbove);
    }
}
