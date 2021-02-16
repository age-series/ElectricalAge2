package org.eln2;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.eln2.utils.OreGen;

import java.util.stream.Collectors;

/**
 * Java wrapper for Eln2.
 * <p>
 * There are no decent Kotlin language adapters, so we need at least this one Java class.
 */
@Mod("eln2")
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

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        Eln2.INSTANCE.initialize();
    }

    private void setup(final FMLCommonSetupEvent event) {
        Eln2.INSTANCE.setup(event);
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        // do something that can only be done on the client
        Eln2.LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().gameSettings);
    }

    private void enqueueIMC(final InterModEnqueueEvent unused_event) {
        // some example code to dispatch IMC to another mod
        InterModComms.sendTo("eln2", "helloworld", () -> {
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

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        // do something when the server starts
        Eln2.LOGGER.info("HELLO from server starting");
    }
    @SubscribeEvent
    public void biomeMod(BiomeLoadingEvent event){
        OreGen.INSTANCE.biomeModification(event);
    }
}
