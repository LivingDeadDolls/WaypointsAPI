package waypointsapi.api;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * A kind of waypoints, for {@link WaypointRegistry#register}: made with {@link #builder}. Later versions may add
 * methods to the builder, never change the ones there are.
 */
public final class KindDefinition {
    private final Identifier id;
    private final Component name;
    private final Item icon;
    private final Function<Identifier, Component> groupNames;
    private final Function<Identifier, ? extends ItemLike> groupIcons;
    private final Function<Identifier, String> groupInitials;
    private final KindSettings settings;
    private final boolean heightless;

    private KindDefinition(Builder builder) {
        id = builder.id;
        name = builder.name;
        icon = builder.icon;
        groupNames = builder.groupNames;
        groupIcons = builder.groupIcons;
        groupInitials = builder.groupInitials;
        settings = builder.settings;
        heightless = builder.heightless;
    }

    /**
     * @param id   unique among kinds, e.g. {@code structurewaypoints:structures}; it also names the kind's files and settings
     * @param name the kind's name on its tab and in the settings
     */
    public static Builder builder(Identifier id, Component name) {
        return new Builder(id, name);
    }

    public Identifier id() {
        return id;
    }

    public Component name() {
        return name;
    }

    /** The item drawn on the kind's tab. */
    public Item icon() {
        return icon;
    }

    /** The name of a group of this kind. */
    public Component groupName(Identifier group) {
        return groupNames.apply(group);
    }

    /** The item drawn for a group of this kind ({@link Builder#groupIcon}), if the mod gives one. */
    public Optional<Item> groupIcon(Identifier group) {
        ItemLike icon = groupIcons.apply(group);
        return icon == null ? Optional.empty() : Optional.of(icon.asItem());
    }

    /** The letter the mod gives a group of this kind ({@link Builder#groupInitial}), if it gives one. */
    public Optional<String> groupInitial(Identifier group) {
        return Optional.ofNullable(groupInitials.apply(group));
    }

    /** How the kind's waypoints are shown, kept by the mod. */
    public KindSettings settings() {
        return settings;
    }

    /** Whether the kind's places have no height of their own ({@link Builder#heightless}). */
    public boolean heightless() {
        return heightless;
    }

    public static final class Builder {
        private final Identifier id;
        private final Component name;
        private Item icon = Items.COMPASS;
        private Function<Identifier, Component> groupNames = group -> Component.literal(group.toString());
        private Function<Identifier, ? extends ItemLike> groupIcons = group -> null;
        private Function<Identifier, String> groupInitials = group -> null;
        private KindSettings settings;
        private boolean heightless;

        private Builder(Identifier id, Component name) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = Objects.requireNonNull(name, "name");
        }

        /**
         * The item drawn on the kind's tab (an item, not a stack: stacks cannot be made yet while the client starts). A
         * compass if not set.
         */
        public Builder icon(ItemLike icon) {
            this.icon = Objects.requireNonNull(icon, "icon").asItem();
            return this;
        }

        /**
         * The names of the kind's groups (a structure set, a mob), shown in the list and in the filter of groups. Called
         * on the client thread. The group's id as text if not set.
         */
        public Builder groupName(Function<Identifier, Component> names) {
            this.groupNames = Objects.requireNonNull(names, "names");
            return this;
        }

        /**
         * The items drawn for the kind's groups (a structure's bell, a mob's spawn egg): in the list's rows and on
         * JourneyMap's marks. Called on the client thread; null for a group without one, which gets the kind's
         * {@link #icon} in the list and a dot on the map. None if not set.
         */
        public Builder groupIcon(Function<Identifier, ? extends ItemLike> icons) {
            this.groupIcons = Objects.requireNonNull(icons, "icons");
            return this;
        }

        /**
         * The letters drawn in the kind's groups' diamonds in the world and on Xaero's marks: only the first character
         * is drawn. Called on the client thread, every frame for each waypoint shown: return a value at hand. Null (or
         * blank) for a group gives the first letter or digit of its {@link #groupName}, as when not set.
         */
        public Builder groupInitial(Function<Identifier, String> initials) {
            this.groupInitials = Objects.requireNonNull(initials, "initials");
            return this;
        }

        /** How the kind's waypoints are shown, kept and saved by the mod: required. */
        public Builder settings(KindSettings settings) {
            this.settings = Objects.requireNonNull(settings, "settings");
            return this;
        }

        /**
         * The kind's places have no height of their own (a biome, a region): their beams are drawn the same width all the
         * way, without the wider part over the place's height, their labels are at the camera's height and do not
         * say how far above or below the player they are, and they become explored by x and z alone (inside an area
         * seen from above, at any height). Places have a height if not set.
         */
        public Builder heightless() {
            this.heightless = true;
            return this;
        }

        /** @throws IllegalStateException when {@link #settings} was not given */
        public KindDefinition build() {
            if (settings == null) throw new IllegalStateException("The kind " + id + " needs its settings");
            return new KindDefinition(this);
        }
    }
}
