package org.eln2.mc.common.blocks.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Material
import org.eln2.mc.BiomeEnvironments
import org.eln2.mc.LOG
import org.eln2.mc.ServerOnly
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaNode
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.isHorizontal
import java.util.*

abstract class CellBlock<C : Cell>(p : Properties? = null) : HorizontalDirectionalBlock(p ?: Properties.of(Material.STONE).noOcclusion()), EntityBlock {
    init {
        @Suppress("LeakingThis")
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH))
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState? {
        return super.defaultBlockState().setValue(FACING, pContext.horizontalDirection.opposite.counterClockWise)
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(FACING)
    }

    @Suppress("UNCHECKED_CAST")
    override fun setPlacedBy(level: Level, blockPos: BlockPos, blockState: BlockState, entity: LivingEntity?, itemStack: ItemStack) {
        val cellEntity = level.getBlockEntity(blockPos)!! as CellBlockEntity<C>
        cellEntity.setPlacedBy(level, getCellProvider())
    }

    override fun onBlockExploded(blockState: BlockState?, level: Level?, blockPos: BlockPos?, explosion: Explosion?) {
        destroy(level ?: error("Level was null"), blockPos ?: error("Position was null"))
        super.onBlockExploded(blockState, level, blockPos, explosion)
    }

    override fun onDestroyedByPlayer(blockState: BlockState?, level: Level?, blockPos: BlockPos?, player: Player?, willHarvest: Boolean, fluidState: FluidState?): Boolean {
        destroy(
            level
            ?: error("Level was null"),
            blockPos ?: error("Position was null")
        )
        return super.onDestroyedByPlayer(blockState, level, blockPos, player, willHarvest, fluidState)
    }

    @Suppress("UNCHECKED_CAST")
    private fun destroy(level: Level, blockPos: BlockPos) {
        if (!level.isClientSide) {
            val cellEntity = level.getBlockEntity(blockPos)!! as CellBlockEntity<C>
            cellEntity.setDestroyed()
        }
    }

    abstract fun getCellProvider(): CellProvider<C>
}

open class CellBlockEntity<C : Cell>(pos: BlockPos, state: BlockState, targetType: BlockEntityType<*>) :
    BlockEntity(targetType, pos, state),
    CellContainer,
    WailaNode {

    open val cellFace = Direction.UP

    private lateinit var graphManager: CellGraphManager
    private lateinit var cellProvider: CellProvider<C>
    private lateinit var savedGraphID: UUID

    @ServerOnly
    var cell: C? = null
        private set

    fun setPlacedBy(level: Level, cellProvider: CellProvider<C>) {
        this.cellProvider = cellProvider

        if (level.isClientSide) {
            return
        }

        // Create the cell based on the provider.

        val cellPos = createCellLocator()

        cell = cellProvider.create(cellPos, BiomeEnvironments.getInformationForBlock(level, cellPos).fieldMap())
        cell!!.container = this

        CellConnections.insertFresh(this, cell!!)
        setChanged()

        cell!!.bindGameObjects(createObjectList())
    }

    fun disconnect() {
        CellConnections.disconnectCell(cell!!, this, false)
    }

    fun reconnect() {
        CellConnections.connectCell(cell!!, this)
    }

    protected open fun createObjectList() = listOf(this)

    open fun setDestroyed() {
        val level = this.level ?: error("Level is null in setDestroyed")
        val cell = this.cell

        if (cell == null) {
            // This means we are on the client.
            // Otherwise, something is going on here.

            require(level.isClientSide) { "Cell is null in setDestroyed" }
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

        if (cell!!.hasGraph) {
            pTag.putString("GraphID", cell!!.graph.id.toString())
        } else {
            LOG.info("Save additional: graph null")
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
            cell!!.onContainerUnloading()
            cell!!.container = null
            cell!!.onContainerUnloaded()
            cell!!.unbindGameObjects()
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

        if (this::savedGraphID.isInitialized && graphManager.contains(savedGraphID)) {
            // fetch graph with ID
            val graph = graphManager.getGraph(savedGraphID)

            // fetch cell instance
            println("Loading cell at location $blockPos")

            cell = graph.getCellByLocator(createCellLocator()) as C

            cellProvider = CellRegistry.getCellProvider(cell!!.id) as CellProvider<C>
            cell!!.container = this
            cell!!.onContainerLoaded()
            cell!!.bindGameObjects(createObjectList())
        }
    }

    //#endregion

    private fun createCellLocator() = LocatorSetBuilder().apply {
        withLocator(blockPos)
        withLocator(cellFace)
        withLocator(FacingLocator(blockState.getValue(HorizontalDirectionalBlock.FACING)))
    }.build()

    override fun getCells(): ArrayList<Cell> {
        return arrayListOf(cell ?: error("Cell is null in getCells"))
    }

    override fun neighborScan(actualCell: Cell): List<CellNeighborInfo> {
        val cell = this.cell ?: error("Cell is null in queryNeighbors")
        val level = this.level ?: error("Level is null in queryNeighbors")

        val results = ArrayList<CellNeighborInfo>()

        Direction.values()
            .filter { it.isHorizontal() }
            .forEach { searchDir ->
                planarCellScan(level, cell, searchDir, results::add)
            }

        return results
    }

    override fun onCellConnected(actualCell: Cell, remoteCell: Cell) {
        LOG.info("Cell Block recorded connection from $actualCell to $remoteCell")
    }

    override fun onCellDisconnected(actualCell: Cell, remoteCell: Cell) {
        LOG.info("Cell Block recorded deleted connection from $actualCell to $remoteCell")
    }

    override fun onTopologyChanged() {
        setChanged()
    }

    override val manager: CellGraphManager
        get() = CellGraphManager.getFor(level as ServerLevel)

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        val cell = this.cell

        if (cell is WailaNode) {
            cell.appendWaila(builder, config)
        }
    }
}
