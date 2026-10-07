package org.eln2.mc.common.content.processing

import com.google.gson.JsonObject
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual
import dev.engine_room.flywheel.api.visual.ShaderLightVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.client.extensions.common.IClientBlockExtensions
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler
import org.ageseries.libage.data.CELSIUS
import org.ageseries.libage.data.OptionalDouble
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.Temperature
import org.ageseries.libage.data.classify
import org.ageseries.libage.data.put
import org.ageseries.libage.mathematics.geometry.Vector2di
import org.ageseries.libage.mathematics.map
import org.eln2.mc.ClientOnly
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.LOG
import org.eln2.mc.MODID
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.FlwMaterials
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.PartialModelHelper
import org.eln2.mc.client.screens.ProgressSupplierMenu
import org.eln2.mc.common.blocks.foundation.BigBlockRepresentativeBlockEntity
import org.eln2.mc.common.blocks.foundation.MultiblockDelegateBlockEntity
import org.eln2.mc.common.blocks.foundation.MultiblockDelegateBlockWithCustomCollider
import org.eln2.mc.common.blocks.foundation.MultiblockDelegateMap
import org.eln2.mc.common.blocks.foundation.MultiblockTransformations
import org.eln2.mc.common.blocks.foundation.ReplaceVanillaParticlesBlockExtension
import org.eln2.mc.common.cells.foundation.CellEnvironment
import org.eln2.mc.common.containers.ContainerHelper
import org.eln2.mc.common.containers.ProgressContainerData
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicate
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.fluids.foundation.EscapingMultipleFractionalFluidTank
import org.eln2.mc.common.fluids.foundation.FractionalFluidStack
import org.eln2.mc.common.fluids.foundation.IFractionalFluidHandler
import org.eln2.mc.common.fluids.foundation.IThermalFluidHandler
import org.eln2.mc.common.fluids.foundation.PurityBasedMultipleFractionalFluidTank
import org.eln2.mc.common.fluids.foundation.ThermalFluidStack
import org.eln2.mc.common.recipes.foundation.*
import org.eln2.mc.Locators
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.joml.SimplexNoise
import java.util.*
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull

private const val COKING_INPUT_SLOT_COUNT = 4
private const val COKING_OUTPUT_SLOT_COUNT = 4

/**
 * Multiple item input, multiple item output, single fluid output constant time recipe.
 * */
class CokingRecipe(
    override val recipeSerializer : Serializer,
    override val recipeId: ResourceLocation,
    val inputItems: Eln2WeightedItemRecipeRequirements,
    val outputItems: List<ItemStack>?,
    val outputFluid: FluidStack?,
    val duration: Int
) : Eln2CustomRecipe<CokingRecipe>, Eln2NonStandardRecipe {
    init {
        require(inputItems.requirements.size <= COKING_INPUT_SLOT_COUNT) {
            "Coking recipe \"$recipeId\" has more input items than is allowed ($COKING_INPUT_SLOT_COUNT)"
        }

        if(outputItems != null) {
            require(outputItems.size <= COKING_OUTPUT_SLOT_COUNT) {
                "Coking recipe \"$recipeId\" has more output items than is allowed ($COKING_OUTPUT_SLOT_COUNT)"
            }
        }
    }

    override fun matches(pContainer: SimpleContainer, pLevel: Level) : Boolean {
        if(pContainer.containerSize != COKING_INPUT_SLOT_COUNT + COKING_OUTPUT_SLOT_COUNT) {
            return false
        }

        val items = pContainer.bindToList().take(COKING_INPUT_SLOT_COUNT)

        return items.applyRecipeWeighted(inputItems, true)
    }

    class Serializer(override val recipeType: RecipeType<CokingRecipe>) : Eln2RecipeSerializer<CokingRecipe> {
        override fun fromJson(pRecipeId: ResourceLocation, pSerializedRecipe: JsonObject): CokingRecipe {
            val inputItems = Eln2WeightedItemRecipeRequirements.fromJson(pSerializedRecipe.get("inputItems"))

            val outputItems = if(pSerializedRecipe.has("outputItems")) {
                pSerializedRecipe.getAsJsonArray("outputItems").map { ShapedRecipe.itemStackFromJson(it.asJsonObject) }
            }
            else {
                null
            }

            val outputFluid = if(pSerializedRecipe.has("outputFluid")) {
                pSerializedRecipe.getAsJsonObject("outputFluid").asFluidStack()
            }
            else {
                null
            }

            val duration = pSerializedRecipe.get("duration").asInt

            return CokingRecipe(
                this, pRecipeId,
                inputItems, outputItems, outputFluid,
                duration
            )
        }

        override fun fromNetwork(pRecipeId: ResourceLocation, pBuffer: FriendlyByteBuf): CokingRecipe {
            val inputItems = Eln2WeightedItemRecipeRequirements.fromNetwork(pBuffer)

            val outputItems = pBuffer.readNullable {
                pBuffer.readList { pBuffer.readItem() }
            }

            val outputFluid = pBuffer.readNullable {
                pBuffer.readFluidStack()
            }

            val duration = pBuffer.readInt()

            return CokingRecipe(
                this, pRecipeId,
                inputItems, outputItems, outputFluid,
                duration
            )
        }

        override fun toNetwork(pBuffer: FriendlyByteBuf, pRecipe: CokingRecipe) {
            pRecipe.inputItems.toNetwork(pBuffer)

            pBuffer.writeNullable(pRecipe.outputItems) { _, outputItems ->
                pBuffer.writeCollection(outputItems) { _, stack ->
                    pBuffer.writeItem(stack)
                }
            }

            pBuffer.writeNullable(pRecipe.outputFluid) { _, outputFluid ->
                pBuffer.writeFluidStack(outputFluid)
            }

            pBuffer.writeInt(pRecipe.duration)
        }
    }
}

class CokeOvenDelegateBlock(initialShapes: List<AABB>, val capabilityMaskLocal: Base6Direction3dMask) : MultiblockDelegateBlockWithCustomCollider(initialShapes = initialShapes) {
    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = CokeOvenDelegateBlockEntity(pPos, pState)

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult
    ): InteractionResult {
        return super.use(pState, pLevel, pPos, pPlayer, pHand, pHit)
    }
}

class CokeOvenDelegateBlockEntity(pPos: BlockPos, pBlockState: BlockState) : MultiblockDelegateBlockEntity(pPos, pBlockState, Eln2Processing.COKE_OVEN_DELEGATE_BLOCK_ENTITY.get()) {
    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if(side != null) {
            val sideLocal = MultiblockTransformations.rot(blockState.getValue(HorizontalDirectionalBlock.FACING))
                .rotate(side)

            if(!(blockState.block as CokeOvenDelegateBlock).capabilityMaskLocal.has(sideLocal)) {
                /**
                 * Rejects the capability on that side:
                 * */
                return LazyOptional.empty()
            }
        }

        val level = this.level
            ?: return super.getCapability(cap, side)

        val representativePos = this.representativePos
            ?: return super.getCapability(cap, side)

        if (!level.isLoaded(representativePos)) {
            return LazyOptional.empty()
        }

        val representative = level.getBlockEntity(representativePos) as? CokeOvenMainBlockEntity
            ?: return super.getCapability(cap, side)

        /**
         * Forward the fetch to the representative:
         * */
        return representative.getCapability(cap, null)
    }
}

class CokeOvenMainBlock : HorizontalDirectionalBlock(eln2StandardBlockProperties().noOcclusion()), EntityBlock {
    companion object {
        fun constructMenu(pLevel: Level, pPos: BlockPos, pPlayer: Player) = pLevel.constructMenuHelper2<CokeOvenMainBlockEntity>(
            pPos,
            pPlayer,
            Component.translatable("menu.$MODID.coke_oven"),
            ::CokeOvenMenu
        )

        val ACTIVE: BooleanProperty = BooleanProperty.create("active")
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(ACTIVE, false)
        )
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState? {
        return super.defaultBlockState().setValue(
            FACING,
            pContext.horizontalDirection
        )
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(FACING)
        pBuilder.add(ACTIVE)
    }

    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("true"))
    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction): Boolean {
        return true
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = CokeOvenMainBlockEntity(pPos, pState)

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ) = constructMenu(pLevel, pPos, pPlayer)

    override fun <T : BlockEntity?> getTicker(pLevel: Level, pState: BlockState, pBlockEntityType: BlockEntityType<T?>): BlockEntityTicker<T?>? {
        if(pLevel.isClientSide) {
            return null
        }

        return BlockEntityTicker(CokeOvenMainBlockEntity::tickServer)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRemove(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pNewState: BlockState,
        pMovedByPiston: Boolean,
    ) {
        if(!pState.`is`(pNewState.block)) {
            if(!pLevel.isClientSide) {
                val blockEntity = pLevel.getBlockEntity(pPos) as? CokeOvenMainBlockEntity

                blockEntity?.setDestroyed()
            }
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston)
    }

    override fun animateTick(pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) {
        if(!pState.getValue(ACTIVE)) {
            return
        }

        val facing = pState.getValue(FACING)
        val sideDir = facing.clockWise
        val particleDir = facing.opposite

        val originX = pPos.x + 0.5 + particleDir.stepX * 0.5
        val originY = pPos.y + 0.5 + particleDir.stepY * 0.5
        val originZ = pPos.z + 0.5 + particleDir.stepZ * 0.5

        if (pRandom.nextDouble() < 0.5) {
            pLevel.playLocalSound(
                originX,
                originY,
                originZ,
                SoundEvents.FURNACE_FIRE_CRACKLE,
                SoundSource.BLOCKS,
                pRandom.nextDouble(0.9, 1.1).toFloat(),
                pRandom.nextDouble(0.9, 1.1).toFloat(),
                false
            )
        }

        repeat(pRandom.nextInt(8)) {
            val incr = if((it and 1) == 0) sideDir else sideDir.opposite

            val pX = originX + 0.825 * incr.stepX + pRandom.nextDouble(-0.1, 0.1)
            val pY = originY + 0.825 * incr.stepY + pRandom.nextDouble(-0.05, 0.05) - 0.225
            val pZ = originZ + 0.825 * incr.stepZ + pRandom.nextDouble(-0.1, 0.1)

            val vX = 0.01 * particleDir.stepX + pRandom.nextDouble(-0.01, 0.01)
            val vY = 0.01 * particleDir.stepY + pRandom.nextDouble(0.0, 0.025)
            val vZ = 0.01 * particleDir.stepZ + pRandom.nextDouble(-0.01, 0.01)

            val num = pRandom.nextDouble()

            when (num) {
                in 0.0..(1.0 / 3.0) -> {
                    pLevel.addParticle(
                        ParticleTypes.SMOKE,
                        pX, pY, pZ,
                        vX, vY, vZ
                    )
                }
                in (1.0 / 3.0)..(2.0 / 3.0) -> {
                    pLevel.addParticle(
                        ParticleTypes.FLAME,
                        pX, pY, pZ,
                        vX, vY, vZ
                    )
                }
                else -> {
                    pLevel.addParticle(
                        ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pX, pY, pZ,
                        vX, vY, vZ
                    )
                }
            }
        }
    }
}

class CokeOvenMainBlockEntity(pPos: BlockPos, pState: BlockState) :
    BlockEntity(Eln2Processing.COKE_OVEN_MAIN_BLOCK_ENTITY.get(), pPos, pState),
    BigBlockRepresentativeBlockEntity<CokeOvenMainBlockEntity>,
    ComponentDisplay
{
    companion object {
        @Suppress("unused")
        @ServerOnly
        fun tickServer(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if(pBlockEntity !is CokeOvenMainBlockEntity) {
                return
            }

            pBlockEntity.serverTick()
        }
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(!pLevel.isClientSide) {
            environment = CellEnvironment.evaluate(
                pLevel,
                Locators.buildLocator {
                    it.put(BLOCK, blockPos)
                }
            )

            if(savedOperationData != null) {
                val optional = pLevel.recipeManager.byKey(savedOperationData!!.operationId)

                if(optional.isEmpty || optional.get() !is CokingRecipe) {
                    LOG.error("Failed to restore coking recipe \"${savedOperationData!!.operationId}\": ${optional.getOrNull()}")
                }
                else {
                    operation = Operation(optional.get() as CokingRecipe, savedOperationData!!.progress)
                }

                savedOperationData = null

                /**
                 * We didn't persist this flag, so we set it here to make sure we check for a new recipe:
                 * */
                inventoryHandler.dirty = true
            }
        }
    }

    val data = ProgressContainerData()

    //#region Multiblock Setup

    override val delegateMap: MultiblockDelegateMap
        get() = Eln2Processing.COKE_OVEN_DELEGATE_MAP.value

    override fun onDelegateUse(
        delegate: BlockEntity,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult
    ) = CokeOvenMainBlock.constructMenu(level!!, blockPos, pPlayer)

    fun setDestroyed() {
        destroyDelegates()
    }

    //#endregion

    //#region Capability

    class InventoryHandler(val blockEntity: CokeOvenMainBlockEntity) : ItemStackHandler(COKING_INPUT_SLOT_COUNT + COKING_OUTPUT_SLOT_COUNT) {
        val inputRange = 0 until COKING_INPUT_SLOT_COUNT
        val outputRange = COKING_INPUT_SLOT_COUNT until (COKING_INPUT_SLOT_COUNT + COKING_OUTPUT_SLOT_COUNT)

        var dirty = false

        fun searchForRecipe() : Optional<CokingRecipe> {
            val level = blockEntity.level!!

            val container = this.bindToSimpleContainer()

            container.and(inputRange)

            return level.recipeManager.getRecipeFor(
                Eln2Processing.COKING_RECIPE,
                container,
                level
            )
        }

        fun consumeInputs(recipe: CokingRecipe) {
            check(stacks.take(COKING_INPUT_SLOT_COUNT).applyRecipeWeighted(recipe.inputItems, true)) {
                DEBUGGER_BREAK("Could not apply the previously verified recipe (input items) $recipe ${blockEntity.blockPos}")
            }
        }

        fun placeOutputs(recipe: CokingRecipe) {
            /**
             * Creates the results:
             * */
            if(recipe.outputItems != null) {
                val handler = IItemHandler_insertItem { a, b, c ->
                    super.insertItem(a, b, c)
                }

                check(handler.insertRange(recipe.outputItems, outputRange)) {
                    DEBUGGER_BREAK("Could not apply the previously verified coking recipe (output items) $recipe ${blockEntity.blockPos}")
                }
            }

            dirty = true
        }

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if(outputRange.contains(slot)) {
                return stack
            }

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

    /**
     * Wraps the extraction of the [parent] with the [blockEntity]'s [org.eln2.mc.common.content.processing.CokeOvenMainBlockEntity.fluidHandlerTemperature].
     * */
    class ThermalHandler(val parent: PurityBasedMultipleFractionalFluidTank, val blockEntity: CokeOvenMainBlockEntity) : IFractionalFluidHandler by parent, IThermalFluidHandler {
        override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction) = 0
        override fun fillFractional(resource: FractionalFluidStack, action: IFluidHandler.FluidAction) = 0.0
        override fun fillThermal(resource: FractionalFluidStack, temperature: OptionalDouble, action: IFluidHandler.FluidAction) = 0.0

        override fun drainThermal(resource: FractionalFluidStack, action: IFluidHandler.FluidAction): ThermalFluidStack? {
            val drain = parent.drainFractional(resource, action)

            if(drain.isEmpty) {
                return null
            }

            return ThermalFluidStack(drain, !blockEntity.fluidHandlerTemperature)
        }

        override fun drainThermal(maxDrain: Double, action: IFluidHandler.FluidAction): ThermalFluidStack? {
            val drain = parent.drainFractional(maxDrain, action)

            if(drain.isEmpty) {
                return null
            }

            return ThermalFluidStack(drain, !blockEntity.fluidHandlerTemperature)
        }
    }

    /**
     * Wraps the [EscapingMultipleFractionalFluidTank.extractionEnd] of [tank] with the [ThermalHandler].
     * */
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

    /**
     * Represents a coking operation in progress.
     * @param elapsedTime The number of ticks elapsed.
     * */
    private class Operation(val recipe: CokingRecipe, var elapsedTime: Int)

    @ServerOnly
    private var operation: Operation? = null

    data class OperationLoadingData(val operationId: ResourceLocation, val progress: Int)

    /**
     * Used during loading. We need the level to read the recipe from the serialized ID.
     * */
    @ServerOnly
    private var savedOperationData: OperationLoadingData? = null

    //#endregion

    /**
     * The environment corresponding to the [blockPos] and [level], loaded in [setLevel].
     * */
    @ServerOnly
    var environment: CellEnvironment? = null

    /**
     * Output temperature when burning, evaluated from some smooth noise in [serverTick].
     * */
    @ServerOnly
    private var noisedBurnTemperature = OptionalDouble.EMPTY

    /**
     * Gets the output temperature of the gas. Ambient if the oven is not running, and [noisedBurnTemperature] if it is running.
     * */
    @ServerOnly
    val fluidHandlerTemperature: Quantity<Temperature> get() {
        val environment = environment
            ?: error(DEBUGGER_BREAK("Tried to get oven output temperature before the environment was fetched"))

        if(operation == null) {
            return environment.ambientTemperature
        }

        if(noisedBurnTemperature.isPresent) {
            return Quantity(noisedBurnTemperature.unwrap())
        }

        return environment.ambientTemperature
    }

    @ServerOnly
    fun serverTick() {
        if(tank.flow()) {
            setChanged()
        }

        /**
         * Tries to start the operation:
         * */
        if(operation == null) {
            if(inventoryHandler.dirty) {
                inventoryHandler.dirty = false

                val optional = inventoryHandler.searchForRecipe()

                if(!optional.isEmpty) {
                    val recipe = optional.get()

                    /**
                     * Checks if we have space for export:
                     * */
                    if (recipe.outputItems == null || (IItemHandler_insertItem(inventoryHandler.bind()::insertItem)).insertRange(recipe.outputItems, inventoryHandler.outputRange)) {
                        /**
                         * Consumes the items:
                         * */
                        inventoryHandler.consumeInputs(recipe)

                        /**
                         * Start:
                         * */
                        operation = Operation(recipe, 0)
                    }
                }
            }
        }

        /**
         * Progresses the operation and sets the [noisedBurnTemperature]:
         * */
        if(operation != null) {
            val noise = SimplexNoise
                .noise((level!!.gameTime * 0.0001).toFloat(), level!!.rainLevel).toDouble()
                .coerceIn(-1.0, 1.0)

            val temperature = !Quantity(
                map(
                    noise,
                    -1.0, 1.0,
                    100.0, 150.0
                ),
                CELSIUS
            )

            noisedBurnTemperature = OptionalDouble.wrap(temperature)

            val operation = operation!!
            operation.elapsedTime++

            data.progress = operation.elapsedTime / operation.recipe.duration.toFloat()

            if(operation.recipe.outputFluid != null) {
                val fluidToExport = FractionalFluidStack(
                    operation.recipe.outputFluid.fluid,
                    operation.recipe.outputFluid.amount.toDouble() / operation.recipe.duration
                )

                tank.insertionEnd.fillFractional(fluidToExport, IFluidHandler.FluidAction.EXECUTE)
            }

            /**
             * Try to finish the operation:
             * */
            if(operation.elapsedTime >= operation.recipe.duration) {
                inventoryHandler.placeOutputs(operation.recipe)
                this.operation = null
            }

            setChanged()
        }
        else {
            data.progress = 0.0f
        }

        /**
         * Updates blockstate for A/V:
         * */
        val isRunning = operation != null
        if(blockState.getValue(CokeOvenMainBlock.ACTIVE) != isRunning) {
            level!!.setBlock(
                blockPos,
                blockState.setValue(CokeOvenMainBlock.ACTIVE, isRunning),
                Block.UPDATE_ALL
            )
        }
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        val operation = operation

        if(operation != null) {
            builder.debugInIDE { "Recipe: ${operation.recipe.recipeId}" }
            builder.progress(operation.elapsedTime / operation.recipe.duration.toDouble())
        }

        builder.debugInIDE { "Handler temp: ${fluidHandlerTemperature.classify()}" }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        pTag.put("inventory", inventoryHandler.serializeNBT())
        pTag.put("tank", tank.serializeNBT())

        if(operation != null) {
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

        if(pTag.contains("operation")) {
            val operationTag = pTag.getCompound("operation")

            val recipe = operationTag.getResourceLocation("recipe")
            val progress = operationTag.getInt("progress")

            savedOperationData = OperationLoadingData(recipe, progress)
        }
    }
}

class CokeOvenMainBlockEntityVisual(
    ctx: VisualizationContext,
    blockEntity: CokeOvenMainBlockEntity,
    partialTick: Float
) : AbstractBlockEntityVisual<CokeOvenMainBlockEntity>(ctx, blockEntity, partialTick), ShaderLightVisual {
    val body: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(FlwModels.COKING_OVEN, FlwMaterials.CUTOUT_SMOOTH_LIT))
        .createInstance()
        .also {
            it.translate(visualPosition)
            it.center()
            it.rotateToFace(blockEntity.representativeFacing.opposite)
            it.uncenter()
        }

    override fun updateLight(p0: Float) {
        // NOOP
    }

    override fun setSectionCollector(sectionCollector: SectionTrackedVisual.SectionCollector) {
        this.lightSections = sectionCollector

        sectionCollector.sections(
            blockEntity.delegateMap.getTotalSpannedSectionsFat(
                blockState.getValue(HorizontalDirectionalBlock.FACING),
                pos
            )
        )
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        p0.accept(body)
    }

    override fun _delete() {
        body.delete()
    }
}

class CokeOvenMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: ProgressContainerData,
    val access: ContainerLevelAccess,
    val level: Level
) : AbstractContainerMenu(Eln2Processing.COKE_OVEN_MENU.get(), pContainerId), ProgressSupplierMenu {
    companion object {
        private val INPUT_SLOTS = mapOf(
            0 to Vector2di(21, 24),
            1 to Vector2di(48, 24),
            2 to Vector2di(75, 24),
            3 to Vector2di(102, 24)
        )

        private val OUTPUT_SLOTS = mapOf(
            4 to Vector2di(25, 49),
            5 to Vector2di(51, 49),
            6 to Vector2di(77, 49),
            7 to Vector2di(103, 49)
        )
    }

    @ServerOnly
    constructor(entity: CokeOvenMainBlockEntity, id: Int, inventory: Inventory): this(
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
        ItemStackHandler(COKING_INPUT_SLOT_COUNT + COKING_OUTPUT_SLOT_COUNT),
        ProgressContainerData(),
        ContainerLevelAccess.NULL,
        playerInventory.player.level()
    )

    init {
        INPUT_SLOTS.forEach { (slot, pos) ->
            addSlot(SlotItemHandler(handler, slot, pos.x, pos.y))
        }

        OUTPUT_SLOTS.forEach { (slot, pos) ->
            addSlot(SlotItemHandlerWithPlacePredicate(handler, slot, pos.x, pos.y) { false })
        }

        addDataSlots(containerData)

        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) = stillValid(access, pPlayer, Eln2Processing.COKE_OVEN_MAIN_BLOCK.get())

    override fun quickMoveStack(pPlayer: Player, pIndex: Int) = ContainerHelper.quickMove(slots, pPlayer, pIndex)

    override fun getProgressForRender() = containerData.progress
}
