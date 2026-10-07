@file:Suppress("unused")

package org.eln2.mc

import com.mojang.brigadier.Command
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RegisterClientCommandsEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.ForgeRegistries
import org.ageseries.libage.data.AUXILIARY_CLASSIFIERS
import org.ageseries.libage.data.DIMENSION_TYPES
import org.ageseries.libage.data.AngularVelocity
import org.ageseries.libage.data.Distance
import org.ageseries.libage.data.Energy
import org.ageseries.libage.data.Mass
import org.ageseries.libage.data.Power
import org.ageseries.libage.data.Temperature
import org.ageseries.libage.data.Time
import org.ageseries.libage.data.Torque
import org.ageseries.libage.data.Volume
import org.ageseries.libage.data.classify
import org.ageseries.libage.data.classifyAuxiliary
import org.ageseries.libage.sim.ChemicalElement
import org.ageseries.libage.utils.libageUseValidation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.mc.client.input.KeyMappingRegistry
import org.eln2.mc.client.overlays.OverlayRegistry
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.FlwInstanceTypes
import org.eln2.mc.client.render.foundation.FlwVisualizerRegistry
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.containers.ContainerRegistry
import org.eln2.mc.common.content.OscilloscopeCopyManager
import org.eln2.mc.common.content.OscilloscopeShader
import org.eln2.mc.common.content.ScrewdriverItem
import org.eln2.mc.common.content.FlashlightItem
import org.eln2.mc.client.dynamicLight.DynamicLightManager
import org.eln2.mc.common.content.fluid.ChemicalBottleItem
import org.eln2.mc.common.content.modules.ContentManager
import org.eln2.mc.common.content.processing.AlloyingRecipe
import org.eln2.mc.common.content.processing.AqueousElectrolysisRecipe
import org.eln2.mc.common.content.processing.BurningRecipe
import org.eln2.mc.common.content.processing.CokingRecipe
import org.eln2.mc.common.content.processing.HydrogenReductionRecipe
import org.eln2.mc.common.entities.EntityRegistry
import org.eln2.mc.common.fluids.ForgeFluidRegistry
import org.eln2.mc.common.grids.TerminalHighlightRenderer
import org.eln2.mc.common.items.CreativeTabRegistry
import org.eln2.mc.common.items.ItemRegistry
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.recipes.RecipeRegistry
import org.eln2.mc.common.sounds.SoundRegistry
import org.eln2.mc.common.specs.SpecRegistry
import org.eln2.mc.common.specs.foundation.SpecContainerPart
import org.eln2.mc.common.specs.foundation.SpecPlacementOverlayClient
import org.eln2.mc.common.specs.foundation.SpecPreviewRenderer
import org.eln2.mc.integration.sodium.EmbeddiumCompat
import org.eln2.mc.integration.sodium.SodiumPlugin
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.io.path.Path

val LOG: Logger = LogManager.getLogger()

const val MODID = "eln2"

/**
 * Conversion factor between Forge Energy (FE/RF) and ELN2 joules.
 * FE is integer-valued, so this factor is chosen large enough to keep quantization error small for typical per-tick transfers while staying within [Int.MAX_VALUE] for reasonable cell capacities.
 * A 840 Wh lead-acid cell stores ~30M FE.
 * */
const val RF_PER_JOULE = 10

@Mod(MODID)
class Eln2 {
    init {
        libageUseValidation(ELN2_DEBUG)

        val context = ModLoadingContext.get()

        Eln2Config.registerSpecs(context)

        val modEventBus = FMLJavaModLoadingContext.get().modEventBus
        val forgeEventBus = MinecraftForge.EVENT_BUS

        BlockRegistry.setup(modEventBus)
        EntityRegistry.setup(modEventBus)
        ItemRegistry.setup(modEventBus)
        CreativeTabRegistry.setup(modEventBus)
        ContainerRegistry.setup(modEventBus)
        RecipeRegistry.setup(modEventBus)
        SoundRegistry.setup(modEventBus)
        ForgeFluidRegistry.setup(modEventBus)

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
            Runnable {
                clientSetup(forgeEventBus, modEventBus)
            }
        }

        Networking.setup()

        CellRegistry.setup(modEventBus)
        PartRegistry.setup(modEventBus)
        SpecRegistry.setup(modEventBus)
        ContentManager.initialize()

        LOG.info("Prepared registries.")
    }

    private fun clientSetup(forgeEventBus: IEventBus, modEventBus: IEventBus) {
        modEventBus.addListener { event: FMLClientSetupEvent ->
            event.enqueueWork {
                FlwInstanceTypes.init()
                FlwVisualizerRegistry.registerFoundationalVisualizers()
                ContentManager.registerBlockEntityVisualizers()
                ContentManager.registerPartVisualizers()
                ContentManager.registerSpecVisualizers()
                ContentManager.setupScreens()
                ContentManager.setRenderLayers()
                FlashlightItem.registerClient()
            }
        }

        modEventBus.addListener(OverlayRegistry::register)
        modEventBus.addListener(KeyMappingRegistry::register)
        modEventBus.addListener(OscilloscopeShader::register)
        modEventBus.addListener(DynamicLightManager::register)

        forgeEventBus.addListener(DynamicLightManager::render)
        forgeEventBus.addListener(::registerClientCommands)

        forgeEventBus.addListener(EventPriority.LOWEST, SpecContainerPart::renderHighlightEvent)
        forgeEventBus.addListener(TerminalHighlightRenderer::render)
        forgeEventBus.addListener(SpecPlacementOverlayClient::onScroll)
        forgeEventBus.addListener(EventPriority.HIGHEST, ScrewdriverItem::onScroll)
        forgeEventBus.addListener(SpecPlacementOverlayClient::onKey)
        forgeEventBus.addListener(SpecPreviewRenderer::render)
        forgeEventBus.addListener(OscilloscopeCopyManager::execute)

        if(SodiumPlugin.shouldApply()) {
            forgeEventBus.addListener(EmbeddiumCompat::`eln2GridRenderer$handleEvent`)
        }

        forgeEventBus.addListener(EventPriority.LOWEST, ChemicalBottleItem::onRightClickBlockEvent)

        FlwModels.initialize()

        LOG.info("Prepared client-side")
    }
}

/**
 * Gets a [ResourceLocation] with ELN2's modid.
 * */
fun resource(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MODID, path)

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

val ELN2_DEBUG get() = true
val ELN2_LOG_STATS get() = false

fun getResourceStringHelper(resource: String) : String =
    if (!SharedConstants.IS_RUNNING_IN_IDE) getResourceString(resource(resource))
    else Files.readString(Path("./src/main/resources/assets/eln2/$resource"))

fun getResourceBinaryHelper(resource: String): ByteArray =
    if (!SharedConstants.IS_RUNNING_IN_IDE) getResourceBinary(resource(resource))
    else Files.readAllBytes(Path("./src/main/resources/assets/eln2/$resource"))

fun getMaterialKey(material: String) : String = "material.$MODID.$material"
fun getChemicalElementKey(element: ChemicalElement) = getMaterialKey(element.label)

fun registerClientCommands(event: RegisterClientCommandsEvent) {
    /**
     * Items that are intentionally not craftable and should be excluded from the missing-recipe check.
     * */
    val missingRecipeBlacklist = setOf(
        resource("spec_container"),
        resource("voltage_source"),
        resource("burnt_fuse"),
        resource("burnt_heating_element")
    )

    val eln2 = Commands.literal("eln2").then(
        Commands.literal("units").then(
            Commands.literal("set").also { pSet ->
                DIMENSION_TYPES.forward.entries.sortedBy { it.value }.forEach { (dimensionType, dimensionName) ->
                    val auxiliaryUnits = AUXILIARY_CLASSIFIERS[dimensionType]
                        ?: return@forEach

                    if(auxiliaryUnits.keys.isNotEmpty()) {
                        pSet.then(
                            Commands.literal(dimensionName).also { pDimension ->
                                auxiliaryUnits.keys.forEach { scaleRef ->
                                    auxiliaryUnits[scaleRef].forEach { identifier ->
                                        pDimension.then(
                                            Commands.literal("to").then(
                                                Commands.literal(identifier).executes {
                                                    Eln2Config.clientConfig.setScaleOverride(dimensionType, identifier)

                                                    Minecraft.getInstance().player?.also { player ->
                                                        player.displayClientMessage(
                                                            Component.literal("$dimensionName -> ${
                                                                classifyAuxiliary(
                                                                    scaleRef,
                                                                    1.0
                                                                )
                                                            }"), false)
                                                    }

                                                    Command.SINGLE_SUCCESS
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ).then(
            Commands.literal("reset").also { pReset ->
                DIMENSION_TYPES.forward.entries.sortedBy { it.value }.forEach { (dimensionType, dimensionName) ->
                    val auxiliaryUnits = AUXILIARY_CLASSIFIERS[dimensionType]
                        ?: return@forEach

                    if(auxiliaryUnits.keys.isNotEmpty()) {
                        pReset.then(Commands.literal(dimensionName).executes {
                            Eln2Config.clientConfig.resetScaleOverride(dimensionType)

                            Minecraft.getInstance().player?.also { player ->
                                player.displayClientMessage(
                                    Component.literal("$dimensionName -> ${
                                        classify(
                                            dimensionType,
                                            1.0 /*Factor*/
                                        )
                                    }"), false)
                            }

                            Command.SINGLE_SUCCESS
                        })
                    }
                }
            }
        ).then(
            Commands.literal("preset").also { pPreset ->
                fun definePreset(name: String, buildPreset: (applicator: (Class<*>, String) -> Unit) -> Unit) {
                    pPreset.then(Commands.literal(name).executes {
                        fun applicator(dimensionType: Class<*>, alias: String) {
                            if(alias.isEmpty()) {
                                Eln2Config.clientConfig.resetScaleOverride(dimensionType)
                            }
                            else {
                                Eln2Config.clientConfig.setScaleOverride(dimensionType, alias)
                            }
                        }

                        buildPreset(::applicator)

                        Minecraft.getInstance().player?.also { player ->
                            player.displayClientMessage(Component.literal("*$name"), false)
                        }

                        Command.SINGLE_SUCCESS
                    })
                }

                definePreset("SI") {
                    it(Time::class.java, "")
                    it(Distance::class.java, "")
                    it(Mass::class.java, "")
                    it(Energy::class.java, "")
                    it(Temperature::class.java, "")
                    it(Power::class.java, "")
                    it(Volume::class.java, "")
                    it(AngularVelocity::class.java, "")
                    it(Torque::class.java, "")
                }

                definePreset("Metric") {
                    it(Time::class.java, "")
                    it(Distance::class.java, "")
                    it(Mass::class.java, "")
                    it(Energy::class.java, "")
                    it(Temperature::class.java, "C")
                    it(Power::class.java, "")
                    it(Volume::class.java, "L")
                    it(AngularVelocity::class.java, "")
                    it(Torque::class.java, "")
                }

                definePreset("Imperial") {
                    it(Time::class.java, "")
                    it(Distance::class.java, "ft")
                    it(Mass::class.java, "lb")
                    it(Energy::class.java, "BTU")
                    it(Temperature::class.java, "F")
                    it(Power::class.java, "hp")
                    it(Volume::class.java, "gal")
                    it(AngularVelocity::class.java, "rpm")
                    it(Torque::class.java, "ftlbf")
                }
            }
        )
    ).then(
        Commands.literal("recipes").then(
            Commands.literal("missing").executes {
                val connection = Minecraft.getInstance().connection
                val level = Minecraft.getInstance().level

                if(connection == null || level == null) {
                    LOG.error("Cannot list missing recipes: not connected to a world.")
                    return@executes Command.SINGLE_SUCCESS
                }

                val recipeManager = connection.recipeManager
                val registryAccess = level.registryAccess()

                val eln2ItemIds = ForgeRegistries.ITEMS.entries
                    .asSequence()
                    .map { it.key.location() }
                    .filter { it.namespace == MODID }
                    .filter { id ->
                        val path = id.path
                        !path.endsWith("_bucket") &&
                            !path.endsWith("_bottle") &&
                            !path.endsWith("_ore") &&
                            !path.startsWith("raw_")
                    }
                    .filter { it !in missingRecipeBlacklist }
                    .toMutableSet()

                for(recipe in recipeManager.recipes) {
                    val resultItem = recipe.getResultItem(registryAccess)

                    if(!resultItem.isEmpty) {
                        val resultId = ForgeRegistries.ITEMS.getKey(resultItem.item)
                        if(resultId != null && resultId.namespace == MODID) {
                            eln2ItemIds.remove(resultId)
                        }
                    } else {
                        for(outputItem in extractNonStandardRecipeOutputs(recipe)) {
                            val resultId = ForgeRegistries.ITEMS.getKey(outputItem.item)
                            if(resultId != null && resultId.namespace == MODID) {
                                eln2ItemIds.remove(resultId)
                            }
                        }
                    }
                }

                if(eln2ItemIds.isEmpty()) {
                    LOG.info("All ELN2 items have at least one recipe.")
                } else {
                    LOG.info("ELN2 items without any recipe ({} total):", eln2ItemIds.size)
                    eln2ItemIds.sortedBy { it.path }.forEach {
                        LOG.info("  {}", it)
                    }
                }

                Minecraft.getInstance().player?.displayClientMessage(
                    Component.literal("Wrote items with missing recipes to logs"), false
                )

                Command.SINGLE_SUCCESS
            }
        )
    )

    event.dispatcher.register(eln2)
}

/**
 * Extracts output [ItemStack]s from non-standard recipes that return [ItemStack.EMPTY] from [Recipe.getResultItem].
 * These recipes carry their outputs in custom fields.
 * */
private fun extractNonStandardRecipeOutputs(recipe: Recipe<*>): List<ItemStack> {
    return when(recipe) {
        is AlloyingRecipe -> listOf(recipe.output)

        is HydrogenReductionRecipe -> listOf(recipe.output)

        is BurningRecipe -> listOfNotNull(recipe.outputItem)

        is CokingRecipe -> recipe.outputItems ?: emptyList()

        is AqueousElectrolysisRecipe -> listOfNotNull(recipe.anodeOutputItem, recipe.cathodeOutputItem)

        else -> emptyList()
    }
}
