package cam72cam.mod.entity;

import cam72cam.mod.ModCore;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.world.World;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class EntityRegistry {
    private static final Map<Class<? extends Entity>, String> identifiers = new HashMap<>();
    private static final Map<String, Supplier<Entity>> constructors = new HashMap<>();
    private static final Map<String, EntitySettings> registered = new HashMap<>();
    private static final List<EntityType<?>> registrations = new ArrayList<>();
    private static final Map<Class<? extends Entity>, EntityType> types = new HashMap<>();
    private static String missingResources;

    private EntityRegistry() {

    }

    public static void register(ModCore.Mod mod, Supplier<Entity> ctr, EntitySettings settings, int distance) {
        Entity tmp = ctr.get();
        Class<? extends Entity> type = tmp.getClass();

        Identifier id = new Identifier(mod.modID(), type.getSimpleName());

        // This has back-compat for older entity names

        identifiers.put(type, id.toString());
        constructors.put(id.toString(), ctr);
        registered.put(id.toString(), settings);

        // TODO expose updateFreq and vecUpdates
        EntityType.Builder<ModdedEntity> partial = EntityType.Builder.create(ModdedEntity::new, EntityClassification.MISC).setShouldReceiveVelocityUpdates(false).setTrackingRange(distance).setUpdateInterval(20);
        if (settings.immuneToFire) {
            partial = partial.immuneToFire();
        }
        EntityType<ModdedEntity> et = partial.build(type.getSimpleName());
        registrations.add(et);
        types.put(type, et);
    }

    public static void registration() {
    }

    public static EntitySettings getSettings(String type) {
        return registered.get(type);
    }

    public static Supplier<Entity> getConstructor(String type) {
        return constructors.get(type);
    }

    protected static Entity create(String type, ModdedEntity base) {
        return getConstructor(type).get().setup(base);
    }

    public static Entity create(World world, Class<? extends Entity> cls) {
        //TODO null checks
        ModdedEntity ent = new ModdedEntity(types.get(cls), world.internal);
        String id = identifiers.get(cls);
        ent.init(id);
        return ent.getSelf();
    }

    @EventBusSubscriber(modid = ModCore.MODID)
    public static class EntityEvents {
        public static void onTileEntityRegistry(RegistryEvent.Register<EntityType<?>> event) {
            event.getRegistry().register(SeatEntity.TYPE);
            registrations.forEach(event.getRegistry()::register);
        }


        @SubscribeEvent
        public static void onEntityJoin(EntityJoinWorldEvent event) {
            if (World.get(event.getWorld()) == null) {
                return;
            }


            if (event.getEntity() instanceof ModdedEntity) {
                String msg = ((ModdedEntity) event.getEntity()).getSelf().tryJoinWorld();
                if (msg != null) {
                    event.setCanceled(true);
                    missingResources = msg;
                }
            }
        }
    }

    @EventBusSubscriber(value = Dist.CLIENT, modid = ModCore.MODID)
    public static class EntityClientEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (missingResources != null && !Minecraft.getInstance().isSingleplayer() && Minecraft.getInstance().getConnection() != null) {
                System.out.println(missingResources);
                Minecraft.getInstance().getConnection().getNetworkManager().closeChannel(PlayerMessage.direct(missingResources).internal);
                Minecraft.getInstance().loadWorld(null);
                Minecraft.getInstance().displayGuiScreen(new DisconnectedScreen(new MultiplayerScreen(new MainMenuScreen()), "disconnect.lost", PlayerMessage.direct(missingResources).internal));
                missingResources = null;
            }
        }
    }
}
