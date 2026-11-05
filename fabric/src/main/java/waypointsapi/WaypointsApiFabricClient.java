package waypointsapi;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import waypointsapi.api.WaypointsPlugin;
import waypointsapi.config.WaypointsApiConfig;
import waypointsapi.display.DirectionHud;
import waypointsapi.display.WaypointRenderer;
import waypointsapi.kind.Kinds;
import waypointsapi.ui.Screens;
import waypointsapi.ui.WaypointListScreen;
import waypointsapi.teleport.TeleportService;
import waypointsapi.waypoint.ExploreTracker;
import waypointsapi.waypoint.WaypointStore;
import waypointsapi.waypoint.WaypointSync;

/** The client side: settings, the other mods' kinds, the list, the display in the world and the map mods. */
public final class WaypointsApiFabricClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "main"));

    @Override
    public void onInitializeClient() {
        WaypointsApiCommon.initClient();
        loadPlugins();

        KeyMapping openList = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.waypointsapi.open_list", InputConstants.UNKNOWN.getValue(), CATEGORY));
        KeyMapping toggleDisplay = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.waypointsapi.toggle_display", InputConstants.UNKNOWN.getValue(), CATEGORY));
        HudElementRegistry.addLast(DirectionHud.ID, (graphics, delta) -> DirectionHud.extract(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            WaypointSync.tick(mc);
            ExploreTracker.tick(mc);
            TeleportService.tick(mc);
            while (toggleDisplay.consumeClick()) toggleDisplay(mc);
            while (openList.consumeClick()) {
                if (mc.player != null) Screens.open(new WaypointListScreen(null));
            }
            WaypointRenderer.tick(mc);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, mc) -> mc.execute(WaypointSync::onLeave));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> WaypointStore.saveIfDirty());

        // Xaero and VoxelMap have no entry point for other mods: started here when installed.
        if (FabricLoader.getInstance().isModLoaded("xaerominimap")) waypointsapi.xaero.XaeroMarkers.start();
        if (FabricLoader.getInstance().isModLoaded("voxelmap")) waypointsapi.voxelmap.VoxelMapWaypoints.start();
    }

    /** The other mods' {@link WaypointsPlugin}s, from the {@code waypointsapi} entrypoint. */
    private static void loadPlugins() {
        for (EntrypointContainer<WaypointsPlugin> container : FabricLoader.getInstance().getEntrypointContainers(Constants.MOD_ID, WaypointsPlugin.class)) {
            String modId = container.getProvider().getMetadata().getId();
            WaypointsPlugin plugin;
            try {
                plugin = container.getEntrypoint();
            } catch (RuntimeException | LinkageError e) {
                Constants.LOG.error("The waypoints plugin of {} could not be created", modId, e);
                continue;
            }
            Kinds.load(modId, plugin);
        }
    }

    private static void toggleDisplay(Minecraft mc) {
        WaypointsApiConfig config = WaypointsApiConfig.get();
        config.showWaypoints = !config.showWaypoints;
        WaypointsApiConfig.HANDLER.save();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.translatable(config.showWaypoints ? "waypointsapi.message.display_on" : "waypointsapi.message.display_off"));
        }
    }
}
