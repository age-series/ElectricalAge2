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

/**
 * This is the root class for Electrical Age.
 *
 * It mostly contains the Forge setup boilerplate.
 */
@Mod("eln2")
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object Eln2 {
	private val LOGGER = LogManager.getLogger()!!

	@SubscribeEvent
	fun setup(event: FMLCommonSetupEvent) {
		// some preinit code
		LOGGER.info("HELLO FROM PREINIT")
		LOGGER.info("HELLO from ${cam72cam.mod.ModCore.MODID}")
		LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.registryName)
	}

	private fun doClientStuff(event: FMLClientSetupEvent) {
		// do something that can only be done on the client
		LOGGER.info("HELLO, Got game settings {}", event.minecraftSupplier.get().gameSettings)
	}

	private fun enqueueIMC(event: InterModEnqueueEvent) {
		// some example code to dispatch IMC to another mod
		InterModComms.sendTo("eln2", "helloworld") {
			LOGGER.info("HELLO world from the MDK")
			"HELLO world"
		}
	}

	private fun processIMC(event: InterModProcessEvent) {
		// some example code to receive and process InterModComms from other mods
		val message = event.imcStream.map {
			it.getMessageSupplier<String>().get()
		}

		LOGGER.info("HELLO, Got IMC {}", message)
	}

	/**
	 * Server startup event handler.
	 *
	 * This is called after most configuration is already done, right as the server begins ticking.
	 */
	@SubscribeEvent
	fun onServerStarting(event: FMLServerStartingEvent?) {
		// do something when the server starts
		LOGGER.info("HELLO from server starting")
	}

	/**
	 * Registry event handler.
	 *
	 * You can use EventBusSubscriber to automatically subscribe events on objects other than the main mod object.
	 * (This is subscribing to the MOD Event bus for receiving Registry Events)
	 */
	@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
	object RegistryEvents {
		@SubscribeEvent
		fun onBlocksRegistry(blockRegistryEvent: RegistryEvent.Register<Block?>?) {
			// register a new block here
			LOGGER.info("HELLO from Register Block")
		}
	}

	init {
		// Explicitly register certain methods for certain events.
		// @SubscribeEvent should also work.
		thedarkcolour.kotlinforforge.forge.MOD_CONTEXT
			.getEventBus().apply {
				addListener { event: InterModEnqueueEvent -> enqueueIMC(event) }
				addListener { event: InterModProcessEvent -> processIMC(event) }
				addListener { event: FMLClientSetupEvent -> doClientStuff(event) }
			}

		// Register ourselves for Forge events as well.
		MinecraftForge.EVENT_BUS.register(this)
	}
}
