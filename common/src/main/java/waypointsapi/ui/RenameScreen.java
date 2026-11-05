package waypointsapi.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import waypointsapi.kind.Kinds;
import waypointsapi.waypoint.Waypoint;
import waypointsapi.waypoint.WaypointStore;

/** Gives a waypoint a name. Leaving the box empty goes back to the name its mod gives it. */
public final class RenameScreen extends Screen {
    private final Screen parent;
    private final Waypoint waypoint;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private EditBox name;

    public RenameScreen(Screen parent, Waypoint waypoint) {
        super(Component.translatable("waypointsapi.rename.title"));
        this.parent = parent;
        this.waypoint = waypoint;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        LinearLayout contents = layout.addToContents(LinearLayout.vertical().spacing(6));
        contents.defaultCellSetting().alignHorizontallyCenter();
        Component defaultName = Kinds.defaultName(waypoint);
        contents.addChild(new StringWidget(defaultName, font));
        name = contents.addChild(new EditBox(font, 0, 0, 200, 20, Component.translatable("waypointsapi.rename.name")));
        name.setMaxLength(64);
        name.setValue(waypoint.customName().orElse(""));
        name.setHint(defaultName.copy().withStyle(EditBox.SEARCH_HINT_STYLE));
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> save()).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).build());
        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        setInitialFocus(name);
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    /** Sets the text and saves, as pressing Done does (for tests). */
    public void saveAs(String text) {
        name.setValue(text);
        save();
    }

    private void save() {
        WaypointStore.rename(waypoint, name.getValue());
        WaypointStore.saveIfDirty();
        onClose();
    }

    @Override
    public void onClose() {
        Screens.open(parent);
        if (parent instanceof WaypointListScreen list) list.refresh();
    }
}
