package org.eln2.mc

import net.minecraftforge.fml.common.Mod
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.mc.common.Networking
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.common.items.ItemRegistry
import org.eln2.mc.utility.SuffixConverter
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(Eln2.MODID)
object Eln2 {
    const val MODID = "eln2"
    val LOGGER: Logger = LogManager.getLogger()

    init {
        LOGGER.info("!!!!!!!!!!!!!!! ${SuffixConverter.convert(12001, 2, arrayOf("one", "two"), 1000)}")

        BlockRegistry.setup(MOD_BUS)
        ItemRegistry.setup(MOD_BUS)

        Networking.setup()

        // custom registries
        CellRegistry.setup(MOD_BUS)

        LOGGER.info("Prepared registries.")

        LibElectricTest.test()
    }
}
