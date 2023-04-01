package org.eln2.mc.common.blocks.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.*
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.ConnectionMode
import org.eln2.mc.common.space.PlacementRotation
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.BlockPosExtensions.plus
import org.eln2.mc.extensions.DirectionExtensions.isVertical
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import java.util.*

open class CellBlockEntity(pos: BlockPos, state: BlockState, targetType: BlockEntityType<*>) :
    BlockEntity(targetType, pos, state),
    ICellContainer,
    IWailaProvider {
    // Initialized when placed or loading

    constructor(pos: BlockPos, state: BlockState): this(pos, state, BlockRegistry.CELL_BLOCK_ENTITY.get())

    open val cellFace = Direction.UP

    lateinit var graphManager: CellGraphManager
    private lateinit var cellProvider: CellProvider

    // Used for loading
    private lateinit var savedGraphID: UUID

    // Cell is not available on the client.
    var cell: CellBase? = null
        private set

    private val serverLevel get() = level as ServerLevel

    private fun getPlacementRotation(): PlacementRotation {
        return PlacementRotation(blockState.getValue(HorizontalDirectionalBlock.FACING))
    }

    private fun getLocalDirection(globalDirection: Direction): RelativeRotationDirection {
        return RelativeRotationDirection.fromForwardUp(blockState.getValue(HorizontalDirectionalBlock.FACING), cellFace, globalDirection)
    }

    @Suppress("UNUSED_PARAMETER") // Will very likely be needed later and helps to know the name of the args.
    fun setPlacedBy(
        level: Level,
        position: BlockPos,
        blockState: BlockState,
        entity: LivingEntity?,
        itemStack: ItemStack,
        cellProvider: CellProvider
    ) {
        this.cellProvider = cellProvider

        if (level.isClientSide) {
            return
        }

        // Create the cell based on the provider.

        cell = cellProvider.create(getCellPos())

        cell!!.container = this

        CellConnectionManager.connect(this, getCellSpace())

        setChanged()
    }

    fun setDestroyed() {
        if (cell == null) {
            // This means we are on the client.
            // Otherwise, something is going on here.

            assert(level!!.isClientSide)
            return
        }

        CellConnectionManager.destroy(getCellSpace(), this)
    }

    private fun canConnectFrom(dir: Direction): Boolean {
        val localDirection = getLocalDirection(dir)

        return cellProvider.canConnectFrom(localDirection)
    }

    //#region Saving and Loading

    override fun saveAdditional(pTag: CompoundTag) {
        if (level!!.isClientSide) {
            // No saving is done on the client

            return
        }

        if (cell!!.hasGraph) {
            pTag.putString("GraphID", cell!!.graph.id.toString())
        } else {
            Eln2.LOGGER.info("Save additional: graph null")
        }
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        if (pTag.contains("GraphID")) {
            savedGraphID = UUID.fromString(pTag.getString("GraphID"))!!
            Eln2.LOGGER.info("Deserialized cell entity at $blockPos")
        } else {
            Eln2.LOGGER.warn("Cell entity at $blockPos does not have serialized data.")
        }
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()

        if (!level!!.isClientSide) {
            cell!!.onContainerUnloaded()

            // GC reference tracking
            cell!!.container = null
        }
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (level!!.isClientSide) {
            return
        }

        // here, we can get our manager. We have the level at this point.

        graphManager = CellGraphManager.getFor(serverLevel)

        if (this::savedGraphID.isInitialized && graphManager.contains(savedGraphID)) {
            // fetch graph with ID
            val graph = graphManager.getGraph(savedGraphID)

            // fetch cell instance
            println("Loading cell at location $blockPos")

            cell = graph.getCell(getCellPos())

            cellProvider = CellRegistry.getProvider(cell!!.id)

            cell!!.container = this
            cell!!.onContainerLoaded()
        }
    }

    //#endregion

    private fun getCellSpace(): CellInfo {
        return CellInfo(cell!!, cellFace)
    }

    private fun getCellPos(): CellPos {
        return CellPos(blockPos, cellFace)
    }

    override fun getCells(): ArrayList<CellInfo> {
        return arrayListOf(getCellSpace())
    }

    override fun query(query: CellQuery): CellInfo? {
        return if (canConnectFrom(query.connectionFace)) {
            getCellSpace()
        } else {
            null
        }
    }

    override fun queryNeighbors(location: CellInfo): ArrayList<CellNeighborInfo> {
        val results = ArrayList<CellNeighborInfo>()

        Direction.values()
            .filter { !it.isVertical() }
            .forEach { direction ->
                val local = getLocalDirection(direction)

                if (cellProvider.canConnectFrom(local)) {
                    CellScanner.planarScan(level!!, blockPos, direction, cellFace){ remoteInfo, remoteContainer, remoteRelative ->
                        results.add(CellNeighborInfo(remoteInfo, remoteContainer, local, remoteRelative))
                    }
                }
            }

        return results
    }

    override fun probeConnectionCandidate(location: CellInfo, direction: Direction, mode: ConnectionMode): RelativeRotationDirection? {
        assert(location.cell == cell!!)

        Eln2.LOGGER.info("CELL PROBE $location $direction $mode")

        if(mode != ConnectionMode.Planar){
            return null
        }

        val local = getLocalDirection(direction)

        return if (cellProvider.canConnectFrom(local)) {
            local
        } else {
            null
        }
    }

    override fun recordConnection(location: CellInfo, direction: RelativeRotationDirection, neighborSpace: CellInfo) {
        Eln2.LOGGER.info("Cell Block recorded connection to the $direction")
    }

    override fun recordDeletedConnection(location: CellInfo, direction: RelativeRotationDirection) {
        Eln2.LOGGER.info("Cell Block recorded deleted to the $direction")
    }

    override fun topologyChanged() {
        setChanged()
    }

    override val manager: CellGraphManager
        get() = CellGraphManager.getFor(serverLevel)

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        val cell = this.cell

        if (cell is IWailaProvider) {
            cell.appendBody(builder, config)
        }
    }
}
