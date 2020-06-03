package org.eln2

import net.darkhax.bookshelf.registry.RegistryHelper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.utils.OreGen

const val MODID = "eln2"

/**
 * The main entry point and registry holder for Electrical Age.
 */
object Eln2 {
    // Directly reference a log4j logger.
    @JvmField
    val LOGGER: Logger = LogManager.getLogger()

    private val registry: RegistryHelper = RegistryHelper.create(MODID, LOGGER, null)

    /**
     * Initialization.
     *
     * This is run at class construction time, and should do as little as possible.
     */
    fun initialize() {
        // Register blocks and items
        ModBlocks.values().forEach {
            registry.registerBlock(it.block, it.name.toLowerCase())
        }
        ModItems.values().forEach {
            registry.registerItem(it.items, it.name.toLowerCase())
        }
        registry.initialize(thedarkcolour.kotlinforforge.forge.MOD_BUS)
    }

    /**
     * Preinit handler.
     *
     * This is run in a threaded context, so we cannot communicate with other mods without using IMC.
     */

    fun loadComplete() {
        OreGen.setupOreGeneration()
    }
}
