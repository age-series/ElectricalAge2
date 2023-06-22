package org.eln2.mc.common.blocks.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
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
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.BiomeEnvironments
import org.eln2.mc.ServerOnly
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.isHorizontal
import java.util.*

abstract class CellBlock(props: Properties? = null) :
    HorizontalDirectionalBlock(props ?: Properties.of(Material.STONE).noOcclusion()), EntityBlock {
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

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        blockState: BlockState,
        entity: LivingEntity?,
        itemStack: ItemStack,
    ) {
        val cellEntity = level.getBlockEntity(pos)!! as CellBlockEntity
        cellEntity.setPlacedBy(
            level,
            pos,
            blockState,
            entity,
            itemStack,
            CellRegistry.getProvider(getCellProvider())
        )
    }


    override fun onBlockExploded(blState: BlockState?, lvl: Level?, pos: BlockPos?, exp: Explosion?) {
        destroy(lvl ?: error("Level was null"), pos ?: error("Position was null"))
        super.onBlockExploded(blState, lvl, pos, exp)
    }

    override fun onDestroyedByPlayer(
        blState: BlockState?,
        lvl: Level?,
        pos: BlockPos?,
        pl: Player?,
        wh: Boolean,
        flState: FluidState?,
    ): Boolean {
        destroy(lvl ?: error("Level was null"), pos ?: error("Position was null"))
        return super.onDestroyedByPlayer(blState, lvl, pos, pl, wh, flState)
    }

    private fun destroy(level: Level, pos: BlockPos) {
        if (!level.isClientSide) {
            val cellEntity = level.getBlockEntity(pos)!! as CellBlockEntity
            cellEntity.setDestroyed()
        }
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity? {
        return CellBlockEntity(pPos, pState)
    }

    // override this:
    abstract fun getCellProvider(): ResourceLocation
}

class BasicCellBlock(val supplier: () -> ResourceLocation) : CellBlock() {
    constructor(p: RegistryObject<CellProvider>) : this({ p.id!! })

    override fun getCellProvider(): ResourceLocation = supplier()
}

open class CellBlockEntity(pos: BlockPos, state: BlockState, targetType: BlockEntityType<*>) :
    BlockEntity(targetType, pos, state),
    CellContainer,
    WailaEntity,
    DataEntity {
    constructor(pos: BlockPos, state: BlockState) : this(pos, state, BlockRegistry.CELL_BLOCK_ENTITY.get())

    open val cellFace = Direction.UP

    private lateinit var graphManager: CellGraphManager
    private lateinit var cellProvider: CellProvider

    // Used for loading
    private lateinit var savedGraphID: UUID

    @ServerOnly
    var cell: Cell? = null
        private set(value) {
            fun removeOld() {
                if (field != null) {
                    dataNode.children.removeIf { it == field!!.dataNode }
                }
            }

            if (value == null) {
                removeOld()
            } else {
                removeOld()

                if (!dataNode.children.any { it == value.dataNode }) {
                    dataNode.withChild(value.dataNode)
                }
            }

            field = value
        }

    @ServerOnly
    private val serverLevel get() = level as ServerLevel

    @Suppress("UNUSED_PARAMETER") // Will very likely be needed later and helps to know the name of the args.
    fun setPlacedBy(
        level: Level,
        position: BlockPos,
        blockState: BlockState,
        entity: LivingEntity?,
        itemStack: ItemStack,
        cellProvider: CellProvider,
    ) {
        this.cellProvider = cellProvider

        if (level.isClientSide) {
            return
        }

        // Create the cell based on the provider.

        val p = getCellPos()
        cell = cellProvider.create(p, BiomeEnvironments.cellEnv(level, p).fieldMap())

        cell!!.container = this

        CellConnections.insertFresh(this, cell!!)

        setChanged()

        cell!!.bindGameObjects(
            listOf(
                this
            )
        )
    }

    fun disconnect() {
        CellConnections.disconnectCell(cell!!, this, false)
    }

    fun reconnect() {
        CellConnections.connectCell(cell!!, this)
    }

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

    //#region Saving and Loading

    override fun saveAdditional(pTag: CompoundTag) {
        if (level!!.isClientSide) {
            // No saving is done on the client

            return
        }

        if (cell!!.hasGraph) {
            pTag.putString("GraphID", cell!!.graph.id.toString())
        } else {
            org.eln2.mc.LOG.info("Save additional: graph null")
        }
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        if (pTag.contains("GraphID")) {
            savedGraphID = UUID.fromString(pTag.getString("GraphID"))!!
            org.eln2.mc.LOG.info("Deserialized cell entity at $blockPos")
        } else {
            org.eln2.mc.LOG.warn("Cell entity at $blockPos does not have serialized data.")
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
            cell!!.bindGameObjects(
                listOf(
                    this
                )
            )
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
        org.eln2.mc.LOG.info("Cell Block recorded connection from $actualCell to $remoteCell")
    }

    override fun onCellDisconnected(actualCell: Cell, remoteCell: Cell) {
        org.eln2.mc.LOG.info("Cell Block recorded deleted connection from $actualCell to $remoteCell")
    }

    override fun onTopologyChanged() {
        setChanged()
    }

    override val manager: CellGraphManager
        get() = CellGraphManager.getFor(serverLevel)

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        val cell = this.cell

        if (cell is WailaEntity) {
            cell.appendBody(builder, config)
        }
    }

    override val dataNode: DataNode = DataNode()
}
