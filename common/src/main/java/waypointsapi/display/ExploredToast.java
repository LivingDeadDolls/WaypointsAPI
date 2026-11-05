package waypointsapi.display;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import waypointsapi.kind.Kinds;
import waypointsapi.waypoint.Waypoint;

/**
 * Says a waypoint has become explored, in the top right corner like an advancement: the kind's icon, a title in the
 * group's color, and the waypoint's name. It chimes as it comes in, a sound of its own (advancements have none, or the
 * challenge's fanfare).
 */
public final class ExploredToast implements Toast {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
    /** How long it stays (milliseconds), like an advancement's. */
    private static final long DISPLAY_TIME = 5000;
    private static final int TEXT_X = 30;
    private static final int TEXT_WIDTH = 125;

    private final Waypoint waypoint;
    private final Component name;
    private final ItemStack icon;
    private final int color;
    private Visibility wantedVisibility = Visibility.HIDE;

    public ExploredToast(Waypoint waypoint) {
        this.waypoint = waypoint;
        this.name = Component.literal(Kinds.name(waypoint));
        this.icon = Kinds.get(waypoint.kind()).map(kind -> new ItemStack(kind.definition().icon())).orElse(ItemStack.EMPTY);
        this.color = WaypointColors.of(waypoint.kind(), waypoint.group());
    }

    public Waypoint waypoint() {
        return waypoint;
    }

    @Override
    public Visibility getWantedVisibility() {
        return wantedVisibility;
    }

    @Override
    public void update(ToastManager manager, long visibleTime) {
        wantedVisibility = visibleTime >= DISPLAY_TIME * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public SoundEvent getSoundEvent() {
        return SoundEvents.NOTE_BLOCK_CHIME.value();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long visibleTime) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
        graphics.text(font, Component.translatable("waypointsapi.toast.explored"), TEXT_X, 7, color, false);
        List<FormattedCharSequence> nameLines = font.split(name, TEXT_WIDTH);
        if (!nameLines.isEmpty()) graphics.text(font, nameLines.getFirst(), TEXT_X, 18, 0xFFFFFFFF, false);
        if (!icon.isEmpty()) graphics.fakeItem(icon, 8, 8);
    }
}
