@file:Suppress("unused")

package org.eln2.mc.common.content.processing

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.CriterionTriggerInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.data.recipes.FinishedRecipe
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.GsonHelper
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler
import net.minecraftforge.registries.ForgeRegistries
import org.ageseries.libage.data.OptionalDouble
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.Temperature
import org.ageseries.libage.data.put
import org.ageseries.libage.mathematics.geometry.Vector2di
import org.ageseries.libage.mathematics.map
import org.eln2.mc.*
import org.eln2.mc.client.screens.ProgressSupplierMenu
import org.eln2.mc.common.cells.foundation.CellEnvironment
import org.eln2.mc.common.containers.ContainerHelper
import org.eln2.mc.common.containers.ProgressContainerData
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicate
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.fluids.foundation.*
import org.eln2.mc.common.recipes.RecipeRegistry
import org.eln2.mc.common.recipes.foundation.*
import org.eln2.mc.extensions.and
import org.eln2.mc.extensions.asFluidStack
import org.eln2.mc.extensions.bindToList
import org.eln2.mc.extensions.bindToSimpleContainer
import org.eln2.mc.extensions.constructMenuHelper2
import org.eln2.mc.extensions.eln2Unlock
import org.eln2.mc.extensions.getResourceLocation
import org.eln2.mc.extensions.putResourceLocation
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.joml.SimplexNoise
import java.util.*
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull

private const val BURNER_INPUT_SLOT_COUNT = 4
private const val BURNER_OUTPUT_SLOT = BURNER_INPUT_SLOT_COUNT
private const val BURNER_SLOT_COUNT = BURNER_INPUT_SLOT_COUNT + 1

//#region Recipe

/**
 * Recipe for the generalized burner reactor.
 *
 * Consumes up to [BURNER_INPUT_SLOT_COUNT] distinct input items over a fixed [duration], producing at most one output fluid and/or one output item.
 * The fluid output is fractional and thermal.
 *
 * The machine is a dumb furnace ignited with flint and steel.
 * While burning, the output gas temperature is noised within [minTemperature], [maxTemperature] (same approach as the coke oven).
 *
 * @param inputItems Weighted item requirements for up to 4 input slots.
 * @param outputItem Optional single item left in the output slot after the operation completes.
 * @param outputFluid Optional fluid produced over the full operation.
 * @param minTemperature Lower bound for the output gas temperature noise (Kelvin).
 * @param maxTemperature Upper bound for the output gas temperature noise (Kelvin).
 * @param duration Total ticks for one full operation.
 */
class BurningRecipe(
    override val recipeSerializer: Serializer,
    override val recipeId: ResourceLocation,
    val inputItems: Eln2WeightedItemRecipeRequirements,
    val outputItem: ItemStack?,
    val outputFluid: FluidStack?,
    val minTemperature: Double,
    val maxTemperature: Double,
    val duration: Int
) : Eln2CustomRecipe<BurningRecipe>, Eln2NonStandardRecipe {
    init {
        require(inputItems.requirements.size <= BURNER_INPUT_SLOT_COUNT) {
            "Burning recipe \"$recipeId\" has more input items than is allowed ($BURNER_INPUT_SLOT_COUNT)"
        }
    }

    override fun matches(pContainer: SimpleContainer, pLevel: Level): Boolean {
        val items = pContainer.bindToList().take(BURNER_INPUT_SLOT_COUNT)
        return items.applyRecipeWeighted(inputItems, true)
    }

    class Builder(val recipe: RecipeType<BurningRecipe>) {
        var inputItems: Eln2WeightedItemRecipeRequirements = Eln2WeightedItemRecipeRequirements(emptyList())
        var outputItem: ItemStack? = null
        var outputFluid: FluidStack? = null
        var minTemperature: Double = 300.0
        var maxTemperature: Double = 500.0
        var duration: Int = 200
        val advancement: Advancement.Builder = Advancement.Builder.advancement()

        fun withInput(options: List<Pair<Ingredient, Int>>, value: Int = 1): Builder {
            val input = Eln2WeightedItemRecipeRequirement(options.map { Eln2WeightedItemIngredient(it.first, it.second) }, value)

            inputItems = Eln2WeightedItemRecipeRequirements(
                inputItems.requirements + input
            )

            return this
        }

        fun withInput(ingredient: Eln2WeightedItemIngredient): Builder {
            inputItems = Eln2WeightedItemRecipeRequirements(
                inputItems.requirements + Eln2WeightedItemRecipeRequirement(
                    listOf(ingredient), ingredient.value
                )
            )

            return this
        }

        fun withInput(ingredient: Eln2WeightedItemIngredient, requiredValue: Int = 1): Builder {
            inputItems = Eln2WeightedItemRecipeRequirements(
                inputItems.requirements + Eln2WeightedItemRecipeRequirement(
                    listOf(ingredient), requiredValue
                )
            )

            return this
        }

        fun withOutputItem(output: ItemStack): Builder {
            this.outputItem = output
            return this
        }

        fun withOutputFluid(fluid: FluidStack): Builder {
            this.outputFluid = fluid
            return this
        }

        fun withTemperatureRange(min: Double, max: Double): Builder {
            this.minTemperature = min
            this.maxTemperature = max
            return this
        }

        fun withDuration(duration: Int): Builder {
            this.duration = duration
            return this
        }

        fun unlockedBy(pCriterionName: String, pCriterionTrigger: CriterionTriggerInstance): Builder {
            advancement.addCriterion(pCriterionName, pCriterionTrigger)
            return this
        }

        fun save(consumer: Consumer<FinishedRecipe?>, id: ResourceLocation) {
            check(inputItems.requirements.isNotEmpty()) { "Input items for burning recipe cannot be empty" }
            check(outputItem != null || outputFluid != null) { "Burning recipe must have at least one of outputItem or outputFluid" }
            if (advancement.criteria.isEmpty()) {
                LOG.error("No criterion for burning recipe $id")
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

                parent.outputItem?.let { stack ->
                    json.add("solid_output", JsonObject().also { solidObj ->
                        solidObj.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.item)!!.toString())
                        solidObj.addProperty("count", stack.count)
                    })
                }

                parent.outputFluid?.let { fluid ->
                    json.add("gas_output", JsonObject().also { gasObj ->
                        gasObj.addProperty("fluid", ForgeRegistries.FLUIDS.getKey(fluid.fluid)!!.toString())
                        gasObj.addProperty("amount", fluid.amount)
                    })
                }

                json.addProperty("minTemperature", parent.minTemperature)
                json.addProperty("maxTemperature", parent.maxTemperature)
                json.addProperty("duration", parent.duration)
            }

            override fun getId(): ResourceLocation = recipeId
            override fun getType(): RecipeSerializer<*> = RecipeRegistry.getRecipeSerializer(parent.recipe)!!.get()
            override fun serializeAdvancement(): JsonObject = parent.advancement.serializeToJson()
        }
    }

    class Serializer(override val recipeType: RecipeType<BurningRecipe>) : Eln2RecipeSerializer<BurningRecipe> {
        override fun fromJson(pRecipeId: ResourceLocation, pSerializedRecipe: JsonObject): BurningRecipe {
            val inputItems = Eln2WeightedItemRecipeRequirements.fromJson(pSerializedRecipe.get("inputItems"))

            val outputItem = if (pSerializedRecipe.has("solid_output")) {
                ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "solid_output"))
            } else {
                null
            }

            val outputFluid = if(pSerializedRecipe.has("gas_output")) {
                pSerializedRecipe.getAsJsonObject("gas_output").asFluidStack()
            } else {
                null
            }

            val minTemperature = pSerializedRecipe.getAsJsonPrimitive("minTemperature").asDouble
            val maxTemperature = pSerializedRecipe.getAsJsonPrimitive("maxTemperature").asDouble
            val duration = pSerializedRecipe.getAsJsonPrimitive("duration").asInt

            return BurningRecipe(
                this, pRecipeId,
                inputItems, outputItem, outputFluid,
                minTemperature, maxTemperature, duration
            )
        }

        override fun fromNetwork(pRecipeId: ResourceLocation, pBuffer: FriendlyByteBuf): BurningRecipe {
            val inputItems = Eln2WeightedItemRecipeRequirements.fromNetwork(pBuffer)

            val outputItem = pBuffer.readNullable {
                pBuffer.readItem()
            }

            val outputFluid = pBuffer.readNullable {
                pBuffer.readFluidStack()
            }

            val minTemperature = pBuffer.readDouble()
            val maxTemperature = pBuffer.readDouble()
            val duration = pBuffer.readInt()

            return BurningRecipe(
                this, pRecipeId,
                inputItems, outputItem, outputFluid,
                minTemperature, maxTemperature, duration
            )
        }

        override fun toNetwork(pBuffer: FriendlyByteBuf, pRecipe: BurningRecipe) {
            pRecipe.inputItems.toNetwork(pBuffer)

            pBuffer.writeNullable(pRecipe.outputItem) { _, stack ->
                pBuffer.writeItem(stack)
            }

            pBuffer.writeNullable(pRecipe.outputFluid) { _, fluid ->
                pBuffer.writeFluidStack(fluid)
            }

            pBuffer.writeDouble(pRecipe.minTemperature)
            pBuffer.writeDouble(pRecipe.maxTemperature)
            pBuffer.writeInt(pRecipe.duration)
        }
    }
}

//#endregion

//#region Block

class BurnerBlock : HorizontalDirectionalBlock(Properties.of().strength(3.5f).requiresCorrectToolForDrops()), EntityBlock {
    companion object {
        val LIT: BooleanProperty = BooleanProperty.create("lit")
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false))
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        pBuilder.add(FACING, LIT)
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity = BurnerBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(pLevel: Level, pState: BlockState, pBlockEntityType: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (pLevel.isClientSide) return null
        return BlockEntityTicker(BurnerBlockEntity::tickServer)
    }

    @Deprecated("Deprecated in Java")
    override fun use(pState: BlockState, pLevel: Level, pPos: BlockPos, pPlayer: Player, pHand: InteractionHand, pHit: BlockHitResult): InteractionResult {
        return pLevel.constructMenuHelper2<BurnerBlockEntity>(pPos, pPlayer, Component.translatable("menu.$MODID.burner_reactor"), ::BurnerMenu)
    }

    override fun animateTick(pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) {
        if (pState.getValue(LIT)) {
            val d0 = pPos.x.toDouble() + 0.5
            val d1 = pPos.y.toDouble()
            val d2 = pPos.z.toDouble() + 0.5
            if (pRandom.nextDouble() < 0.5) {
                pLevel.playLocalSound(d0, d1, d2, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0f, 1.0f, false)
            }
            val direction = pState.getValue(FACING)
            repeat(4) {
                val d4 = pRandom.nextDouble() * 0.6 - 0.3
                val d5 = if (direction.axis === Direction.Axis.X) direction.stepX.toDouble() * 0.52 else d4
                val d6 = pRandom.nextDouble() * 6.0 / 16.0
                val d7 = if (direction.axis === Direction.Axis.Z) direction.stepZ.toDouble() * 0.52 else d4
                pLevel.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + d6, d2 + d7, 0.0, 0.0, 0.0)
                pLevel.addParticle(ParticleTypes.FLAME, d0 + d5, d1 + d6, d2 + d7, 0.0, 0.0, 0.0)
            }
        }
    }
}

//#endregion

//#region Block Entity

class BurnerBlockEntity(pPos: BlockPos, pState: BlockState) : BlockEntity(Eln2Processing.BURNER_BLOCK_ENTITY.get(), pPos, pState), ComponentDisplay {
    companion object {
        @ServerOnly
        fun tickServer(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pBlockEntity is BurnerBlockEntity) {
                pBlockEntity.serverTick()
            }
        }
    }

    //#region Capability

    class InventoryHandler(val blockEntity: BurnerBlockEntity) : ItemStackHandler(BURNER_SLOT_COUNT) {
        val inputRange = 0 until BURNER_INPUT_SLOT_COUNT
        val outputSlot = BURNER_OUTPUT_SLOT

        private var dirty = false

        fun wasDirty() : Boolean {
            if(dirty) {
                dirty = false
                return true
            }

            return false
        }

        fun markDirty() {
            dirty = true
        }

        fun searchForRecipe(): Optional<BurningRecipe> {
            val level = blockEntity.level!!
            val container = this.bindToSimpleContainer()
            container.and(inputRange)
            return level.recipeManager.getRecipeFor(
                Eln2Processing.BURNING_RECIPE,
                container,
                level
            )
        }

        fun consumeInputs(recipe: BurningRecipe) {
            check(stacks.take(BURNER_INPUT_SLOT_COUNT).applyRecipeWeighted(recipe.inputItems, true)) {
                DEBUGGER_BREAK("Could not apply the previously verified recipe (input items) $recipe ${blockEntity.blockPos}")
            }
        }

        fun placeOutput(recipe: BurningRecipe) {
            if (recipe.outputItem != null) {
                val remaining = super.insertItem(outputSlot, recipe.outputItem.copy(), false)
                check(remaining.isEmpty) {
                    DEBUGGER_BREAK("Could not insert output item for burning recipe $recipe ${blockEntity.blockPos}")
                }
            }
        }

        fun hasSpaceForOutput(recipe: BurningRecipe): Boolean {
            if (recipe.outputItem == null) return true
            return super.insertItem(outputSlot, recipe.outputItem.copy(), true).isEmpty
        }

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if (slot == outputSlot) return stack
            return super.insertItem(slot, stack, simulate)
        }

        override fun onContentsChanged(slot: Int) {
            blockEntity.setChanged()
            dirty = true
        }
    }

    val inventoryHandler = InventoryHandler(this)
    val inventoryHandlerLazy: LazyOptional<InventoryHandler> = LazyOptional.of { inventoryHandler }

    val tank = EscapingMultipleFractionalFluidTank(
        20,
        100.0,
        false
    )

    class ThermalHandler(val parent: PurityBasedMultipleFractionalFluidTank, val blockEntity: BurnerBlockEntity) : IFractionalFluidHandler by parent, IThermalFluidHandler {
        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction) = 0
        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = 0.0
        override fun fillThermal(resource: FractionalFluidStack, temperature: OptionalDouble, action: IFluidHandler.FluidAction) = 0.0

        override fun drainThermal(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): ThermalFluidStack? {
            val drain = parent.drainFractional(resource, action)
            if (drain.isEmpty) return null
            return ThermalFluidStack(drain, !blockEntity.fluidHandlerTemperature)
        }

        override fun drainThermal(maxDrain: Double, action: IFluidHandler.FluidAction): ThermalFluidStack? {
            val drain = parent.drainFractional(maxDrain, action)
            if (drain.isEmpty) return null
            return ThermalFluidStack(drain, !blockEntity.fluidHandlerTemperature)
        }
    }

    val fluidHandler = ThermalHandler(PurityBasedMultipleFractionalFluidTank(tank.extractionEnd), this)
    val fluidHandlerLazy: LazyOptional<ThermalHandler> = LazyOptional.of { fluidHandler }

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryHandlerLazy.cast()
        }

        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidHandlerLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        inventoryHandlerLazy.invalidate()
        fluidHandlerLazy.invalidate()
    }

    //#endregion

    //#region Recipe State

    private class Operation(val recipe: BurningRecipe, var elapsedTime: Int)

    @ServerOnly
    private var operation: Operation? = null

    data class OperationLoadingData(val operationId: ResourceLocation, val progress: Int)

    @ServerOnly
    private var savedOperationData: OperationLoadingData? = null

    //#endregion

    val data = ProgressContainerData()

    @ServerOnly
    var environment: CellEnvironment? = null

    @ServerOnly
    private var noisedBurnTemperature = OptionalDouble.EMPTY

    @ServerOnly
    val fluidHandlerTemperature: Quantity<Temperature> get() {
        val environment = environment
            ?: error(DEBUGGER_BREAK("Tried to get burner temperature before the environment was fetched"))

        if (operation == null) {
            return environment.ambientTemperature
        }

        if (noisedBurnTemperature.isPresent) {
            return Quantity(noisedBurnTemperature.unwrap())
        }

        return environment.ambientTemperature
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (!pLevel.isClientSide) {
            environment = CellEnvironment.evaluate(
                pLevel,
                Locators.buildLocator {
                    it.put(BLOCK, blockPos)
                }
            )

            if (savedOperationData != null) {
                val optional = pLevel.recipeManager.byKey(savedOperationData!!.operationId)

                if (optional.isEmpty || optional.get() !is BurningRecipe) {
                    LOG.error("Failed to restore burning recipe \"${savedOperationData!!.operationId}\": ${optional.getOrNull()}")
                }
                else {
                    operation = Operation(optional.get() as BurningRecipe, savedOperationData!!.progress)
                }

                savedOperationData = null
                inventoryHandler.markDirty()
            }
        }
    }

    @ServerOnly
    fun serverTick() {
        if (tank.flow()) {
            setChanged()
        }

        //#region Start Operation

        if (operation == null) {
            if (inventoryHandler.wasDirty()) {
                val optional = inventoryHandler.searchForRecipe()

                if (optional.isPresent) {
                    val recipe = optional.get()

                    if (inventoryHandler.hasSpaceForOutput(recipe)) {
                        inventoryHandler.consumeInputs(recipe)
                        operation = Operation(recipe, 0)
                    }
                }
            }
        }

        //#endregion

        //#region Progress Operation

        if (operation != null) {
            val noise = SimplexNoise
                .noise((level!!.gameTime * 0.0001).toFloat(), level!!.rainLevel).toDouble()
                .coerceIn(-1.0, 1.0)

            val temperature = map(
                noise,
                -1.0, 1.0,
                operation!!.recipe.minTemperature, operation!!.recipe.maxTemperature
            )

            noisedBurnTemperature = OptionalDouble.wrap(temperature)

            val op = operation!!
            op.elapsedTime++

            data.progress = op.elapsedTime / op.recipe.duration.toFloat()

            if (op.recipe.outputFluid != null) {
                val fluidToExport = FractionalFluidStack(
                    op.recipe.outputFluid.fluid,
                    op.recipe.outputFluid.amount.toDouble() / op.recipe.duration
                )

                tank.insertionEnd.fillFractional(fluidToExport, IFluidHandler.FluidAction.EXECUTE)
            }

            if (op.elapsedTime >= op.recipe.duration) {
                inventoryHandler.placeOutput(op.recipe)
                this.operation = null
                inventoryHandler.markDirty()
            }

            setChanged()
        }
        else {
            data.progress = 0.0f
        }

        //#endregion

        val shouldBeLit = operation != null

        if (blockState.getValue(BurnerBlock.LIT) != shouldBeLit) {
            level!!.setBlock(
                blockPos,
                blockState.setValue(BurnerBlock.LIT, shouldBeLit),
                Block.UPDATE_ALL
            )
        }
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        val op = operation

        if (op != null) {
            builder.debugInIDE { "Recipe: ${op.recipe.recipeId}" }
            builder.progress(op.elapsedTime / op.recipe.duration.toDouble())
        }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        pTag.put("inventory", inventoryHandler.serializeNBT())
        pTag.put("tank", tank.serializeNBT())

        if (operation != null) {
            val operationTag = CompoundTag()

            operationTag.putResourceLocation("recipe", operation!!.recipe.recipeId)
            operationTag.putInt("progress", operation!!.elapsedTime)

            pTag.put("operation", operationTag)
        }
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        inventoryHandler.deserializeNBT(pTag.getCompound("inventory"))
        tank.deserializeNBT(pTag.getCompound("tank"))

        if (pTag.contains("operation")) {
            val operationTag = pTag.getCompound("operation")

            val recipe = operationTag.getResourceLocation("recipe")
            val progress = operationTag.getInt("progress")

            savedOperationData = OperationLoadingData(recipe, progress)
        }
    }
}

//#endregion

//#region Menu

class BurnerMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: ProgressContainerData,
    val access: ContainerLevelAccess,
    val level: Level
) : AbstractContainerMenu(Eln2Processing.BURNER_MENU.get(), pContainerId), ProgressSupplierMenu {
    companion object {
        private val INPUT_SLOTS = mapOf(
            0 to Vector2di(33, 29),
            1 to Vector2di(52, 29),
            2 to Vector2di(33, 48),
            3 to Vector2di(52, 48)
        )
    }

    @ServerOnly
    constructor(entity: BurnerBlockEntity, id: Int, inventory: Inventory) : this(
        id,
        inventory,
        entity.inventoryHandler,
        entity.data,
        ContainerLevelAccess.create(entity.level!!, entity.blockPos),
        entity.level!!
    )

    @ClientOnly
    constructor(pContainerId: Int, playerInventory: Inventory) : this(
        pContainerId,
        playerInventory,
        ItemStackHandler(BURNER_SLOT_COUNT),
        ProgressContainerData(),
        ContainerLevelAccess.NULL,
        playerInventory.player.level()
    )

    init {
        INPUT_SLOTS.forEach { (slot, pos) ->
            addSlot(SlotItemHandler(handler, slot, pos.x, pos.y))
        }

        addSlot(SlotItemHandlerWithPlacePredicate(handler, BURNER_OUTPUT_SLOT, 117, 38) { false })

        addDataSlots(containerData)

        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) = stillValid(access, pPlayer, Eln2Processing.BURNER_BLOCK.block.get())

    override fun quickMoveStack(pPlayer: Player, pIndex: Int) = ContainerHelper.quickMove(slots, pPlayer, pIndex)

    override fun getProgressForRender() = containerData.progress
}

//#endregion
