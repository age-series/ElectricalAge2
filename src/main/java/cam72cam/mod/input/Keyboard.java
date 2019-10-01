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
import java.util.function.Consumer;

public class Keyboard {
    private static Map<UUID, Vec3d> vecs = new HashMap<>();
    @SideOnly(Side.CLIENT)
    private static List<KeyBinding> keys = new ArrayList<>();

    /* Player Movement */
    private static Map<String, Consumer<Player>> keyFuncs = new HashMap<>();

    public static Vec3d getMovement(Player player) {
        return vecs.getOrDefault(player.getUUID(), Vec3d.ZERO);
    }

    public static void registerKey(String name, int keyCode, String category, Consumer<Player> handler) {
        keyFuncs.put(name, handler);
        KeyBinding key = new KeyBinding(name, keyCode, category);
        ClientRegistry.registerKeyBinding(key);
        keys.add(key);
    }

    public static void registerClientEvents() {
        ClientEvents.TICK.register(() -> {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            if (player == null) {
                return;
            }
            new MovementPacket(
                    player.getUniqueID(),
                    new Vec3d(player.moveStrafing, 0, player.moveForward).scale(player.isSprinting() ? 0.4 : 0.2)
            ).sendToServer();

            for (KeyBinding key : keys) {
                if (key.isKeyDown()) {
                    new KeyPacket(key.getKeyDescription()).sendToServer();
                }
            }
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

    public static class KeyPacket extends Packet {
        public KeyPacket() {

        }

        public KeyPacket(String name) {
            data.setString("name", name);
        }

        @Override
        protected void handle() {
            keyFuncs.get(data.getString("name")).accept(getPlayer());
        }
    }
}
