package waypointsapi.display;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import waypointsapi.kind.Kinds;

/** One color per group of a kind: from the kind's settings (the player's choice or the mod's), else one picked from the group's id. */
public final class WaypointColors {
    private WaypointColors() {
    }

    /** Opaque RGB as ARGB. */
    public static int of(Identifier kind, Identifier group) {
        int color = Kinds.color(kind, group);
        return color == -1 ? picked(group) : 0xFF000000 | (color & 0xFFFFFF);
    }

    /** A bright hue from the id, the same on every run. */
    public static int picked(Identifier group) {
        float hue = (group.toString().hashCode() & 0xFFFF) / 65536f;
        return 0xFF000000 | Mth.hsvToRgb(hue, 0.7f, 1.0f) & 0xFFFFFF;
    }

    /**
     * The outline that keeps text of a color readable: white around a dark color (a deep blue, which a dark outline would
     * swallow), black around the others. Dark is a relative luminance below a quarter.
     */
    public static int outlineFor(int rgb) {
        double luminance = (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF)) / 255.0;
        return luminance < 0.25 ? 0xFFFFFFFF : 0xFF000000;
    }

    public static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
