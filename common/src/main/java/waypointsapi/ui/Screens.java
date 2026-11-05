package waypointsapi.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opening screens, in one place because the call differs between Minecraft versions. */
public final class Screens {
    private Screens() {
    }

    public static void open(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    public static Screen current() {
        return Minecraft.getInstance().gui.screen();
    }
}
