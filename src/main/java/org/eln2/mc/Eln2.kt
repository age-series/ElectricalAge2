package org.eln2.mc

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.mc.client.ClientEvents
import org.eln2.mc.client.render.foundation.PartialModels
import org.eln2.mc.common.Configuration
import org.eln2.mc.common.ElectricalAgeConfiguration
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.containers.ContainerRegistry
import org.eln2.mc.common.items.ItemRegistry
import org.eln2.mc.common.network.ModStatistics
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.parts.foundation.PartRegistry
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

        if (Dist.CLIENT == FMLEnvironment.dist) {
            // Client-side setup

            MOD_BUS.register(ClientEvents)
            PartialModels.initialize()
        }

        Networking.setup()

        // custom registries
        CellRegistry.setup(MOD_BUS)
        PartRegistry.setup(MOD_BUS)

        LOGGER.info("Prepared registries.")

        if (config.enableAnalytics) {
            ModStatistics.sendAnalytics()
        }
    }

    fun resource(path : String) : ResourceLocation{
        return ResourceLocation(MODID, path)
    }

}
