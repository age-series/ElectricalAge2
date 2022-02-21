package org.eln2.mc

import net.minecraftforge.fml.common.Mod
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.mc.common.Configuration
import org.eln2.mc.common.ElectricalAgeConfiguration
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.common.containers.ContainerRegistry
import org.eln2.mc.common.items.ItemRegistry
import org.eln2.mc.common.network.ModStatistics
import org.eln2.mc.common.network.Networking
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(Eln2.MODID)
object Eln2 {
    const val MODID = "eln2"
    val LOGGER: Logger = LogManager.getLogger()
    val config: ElectricalAgeConfiguration


    init {
        Configuration.loadConfig()
        config = Configuration.config

        BlockRegistry.setup(MOD_BUS)
        ItemRegistry.setup(MOD_BUS)
        ContainerRegistry.setup(MOD_BUS)

        MOD_BUS.register(ContainerRegistry) //client-side setup

        Networking.setup()

        // custom registries
        CellRegistry.setup(MOD_BUS)

        LOGGER.info("Prepared registries.")

        if (config.enableAnalytics) {
            ModStatistics.sendAnalytics()
        }
    }

}
