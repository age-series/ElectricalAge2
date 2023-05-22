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
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.*
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.space.*
import org.eln2.mc.data.DataAccessNode
import org.eln2.mc.data.IDataEntity
import org.eln2.mc.extensions.isHorizontal
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import java.util.*

open class CellBlockEntity(pos: BlockPos, state: BlockState, targetType: BlockEntityType<*>) :
    BlockEntity(targetType, pos, state),
    ICellContainer,
    IWailaProvider,
    IDataEntity {
    // Initialized when placed or loading

    constructor(pos: BlockPos, state: BlockState): this(pos, state, BlockRegistry.CELL_BLOCK_ENTITY.get())

    open val cellFace = Direction.UP

    lateinit var graphManager: CellGraphManager
    private lateinit var cellProvider: CellProvider

    // Used for loading
    private lateinit var savedGraphID: UUID

    // Cell is not available on the client.

    var cell: CellBase? = null
        private set(value) {
            fun removeOld() {
                if(field != null) {
                    dataAccessNode.children.removeIf { it == field!!.dataAccessNode }
                }
            }

            if(value == null) {
                removeOld()
            }
            else {
                removeOld()

                if(!dataAccessNode.children.any { it == value.dataAccessNode }) {
                    dataAccessNode.withChild(value.dataAccessNode)
                }
            }

            field = value
        }

    private val serverLevel get() = level as ServerLevel

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

        CellConnectionManager.connect(this, cell ?: error("Unexpected"))

        setChanged()
    }

    fun setDestroyed() {
        val level = this.level ?: error("Level is null in setDestroyed")
        val cell = this.cell

        if (cell == null) {
            // This means we are on the client.
            // Otherwise, something is going on here.

            require(level.isClientSide) { "Cell is null in setDestroyed" }
            return
        }

        CellConnectionManager.destroy(cell, this)
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
            LOGGER.info("Save additional: graph null")
        }
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        if (pTag.contains("GraphID")) {
            savedGraphID = UUID.fromString(pTag.getString("GraphID"))!!
            LOGGER.info("Deserialized cell entity at $blockPos")
        } else {
            LOGGER.warn("Cell entity at $blockPos does not have serialized data.")
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

    private fun getCellPos(): CellPos {
        return CellPos(
            LocationDescriptor()
                .withLocator(BlockPosLocator(blockPos))
                .withLocator(BlockFaceLocator(cellFace))
                .withLocator(IdentityDirectionLocator(blockState.getValue(HorizontalDirectionalBlock.FACING)))
        )
    }

    override fun getCells(): ArrayList<CellBase> {
        return arrayListOf(cell ?: error("Cell is null in getCells"))
    }

    override fun queryNeighbors(actualCell: CellBase): ArrayList<CellNeighborInfo> {
        val cell = this.cell ?: error("Cell is null in queryNeighbors")
        val level = this.level ?: error("Level is null in queryNeighbors")

        val results = ArrayList<CellNeighborInfo>()

        Direction.values()
            .filter { it.isHorizontal() }
            .forEach { searchDir ->
                planarScan(level, cell, searchDir, results::add)
            }

        return results
    }

    override fun recordConnection(actualCell: CellBase, remoteCell: CellBase) {
        LOGGER.info("Cell Block recorded connection from $actualCell to $remoteCell")
    }

    override fun recordDeletedConnection(actualCell: CellBase, remoteCell: CellBase) {
        LOGGER.info("Cell Block recorded deleted connection from $actualCell to $remoteCell")
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

    override val dataAccessNode: DataAccessNode = DataAccessNode()
}
