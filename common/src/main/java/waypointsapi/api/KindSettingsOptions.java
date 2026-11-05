package waypointsapi.api;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.display.WaypointColors;
import waypointsapi.kind.Kinds;
import waypointsapi.ui.Screens;

/**
 * The options of a kind's {@link KindSettings} for a YACL screen of the mod's own, the same in every mod: one group for
 * the kind (a line saying the look is Waypoints API's, with a button to its screen; shown, distance, how many), and one for each of its groups (shown, color, only the nearest), those of
 * {@link KindSettings#groups()} and of saved waypoints, by name. The options write to the settings; the mod's screen
 * saves them with the rest of its config. Build them when the screen opens (the groups are those of that moment).
 */
public final class KindSettingsOptions {
    private KindSettingsOptions() {
    }

    /** @throws IllegalArgumentException when the kind is not one registered with Waypoints API */
    public static List<OptionGroup> of(WaypointKind kind) {
        Kinds.Kind registered = Kinds.get(kind.id()).orElseThrow(() -> new IllegalArgumentException("Not a registered kind: " + kind.id()));
        KindSettings settings = registered.definition().settings();
        List<OptionGroup> groups = new ArrayList<>();
        groups.add(OptionGroup.createBuilder().name(registered.name())
                .option(LabelOption.create(Component.translatable("waypointsapi.config.kind.look_elsewhere")))
                .option(ButtonOption.createBuilder()
                        .name(Component.translatable("waypointsapi.config.open_api_settings"))
                        .text(Component.translatable("waypointsapi.config.open"))
                        .description(OptionDescription.of(Component.translatable("waypointsapi.config.open_api_settings.desc")))
                        .action(screen -> Screens.open(WaypointsApiConfig.createScreen(screen)))
                        .build())
                .option(bool("kind.show", true, settings::shown, settings::setShown))
                .option(slider("kind.distance", settings.defaultDisplayDistance(), settings::displayDistance, settings::setDisplayDistance,
                        16, Math.max(16, settings.maxDisplayDistance()), 16))
                .option(slider("kind.max_shown", settings.defaultMaxShown(), settings::maxShown, settings::setMaxShown, 0, 64, 1))
                .build());
        for (Identifier group : registered.groups()) {
            Color base = new Color(color(settings.defaultGroupColor(group), group));
            groups.add(OptionGroup.createBuilder().name(Kinds.groupName(kind.id(), group)).collapsed(true)
                    .option(bool("group.show", settings.defaultGroupShown(group), () -> settings.groupShown(group),
                            value -> settings.setGroupShown(group, value)))
                    .option(Option.<Color>createBuilder()
                            .name(Component.translatable("waypointsapi.config.group.color"))
                            .description(OptionDescription.of(Component.translatable("waypointsapi.config.group.color.desc")))
                            .binding(base, () -> new Color(color(settings.groupColor(group), group)), value -> {
                                int rgb = value.getRGB() & 0xFFFFFF;
                                settings.setGroupColor(group, rgb == (base.getRGB() & 0xFFFFFF) ? -1 : rgb);
                            })
                            .controller(ColorControllerBuilder::create)
                            .build())
                    .option(bool("group.nearest_only", settings.defaultGroupNearestOnly(group), () -> settings.groupNearestOnly(group),
                            value -> settings.setGroupNearestOnly(group, value)))
                    .build());
        }
        return groups;
    }

    /** Opaque RGB: the given one, or the one picked from the group's id for -1. */
    private static int color(int rgb, Identifier group) {
        return (rgb == -1 ? WaypointColors.picked(group) : rgb) & 0xFFFFFF;
    }

    private static Option<Boolean> bool(String key, boolean defaultValue, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("waypointsapi.config." + key))
                .description(OptionDescription.of(Component.translatable("waypointsapi.config." + key + ".desc")))
                .binding(defaultValue, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Integer> slider(String key, int defaultValue, Supplier<Integer> getter, Consumer<Integer> setter,
                                          int min, int max, int step) {
        return Option.<Integer>createBuilder()
                .name(Component.translatable("waypointsapi.config." + key))
                .description(OptionDescription.of(Component.translatable("waypointsapi.config." + key + ".desc")))
                .binding(defaultValue, getter, setter)
                .controller(option -> IntegerSliderControllerBuilder.create(option).range(min, max).step(step))
                .build();
    }
}
