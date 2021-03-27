package org.eln2;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.*;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.eln2.config.CleintConfigs;
import org.eln2.utils.OreGen;
import java.util.stream.Collectors;

/**
 * Java wrapper for Eln2.
 *
 * There are no decent Kotlin language adapters, so we need at least this one Java class.
 */
@Mod(Eln2.MODID)
public class Eln2Wrapper {
    public Eln2Wrapper() {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the enqueueIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::done);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        Eln2.INSTANCE.initialize();
    }

    private void setup(final FMLCommonSetupEvent event) {
        Eln2.INSTANCE.setup(event);
    }

    private void done(final FMLLoadCompleteEvent event) {
        Eln2.INSTANCE.loadComplete(event);
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        Eln2.LOGGER.info("Hello from the client code :)");
    }

    private void enqueueIMC(final InterModEnqueueEvent unused_event) {
        // some example code to dispatch IMC to another mod
        InterModComms.sendTo(Eln2.MODID, "helloworld", () -> {
            Eln2.LOGGER.info("Hello world from the MDK");
            return "Hello world";
        });
    }

    private void processIMC(final InterModProcessEvent event) {
        // some example code to receive and process InterModComms from other mods
        Eln2.LOGGER.info("Got IMC {}", event.getIMCStream().
            map(m -> m.getMessageSupplier().get()).
            collect(Collectors.toList()));
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        Eln2.LOGGER.info("Hello from the server code :)");
    }

    /**
     * This registers the ore generation with Forge.
     *
     * Oddly enough, you cannot rename this function from biomeMod. Fancy.
     *
     * @param event the event Forge gives us
     */
    @SubscribeEvent
    public void biomeMod(BiomeLoadingEvent event){
        OreGen.INSTANCE.registerOreGeneration(event);
    }
}
