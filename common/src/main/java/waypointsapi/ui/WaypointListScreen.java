package waypointsapi.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.display.WaypointColors;
import waypointsapi.display.WaypointRenderer;
import waypointsapi.integration.MapIntegration;
import waypointsapi.kind.Kinds;
import waypointsapi.teleport.TeleportService;
import waypointsapi.waypoint.Waypoint;
import waypointsapi.waypoint.WaypointGuide;
import waypointsapi.waypoint.WaypointStore;

/**
 * The list of waypoints: one tab per kind, with search, filters by dimension and state, sorting, renaming, pinning
 * or never showing, marking explored, teleporting, adding to map mods, and the way to the groups and the settings.
 * Built from vanilla widgets only, so resource packs that restyle vanilla screens restyle it too.
 */
public final class WaypointListScreen extends Screen {
    private static final int HEADER = 74;
    /** Two rows of buttons: those for the selected waypoint, then those for the list. */
    private static final int FOOTER = 57;
    private static final int ROW_HEIGHT = 24;
    /** Where a row's text starts, after the icon. */
    private static final int TEXT_X = 22;
    private static final int REFRESH_TICKS = 20;
    /** Five buttons across a row of the footer, fitting a 427 wide screen (854 pixels at scale 2). */
    private static final int FOOTER_BUTTON = 80;
    private static final int TAB_WIDTH = 110;
    private static final int ICON = 16;

    /** Kept between openings, like a remembered tab. */
    private static Identifier selectedKind;
    private static String search = "";
    private static WaypointList.DimensionFilter dimensionFilter = WaypointList.DimensionFilter.CURRENT;
    private static WaypointList.StateFilter stateFilter = WaypointList.StateFilter.ALL;
    private static WaypointList.Sort sort = WaypointList.Sort.DISTANCE;

    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER, FOOTER);
    private final List<TabButton> tabs = new ArrayList<>();
    private RowList list;
    private Button rename;
    private Button show;
    private Button explored;
    private Button teleport;
    private Button toMap;
    private Button groups;
    private int ticks;
    /** The waypoints shown in the world when the rows were last made. */
    private Set<String> shown = Set.of();
    /** The waypoint to select when the screen opens (clicked on a map), or null. */
    private Waypoint openAt;

    private record TabButton(Kinds.Kind kind, Button button) {
    }

    public WaypointListScreen(Screen parent) {
        super(Component.translatable("waypointsapi.list.title"));
        this.parent = parent;
    }

    /** The list at a waypoint's tab with it selected, its filters widened if they leave it out. */
    public static WaypointListScreen at(Screen parent, Waypoint waypoint) {
        WaypointListScreen screen = new WaypointListScreen(parent);
        screen.openAt = waypoint;
        selectedKind = waypoint.kind();
        return screen;
    }

    @Override
    protected void init() {
        List<Kinds.Kind> kinds = Kinds.all();
        if (kinds.stream().noneMatch(kind -> kind.id().equals(selectedKind))) selectedKind = kinds.isEmpty() ? null : kinds.getFirst().id();

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));
        LinearLayout tabRow = header.addChild(LinearLayout.horizontal().spacing(2));
        tabs.clear();
        int tabWidth = kinds.isEmpty() ? TAB_WIDTH : Math.min(TAB_WIDTH, (width - 8) / kinds.size() - 2);
        for (Kinds.Kind kind : kinds) {
            Button button = tabRow.addChild(Button.builder(kind.name(), b -> selectTab(kind.id())).width(tabWidth).build());
            tabs.add(new TabButton(kind, button));
        }
        if (kinds.isEmpty()) header.addChild(new StringWidget(Component.translatable("waypointsapi.list.no_kinds").withStyle(ChatFormatting.GRAY), font));

        LinearLayout controls = header.addChild(LinearLayout.horizontal().spacing(4));
        EditBox searchBox = controls.addChild(new EditBox(font, 0, 0, 110, 20, Component.translatable("waypointsapi.list.search")));
        searchBox.setHint(Component.translatable("waypointsapi.list.search").withStyle(EditBox.SEARCH_HINT_STYLE));
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            refresh();
        });
        controls.addChild(CycleButton.builder((WaypointList.DimensionFilter value) -> label("dimension", value), dimensionFilter)
                .withValues(WaypointList.DimensionFilter.values())
                .displayOnlyValue().create(0, 0, 100, 20, Component.translatable("waypointsapi.list.dimension"), (button, value) -> {
                    dimensionFilter = value;
                    refresh();
                }));
        controls.addChild(CycleButton.builder((WaypointList.StateFilter value) -> label("state", value), stateFilter)
                .withValues(WaypointList.StateFilter.values())
                .displayOnlyValue().create(0, 0, 90, 20, Component.translatable("waypointsapi.list.state"), (button, value) -> {
                    stateFilter = value;
                    refresh();
                }));
        controls.addChild(CycleButton.builder((WaypointList.Sort value) -> label("sort", value), sort)
                .withValues(WaypointList.Sort.values())
                .displayOnlyValue().create(0, 0, 90, 20, Component.translatable("waypointsapi.list.sort"), (button, value) -> {
                    sort = value;
                    refresh();
                }));

        list = layout.addToContents(new RowList(minecraft));

        LinearLayout footer = layout.addToFooter(LinearLayout.vertical().spacing(4));
        LinearLayout selectedRow = footer.addChild(LinearLayout.horizontal().spacing(4));
        LinearLayout listRow = footer.addChild(LinearLayout.horizontal().spacing(4));
        boolean map = !MapIntegration.targets().isEmpty();
        // Narrower where the screen is too narrow for five.
        int buttonWidth = Math.min(FOOTER_BUTTON, (width - 8 - 4 * 4) / 5);
        rename = selectedRow.addChild(Button.builder(Component.translatable("waypointsapi.list.rename"), button -> renameSelected()).width(buttonWidth).build());
        show = selectedRow.addChild(Button.builder(Component.translatable("waypointsapi.list.show.auto"), button -> cycleShowSelected()).width(buttonWidth).build());
        explored = selectedRow.addChild(Button.builder(Component.translatable("waypointsapi.list.explored.mark"), button -> toggleExplored()).width(buttonWidth).build());
        teleport = selectedRow.addChild(Button.builder(Component.translatable("waypointsapi.list.teleport"), button -> teleportSelected()).width(buttonWidth).build());
        toMap = map ? selectedRow.addChild(Button.builder(Component.translatable("waypointsapi.list.to_map"), button -> addSelectedToMap()).width(buttonWidth).build()) : null;
        listRow.addChild(Button.builder(Component.translatable("waypointsapi.list.guide"), button -> guideNearest()).width(buttonWidth).build());
        groups = listRow.addChild(Button.builder(Component.translatable("waypointsapi.list.groups"),
                button -> kind().ifPresent(kind -> Screens.open(new GroupFilterScreen(this, kind)))).width(buttonWidth).build());
        listRow.addChild(Button.builder(Component.translatable("waypointsapi.list.settings"),
                button -> Screens.open(WaypointsApiConfig.createScreen(this))).width(buttonWidth).build());
        listRow.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(buttonWidth).build());

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        refresh();
        setInitialFocus(searchBox);
        if (openAt != null) {
            Waypoint wanted = openAt;
            openAt = null;
            if (!reveal(wanted.key())) {
                // Left out by the filters: show every dimension and state; a hidden group shows when searched by name.
                dimensionFilter = WaypointList.DimensionFilter.ALL;
                stateFilter = WaypointList.StateFilter.ALL;
                search = Kinds.groupShown(wanted.kind(), wanted.group()) ? "" : Kinds.name(wanted);
                rebuildWidgets();
                reveal(wanted.key());
            }
        }
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if (list != null) list.updateSize(width, layout);
    }

    /** The kind icons on the tabs, over the buttons. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        for (TabButton tab : tabs) {
            Button button = tab.button();
            graphics.item(new net.minecraft.world.item.ItemStack(tab.kind().definition().icon()), button.getX() + 2, button.getY() + (button.getHeight() - ICON) / 2);
        }
    }

    /** The kinds on the tabs, in order (for tests). */
    public List<Identifier> tabs() {
        return tabs.stream().map(tab -> tab.kind().id()).toList();
    }

    /** The kind whose tab is open. */
    public Optional<Kinds.Kind> kind() {
        return selectedKind == null ? Optional.empty() : Kinds.get(selectedKind);
    }

    /** Opens a kind's tab (as clicking it does). */
    public void selectTab(Identifier kind) {
        selectedKind = kind;
        refresh();
    }

    @Override
    public void tick() {
        if (++ticks >= REFRESH_TICKS) {
            ticks = 0;
            refresh();
        }
    }

    @Override
    public void onClose() {
        WaypointStore.saveIfDirty();
        Screens.open(parent);
    }

    /** The rows shown now (for tests). */
    public List<WaypointList.Row> rows() {
        List<WaypointList.Row> rows = new ArrayList<>();
        for (RowList.RowEntry entry : list.children()) rows.add(entry.row);
        return rows;
    }

    /** Selects the row with this key (for tests and keyboard use). */
    public void select(String key) {
        reveal(key);
    }

    /** The waypoint of the selected row, if any (for tests). */
    public Optional<Waypoint> selectedWaypoint() {
        RowList.RowEntry selected = list == null ? null : list.getSelected();
        return selected == null ? Optional.empty() : Optional.of(selected.row.waypoint());
    }

    /** Selects a row and scrolls to it. False if no row has that key. */
    private boolean reveal(String key) {
        for (RowList.RowEntry entry : list.children()) {
            if (entry.row.key().equals(key)) {
                list.setSelected(entry);
                list.reveal(entry);
                return true;
            }
        }
        return false;
    }

    void refresh() {
        for (TabButton tab : tabs) tab.button().active = !tab.kind().id().equals(selectedKind);
        if (groups != null) groups.active = kind().isPresent();
        if (list == null || minecraft == null || minecraft.player == null || minecraft.level == null) return;
        Optional<Kinds.Kind> kind = kind();
        var current = minecraft.level.dimension();
        List<Waypoint> waypoints = new ArrayList<>();
        if (kind.isPresent()) {
            for (var dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) waypoints.addAll(WaypointStore.all(kind.get().id(), dimension));
            if (current != Level.OVERWORLD && current != Level.NETHER && current != Level.END) waypoints.addAll(WaypointStore.all(kind.get().id(), current));
        }
        shown = shownKeys();
        WaypointsApiConfig config = WaypointsApiConfig.get();
        WaypointList.Names names = new WaypointList.Names() {
            @Override
            public String name(Waypoint waypoint) {
                return Kinds.name(waypoint);
            }

            @Override
            public String groupName(Waypoint waypoint) {
                return Kinds.groupName(waypoint.kind(), waypoint.group()).getString();
            }
        };
        WaypointList.Filter filter = new WaypointList.Filter(search, dimensionFilter, stateFilter, sort,
                group -> kind.isPresent() && !Kinds.groupShown(kind.get().id(), group), shown);
        list.show(WaypointList.filter(WaypointList.rows(waypoints, current, minecraft.player.position(), names), filter, current));
        updateButtons();
    }

    /** The waypoints shown in the world now. */
    private static Set<String> shownKeys() {
        Set<String> keys = new HashSet<>();
        for (var waypoint : WaypointRenderer.shown()) keys.add(waypoint.waypoint().key());
        return keys;
    }

    private Optional<Waypoint> selected() {
        return selectedWaypoint().flatMap(WaypointStore::current);
    }

    /** Cycles whether the selected waypoint is shown in the world: by the usual rules, always (pinned), never (as its button does). */
    public void cycleShowSelected() {
        selected().ifPresent(waypoint -> {
            Waypoint.Show next = switch (waypoint.show()) {
                case AUTO -> Waypoint.Show.PINNED;
                case PINNED -> Waypoint.Show.HIDDEN;
                case HIDDEN -> Waypoint.Show.AUTO;
            };
            WaypointStore.setShow(waypoint, next);
            refresh();
            reveal(waypoint.key());
        });
    }

    /** Marks the selected waypoint explored, or takes the mark off. */
    private void toggleExplored() {
        selected().ifPresent(waypoint -> {
            WaypointStore.setExplored(waypoint, !waypoint.explored());
            refresh();
            reveal(waypoint.key());
        });
    }

    /**
     * Guides to the nearest unexplored waypoint of those the list shows now (its tab, search and filters), in the
     * player's dimension, and closes the list so the way is seen.
     */
    void guideNearest() {
        if (minecraft == null || minecraft.level == null) return;
        List<Waypoint> listed = new ArrayList<>();
        for (WaypointList.Row row : rows()) {
            if (row.waypoint().dimension() == minecraft.level.dimension()) listed.add(row.waypoint());
        }
        Component what = search.isBlank() ? kind().map(Kinds.Kind::name).orElse(Component.translatable("waypointsapi.guide.listed"))
                : Component.literal(search.strip());
        if (WaypointGuide.guideNearest(minecraft, listed, what)) Screens.open(null);
    }

    /** Adds the selected waypoint to the waypoints of each map mod turned on. */
    private void addSelectedToMap() {
        Optional<Waypoint> waypoint = selected();
        if (waypoint.isEmpty() || minecraft.player == null) return;
        List<String> added = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (MapIntegration.Target target : MapIntegration.targets()) (target.addWaypoint(waypoint.get()) ? added : failed).add(target.name());
        Component message;
        if (failed.isEmpty()) message = Component.translatable("waypointsapi.list.to_map.done", String.join(", ", added));
        else if (added.isEmpty()) message = Component.translatable("waypointsapi.list.to_map.failed", String.join(", ", failed));
        else message = Component.translatable("waypointsapi.list.to_map.partly", String.join(", ", added), String.join(", ", failed));
        minecraft.player.sendOverlayMessage(message);
    }

    private void teleportSelected() {
        selected().ifPresent(waypoint -> {
            if (TeleportService.teleport(minecraft, waypoint)) Screens.open(null);
            else if (minecraft.player != null) minecraft.player.sendOverlayMessage(Component.translatable("waypointsapi.teleport.not_allowed"));
        });
    }

    private void renameSelected() {
        selected().ifPresent(waypoint -> Screens.open(new RenameScreen(this, waypoint)));
    }

    private void updateButtons() {
        Optional<Waypoint> selected = list == null ? Optional.empty() : selected();
        boolean any = selected.isPresent();
        if (rename != null) rename.active = any;
        if (show != null) {
            show.active = any;
            Waypoint.Show current = selected.map(Waypoint::show).orElse(Waypoint.Show.AUTO);
            show.setMessage(Component.translatable("waypointsapi.list.show." + current.name().toLowerCase(Locale.ROOT)));
        }
        if (explored != null) {
            explored.active = any;
            boolean is = selected.map(Waypoint::explored).orElse(false);
            explored.setMessage(Component.translatable(is ? "waypointsapi.list.explored.unmark" : "waypointsapi.list.explored.mark"));
        }
        if (teleport != null) teleport.active = any && TeleportService.available(minecraft);
        if (toMap != null) toMap.active = any;
    }

    private static Component label(String group, Enum<?> value) {
        return Component.translatable("waypointsapi.list." + group + "." + value.name().toLowerCase(Locale.ROOT));
    }

    final class RowList extends ObjectSelectionList<RowList.RowEntry> {
        RowList(Minecraft minecraft) {
            super(minecraft, WaypointListScreen.this.width, WaypointListScreen.this.height - HEADER - FOOTER, HEADER, ROW_HEIGHT);
        }

        void show(List<WaypointList.Row> rows) {
            String selectedKey = getSelected() == null ? null : getSelected().row.key();
            List<RowEntry> entries = new ArrayList<>();
            RowEntry reselect = null;
            for (WaypointList.Row row : rows) {
                RowEntry entry = new RowEntry(row);
                entries.add(entry);
                if (row.key().equals(selectedKey)) reselect = entry;
            }
            replaceEntries(entries);
            setSelected(reselect);
        }

        @Override
        public int getRowWidth() {
            return Math.min(420, WaypointListScreen.this.width - 40);
        }

        @Override
        public void setSelected(RowEntry selected) {
            super.setSelected(selected);
            updateButtons();
        }

        void reveal(RowEntry entry) {
            centerScrollOn(entry);
        }

        final class RowEntry extends ObjectSelectionList.Entry<RowEntry> {
            private final WaypointList.Row row;

            RowEntry(WaypointList.Row row) {
                this.row = row;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                int x = getContentX();
                int y = getContentY();
                Waypoint waypoint = row.waypoint();
                // The group's item (else the kind's), with a square of the group's color on its corner.
                graphics.item(new net.minecraft.world.item.ItemStack(Kinds.icon(waypoint.kind(), waypoint.group())), x + 1, y + 2);
                int color = WaypointColors.of(waypoint.kind(), waypoint.group());
                graphics.fill(x + 12, y + 13, x + 19, y + 20, WaypointColors.withAlpha(WaypointColors.outlineFor(color), 0xFF));
                graphics.fill(x + 13, y + 14, x + 18, y + 19, WaypointColors.withAlpha(color, 0xFF));
                String name = waypoint.explored() ? WaypointRenderer.EXPLORED_MARK + " " + row.name() : row.name();
                graphics.text(font, name, x + TEXT_X, y + 1, waypoint.explored() ? 0xFFA0A0A0 : 0xFFFFFFFF, true);
                Component detail = Component.literal(row.groupName() + "  ").withStyle(ChatFormatting.GRAY)
                        .append(Component.translatable("waypointsapi.dimension.short." + waypoint.dimension().identifier().getPath()).withStyle(ChatFormatting.DARK_GRAY));
                graphics.text(font, detail, x + TEXT_X, y + 12, 0xFFFFFFFF, false);
                String where = waypoint.pos().getX() + ", " + waypoint.pos().getZ() + (Double.isInfinite(row.distance()) ? "" : "  " + Math.round(row.distance()) + "m");
                int right = getContentRight();
                graphics.text(font, where, right - font.width(where) - 2, y + 1, 0xFFE0E0E0, true);
                // Whether it is in the world: pinned, set never to show, or shown now.
                String tag = waypoint.show() == Waypoint.Show.PINNED ? "pinned" : waypoint.show() == Waypoint.Show.HIDDEN ? "hidden" : shown.contains(row.key()) ? "shown" : null;
                if (tag != null) {
                    Component label = Component.translatable("waypointsapi.list.tag." + tag);
                    int tagColor = switch (tag) {
                        case "pinned" -> 0xFFFFC040;
                        case "hidden" -> 0xFF909090;
                        default -> 0xFF80C8FF;
                    };
                    graphics.text(font, label, right - font.width(label) - 2, y + 12, tagColor, false);
                }
            }

            @Override
            public Component getNarration() {
                return Component.literal(row.name() + ", " + row.groupName());
            }
        }
    }
}
