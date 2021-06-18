package org.eln2

import net.minecraftforge.fml.common.thread.SidedThreadGroups
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent
import net.minecraftforge.fml.event.server.FMLServerStartingEvent
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.ForgeRegistries
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import org.eln2.config.ConfigHandler
import org.eln2.registry.ChunkItems
import org.eln2.registry.MiscBlocks
import org.eln2.registry.MiscItems
import org.eln2.registry.OreBlocks
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
     * This is run at class construction time, and should do as little as possible (register blocks/items)
     */
    fun initialize() {
        Configurator.setLevel(LOGGER.name, Level.ALL)
        val modEventBus = FMLJavaModLoadingContext.get().modEventBus

        // Register Items
        ChunkItems.values().forEach {
            ForgeRegistries.ITEMS.register(it.items)
            modEventBus.register(it.items)
        }
        MiscItems.values().forEach {
            ForgeRegistries.ITEMS.register(it.items)
            modEventBus.register(it.items)
        }

        // Register Blocks
        OreBlocks.values().forEach {
            ForgeRegistries.BLOCKS.register(it.block)
            modEventBus.register(it.block)
        }
        MiscBlocks.values().forEach {
            ForgeRegistries.BLOCKS.register(it.block)
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
