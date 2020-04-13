package net.electricalage.eln2

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.InterModComms
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent
import net.minecraftforge.fml.event.server.FMLServerStartingEvent
import org.apache.logging.log4j.LogManager

@Mod("eln2")
class Eln2 {
	private fun setup(event: FMLCommonSetupEvent) {
		// some preinit code
		LOGGER.info("HELLO FROM PREINIT")
		LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.registryName)
	}

	private fun doClientStuff(event: FMLClientSetupEvent) {
		// do something that can only be done on the client
		LOGGER.info("Got game settings {}", event.minecraftSupplier.get().gameSettings)
	}

	private fun enqueueIMC(event: InterModEnqueueEvent) {
		// some example code to dispatch IMC to another mod
		InterModComms.sendTo("eln2", "helloworld") {
			LOGGER.info("Hello world from the MDK")
			"Hello world"
		}
	}

	private fun processIMC(event: InterModProcessEvent) {
		// some example code to receive and process InterModComms from other mods
		val message = event.imcStream.map {
			it.getMessageSupplier<String>().get()
		}

		LOGGER.info("Got IMC {}", message)
	}

	// You can use SubscribeEvent and let the Event Bus discover methods to call
	@SubscribeEvent
	fun onServerStarting(event: FMLServerStartingEvent?) {
		// do something when the server starts
		LOGGER.info("HELLO from server starting")
	}

	// You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
	// Event bus for receiving Registry Events)
	object RegistryEvents {
		@SubscribeEvent
		fun onBlocksRegistry(blockRegistryEvent: RegistryEvent.Register<Block?>?) {
			// register a new block here
			LOGGER.info("HELLO from Register Block")
		}
	}

	companion object {
		// Directly reference a log4j logger.
		private val LOGGER = LogManager.getLogger()!!
	}

	init {
		// Register the setup method for modloading
		thedarkcolour.kotlinforforge.forge.MOD_CONTEXT
			.getEventBus().apply {
				addListener { event: FMLCommonSetupEvent -> setup(event) }
				addListener { event: InterModEnqueueEvent -> enqueueIMC(event) }
				addListener { event: InterModProcessEvent -> processIMC(event) }
				addListener { event: FMLClientSetupEvent -> doClientStuff(event) }
			}

		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this)
	}
}
