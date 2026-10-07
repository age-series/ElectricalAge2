@file:Suppress("unused")

package org.eln2.mc.common.content.processing

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.CriterionTriggerInstance
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.data.recipes.FinishedRecipe
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.level.Level
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
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.registries.ForgeRegistries
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.geometry.Vector2di
import org.ageseries.libage.mathematics.map
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Simulator
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.sim.electrical.ElectricalSimulation
import org.eln2.mc.*
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.blocks.foundation.UprightHorizontalDirectionCellBlock
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.containers.ContainerHelper
import org.eln2.mc.common.containers.MyAbstractContainerScreen
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicate
import org.eln2.mc.common.content.HeatingElementItem
import org.eln2.mc.common.content.modules.Eln2ForgeFluids
import org.eln2.mc.common.content.modules.Eln2HeatingElements
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.fluids.foundation.FractionalFluidStack
import org.eln2.mc.common.fluids.foundation.IFractionalFluidHandler
import org.eln2.mc.common.fluids.foundation.fractional
import org.eln2.mc.common.recipes.RecipeRegistry
import org.eln2.mc.common.recipes.foundation.*
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.BulkPacketHandlerBlockEntity
import org.eln2.mc.common.network.serverToClient.sendBulkPacket
import org.eln2.mc.common.sounds.foundation.SimpleLoopingBlockEntitySoundInstance
import org.eln2.mc.common.sounds.foundation.SoundInfo
import org.eln2.mc.common.sounds.foundation.SoundInstanceTickEvent
import org.ageseries.libage.mathematics.FramerateIndependentSmoother1d
import net.minecraft.sounds.SoundEvent
import net.minecraftforge.registries.RegistryObject
import java.util.function.Consumer
import kotlin.math.abs

private const val HYDROGEN_REDUCTION_INPUT_SLOT_COUNT = 4

class HydrogenReductionRecipe(
    override val recipeSerializer: Serializer,
    override val recipeId: ResourceLocation,
    val inputItems: Eln2WeightedItemRecipeRequirements,
    val output: ItemStack,
    val hydrogenAmount: Int,
    val minimumTemperature: Quantity<Temperature>,
    val energyCost: Double,
    val optimalTemperature: Quantity<Temperature>,
) : Eln2CustomRecipe<HydrogenReductionRecipe>, Eln2NonStandardRecipe {
    override fun matches(pContainer: SimpleContainer, pLevel: Level): Boolean {
        val items = pContainer.bindToList()
        return items.applyRecipeWeighted(inputItems, true)
    }

    class Builder(val recipe: RecipeType<HydrogenReductionRecipe>) {
        var inputItems: Eln2WeightedItemRecipeRequirements = Eln2WeightedItemRecipeRequirements(emptyList())
        var output: ItemStack = ItemStack.EMPTY
        var hydrogenAmount: Int = 0
        var minimumTemperature: Quantity<Temperature> = Quantity(800.0, CELSIUS)
        var energyCost: Double = 10.0
        var optimalTemperature: Quantity<Temperature> = Quantity(1000.0, CELSIUS)
        val advancement: Advancement.Builder = Advancement.Builder.advancement()

        fun withInput(ingredient: Eln2WeightedItemIngredient): Builder {
            inputItems = Eln2WeightedItemRecipeRequirements(
                inputItems.requirements + Eln2WeightedItemRecipeRequirement(
                    listOf(ingredient), ingredient.value
                )
            )
            return this
        }

        fun withInput(ingredient: Eln2WeightedItemIngredient, requiredValue: Int): Builder {
            inputItems = Eln2WeightedItemRecipeRequirements(
                inputItems.requirements + Eln2WeightedItemRecipeRequirement(
                    listOf(ingredient), requiredValue
                )
            )
            return this
        }

        fun withOutput(output: ItemStack): Builder {
            this.output = output
            return this
        }

        fun withHydrogenAmount(amount: Int): Builder {
            hydrogenAmount = amount
            return this
        }

        fun withMinimumTemperature(temperature: Quantity<Temperature>): Builder {
            minimumTemperature = temperature
            return this
        }

        /**
         * The process cost, in J/mB of Hydrogen.
         * */
        fun withEnergyCost(cost: Double): Builder {
            energyCost = cost
            return this
        }

        fun withOptimalTemperature(temperature: Quantity<Temperature>): Builder {
            optimalTemperature = temperature
            return this
        }

        fun unlockedBy(pCriterionName: String, pCriterionTrigger: CriterionTriggerInstance): Builder {
            advancement.addCriterion(pCriterionName, pCriterionTrigger)
            return this
        }

        fun save(consumer: Consumer<FinishedRecipe?>, id: ResourceLocation) {
            check(inputItems.requirements.isNotEmpty()) { "Input items for hydrogen reduction recipe cannot be empty" }
            check(!output.isEmpty) { "Output for hydrogen reduction recipe cannot be empty" }
            check(hydrogenAmount > 0) { "Hydrogen amount must be positive" }
            if (advancement.criteria.isEmpty()) {
                LOG.error("No criterion for hydrogen reduction recipe $id")
            } else {
                advancement.eln2Unlock(id)
            }
            consumer.accept(Result(this, id))
        }

        class Result(val parent: Builder, val recipeId: ResourceLocation) : Eln2FinishedRecipe {
            override fun serializeRecipeData(json: JsonObject) {
                json.add("inputItems", JsonArray().also { arr ->
                    parent.inputItems.requirements.forEach { req ->
                        arr.add(JsonObject().also { reqObj ->
                            reqObj.addProperty("requiredValue", req.requiredValue)
                            reqObj.add("options", JsonArray().also { opts ->
                                req.options.forEach { opt ->
                                    opts.add(JsonObject().also { optObj ->
                                        optObj.add("ingredient", opt.ingredient.toJson())
                                        optObj.addProperty("value", opt.value)
                                    })
                                }
                            })
                        })
                    }
                })

                json.add("result", JsonObject().also { resultJson ->
                    resultJson.addProperty("item", ForgeRegistries.ITEMS.getKey(parent.output.item)!!.toString())
                    resultJson.addProperty("count", parent.output.count)
                })

                json.addProperty("hydrogenAmount", parent.hydrogenAmount)
                json.addProperty("minimumTemperature", parent.minimumTemperature..CELSIUS)
                json.addProperty("energyCost", parent.energyCost)
                json.addProperty("optimalTemperature", parent.optimalTemperature..CELSIUS)
            }

            override fun getId(): ResourceLocation = recipeId
            override fun getType(): RecipeSerializer<*> = RecipeRegistry.getRecipeSerializer(parent.recipe)!!.get()
            override fun serializeAdvancement(): JsonObject = parent.advancement.serializeToJson()
        }
    }

    class Serializer(override val recipeType: RecipeType<HydrogenReductionRecipe>) : Eln2RecipeSerializer<HydrogenReductionRecipe> {
        override fun fromJson(pRecipeId: ResourceLocation, pSerializedRecipe: JsonObject): HydrogenReductionRecipe {
            val inputItems = Eln2WeightedItemRecipeRequirements.fromJson(pSerializedRecipe.get("inputItems"))
            val output = ShapedRecipe.itemStackFromJson(pSerializedRecipe.getAsJsonObject("result"))
            val hydrogenAmount = pSerializedRecipe.get("hydrogenAmount").asInt
            val minimumTemperature = Quantity(pSerializedRecipe.get("minimumTemperature").asDouble, CELSIUS)
            val energyCost = pSerializedRecipe.get("energyCost").asDouble
            val optimalTemperature = Quantity(pSerializedRecipe.get("optimalTemperature").asDouble, CELSIUS)

            return HydrogenReductionRecipe(
                this, pRecipeId,
                inputItems, output,
                hydrogenAmount,
                minimumTemperature,
                energyCost,
                optimalTemperature
            )
        }

        override fun fromNetwork(pRecipeId: ResourceLocation, pBuffer: FriendlyByteBuf): HydrogenReductionRecipe {
            val inputItems = Eln2WeightedItemRecipeRequirements.fromNetwork(pBuffer)
            val output = pBuffer.readItem()
            val hydrogenAmount = pBuffer.readInt()
            val minimumTemperature = Quantity(pBuffer.readDouble(), CELSIUS)
            val energyCost = pBuffer.readDouble()
            val optimalTemperature = Quantity(pBuffer.readDouble(), CELSIUS)
            return HydrogenReductionRecipe(this, pRecipeId, inputItems, output, hydrogenAmount, minimumTemperature, energyCost, optimalTemperature)
        }

        override fun toNetwork(pBuffer: FriendlyByteBuf, pRecipe: HydrogenReductionRecipe) {
            pRecipe.inputItems.toNetwork(pBuffer)
            pBuffer.writeItem(pRecipe.output)
            pBuffer.writeInt(pRecipe.hydrogenAmount)
            pBuffer.writeDouble(!pRecipe.minimumTemperature)
            pBuffer.writeDouble(pRecipe.energyCost)
            pBuffer.writeDouble(!pRecipe.optimalTemperature)
        }
    }
}

/**
 * Cell for a generalized electrical furnace.
 *
 * Owns an insulated thermal body and an electrical resistor.
 * The resistance is driven by the installed [HeatingElementItem]'s **ρ(T)** curve and wire geometry.
 *
 * Lifecycle:
 *  - [bind] is called by the block entity when the cell is acquired.
 *  - [unbind] is called when the chunk unloads or the block is removed.
 *  - While unbound, the resistor is set to [ElectricalSimulation.MAX_RESISTANCE].
 *  - The block entity sets [isActive] and [heatingElementItem] from the server thread.
 */
class ElectricalFurnaceCell(
    ci: CellCreateInfo,
    override val electricalMap: PoleMap,
    thermalMassDef: ThermalMassDefinition,
    val leakageParameters: ConnectionParameters,
    maxBreakdownTemperature: Quantity<Temperature>,
) : Cell(ci), SidedElectricalMapped<ElectricalFurnaceCell> {
    companion object {
        private const val TEMPERATURE_TAG = "temperature"
    }

    override val electricalSize: ElectricalSize
        get() = ElectricalSize.Any

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

    /**
     * True when the cell is wired to a living block entity.
     * Set by [bind] / [unbind].
     */
    @CrossThreadAccess
    var isBound: Boolean = false
        private set

    /**
     * Called by the block entity when the cell is acquired (block placed / world loaded).
     */
    @OnServerThread
    fun bind() {
        isBound = true
    }

    /**
     * Called by the block entity when the cell is released (block broken / chunk unloaded).
     */
    @OnServerThread
    fun unbind() {
        isBound = false
    }

    /**
     * Whether the furnace should draw power and heat up.
     * Set by the block entity on the server thread.
     */
    @CrossThreadAccess
    @OnServerThread
    var isActive: Boolean = false

    /**
     * Maximum process thermal power drain at optimal temperature.
     */
    @CrossThreadAccess
    @OnServerThread
    var processMaxPower: Double = 0.0

    /**
     * Minimum temperature for processing, in K.
     */
    @CrossThreadAccess
    @OnServerThread
    var processMinTemperature: Double = 0.0

    /**
     * Optimal temperature for full processing speed, in K.
     */
    @CrossThreadAccess
    @OnServerThread
    var processOptimalTemperature: Double = 0.0

    /**
     * The currently installed heating element.
     * Null when empty or burnt out. Set by the block entity.
     */
    @CrossThreadAccess
    @OnServerThread
    var heatingElementItem: HeatingElementItem? = null

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

        if (processMaxPower > 0.0 && processOptimalTemperature > processMinTemperature) {
            val t = !thermalBody.temperature
            val factor = map(t, processMinTemperature, processOptimalTemperature, 0.0, 1.0).coerceIn(0.0, 1.0)
            val drain = processMaxPower * factor * dt

            if (drain > 0.0) {
                thermalBody.energy -= Quantity(drain, JOULE)
            }
        }

        if (!isBound) {
            resistor.component.updateResistance(ElectricalSimulation.MAX_RESISTANCE)
            return
        }

        val element = heatingElementItem

        if (element == null || !isActive) {
            resistor.component.updateResistance(ElectricalSimulation.MAX_RESISTANCE)
            return
        }

        val targetResistance = element.resistanceAt(thermalBody.temperature)
        resistor.component.updateResistance(targetResistance, 1e-4)

        val delta = abs(resistor.component.power) * dt

        if (delta > 1e-3) {
            thermalBody.energy += delta
            setChanged()
        }
    }
}

class HydrogenReductionFurnaceBlock : UprightHorizontalDirectionCellBlock<ElectricalFurnaceCell>() {
    override fun getCellProvider(): CellProvider<ElectricalFurnaceCell> {
        return Eln2Processing.HYDROGEN_REDUCTION_FURNACE_CELL.get()
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return HydrogenReductionFurnaceBlockEntity(pPos, pState)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T> {
        return BlockEntityTicker(HydrogenReductionFurnaceBlockEntity::tick)
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
        return pLevel.constructMenuHelper2(pPos, pPlayer, Component.translatable("menu.$MODID.hydrogen_reduction_furnace"), ::HydrogenReductionFurnaceMenu)
    }
}

class HydrogenReductionFurnaceBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity<ElectricalFurnaceCell>(pos, state, BlockRegistry.getBlockEntityType(state.block).get()),
    BulkPacketHandlerBlockEntity,
    ComponentDisplay {

    companion object {
        /**
         * The maximum hydrogen input rate, in mB/tick.
         * This defines the maximum processing speed of the machine.
         * */
        private const val HYDROGEN_FLOW_RATE = 2.5
        private const val NOMINAL_POWER = 500.0

        fun tick(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pLevel == null || pBlockEntity == null) {
                return
            }

            if (pBlockEntity !is HydrogenReductionFurnaceBlockEntity) {
                LOG.error(DEBUGGER_BREAK("Got $pBlockEntity instead of hydrogen reduction furnace"))
                return
            }

            if (pLevel.isClientSide) {
                pBlockEntity.clientTick()
            } else {
                pBlockEntity.serverTick()
            }
        }
    }

    @ServerOnly
    private var lastSentPower = Double.NaN

    @ClientOnly
    class RenderState {
        var targetPower = 0.0
        val powerSmoother = FramerateIndependentSmoother1d(0.25)
        var soundInstance: SimpleLoopingBlockEntitySoundInstance<HydrogenReductionFurnaceBlockEntity>? = null
    }

    @ClientOnly
    var renderState: RenderState? = null
        private set

    @ClientOnly
    override val clientSidePacketHandlerLazy = createClientSideHandler()

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        handler.withHandler<PowerPacket>(PowerPacket::deserialize) {
            renderState?.targetPower = it.power
        }
    }

    @ClientOnly
    fun clientTick() {
        val state = renderState ?: return

        if (state.soundInstance == null) {
            state.soundInstance = SimpleLoopingBlockEntitySoundInstance(this, Eln2Processing.HYDROGEN_REDUCTION_FURNACE_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    state.powerSmoother.update(state.targetPower)
                    it.soundInfo = SoundInfo.electromagnetic(
                        state.powerSmoother.value,
                        NOMINAL_POWER
                    )
                }

                it.registerOnAudioManager()
            }
        }
    }

    @ServerOnly
    override fun getUpdateTag(): CompoundTag {
        if (hasCell) {
            val currentPower = !cell.resistor.component.readouts.power
            lastSentPower = currentPower
            sendBulkPacket(PowerPacket::serialize, PowerPacket(currentPower))
        }

        return super.getUpdateTag()
    }

    data class PowerPacket(val power: Double) {
        companion object {
            fun serialize(packet: PowerPacket, buffer: FriendlyByteBuf) {
                buffer.writeDouble(packet.power)
            }

            fun deserialize(buffer: FriendlyByteBuf) = PowerPacket(
                buffer.readDouble()
            )
        }
    }
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

    class HydrogenFurnaceData : SimpleContainerData(4) {
        companion object {
            private const val TEMPERATURE = 0
            private const val PROGRESS = 1
            private const val MAX_TEMPERATURE = 2
            private const val RECIPE_TEMPERATURE = 3
        }

        var temperature: Int
            get() = this.get(TEMPERATURE)
            set(value) { this.set(TEMPERATURE, value) }

        var progress: Double
            get() = (this.get(PROGRESS) / 16384.0).coerceIn(0.0, 1.0)
            set(value) { this.set(PROGRESS, (value * 16384).toInt().coerceIn(0, 16384)) }

        var maxTemperature: Int
            get() = this.get(MAX_TEMPERATURE)
            set(value) { this.set(MAX_TEMPERATURE, value) }

        var recipeTemperature: Int
            get() = this.get(RECIPE_TEMPERATURE)
            set(value) { this.set(RECIPE_TEMPERATURE, value) }
    }

    //#region Capability

    class InventoryHandler(val blockEntity: HydrogenReductionFurnaceBlockEntity) : ItemStackHandler(HYDROGEN_REDUCTION_INPUT_SLOT_COUNT + 2) {
        companion object {
            const val ELEMENT_SLOT = 4
            const val OUTPUT_SLOT = 5
        }

        private val inputSlots = 0 until HYDROGEN_REDUCTION_INPUT_SLOT_COUNT
        private var isChanged = false

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if (slot == OUTPUT_SLOT) {
                return stack
            }

            return super.insertItem(slot, stack, simulate)
        }

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            if (slot == ELEMENT_SLOT) {
                return stack.item is HeatingElementItem
            }

            return true
        }

        override fun onContentsChanged(slot: Int) {
            if (slot in inputSlots || slot == OUTPUT_SLOT) {
                isChanged = true
            }

            blockEntity.setChanged()
        }

        fun wasChanged(): Boolean {
            if (isChanged) {
                isChanged = false
                return true
            }

            return false
        }

        fun markChanged() {
            isChanged = true
        }

        fun buildInputContainer(): SimpleContainer {
            val container = SimpleContainer(HYDROGEN_REDUCTION_INPUT_SLOT_COUNT)

            for (i in 0 until HYDROGEN_REDUCTION_INPUT_SLOT_COUNT) {
                container.setItem(i, getStackInSlot(i))
            }

            return container
        }

        fun hasSpaceForOutput(stack: ItemStack): Boolean {
            return super.insertItem(OUTPUT_SLOT, stack.copy(), true).isEmpty
        }

        fun consumeInputs(recipe: HydrogenReductionRecipe) {
            check(stacks.take(HYDROGEN_REDUCTION_INPUT_SLOT_COUNT).applyRecipeWeighted(recipe.inputItems, true))
        }

        fun placeOutput(stack: ItemStack) {
            check(super.insertItem(OUTPUT_SLOT, stack, false).isEmpty) {
                DEBUGGER_BREAK()
            }
        }

        fun searchForRecipe(): HydrogenReductionRecipe? {
            val level = blockEntity.level
                ?: return null

            val container = buildInputContainer()

            return level.recipeManager.getRecipeFor(Eln2Processing.HYDROGEN_REDUCTION_RECIPE, container, level).orElse(null)
        }

        fun getElement(): HeatingElementItem? {
            val stack = getStackInSlot(ELEMENT_SLOT)

            return stack.item as? HeatingElementItem
        }

        fun setBurntElement() {
            if(getElement() != null) {
                setStackInSlot(ELEMENT_SLOT, ItemStack(Eln2HeatingElements.BURNT_HEATING_ELEMENT.get()))
            }
        }
    }

    val inventoryHandler = InventoryHandler(this)
    val inventoryHandlerLazy: LazyOptional<InventoryHandler> = LazyOptional.of { inventoryHandler }
    val data = HydrogenFurnaceData()

    class HydrogenFluidHandler(val capacity: Double) : IFractionalFluidHandler {
        private val hydrogenFluid: Fluid get() = Eln2ForgeFluids.HYDROGEN.get()

        /**
         * mB of hydrogen in the tank.
         * */
        var hydrogen = 0.0

        val remainingCapacity: Double get() = (capacity - hydrogen).coerceAtLeast(0.0)

        override fun getTanks(): Int = 1
        override fun getFractionalFluidInTank(tank: Int) = FractionalFluidStack(hydrogenFluid, hydrogen)
        override fun getFractionalTankCapacity(tank: Int) = capacity
        override fun getFluidInTank(tank: Int): FluidStack = getFractionalFluidInTank(tank).quantized()
        override fun getTankCapacity(tank: Int): Int = capacity.toInt()
        override fun isFluidValid(tank: Int, stack: FluidStack) = stack.fluid == hydrogenFluid

        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): Double {
            if (resource.isEmpty || resource.fluid != hydrogenFluid) {
                return 0.0
            }

            val accepted = resource.amount.coerceAtMost(remainingCapacity)
            if (accepted < FractionalFluidStack.EPSILON) {
                return 0.0
            }

            if (action == IFluidHandler.FluidAction.EXECUTE) {
                hydrogen += accepted
            }

            return accepted
        }

        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int {
            if (resource.isEmpty) {
                return 0
            }

            val fractional = resource.fractional()
            val simulated = fillFractional(fractional, IFluidHandler.FluidAction.SIMULATE)
            val quantized = fractional.copyWithAmount(simulated).quantized()

            if (quantized.isEmpty) {
                return 0
            }

            if (action == IFluidHandler.FluidAction.SIMULATE) {
                return quantized.amount
            }

            fillFractional(quantized.fractional(), IFluidHandler.FluidAction.EXECUTE)
            return quantized.amount
        }

        override fun drainFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY
        override fun drainFractional(maxDrain: Double, action: IFluidHandler.FluidAction) = FractionalFluidStack.EMPTY
        override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY
        override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack = FluidStack.EMPTY
    }

    val tank = HydrogenFluidHandler(1000.0)
    val tankLazy: LazyOptional<HydrogenFluidHandler> = LazyOptional.of { tank }

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryHandlerLazy.cast()
        }

        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return tankLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        inventoryHandlerLazy.invalidate()
        tankLazy.invalidate()
    }

    //#endregion

    private class Operation(val recipe: HydrogenReductionRecipe, var investedHydrogen: Double)

    @ServerOnly
    private var operation: Operation? = null

    private data class OperationLoadingData(val operationId: ResourceLocation, val investedHydrogen: Double)

    @ServerOnly
    private var savedOperationData: OperationLoadingData? = null

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        pTag.put("inventory", inventoryHandler.serializeNBT())
        pTag.putDouble("hydrogen", tank.hydrogen)

        if (operation != null) {
            pTag.putDouble("investedHydrogen", operation!!.investedHydrogen)
            pTag.putString("recipe", operation!!.recipe.recipeId.toString())
        }
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        inventoryHandler.deserializeNBT(pTag.getCompound("inventory"))
        tank.hydrogen = pTag.getDouble("hydrogen")

        if (pTag.contains("investedHydrogen")) {
            savedOperationData = OperationLoadingData(
                ResourceLocation.parse(pTag.getString("recipe")),
                pTag.getDouble("investedHydrogen")
            )
        }
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (pLevel.isClientSide) {
            renderState = RenderState()
        }
        else if (savedOperationData != null) {
            val optional = pLevel.recipeManager.byKey(savedOperationData!!.operationId)

            if (optional.isPresent && optional.get() is HydrogenReductionRecipe) {
                operation = Operation(optional.get() as HydrogenReductionRecipe, savedOperationData!!.investedHydrogen)
            }

            savedOperationData = null
        }
    }

    @ServerOnly
    fun serverTick() {
        val element = inventoryHandler.getElement()
        cell.heatingElementItem = element
        val temperature = cell.thermalBody.temperature
        data.temperature = (!temperature).toInt()

        data.maxTemperature = element?.let { (!it.maxTemperature).toInt() } ?: 0
        data.recipeTemperature = operation?.recipe?.let { (!it.minimumTemperature).toInt() } ?: 0

        if (inventoryHandler.wasChanged()) {
            val actualRecipe = inventoryHandler.searchForRecipe()

            if (actualRecipe == null) {
                operation = null
            }
            else {
                if (actualRecipe.recipeId != operation?.recipe?.recipeId) {
                    operation = if (inventoryHandler.hasSpaceForOutput(actualRecipe.output.copy())) {
                        Operation(actualRecipe, 0.0)
                    }
                    else {
                        null
                    }
                }
            }

            setChanged()
        }

        if (operation != null && element != null) {
            if (element.isExceedingMaxTemperature(temperature)) {
                inventoryHandler.setBurntElement()
                setChanged()
            }
        }

        if (operation != null) {
            cell.isActive = true

            val op = operation!!
            val recipe = op.recipe

            cell.processMaxPower = HYDROGEN_FLOW_RATE * 20.0 * recipe.energyCost
            cell.processMinTemperature = !recipe.minimumTemperature
            cell.processOptimalTemperature = !recipe.optimalTemperature

            val factor = if (!recipe.optimalTemperature > !recipe.minimumTemperature) {
                map(!temperature, !recipe.minimumTemperature, !recipe.optimalTemperature, 0.0, 1.0).coerceIn(0.0, 1.0)
            }
            else {
                if (!temperature >= !recipe.minimumTemperature) 1.0 else 0.0
            }

            if (factor > 0.0) {
                val hydrogenToDrain = minOf(
                    HYDROGEN_FLOW_RATE * factor,
                    recipe.hydrogenAmount.toDouble() - op.investedHydrogen,
                    tank.hydrogen,
                )

                if (hydrogenToDrain > 0.0) {
                    tank.hydrogen -= hydrogenToDrain
                    op.investedHydrogen += hydrogenToDrain
                    setChanged()
                }

                data.progress = (op.investedHydrogen / recipe.hydrogenAmount.toDouble()).coerceIn(0.0, 1.0)

                if (op.investedHydrogen >= recipe.hydrogenAmount.toDouble()) {
                    inventoryHandler.consumeInputs(recipe)
                    inventoryHandler.placeOutput(recipe.output.copy())
                    inventoryHandler.markChanged()
                    operation = null
                    setChanged()
                }
            }
        }
        else {
            data.progress = 0.0
            cell.isActive = false
            cell.processMaxPower = 0.0
        }

        val currentPower = !cell.resistor.component.readouts.power

        if (!currentPower.approxEq(lastSentPower, 1.0)) {
            lastSentPower = currentPower
            sendBulkPacket(PowerPacket::serialize, PowerPacket(currentPower))
        }
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        if (!hasCell) return
        builder.quantity(cell.thermalBody.temperature)
        builder.quantityInput(cell.resistor.component.readouts.power)
    }
}

class HydrogenReductionFurnaceMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: HydrogenReductionFurnaceBlockEntity.HydrogenFurnaceData,
    val access: ContainerLevelAccess,
    val level: Level,
) : AbstractContainerMenu(Eln2Processing.HYDROGEN_REDUCTION_FURNACE_MENU.get(), pContainerId) {
    @ServerOnly
    constructor(entity: HydrogenReductionFurnaceBlockEntity, id: Int, inventory: Inventory) : this(
        id, inventory,
        entity.inventoryHandler,
        entity.data,
        ContainerLevelAccess.create(entity.level!!, entity.blockPos),
        entity.level!!
    )

    constructor(pContainerId: Int, playerInventory: Inventory) : this(
        pContainerId, playerInventory,
        ItemStackHandler(HYDROGEN_REDUCTION_INPUT_SLOT_COUNT + 2),
        HydrogenReductionFurnaceBlockEntity.HydrogenFurnaceData(),
        ContainerLevelAccess.NULL,
        playerInventory.player.level()
    )

    init {
        addSlot(SlotItemHandlerWithPlacePredicate(handler, 0, 30, 17) { it.item !is HeatingElementItem })
        addSlot(SlotItemHandlerWithPlacePredicate(handler, 1, 52, 17) { it.item !is HeatingElementItem })
        addSlot(SlotItemHandlerWithPlacePredicate(handler, 2, 30, 39) { it.item !is HeatingElementItem })
        addSlot(SlotItemHandlerWithPlacePredicate(handler, 3, 52, 39) { it.item !is HeatingElementItem })

        addSlot(SlotItemHandlerWithPlacePredicate(handler, HydrogenReductionFurnaceBlockEntity.InventoryHandler.ELEMENT_SLOT, 76, 53) {
            it.item is HeatingElementItem
        })

        addSlot(SlotItemHandlerWithPlacePredicate(handler, HydrogenReductionFurnaceBlockEntity.InventoryHandler.OUTPUT_SLOT, 116, 35) { false })

        addDataSlots(containerData)

        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) = stillValid(access, pPlayer, Eln2Processing.HYDROGEN_REDUCTION_FURNACE_BLOCK.block.get())

    override fun quickMoveStack(pPlayer: Player, pIndex: Int) = ContainerHelper.quickMove(slots, pPlayer, pIndex)
}

class HydrogenReductionFurnaceScreen(menu: HydrogenReductionFurnaceMenu, playerInventory: Inventory, title: Component) :
    MyAbstractContainerScreen<HydrogenReductionFurnaceMenu>(menu, playerInventory, title) {

    companion object {
        private val TEXTURE = resource("textures/gui/container/furnace.png")

        private val INDICATOR_POS = Vector2di(13, 28)
        private const val INDICATOR_HEIGHT = 57 - 28
        private const val INDICATOR_WIDTH = 21 - 13

        private val PROGRESS_ARROW_POS = Vector2di(79, 34)
        private val PROGRESS_UV_POS = Vector2di(176, 14)
        private val PROGRESS_UV_SIZE = Vector2di(24, 16)
    }

    private fun renderIndicator(pGuiGraphics: GuiGraphics) {
        val data = menu.containerData

        if(data.maxTemperature == 0) {
            return
        }

        val position = INDICATOR_POS
        val cold = MyColor(100, 32, 195, 208)
        val hot = MyColor(100, 255, 45, 0)

        val barLeft = leftPos + position.x
        val barTop = topPos + position.y
        val barWidth = INDICATOR_WIDTH
        val barHeight = INDICATOR_HEIGHT

        // Full cold bar as background:
        pGuiGraphics.fill(
            barLeft, barTop,
            barLeft + barWidth, barTop + barHeight,
            cold.data
        )

        // Temperature fill:
        val maxTemp = data.maxTemperature
        val fillProgress = (data.temperature.toDouble() / maxTemp.toDouble()).coerceIn(0.0, 1.0)
        val fillHeight = (fillProgress * barHeight).toInt().coerceIn(0, barHeight)

        if (fillHeight > 0) {
            pGuiGraphics.fillGradient(
                barLeft,
                barTop + barHeight - fillHeight,
                barLeft + barWidth,
                barTop + barHeight,
                MyColor.lerp(cold, hot, fillProgress.toFloat()).data,
                cold.data
            )
        }

        // Recipe temperature marker:
        if (data.recipeTemperature > 0) {
            val recipeFraction = (data.recipeTemperature.toDouble() / maxTemp.toDouble()).coerceIn(0.0, 1.0)
            val markerY = (barTop + barHeight - (recipeFraction * barHeight).toInt()).coerceIn(barTop, barTop + barHeight)

            pGuiGraphics.fill(
                barLeft - 1,
                markerY - 1,
                barLeft + barWidth + 1,
                markerY + 1,
                MyColor(200, 25, 25, 30).data
            )
        }
    }

    private fun renderProgressArrow(pGuiGraphics: GuiGraphics) {
        pGuiGraphics.blit(
            TEXTURE,
            leftPos + PROGRESS_ARROW_POS.x,
            topPos + PROGRESS_ARROW_POS.y,
            PROGRESS_UV_POS.x.toFloat(),
            PROGRESS_UV_POS.y.toFloat(),
            map(
                menu.containerData.progress.toFloat(),
                0f,
                1f,
                0f,
                PROGRESS_UV_SIZE.x.toFloat()
            ).toInt(),
            PROGRESS_UV_SIZE.y,
            256,
            256
        )
    }

    override fun render(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick)
        renderIndicator(pGuiGraphics)
        renderProgressArrow(pGuiGraphics)
    }

    override fun renderBg(pGuiGraphics: GuiGraphics, pPartialTick: Float, pMouseX: Int, pMouseY: Int) {
        blitHelper(pGuiGraphics, TEXTURE)
    }
}
