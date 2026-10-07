package org.eln2.mc.common.blocks.foundation

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraftforge.client.extensions.common.IClientBlockExtensions
import org.ageseries.libage.data.LocatorBuilder
import org.ageseries.libage.data.put
import org.ageseries.libage.mathematics.geometry.BoundingBox3d
import org.ageseries.libage.mathematics.geometry.OrientedBoundingBox3d
import org.ageseries.libage.mathematics.geometry.Rotation2d
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.LOG
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.grids.*
import org.eln2.mc.common.specs.foundation.SpecGeometry
import org.eln2.mc.Locators
import org.eln2.mc.extensions.eln2StandardBlockProperties
import org.eln2.mc.extensions.toVector3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.maskXY
import org.eln2.mc.mathematics.toHorizontalFacing
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Base class for the cell block. Doesn't have any block state, like placement direction.
 * */
abstract class CellBlock<C : Cell>(p : Properties? = null) : Block(p ?: eln2StandardBlockProperties().noOcclusion()), EntityBlock {
    abstract fun getCellProvider(): CellProvider<C>

    /**
     * Appends the custom block state data to the locator builder.
     * Should append any placement information, except the block position (it is implicitly appended).
     * */
    abstract fun appendLocatorData(state: BlockState, builder: LocatorBuilder)

    @Suppress("UNCHECKED_CAST")
    final override fun setPlacedBy(level: Level, blockPos: BlockPos, blockState: BlockState, entity: LivingEntity?, itemStack: ItemStack) {
        val cellEntity = level.getBlockEntity(blockPos)!! as CellBlockEntity<C>
        cellEntity.setPlacedBy(level, getCellProvider())
    }

    /**
     * Called when the block state **changes**. This doesn't mean our block was removed (set to air).
     * We will check if our block state just changed, but the block stays.
     * */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRemove(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pNewState: BlockState,
        pMovedByPiston: Boolean,
    ) {
        if(!pState.`is`(pNewState.block)) {
            markCellDestroyed(pLevel, pPos)
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston)
    }

    fun markCellDestroyed(level: Level, blockPos: BlockPos) {
        if (!level.isClientSide) {
            val cellEntity = level.getBlockEntity(blockPos)!! as? CellBlockEntity<*>
            cellEntity?.setDestroyed()
        }
    }

    /**
     * Implements the default spatial neighbor scan. Called by the default implementation of [CellBlockEntity.spatialNeighborScan].
     * This scan is spatial, meaning the search is algorithm uses the custom block state.
     * */
    abstract fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell)

    /**
     * Replaced by [onRemove].
     * */
    /*
    override fun onBlockExploded(blockState: BlockState?, level: Level?, blockPos: BlockPos?, explosion: Explosion?) {
        markCellDestroyed(level ?: error("Level was null"), blockPos ?: error("Position was null"))
        super.onBlockExploded(blockState, level, blockPos, explosion)
    }
    */

    /**
     * Replaced by [onRemove].
     * */
    /*
    override fun onDestroyedByPlayer(blockState: BlockState?, level: Level?, blockPos: BlockPos?, player: Player?, willHarvest: Boolean, fluidState: FluidState?): Boolean {
        markCellDestroyed(
            level ?: error(DEBUGGER_BREAK("Level was null")),
            blockPos ?: error(DEBUGGER_BREAK("Position was null"))
        )
        return super.onDestroyedByPlayer(blockState, level, blockPos, player, willHarvest, fluidState)
    }
    */
}

/**
 * Cell block that is always facing west, east, north, or south, and its normal is always up.
 * This is the preferred machine block because it simplifies everything.
 *
 * This means only the horizontal facing is stored.
 * */
abstract class UprightHorizontalDirectionCellBlock<C : Cell>(p : Properties? = null) : CellBlock<C>(p) {
    init {
        @Suppress("LeakingThis")
        registerDefaultState(getStateDefinition().any().setValue(
            HorizontalDirectionalBlock.FACING,
            Direction.NORTH
        ))
    }

    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState? {
        return super.defaultBlockState().setValue(
            HorizontalDirectionalBlock.FACING,
            pContext.horizontalDirection
        )
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(HorizontalDirectionalBlock.FACING)
    }

    override fun appendLocatorData(state: BlockState, builder: LocatorBuilder) {
        /**
         * By definition, the normal is always up.
         * */
        builder.put(Locators.SUBSTRATE_FACE, Direction.UP)
        /**
         * This is the only data that changes:
         * */
        builder.put(Locators.CONVENTIONAL_FACING, state.getValue(HorizontalDirectionalBlock.FACING).toHorizontalFacing())
    }

    /**
     * Specific scan for this block type.
     * Scans the 4 horizontal directions with a planar cell scan.
     * */
    override fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, cell: Cell) {
        Base6Direction3dMask.HORIZONTALS.directionList.forEach { searchDir ->
            planarCellScan(level, cell, searchDir) {
                check(results.add(it)) {
                    DEBUGGER_BREAK("Duplicate planar cell scan $it")
                }
            }
        }
    }
}

/**
 * A block entity that has a cell. It contains normal block entity logic, as well as connection logic.
 * It can be owned by various implementations of the [CellBlock]. The connection logic needs to take that into account ([spatialNeighborScan]).
 * The default implementation is the simple case for [UprightHorizontalDirectionCellBlock].
 * */
open class CellBlockEntity<C : Cell>(pos: BlockPos, state: BlockState, targetType: BlockEntityType<*>) : BlockEntity(targetType, pos, state), CellContainer {
    /**
     * Builds the locator, by appending known information (the [CellLayer.Block] and the [blockPos]), and then appending custom data via [CellBlock.appendLocatorData]).
     * */
    val locator = Locators.buildLocator {
        it.put(CELL_LAYER, CellLayer.Block)
        it.put(BLOCK, blockPos)

        val block = state.block as CellBlock<*>
        block.appendLocatorData(blockState, it)
    }

    private lateinit var graphManager: CellGraphManager
    private lateinit var cellProvider: CellProvider<C>
    private lateinit var savedGraphID: UUID

    private var cellField: C? = null

    val hasCell get() = cellField != null

    @ServerOnly
    val cell: C get() = cellField ?: if(level == null) {
        error(DEBUGGER_BREAK("TRIED TO ACCESS BLOCK ENTITY CELL BEFORE LEVEL WAS SET!"))
    }
    else {
        if(level!!.isClientSide) {
            error(DEBUGGER_BREAK("TRIED TO ACCESS BLOCK ENTITY CELL ON CLIENT!"))
        }
        else {
            error(DEBUGGER_BREAK("Tried to get block entity cell before it was set $this (this can happen if the world was not saved properly, e.g. force-closed)"))
        }
    }

    open fun setPlacedBy(level: Level, cellProvider: CellProvider<C>) {
        this.cellProvider = cellProvider

        if (level.isClientSide) {
            return
        }

        if(cellField != null) {
            DEBUGGER_BREAK()
        }

        // Create the cell based on the provider.

        cellField = cellProvider.create(locator, CellEnvironment.evaluate(level, locator))

        cell.container = this

        CellConnections.insertFresh(this, cell)
        setChanged()

        cell.bindGameObjects(createObjectList())

        onCellAcquired()
    }

    fun disconnect() {
        CellConnections.disconnectCell(cell, this, false)
    }

    fun reconnect() {
        CellConnections.connectCell(cell, this)
    }

    protected open fun createObjectList() = listOf(this)

    open fun setDestroyed() {
        val level = this.level
        val cell = this.cell

        if (cellField == null) {
            // This means we are on the client.
            // Otherwise, something is going on here.

            require(level?.isClientSide == false) {
                DEBUGGER_BREAK("Cell is null in setDestroyed")
            }

            return
        }

        cell.unbindGameObjects()
        CellConnections.destroy(cell, this)
    }

    override fun saveAdditional(pTag: CompoundTag) {
        if (level!!.isClientSide) {
            // No saving is done on the client
            return
        }

        if (cell.hasGraph) {
            pTag.putString("GraphID", cell.graph.id.toString())
        } else {
            LOG.error("Save additional: graph null")
        }
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        if (pTag.contains("GraphID")) {
            savedGraphID = UUID.fromString(pTag.getString("GraphID"))!!
            LOG.info("Deserialized cell entity at $blockPos")
        } else {
            LOG.warn("Cell entity at $blockPos does not have serialized data.")
        }
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()

        if (!level!!.isClientSide) {
            cell.onContainerUnloading()
            cell.container = null
            cell.onContainerUnloaded()
            cell.unbindGameObjects()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (level!!.isClientSide) {
            return
        }

        // here, we can get our manager. We have the level at this point.

        graphManager = CellGraphManager.getFor(level as ServerLevel)

        if (this::savedGraphID.isInitialized) {
            if (graphManager.contains(savedGraphID)) {
                // fetch graph with ID
                val graph = graphManager.getGraph(savedGraphID)

                // fetch cell instance
                LOG.info("Loading cell at location $blockPos")

                cellField = graph.getCellByLocator(locator) as C

                cellProvider = CellRegistry.getCellProvider(cell.id) as CellProvider<C>
                cell.container = this
                cell.onContainerLoaded()
                cell.bindGameObjects(createObjectList())

                onCellAcquired()
            } else {
                LOG.error(
                    DEBUGGER_BREAK(
                        "Cell entity at $blockPos has saved graph ID $savedGraphID, but it was not found in the graph manager. " +
                            "This usually means the world was not saved properly (e.g. the game was force-closed). " +
                            "The cell will not be loaded; the block entity may not function correctly."
                    )
                )
            }
        }
    }

    protected open fun onCellAcquired() { }

    //#endregion

    override fun getCells(): ArrayList<Cell> {
        return arrayListOf(cell)
    }

    /**
     * Finds all possible simulation neighbors, by both a spatial scan, and extra cases (e.g. grid connections).
     * */
    override fun neighborScan(actualCell: Cell): List<CellAndContainerHandle> {
        val level = this.level ?: error("Level is null in queryNeighbors")
        val results = HashSet<CellAndContainerHandle>()

        spatialNeighborScan(level, results, actualCell)
        addExtraConnections(results)

        return results.toList()
    }

    /**
     * Does a search for simulation neighbors.
     * By default, it delegates to the cell block implementation's [CellBlock.spatialNeighborScan].
     * */
    protected open fun spatialNeighborScan(level: Level, results: HashSet<CellAndContainerHandle>, actualCell: Cell) {
        // I don't remember what actualCell meant...
        check(actualCell == cellField) {
            DEBUGGER_BREAK("Actual cell is not equal to the cell owned by the cell block entity")
        }

        val block = blockState.block as CellBlock<*>

        block.spatialNeighborScan(level, results, actualCell)
    }

    /**
     * Adds special simulation neighbors (e.g. grid-connected neighbors).
     * */
    protected open fun addExtraConnections(results: MutableSet<CellAndContainerHandle>) { }

    override fun onCellConnected(actualCell: Cell, remoteCell: Cell) {
        LOG.debug("Cell Block recorded connection from {} to {}", actualCell, remoteCell)
    }

    override fun onCellDisconnected(actualCell: Cell, remoteCell: Cell) {
        LOG.debug("Cell Block recorded deleted connection from {} to {}", actualCell, remoteCell)
    }

    override fun onTopologyChanged() {
        setChanged()
    }

    override val manager: CellGraphManager
        get() = CellGraphManager.getFor(level as ServerLevel)
}

abstract class GridCellBlockEntity<C : Cell>(pos: BlockPos, state: BlockState, targetType: BlockEntityType<*>) : CellBlockEntity<C>(pos, state, targetType), GridTerminalContainer {
    var containerID: UUID = UUID.randomUUID()
        private set

    private var gridTerminalSystemField: GridTerminalSystem? = null
    // FRAK YOU!
    val gridTerminalSystem get() = checkNotNull(gridTerminalSystemField) {
        "Grid terminal system is not initialized yet!"
    }

    val positiveX get() = blockState.getValue(HorizontalDirectionalBlock.FACING).toHorizontalFacing().rotation3d * Vector3d.unitX
    val positiveZ get() = blockState.getValue(HorizontalDirectionalBlock.FACING).toHorizontalFacing().rotation3d * Vector3d.unitZ

    /**
     * Creates a bounding box in the world frame.
     * @param x Center X in the local frame.
     * @param y Center Y in the local frame.
     * @param z Center Z in the local frame.
     * @param sizeX Size along X in the local frame.
     * @param sizeY Size along Y in the local frame.
     * @param sizeZ Size along Z in the local frame.
     * @return A bounding box in the world frame.
     */
    protected fun boundingBox(
        x: Double,
        y: Double,
        z: Double,
        sizeX: Double,
        sizeY: Double,
        sizeZ: Double,
        orientation: Rotation2d = Rotation2d.identity,
    ) = SpecGeometry.boundingBox(
        blockPos.toVector3d() + Vector3d(0.5, 0.0, 0.5) +
            positiveX * x +
            Vector3d.unitY * y +
            positiveZ * z,
        orientation * blockState.getValue(HorizontalDirectionalBlock.FACING).toHorizontalFacing().rotation2d,
        Vector3d(sizeX, sizeY, sizeZ),
        blockState.getValue(HorizontalDirectionalBlock.FACING).toHorizontalFacing(),
        Direction.UP
    )

    protected fun defineCellBoxTerminal(
        box3d: OrientedBoundingBox3d, attachment: Vector3d? = null,
        highlightColor: MyColor? = MyColor(
            0.8f,
            1f,
            0.58f,
            0.44f
        ),
        categories: List<GridMaterialCategory>,
    ) = gridTerminalSystem.defineTerminal<GridTerminal>(
        TerminalFactories(
            { ci ->
                CellTerminal(ci, locator, attachment ?: box3d.center, box3d) { this.cell }.also {
                    it.categories.addAll(categories)
                }
            },
            { GridTerminalClient(it, locator, attachment ?: box3d.center, box3d, highlightColor) }
        )
    )

    protected fun defineCellBoxTerminal(
        x: Double, y: Double, z: Double,
        sizeX: Double, sizeY: Double, sizeZ: Double,
        orientation: Rotation2d = Rotation2d.identity,
        attachment: Vector3d? = null,
        highlightColor: MyColor? = MyColor(0.8f, 1f, 0.58f, 0.44f),
        categories: List<GridMaterialCategory>,
    ) = defineCellBoxTerminal(boundingBox(x, y, z, sizeX, sizeY, sizeZ, orientation), attachment, highlightColor, categories)

    fun boundingBox(box: BoundingBox3d, orientation: Rotation2d = Rotation2d.identity) : OrientedBoundingBox3d {
        val center = box.center
        val size = box.size
        return boundingBox(center.x, center.y, center.z, size.x, size.y, size.z, orientation)
    }

    // BB = BlockBench
    fun defineCellBoxTerminalBB(
        x: Double, y: Double, z: Double,
        sizeX: Double, sizeY: Double, sizeZ: Double,
        orientation: Rotation2d = Rotation2d.identity,
        attachment: Vector3d? = null,
        highlightColor: MyColor? = MyColor(0.8f, 1f, 0.58f, 0.44f),
        categories: List<GridMaterialCategory> = listOf(GridMaterialCategory.MicroGrid),
        modelScale: Double = 1.0
    ) : Supplier<GridTerminal> {
        val size = Vector3d(sizeX / 16.0, sizeY / 16.0, sizeZ / 16.0) * modelScale
        val box = BoundingBox3d.fromCenterSize(-Vector3d.unitY * size.y / 2.0 + ((Vector3d(x / 16.0, y / 16.0, z / 16.0)) - Vector3d.one * maskXY / 2.0) * modelScale + size / 2.0, size)
        return defineCellBoxTerminal(boundingBox(box, orientation), attachment, highlightColor, categories)
    }

    private var gridTerminalSystemTag: CompoundTag? = null

    override fun setPlacedBy(level: Level, cellProvider: CellProvider<C>) {
        gridTerminalSystem.initializeFresh()
        super.setPlacedBy(level, cellProvider)

        if(!level.isClientSide) {
            cell.ifNode<GridNode> {
                it.mapFromGridTerminalSystem(gridTerminalSystem)
            }
        }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        pTag.putUUID(CONTAINER_ID, containerID)
        pTag.put(GRID_TERMINAL_SYSTEM, gridTerminalSystem.save(GridTerminalSystem.SaveType.Server))
    }

    override fun getUpdateTag(): CompoundTag {
        val tag = CompoundTag()
        tag.put(GRID_TERMINAL_SYSTEM, gridTerminalSystem.save(GridTerminalSystem.SaveType.Client))
        return tag
    }

    override fun handleUpdateTag(tag: CompoundTag?) {
        if(tag == null) {
            return
        }

        if(tag.contains(GRID_TERMINAL_SYSTEM)) {
            gridTerminalSystem.initializeSaved(tag.getCompound(GRID_TERMINAL_SYSTEM))
        }
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        if(pTag.contains(CONTAINER_ID)) {
            containerID = pTag.getUUID(CONTAINER_ID)
        }

        if(pTag.contains(GRID_TERMINAL_SYSTEM)) {
            gridTerminalSystemTag = pTag.getCompound(GRID_TERMINAL_SYSTEM)
        }
    }

    override fun setLevel(pLevel: Level) {
        check(gridTerminalSystemField == null) {
            "Grid terminal system was present in setLevel"
        }

        gridTerminalSystemField = GridTerminalSystem(pLevel)
        createTerminals()

        super.setLevel(pLevel)

        if(gridTerminalSystemTag != null) {
            check(!pLevel.isClientSide)
            gridTerminalSystem.initializeSaved(gridTerminalSystemTag!!)
            gridTerminalSystemTag = null
        }
    }

    protected abstract fun createTerminals()

    override fun pickTerminal(player: LivingEntity) = gridTerminalSystem.pick(player)

    override fun getTerminalByEndpointID(endpointID: UUID) = gridTerminalSystem.getByEndpointID(endpointID)

    override fun setDestroyed() {
        super.setDestroyed()
        gridTerminalSystem.destroy()
    }

    override fun addExtraConnections(results: MutableSet<CellAndContainerHandle>) {
        gridTerminalSystem.forEachTerminalOfType<CellTerminal> {
            if(it.stagingCell != null) {
                results.add(CellAndContainerHandle.captureInScope(it.stagingCell!!))
            }
        }

        if(hasCell) {
            cell.ifNode<GridNode> { node ->
                node.forEachConnectionCell {
                    results.add(CellAndContainerHandle.captureInScope(it))
                }
            }
        }
    }

    companion object {
        private const val CONTAINER_ID = "containerID"
        private const val GRID_TERMINAL_SYSTEM = "gridTerminalSystem"
    }
}
