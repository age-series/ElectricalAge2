package org.eln2.mc.common.content.processing

import com.google.gson.JsonObject
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual
import dev.engine_room.flywheel.api.visual.ShaderLightVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.RegistryAccess
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.util.GsonHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.client.extensions.common.IClientBlockExtensions
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.FramerateIndependentSmoother1d
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.map
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.utils.Stopwatch
import org.eln2.mc.*
import org.eln2.mc.client.render.FlwMaterials
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.FlwModels.VULCANIZING_AUTOCLAVE_DOOR
import org.eln2.mc.client.render.FlwModels.iterateVertexPositions
import org.eln2.mc.client.render.foundation.FlwInstanceTypes
import org.eln2.mc.client.render.foundation.PartialModelHelper
import org.eln2.mc.client.render.foundation.ThermalTint
import org.eln2.mc.client.render.foundation.TransformedLightOverrideInstance
import org.eln2.mc.common.blocks.foundation.*
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.content.ThermalWireObject
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.content.processing.VulcanizingAutoclaveMainBlockEntity.StateMachine
import org.eln2.mc.common.content.processing.VulcanizingAutoclaveMainBlockEntity.StateMachine.IsProcessingPacket
import org.eln2.mc.common.network.serverToClient.BulkPacketHandlerBlockEntity
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.sendBulkPacket
import org.eln2.mc.common.recipes.foundation.INPUT_SLOT
import org.eln2.mc.common.recipes.foundation.OUTPUT_SLOT
import org.eln2.mc.common.sounds.foundation.SimpleLoopingBlockEntitySoundInstance
import org.eln2.mc.common.sounds.foundation.SoundInfo
import org.eln2.mc.common.sounds.foundation.SoundInstanceTickEvent
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.mathematics.Base6Direction3dMask
import java.util.*
import java.util.function.Consumer
import kotlin.math.PI
import kotlin.random.Random

/**
 * Multi-block, Multi-cell device.
 * There still is a representative block with its cell, that has the main logic: [VulcanizingAutoclaveMainCell], [VulcanizingAutoclaveMainBlock], [VulcanizingAutoclaveMainBlockEntity].
 * The main cell doesn't have simulation objects and doesn't create connections with the exterior.
 * It creates a connection with the [VulcanizingAutoclaveThermalPortCell].
 *
 * The [VulcanizingAutoclaveThermalPortCell], [VulcanizingAutoclaveThermalPortBlock], [VulcanizingAutoclaveThermalPortBlockEntity] has a cell and a thermal simulation object.
 * It forms a connection to any wire that is hooked up to it, and it connects to the [VulcanizingAutoclaveMainCell].
 * It doesn't have any logic.
 * */
//#region Vulcanizing Autoclave

/**
 * Recipe for the vulcanizing autoclave.
 * @param input The input ingredient. Must be a single item stack with 1 count.
 * @param output The output item. Must be a single item with 1 or more count.
 * @param burnOutput The output item, when the temperature exceeded the [maxTemperature].
 * @param duration The base duration, in seconds.
 * @param minTemperature If the temperature is below this threshold, the recipe will not progress.
 * @param maxTemperature If the temperature is above this threshold, the ingredients will burn.
 * */
class VulcanizingRecipe(
    val recipeSerializer: Serializer,
    val recipeId: ResourceLocation,
    val input: Ingredient,
    val output: ItemStack,
    val burnOutput: ItemStack,
    val duration: Double,
    val minTemperature: Double,
    val maxTemperature: Double
) : Recipe<SimpleContainer> {
    // WARNING: Do NOT access `input.items` (Ingredient.getItems()) in an init block or during deserialization.
    // Doing so forces tag resolution before the TagManager has finished loading, caching an empty result
    // permanently. This causes tag-based ingredients to silently fail until /reload. See: SimpleRecipes.kt.

    override fun matches(pContainer: SimpleContainer, pLevel: Level) = input.test(pContainer.getItem(INPUT_SLOT))
    override fun assemble(pContainer: SimpleContainer, pRegistryAccess: RegistryAccess): ItemStack = output.copy()
    override fun canCraftInDimensions(pWidth: Int, pHeight: Int) = true
    override fun getResultItem(pRegistryAccess: RegistryAccess): ItemStack = output.copy()

    override fun getId() = recipeId
    override fun getSerializer() = recipeSerializer
    override fun getType() = recipeSerializer.recipeType

    class Serializer(val recipeType: RecipeType<VulcanizingRecipe>) : RecipeSerializer<VulcanizingRecipe> {
        override fun fromJson(pRecipeId: ResourceLocation, pSerializedRecipe: JsonObject): VulcanizingRecipe {
            val input = Ingredient.fromJson(pSerializedRecipe.get("ingredient"))
            val output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "result"))
            val burnOutput = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "resultBurnt"))
            val duration = pSerializedRecipe.getAsJsonPrimitive("duration").asDouble
            val minTemperature = pSerializedRecipe.getAsJsonPrimitive("minTemperature").asDouble
            val maxTemperature = pSerializedRecipe.getAsJsonPrimitive("maxTemperature").asDouble

            return VulcanizingRecipe(
                this,
                pRecipeId,
                input, output, burnOutput,
                duration,
                minTemperature, maxTemperature
            )
        }

        override fun fromNetwork(pRecipeId: ResourceLocation, pBuffer: FriendlyByteBuf): VulcanizingRecipe {
            val input = Ingredient.fromNetwork(pBuffer)
            val output = pBuffer.readItem()
            val burnOutput = pBuffer.readItem()
            val duration = pBuffer.readDouble()
            val minTemperature = pBuffer.readDouble()
            val maxTemperature = pBuffer.readDouble()

            return VulcanizingRecipe(
                this,
                pRecipeId,
                input, output, burnOutput,
                duration,
                minTemperature, maxTemperature
            )
        }

        override fun toNetwork(pBuffer: FriendlyByteBuf, pRecipe: VulcanizingRecipe) {
            pRecipe.input.toNetwork(pBuffer)
            pBuffer.writeItem(pRecipe.output)
            pBuffer.writeItem(pRecipe.burnOutput)
            pBuffer.writeDouble(pRecipe.duration)
            pBuffer.writeDouble(pRecipe.minTemperature)
            pBuffer.writeDouble(pRecipe.maxTemperature)
        }
    }
}

class VulcanizingAutoclaveThermalPortCell(
    ci: CellCreateInfo,
    thermalDef: ThermalMassDefinition,
    override val thermalMap: MonopoleMap,
    override val thermalSize: ThermalSize
) : Cell(ci), SidedThermalMonoMapped<VulcanizingAutoclaveThermalPortCell> {
    @SimObject
    val thermalWire = ThermalWireObject(this, thermalDef())

    @Behavior
    val thermalExplosion = ThermalBreakdownBehavior.create(
        Quantity(411.5, CELSIUS),
        this,
        thermalWire.thermalBody::temperature
    )
}

class VulcanizingAutoclaveThermalPortBlock : MultiblockDelegateUprightHorizontalDirectionCellBlock<VulcanizingAutoclaveThermalPortCell>() {
    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState, pAdjacentState: BlockState, pDirection: Direction) = true

    override fun getCellProvider() = Eln2Processing.VULCANIZING_AUTOCLAVE_THERMAL_PORT_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = VulcanizingAutoclaveThermalPortBlockEntity(pPos, pState)

    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        val blockEntity = level.getBlockEntity(cell.locator.requireLocator(Locators.BLOCK)) as VulcanizingAutoclaveThermalPortBlockEntity

        val representative = getRepresentativeFromDelegateBlockEntity<VulcanizingAutoclaveMainBlockEntity?>(blockEntity, blockEntity.representativePos)
            ?: return

        /**
         * Scans the port itself:
         * */
        planarCellScan(
            level,
            cell,
            cell.locator.requireLocator(Locators.CONVENTIONAL_FACING).direction.opposite,
            results::add
        )

        /**
         * Links up the main:
         * */
        results.add(CellAndContainerHandle.captureInScope(representative.cell))
    }
}

class VulcanizingAutoclaveThermalPortBlockEntity(pPos: BlockPos, pBlockState: BlockState) :
    MultiblockDelegateCellBlockEntity<VulcanizingAutoclaveThermalPortCell>(pPos, pBlockState, Eln2Processing.VULCANIZING_AUTOCLAVE_THERMAL_PORT_BLOCK_ENTITY.get()),
    ValidateFormationBlockEntity<VulcanizingAutoclaveThermalPortBlockEntity>
{
    override fun validateMultiblockFormation() {
        check(cell.connections.isNotEmpty() && cell.connections.any { it is VulcanizingAutoclaveMainCell }) {
            DEBUGGER_BREAK("Vulcanizing autoclave didn't form correctly: the thermal port cell doesn't have the required connection (${cell.connections.size})")
        }
    }
}

class VulcanizingAutoclaveMainCell(ci: CellCreateInfo) : Cell(ci) {
    /**
     * Flag set by the block entity to indicate if a recipe is currently loaded.
     * */
    @CrossThreadAccess
    var isOperating = false // Not saved. The operation happens in the server loop.
        set(value) {
            if(field != value) {
                field = value
                setChanged()
            }
        }

    @CrossThreadAccess
    var doorClosed = false
        set(value) {
            if(field != value) {
                field = value
                setChanged()
            }
        }

    val isFormed get() = connections.isNotEmpty()

    val heatPort get() = if(!isFormed) error("Tried to get heat port cell, but the autoclave is not formed correctly yet!") else connections[0] as VulcanizingAutoclaveThermalPortCell

    @Replicator
    fun temperatureReplicator(target: InternalTemperatureConsumer) = InternalTemperatureReplicatorBehavior(target) {
        if (isFormed) {
            !heatPort.thermalWire.thermalBody.temperature
        }
        else {
            0.0
        }
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPre(this::tick)
    }

    private fun applyLoss(dt: Double, loss: Quantity<ThermalConductance>) {
        val thermalBody = heatPort.thermalWire.thermalBody

        var newEnergy = !thermalBody.energy - !(thermalBody.temperature - heatPort.environmentData.ambientTemperature) * !loss * dt

        if(newEnergy < 0.0) {
            newEnergy = 0.0
        }
        else {
            if(newEnergy / !thermalBody.mass / !thermalBody.material.specificHeat < !heatPort.environmentData.ambientTemperature) {
                newEnergy = !heatPort.environmentData.ambientTemperature * !thermalBody.mass * !thermalBody.material.specificHeat
            }
        }

        if(!newEnergy.approxEq(!thermalBody.energy)) {
            thermalBody.energy = Quantity(newEnergy, JOULE)
            heatPort.setChanged()
        }
    }

    /**
     * Applies thermal loss due to the recipe and, if the door is open, applies a large penalty.
     * */
    private fun tick(dt: Double, phase: SimulationPhase) {
        if(!isFormed) {
            return
        }

        if(isOperating) {
            applyLoss(dt, OPERATING_CONSUMPTION)
        }

        if(!doorClosed) {
            applyLoss(dt, OPEN_DOOR_LOSS)
        }
    }

    override fun saveCellData(): CompoundTag {
        val tag = CompoundTag()
        tag.putBoolean(DOOR_CLOSED, doorClosed)
        return tag
    }

    override fun loadCellData(tag: CompoundTag) {
        doorClosed = tag.getBoolean(DOOR_CLOSED)
    }

    companion object {
        private const val DOOR_CLOSED = "doorClosed"

        private val OPEN_DOOR_LOSS = Quantity(10.0, WATT_PER_KELVIN)
        private val OPERATING_CONSUMPTION = Quantity(1.0,  WATT_PER_KELVIN)
    }
}

class VulcanizingAutoclaveMainBlock : UprightHorizontalDirectionCellBlock<VulcanizingAutoclaveMainCell>() {
    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
    }

    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction): Boolean = true

    override fun getCellProvider() = Eln2Processing.VULCANIZING_AUTOCLAVE_MAIN_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = VulcanizingAutoclaveMainBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(pLevel: Level, pState: BlockState, pBlockEntityType: BlockEntityType<T?>): BlockEntityTicker<T?> {
        return BlockEntityTicker(VulcanizingAutoclaveMainBlockEntity::tick)
    }

    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        val pos = cell.locator.requireLocator(Locators.BLOCK)
        val facing = cell.locator.requireLocator(Locators.CONVENTIONAL_FACING)
        val blockEntity = level.getBlockEntity(pos) as VulcanizingAutoclaveMainBlockEntity

        /**
         * Only links up the delegate:
         * */
        blockEntity.delegateMap.forEachDelegateInWorld(facing.direction, pos) {
            val delegate = level.getBlockEntity(it) as? VulcanizingAutoclaveThermalPortBlockEntity
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
        pHit: BlockHitResult
    ): InteractionResult {
        if(pHand != InteractionHand.MAIN_HAND || pLevel.isClientSide) {
            return InteractionResult.FAIL
        }

        val blockEntity = pLevel.getBlockEntity(pPos) as? VulcanizingAutoclaveMainBlockEntity
            ?: return InteractionResult.FAIL

        return blockEntity.onUseByPlayer(pPlayer)
    }
}

private data class DoorStateSwitchMessage(val previousState: Int, val newState: Int) {
    companion object {
        fun serialize(packet: DoorStateSwitchMessage, buffer: FriendlyByteBuf) {
            buffer.writeInt(packet.previousState)
            buffer.writeInt(packet.newState)
        }

        fun deserialize(buffer: FriendlyByteBuf) = DoorStateSwitchMessage(
            buffer.readInt(),
            buffer.readInt()
        )
    }
}

private data class ChangeLoadMessage(val targetState: Int) {
    companion object {
        fun serialize(packet: ChangeLoadMessage, buffer: FriendlyByteBuf) {
            buffer.writeInt(packet.targetState)
        }

        fun deserialize(buffer: FriendlyByteBuf) = ChangeLoadMessage(
            buffer.readInt()
        )
    }
}

class VulcanizingAutoclaveMainBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity<VulcanizingAutoclaveMainCell>(pos, state, Eln2Processing.VULCANIZING_AUTOCLAVE_MAIN_BLOCK_ENTITY.get()),
    BigBlockRepresentativeBlockEntity<VulcanizingAutoclaveMainBlockEntity>,
    ValidateFormationBlockEntity<VulcanizingAutoclaveMainBlockEntity>,
    BulkPacketHandlerBlockEntity,
    InternalTemperatureConsumer,
    org.eln2.mc.common.content.WrenchInteractable,
    ComponentDisplay
{
    companion object {
        private const val INVENTORY = "inventory"
        private const val STATE_MACHINE = "stateMachine"

        fun tick(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pLevel == null || pBlockEntity == null) {
                LOG.error("level or entity null")
                return
            }

            if (pBlockEntity !is VulcanizingAutoclaveMainBlockEntity) {
                LOG.error(DEBUGGER_BREAK("Got $pBlockEntity instead of autoclave block entity"))
                return
            }

            if (pLevel.isClientSide) {
                pBlockEntity.clientTick()
            }
            else {
                pBlockEntity.serverTick()
            }
        }
    }

    //#region Multiblock Setup

    override val delegateMap: MultiblockDelegateMap
        get() = Eln2Processing.VULCANIZING_AUTOCLAVE_DELEGATE_MAP.value

    override fun validateMultiblockFormation() {
        check(cell.connections.size == 1 && cell.connections[0] is VulcanizingAutoclaveThermalPortCell) {
            DEBUGGER_BREAK("Vulcanizing autoclave didn't form correctly: the main cell doesn't have the correct connections (${cell.connections.size})")
        }
    }

    override fun setDestroyed() {
        destroyDelegates()

        super.setDestroyed()
    }

    //#endregion

    //#region Inventory

    val inventoryHandler = InventoryHandler(this)
    val inventoryHandlerLazy: LazyOptional<InventoryHandler> = LazyOptional.of { inventoryHandler }

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryHandlerLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        inventoryHandlerLazy.invalidate()
    }

    class InventoryHandler(val blockEntity: VulcanizingAutoclaveMainBlockEntity) : net.minecraftforge.items.ItemStackHandler(2) {
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            return stack
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
            return ItemStack.EMPTY // prevents automation from extracting input
        }
    }

    //#endregion

    //#region Recipe Processing

    @ServerOnly
    fun searchForRecipe(): Optional<VulcanizingRecipe> {
        val stack = inventoryHandler.getStackInSlot(INPUT_SLOT)

        if(stack.isEmpty) {
            return Optional.empty()
        }

        val level = level!!

        return level.recipeManager.getRecipeFor(
            Eln2Processing.VULCANIZING_RECIPE,
            inventoryHandler.bindToSimpleContainer(),
            level
        )
    }

    //#endregion

    private enum class HissType {
        Interrupted,
        VulcanizationSuccessful,
        Burnt
    }

    @ServerOnly
    private fun hiss(type: HissType) {
        val (x, y, z) = blockPos.toVector3d() + Vector3d(0.5)

        level!!.playSound(
            null,
            x, y, z,
            when(type) {
                HissType.Interrupted -> Eln2Processing.VULCANIZING_AUTOCLAVE_FAST_STEAM_RELEASE_SOUND.get()
                HissType.VulcanizationSuccessful -> Eln2Processing.VULCANIZING_AUTOCLAVE_SUCCESS_STEAM_RELEASE_SOUND.get()
                HissType.Burnt -> Eln2Processing.VULCANIZING_AUTOCLAVE_BURNT_STEAM_RELEASE_SOUND.get()
            },
            SoundSource.BLOCKS,
            randomFloat(0.8f, 1.1f),
            randomFloat(0.9f, 1.1f)
        )
    }

    //#region Player Interaction

    /**
     * Opens/closes the door and plays sounds and sends particles if applicable.
     * */
    @ServerOnly // called on client, but ignored
    override fun applyWrench(wrench: org.eln2.mc.common.content.WrenchItem, context: UseOnContext): InteractionResult {
        if(context.level.isClientSide) {
            return InteractionResult.PASS
        }

        val stateMachine = stateMachine!!
        val previousDoor = stateMachine.doorState
        val wasProcessing = stateMachine.isProcessing

        return if(stateMachine.doorAction()) {
            val level = level as ServerLevel

            if(previousDoor == StateMachine.DoorState.Closed && wasProcessing) {
                val facing = representativeFacing
                val (x, y, z) = blockPos.toVector3d() + Vector3d(0.5) + facing.vector3d * 0.5
                val dist = Random.nextDouble(0.1, 0.4)

                repeat(10) {
                    val (incrX, incrY, incrZ) = Base6Direction3dMask
                        .perpendicular(facing).directionList[Random.nextInt(0, 4)]
                        .vector3d * Random.nextDouble(0.1, 0.5)


                    level.sendParticles(
                        ParticleTypes.LARGE_SMOKE,
                        x + incrX, y + incrY, z + incrZ,
                        Random.nextInt(3, 7),
                        facing.stepX.toDouble() * dist, facing.stepY.toDouble() * dist, facing.stepZ.toDouble() * dist,
                        0.3
                    )
                }

                hiss(HissType.Interrupted)
            }

            InteractionResult.SUCCESS
        }
        else {
            InteractionResult.FAIL
        }
    }

    /**
     * Takes out outputs or inserts items into the device, if the door is open.
     * */
    @ServerOnly
    fun onUseByPlayer(pPlayer: Player) : InteractionResult {
        val stateMachine = stateMachine!!

        if(stateMachine.doorState != StateMachine.DoorState.Open) {
            return InteractionResult.FAIL
        }

        val level = level as ServerLevel

        fun drop(stack: ItemStack) {
            val (x, y, z) = blockPos.toVector3d() + Vector3d(0.5) + representativeFacing.vector3d
            level.addItem(x, y, z, stack)
        }

        /**
         * Takes out the output, if possible:
         * */
        if(!inventoryHandler.getStackInSlot(OUTPUT_SLOT).isEmpty) {
            val stack = inventoryHandler.getStackInSlot(OUTPUT_SLOT)
            inventoryHandler.setStackInSlot(OUTPUT_SLOT, ItemStack.EMPTY)
            setChanged()

            drop(stack)

            // Potentially allows new stuff to happen:
            stateMachine.invalidateRecipe()
            stateMachine.changeLoadState(StateMachine.LoadState.Empty)

            return InteractionResult.SUCCESS
        }

        /**
         * Makes sure the input is clear. If not, we take it out:
         * */
        if(!inventoryHandler.getStackInSlot(INPUT_SLOT).isEmpty) {
            val stack = inventoryHandler.getStackInSlot(INPUT_SLOT)
            inventoryHandler.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY)
            setChanged()

            drop(stack)

            stateMachine.invalidateRecipe()
            stateMachine.changeLoadState(StateMachine.LoadState.Empty)

            return InteractionResult.SUCCESS
        }

        val stackInHand = pPlayer.mainHandItem

        if(stackInHand.isEmpty) {
            return InteractionResult.FAIL
        }

        /**
         * Checks if a recipe exists:
         * */
        inventoryHandler.setStackInSlot(INPUT_SLOT, stackInHand.copyWithCount(1))
        if(searchForRecipe().isEmpty) {
            inventoryHandler.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY)
            return InteractionResult.FAIL
        }

        /**
         * Accepts the recipe:
         * */
        stateMachine.invalidateRecipe()
        stateMachine.changeLoadState(StateMachine.LoadState.Ingredients)
        setChanged()

        stackInHand.eln2Consume(pPlayer)
        return InteractionResult.CONSUME
    }

    //#endregion

    var renderState: RenderState? = null
        private set

    @ClientOnly
    class RenderState {
        var doorStatePair = Pair(StateMachine.DoorState.Closed, StateMachine.DoorState.Closed)
        var loadState = StateMachine.LoadState.Empty
        var internalTemperature = 0.0
        var isProcessing = false

        var soundInstance: SimpleLoopingBlockEntitySoundInstance<VulcanizingAutoclaveMainBlockEntity>? = null
        val humSoundVolumeSmoother = FramerateIndependentSmoother1d(0.5)
    }

    @ClientOnly
    override val clientSidePacketHandlerLazy = createClientSideHandler()

    @ServerOnly
    private var stateMachine: StateMachine? = null
    private var savedStateMachineData: CompoundTag? = null // The same issue as the cell graph; we use the same workaround

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (pLevel.isClientSide) {
            renderState = RenderState()
        }
        else {
            stateMachine = StateMachine(pLevel as ServerLevel, this)
            if(savedStateMachineData != null) {
                stateMachine!!.loadTag(savedStateMachineData!!)
                savedStateMachineData = null
            }
        }
    }

    /**
     * Updates the working sound.
     * */
    fun clientTick() {
        val renderState = renderState
            ?: return

        renderState.humSoundVolumeSmoother.update(if(renderState.isProcessing) 1.0 else 0.0)

        if(renderState.soundInstance == null) {
            renderState.soundInstance = SimpleLoopingBlockEntitySoundInstance(this, Eln2Processing.VULCANIZING_AUTOCLAVE_HUM_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { e ->
                    it.soundInfo = SoundInfo(1.0, renderState.humSoundVolumeSmoother.value)
                }

                it.registerOnAudioManager()
            }
        }
    }

    @ServerOnly
    fun serverTick() {
        val stateMachine = stateMachine!!

        stateMachine.update()

        cell.doorClosed = !(stateMachine.doorState == StateMachine.DoorState.Opening || stateMachine.doorState == StateMachine.DoorState.Open)

        if(stateMachine.recipeInvalidated()) {
            stateMachine.isProcessing = false

            val recipe = searchForRecipe()

            /**
             * The recipe is no longer valid:
             * */
            if(recipe.isEmpty) {
                stateMachine.clearOperation()
            }
            /**
             * Start new operation:
             * */
            else {
                stateMachine.beginOperation(recipe.get())
            }
        }
        else {
            val operation = stateMachine.operation

            if(operation != null) {
                if(stateMachine.doorState == StateMachine.DoorState.Closed) {
                    val temperature = cell.heatPort.thermalWire.thermalBody.temperature

                    /**
                     * Tries to progress and finish the operation, if in the right thermal range:
                     * */
                    if(!temperature in operation.recipe.minTemperature..operation.recipe.maxTemperature) {
                        stateMachine.isProcessing = true
                        cell.isOperating = true

                        if(stateMachine.progressOperation()) {
                            inventoryHandler.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY)
                            inventoryHandler.setStackInSlot(OUTPUT_SLOT, operation.recipe.output.copy())
                            stateMachine.changeLoadState(StateMachine.LoadState.Results)
                            hiss(HissType.VulcanizationSuccessful)
                        }
                    }
                    /**
                     * Destroys the ingredients and outputs the burnt result:
                     * */
                    else if(temperature > operation.recipe.maxTemperature) {
                        stateMachine.clearOperation()
                        stateMachine.isProcessing = false
                        inventoryHandler.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY)
                        inventoryHandler.setStackInSlot(OUTPUT_SLOT, operation.recipe.burnOutput.copy())
                        stateMachine.changeLoadState(StateMachine.LoadState.Burnt)
                        hiss(HissType.Burnt)
                    }
                }
            }
            /**
             * Machine is idle:
             * */
            else {
                cell.isOperating = false
                stateMachine.isProcessing = false
            }
        }

        if(stateMachine.needsSave()) {
            setChanged()
        }
    }

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        fun modifyState(body: RenderState.() -> Unit) {
            val state = renderState!!
            body(state)
        }

        handler.withHandler<DoorStateSwitchMessage>(DoorStateSwitchMessage::deserialize) {
            modifyState {
                doorStatePair = Pair(
                    StateMachine.DoorState.entries[it.previousState],
                    StateMachine.DoorState.entries[it.newState]
                )
            }
        }

        handler.withHandler<ChangeLoadMessage>(ChangeLoadMessage::deserialize) {
            modifyState {
                loadState = StateMachine.LoadState.entries[it.targetState]
            }
        }

        handler.withHandler<IsProcessingPacket>(IsProcessingPacket::deserialize) {
            modifyState {
                isProcessing = it.value
            }
        }

        handler.withHandler<InternalTemperaturePacket>(InternalTemperaturePacket::deserialize) {
            modifyState {
                internalTemperature = it.temperature
            }
        }
    }

    override fun onInternalTemperatureChange(temperature: Quantity<Temperature>) {
        sendBulkPacket(InternalTemperaturePacket::serialize, InternalTemperaturePacket(!temperature))
    }

    data class InternalTemperaturePacket(val temperature: Double) {
        companion object {
            fun serialize(packet: InternalTemperaturePacket, buffer: FriendlyByteBuf) {
                buffer.writeDouble(packet.temperature)
            }

            fun deserialize(buffer: FriendlyByteBuf) = InternalTemperaturePacket(
                buffer.readDouble()
            )
        }
    }

    // onSyncSuggested
    override fun getUpdateTag(): CompoundTag {
        val stateMachine = stateMachine!!
        sendBulkPacket(DoorStateSwitchMessage::serialize, DoorStateSwitchMessage(stateMachine.doorState.index, stateMachine.doorState.index))
        sendBulkPacket(ChangeLoadMessage::serialize, ChangeLoadMessage(stateMachine.loadState.index))
        sendBulkPacket(IsProcessingPacket::serialize, IsProcessingPacket(stateMachine.isProcessing))

        if(cell.isFormed) {
            sendBulkPacket(InternalTemperaturePacket::serialize, InternalTemperaturePacket(!cell.heatPort.thermalWire.thermalBody.temperature))
        }

        return super.getUpdateTag()
    }

    @ServerOnly
    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        pTag.put(INVENTORY, inventoryHandler.serializeNBT())
        pTag.put(STATE_MACHINE, stateMachine!!.saveTag())
    }

    @ServerOnly
    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        inventoryHandler.deserializeNBT(pTag.getCompound(INVENTORY))
        savedStateMachineData = pTag.getCompound(STATE_MACHINE)
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        val sm = stateMachine!!
        builder.debugInIDE { "Door: ${sm.doorState}" }
        builder.debugInIDE { "Op: ${sm.operation}" }

        if(cell.isFormed) {
            builder.quantity(cell.heatPort.thermalWire.thermalBody.temperature)
        }
    }

    @ServerOnly @OnServerThread
    class StateMachine(val level: ServerLevel, val blockEntity: VulcanizingAutoclaveMainBlockEntity) {
        companion object {
            const val DOOR_TURN_RATE = 0.25

            private const val DOOR_STATE = "doorState"
            private const val DOOR_SWITCH_PROGRESS = "doorSwitchProgress"
            private const val RECIPE_INVALIDATED = "recipeInvalidated"
            private const val OPERATION = "operation"
            private const val OPERATION_RECIPE = "recipe"
            private const val OPERATION_PROGRESS = "progress"
            private const val LOAD_STATE = "loadState"
            private const val IS_PROCESSING = "isProcessing"
        }

        //#region Door State

        var doorState = DoorState.Closed
            private set

        var doorSwitchProgress = 0.0
            private set

        enum class DoorState(val index: Int) {
            Opening(0),
            Open(1),
            Closing(2),
            Closed(3);
        }

        //#endregion

        //#region Dirty

        private var saveChanged = false

        fun needsSave() : Boolean {
            val result = saveChanged
            saveChanged = false
            return result
        }

        //#endregion

        //#region Recipe

        private var recipeInvalidated = false

        fun recipeInvalidated() : Boolean {
            val result = recipeInvalidated

            if(result) {
                saveChanged = true
            }

            recipeInvalidated = false
            return result
        }

        fun invalidateRecipe() {
            if(recipeInvalidated) {
                return
            }

            recipeInvalidated = true
            saveChanged = true
        }

        class Operation(val recipe: VulcanizingRecipe, var progress: Double)

        var operation: Operation? = null
            private set

        fun clearOperation() {
            if(operation == null) {
                return
            }

            operation = null
            saveChanged = true
        }

        fun beginOperation(recipe: VulcanizingRecipe) {
            operation = Operation(recipe, 0.0)
            saveChanged = true
        }

        fun progressOperation() : Boolean {
            val operation = operation
                ?: error("Cannot progress operation!")

            val rate = 1.0 / operation.recipe.duration
            operation.progress += rate * (1.0 / 20.0)

            saveChanged = true

            if(operation.progress >= 1.0 || operation.progress.approxEq(1.0)) {
                clearOperation()
                return true
            }

            return false
        }

        //#endregion

        //#region Load

        var loadState = LoadState.Empty
            private set

        enum class LoadState(val index: Int) {
            Empty(0),
            Ingredients(1),
            Results(2),
            Burnt(3);
        }


        fun changeLoadState(targetState: LoadState) {
            if(targetState == loadState) {
                return
            }

            loadState = targetState
            blockEntity.sendBulkPacket(ChangeLoadMessage::serialize, ChangeLoadMessage(targetState.index))
            saveChanged = true
        }

        //#endregion

        //#region A/V

        var isProcessing = false
            set(value) {
                if(field != value) {
                    field = value
                    blockEntity.sendBulkPacket(IsProcessingPacket::serialize, IsProcessingPacket(value))
                    saveChanged = true
                }
            }

        data class IsProcessingPacket(val value: Boolean) {
            companion object {
                fun serialize(packet: IsProcessingPacket, buffer: FriendlyByteBuf) {
                    buffer.writeBoolean(packet.value)
                }

                fun deserialize(buffer: FriendlyByteBuf) = IsProcessingPacket(
                    buffer.readBoolean()
                )
            }
        }

        //#endregion

        private fun updateDoor() {
            if(doorState == DoorState.Open || doorState == DoorState.Closed) {
                return
            }

            doorSwitchProgress += DOOR_TURN_RATE * (1.0 / 20.0)
            saveChanged = true

            if(doorSwitchProgress > 1.0 || doorSwitchProgress.approxEq(1.0)) {
                doorSwitchProgress = 0.0

                if(doorState == DoorState.Opening) {
                    doorState = DoorState.Open

                    blockEntity.sendBulkPacket(DoorStateSwitchMessage::serialize, DoorStateSwitchMessage(
                        DoorState.Opening.index,
                        DoorState.Open.index
                    ))
                }
                else {
                    doorState = DoorState.Closed

                    blockEntity.sendBulkPacket(DoorStateSwitchMessage::serialize, DoorStateSwitchMessage(
                        DoorState.Closing.index,
                        DoorState.Closed.index
                    ))

                    invalidateRecipe()
                }
            }
        }

        fun doorAction() = when(doorState) {
            DoorState.Open -> {
                doorState = DoorState.Closing
                blockEntity.sendBulkPacket(DoorStateSwitchMessage::serialize, DoorStateSwitchMessage(
                    DoorState.Open.index,
                    DoorState.Closing.index
                ))
                true
            }
            DoorState.Closed ->  {
                doorState = DoorState.Opening
                blockEntity.sendBulkPacket(DoorStateSwitchMessage::serialize, DoorStateSwitchMessage(
                    DoorState.Closed.index,
                    DoorState.Opening.index
                ))
                clearOperation()
                true
            }
            else -> false
        }

        fun update() {
            updateDoor()
        }

        fun saveTag() : CompoundTag {
            val tag = CompoundTag()
            tag.putInt(DOOR_STATE, doorState.index)
            tag.putDouble(DOOR_SWITCH_PROGRESS, doorSwitchProgress)
            tag.putBoolean(RECIPE_INVALIDATED, recipeInvalidated)

            operation?.also {
                val operationTag = CompoundTag()
                operationTag.putResourceLocation(OPERATION_RECIPE, it.recipe.recipeId)
                operationTag.putDouble(OPERATION_PROGRESS, it.progress)
                tag.put(OPERATION, operationTag)
            }

            tag.putInt(LOAD_STATE, loadState.index)
            tag.putBoolean(IS_PROCESSING, isProcessing)

            return tag
        }

        fun loadTag(tag: CompoundTag) {
            doorState = DoorState.entries[tag.getInt(DOOR_STATE)]
            doorSwitchProgress = tag.getDouble(DOOR_SWITCH_PROGRESS)
            recipeInvalidated = tag.getBoolean(RECIPE_INVALIDATED)

            if(tag.contains(OPERATION)) {
                val operationTag = tag.getCompound(OPERATION)
                val recipeId = operationTag.getResourceLocation(OPERATION_RECIPE)
                val progress = operationTag.getDouble(OPERATION_PROGRESS)

                val recipe = level.recipeManager.byKey(recipeId)

                if(recipe.isEmpty) {
                    error("Could not resolve saved recipe $recipeId!")
                }

                operation = Operation(recipe.get() as VulcanizingRecipe, progress)
            }

            loadState = LoadState.entries[tag.getInt(LOAD_STATE)]
            isProcessing = tag.getBoolean(IS_PROCESSING)
        }
    }
}

class VulcanizingAutoclaveMainBlockEntityVisual(
    ctx: VisualizationContext,
    blockEntity: VulcanizingAutoclaveMainBlockEntity,
    partialTick: Float
) :
    dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual<VulcanizingAutoclaveMainBlockEntity>(ctx, blockEntity, partialTick),
    ShaderLightVisual,
    SimpleDynamicVisual
{
    companion object {
        val DOOR_PIVOT = lazy {
            var minX = Double.POSITIVE_INFINITY
            var maxZ = Double.NEGATIVE_INFINITY

            iterateVertexPositions(VULCANIZING_AUTOCLAVE_DOOR.get()) { (x, y, z) ->
                if(x < minX) {
                    minX = x
                }

                if(z > maxZ) {
                    maxZ = z
                }
            }

            Pair(minX, maxZ)
        }
    }

    val body: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(FlwModels.VULCANIZING_AUTOCLAVE_BODY, FlwMaterials.CUTOUT_TRANSLUCENT_SMOOTH_LIT))
        .createInstance()
        .also {
            it.translate(visualPosition)
            it.center()
            it.rotateToFace(blockEntity.representativeFacing.opposite)
            it.uncenter()
        }

    val door: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(FlwModels.VULCANIZING_AUTOCLAVE_DOOR, FlwMaterials.CUTOUT_SMOOTH_LIT))
        .createInstance()

    var load = StateMachine.LoadState.Empty
    var loadInstance: TransformedInstance? = null

    val incandescent: TransformedLightOverrideInstance = visualizationContext.instancerProvider()
        .instancer(FlwInstanceTypes.TRANSFORMED_LIGHT_OVERRIDE, PartialModelHelper.applyMaterial(FlwModels.VULCANIZING_AUTOCLAVE_INCANDESCENT, FlwMaterials.SMOOTH_LIT))
        .createInstance()
        .also {
            it.translate(visualPosition)
            it.center()
            it.rotateToFace(blockEntity.representativeFacing.opposite)
            it.uncenter()
        }

    private var doorOpen = false
    private var doorOpenParameter = 0.0
    private val stopwatch = Stopwatch()

    private var temperature = 0.0

    private fun rotateDoorEaseInOut() {
        val (pivotX, pivotZ) = DOOR_PIVOT.value

        door.setIdentityTransform()
        door.translate(visualPosition)
        door.center()
        door.rotateToFace(blockEntity.representativeFacing.opposite)
        door.uncenter()

        door.translate(pivotX, 0.0, pivotZ)
        door.rotateY( map(easeInOutCubic(doorOpenParameter), 0.0, 1.0, 0.0, PI / 2.0).toFloat())
        door.translate(-pivotX, 0.0, -pivotZ)
        door.setChanged()
    }

    init {
        rotateDoorEaseInOut()
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

    private fun updateDoor() {
        val dt = stopwatch.sample()

        val renderState = blockEntity.renderState!!

        val (psPreviousDoorState, rsTargetDoorState) = renderState.doorStatePair
        val targetDoorOpen = rsTargetDoorState == StateMachine.DoorState.Opening || rsTargetDoorState == StateMachine.DoorState.Open
        val previousDoorOpen = psPreviousDoorState == StateMachine.DoorState.Opening || psPreviousDoorState == StateMachine.DoorState.Open

        if(targetDoorOpen != doorOpen) {
            doorOpen = targetDoorOpen

            if(targetDoorOpen == previousDoorOpen) {
                // Apply immediately:
                doorOpenParameter = if(targetDoorOpen) 1.0 else 0.0
                rotateDoorEaseInOut()
                return
            }
        }

        val parametricSpeed = StateMachine.DOOR_TURN_RATE * if(doorOpen) 1.0 else -1.0
        val newDoorSwingProgress = (doorOpenParameter + parametricSpeed * !dt).coerceIn(0.0, 1.0)

        if(newDoorSwingProgress != doorOpenParameter) {
            doorOpenParameter = newDoorSwingProgress
            rotateDoorEaseInOut()
        }
    }

    private fun updateLoad() {
        val target = blockEntity.renderState!!.loadState

        if(target == load) {
            return
        }

        load = target
        loadInstance?.delete()

        val model = when(target) {
            StateMachine.LoadState.Empty -> {
                return
            }
            StateMachine.LoadState.Ingredients -> {
                FlwModels.VULCANIZING_AUTOCLAVE_LATEX_SULFUR
            }
            StateMachine.LoadState.Results -> {
                FlwModels.VULCANIZING_AUTOCLAVE_RUBBER
            }
            StateMachine.LoadState.Burnt -> {
                FlwModels.VULCANIZING_AUTOCLAVE_BURNT
            }
        }

        loadInstance = visualizationContext.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(model, FlwMaterials.OIT_NON_MIP_SMOOTH_LIT))
            .createInstance()
            .also {
                it.translate(visualPosition)
                it.center()
                it.rotateToFace(blockEntity.representativeFacing.opposite)
                it.uncenter()
            }
    }

    private fun updateIncandescent() {
        val targetTemperature = blockEntity.renderState!!.internalTemperature

        if(targetTemperature == temperature) {
            return
        }

        incandescent.colorWithOverride(
            ThermalTint.DEFAULT_LIGHT_OVERRIDE,
            Quantity(targetTemperature, KELVIN)
        )

        incandescent.setChanged()
    }

    override fun beginFrame(p0: DynamicVisual.Context?) {
        updateDoor()
        updateLoad()
        updateIncandescent()
    }

    override fun updateLight(p0: Float) {
        // Smooth lighting is a curse, we have to use it for everything once we use it for something
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        p0.accept(body)
        p0.accept(door)
    }

    override fun _delete() {
        body.delete()
        door.delete()
        loadInstance?.delete()
        incandescent.delete()
    }
}

//#endregion
