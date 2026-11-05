package waypointsapi.config;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import waypointsapi.Constants;
import waypointsapi.kind.Kinds;
import waypointsapi.platform.Services;
import waypointsapi.ui.Screens;

/**
 * Settings saved to config/waypointsapi.json5: how waypoints of every kind are drawn, and which map mods get them. Each
 * kind's own settings (shown or not, how far, how many, its groups) are its mod's ({@link waypointsapi.api.KindSettings}).
 */
public final class WaypointsApiConfig {
    public static final ConfigClassHandler<WaypointsApiConfig> HANDLER = ConfigClassHandler.createBuilder(WaypointsApiConfig.class)
            .id(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID + ".json5"))
                    .setJson5(true)
                    .build())
            .build();

    /** Beams, labels and the arrows at the screen's edge. */
    @SerialEntry
    public boolean showWaypoints = true;

    @SerialEntry
    public boolean showBeams = true;

    /** A diamond at the place's height on each beam, shown whether the beams are or not. */
    @SerialEntry
    public boolean showMarkers = true;

    /** The first letter of the group's name in each diamond. */
    @SerialEntry
    public boolean showInitials = true;

    @SerialEntry
    public boolean showLabels = true;

    @SerialEntry
    public boolean labelName = true;

    @SerialEntry
    public boolean labelCoordinates = false;

    @SerialEntry
    public boolean labelDistance = true;

    /** How far above or below the player the waypoint is. */
    @SerialEntry
    public boolean labelHeight = true;

    /** The nearest this many get a label; others only while looked at. */
    @SerialEntry
    public int maxLabels = 8;

    /** Arrows at the screen's edges for the waypoints shown that are out of view. */
    @SerialEntry
    public boolean showDirectionHud = true;

    /** Whether explored waypoints are shown in the world too (a pinned one always is). */
    @SerialEntry
    public boolean showExplored = false;

    /** The other dimension's waypoints (the Overworld's in the Nether, and back), at scaled coordinates; pinned ones always. */
    @SerialEntry
    public boolean showOtherDimension = true;

    /** Only the nearest waypoint of every group is shown, whatever each group's own setting says. */
    @SerialEntry
    public boolean nearestOfEachGroup = false;

    @SerialEntry
    public boolean mapJourneyMap = true;

    @SerialEntry
    public boolean mapXaero = true;

    @SerialEntry
    public boolean mapVoxelMap = true;

    public static WaypointsApiConfig get() {
        return HANDLER.instance();
    }

    /** Whether only the nearest of a group is shown: for every group when {@link #nearestOfEachGroup}, else by the kind's setting. */
    public boolean nearestOnly(Identifier kind, Identifier group) {
        return nearestOfEachGroup || Kinds.nearestOnly(kind, group);
    }

    /**
     * One category for each question: which waypoints are shown, how they look, where each kind's own settings are (a button
     * to its mod's screen), and which map mods get them. Each starts with a line saying what is changed there, and where the
     * rest is.
     */
    /**
     * The pictures under the descriptions, by option, files in {@code textures/gui/config/}: cut from the game test's
     * screenshots by {@code scripts/config_images.py}.
     */
    public static final Map<String, String> PICTURES = Map.of(
            "show_waypoints", "labels.png",
            "show_beams", "beams.png",
            "show_markers", "markers.png",
            "show_initials", "markers.png",
            "show_direction_hud", "arrows.gif",
            "show_labels", "labels.png",
            "max_labels", "labels.png");

    public static Identifier picture(String file) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/config/" + file);
    }

    /** The size of the pictures in the descriptions (half the game test's screenshots). */
    private static final int PICTURE_WIDTH = 320;
    private static final int PICTURE_HEIGHT = 180;

    public static Screen createScreen(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> {
            builder.title(Component.translatable("waypointsapi.config.title"))
                    .category(category("shown")
                            .option(bool("show_waypoints", defaults.showWaypoints, () -> config.showWaypoints, value -> config.showWaypoints = value))
                            .option(bool("show_explored", defaults.showExplored, () -> config.showExplored, value -> config.showExplored = value))
                            .option(bool("show_other_dimension", defaults.showOtherDimension, () -> config.showOtherDimension, value -> config.showOtherDimension = value))
                            .option(bool("nearest_of_each_group", defaults.nearestOfEachGroup, () -> config.nearestOfEachGroup, value -> config.nearestOfEachGroup = value))
                            .build())
                    .category(category("look")
                            .group(group("beams",
                                    bool("show_beams", defaults.showBeams, () -> config.showBeams, value -> config.showBeams = value),
                                    bool("show_markers", defaults.showMarkers, () -> config.showMarkers, value -> config.showMarkers = value),
                                    bool("show_initials", defaults.showInitials, () -> config.showInitials, value -> config.showInitials = value),
                                    bool("show_direction_hud", defaults.showDirectionHud, () -> config.showDirectionHud, value -> config.showDirectionHud = value)))
                            .group(group("labels",
                                    bool("show_labels", defaults.showLabels, () -> config.showLabels, value -> config.showLabels = value),
                                    slider("max_labels", defaults.maxLabels, () -> config.maxLabels, value -> config.maxLabels = value, 0, 32, 1)))
                            .group(group("label_parts",
                                    bool("label_name", defaults.labelName, () -> config.labelName, value -> config.labelName = value),
                                    bool("label_distance", defaults.labelDistance, () -> config.labelDistance, value -> config.labelDistance = value),
                                    bool("label_height", defaults.labelHeight, () -> config.labelHeight, value -> config.labelHeight = value),
                                    bool("label_coordinates", defaults.labelCoordinates, () -> config.labelCoordinates, value -> config.labelCoordinates = value)))
                            .build())
                    .category(category("kinds")
                            .options(kindButtons())
                            .build())
                    .category(category("maps")
                            .option(bool("map_journeymap", defaults.mapJourneyMap, () -> config.mapJourneyMap, value -> config.mapJourneyMap = value))
                            .option(bool("map_xaero", defaults.mapXaero, () -> config.mapXaero, value -> config.mapXaero = value))
                            .option(bool("map_voxelmap", defaults.mapVoxelMap, () -> config.mapVoxelMap, value -> config.mapVoxelMap = value))
                            .build());
            return builder;
        }).generateScreen(parent);
    }

    /** A category whose first line says what is changed in it. */
    private static ConfigCategory.Builder category(String key) {
        Component about = Component.translatable("waypointsapi.config.category." + key + ".about");
        return ConfigCategory.createBuilder()
                .name(Component.translatable("waypointsapi.config.category." + key))
                .tooltip(about)
                .option(LabelOption.create(about));
    }

    /** A button for each kind, opening the settings screen of the mod that added it; greyed out when the mod has none. */
    private static List<Option<?>> kindButtons() {
        List<Option<?>> buttons = new ArrayList<>();
        for (Kinds.Kind kind : Kinds.all()) {
            boolean hasScreen = kind.setting(settings -> settings.settingsScreen(null).isPresent(), false);
            buttons.add(ButtonOption.createBuilder()
                    .name(kind.name())
                    .text(Component.translatable("waypointsapi.config.open"))
                    .description(OptionDescription.of(Component.translatable(hasScreen
                            ? "waypointsapi.config.kind_settings.desc" : "waypointsapi.config.kind_settings.none.desc")))
                    .available(hasScreen)
                    .action(screen -> kind.setting(settings -> settings.settingsScreen(screen), Optional.<Screen>empty()).ifPresent(Screens::open))
                    .build());
        }
        if (buttons.isEmpty()) buttons.add(LabelOption.create(Component.translatable("waypointsapi.list.no_kinds")));
        return buttons;
    }

    /** The option's text, with its picture under it if it has one ({@link #PICTURES}; a GIF moves). */
    private static OptionDescription description(String key) {
        OptionDescription.Builder description = OptionDescription.createBuilder().text(Component.translatable("waypointsapi.config." + key + ".desc"));
        String picture = PICTURES.get(key);
        if (picture == null) return description.build();
        Identifier file = picture(picture);
        return (picture.endsWith(".gif") ? description.gifImage(file) : description.image(file, PICTURE_WIDTH, PICTURE_HEIGHT)).build();
    }

    private static OptionGroup group(String key, Option<?>... options) {
        OptionGroup.Builder group = OptionGroup.createBuilder().name(Component.translatable("waypointsapi.config.group." + key));
        for (Option<?> option : options) group.option(option);
        return group.build();
    }

    private static Option<Boolean> bool(String key, boolean defaultValue, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("waypointsapi.config." + key))
                .description(description(key))
                .binding(defaultValue, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Integer> slider(String key, int defaultValue, Supplier<Integer> getter, Consumer<Integer> setter, int min, int max, int step) {
        return Option.<Integer>createBuilder()
                .name(Component.translatable("waypointsapi.config." + key))
                .description(description(key))
                .binding(defaultValue, getter, setter)
                .controller(option -> IntegerSliderControllerBuilder.create(option).range(min, max).step(step))
                .build();
    }
}
