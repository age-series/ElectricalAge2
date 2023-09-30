package org.eln2.mc.common.parts.foundation

import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.eln2.mc.*
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.client.render.foundation.MultipartBlockEntityInstance
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.cells.foundation.Cell
import org.eln2.mc.common.cells.foundation.CellGraphManager
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.network.serverToClient.BulkMessages
import org.eln2.mc.common.network.serverToClient.PacketHandler
import org.eln2.mc.common.network.serverToClient.PacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.PartMessage
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import kotlin.math.PI

/**
 * Encapsulates all the data associated with a part's placement.
 * */
data class PartPlacementInfo(
    val position: BlockPos,
    val face: Direction,
    val horizontalFacing: Direction,
    val level: Level,
    val multipart: MultipartBlockEntity,
) {
    fun createLocator() = LocatorSet().apply {
        withLocator(position)
        withLocator(FacingLocator(horizontalFacing)) // is this right?
        withLocator(face)
    }
}

enum class PartUpdateType(val id: Int) {
    Add(1),
    Remove(2);

    companion object {
        fun fromId(id: Int): PartUpdateType {
            return when (id) {
                Add.id -> Add
                Remove.id -> Remove
                else -> error("Invalid part update type id $id")
            }
        }
    }
}

data class PartUpdate(val part: Part<*>, val type: PartUpdateType)
data class PartUseInfo(val player: Player, val hand: InteractionHand)

object PartGeometry {
    /**
     * @see Part.modelBoundingBox
     * */
    fun modelBoundingBox(sizeActual: Vec3, facingWorld: Direction, faceWorld: Direction): AABB {
        val center = Vec3(0.0, 0.0, 0.0)
        val halfSize = sizeActual / 2.0
        return AABB(center - halfSize, center + halfSize)
            .transformed(facingRotation(facingWorld))
            .transformed(faceWorld.rotation.toJoml())
            .move(faceOffset(sizeActual, faceWorld))
    }

    /**
     * @see Part.facingRotation
     * */
    fun facingRotation(facingWorld: Direction) = Quaternionf(
        AxisAngle4f(
            when (facingWorld) {
                Direction.NORTH -> 0.0
                Direction.SOUTH -> PI
                Direction.WEST -> PI / 2.0
                Direction.EAST -> -PI / 2.0
                else -> error("Invalid horizontal facing $facingWorld")
            }.toFloat(),
            Vector3f(0.0f, 1.0f, 0.0f)
        )
    )

    fun faceOffset(sizeActual: Vec3, faceWorld: Direction): Vec3 {
        val halfSize = sizeActual / 2.0

        val positiveOffset = halfSize.y
        val negativeOffset = 1 - halfSize.y

        return when (val axis = faceWorld.axis) {
            Direction.Axis.X -> Vec3(
                (if (faceWorld.axisDirection == Direction.AxisDirection.POSITIVE) positiveOffset else negativeOffset),
                0.5,
                0.5
            )

            Direction.Axis.Y -> Vec3(
                0.5,
                (if (faceWorld.axisDirection == Direction.AxisDirection.POSITIVE) positiveOffset else negativeOffset),
                0.5
            )

            Direction.Axis.Z -> Vec3(
                0.5,
                0.5,
                (if (faceWorld.axisDirection == Direction.AxisDirection.POSITIVE) positiveOffset else negativeOffset)
            )

            else -> error("Invalid axis $axis")
        }
    }

    fun gridBoundingBox(sizeActual: Vec3, facingWorld: Direction, faceWorld: Direction, posWorld: BlockPos): AABB =
        modelBoundingBox(sizeActual, facingWorld, faceWorld).move(posWorld)

    fun worldBoundingBox(sizeActual: Vec3, facingWorld: Direction, faceWorld: Direction, posWorld: BlockPos): AABB =
        gridBoundingBox(sizeActual, facingWorld, faceWorld, posWorld).move(Vec3(-0.5, 0.0, -0.5))
}

/**
 * Parts are entity-like units that exist in a multipart entity. They are similar to normal block entities,
 * but up to 6 can exist in the same block space.
 * They are placed on the inner faces of a multipart container block space.
 * */
abstract class Part<Renderer : PartRenderer>(val id: ResourceLocation, val placement: PartPlacementInfo) : DataEntity {
    companion object {
        fun createPartDropStack(id: ResourceLocation, saveTag: CompoundTag?, count: Int = 1): ItemStack {
            val item = PartRegistry.getPartItem(id)
            val stack = ItemStack(item, count)

            stack.tag = saveTag

            return stack
        }
    }

    /**
     * [PacketHandler] for server -> client packets.
     * It will receive messages if and only if the base [handleBulkMessage] gets called when a bulk message is received.
     * */
    @ClientOnly
    private val packetHandlerLazy = lazy {
        val builder = PacketHandlerBuilder()
        registerPackets(builder)
        builder.build()
    }

    @ClientOnly
    protected open fun registerPackets(builder: PacketHandlerBuilder) {}

    /**
     * Enqueues a bulk packet to be sent to the client.
     * This makes sense to call if and only if [P] is registered on the client
     * in [registerPackets], and the default behavior of [handleBulkMessage] gets executed.
     * */
    @ServerOnly
    protected inline fun <reified P> sendBulkPacket(packet: P) {
        enqueueBulkMessage(
            PacketHandler.encode(packet)
        )
    }

    @ClientOnly
    open fun handleBulkMessage(msg: ByteArray) {
        packetHandlerLazy.value.handle(msg)
    }

    fun enqueueBulkMessage(payload: ByteArray) {
        require(!placement.level.isClientSide) { "Tried to send bulk message from client" }
        BulkMessages.enqueuePartMessage(
            placement.level as ServerLevel,
            PartMessage(placement.position, placement.face, payload)
        )
    }

    /**
     * This is the size that will be used to create the bounding box for this part.
     * It should not exceed the block size, but that is not enforced.
     * */
    abstract val partSize: Vec3

    private var cachedShape: VoxelShape? = null

    var brightness: Int = 0
        private set

    /**
     * This gets the relative direction towards the global direction, taking into account the facing of this part.
     * @param dirWorld A global direction.
     * @return The relative direction towards the global direction.
     * */
    fun getDirectionActual(dirWorld: Direction): Base6Direction3d {
        return Base6Direction3d.fromForwardUp(
            placement.horizontalFacing,
            placement.face,
            dirWorld
        )
    }

    /**
     * This is the bounding box of the part, rotated and placed
     * on the inner face. It is not translated to the position of the part in the world (it is a local frame)
     * */
    private val modelBoundingBox: AABB
        get() = PartGeometry.modelBoundingBox(partSize, placement.horizontalFacing, placement.face)

    /**
     * @return The local Y rotation due to facing.
     * */
    val facingRotation: Quaternionf get() = PartGeometry.facingRotation(placement.horizontalFacing)

    /**
     * @return The offset towards the placement face, calculated using the base size.
     * */
    private val txFace: Vec3 get() = PartGeometry.faceOffset(partSize, placement.face)

    /**
     * This is the bounding box of the part, in its block position.
     * */
    val gridBoundingBox: AABB
        get() = PartGeometry.gridBoundingBox(
            partSize,
            placement.horizontalFacing,
            placement.face,
            placement.position
        )

    /**
     * This is the bounding box of the part, in final world coordinates.
     * */
    val worldBoundingBox: AABB
        get() = PartGeometry.worldBoundingBox(
            partSize,
            placement.horizontalFacing,
            placement.face,
            placement.position
        )

    /**
     * Gets the shape of this part. Used for block highlighting and collisions.
     * The default implementation creates a shape from the model bounding box and caches it.
     * */
    open val shape: VoxelShape
        get() {
            if (cachedShape == null) {
                cachedShape = Shapes.create(modelBoundingBox)
            }

            return cachedShape!!
        }

    /**
     * Called when the part is right-clicked by a living entity.
     * */
    open fun onUsedBy(context: PartUseInfo): InteractionResult {
        return InteractionResult.SUCCESS
    }

    /**
     * This method is used for saving the part.
     * @return A compound tag with all the save data for this part, or null, if no data needs saving.
     * */
    @ServerOnly
    open fun getSaveTag(): CompoundTag? {
        return null
    }

    /**
     * This method is called to restore the part data from the compound tag.
     * This method is used on both logical sides. The client only receives this call
     * when the initial chunk synchronization happens.
     * @param tag The custom data tag, as created by getSaveTag.
     * */
    open fun loadFromTag(tag: CompoundTag) {}

    /**
     * This method is called when this part is invalidated, and in need of synchronization to clients.
     * You will receive this tag in *handleSyncTag* on the client, _if_ the tag is not null.
     * @return A compound tag with all part updates. You may return null, but that might indicate an error in logic.
     * This method is called only when an update is _requested_, so there should be data in need of synchronization.
     *
     * */
    @ServerOnly
    open fun getSyncTag(): CompoundTag? {
        return null
    }

    /**
     * This method is called on the client after the server logic of this part requested an update, and the update was received.
     * @param tag The custom data tag, as returned by the getSyncTag method on the server.
     * */
    @ClientOnly
    open fun handleSyncTag(tag: CompoundTag) {
    }

    /**
     * This method invalidates the saved data of the part.
     * This ensures that the part will be saved to the disk.
     * */
    @ServerOnly
    fun setSaveDirty() {
        if (placement.level.isClientSide) {
            error("Cannot save on the client")
        }

        placement.multipart.setChanged()
    }

    /**
     * This method synchronizes all changes from the server to the client.
     * It results in calls to the *getSyncTag* **(server)** / *handleSyncTag* **(client)** combo.
     * */
    @ServerOnly
    fun setSyncDirty() {
        if (placement.level.isClientSide) {
            error("Cannot sync changes from client to server!")
        }

        placement.multipart.enqueuePartSync(placement.face)
    }

    /**
     * This method invalidates the saved data and synchronizes to clients.
     * @see setSaveDirty
     * @see setSyncDirty
     * */
    @ServerOnly
    fun setSyncAndSaveDirty() {
        setSyncDirty()
        setSaveDirty()
    }

    /**
     *  Called on the server when the part is placed.
     * */
    @ServerOnly
    open fun onPlaced() {}

    /**
     * Called on the server when the part finished loading from disk
     * */
    @ServerOnly
    open fun onLoaded() {}

    /**
     * Called when this part is added to a multipart.
     * */
    open fun onAdded() {}

    /**
     * Called when this part is being unloaded.
     * */
    open fun onUnloaded() {}

    /**
     * Called when the part is destroyed (broken).
     * */
    open fun onBroken() {}

    /**
     * Called when the part is removed from the multipart.
     * */
    open fun onRemoved() {}

    /**
     * Called when this part is received and added to the client multipart, just before rendering set-up is enqueued.
     * */
    @ClientOnly
    open fun onAddedToClient() {}

    /**
     * Called when synchronization is suggested. This happens when a client enters the viewing area of the part.
     * */
    @ServerOnly
    open fun onSyncSuggested() {}

    @ClientOnly
    protected var cachedRenderer: Renderer? = null
        private set

    /**
     * Gets the renderer instance for this part.
     * By default, it calls the createRenderer method, and caches the result.
     * */
    @ClientOnly
    open val renderer: Renderer
        get() {
            if (!placement.level.isClientSide) {
                error("Tried to get renderer on non-client side!")
            }

            if (cachedRenderer == null) {
                cachedRenderer = createRenderer()
                initializeRenderer()
            }

            return cachedRenderer!!
        }

    /**
     * Creates a renderer instance for this part.
     * @return A new instance of the part renderer.
     * */
    @ClientOnly
    abstract fun createRenderer(): Renderer

    /**
     * Called to initialize the [Renderer], right after it is created by [createRenderer]
     * */
    @ClientOnly
    open fun initializeRenderer() { }

    @ClientOnly
    open fun destroyRenderer() {
        cachedRenderer?.remove()
        cachedRenderer = null
    }

    override val dataNode: DataNode = DataNode()
}

/**
 * This is a factory for parts. It also has the size used to validate placement (part-part collisions).
 * */
abstract class PartProvider {
    val id: ResourceLocation get() = PartRegistry.getId(this)

    /**
     * Used to create a new instance of the part. Called when the part is placed
     * or when the multipart entity is loading from disk.
     * @param context The placement context of this part.
     * @return Unique instance of the part.
     */
    abstract fun create(context: PartPlacementInfo): Part<*>

    /**
     * This is the size used to validate placement. This is different from baseSize, because
     * you can implement a visual placement margin here.
     * */
    abstract val placementCollisionSize: Vec3

    open fun canPlace(context: PartPlacementInfo): Boolean = true
}

/**
 * The basic part provider uses a functional interface as part factory.
 * Often, the part's constructor can be passed in as factory.
 * */
open class BasicPartProvider(
    val factory: ((id: ResourceLocation, context: PartPlacementInfo) -> Part<*>),
    final override val placementCollisionSize: Vec3,
) : PartProvider() {
    override fun create(context: PartPlacementInfo) = factory(id, context)
}

/**
 * Represents a part that has a cell.
 * */
interface PartCellContainer<C : Cell> {
    /**
     * This is the cell owned by the part.
     * */
    val cell: Cell

    /**
     * Indicates if the cell is available (loaded).
     * */
    val hasCell: Boolean

    /**
     * @return The provider associated with the cell.
     * */
    val provider: CellProvider<C>

    /**
     * Indicates whether this part allows planar connections.
     * @see CellPartConnectionMode.Planar
     * */
    val allowPlanarConnections: Boolean

    /**
     * Indicates whether if this part allows inner connections.
     * @see CellPartConnectionMode.Inner
     * */
    val allowInnerConnections: Boolean

    /**
     * Indicates if this part allows wrapped connections.
     * @see CellPartConnectionMode.Wrapped
     * */
    val allowWrappedConnections: Boolean

    fun onConnected(remoteCell: Cell)
    fun onDisconnected(remoteCell: Cell)
}

/**
 * This part represents a simulation object. It can become part of a cell network.
 * */
abstract class CellPart<C: Cell, R : PartRenderer>(
    id: ResourceLocation,
    placement: PartPlacementInfo,
    final override val provider: CellProvider<C>,
) : Part<R>(id, placement), PartCellContainer<C>, WailaEntity {
    companion object {
        private const val GRAPH_ID = "GraphID"
        private const val CUSTOM_SIMULATION_DATA = "SimulationData"
    }

    /**
     * The actual cell contained within this part.
     * It only exists on the server (it is a simulation-only item)
     * */
    @ServerOnly
    final override lateinit var cell: C

    final override val hasCell: Boolean
        get() = this::cell.isInitialized

    val cellPos = placement.createLocator()

    /**
     * Used by the loading procedures.
     * */
    @ServerOnly
    private lateinit var loadGraphId: UUID

    @ServerOnly
    private var customSimulationData: CompoundTag? = null

    protected var isAlive = false
        private set

    /**
     * Notifies the cell of the new container.
     * */
    override fun onPlaced() {
        cell = provider.create(cellPos, BiomeEnvironments.getInformationForBlock(placement.level, cellPos).fieldMap())
        cell.container = placement.multipart
        isAlive = true
        acquireCell()
    }

    /**
     * Notifies the cell that the container has been removed.
     * */
    override fun onUnloaded() {
        if (hasCell) {
            cell.onContainerUnloading()
            cell.container = null
            cell.onContainerUnloaded()
            cell.unbindGameObjects()
            isAlive = false
            onCellReleased()
        }
    }

    override fun onRemoved() {
        super.onRemoved()

        isAlive = false
    }

    /**
     * The saved data includes the Graph ID. This is used to fetch the cell after loading.
     * */
    override fun getSaveTag(): CompoundTag? {
        if (!hasCell) {
            LOG.error("Saving, but cell not initialized!")
            return null
        }

        val tag = CompoundTag()

        tag.putUUID(GRAPH_ID, cell.graph.id)

        saveCustomSimData()?.also {
            tag.put(CUSTOM_SIMULATION_DATA, it)
        }

        return tag
    }

    /**
     * This method gets the graph ID from the saved data.
     * The level is not available at this point, so we defer cell fetching to the onLoaded method.
     * */
    override fun loadFromTag(tag: CompoundTag) {
        if (placement.level.isClientSide) {
            return
        }

        if (tag.contains(GRAPH_ID)) {
            loadGraphId = tag.getUUID("GraphID")
        } else {
            LOG.info("Part at $cellPos did not have saved data")
        }

        tag.useSubTagIfPreset(CUSTOM_SIMULATION_DATA) { customSimulationData = it }
    }

    /**
     * This is the final stage of loading. We have the level, so we can fetch the cell using the saved data.
     * */
    @Suppress("UNCHECKED_CAST")
    override fun onLoaded() {
        if (placement.level.isClientSide) {
            return
        }

        cell = if (!this::loadGraphId.isInitialized) {
            LOG.error("Part cell not initialized!")
            // Should we blow up the game?
            provider.create(cellPos, BiomeEnvironments.getInformationForBlock(placement.level, cellPos).fieldMap())
        } else {
            CellGraphManager.getFor(placement.level as ServerLevel)
                .getGraph(loadGraphId)
                .getCell(cellPos) as C
        }

        cell.container = placement.multipart
        cell.onContainerLoaded()

        if (customSimulationData != null) {
            LOG.info(customSimulationData)
            loadCustomSimData(customSimulationData!!)
            customSimulationData = null
        }

        isAlive = true
        acquireCell()

        cell.bindGameObjects(listOf(this, placement.multipart))
    }

    open fun saveCustomSimData(): CompoundTag? {
        return null
    }

    open fun loadCustomSimData(tag: CompoundTag) {}

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        if (hasCell) {
            this.cell.appendWaila(builder, config)
        }
    }

    override fun onConnected(remoteCell: Cell) {}

    override fun onDisconnected(remoteCell: Cell) {}

    private fun acquireCell() {
        require(!dataNode.children.any { it == cell.dataNode }) { "Duplicate cell set" }
        dataNode.withChild(cell.dataNode)
        onCellAcquired()
    }

    open fun onCellAcquired() {}
    open fun onCellReleased() {}

    override val allowPlanarConnections = true
    override val allowInnerConnections = true
    override val allowWrappedConnections = true
}


open class BasicCellPart<C: Cell, R : PartRenderer>(
    id: ResourceLocation,
    placementContext: PartPlacementInfo,
    override val partSize: Vec3,
    provider: CellProvider<C>,
    private val rendererFactory: PartRendererFactory<R>,
) :
    CellPart<C, R>(id, placementContext, provider) {
    override fun createRenderer(): R {
        return rendererFactory.create(this)
    }
}

/**
 * A connection mode represents the way two cells may be connected.
 * */
enum class CellPartConnectionMode(val index: Int) {
    /**
     * The connection mode could not be identified.
     * */
    Unknown(0),

    /**
     * Planar connections are connections between units placed on the same plane, in adjacent containers.
     * */
    Planar(1),

    /**
     * Inner connections are connections between units placed on perpendicular faces in the same container.
     * */
    Inner(2),

    /**
     * Wrapped connections are connections between units placed on perpendicular faces of the same block.
     * Akin to a connection wrapping around the corner of the substrate block.
     * */
    Wrapped(3);

    companion object {
        val byId = values().toList()
    }
}

@JvmInline
value class CellPartConnectionInfo(val data: Int) {
    val mode get() = CellPartConnectionMode.byId[(data and 3)]
    val actualDirActualPlr get() = Base6Direction3d.byId[(data shr 2)]
    constructor(mode: CellPartConnectionMode, actualDirActualPlr: Base6Direction3d) : this(mode.index or (actualDirActualPlr.id shl 2))
}

fun solveCellPartConnection(actualCell: Cell, remoteCell: Cell): CellPartConnectionInfo {
    val actualPosWorld = actualCell.locator.requireLocator<BlockLocator>()
    val remotePosWorld = remoteCell.locator.requireLocator<BlockLocator>()
    val actualFaceWorld = actualCell.locator.requireLocator<FaceLocator>()
    val remoteFaceWorld = remoteCell.locator.requireLocator<FaceLocator>()

    val mode: CellPartConnectionMode

    val dir = if (actualPosWorld == remotePosWorld) {
        if (actualFaceWorld == remoteFaceWorld) {
            error("Invalid configuration") // Cannot have multiple parts in same face, something is super wrong up the chain
        }

        // The only mode that uses this is the Inner mode.
        // But, if we find that the two directions are not perpendicular, this is not Inner, and as such, it is Unknown:
        if (actualFaceWorld == remoteFaceWorld.opposite) {
            // This is unknown. Inner connections happen between parts on perpendicular faces:
            mode = CellPartConnectionMode.Unknown
            actualFaceWorld
        } else {
            // This is Inner:
            mode = CellPartConnectionMode.Inner
            remoteFaceWorld.opposite
        }
    } else {
        // They are planar if the normals match up.
        if (actualFaceWorld == remoteFaceWorld) {
            val txActualTarget = actualPosWorld.directionTo(remotePosWorld)

            if (txActualTarget == null) {
                // They are not in-plane, which means Unknown
                mode = CellPartConnectionMode.Unknown
                actualFaceWorld
            } else {
                // This is planar:
                mode = CellPartConnectionMode.Planar
                txActualTarget
            }
        } else {
            // Scan for Wrapped connections. If those do not match, then Unknown.
            val solution = Base6Direction3dMask.perpendicular(actualFaceWorld)
                .directionList
                .firstOrNull { (actualPosWorld + it - actualFaceWorld) == remotePosWorld }

            if (solution != null) {
                // Solution was found, this is wrapped:
                mode = CellPartConnectionMode.Wrapped
                solution
            } else {
                mode = CellPartConnectionMode.Unknown
                actualFaceWorld
            }
        }
    }

    return CellPartConnectionInfo(
        mode,
        Base6Direction3d.fromForwardUp(
            actualCell.locator.requireLocator<FacingLocator>().forwardWorld,
            actualFaceWorld,
            dir
        )
    )
}

/**
 * Represents a part that can be ticked by the multipart block entity.
 * @see MultipartBlockEntity.addTicker
 * @see MultipartBlockEntity.removeTicker
 * */
interface TickablePart {
    fun tick()
}

enum class ItemPersistentPartLoadOrder {
    /**
     * The data is loaded before the simulation is built.
     * */
    BeforeSim,
    /**
     * The data is loaded after the simulation is built.
     * */
    AfterSim
}

interface ItemPersistentPart {
    val order: ItemPersistentPartLoadOrder

    /**
     * Saves the part to an item tag.
     * */
    fun saveToItemNbt(tag: CompoundTag)

    /**
     * Loads the part from the item tag.
     * @param tag The saved tag. Null if no data was present in the item (possibly because the item was newly created)
     * */
    fun loadFromItemNbt(tag: CompoundTag?)
}

/**
 * This is the per-part renderer. One is created for every instance of a part.
 * The various methods may be called from separate threads.
 * Thread safety must be guaranteed by the implementation.
 * */
@CrossThreadAccess
abstract class PartRenderer {
    lateinit var multipart: MultipartBlockEntityInstance
        private set

    val hasMultipart get() = this::multipart.isInitialized

    fun isSetupWith(multipartBlockEntityInstance: MultipartBlockEntityInstance): Boolean {
        return this::multipart.isInitialized && multipart == multipartBlockEntityInstance
    }

    /**
     * Called when the part is picked up by the renderer.
     * @param multipart The renderer instance.
     * */
    fun setupRendering(multipart: MultipartBlockEntityInstance) {
        this.multipart = multipart
        setupRendering()
    }

    /**
     * Called to set up rendering, when [multipart] has been acquired.
     * */
    protected open fun setupRendering() { }

    /**
     * Called each frame. This method may be used to animate parts or to
     * apply general per-frame updates.
     * */
    open fun beginFrame() { }

    /**
     * Called when a re-light is required.
     * This happens when the light sources are changed.
     * @return A list of models that need relighting, or null if none do so.
     * */
    open fun getModelsToRelight(): List<FlatLit<*>>? = null

    open fun afterRelight(models: List<FlatLit<*>>) {}

    /**
     * Called when the renderer is no longer required.
     * All resources must be released here.
     * */
    abstract fun remove()
}

fun interface PartRendererFactory<R : PartRenderer> {
    fun create(part: Part<R>): R
}

fun basicPartRenderer(model: PartialModel, downOffset: Double): PartRendererFactory<BasicPartRenderer> {
    return PartRendererFactory { part ->
        BasicPartRenderer(part, model).also { it.downOffset = downOffset }
    }
}
