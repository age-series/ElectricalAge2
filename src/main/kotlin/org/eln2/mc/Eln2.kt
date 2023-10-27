package org.eln2.mc

import net.minecraft.SharedConstants
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
import org.eln2.mc.client.render.foundation.FlywheelRegistry
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.RenderTypes
import org.eln2.mc.common.blocks.BlockRegistry
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
            modEventBus.addListener { event: FMLClientSetupEvent ->
                event.enqueueWork {
                    FlywheelRegistry.initialize()
                    RenderTypes.initialize()
                    Content.clientWork()
                }
            }

            PartialModels.initialize()

            LOG.info("Prepared client-side")
        }

        Networking.setup()

        CellRegistry.setup(modEventBus)
        PartRegistry.setup(modEventBus)
        Content.initialize()

        LOG.info("Prepared registries.")
    }
}

/**
 * Gets a [ResourceLocation] with ELN2's modid.
 * */
fun resource(path: String) = ResourceLocation(MODID, path)

/**
 * Gets the [Resource] at the specified [location]. If it does not exist, an exception is thrown.
 * */
fun getResource(location: ResourceLocation): Resource = Minecraft.getInstance().resourceManager.getResource(location).orElseThrow()

/**
 * Opens an [InputStream] for the [Resource] at the specified [location]. If it does not exist, an exception is thrown.
 * */
fun getResourceStream(location: ResourceLocation): InputStream = getResource(location).open()

/**
 * Gets a byte array that contains the content from the [Resource] at the specified [location].
 * */
fun getResourceBinary(location: ResourceLocation) : ByteArray {
    val stream = getResourceStream(location)
    val result = stream.readAllBytes()
    stream.close()

    return result
}

/**
 * Gets a string that contains the content from the [Resource] at the specified [location].
 * */
fun getResourceString(location: ResourceLocation, charset: Charset = Charset.defaultCharset()) : String =
    getResourceBinary(location).toString(charset)

fun getResourceStringHelper(resource: String) : String =
    if (!SharedConstants.IS_RUNNING_IN_IDE) getResourceString(resource(resource))
    else Files.readString(Path("./src/main/resources/assets/eln2/$resource"))

fun getResourceBinaryHelper(resource: String) =
    if (!SharedConstants.IS_RUNNING_IN_IDE) getResourceBinary(resource(resource))
    else Files.readAllBytes(Path("./src/main/resources/assets/eln2/$resource"))
