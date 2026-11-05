package waypointsapi.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import waypointsapi.display.CameraPosition;
import waypointsapi.display.WaypointRenderer;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    /**
     * Right before the level renderer turns the frame's gizmos into geometry: add the beams and labels for this
     * frame's camera, into the renderer's own per-frame collection (opened here too, since not every frame is drawn
     * from inside the game loop's, a screenshot for one).
     */
    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void waypointsapi$frameGizmos(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        try (Gizmos.TemporaryCollection ignored = ((LevelRenderer) (Object) this).collectPerFrameRenderThreadGizmos()) {
            WaypointRenderer.frame(mc, CameraPosition.of(mc), CameraPosition.forward(mc));
        }
    }
}
