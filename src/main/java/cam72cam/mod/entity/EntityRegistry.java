package cam72cam.mod.entity;

import cam72cam.mod.ModCore;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.event.CommonEvents;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.world.World;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class EntityRegistry {
    private static final Map<Class<? extends Entity>, String> identifiers = new HashMap<>();
    private static final Map<String, Supplier<Entity>> constructors = new HashMap<>();
    private static final Map<String, EntitySettings> registered = new HashMap<>();
    private static String missingResources;

    private EntityRegistry() {

    }

    public static void register(ModCore.Mod mod, Supplier<Entity> ctr, EntitySettings settings, int distance) {
        Entity tmp = ctr.get();
        Class<? extends Entity> type = tmp.getClass();

        CommonEvents.Entity.REGISTER.subscribe(() -> {
            Identifier id = new Identifier(mod.modID(), type.getSimpleName());

            // This has back-compat for older entity names
            // TODO expose updateFreq and vecUpdates
            net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(id.internal, ModdedEntity.class, type.getSimpleName(), constructors.size(), ModCore.instance, distance, 20, false);

            identifiers.put(type, id.toString());
            constructors.put(id.toString(), ctr);
            registered.put(id.toString(), settings);
        });
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
        ModdedEntity ent = new ModdedEntity(world.internal);
        String id = identifiers.get(cls);
        ent.init(id);
        return ent.getSelf();
    }


    public static void registerEvents() {
        CommonEvents.Entity.REGISTER.subscribe(() -> {
            net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(SeatEntity.ID, SeatEntity.class, SeatEntity.class.getSimpleName(), constructors.size()+1, ModCore.instance, 512, 20, false);
        });

        CommonEvents.Entity.JOIN.subscribe((world, entity) -> {
            if (entity instanceof ModdedEntity) {
                if (World.get(world) != null) {
                    String msg = ((ModdedEntity) entity).getSelf().tryJoinWorld();
                    if (msg != null) {
                        missingResources = msg;
                        return false;
                    }
                }
            }
            return true;
        });
    }

    @SideOnly(Side.CLIENT)
    public static void registerClientEvents() {
        ClientEvents.TICK.subscribe(() -> {
            if (missingResources != null && !Minecraft.getMinecraft().isSingleplayer() && Minecraft.getMinecraft().getConnection() != null) {
                System.out.println(missingResources);
                Minecraft.getMinecraft().getConnection().getNetworkManager().closeChannel(PlayerMessage.direct(missingResources).internal);
                Minecraft.getMinecraft().loadWorld(null);
                Minecraft.getMinecraft().displayGuiScreen(new GuiDisconnected(new GuiMultiplayer(new GuiMainMenu()), "disconnect.lost", PlayerMessage.direct(missingResources).internal));
                missingResources = null;
            }
        });
    }
}
