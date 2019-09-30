package cam72cam.mod.event;

import cam72cam.mod.ModCore;
import cam72cam.mod.entity.EntityRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

public class ClientEvents {
    private static void registerClientEvents() {
        EntityRegistry.registerClientEvents();
    }

    public static Event<Runnable> TICK = new Event<>();

    @Mod.EventBusSubscriber(value = Side.CLIENT, modid = ModCore.MODID)
    public static class ClientEventBus {
        static {
            registerClientEvents();
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            TICK.callbacks.forEach(Runnable::run);
        }
    }
}
