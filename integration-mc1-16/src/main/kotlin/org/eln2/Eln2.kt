package org.eln2

import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraftforge.fml.common.registry.GameRegistry
import net.minecraftforge.fml.common.thread.SidedThreadGroups
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent
import net.minecraftforge.fml.event.server.FMLServerStartingEvent
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import org.eln2.blocks.createOreBlocks
import org.eln2.blocks.oreBlockMap
import org.eln2.config.ConfigHandler
import org.eln2.items.createOreChunks
import org.eln2.items.oreChunkMap
import org.eln2.registry.MiscBlocks
import org.eln2.registry.MiscItems
import org.eln2.utils.AnalyticsHandler
import org.eln2.utils.OreGen

/**
 * The main entry point and registry holder for Electrical Age.
 *
 * The @JvmField annotation is needed on some calls, because [Eln2Wrapper] is a Java class that can't see Kotlin stuff.
 */
object Eln2 {
    @JvmField
    val LOGGER: Logger = LogManager.getLogger()
    const val MODID = "eln2"

    val logicalServer = Thread.currentThread().threadGroup == SidedThreadGroups.SERVER

    /**
     * Initialization.
     *
     * This is run at class construction time, and should do as little as possible (register blocks/item)
     */
    fun initialize() {
        Configurator.setLevel(LOGGER.name, Level.ALL)
        val modEventBus = FMLJavaModLoadingContext.get().modEventBus
        val itemRegistry = GameRegistry.findRegistry(Item::class.java)
        val blockRegistry = GameRegistry.findRegistry(Block::class.java)

        // Register Items
        createOreChunks()
        oreChunkMap.forEach {
            itemRegistry.register(it.value)
            modEventBus.register(it.value)
        }
        MiscItems.values().forEach {
            itemRegistry.register(it.item)
            modEventBus.register(it.item)
        }

        // Register Blocks
        createOreBlocks()
        oreBlockMap.forEach {
            blockRegistry.register(it.value)
            modEventBus.register(it.value)
        }
        MiscBlocks.values().forEach {
            blockRegistry.register(it.block)
            modEventBus.register(it.block)
        }
    }

    /**
     * Preinit handler.
     *
     * This is run in a threaded context, so we cannot communicate with other mods without using IMC.
     */
    fun setup(event: FMLCommonSetupEvent) {
        ConfigHandler()
        OreGen.setupOreGeneration()
    }

    fun loadComplete(event: FMLLoadCompleteEvent) {
        AnalyticsHandler.sendAnalyticsData()
    }

    fun serverPreStart(event: FMLServerAboutToStartEvent) {
        //val saveFolder = event.server.func_240776_a_(FolderName.DOT).parent.toAbsolutePath()
        //val eln2DataPath = saveFolder.resolve("eln2.dat")
        //org.eln2.node.NodeManager.path = eln2DataPath
    }

    fun serverStart(event: FMLServerStartingEvent) {

    }

    fun serverStop(event: FMLServerStoppingEvent) {

    }
}
