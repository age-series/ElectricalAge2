package cam72cam.mod.input;

import cam72cam.mod.entity.Player;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.net.Packet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

public class Keyboard {
    private static Map<UUID, Vec3d> vecs = new HashMap<>();

    public static Vec3d getMovement(Player player) {
        return vecs.getOrDefault(player.getUUID(), Vec3d.ZERO);
    }

    @SideOnly(Side.CLIENT)
    public static void registerKey(String name, int keyCode, String category, Runnable handler) {
        KeyBinding key = new KeyBinding(name, keyCode, category);
        ClientRegistry.registerKeyBinding(key);
        ClientEvents.TICK.subscribe(() -> {
            if (key.isKeyDown()) {
                handler.run();
            }
        });
    }

    @SideOnly(Side.CLIENT)
    public static void registerClientEvents() {
        ClientEvents.TICK.subscribe(() -> {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            if (player == null) {
                return;
            }
            new MovementPacket(
                    player.getUniqueID(),
                    new Vec3d(player.moveStrafing, 0, player.moveForward).scale(player.isSprinting() ? 0.4 : 0.2)
            ).sendToServer();
        });
    }

    public static class MovementPacket extends Packet {
        public MovementPacket() {

        }

        public MovementPacket(UUID id, Vec3d move) {
            data.setUUID("id", id);
            data.setVec3d("move", move);
            vecs.put(data.getUUID("id"), data.getVec3d("move"));
        }

        @Override
        protected void handle() {
            vecs.put(data.getUUID("id"), data.getVec3d("move"));
        }
    }
}
