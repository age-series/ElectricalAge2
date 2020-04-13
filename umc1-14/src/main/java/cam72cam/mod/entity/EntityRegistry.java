package cam72cam.mod.entity;

import cam72cam.mod.ModCore;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.event.CommonEvents;
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
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class EntityRegistry {
    private static final Map<Class<? extends Entity>, EntityType<?>> registered = new HashMap<>();

    private static String missingResources;

    private EntityRegistry() {

    }

    public static void register(ModCore.Mod mod, Supplier<Entity> ctr, EntitySettings settings, int distance) {
        Entity tmp = ctr.get();
        Class<? extends Entity> type = tmp.getClass();
        Identifier id = new Identifier(mod.modID(), type.getSimpleName());

        // TODO expose updateFreq and vecUpdates

        CommonEvents.Entity.REGISTER.subscribe(() -> {
            EntityType.Builder<net.minecraft.entity.Entity> builder = EntityType.Builder.create((et, world) -> new ModdedEntity(et, world, ctr, settings), EntityClassification.MISC)
                    .setShouldReceiveVelocityUpdates(false)
                    .setTrackingRange(distance)
                    .setUpdateInterval(20)
                    .setCustomClientFactory((se, world) -> new ModdedEntity(registered.get(type), world, ctr, settings));
            if (settings.immuneToFire) {
                builder = builder.immuneToFire();
            }
            EntityType<net.minecraft.entity.Entity> et = builder.build(id.toString());
            et.setRegistryName(id.internal);
            ForgeRegistries.ENTITIES.register(et);
            registered.put(type, et);
        });
    }

    public static Entity create(World world, Class<? extends Entity> cls) {
        return ((ModdedEntity)registered.get(cls).create(world.internal)).getSelf();
    }

    public static void registerEvents() {
        CommonEvents.Entity.REGISTER.subscribe(() -> ForgeRegistries.ENTITIES.register(SeatEntity.TYPE));
        CommonEvents.Entity.JOIN.subscribe((world, entity) -> {
            if (entity instanceof ModdedEntity) {
                if (World.get(world) != null) {
                    String msg = ((ModdedEntity) entity).getSelf().tryJoinWorld();
                    if (msg != null) {
                        //missingResources = msg;
                        //return false; GAH FORGE FMLPlayMessages:166!
                    }
                }
            }
            return true;
        });
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerClientEvents() {
        ClientEvents.TICK.subscribe(() -> {
            if (missingResources != null && !Minecraft.getInstance().isSingleplayer() && Minecraft.getInstance().getConnection() != null) {
                ModCore.error(missingResources);
                Minecraft.getInstance().getConnection().getNetworkManager().closeChannel(PlayerMessage.direct(missingResources).internal);
                Minecraft.getInstance().loadWorld(null);
                Minecraft.getInstance().displayGuiScreen(new DisconnectedScreen(new MultiplayerScreen(new MainMenuScreen()), "disconnect.lost", PlayerMessage.direct(missingResources).internal));
                missingResources = null;
            }
        });
    }
}
