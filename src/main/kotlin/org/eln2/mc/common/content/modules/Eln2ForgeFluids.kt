@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import dev.engine_room.flywheel.api.visualization.VisualizerRegistry
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.material.Fluid
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.fluids.FluidType
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.CELSIUS
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.Temperature
import org.ageseries.libage.data.WATT_PER_KELVIN
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.utils.addUnique
import org.ageseries.libage.utils.putUnique
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.FTL
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.DummyBlockEntityRendererProvider
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.blocks.BlockRegistry.blockAndItem
import org.eln2.mc.common.blocks.BlockRegistry.blockEntityOnly
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.content.fluid.*
import org.eln2.mc.common.content.modules.ContentManager.withSelfDrop
import org.eln2.mc.common.content.fluid.GasReleaseBlock
import org.eln2.mc.common.content.fluid.GasReleaseBlockEntity
import org.eln2.mc.common.content.processing.PhaseChangeModuleBlock
import org.eln2.mc.common.content.processing.PhaseChangeModuleBlockEntity
import org.eln2.mc.common.content.processing.PhaseChangeModuleBlockEntityVisual
import org.eln2.mc.common.content.processing.PhaseChangeModuleCell
import org.eln2.mc.common.content.processing.PhaseChangeModuleModel
import org.eln2.mc.common.fluids.ForgeFluidRegistry
import org.eln2.mc.common.fluids.ForgeFluidRegistry.basicForgeFluid
import org.eln2.mc.common.items.ItemRegistry
import org.eln2.mc.common.items.ItemRegistry.item
import org.eln2.mc.common.sounds.SoundRegistry.soundEventVariableRange
import java.util.function.Supplier

object Eln2ForgeFluids : ContentModule() {
    //#region Registration Helpers

    /**
     * Deferred information for setting render layers in [setRenderLayers].
     * */
    private val renderLayerSetups = ArrayList<ForgeFluidRegistry.ForgeFluidRegistryItem>()

    /**
     * Sets the translucent render layer for the source and the flowing blocks of the fluid.
     * */
    fun ForgeFluidRegistry.BasicForgeFluidBuilder.withTranslucentLayer() {
        ContentManager.requireInit()

        this.onRegister {
            renderLayerSetups.add(it)
        }
    }

    /**
     * Read by [org.eln2.mc.Eln2ItemModelProviderDatagen] and [org.eln2.mc.common.ModEvents.registerItemColors].
     * */
    val CHEMICAL_BOTTLES_FOR_RESOLVE_AND_DATAGEN = LinkedHashMap<ForgeFluidRegistry.ForgeFluidRegistryItem, ChemicalBottleRegistryItem>()

    data class ChemicalBottleRegistryItem(
        val fluidRegistryItem: ForgeFluidRegistry.ForgeFluidRegistryItem,
        val tintColor: MyColor,
        val bottleItem: RegistryObject<ChemicalBottleItem>
    )

    /**
     * Registers a [ChemicalBottleItem] for the ELN2 fluid.
     * */
    fun ForgeFluidRegistry.BasicForgeFluidBuilder.withChemicalBottle() {
        ContentManager.requireInit()

        val tint = requireNotNull(this.tintColor) {
            DEBUGGER_BREAK("Bottle requires tint color")
        }

        this.onRegister {
            val bottle = ItemRegistry.item("${it.id}_bottle") {
                ChemicalBottleItem(it)
            }

            CHEMICAL_BOTTLES_FOR_RESOLVE_AND_DATAGEN.putUnique(it, ChemicalBottleRegistryItem(it, tint, bottle)) {
                DEBUGGER_BREAK("Duplicate chemical bottle for $this")
            }
        }
    }

    /**
     * Returns the chemical bottle for the [fluid] or null, if one isn't registered.
     * This is a slow operation.
     * */
    fun getChemicalBottle(fluid: Fluid) : ChemicalBottleItem? {
        val entry = CHEMICAL_BOTTLES_FOR_RESOLVE_AND_DATAGEN.entries.firstOrNull {
            it.key.get() == fluid
        } ?: return null

        return entry.value.bottleItem.get()
    }

    fun getChemicalBottle(fluid: ForgeFluidRegistry.ForgeFluidRegistryItem) = CHEMICAL_BOTTLES_FOR_RESOLVE_AND_DATAGEN[fluid]

    fun ForgeFluidRegistry.ForgeFluidRegistryItem.requireBottle() = getChemicalBottle(this) ?: FTL("Chemical bottle doesn't exist for $this")

    private val TANKS_FOR_VISUALIZER_AND_RENDERER_REGISTRY = LinkedHashSet<TankInfo>()

    data class TankInfo(
        val cell: RegistryObject<CellProvider<PhaseChangeModuleCell>>,
        val blockAndItem: BlockRegistry.BlockRegistryItem<PhaseChangeModuleBlock>,
        val blockEntity: RegistryObject<BlockEntityType<PhaseChangeModuleBlockEntity>>
    )

    fun tank(name: String, maxTemperature: Quantity<Temperature>, capacity: Int, modelSupplier: Supplier<PartialModel>) : TankInfo {
        val leakage = ConnectionParameters(conductance = Quantity(1.0, WATT_PER_KELVIN))

        val cell = CellRegistry.cellImmediate(name) {
            PhaseChangeModuleCell(it, leakage, maxTemperature, allowExternalConnections = false)
        }

        var blockEntity: RegistryObject<BlockEntityType<PhaseChangeModuleBlockEntity>>? = null

        val modelLazy = lazy {
            PhaseChangeModuleModel(false, modelSupplier)
        }

        val blockAndItem = BlockRegistry.blockAndItem(name) {
            PhaseChangeModuleBlock(cell, capacity, blockEntity!!, modelLazy.value)
        }

        blockAndItem.withSelfDrop()

        blockEntity = BlockRegistry.blockEntityOnly(name, blockAndItem, ::PhaseChangeModuleBlockEntity)

        val obj = TankInfo(cell, blockAndItem, blockEntity)
        TANKS_FOR_VISUALIZER_AND_RENDERER_REGISTRY.addUnique(obj)

        return obj
    }

    private fun registerTankVisualizers() {
        TANKS_FOR_VISUALIZER_AND_RENDERER_REGISTRY.forEach { obj ->
            VisualizerRegistry.setVisualizer(
                obj.blockEntity.get(),
                SimpleBlockEntityVisualizer(::PhaseChangeModuleBlockEntityVisual) { true }
            )
        }
    }

    private fun registerTankBlockEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        TANKS_FOR_VISUALIZER_AND_RENDERER_REGISTRY.forEach { obj ->
            event.registerBlockEntityRenderer(
                obj.blockEntity.get(),
                DummyBlockEntityRendererProvider()
            )
        }
    }

    //#endregion

    override fun registerBlockEntityVisualizers() {
        registerTankVisualizers()

        VisualizerRegistry.setVisualizer(
            FLUID_PIPE_BLOCK_ENTITY.get(),
            SimpleBlockEntityVisualizer(::FluidPipeBlockEntityVisual) { false }
        )
    }

    override fun registerBlockEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        registerTankBlockEntityRenderers(event)

        event.registerBlockEntityRenderer(
            FLUID_PIPE_BLOCK_ENTITY.get(),
            DummyBlockEntityRendererProvider(true)
        )
    }

    /**
     * Registers the render layers from [renderLayerSetups].
     * */
    override fun setRenderLayers() {
        renderLayerSetups.forEach { fluid ->
            ItemBlockRenderTypes.setRenderLayer(fluid.source.get(), RenderType.translucent())
            ItemBlockRenderTypes.setRenderLayer(fluid.flowing.get(), RenderType.translucent())
        }
    }

    //#region Pipe

    val FLUID_PIPE_BLOCK = blockAndItem("fluid_pipe", ::FluidPipeBlock)
        .withSelfDrop()

    val FLUID_PIPE_BLOCK_ENTITY = blockEntityOnly(
        "fluid_pipe",
        FLUID_PIPE_BLOCK,
        ::FluidPipeBlockEntity
    )

    val FLUID_PIPE_EXTRACTION_VALVE = item("fluid_pipe_extraction_valve", ::FluidPipeModuleItem)
    val FLUID_PIPE_INSERTION_VALVE = item("fluid_pipe_insertion_valve", ::FluidPipeModuleItem)

    //#endregion

    //#region Tanks

    val IRON_TANK = tank("iron_tank", Quantity(500.0, CELSIUS), 4000) {
        FlwModels.IRON_TANK
    }

    //#endregion

    val STEAM = basicForgeFluid("steam") {
        tintColor = MyColor(150, 240, 240, 240)
        properties {
            FluidType.Properties.create()
                .density(-1000)
                .viscosity(100)
        }

        withTranslucentLayer()
    }

    val FUEL_GAS = basicForgeFluid("fuel_gas") {
        tintColor = MyColor(50, 200, 200, 200)
        properties {
            FluidType.Properties.create()
                .density(-100)
                .viscosity(50)
        }
    }

    val NAPHTHA = basicForgeFluid("naphtha") {
        tintColor = MyColor(200, 255, 255, 220)
        properties {
            FluidType.Properties.create()
                .density(700)
                .viscosity(800)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val NAPHTHA_GAS = basicForgeFluid("naphtha_gas") {
        tintColor = MyColor(100, 200, 240, 255)
        properties {
            FluidType.Properties.create()
                .density(-1000)
                .viscosity(100)
        }

        withTranslucentLayer()
    }

    val CREOSOTE = basicForgeFluid("creosote") {
        tintColor = MyColor(200, 208, 218, 40)
        properties {
            FluidType.Properties.create()
                .density(1200)
                .viscosity(400)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val CREOSOTE_GAS = basicForgeFluid("creosote_gas") {
        tintColor = MyColor(100, 208, 218, 40)
        FluidType.Properties.create()
            .density(-600)
            .viscosity(100)
    }

    val COKE_GAS = basicForgeFluid("coke_gas") {
        tintColor = MyColor(150, 216, 250, 8)
        properties {
            FluidType.Properties.create()
                .density(-500)
                .viscosity(100)
        }

        withTranslucentLayer()
    }

    val COAL_TAR = basicForgeFluid("coal_tar") {
        tintColor = MyColor(255, 20, 20, 20)
        properties {
            FluidType.Properties.create()
                .density(1180)
                .viscosity(5000)
        }
    }

    val HEAVY_COAL_TAR = basicForgeFluid("heavy_coal_tar") {
        tintColor = MyColor(255, 10, 10, 10)
        properties {
            FluidType.Properties.create()
                .density(1510)
                .viscosity(5500)
        }
    }

    val PITCH = basicForgeFluid("pitch") {
        tintColor = MyColor(255, 5, 5, 5)
        properties {
            FluidType.Properties.create()
                .density(2000)
                .viscosity(6000)
        }

        withChemicalBottle()
    }

    val CRUDE_OIL = basicForgeFluid("crude_oil") {
        tintColor = MyColor(255, 30, 20, 10)
        properties {
            FluidType.Properties.create()
                .density(850)
                .viscosity(2000)
        }

        withChemicalBottle()
    }

    val HEAVY_OIL = basicForgeFluid("heavy_oil") {
        tintColor = MyColor(255, 40, 20, 10)
        properties {
            FluidType.Properties.create()
                .density(950)
                .viscosity(4000)
        }
    }

    val DIESEL = basicForgeFluid("diesel") {
        tintColor = MyColor(200, 255, 200, 0)
        properties {
            FluidType.Properties.create()
                .density(830)
                .viscosity(1500)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val DIESEL_GAS = basicForgeFluid("diesel_gas") {
        tintColor = MyColor(100, 220, 200, 150)
        properties {
            FluidType.Properties.create()
                .density(-500)
                .viscosity(200)
        }

        withTranslucentLayer()
    }

    val BITUMEN = basicForgeFluid("bitumen") {
        tintColor = MyColor(255, 10, 10, 10)
        properties {
            FluidType.Properties.create()
                .density(1200)
                .viscosity(10000)
        }

        withChemicalBottle()
    }

    val LIQUID_METHANE = basicForgeFluid("liquid_methane") {
        tintColor = MyColor(180, 100, 100, 255)
        properties {
            FluidType.Properties.create()
                .density(420)
                .viscosity(500)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val METHANE = basicForgeFluid("methane") {
        tintColor = MyColor(100, 200, 200, 255)
        properties {
            FluidType.Properties.create()
                .density(-500)
                .viscosity(100)
        }

        withTranslucentLayer()
    }

    val LIQUID_PROPANE = basicForgeFluid("liquid_propane") {
        tintColor = MyColor(180, 255, 255, 255)
        properties {
            FluidType.Properties.create()
                .density(580)
                .viscosity(600)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val PROPANE = basicForgeFluid("propane") {
        tintColor = MyColor(100, 255, 255, 255)
        properties {
            FluidType.Properties.create()
                .density(-600)
                .viscosity(100)
        }

        withTranslucentLayer()
    }

    val LIQUID_BUTANE = basicForgeFluid("liquid_butane") {
        tintColor = MyColor(180, 255, 240, 220)
        properties {
            FluidType.Properties.create()
                .density(600)
                .viscosity(700)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val BUTANE = basicForgeFluid("butane") {
        tintColor = MyColor(100, 255, 250, 230)
        properties {
            FluidType.Properties.create()
                .density(-600)
                .viscosity(100)
        }

        withTranslucentLayer()
    }

    val LIQUID_HYDROGEN = basicForgeFluid("liquid_hydrogen") {
        tintColor = MyColor(180, 200, 180, 255)
        properties {
            FluidType.Properties.create()
                .density(70)
                .viscosity(200)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val HYDROGEN = basicForgeFluid("hydrogen") {
        tintColor = MyColor(80, 220, 200, 255)
        properties {
            FluidType.Properties.create()
                .density(-1000)
                .viscosity(50)
        }

        withTranslucentLayer()
    }

    val LIQUID_NITROGEN = basicForgeFluid("liquid_nitrogen") {
        tintColor = MyColor(180, 200, 240, 255)
        properties {
            FluidType.Properties.create()
                .density(810)
                .viscosity(400)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val NITROGEN = basicForgeFluid("nitrogen") {
        tintColor = MyColor(100, 220, 240, 255)
        properties {
            FluidType.Properties.create()
                .density(-800)
                .viscosity(100)
        }

        withTranslucentLayer()
    }

    val LIQUID_ACETYLENE = basicForgeFluid("liquid_acetylene") {
        tintColor = MyColor(180, 230, 230, 230)
        properties {
            FluidType.Properties.create()
                .density(620)
                .viscosity(500)
        }

        withTranslucentLayer()
    }

    val ACETYLENE = basicForgeFluid("acetylene") {
        tintColor = MyColor(100, 230, 230, 230)
        properties {
            FluidType.Properties.create()
                .density(-600)
                .viscosity(100)
        }

        withTranslucentLayer()
    }

    val INSULATING_VARNISH = basicForgeFluid("insulating_varnish") {
        tintColor = MyColor(180, 28, 23, 23)
        properties {
            FluidType.Properties.create()
                .density(1500)
                .viscosity(4000)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val SULFUR_DIOXIDE = basicForgeFluid("sulfur_dioxide") {
        tintColor = MyColor(100, 230, 230, 170)
        properties {
            FluidType.Properties.create()
                .density(-600)
                .viscosity(50)
        }

        withTranslucentLayer()
    }

    val DILUTE_SULFURIC_ACID = basicForgeFluid("dilute_sulfuric_acid") {
        tintColor = MyColor(100, 255, 255, 200)
        properties {
            FluidType.Properties.create()
                .density(2400)
                .viscosity(600)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val NITROGEN_DIOXIDE = basicForgeFluid("nitrogen_dioxide") {
        tintColor = MyColor(255, 102, 51, 34)
        properties {
            FluidType.Properties.create()
                .density(-600)
                .viscosity(60)
        }

        withTranslucentLayer()
    }

    val LIQUID_OXYGEN = basicForgeFluid("liquid_oxygen") {
        tintColor = MyColor(180, 100, 200, 255)
        properties {
            FluidType.Properties.create()
                .density(1140)
                .viscosity(300)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val OXYGEN = basicForgeFluid("oxygen") {
        tintColor = MyColor(100, 200, 200, 255)
        properties {
            FluidType.Properties.create()
                .density(-1000)
                .viscosity(50)
        }

        withTranslucentLayer()
    }

    val SODIUM_TUNGSTATE_SOLUTION = basicForgeFluid("sodium_tungstate_solution") {
        tintColor = MyColor(200, 255, 255, 100)
        properties {
            FluidType.Properties.create()
                .density(1500)
                .viscosity(1200)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val SODIUM_HYDROXIDE_SOLUTION = basicForgeFluid("sodium_hydroxide_solution") {
        tintColor = MyColor(200, 255, 200, 200)
        properties {
            FluidType.Properties.create()
                .density(1200)
                .viscosity(800)
        }

        withTranslucentLayer()
        withChemicalBottle()
    }

    val HHO_GAS = basicForgeFluid("hho_gas") {
        tintColor = MyColor(100, 220, 220, 255)
        properties {
            FluidType.Properties.create()
                .density(-500)
                .viscosity(80)
        }

        withTranslucentLayer()
    }

    //#region Gas Release Vent

    val GAS_RELEASE_BLOCK = blockAndItem("gas_release", ::GasReleaseBlock)
        .withSelfDrop()

    val GAS_RELEASE_BLOCK_ENTITY = blockEntityOnly(
        "gas_release",
        GAS_RELEASE_BLOCK.block,
        ::GasReleaseBlockEntity
    )

    val GAS_RELEASE_SOUND = soundEventVariableRange("gas_release")

    //#endregion
}
