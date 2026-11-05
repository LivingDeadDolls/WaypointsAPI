package waypointsapi.ui;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import waypointsapi.api.KindSettings;
import waypointsapi.display.WaypointColors;
import waypointsapi.kind.Kinds;

/**
 * Shows or hides each group of one kind, in the kind's settings (its mod's), saved once when the screen closes. Click a
 * row to switch it. Also opens the mod's settings screen, when it has one.
 */
public final class GroupFilterScreen extends Screen {
    private static final int ROW_HEIGHT = 20;

    private final Screen parent;
    private final Kinds.Kind kind;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private GroupList list;
    private boolean changed;

    public GroupFilterScreen(Screen parent, Kinds.Kind kind) {
        super(Component.translatable("waypointsapi.groups.title", kind.name()));
        this.parent = parent;
        this.kind = kind;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        list = layout.addToContents(new GroupList(minecraft));
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.translatable("waypointsapi.groups.show_all"), button -> {
            for (Identifier group : kind.groups()) setShown(group, true);
        }).build());
        if (kind.setting(settings -> settings.settingsScreen(this), Optional.<Screen>empty()).isPresent()) {
            footer.addChild(Button.builder(Component.translatable("waypointsapi.groups.settings"), button -> {
                saveIfChanged();
                kind.setting(settings -> settings.settingsScreen(this), Optional.<Screen>empty()).ifPresent(Screens::open);
            }).build());
        }
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if (list != null) list.updateSize(width, layout);
    }

    @Override
    public void onClose() {
        saveIfChanged();
        Screens.open(parent);
        if (parent instanceof WaypointListScreen screen) screen.refresh();
    }

    private void setShown(Identifier group, boolean shown) {
        if (kind.changeSetting(settings -> settings.setGroupShown(group, shown))) changed = true;
    }

    private void saveIfChanged() {
        if (!changed) return;
        changed = false;
        kind.changeSetting(KindSettings::save);
    }

    final class GroupList extends ObjectSelectionList<GroupList.GroupEntry> {
        GroupList(Minecraft minecraft) {
            super(minecraft, GroupFilterScreen.this.width, GroupFilterScreen.this.height - 66, 33, ROW_HEIGHT);
            for (Identifier group : kind.groups()) addEntry(new GroupEntry(group));
        }

        final class GroupEntry extends ObjectSelectionList.Entry<GroupEntry> {
            private final Identifier group;

            GroupEntry(Identifier group) {
                this.group = group;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                boolean shown = Kinds.groupShown(kind.id(), group);
                int x = getContentX();
                int y = getContentY();
                graphics.fill(x + 2, y + 4, x + 10, y + 12, WaypointColors.withAlpha(WaypointColors.of(kind.id(), group), shown ? 0xFF : 0x50));
                graphics.text(font, Kinds.groupName(kind.id(), group), x + 14, y + 4, shown ? 0xFFFFFFFF : 0xFF808080, true);
                Component state = Component.translatable(shown ? "waypointsapi.groups.shown" : "waypointsapi.groups.hidden");
                graphics.text(font, state, getContentRight() - font.width(state) - 2, y + 4, shown ? 0xFF80FF80 : 0xFF808080, false);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                setShown(group, !Kinds.groupShown(kind.id(), group));
                return super.mouseClicked(event, doubleClick);
            }

            @Override
            public Component getNarration() {
                return Kinds.groupName(kind.id(), group);
            }
        }
    }
}
