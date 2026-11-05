package waypointsapi.teleport;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;
import waypointsapi.waypoint.Waypoint;

/**
 * Teleports the player to a waypoint with vanilla commands, which the server allows only to players with the
 * permission (offered only to them). With a ceiling (the Nether), {@code /spreadplayers} puts the player on ground below
 * the waypoint's height. Without one, the player is first taken to the waypoint itself, so that the server loads its
 * chunk; once the client has the chunk too, onto the top of the column there as the server knows it (water included).
 */
public final class TeleportService {
    /** How far from a place with a ceiling the ground may be: room enough to find some that is not lava. */
    static final int CEILING_RANGE = 16;
    /** How long to wait for the chunk before giving up on putting the player onto the ground. */
    private static final int WAIT_TICKS = 200;

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, int ticksLeft) {
    }

    private static Pending pending;

    private TeleportService() {
    }

    /** Whether teleporting can be offered now (the button is shown enabled). */
    public static boolean available(Minecraft mc) {
        return mc.player != null && mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** Sends the command. False (and nothing sent) without the permission. */
    public static boolean teleport(Minecraft mc, Waypoint waypoint) {
        if (!available(mc)) return false;
        BlockPos pos = waypoint.pos();
        String in = "execute in " + waypoint.dimension().identifier() + " run ";
        if (waypoint.dimension() == Level.NETHER) {
            pending = null;
            mc.player.connection.sendCommand(in + "spreadplayers " + pos.getX() + " " + pos.getZ() + " 0 " + CEILING_RANGE
                    + " under " + (pos.getY() + 1) + " false @s");
        } else {
            pending = new Pending(waypoint.dimension(), pos, WAIT_TICKS);
            mc.player.connection.sendCommand(in + "tp @s " + (pos.getX() + 0.5) + " " + pos.getY() + " " + (pos.getZ() + 0.5));
        }
        return true;
    }

    /** Every client tick: once the player is at a waypoint whose chunk the client has, onto the ground there. */
    public static void tick(Minecraft mc) {
        Pending now = pending;
        if (now == null) return;
        if (mc.player == null || mc.level == null || now.ticksLeft() <= 0) {
            pending = null;
            return;
        }
        BlockPos pos = now.pos();
        boolean arrived = mc.level.dimension() == now.dimension() && Math.abs(mc.player.getX() - (pos.getX() + 0.5)) < 1
                && Math.abs(mc.player.getZ() - (pos.getZ() + 0.5)) < 1 && mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (!arrived) {
            pending = new Pending(now.dimension(), pos, now.ticksLeft() - 1);
            return;
        }
        pending = null;
        mc.player.connection.sendCommand("execute positioned " + (pos.getX() + 0.5) + " 0 " + (pos.getZ() + 0.5)
                + " positioned over motion_blocking run tp @s ~ ~ ~");
    }
}
