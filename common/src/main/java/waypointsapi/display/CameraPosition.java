package waypointsapi.display;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** The camera of the frame being drawn, in one place because the accessor differs between Minecraft versions. */
public final class CameraPosition {
    private CameraPosition() {
    }

    public static Vec3 of(Minecraft mc) {
        return mc.gameRenderer.getMainCamera().position();
    }

    /** The camera's vertical field of view in degrees, as drawn this frame (sprinting and effects change it). */
    public static float fov(Minecraft mc) {
        return mc.gameRenderer.getMainCamera().getFov();
    }

    /** Where the camera looks, as a unit vector. */
    public static Vec3 forward(Minecraft mc) {
        var forward = mc.gameRenderer.getMainCamera().forwardVector();
        return new Vec3(forward.x(), forward.y(), forward.z());
    }
}
