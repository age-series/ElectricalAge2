package org.eln2

import net.darkhax.bookshelf.registry.RegistryHelper
import net.minecraftforge.fml.common.thread.SidedThreadGroups
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.registry.*
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

    private val registry: RegistryHelper = RegistryHelper(MODID, LOGGER)

    val logicalServer = Thread.currentThread().threadGroup == SidedThreadGroups.SERVER

    /**
     * Initialization.
     *
     * This is run at class construction time, and should do as little as possible (register blocks/items)
     */
    fun initialize() {
        // Register Items
        ChunkItems.values().forEach { registry.items.register(it.items, it.name.toLowerCase()) }
        MiscItems.values().forEach { registry.items.register(it.items, it.name.toLowerCase()) }

        // Register Blocks
        OreBlocks.values().forEach { registry.blocks.register(it.block, it.name.toLowerCase()) }
        MiscBlocks.values().forEach { registry.blocks.register(it.block, it.name.toLowerCase()) }
        registry.initialize(FMLJavaModLoadingContext.get().modEventBus)
    }

    /**
     * Preinit handler.
     *
     * This is run in a threaded context, so we cannot communicate with other mods without using IMC.
     */
    fun setup(event: FMLCommonSetupEvent) {
        OreGen.setupOreGeneration()
    }

    fun loadComplete(event: FMLLoadCompleteEvent) {
        AnalyticsHandler.sendAnalyticsData()
    }
}
