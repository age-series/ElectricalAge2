package org.eln2.mc

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent
import net.minecraftforge.fml.InterModComms
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent
import net.minecraftforge.fml.InterModComms.IMCMessage
import java.util.stream.Collectors
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import org.apache.logging.log4j.LogManager
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.forge.addGenericListener

@Mod(Eln2.MODID)
object Eln2 {
    const val MODID: String = "eln2"
    val LOGGER = LogManager.getLogger()

    private fun setup(event: FMLCommonSetupEvent) {
        LOGGER.info("HELLO FROM PREINIT")
        LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.registryName)
    }

    private fun enqueueIMC(event: InterModEnqueueEvent) {
        InterModComms.sendTo("eln2", "helloworld") {
            LOGGER.info("Hello world from the MDK")
            "Hello world"
        }
    }

    private fun processIMC(event: InterModProcessEvent) {
        LOGGER.info(
            "Got IMC {}",
            event.imcStream.map { m: IMCMessage -> m.messageSupplier().get() }.collect(Collectors.toList())
        )
    }

    private fun onServerStarting(event: ServerStartingEvent) {
        LOGGER.info("HELLO from server starting")
    }

    private fun onBlocksRegistry(event: RegistryEvent.Register<Block>) {
        LOGGER.info("Hello from block registry")
    }

    init {
        MOD_BUS.addListener { event: FMLCommonSetupEvent -> setup(event) }
        MOD_BUS.addListener { event: InterModEnqueueEvent -> enqueueIMC(event) }
        MOD_BUS.addListener { event: InterModProcessEvent -> processIMC(event) }
        MOD_BUS.addGenericListener({ event: RegistryEvent.Register<Block> -> onBlocksRegistry(event)})
        MOD_BUS.register(this)

        FORGE_BUS.addListener { event: ServerStartingEvent -> onServerStarting(event) }
        FORGE_BUS.register(this)
    }
}
