package cam72cam.mod.event;

import cam72cam.mod.ModCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;

import java.util.function.Consumer;

public class CommonEvents {
    private static void registerEvents() {
        cam72cam.mod.world.World.registerEvents();
        cam72cam.mod.entity.EntityRegistry.registerEvents();
        cam72cam.mod.world.ChunkManager.registerEvents();
    }

    public static final class World {
        public static final Event<Consumer<net.minecraft.world.World>> LOAD = new Event<>();
        public static final Event<Consumer<net.minecraft.world.World>> UNLOAD = new Event<>();
        public static final Event<Consumer<net.minecraft.world.World>> TICK = new Event<>();
    }

    public static final class Block {
        public static final Event<Runnable> REGISTER = new Event<>();
        public static final Event<EventBus.BlockBrokenEvent> BROKEN = new Event<>();
    }

    public static final class Item {
        public static final Event<Runnable> REGISTER = new Event<>();
    }

    public static final class Recipe {
        public static final Event<Runnable> REGISTER = new Event<>();
    }

    public static final class Entity {
        public static final Event<Runnable> REGISTER = new Event<>();
        public static final Event<EventBus.EntityJoinEvent> JOIN = new Event<>();
    }

    @Mod.EventBusSubscriber(modid = ModCore.MODID)
    public static final class EventBus {
        static {
            registerEvents();
        }

        // World
        @SubscribeEvent
        public static void onWorldLoad(WorldEvent.Load event) {
            World.LOAD.callbacks.forEach(x -> x.accept(event.getWorld()));
        }

        @SubscribeEvent
        public static void onWorldUnload(WorldEvent.Unload event) {
            World.UNLOAD.callbacks.forEach(x -> x.accept(event.getWorld()));
        }

        @SubscribeEvent
        public static void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                World.TICK.callbacks.forEach(x -> x.accept(event.world));
            }
        }

        @SubscribeEvent
        public static void registerBlocks(RegistryEvent.Register<net.minecraft.block.Block> event) {
            Block.REGISTER.callbacks.forEach(Runnable::run);
        }

        @FunctionalInterface
        public interface BlockBrokenEvent {
            boolean onBroken(net.minecraft.world.World world, BlockPos pos, EntityPlayer player);
        }
        @SubscribeEvent
        public static void onBlockBreakEvent(BlockEvent.BreakEvent event) {
            for (BlockBrokenEvent callback : Block.BROKEN.callbacks) {
                if (!callback.onBroken(event.getWorld(), event.getPos(), event.getPlayer())) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        @SubscribeEvent
        public static void registerItems(RegistryEvent.Register<net.minecraft.item.Item> event) {
            Item.REGISTER.callbacks.forEach(Runnable::run);
        }

        @SubscribeEvent
        public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
            Recipe.REGISTER.callbacks.forEach(Runnable::run);
        }

        @SubscribeEvent
        public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
            Entity.REGISTER.callbacks.forEach(Runnable::run);
        }

        @FunctionalInterface
        public interface EntityJoinEvent {
            boolean onJoin(net.minecraft.world.World world, net.minecraft.entity.Entity entity);
        }
        @SubscribeEvent
        public static void onEntityJoin(EntityJoinWorldEvent event) {
            for (EntityJoinEvent callback : Entity.JOIN.callbacks) {
                if (!callback.onJoin(event.getWorld(), event.getEntity())) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }
}
