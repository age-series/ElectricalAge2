package cam72cam.mod.net;

import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Player;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.util.TagCompound;
import cam72cam.mod.world.World;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class Packet {
    private static final String VERSION = "1.0";
    private static final SimpleChannel net = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("universalmodcore", "cam72cam.mod"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals
    );
    private static Map<String, Supplier<Packet>> types = new HashMap<>();

    static {
        net.registerMessage(0, Message.class, Message::toBytes, Message::new, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                msg.packet.ctx = ctx.get();
                msg.packet.handle();
            });
            ctx.get().setPacketHandled(true);
        });
    }

    protected TagCompound data = new TagCompound();
    NetworkEvent.Context ctx;

    public static void register(Supplier<Packet> sup, PacketDirection dir) {
        types.put(sup.get().getClass().toString(), sup);
    }

    protected abstract void handle();

    protected final World getWorld() {
        return getPlayer().getWorld();
    }

    protected final Player getPlayer() {
        return ctx.getDirection() == NetworkDirection.PLAY_TO_CLIENT ? MinecraftClient.getPlayer() : new Player(ctx.getSender());
    }

    public void sendToAllAround(World world, Vec3d pos, double distance) {
        net.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, distance, world.internal.getDimension().getType())), new Message(this));
    }

    public void sendToServer() {
        net.sendToServer(new Message(this));
    }

    public void sendToAll() {
        net.send(PacketDistributor.ALL.noArg(), new Message(this));
    }

    public static class Message {
        Packet packet;

        public Message(Packet pkt) {
            this.packet = pkt;
        }

        public Message(PacketBuffer buff) {
            fromBytes(buff);
        }

        public void fromBytes(PacketBuffer buf) {
            TagCompound data = new TagCompound(buf.readCompoundTag());
            String cls = data.getString("cam72cam.mod.pktid");
            packet = types.get(cls).get();
            packet.data = data;
        }

        public void toBytes(PacketBuffer buf) {
            packet.data.setString("cam72cam.mod.pktid", packet.getClass().toString());
            buf.writeCompoundTag(packet.data.internal);
        }
    }
}
