package org.eln2.mc

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLEnvironment
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.mc.client.render.FlywheelRegistry
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.RenderTypes
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.capabilities.CapabilityRegistry
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.containers.ContainerRegistry
import org.eln2.mc.common.content.Content
import org.eln2.mc.common.entities.EntityRegistry
import org.eln2.mc.common.fluids.FluidRegistry
import org.eln2.mc.common.items.ItemRegistry
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.parts.PartRegistry
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.io.path.Path

val LOG: Logger = LogManager.getLogger()
const val MODID = "eln2"

@Mod(MODID)
class Eln2 {
    init {
        val modEventBus = FMLJavaModLoadingContext.get().modEventBus

        BlockRegistry.setup(modEventBus)
        EntityRegistry.setup(modEventBus)
        ItemRegistry.setup(modEventBus)
        ContainerRegistry.setup(modEventBus)
        FluidRegistry.setup(modEventBus)

        if (Dist.CLIENT == FMLEnvironment.dist) {
            // Client-side setup

            modEventBus.addListener { event: FMLClientSetupEvent ->
                event.enqueueWork {
                    FlywheelRegistry.initialize()
                    RenderTypes.initialize()
                }
            }

            modEventBus.register(Content.ClientSetup::clientSetup)

            PartialModels.initialize()

            LOG.info("Prepared client-side")
        }

        Networking.setup()

        CellRegistry.setup(modEventBus)
        PartRegistry.setup(modEventBus)
        CapabilityRegistry.setup(modEventBus)
        Content.initialize()

        LOG.info("Prepared registries.")
    }
}

fun resource(path: String): ResourceLocation {
    return ResourceLocation(MODID, path)
}

fun getResource(location: ResourceLocation): Resource =
    Minecraft.getInstance().resourceManager.getResource(location).orElseThrow()

fun getResourceStream(location: ResourceLocation): InputStream = getResource(location).open()
fun getResourceBinary(location: ResourceLocation) = getResourceStream(location).readAllBytes()
fun getResourceString(location: ResourceLocation): String =
    getResourceStream(location).readAllBytes().toString(Charset.defaultCharset())

val GAME = false

fun getResourceStringHelper(s: String) =
    if (GAME) getResourceString(resource(s))
    else Files.readString(Path("./src/main/resources/assets/eln2/$s"))
fun getResourceBinaryHelper(s: String) =
    if (GAME) getResourceBinary(resource(s))
    else Files.readAllBytes(Path("./src/main/resources/assets/eln2/$s"))
