package cam72cam.mod;

import cam72cam.mod.config.ConfigFile;
import cam72cam.mod.entity.ModdedEntity;
import cam72cam.mod.entity.sync.EntitySync;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.event.CommonEvents;
import cam72cam.mod.gui.GuiRegistry;
import cam72cam.mod.input.Keyboard;
import cam72cam.mod.input.Mouse;
import cam72cam.mod.net.Packet;
import cam72cam.mod.net.PacketDirection;
import cam72cam.mod.render.BlockRender;
import cam72cam.mod.text.Command;
import cam72cam.mod.util.ModCoreCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IFutureReloadListener;
import net.minecraft.resources.IResourceManager;
import net.minecraft.resources.SimpleReloadableResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@net.minecraftforge.fml.common.Mod(ModCore.MODID)
public class ModCore {
    public static final String MODID = "universalmodcore";
    public static final String NAME = "UniversalModCore";
    public static final String VERSION = "1.0.0";
    public static ModCore instance;
    public static boolean hasResources;
    static List<Supplier<Mod>> modCtrs = new ArrayList<>();
    private static boolean isInReload;

    private List<Mod> mods;
    private Logger logger;

    static {
        cam72cam.immersiverailroading.Mod.hackRegistration();
    }

    public static void register(Supplier<Mod> ctr) {
        modCtrs.add(ctr);
    }

    public ModCore() {
        System.out.println("Welcome to UniversalModCore!");
        instance = this;
        mods = modCtrs.stream().map(Supplier::get).collect(Collectors.toList());
        proxy.event(ModEvent.CONSTRUCT);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::preInit);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::init);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::postInit);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverStarting);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::serverStarted);

        MinecraftForge.EVENT_BUS.register(this);
    }

    public void preInit(FMLCommonSetupEvent event) {
        logger = LogManager.getLogger();
        proxy.event(ModEvent.INITIALIZE);
        hasResources = true;
    }

    public void init(InterModEnqueueEvent event) {
        proxy.event(ModEvent.SETUP);
    }

    public void postInit(FMLLoadCompleteEvent event) {
        proxy.event(ModEvent.FINALIZE);
    }

    public void serverStarting(FMLServerStartingEvent event) {
        Command.registration(event.getCommandDispatcher());
    }

    public void serverStarted(FMLServerStartedEvent event) {
        proxy.event(ModEvent.START);
    }

    public static abstract class Mod {
        public abstract String modID();

        public abstract void commonEvent(ModEvent event);
        public abstract void clientEvent(ModEvent event);
        public abstract void serverEvent(ModEvent event);

        public final Path getConfig(String fname) {
            return Paths.get(FMLPaths.CONFIGDIR.get().toString(), fname);
        }

        public static void debug(String msg, Object...params) {
            ModCore.debug(msg, params);
        }
        public static void info(String msg, Object...params) {
            ModCore.info(msg, params);
        }
        public static void warn(String msg, Object...params) {
            ModCore.warn(msg, params);
        }
        public static void error(String msg, Object...params) {
            ModCore.error(msg, params);
        }
        public static void catching(Throwable ex) {
            ModCore.catching(ex);
        }
    }

    private static Proxy proxy = DistExecutor.runForDist(() -> ClientProxy::new, () -> ServerProxy::new);
    public static class Proxy {
        public Proxy() {
        }

        public void event(ModEvent event) {
            instance.mods.forEach(m -> m.commonEvent(event));
        }
    }

    public static class ClientProxy extends Proxy {
        public void event(ModEvent event) {
            super.event(event);
            instance.mods.forEach(m -> m.clientEvent(event));
        }
    }

    public static class ServerProxy extends Proxy {
        public void event(ModEvent event) {
            super.event(event);
            instance.mods.forEach(m -> m.serverEvent(event));
        }
    }

    public static boolean isInReload() {
        return isInReload;
    }


    static {
        ModCore.register(Internal::new);
    }

    public static class Internal extends Mod {
        public int skipN = 0;

        @Override
        public String modID() {
            return "universalmodcoreinternal";
        }

        @Override
        public void commonEvent(ModEvent event) {
            switch (event) {
                case CONSTRUCT:
                    Packet.register(EntitySync.EntitySyncPacket::new, PacketDirection.ServerToClient);
                    Packet.register(Keyboard.MovementPacket::new, PacketDirection.ClientToServer);
                    Packet.register(ModdedEntity.PassengerPositionsPacket::new, PacketDirection.ServerToClient);
                    Packet.register(Mouse.MousePressPacket::new, PacketDirection.ClientToServer);
                    Command.register(new ModCoreCommand());
                    ConfigFile.sync(Config.class);
                    break;
                case SETUP:
                    //World.MAX_ENTITY_RADIUS = Math.max(World.MAX_ENTITY_RADIUS, 32);
                    break;
                case START:
                    break;
            }
        }

        public interface SynchronousResourceReloadListener extends IFutureReloadListener {
            default CompletableFuture<Void> reload(IFutureReloadListener.IStage stage, IResourceManager resourceManager, IProfiler preparationsProfiler, IProfiler reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
                return stage.markCompleteAwaitingOthers(Unit.INSTANCE).thenRunAsync(() -> {
                    this.apply(resourceManager);
                }, backgroundExecutor);
            }

            void apply(IResourceManager var1);
        }

        @Override
        public void clientEvent(ModEvent event) {
            switch (event) {
                case SETUP:
                    /*
                    ((SimpleReloadableResourceManager) Minecraft.getInstance().getResourceManager()).addReloadListener((SynchronousResourceReloadListener)resourceManager -> {
                        if (skipN > 0) {
                            skipN--;
                            return;
                        }
                        ModCore.instance.mods.forEach(mod -> mod.clientEvent(ModEvent.RELOAD));
                        ClientEvents.fireReload();
                    });
                    */
                    BlockRender.onPostColorSetup();
                    //ClientEvents.fireReload();
                    break;
            }

        }

        @Override
        public void serverEvent(ModEvent event) {
        }
    }

    static int i = 1;
    public static void testReload() {
        if (i % 4 == 0) { // 4 sheets, we fire on the last one
            ModCore.isInReload = true;
            proxy.event(ModEvent.RELOAD);
            ClientEvents.fireReload();
            ModCore.isInReload = false;
        }
        i++;
    }

    public static void debug(String msg, Object... params) {
        if (instance == null || instance.logger == null) {
            System.out.println("DEBUG: " + String.format(msg, params));
            return;
        }

        /*TODO if (ConfigDebug.debugLog) {
            instance.logger.info(String.format(msg, params));
        }*/
    }

    public static void info(String msg, Object... params) {
        if (instance == null || instance.logger == null) {
            System.out.println("INFO: " + String.format(msg, params));
            return;
        }

        instance.logger.info(String.format(msg, params));
    }

    public static void warn(String msg, Object... params) {
        if (instance == null || instance.logger == null) {
            System.out.println("WARN: " + String.format(msg, params));
            return;
        }

        instance.logger.warn(String.format(msg, params));
    }

    public static void error(String msg, Object... params) {
        if (instance == null || instance.logger == null) {
            System.out.println("ERROR: " + String.format(msg, params));
            return;
        }

        instance.logger.error(String.format(msg, params));
    }

    public static void catching(Throwable ex) {
        if (instance == null || instance.logger == null) {
            ex.printStackTrace();
            return;
        }

        instance.logger.catching(ex);
    }
}
