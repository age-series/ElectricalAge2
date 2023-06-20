package org.eln2.mc

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.mc.client.ClientEvents
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.capabilities.CapabilityRegistry
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.containers.ContainerRegistry
import org.eln2.mc.common.content.Content
import org.eln2.mc.common.entities.EntityRegistry
import org.eln2.mc.common.items.ItemRegistry
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.parts.PartRegistry
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import java.io.InputStream
import java.nio.charset.Charset

@Mod(Eln2.MODID)
object Eln2 {
    const val MODID = "eln2"
    val LOGGER: Logger = LogManager.getLogger()
    val config: ElnConfig

    init {
        Configuration.loadConfig()
        config = Configuration.instance

        BlockRegistry.setup(MOD_BUS)
        EntityRegistry.setup(MOD_BUS)
        ItemRegistry.setup(MOD_BUS)
        ContainerRegistry.setup(MOD_BUS)

        if (Dist.CLIENT == FMLEnvironment.dist) {
            // Client-side setup

            MOD_BUS.register(ClientEvents)
            MOD_BUS.register(Content.ClientSetup)

            PartialModels.initialize()
        }

        Networking.setup()

        // custom registries
        CellRegistry.setup(MOD_BUS)
        PartRegistry.setup(MOD_BUS)
        CapabilityRegistry.setup(MOD_BUS)
        Content.initialize()

        LOGGER.info("Prepared registries.")
    }

    fun resource(path: String): ResourceLocation {
        return ResourceLocation(MODID, path)
    }
}

fun getResource(location: ResourceLocation): Resource = Minecraft.getInstance().resourceManager.getResource(location).orElseThrow()
fun getResourceStream(location: ResourceLocation): InputStream = getResource(location).open()
fun getResourceString(location: ResourceLocation): String = getResourceStream(location).readAllBytes().toString(Charset.defaultCharset())
