package waypointsapi.display;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import waypointsapi.Constants;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.waypoint.WaypointGuide;

/**
 * Arrows at the left and right edges of the screen for shown waypoints that are outside the view, nearest at the
 * top, with how far and which way (the compass point) each is. The sprites are {@code waypointsapi:hud/arrow_left} and
 * {@code hud/arrow_right} (white, tinted with the group's color), so a resource pack can replace them.
 */
public final class DirectionHud {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "direction");
    public static final Identifier ARROW_LEFT = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "hud/arrow_left");
    public static final Identifier ARROW_RIGHT = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "hud/arrow_right");
    private static final int ARROW_WIDTH = 7;
    private static final int ARROW_HEIGHT = 9;
    private static final int ROW = 12;
    private static final int MAX_PER_SIDE = 6;

    /** An arrow to draw: which side, and the waypoint. */
    public record Arrow(boolean left, WaypointDisplay.Shown waypoint) {
    }

    private DirectionHud() {
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !WaypointsApiConfig.get().showDirectionHud) return;
        List<Arrow> arrows = arrows(mc, WaypointRenderer.shown());
        int width = graphics.guiWidth();
        int centerY = graphics.guiHeight() / 2;
        int left = 0;
        int right = 0;
        for (Arrow arrow : arrows) {
            int row = arrow.left() ? left++ : right++;
            int y = centerY - ARROW_HEIGHT / 2 + (row - MAX_PER_SIDE / 2) * ROW;
            int color = WaypointColors.withAlpha(WaypointColors.of(arrow.waypoint().waypoint().kind(), arrow.waypoint().waypoint().group()), 0xFF);
            String text = text(mc, arrow.waypoint());
            if (arrow.left()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_LEFT, 2, y, ARROW_WIDTH, ARROW_HEIGHT, color);
                text(graphics, mc, text, 2 + ARROW_WIDTH + 2, y + 1, color);
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_RIGHT, width - 2 - ARROW_WIDTH, y, ARROW_WIDTH, ARROW_HEIGHT, color);
                text(graphics, mc, text, width - 4 - ARROW_WIDTH - mc.font.width(text), y + 1, color);
            }
        }
    }

    /**
     * Text in a color with the usual shadow, or for a dark color an outline in white ({@link WaypointColors#outlineFor}):
     * the shadow, a darker copy of the color, would not set it off.
     */
    private static void text(GuiGraphicsExtractor graphics, Minecraft mc, String text, int x, int y, int color) {
        int outline = WaypointColors.outlineFor(color);
        if (outline == 0xFF000000) {
            graphics.text(mc.font, text, x, y, color, true);
            return;
        }
        for (int[] off : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) graphics.text(mc.font, text, x + off[0], y + off[1], outline, false);
        graphics.text(mc.font, text, x, y, color, false);
    }

    /** "120m NE": how far, and the compass point it is in from the player. */
    public static String text(Minecraft mc, WaypointDisplay.Shown waypoint) {
        String direction = WaypointGuide.direction(waypoint.x() - mc.player.getX(), waypoint.z() - mc.player.getZ());
        return Math.round(waypoint.distance()) + "m " + net.minecraft.network.chat.Component.translatable("waypointsapi.direction.short." + direction).getString();
    }

    /** The waypoints outside the horizontal view, at most a few per side, nearest first. */
    public static List<Arrow> arrows(Minecraft mc, List<WaypointDisplay.Shown> shown) {
        double halfWidth = halfHorizontalFov(mc);
        List<Arrow> arrows = new ArrayList<>();
        int left = 0;
        int right = 0;
        for (WaypointDisplay.Shown waypoint : shown) {
            double dx = waypoint.x() - mc.player.getX();
            double dz = waypoint.z() - mc.player.getZ();
            float bearing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            float relative = Mth.wrapDegrees(bearing - mc.player.getYRot());
            if (Math.abs(relative) <= halfWidth) continue;
            boolean isLeft = relative < 0;
            if (isLeft ? left >= MAX_PER_SIDE : right >= MAX_PER_SIDE) continue;
            if (isLeft) left++;
            else right++;
            arrows.add(new Arrow(isLeft, waypoint));
        }
        return arrows;
    }

    private static double halfHorizontalFov(Minecraft mc) {
        double vertical = Math.toRadians(mc.options.fov().get());
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        return Math.toDegrees(Math.atan(Math.tan(vertical / 2) * aspect));
    }
}
