@file:Suppress("unused", "UNUSED_PARAMETER")

package org.eln2.mc.common.content.processing

import com.google.gson.JsonObject
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual
import dev.engine_room.flywheel.api.visual.ShaderLightVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.CriterionTriggerInstance
import net.minecraft.data.recipes.FinishedRecipe
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemStackHandler
import org.ageseries.libage.data.JOULE
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.Temperature
import org.ageseries.libage.data.requireLocator
import org.ageseries.libage.mathematics.geometry.Vector2di
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Simulator
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.sim.electrical.ElectricalSimulation
import org.eln2.mc.CrossThreadAccess
import org.eln2.mc.MODID
import org.eln2.mc.LOG
import org.eln2.mc.Locators
import org.eln2.mc.OnServerThread
import org.eln2.mc.PoleMap
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.FlwMaterials
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.PartialModelHelper
import org.eln2.mc.client.screens.ProgressSupplierMenu
import org.eln2.mc.common.blocks.foundation.*
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.containers.ContainerHelper
import org.eln2.mc.common.containers.ProgressContainerData
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicate
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.fluids.foundation.FractionalFluidStack
import org.eln2.mc.common.fluids.foundation.FractionalFluidTankCapacityConstraint
import org.eln2.mc.common.fluids.foundation.IFractionalFluidHandler
import org.eln2.mc.common.fluids.foundation.MultipleFractionalFluidTank
import org.eln2.mc.common.fluids.foundation.PurityBasedMultipleFractionalFluidTank
import org.eln2.mc.common.recipes.RecipeRegistry
import org.eln2.mc.common.recipes.foundation.*
import org.eln2.mc.extensions.*
import org.eln2.mc.fluidStackToJson
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.itemStackToJson
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.min

//#region Recipe Containers

/**
 * Container for non-separated electrolysis recipe matching. Carries item slots and a snapshot of the anode input liquid tank fluids.
 * When no separator is installed, the cathode side is merged into the anode, so only the anode input liquid tank is relevant.
 * */
private class NonSeparatedElectrolysisContainer(
    anodeElectrode: ItemStack,
    cathodeElectrode: ItemStack,
    val anodeInputLiquidFluids: List<FractionalFluidStack>,
) : SimpleContainer(SLOT_COUNT) {
    companion object {
        const val ANODE_ELECTRODE_SLOT = 0
        const val CATHODE_ELECTRODE_SLOT = 1
        const val SEPARATOR_SLOT = 2
        const val SLOT_COUNT = 3
    }

    init {
        setItem(ANODE_ELECTRODE_SLOT, anodeElectrode)
        setItem(CATHODE_ELECTRODE_SLOT, cathodeElectrode)
    }
}

/**
 * Container for separated electrolysis recipe matching. Carries item slots (including separator) and snapshots of both anode and cathode input liquid tank fluids.
 * */
private class SeparatedElectrolysisContainer(
    anodeElectrode: ItemStack,
    cathodeElectrode: ItemStack,
    separator: ItemStack,
    val anodeInputLiquidFluids: List<FractionalFluidStack>,
    val cathodeInputLiquidFluids: List<FractionalFluidStack>,
) : SimpleContainer(SLOT_COUNT) {
    companion object {
        const val ANODE_ELECTRODE_SLOT = 0
        const val CATHODE_ELECTRODE_SLOT = 1
        const val SEPARATOR_SLOT = 2
        const val SLOT_COUNT = 3
    }

    init {
        setItem(ANODE_ELECTRODE_SLOT, anodeElectrode)
        setItem(CATHODE_ELECTRODE_SLOT, cathodeElectrode)
        setItem(SEPARATOR_SLOT, separator)
    }
}

/**
 * Exact-match check: returns true if [tankFluids] contains a stack of the same fluid as [required] with at least the required amount.
 * Allows dilution by water.
 * */
private fun fluidMatches(tankFluids: List<FractionalFluidStack>, required: FluidStack): Boolean {
    if (required.isEmpty) {
        return true
    }

    return tankFluids.size == 1 && tankFluids[0].let { stack ->
        stack.fluid == required.fluid && stack.amount >= required.amount
    }
}

//#endregion

//#region Recipe

/**
 * Base recipe for aqueous electrolysis.
 *
 * Holds common fields shared by [NonSeparatedAqueousElectrolysisRecipe] and [SeparatedAqueousElectrolysisRecipe].
 * The [Serializer] dispatches to the appropriate subclass based on the "separated" JSON key / network boolean.
 *
 * @param anodeElectrode The anode electrode. Catalyst, not consumed.
 * @param cathodeElectrode The cathode electrode. Catalyst, not consumed.
 * @param anodeOutputItem Optional solid deposited at the anode.
 * @param cathodeOutputItem Optional solid deposited at the cathode.
 * @param energyCost Total energy (J) required to complete the process.
 * @param resistance The electrolyte resistance (ohms).
 * */
sealed class AqueousElectrolysisRecipe(
    override val recipeSerializer: Serializer,
    override val recipeId: ResourceLocation,
    val anodeElectrode: Ingredient,
    val cathodeElectrode: Ingredient,
    val anodeOutputItem: ItemStack?,
    val cathodeOutputItem: ItemStack?,
    val energyCost: Double,
    val resistance: Double,
) : Eln2CustomRecipe<AqueousElectrolysisRecipe>, Eln2NonStandardRecipe {

    abstract val isSeparated: Boolean

    companion object {
        const val ANODE_ELECTRODE_SLOT = 0
        const val CATHODE_ELECTRODE_SLOT = 1
        const val SEPARATOR_SLOT = 2
    }

    override fun matches(pContainer: SimpleContainer, pLevel: Level): Boolean {
        if (pContainer !is NonSeparatedElectrolysisContainer && pContainer !is SeparatedElectrolysisContainer) {
            return false
        }

        return anodeElectrode.test(pContainer.getItem(ANODE_ELECTRODE_SLOT)) &&
            cathodeElectrode.test(pContainer.getItem(CATHODE_ELECTRODE_SLOT))
    }

    abstract class Builder {
        var anodeElectrode: Ingredient = Ingredient.EMPTY
        var cathodeElectrode: Ingredient = Ingredient.EMPTY
        var anodeOutputItem: ItemStack? = null
        var cathodeOutputItem: ItemStack? = null
        var energyCost: Double = 0.0
        var resistance: Double = 0.0
        val advancement: Advancement.Builder = Advancement.Builder.advancement()

        abstract val isSeparated: Boolean

        fun withAnodeElectrode(electrode: ItemLike): Builder {
            this.anodeElectrode = Ingredient.of(electrode)
            return this
        }

        fun withAnodeElectrode(electrode: Ingredient): Builder {
            this.anodeElectrode = electrode
            return this
        }

        fun withCathodeElectrode(electrode: ItemLike): Builder {
            this.cathodeElectrode = Ingredient.of(electrode)
            return this
        }

        fun withCathodeElectrode(electrode: Ingredient): Builder {
            this.cathodeElectrode = electrode
            return this
        }

        fun withAnodeOutputItem(item: ItemLike, count: Int = 1): Builder {
            this.anodeOutputItem = ItemStack(item, count)
            return this
        }

        fun withAnodeOutputItem(stack: ItemStack): Builder {
            this.anodeOutputItem = stack
            return this
        }

        fun withCathodeOutputItem(item: ItemLike, count: Int = 1): Builder {
            this.cathodeOutputItem = ItemStack(item, count)
            return this
        }

        fun withCathodeOutputItem(stack: ItemStack): Builder {
            this.cathodeOutputItem = stack
            return this
        }

        fun withEnergyCost(cost: Double): Builder {
            this.energyCost = cost
            return this
        }

        fun withResistance(resistance: Double): Builder {
            this.resistance = resistance
            return this
        }

        fun unlockedBy(pCriterionName: String, pCriterionTrigger: CriterionTriggerInstance): Builder {
            advancement.addCriterion(pCriterionName, pCriterionTrigger)
            return this
        }

        internal fun validateBase(id: ResourceLocation) {
            check(!anodeElectrode.isEmpty) {
                "Anode electrode for electrolysis recipe $id cannot be empty"
            }

            check(!cathodeElectrode.isEmpty) {
                "Cathode electrode for electrolysis recipe $id cannot be empty"
            }

            check(energyCost > 0.0) {
                "Energy cost for electrolysis recipe $id must be positive"
            }

            check(resistance > 0.0) {
                "Resistance for electrolysis recipe $id must be positive"
            }
        }

        internal fun serializeBase(json: JsonObject) {
            json.addProperty("separated", isSeparated)

            json.add("anodeElectrode", anodeElectrode.toJson())

            json.add("cathodeElectrode", cathodeElectrode.toJson())

            anodeOutputItem?.let { stack ->
                json.add("anodeOutputItem", itemStackToJson(stack))
            }

            cathodeOutputItem?.let { stack ->
                json.add("cathodeOutputItem", itemStackToJson(stack))
            }

            json.addProperty("energyCost", energyCost)

            json.addProperty("resistance", resistance)
        }

        internal fun validateAdvancement(id: ResourceLocation) {
            if (advancement.criteria.isEmpty()) {
                LOG.error("No criterion for electrolysis recipe $id")
            } else {
                advancement.eln2Unlock(id)
            }
        }
    }

    class Serializer(override val recipeType: RecipeType<AqueousElectrolysisRecipe>) : Eln2RecipeSerializer<AqueousElectrolysisRecipe> {
        override fun fromJson(pRecipeId: ResourceLocation, pSerializedRecipe: JsonObject): AqueousElectrolysisRecipe {
            val separated = pSerializedRecipe.getAsJsonPrimitive("separated").asBoolean

            val anodeElectrode = Ingredient.fromJson(pSerializedRecipe.get("anodeElectrode"))
            val cathodeElectrode = Ingredient.fromJson(pSerializedRecipe.get("cathodeElectrode"))

            val anodeOutputItem = if (pSerializedRecipe.has("anodeOutputItem")) {
                ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "anodeOutputItem"))
            } else {
                null
            }

            val cathodeOutputItem = if (pSerializedRecipe.has("cathodeOutputItem")) {
                ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "cathodeOutputItem"))
            } else {
                null
            }

            val energyCost = pSerializedRecipe.getAsJsonPrimitive("energyCost").asDouble
            val resistance = pSerializedRecipe.getAsJsonPrimitive("resistance").asDouble

            return if (separated) {
                val anodeInputFluid = GsonHelper.getAsJsonObject(pSerializedRecipe, "anodeInputFluid").asFluidStack()
                val cathodeInputFluid = GsonHelper.getAsJsonObject(pSerializedRecipe, "cathodeInputFluid").asFluidStack()

                val anodeOutputFluid = if (pSerializedRecipe.has("anodeOutputFluid")) {
                    pSerializedRecipe.getAsJsonObject("anodeOutputFluid").asFluidStack()
                } else {
                    null
                }

                val cathodeOutputFluid = if (pSerializedRecipe.has("cathodeOutputFluid")) {
                    pSerializedRecipe.getAsJsonObject("cathodeOutputFluid").asFluidStack()
                } else {
                    null
                }

                val anodeOutputGas = if (pSerializedRecipe.has("anodeOutputGas")) {
                    pSerializedRecipe.getAsJsonObject("anodeOutputGas").asFluidStack()
                } else {
                    null
                }

                val cathodeOutputGas = if (pSerializedRecipe.has("cathodeOutputGas")) {
                    pSerializedRecipe.getAsJsonObject("cathodeOutputGas").asFluidStack()
                } else {
                    null
                }

                val separator = Ingredient.fromJson(pSerializedRecipe.get("separator"))

                SeparatedAqueousElectrolysisRecipe(
                    this, pRecipeId,
                    anodeElectrode, cathodeElectrode,
                    anodeOutputItem, cathodeOutputItem,
                    energyCost, resistance,
                    anodeInputFluid, cathodeInputFluid,
                    anodeOutputFluid, cathodeOutputFluid,
                    anodeOutputGas, cathodeOutputGas,
                    separator
                )
            } else {
                val inputFluid = GsonHelper.getAsJsonObject(pSerializedRecipe, "inputFluid").asFluidStack()

                val outputFluid = if (pSerializedRecipe.has("outputFluid")) {
                    pSerializedRecipe.getAsJsonObject("outputFluid").asFluidStack()
                } else {
                    null
                }

                val outputGas = if (pSerializedRecipe.has("outputGas")) {
                    pSerializedRecipe.getAsJsonObject("outputGas").asFluidStack()
                } else {
                    null
                }

                NonSeparatedAqueousElectrolysisRecipe(
                    this, pRecipeId,
                    anodeElectrode, cathodeElectrode,
                    anodeOutputItem, cathodeOutputItem,
                    energyCost, resistance,
                    inputFluid, outputFluid, outputGas
                )
            }
        }

        override fun fromNetwork(pRecipeId: ResourceLocation, pBuffer: FriendlyByteBuf): AqueousElectrolysisRecipe {
            val separated = pBuffer.readBoolean()

            val anodeElectrode = Ingredient.fromNetwork(pBuffer)
            val cathodeElectrode = Ingredient.fromNetwork(pBuffer)

            val anodeOutputItem = pBuffer.readNullable { pBuffer.readItem() }
            val cathodeOutputItem = pBuffer.readNullable { pBuffer.readItem() }

            val energyCost = pBuffer.readDouble()
            val resistance = pBuffer.readDouble()

            return if (separated) {
                val anodeInputFluid = pBuffer.readFluidStack()
                val cathodeInputFluid = pBuffer.readFluidStack()

                val anodeOutputFluid = pBuffer.readNullable { pBuffer.readFluidStack() }
                val cathodeOutputFluid = pBuffer.readNullable { pBuffer.readFluidStack() }
                val anodeOutputGas = pBuffer.readNullable { pBuffer.readFluidStack() }
                val cathodeOutputGas = pBuffer.readNullable { pBuffer.readFluidStack() }

                val separator = Ingredient.fromNetwork(pBuffer)

                SeparatedAqueousElectrolysisRecipe(
                    this, pRecipeId,
                    anodeElectrode, cathodeElectrode,
                    anodeOutputItem, cathodeOutputItem,
                    energyCost, resistance,
                    anodeInputFluid, cathodeInputFluid,
                    anodeOutputFluid, cathodeOutputFluid,
                    anodeOutputGas, cathodeOutputGas,
                    separator
                )
            } else {
                val inputFluid = pBuffer.readFluidStack()

                val outputFluid = pBuffer.readNullable { pBuffer.readFluidStack() }
                val outputGas = pBuffer.readNullable { pBuffer.readFluidStack() }

                NonSeparatedAqueousElectrolysisRecipe(
                    this, pRecipeId,
                    anodeElectrode, cathodeElectrode,
                    anodeOutputItem, cathodeOutputItem,
                    energyCost, resistance,
                    inputFluid, outputFluid, outputGas
                )
            }
        }

        override fun toNetwork(pBuffer: FriendlyByteBuf, pRecipe: AqueousElectrolysisRecipe) {
            pBuffer.writeBoolean(pRecipe.isSeparated)

            pRecipe.anodeElectrode.toNetwork(pBuffer)
            pRecipe.cathodeElectrode.toNetwork(pBuffer)

            pBuffer.writeNullable(pRecipe.anodeOutputItem) { _, stack -> pBuffer.writeItem(stack) }
            pBuffer.writeNullable(pRecipe.cathodeOutputItem) { _, stack -> pBuffer.writeItem(stack) }

            pBuffer.writeDouble(pRecipe.energyCost)
            pBuffer.writeDouble(pRecipe.resistance)

            when (pRecipe) {
                is NonSeparatedAqueousElectrolysisRecipe -> {
                    pBuffer.writeFluidStack(pRecipe.inputFluid)
                    pBuffer.writeNullable(pRecipe.outputFluid) { _, fluid -> pBuffer.writeFluidStack(fluid) }
                    pBuffer.writeNullable(pRecipe.outputGas) { _, fluid -> pBuffer.writeFluidStack(fluid) }
                }

                is SeparatedAqueousElectrolysisRecipe -> {
                    pBuffer.writeFluidStack(pRecipe.anodeInputFluid)
                    pBuffer.writeFluidStack(pRecipe.cathodeInputFluid)
                    pBuffer.writeNullable(pRecipe.anodeOutputFluid) { _, fluid -> pBuffer.writeFluidStack(fluid) }
                    pBuffer.writeNullable(pRecipe.cathodeOutputFluid) { _, fluid -> pBuffer.writeFluidStack(fluid) }
                    pBuffer.writeNullable(pRecipe.anodeOutputGas) { _, fluid -> pBuffer.writeFluidStack(fluid) }
                    pBuffer.writeNullable(pRecipe.cathodeOutputGas) { _, fluid -> pBuffer.writeFluidStack(fluid) }
                    pRecipe.separator.toNetwork(pBuffer)
                }
            }
        }
    }
}

/**
 * Non-separated aqueous electrolysis recipe.
 *
 * Uses a single input fluid, and can produce a single output fluid and/or a single output gas.
 * No separator is required.
 *
 * @param inputFluid The consumed input fluid.
 * @param outputFluid Optional output fluid.
 * @param outputGas Optional output gas.
 * */
class NonSeparatedAqueousElectrolysisRecipe(
    recipeSerializer: Serializer,
    recipeId: ResourceLocation,
    anodeElectrode: Ingredient,
    cathodeElectrode: Ingredient,
    anodeOutputItem: ItemStack?,
    cathodeOutputItem: ItemStack?,
    energyCost: Double,
    resistance: Double,
    val inputFluid: FluidStack,
    val outputFluid: FluidStack?,
    val outputGas: FluidStack?,
) : AqueousElectrolysisRecipe(
    recipeSerializer, recipeId,
    anodeElectrode, cathodeElectrode,
    anodeOutputItem, cathodeOutputItem,
    energyCost, resistance
) {
    override val isSeparated = false

    override fun matches(pContainer: SimpleContainer, pLevel: Level): Boolean {
        val container = pContainer as? NonSeparatedElectrolysisContainer
            ?: return false

        return super.matches(pContainer, pLevel) &&
            fluidMatches(container.anodeInputLiquidFluids, inputFluid)
    }

    class Builder(val recipe: RecipeType<AqueousElectrolysisRecipe>) : AqueousElectrolysisRecipe.Builder() {
        override val isSeparated = false

        var inputFluid: FluidStack = FluidStack.EMPTY

        var outputFluid: FluidStack? = null

        var outputGas: FluidStack? = null

        fun withInputFluid(fluid: FluidStack): Builder {
            this.inputFluid = fluid
            return this
        }

        fun withOutputFluid(fluid: FluidStack): Builder {
            this.outputFluid = fluid
            return this
        }

        fun withOutputGas(fluid: FluidStack): Builder {
            this.outputGas = fluid
            return this
        }

        fun save(consumer: Consumer<FinishedRecipe?>, id: ResourceLocation) {
            validateBase(id)

            check(!inputFluid.isEmpty) {
                "Input fluid for electrolysis recipe $id cannot be empty"
            }

            check(
                anodeOutputItem != null || cathodeOutputItem != null ||
                outputFluid != null || outputGas != null
            ) {
                "Electrolysis recipe $id must have at least one output"
            }

            validateAdvancement(id)

            consumer.accept(Result(this, id))
        }

        class Result(val parent: Builder, val recipeId: ResourceLocation) : Eln2FinishedRecipe {
            override fun serializeRecipeData(json: JsonObject) {
                parent.serializeBase(json)

                json.add("inputFluid", fluidStackToJson(parent.inputFluid))

                parent.outputFluid?.let { fluid ->
                    json.add("outputFluid", fluidStackToJson(fluid))
                }

                parent.outputGas?.let { fluid ->
                    json.add("outputGas", fluidStackToJson(fluid))
                }
            }

            override fun getId(): ResourceLocation = recipeId

            override fun getType(): RecipeSerializer<*> = RecipeRegistry.getRecipeSerializer(parent.recipe)!!.get()

            override fun serializeAdvancement(): JsonObject = parent.advancement.serializeToJson()
        }
    }
}

/**
 * Separated aqueous electrolysis recipe.
 *
 * Uses two separate input fluids (anolyte and catholyte), and can produce separate output fluids,
 * output gases, and output solids for each electrode. Requires a separator item (catalyst, not consumed).
 *
 * @param anodeInputFluid The consumed anolyte.
 * @param cathodeInputFluid The consumed catholyte.
 * @param anodeOutputFluid Optional anolyte output fluid.
 * @param cathodeOutputFluid Optional catholyte output fluid.
 * @param anodeOutputGas Optional anode output gas.
 * @param cathodeOutputGas Optional cathode output gas.
 * @param separator The separator item. Catalyst, not consumed.
 * */
class SeparatedAqueousElectrolysisRecipe(
    recipeSerializer: Serializer,
    recipeId: ResourceLocation,
    anodeElectrode: Ingredient,
    cathodeElectrode: Ingredient,
    anodeOutputItem: ItemStack?,
    cathodeOutputItem: ItemStack?,
    energyCost: Double,
    resistance: Double,
    val anodeInputFluid: FluidStack,
    val cathodeInputFluid: FluidStack,
    val anodeOutputFluid: FluidStack?,
    val cathodeOutputFluid: FluidStack?,
    val anodeOutputGas: FluidStack?,
    val cathodeOutputGas: FluidStack?,
    val separator: Ingredient,
) : AqueousElectrolysisRecipe(
    recipeSerializer, recipeId,
    anodeElectrode, cathodeElectrode,
    anodeOutputItem, cathodeOutputItem,
    energyCost, resistance
) {
    override val isSeparated = true

    override fun matches(pContainer: SimpleContainer, pLevel: Level): Boolean {
        val container = pContainer as? SeparatedElectrolysisContainer
            ?: return false

        return super.matches(pContainer, pLevel) &&
            separator.test(pContainer.getItem(SEPARATOR_SLOT)) &&
            fluidMatches(container.anodeInputLiquidFluids, anodeInputFluid) &&
            fluidMatches(container.cathodeInputLiquidFluids, cathodeInputFluid)
    }

    class Builder(val recipe: RecipeType<AqueousElectrolysisRecipe>) : AqueousElectrolysisRecipe.Builder() {
        override val isSeparated = true

        var anodeInputFluid: FluidStack = FluidStack.EMPTY
        var cathodeInputFluid: FluidStack = FluidStack.EMPTY
        var anodeOutputFluid: FluidStack? = null
        var cathodeOutputFluid: FluidStack? = null
        var anodeOutputGas: FluidStack? = null
        var cathodeOutputGas: FluidStack? = null
        var separator: Ingredient = Ingredient.EMPTY

        fun withAnodeInputFluid(fluid: FluidStack): Builder {
            this.anodeInputFluid = fluid
            return this
        }

        fun withCathodeInputFluid(fluid: FluidStack): Builder {
            this.cathodeInputFluid = fluid
            return this
        }

        fun withAnodeOutputFluid(fluid: FluidStack): Builder {
            this.anodeOutputFluid = fluid
            return this
        }

        fun withCathodeOutputFluid(fluid: FluidStack): Builder {
            this.cathodeOutputFluid = fluid
            return this
        }

        fun withAnodeOutputGas(fluid: FluidStack): Builder {
            this.anodeOutputGas = fluid
            return this
        }

        fun withCathodeOutputGas(fluid: FluidStack): Builder {
            this.cathodeOutputGas = fluid
            return this
        }

        fun withSeparator(separator: ItemLike): Builder {
            this.separator = Ingredient.of(separator)
            return this
        }

        fun withSeparator(separator: Ingredient): Builder {
            this.separator = separator
            return this
        }

        fun save(consumer: Consumer<FinishedRecipe?>, id: ResourceLocation) {
            validateBase(id)

            check(!anodeInputFluid.isEmpty) {
                "Anode input fluid for electrolysis recipe $id cannot be empty"
            }

            check(!cathodeInputFluid.isEmpty) {
                "Cathode input fluid for electrolysis recipe $id cannot be empty"
            }

            check(!separator.isEmpty) {
                "Separator for electrolysis recipe $id cannot be empty"
            }

            check(
                anodeOutputItem != null || cathodeOutputItem != null ||
                anodeOutputFluid != null || cathodeOutputFluid != null ||
                anodeOutputGas != null || cathodeOutputGas != null
            ) {
                "Electrolysis recipe $id must have at least one output"
            }

            validateAdvancement(id)

            consumer.accept(Result(this, id))
        }

        class Result(val parent: Builder, val recipeId: ResourceLocation) : Eln2FinishedRecipe {
            override fun serializeRecipeData(json: JsonObject) {
                parent.serializeBase(json)

                json.add("anodeInputFluid", fluidStackToJson(parent.anodeInputFluid))
                json.add("cathodeInputFluid", fluidStackToJson(parent.cathodeInputFluid))

                parent.anodeOutputFluid?.let { fluid ->
                    json.add("anodeOutputFluid", fluidStackToJson(fluid))
                }

                parent.cathodeOutputFluid?.let { fluid ->
                    json.add("cathodeOutputFluid", fluidStackToJson(fluid))
                }

                parent.anodeOutputGas?.let { fluid ->
                    json.add("anodeOutputGas", fluidStackToJson(fluid))
                }

                parent.cathodeOutputGas?.let { fluid ->
                    json.add("cathodeOutputGas", fluidStackToJson(fluid))
                }

                json.add("separator", parent.separator.toJson())
            }

            override fun getId(): ResourceLocation = recipeId

            override fun getType(): RecipeSerializer<*> = RecipeRegistry.getRecipeSerializer(parent.recipe)!!.get()

            override fun serializeAdvancement(): JsonObject = parent.advancement.serializeToJson()
        }
    }
}

//#endregion

//#region Items

/**
 * Marker item for electrolysis electrodes. Used by [ElectrolysisMainBlockEntity.ElectrolysisInventoryHandler] for slot filtering.
 * */
open class ElectrodeItem : Item(Properties().stacksTo(1)) {
    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)
        pTooltipComponents.add(Component.translatable("tooltip.eln2.electrode.use").withStyle(ChatFormatting.GRAY))
    }
}

/**
 * Marker item for electrolysis separators. Used by [ElectrolysisMainBlockEntity.ElectrolysisInventoryHandler] for slot filtering.
 * */
open class SeparatorItem : Item(Properties().stacksTo(1)) {
    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)
        pTooltipComponents.add(Component.translatable("tooltip.eln2.separator.use").withStyle(ChatFormatting.GRAY))
    }
}

//#endregion

/**
 * Proxy cell for the electrolysis port blocks. Acts as a passthrough between the wire and the main cell.
 * */
class ElectrolysisProxyCell(
    ci: CellCreateInfo,
    override val electricalMap: PoleMap,
    override val electricalSize: ElectricalSize,
) : Cell(ci), SidedElectricalMapped<ElectrolysisProxyCell> {
    companion object {
        const val PROXY_RESISTANCE = 1e-4
    }

    @SimObject
    val resistor = PolarResistorObject(this, electricalMap).also {
        it.component.resistance = PROXY_RESISTANCE
    }
}

/**
 * Main electrolysis cell. Contains the load resistor that represents the electrolysis process.
 *
 * The resistance is driven by the active recipe's resistance multiplied by [cellCount] (cells in series).
 * Electrical power is converted to thermal energy in the [thermalBody] as waste heat.
 *
 * Lifecycle:
 *  - [bind] is called by the block entity when the cell is acquired.
 *  - [unbind] is called when the chunk unloads or the block is removed.
 *  - While unbound, the resistor is set to [ElectricalSimulation.MAX_RESISTANCE].
 *  - The block entity sets [isActive] and [processResistance] from the server thread.
 *
 * @param cellCount Number of electrolysis cells in series. Total resistance = recipe.resistance * cellCount.
 * @param thermalMassDef Thermal mass definition for the electrolyte vessel.
 * @param leakageParameters Thermal leakage to the environment.
 * @param maxBreakdownTemperature Temperature at which the vessel is destroyed.
 * */
class ElectrolysisCell(
    ci: CellCreateInfo,
    override val electricalMap: PoleMap,
    override val electricalSize: ElectricalSize,
    val cellCount: Int,
    thermalMassDef: ThermalMassDefinition,
    val leakageParameters: ConnectionParameters,
    maxBreakdownTemperature: Quantity<Temperature>,
) : Cell(ci), SidedElectricalMapped<ElectrolysisCell> {
    companion object {
        private const val TEMPERATURE_TAG = "temperature"
    }

    @SimObject
    val resistor = PolarResistorObject(this, electricalMap)

    val thermalBody = thermalMassDef.get().also {
        environmentData.loadTemperature(it)
    }

    private val environmentSimulator = Simulator().also {
        it.add(thermalBody)
        environmentData.connect(it, leakageParameters, thermalBody)
    }

    @Behavior
    val explosion = ThermalBreakdownBehavior.create(
        maxBreakdownTemperature,
        self(),
        thermalBody::temperature,
    )

    //#region Game-thread state

    @CrossThreadAccess
    var isBound: Boolean = false
        private set

    @OnServerThread
    fun bind() {
        isBound = true
    }

    @OnServerThread
    fun unbind() {
        isBound = false
    }

    /**
     * Whether the cell should draw power for electrolysis.
     * Set by the block entity on the server thread.
     * */
    @CrossThreadAccess
    @OnServerThread
    var isActive: Boolean = false

    /**
     * The resistance per cell, set from the recipe. Total resistance = this * [cellCount].
     * Zero means no recipe is active.
     * */
    var processResistance: Double = 0.0

    /**
     * Last measured electrical power, copied from the resistor during the simulation tick.
     * Safe to read from the server thread.
     * */
    @CrossThreadAccess
    var lastPower: Double = 0.0
        private set

    //#endregion

    override fun saveCellData() = CompoundTag().also {
        it.putQuantity(TEMPERATURE_TAG, thermalBody.temperature)
    }

    override fun loadCellData(tag: CompoundTag) {
        thermalBody.temperature = tag.getQuantity(TEMPERATURE_TAG)
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPre(this::simulationTick)
    }

    private fun simulationTick(dt: Double, phase: SimulationPhase) {
        environmentSimulator.step(dt)

        if (!isBound) {
            lastPower = 0.0
            resistor.component.updateResistance(ElectricalSimulation.MAX_RESISTANCE)
            return
        }

        if (!isActive || processResistance <= 0.0) {
            lastPower = 0.0
            resistor.component.updateResistance(ElectricalSimulation.MAX_RESISTANCE)
            return
        }

        val targetResistance = processResistance * cellCount
        resistor.component.updateResistance(targetResistance, 1e-4)

        val power = abs(resistor.component.power)
        lastPower = power

        val delta = power * dt

        if (delta > 1e-3) {
            thermalBody.energy += Quantity(delta, JOULE)
            setChanged()
        }
    }
}

class ElectrolysisProxyBlock : MultiblockDelegateUprightHorizontalDirectionCellBlock<ElectrolysisProxyCell>() {
    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState, pAdjacentState: BlockState, pDirection: Direction) = true

    override fun getCellProvider() = Eln2Processing.ELECTROLYSIS_PROXY_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = ElectrolysisProxyBlockEntity(pPos, pState)
}

class ElectrolysisProxyBlockEntity(pPos: BlockPos, pBlockState: BlockState) :
    MultiblockDelegateCellBlockEntity<ElectrolysisProxyCell>(
        pPos, pBlockState, Eln2Processing.ELECTROLYSIS_PROXY_BLOCK_ENTITY.get()
    )
{
    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (side == null) {
            return super.getCapability(cap, side)
        }

        val level = this.level
            ?: return super.getCapability(cap, side)

        val repPos = this.representativePos
            ?: return super.getCapability(cap, side)

        if (!level.isLoaded(repPos)) {
            return LazyOptional.empty()
        }

        val representative = level.getBlockEntity(repPos) as? ElectrolysisMainBlockEntity
            ?: return super.getCapability(cap, side)

        val isLeft = isLeftDelegate()

        val facing = blockState.getValue(HorizontalDirectionalBlock.FACING)

        return when {
            cap == ForgeCapabilities.ITEM_HANDLER && side == Direction.DOWN -> {
                (if (isLeft) representative.leftItemLazy else representative.rightItemLazy).cast()
            }

            cap == ForgeCapabilities.FLUID_HANDLER && side == Direction.UP -> {
                (if (isLeft) representative.leftGasLazy else representative.rightGasLazy).cast()
            }

            cap == ForgeCapabilities.FLUID_HANDLER && side == facing -> {
                (if (isLeft) representative.leftInputLiquidLazy else representative.rightInputLiquidLazy).cast()
            }

            cap == ForgeCapabilities.FLUID_HANDLER && side == facing.opposite -> {
                (if (isLeft) representative.leftOutputLiquidLazy else representative.rightOutputLiquidLazy).cast()
            }

            else -> super.getCapability(cap, side)
        }
    }

    /**
     * Determines if this delegate is the left (+1, 0, 0 in multiblock-local coords) or right (-1, 0, 0) delegate, from the player's perspective when facing the block.
     * */
    @ServerOnly
    fun isLeftDelegate(): Boolean {
        val repPos = this.representativePos
            ?: error("Cannot determine delegate side: representativePos is null")

        val facing = blockState.getValue(HorizontalDirectionalBlock.FACING)

        val localPos = MultiblockTransformations.transformWorldMultiblock(
            facing, repPos, blockPos
        )

        return localPos.x > 0
    }
}

class ElectrolysisMainBlock : UprightHorizontalDirectionCellBlock<ElectrolysisCell>() {
    override fun getCellProvider() = Eln2Processing.ELECTROLYSIS_MAIN_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = ElectrolysisMainBlockEntity(pPos, pState)

    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState?, pAdjacentState: BlockState?, pDirection: Direction?) = true

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T> {
        return BlockEntityTicker(ElectrolysisMainBlockEntity::tick)
    }

    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        val pos = cell.locator.requireLocator(Locators.BLOCK)

        val blockEntity = level.getBlockEntity(pos) as? ElectrolysisMainBlockEntity
            ?: return

        val facing = blockEntity.representativeFacing

        blockEntity.delegateMap.forEachDelegateInWorld(facing, pos) { delegatePos ->
            val delegate = level.getBlockEntity(delegatePos) as? ElectrolysisProxyBlockEntity
                ?: return@forEachDelegateInWorld

            results.add(CellAndContainerHandle.captureInScope(delegate.cell))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        return pLevel.constructMenuHelper2(pPos, pPlayer, Component.translatable("menu.$MODID.electrolysis"), ::ElectrolysisMenu)
    }
}

class ElectrolysisMainBlockEntity(pPos: BlockPos, pBlockState: BlockState) :
    CellBlockEntity<ElectrolysisCell>(
        pPos, pBlockState, Eln2Processing.ELECTROLYSIS_MAIN_BLOCK_ENTITY.get(),
    ),
    BigBlockRepresentativeBlockEntity<ElectrolysisMainBlockEntity>,
    ComponentDisplay
{
    companion object {
        const val TANK_CAPACITY = 40000.0

        fun tick(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pLevel == null || pBlockEntity == null) {
                return
            }

            if (pBlockEntity !is ElectrolysisMainBlockEntity) {
                LOG.error("Got $pBlockEntity instead of electrolysis main block entity")
                return
            }

            if (!pLevel.isClientSide) {
                pBlockEntity.serverTick()
            }
        }
    }

    override val delegateMap: MultiblockDelegateMap
        get() = Eln2Processing.ELECTROLYSIS_DELEGATE_MAP.value

    val data = ProgressContainerData()

    //#region Capability

    /**
     * Inventory handler for the electrolysis machine.
     * Slots: [ANODE_ELECTRODE_SLOT], [CATHODE_ELECTRODE_SLOT], [SEPARATOR_SLOT], [ANODE_OUTPUT_SLOT], [CATHODE_OUTPUT_SLOT].
     * */
    class ElectrolysisInventoryHandler(val blockEntity: ElectrolysisMainBlockEntity) : ItemStackHandler(SLOT_COUNT) {
        companion object {
            const val ANODE_ELECTRODE_SLOT = 0
            const val CATHODE_ELECTRODE_SLOT = 1
            const val SEPARATOR_SLOT = 2
            const val ANODE_OUTPUT_SLOT = 3
            const val CATHODE_OUTPUT_SLOT = 4
            const val SLOT_COUNT = 5
        }

        private val outputRange = ANODE_OUTPUT_SLOT..CATHODE_OUTPUT_SLOT

        private var separatorPresent = false

        private var dirty = false

        fun wasChanged(): Boolean {
            if (dirty) {
                dirty = false
                return true
            }

            return false
        }

        fun markChanged() {
            dirty = true
        }

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if (outputRange.contains(slot)) {
                return stack
            }

            return super.insertItem(slot, stack, simulate)
        }

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return when (slot) {
                ANODE_ELECTRODE_SLOT, CATHODE_ELECTRODE_SLOT -> stack.item is ElectrodeItem
                SEPARATOR_SLOT -> stack.item is SeparatorItem
                else -> false
            }
        }

        override fun onContentsChanged(slot: Int) {
            blockEntity.setChanged()
            dirty = true

            if (slot == SEPARATOR_SLOT) {
                val nowPresent = !getStackInSlot(SEPARATOR_SLOT).isEmpty

                if (nowPresent != separatorPresent) {
                    separatorPresent = nowPresent

                    if (nowPresent) {
                        blockEntity.fluidHandler.onSeparatorAdded()
                    } else {
                        blockEntity.fluidHandler.onSeparatorRemoved()
                    }
                }
            }
        }

        fun hasSeparator(): Boolean {
            return !getStackInSlot(SEPARATOR_SLOT).isEmpty
        }

        /**
         * Called during load to sync the internal [separatorPresent] flag without triggering merge/split.
         * */
        fun syncSeparatorState() {
            separatorPresent = hasSeparator()
        }
    }

    /**
     * Extract-only wrapper for a single slot of a parent [IItemHandler]. Used by the delegate bottom faces.
     * */
    class SingleSlotExtractHandler(val parent: IItemHandler, val slot: Int) : IItemHandler {
        override fun getSlots() = 1

        override fun getStackInSlot(pSlot: Int): ItemStack {
            if (pSlot != 0) {
                return ItemStack.EMPTY
            }

            return parent.getStackInSlot(slot)
        }

        override fun insertItem(pSlot: Int, pStack: ItemStack, pSimulate: Boolean): ItemStack {
            return pStack
        }

        override fun extractItem(pSlot: Int, pAmount: Int, pSimulate: Boolean): ItemStack {
            if (pSlot != 0) {
                return ItemStack.EMPTY
            }

            return parent.extractItem(slot, pAmount, pSimulate)
        }

        override fun getSlotLimit(pSlot: Int): Int {
            if (pSlot != 0) {
                return 0
            }

            return parent.getSlotLimit(slot)
        }

        override fun isItemValid(pSlot: Int, pStack: ItemStack): Boolean {
            return false
        }
    }

    /**
     * One half of the electrolysis fluid system. Holds an input liquid tank, an output liquid tank, and an output gas tank.
     * @param inputLiquidTank The raw input liquid tank for this side.
     * @param outputLiquidTank The raw output liquid tank for this side.
     * @param outputGasTank The raw output gas tank for this side.
     * */
    class ElectrolysisFluidSide(
        val inputLiquidTank: MultipleFractionalFluidTank,
        val outputLiquidTank: MultipleFractionalFluidTank,
        val outputGasTank: MultipleFractionalFluidTank,
    ) {
        val inputLiquidPurity = PurityBasedMultipleFractionalFluidTank(inputLiquidTank)

        val outputLiquidPurity = PurityBasedMultipleFractionalFluidTank(outputLiquidTank)

        val outputGasPurity = PurityBasedMultipleFractionalFluidTank(outputGasTank)

        fun serializeNBT(): CompoundTag {
            val tag = CompoundTag()
            tag.put("inputLiquid", inputLiquidTank.serializeNBT())
            tag.put("outputLiquid", outputLiquidTank.serializeNBT())
            tag.put("outputGas", outputGasTank.serializeNBT())
            return tag
        }

        fun deserializeNBT(tag: CompoundTag) {
            inputLiquidTank.deserializeNBT(tag.getCompound("inputLiquid"))
            outputLiquidTank.deserializeNBT(tag.getCompound("outputLiquid"))
            outputGasTank.deserializeNBT(tag.getCompound("outputGas"))
        }
    }

    /**
     * Manages the six-tank electrolysis fluid system: anode input liquid, anode output liquid, anode output gas, cathode input liquid, cathode output liquid, cathode output gas.
     * The [FractionalFluidTankCapacityConstraint] constrains the total capacity across all six tanks.
     * The [anode] side is the representative when no separator is installed.
     * Call [onSeparatorAdded] / [onSeparatorRemoved] when the separator item changes.
     * @param capacity Total shared capacity across all six tanks.
     * */
    class ElectrolysisFluidHandler(val capacity: Double) {
        val anode = ElectrolysisFluidSide(
            MultipleFractionalFluidTank(capacity, false),
            MultipleFractionalFluidTank(capacity, false),
            MultipleFractionalFluidTank(capacity, false),
        )

        val cathode = ElectrolysisFluidSide(
            MultipleFractionalFluidTank(capacity, false),
            MultipleFractionalFluidTank(capacity, false),
            MultipleFractionalFluidTank(capacity, false),
        )

        val constraint = FractionalFluidTankCapacityConstraint(
            capacity,
            arrayOf(
                anode.inputLiquidTank, anode.outputLiquidTank, anode.outputGasTank,
                cathode.inputLiquidTank, cathode.outputLiquidTank, cathode.outputGasTank,
            )
        )

        /**
         * Merges all fluid from [cathode] into [anode]. Called when the separator is removed.
         * */
        fun onSeparatorRemoved() {
            moveAll(cathode, anode)
        }

        /**
         * Splits all fluid from [anode] equally into [cathode]. Called when the separator is inserted.
         * */
        fun onSeparatorAdded() {
            splitHalf(anode, cathode)
        }

        fun serializeNBT(): CompoundTag {
            val tag = CompoundTag()
            tag.put("anode", anode.serializeNBT())
            tag.put("cathode", cathode.serializeNBT())
            return tag
        }

        fun deserializeNBT(tag: CompoundTag) {
            anode.deserializeNBT(tag.getCompound("anode"))
            cathode.deserializeNBT(tag.getCompound("cathode"))
        }

        companion object {
            private fun poolFluids(vararg tanks: MultipleFractionalFluidTank): Map<Fluid, Double> {
                val pool = LinkedHashMap<Fluid, Double>()

                for (tank in tanks) {
                    for (stack in tank.fluids) {
                        if (stack.isEmpty) {
                            continue
                        }

                        pool.merge(stack.fluid, stack.amount) { a, b -> a + b }
                    }
                }

                return pool
            }

            private fun clearTanks(vararg tanks: MultipleFractionalFluidTank) {
                for (tank in tanks) {
                    tank.fluids.clear()
                    tank.incrementVersion()
                }
            }

            private fun moveAll(source: ElectrolysisFluidSide, destination: ElectrolysisFluidSide) {
                moveAll(source.inputLiquidTank, destination.inputLiquidTank, source.outputLiquidTank, destination.outputLiquidTank, source.outputGasTank, destination.outputGasTank)
            }

            private fun moveAll(
                sourceLiquid: MultipleFractionalFluidTank, destLiquid: MultipleFractionalFluidTank,
                sourceOutputLiquid: MultipleFractionalFluidTank, destOutputLiquid: MultipleFractionalFluidTank,
                sourceGas: MultipleFractionalFluidTank, destGas: MultipleFractionalFluidTank,
            ) {
                moveAll(sourceLiquid, destLiquid)
                moveAll(sourceOutputLiquid, destOutputLiquid)
                moveAll(sourceGas, destGas)
            }

            private fun moveAll(source: MultipleFractionalFluidTank, destination: MultipleFractionalFluidTank) {
                val pool = poolFluids(source)

                clearTanks(source)

                for ((fluid, amount) in pool) {
                    if (amount < FractionalFluidStack.EPSILON) {
                        continue
                    }

                    val toMove = min(amount, destination.remainingCapacity)

                    if (toMove >= FractionalFluidStack.EPSILON) {
                        destination.mergeStack(FractionalFluidStack(fluid, toMove), false)
                        destination.incrementVersion()
                    }
                }
            }

            private fun splitHalf(source: ElectrolysisFluidSide, destination: ElectrolysisFluidSide) {
                splitHalf(source.inputLiquidTank, destination.inputLiquidTank)
                splitHalf(source.outputLiquidTank, destination.outputLiquidTank)
                splitHalf(source.outputGasTank, destination.outputGasTank)
            }

            private fun splitHalf(source: MultipleFractionalFluidTank, destination: MultipleFractionalFluidTank) {
                val pool = poolFluids(source, destination)

                clearTanks(source, destination)

                val half = LinkedHashMap<Fluid, Double>()

                for ((fluid, amount) in pool) {
                    half[fluid] = amount * 0.5
                }

                for ((fluid, amount) in half) {
                    if (amount < FractionalFluidStack.EPSILON) {
                        continue
                    }

                    val toSource = min(amount, source.remainingCapacity)

                    if (toSource >= FractionalFluidStack.EPSILON) {
                        source.mergeStack(FractionalFluidStack(fluid, toSource), false)
                        source.incrementVersion()
                    }

                    val toDest = min(amount, destination.remainingCapacity)

                    if (toDest >= FractionalFluidStack.EPSILON) {
                        destination.mergeStack(FractionalFluidStack(fluid, toDest), false)
                        destination.incrementVersion()
                    }
                }
            }
        }
    }

    /**
     * Routes input liquid fill operations to the correct side based on separator state. Drain is rejected.
     * When no separator is present, both sides route to anode.
     * @param fluidHandler The owning fluid handler.
     * @param inventoryHandler The inventory handler, used to check separator state.
     * @param isLeft True if this handler is for the left (anode) delegate.
     * */
    class SidedInputLiquidHandler(val fluidHandler: ElectrolysisFluidHandler, val inventoryHandler: ElectrolysisInventoryHandler, val isLeft: Boolean) : IFractionalFluidHandler {
        private fun currentInputLiquid(): PurityBasedMultipleFractionalFluidTank {
            return if (inventoryHandler.hasSeparator()) {
                if (isLeft) fluidHandler.anode.inputLiquidPurity else fluidHandler.cathode.inputLiquidPurity
            } else {
                fluidHandler.anode.inputLiquidPurity
            }
        }

        override fun getTanks() = currentInputLiquid().tanks

        override fun getFluidInTank(tank: Int) = currentInputLiquid().getFluidInTank(tank)

        override fun getTankCapacity(tank: Int) = currentInputLiquid().getTankCapacity(tank)

        override fun isFluidValid(tank: Int, stack: FluidStack) = currentInputLiquid().isFluidValid(tank, stack)

        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction) = currentInputLiquid().fill(resource, action)

        override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY

        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY

        override fun getFractionalFluidInTank(tank: Int) = currentInputLiquid().getFractionalFluidInTank(tank)

        override fun getFractionalTankCapacity(tank: Int) = currentInputLiquid().getFractionalTankCapacity(tank)

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = currentInputLiquid().fillFractional(resource, action)

        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY

        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY
    }

    /**
     * Routes output liquid drain operations to the correct side. Fill is rejected.
     * */
    class SidedOutputLiquidHandler(val fluidHandler: ElectrolysisFluidHandler, val inventoryHandler: ElectrolysisInventoryHandler, val isLeft: Boolean) : IFractionalFluidHandler {
        private fun currentOutputLiquid(): PurityBasedMultipleFractionalFluidTank {
            return if (inventoryHandler.hasSeparator()) {
                if (isLeft) fluidHandler.anode.outputLiquidPurity else fluidHandler.cathode.outputLiquidPurity
            } else {
                fluidHandler.anode.outputLiquidPurity
            }
        }

        override fun getTanks() = currentOutputLiquid().tanks

        override fun getFluidInTank(tank: Int) = currentOutputLiquid().getFluidInTank(tank)

        override fun getTankCapacity(tank: Int) = currentOutputLiquid().getTankCapacity(tank)

        override fun isFluidValid(tank: Int, stack: FluidStack) = false

        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction) = 0

        override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction) = currentOutputLiquid().drain(resource, action)

        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction) = currentOutputLiquid().drain(maxDrain, action)

        override fun getFractionalFluidInTank(tank: Int) = currentOutputLiquid().getFractionalFluidInTank(tank)

        override fun getFractionalTankCapacity(tank: Int) = currentOutputLiquid().getFractionalTankCapacity(tank)

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = 0.0

        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = currentOutputLiquid().drainFractional(resource, action)

        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction) = currentOutputLiquid().drainFractional(maxDrain, action)
    }

    /**
     * Routes gas drain operations to the correct side's output gas tank. Fill is rejected.
     * */
    class SidedGasHandler(val fluidHandler: ElectrolysisFluidHandler, val inventoryHandler: ElectrolysisInventoryHandler, val isLeft: Boolean) : IFractionalFluidHandler {
        private fun currentGas(): PurityBasedMultipleFractionalFluidTank {
            return if (inventoryHandler.hasSeparator()) {
                if (isLeft) fluidHandler.anode.outputGasPurity else fluidHandler.cathode.outputGasPurity
            } else {
                fluidHandler.anode.outputGasPurity
            }
        }

        override fun getTanks() = currentGas().tanks

        override fun getFluidInTank(tank: Int) = currentGas().getFluidInTank(tank)

        override fun getTankCapacity(tank: Int) = currentGas().getTankCapacity(tank)

        override fun isFluidValid(tank: Int, stack: FluidStack) = false

        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction) = 0

        override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction) = currentGas().drain(resource, action)

        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction) = currentGas().drain(maxDrain, action)

        override fun getFractionalFluidInTank(tank: Int) = currentGas().getFractionalFluidInTank(tank)

        override fun getFractionalTankCapacity(tank: Int) = currentGas().getFractionalTankCapacity(tank)

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = 0.0

        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = currentGas().drainFractional(resource, action)

        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction) = currentGas().drainFractional(maxDrain, action)
    }

    // Convention: left is anode, right is cathode
    internal val inventoryHandler = ElectrolysisInventoryHandler(this)
    val inventoryHandlerLazy: LazyOptional<IItemHandler> = LazyOptional.of { inventoryHandler }
    val leftItemLazy: LazyOptional<SingleSlotExtractHandler> = LazyOptional.of { SingleSlotExtractHandler(inventoryHandler, ElectrolysisInventoryHandler.ANODE_OUTPUT_SLOT) }
    val rightItemLazy: LazyOptional<SingleSlotExtractHandler> = LazyOptional.of { SingleSlotExtractHandler(inventoryHandler, ElectrolysisInventoryHandler.CATHODE_OUTPUT_SLOT) }

    val fluidHandler = ElectrolysisFluidHandler(TANK_CAPACITY)
    val leftInputLiquidLazy: LazyOptional<IFractionalFluidHandler> = LazyOptional.of { SidedInputLiquidHandler(fluidHandler, inventoryHandler, isLeft = true) }
    val rightInputLiquidLazy: LazyOptional<IFractionalFluidHandler> = LazyOptional.of { SidedInputLiquidHandler(fluidHandler, inventoryHandler, isLeft = false) }
    val leftOutputLiquidLazy: LazyOptional<IFractionalFluidHandler> = LazyOptional.of { SidedOutputLiquidHandler(fluidHandler, inventoryHandler, isLeft = true) }
    val rightOutputLiquidLazy: LazyOptional<IFractionalFluidHandler> = LazyOptional.of { SidedOutputLiquidHandler(fluidHandler, inventoryHandler, isLeft = false) }
    val leftGasLazy: LazyOptional<IFractionalFluidHandler> = LazyOptional.of { SidedGasHandler(fluidHandler, inventoryHandler, isLeft = true) }
    val rightGasLazy: LazyOptional<IFractionalFluidHandler> = LazyOptional.of { SidedGasHandler(fluidHandler, inventoryHandler, isLeft = false) }

    //#endregion

    /**
     * Builds a recipe-matching container from the current inventory and fluid state. The container type reflects the separator state:
     * [SeparatedElectrolysisContainer] when a separator is installed, [NonSeparatedElectrolysisContainer] otherwise.
     * Fluid lists are snapshots copied from the tanks at call time.
     * */
    fun buildRecipeContainer(): SimpleContainer {
        val anodeElectrode = inventoryHandler.getStackInSlot(ElectrolysisInventoryHandler.ANODE_ELECTRODE_SLOT)
        val cathodeElectrode = inventoryHandler.getStackInSlot(ElectrolysisInventoryHandler.CATHODE_ELECTRODE_SLOT)

        return if (inventoryHandler.hasSeparator()) {
            val separator = inventoryHandler.getStackInSlot(ElectrolysisInventoryHandler.SEPARATOR_SLOT)

            SeparatedElectrolysisContainer(
                anodeElectrode,
                cathodeElectrode,
                separator,
                fluidHandler.anode.inputLiquidTank.fluids.toList(),
                fluidHandler.cathode.inputLiquidTank.fluids.toList(),
            )
        } else {
            NonSeparatedElectrolysisContainer(
                anodeElectrode,
                cathodeElectrode,
                fluidHandler.anode.inputLiquidTank.fluids.toList(),
            )
        }
    }

    /**
     * Searches for a matching electrolysis recipe based on the current inventory and fluid state.
     * Returns null if no recipe matches or the level is unavailable.
     * */
    fun searchForRecipe(): AqueousElectrolysisRecipe? {
        val level = this.level ?: return null

        val container = buildRecipeContainer()

        return level.recipeManager.getRecipeFor(
            Eln2Processing.ELECTROLYSIS_RECIPE,
            container,
            level,
        ).orElse(null)
    }

    /**
     * Checks if there is space in the output tanks and item slots for the given recipe's outputs.
     * Gases are ignored (vented). Fluid space is checked via simulated fill; item space via simulated insertion.
     * */
    fun canExportOutputs(recipe: AqueousElectrolysisRecipe): Boolean {
        if (recipe.anodeOutputItem != null) {
            val remaining = inventoryHandler.insertItem(
                ElectrolysisInventoryHandler.ANODE_OUTPUT_SLOT,
                recipe.anodeOutputItem.copy(),
                true,
            )

            if (!remaining.isEmpty) {
                return false
            }
        }

        if (recipe.cathodeOutputItem != null) {
            val remaining = inventoryHandler.insertItem(
                ElectrolysisInventoryHandler.CATHODE_OUTPUT_SLOT,
                recipe.cathodeOutputItem.copy(),
                true,
            )

            if (!remaining.isEmpty) {
                return false
            }
        }

        val anodeSide = fluidHandler.anode
        val cathodeSide = if (inventoryHandler.hasSeparator()) fluidHandler.cathode else anodeSide

        when (recipe) {
            is NonSeparatedAqueousElectrolysisRecipe -> {
                recipe.outputFluid?.let { outputFluid ->
                    val accepted = anodeSide.outputLiquidTank.fillFractional(
                        FractionalFluidStack(outputFluid.fluid, outputFluid.amount.toDouble()),
                        IFluidHandler.FluidAction.SIMULATE,
                    )

                    if (accepted < outputFluid.amount.toDouble() - FractionalFluidStack.EPSILON) {
                        return false
                    }
                }
            }

            is SeparatedAqueousElectrolysisRecipe -> {
                recipe.anodeOutputFluid?.let { outputFluid ->
                    val accepted = anodeSide.outputLiquidTank.fillFractional(
                        FractionalFluidStack(outputFluid.fluid, outputFluid.amount.toDouble()),
                        IFluidHandler.FluidAction.SIMULATE,
                    )

                    if (accepted < outputFluid.amount.toDouble() - FractionalFluidStack.EPSILON) {
                        return false
                    }
                }

                recipe.cathodeOutputFluid?.let { outputFluid ->
                    val accepted = cathodeSide.outputLiquidTank.fillFractional(
                        FractionalFluidStack(outputFluid.fluid, outputFluid.amount.toDouble()),
                        IFluidHandler.FluidAction.SIMULATE,
                    )

                    if (accepted < outputFluid.amount.toDouble() - FractionalFluidStack.EPSILON) {
                        return false
                    }
                }
            }
        }

        return true
    }

    /**
     * Consumes the input fluids from the input liquid tanks. Electrodes and separator are catalysts and are not consumed.
     * Must only be called after [canExportOutputs] has verified space.
     * */
    fun consumeInputs(recipe: AqueousElectrolysisRecipe) {
        val anodeSide = fluidHandler.anode
        val cathodeSide = if (inventoryHandler.hasSeparator()) fluidHandler.cathode else anodeSide

        when (recipe) {
            is NonSeparatedAqueousElectrolysisRecipe -> {
                anodeSide.inputLiquidTank.drainFractional(
                    FractionalFluidStack(recipe.inputFluid.fluid, recipe.inputFluid.amount.toDouble()),
                    IFluidHandler.FluidAction.EXECUTE,
                )
            }

            is SeparatedAqueousElectrolysisRecipe -> {
                anodeSide.inputLiquidTank.drainFractional(
                    FractionalFluidStack(recipe.anodeInputFluid.fluid, recipe.anodeInputFluid.amount.toDouble()),
                    IFluidHandler.FluidAction.EXECUTE,
                )

                cathodeSide.inputLiquidTank.drainFractional(
                    FractionalFluidStack(recipe.cathodeInputFluid.fluid, recipe.cathodeInputFluid.amount.toDouble()),
                    IFluidHandler.FluidAction.EXECUTE,
                )
            }
        }

        setChanged()
    }

    /**
     * Places all outputs into the output item slots, output liquid tanks, and output gas tanks.
     * Gases that don't fit are silently vented. Must only be called after [canExportOutputs] has verified space for liquids and items.
     * */
    fun placeOutputs(recipe: AqueousElectrolysisRecipe) {
        recipe.anodeOutputItem?.let { output ->
            inventoryHandler.insertItem(ElectrolysisInventoryHandler.ANODE_OUTPUT_SLOT, output.copy(), false)
        }

        recipe.cathodeOutputItem?.let { output ->
            inventoryHandler.insertItem(ElectrolysisInventoryHandler.CATHODE_OUTPUT_SLOT, output.copy(), false)
        }

        val anodeSide = fluidHandler.anode
        val cathodeSide = if (inventoryHandler.hasSeparator()) fluidHandler.cathode else anodeSide

        when (recipe) {
            is NonSeparatedAqueousElectrolysisRecipe -> {
                recipe.outputFluid?.let { outputFluid ->
                    anodeSide.outputLiquidTank.fillFractional(
                        FractionalFluidStack(outputFluid.fluid, outputFluid.amount.toDouble()),
                        IFluidHandler.FluidAction.EXECUTE,
                    )
                }

                recipe.outputGas?.let { outputGas ->
                    anodeSide.outputGasTank.fillFractional(
                        FractionalFluidStack(outputGas.fluid, outputGas.amount.toDouble()),
                        IFluidHandler.FluidAction.EXECUTE,
                    )
                }
            }

            is SeparatedAqueousElectrolysisRecipe -> {
                recipe.anodeOutputFluid?.let { outputFluid ->
                    anodeSide.outputLiquidTank.fillFractional(
                        FractionalFluidStack(outputFluid.fluid, outputFluid.amount.toDouble()),
                        IFluidHandler.FluidAction.EXECUTE,
                    )
                }

                recipe.cathodeOutputFluid?.let { outputFluid ->
                    cathodeSide.outputLiquidTank.fillFractional(
                        FractionalFluidStack(outputFluid.fluid, outputFluid.amount.toDouble()),
                        IFluidHandler.FluidAction.EXECUTE,
                    )
                }

                recipe.anodeOutputGas?.let { outputGas ->
                    anodeSide.outputGasTank.fillFractional(
                        FractionalFluidStack(outputGas.fluid, outputGas.amount.toDouble()),
                        IFluidHandler.FluidAction.EXECUTE,
                    )
                }

                recipe.cathodeOutputGas?.let { outputGas ->
                    cathodeSide.outputGasTank.fillFractional(
                        FractionalFluidStack(outputGas.fluid, outputGas.amount.toDouble()),
                        IFluidHandler.FluidAction.EXECUTE,
                    )
                }
            }
        }

        setChanged()
    }

    @ServerOnly
    override fun setDestroyed() {
        destroyDelegates()
        super.setDestroyed()
    }

    //#region Recipe State

    private class Operation(val recipe: AqueousElectrolysisRecipe, var investedEnergy: Double)

    @ServerOnly
    private var operation: Operation? = null

    private data class OperationLoadingData(val operationId: ResourceLocation, val investedEnergy: Double)

    @ServerOnly
    private var savedOperationData: OperationLoadingData? = null

    @ServerOnly
    private var lastAnodeInputLiquidVersion = -1

    @ServerOnly
    private var lastCathodeInputLiquidVersion = -1

    //#endregion

    @ServerOnly
    @OnServerThread
    override fun onCellAcquired() {
        super.onCellAcquired()
        cell.bind()
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()

        if (hasCell) {
            cell.unbind()
        }
    }

    @ServerOnly
    fun serverTick() {
        val anodeVersion = fluidHandler.anode.inputLiquidTank.version
        val cathodeVersion = fluidHandler.cathode.inputLiquidTank.version

        val inventoryChanged = inventoryHandler.wasChanged()
        val inputLiquidChanged = anodeVersion != lastAnodeInputLiquidVersion ||
            cathodeVersion != lastCathodeInputLiquidVersion

        lastAnodeInputLiquidVersion = anodeVersion
        lastCathodeInputLiquidVersion = cathodeVersion

        val inputsChanged = inventoryChanged || inputLiquidChanged

        if (operation != null) {
            val op = operation!!
            val recipe = op.recipe

            if (inputsChanged) {
                val container = buildRecipeContainer()
                val stillMatches = recipe.matches(container, level!!)

                if (!stillMatches) {
                    val actualRecipe = searchForRecipe()

                    if (actualRecipe == null) {
                        operation = null
                    } else {
                        if (actualRecipe.recipeId != recipe.recipeId) {
                            operation = if (canExportOutputs(actualRecipe)) {
                                Operation(actualRecipe, 0.0)
                            } else {
                                null
                            }
                        }
                    }

                    setChanged()
                }
            }
        } else {
            if (inputsChanged) {
                val actualRecipe = searchForRecipe()

                if (actualRecipe != null && canExportOutputs(actualRecipe)) {
                    operation = Operation(actualRecipe, 0.0)
                    setChanged()
                }
            }
        }

        if (operation != null) {
            cell.isActive = true
            cell.processResistance = operation!!.recipe.resistance

            val op = operation!!
            val recipe = op.recipe

            val power = cell.lastPower

            if (power > 1e-3) {
                val energyThisTick = power / 20.0

                op.investedEnergy += energyThisTick

                setChanged()
            }

            data.progress = (op.investedEnergy / recipe.energyCost).toFloat().coerceIn(0.0f, 1.0f)

            if (op.investedEnergy >= recipe.energyCost) {
                consumeInputs(recipe)
                placeOutputs(recipe)
                operation = null
                inventoryHandler.markChanged()
                setChanged()
            }
        } else {
            cell.isActive = false
            cell.processResistance = 0.0
            data.progress = 0.0f
        }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        pTag.put("inventory", inventoryHandler.serializeNBT())
        pTag.put("fluids", fluidHandler.serializeNBT())

        if (operation != null) {
            pTag.putDouble("investedEnergy", operation!!.investedEnergy)
            pTag.putString("recipe", operation!!.recipe.recipeId.toString())
        }
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        inventoryHandler.deserializeNBT(pTag.getCompound("inventory"))
        fluidHandler.deserializeNBT(pTag.getCompound("fluids"))
        inventoryHandler.syncSeparatorState()

        if (pTag.contains("investedEnergy")) {
            savedOperationData = OperationLoadingData(
                ResourceLocation.parse(pTag.getString("recipe")),
                pTag.getDouble("investedEnergy"),
            )
        }
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (!pLevel.isClientSide && savedOperationData != null) {
            val optional = pLevel.recipeManager.byKey(savedOperationData!!.operationId)

            if (optional.isPresent && optional.get() is AqueousElectrolysisRecipe) {
                operation = Operation(optional.get() as AqueousElectrolysisRecipe, savedOperationData!!.investedEnergy)
            }

            savedOperationData = null
        }
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        inventoryHandlerLazy.invalidate()
        leftItemLazy.invalidate()
        rightItemLazy.invalidate()
        leftInputLiquidLazy.invalidate()
        rightInputLiquidLazy.invalidate()
        leftOutputLiquidLazy.invalidate()
        rightOutputLiquidLazy.invalidate()
        leftGasLazy.invalidate()
        rightGasLazy.invalidate()
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        if (!hasCell) {
            return
        }

        builder.debugInIDE { "Power: ${cell.lastPower.rounded()}" }
    }
}

class ElectrolysisMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: ProgressContainerData,
    val access: ContainerLevelAccess,
    val level: Level,
) : AbstractContainerMenu(Eln2Processing.ELECTROLYSIS_MENU.get(), pContainerId), ProgressSupplierMenu {
    companion object {
        private val ANODE_ELECTRODE_POS = Vector2di(45, 22)
        private val CATHODE_ELECTRODE_POS = Vector2di(96, 22)
        private val SEPARATOR_POS = Vector2di(70, 40)
        private val ANODE_OUTPUT_POS = Vector2di(45, 58)
        private val CATHODE_OUTPUT_POS = Vector2di(96, 58)
    }

    @ServerOnly
    constructor(entity: ElectrolysisMainBlockEntity, id: Int, inventory: Inventory) : this(
        id, inventory,
        entity.inventoryHandler,
        entity.data,
        ContainerLevelAccess.create(entity.level!!, entity.blockPos),
        entity.level!!,
    )

    constructor(pContainerId: Int, playerInventory: Inventory) : this(
        pContainerId, playerInventory,
        ItemStackHandler(ElectrolysisMainBlockEntity.ElectrolysisInventoryHandler.SLOT_COUNT),
        ProgressContainerData(),
        ContainerLevelAccess.NULL,
        playerInventory.player.level(),
    )

    init {
        addSlot(SlotItemHandlerWithPlacePredicate(handler, ElectrolysisMainBlockEntity.ElectrolysisInventoryHandler.ANODE_ELECTRODE_SLOT, ANODE_ELECTRODE_POS.x, ANODE_ELECTRODE_POS.y) {
            it.item is ElectrodeItem
        })

        addSlot(SlotItemHandlerWithPlacePredicate(handler, ElectrolysisMainBlockEntity.ElectrolysisInventoryHandler.CATHODE_ELECTRODE_SLOT, CATHODE_ELECTRODE_POS.x, CATHODE_ELECTRODE_POS.y) {
            it.item is ElectrodeItem
        })

        addSlot(SlotItemHandlerWithPlacePredicate(handler, ElectrolysisMainBlockEntity.ElectrolysisInventoryHandler.SEPARATOR_SLOT, SEPARATOR_POS.x, SEPARATOR_POS.y) {
            it.item is SeparatorItem
        })

        addSlot(SlotItemHandlerWithPlacePredicate(handler, ElectrolysisMainBlockEntity.ElectrolysisInventoryHandler.ANODE_OUTPUT_SLOT, ANODE_OUTPUT_POS.x, ANODE_OUTPUT_POS.y) { false })

        addSlot(SlotItemHandlerWithPlacePredicate(handler, ElectrolysisMainBlockEntity.ElectrolysisInventoryHandler.CATHODE_OUTPUT_SLOT, CATHODE_OUTPUT_POS.x, CATHODE_OUTPUT_POS.y) { false })

        addDataSlots(containerData)

        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) = stillValid(access, pPlayer, Eln2Processing.ELECTROLYSIS_MAIN_BLOCK.get())

    override fun quickMoveStack(pPlayer: Player, pIndex: Int) = ContainerHelper.quickMove(slots, pPlayer, pIndex)

    override fun getProgressForRender() = containerData.progress
}

