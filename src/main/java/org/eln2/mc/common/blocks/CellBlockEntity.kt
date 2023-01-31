package org.eln2.mc.common.blocks

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.eln2.mc.Eln2
import org.eln2.mc.common.DirectionMask
import org.eln2.mc.common.PlacementRotation
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.*
import org.eln2.mc.common.cell.container.CellSpaceLocation
import org.eln2.mc.common.cell.container.CellSpaceQuery
import org.eln2.mc.common.cell.container.ICellContainer
import org.eln2.mc.extensions.BlockEntityExtensions.getNeighborEntity
import org.eln2.mc.extensions.BlockPosExtensions.plus
import org.eln2.mc.extensions.DirectionExtensions.isVertical
import java.util.*
import kotlin.collections.ArrayList

class CellBlockEntity(var pos : BlockPos, var state: BlockState)
    : BlockEntity(BlockRegistry.CELL_BLOCK_ENTITY.get(), pos, state),
    ICellContainer {
    // Initialized when placed or loading

    open val cellFace = Direction.UP

    lateinit var graphManager : CellGraphManager
    lateinit var cellProvider : CellProvider

    // Used for loading
    private lateinit var savedGraphID : UUID

    // Cell is not available on the client.
    var cell : CellBase? = null

    private val serverLevel get() = level as ServerLevel

    private fun getPlacementRotation() : PlacementRotation{
        return PlacementRotation (state.getValue(HorizontalDirectionalBlock.FACING))
    }

    private fun getLocalDirection(globalDirection : Direction) : RelativeRotationDirection{
        val placementRotation = getPlacementRotation()

        return placementRotation.getRelativeFromAbsolute(globalDirection)
    }

    @Suppress("UNUSED_PARAMETER") // Will very likely be needed later and helps to know the name of the args.
    fun setPlacedBy(
        level : Level,
        position : BlockPos,
        blockState : BlockState,
        entity : LivingEntity?,
        itemStack : ItemStack,
        cellProvider: CellProvider
    ) {
        this.cellProvider = cellProvider

        if(level.isClientSide){
            return
        }

        // Create the cell based on the provider.

        cell = cellProvider.create(getCellPos())

        cell!!.entity = this
        cell!!.id = cellProvider.registryName!!

        CellConnectionManager.connect(this, getCellSpace())

        setChanged()
    }

    fun setDestroyed() {
        if(cell == null){
            // This means we are on the client.
            // Otherwise, something is going on here.

            assert(level!!.isClientSide)
            return
        }

        CellConnectionManager.destroy(getCellSpace(), this)
    }

    private fun canConnectFrom(dir : Direction) : Boolean {
        val localDirection = getLocalDirection(dir)

        return cellProvider.canConnectFrom(localDirection)
    }

    //#region Saving and Loading

    override fun saveAdditional(pTag: CompoundTag) {
        if(level!!.isClientSide){
            // No saving is done on the client

            return
        }

        if (cell!!.hasGraph()) {
            pTag.putString("GraphID", cell!!.graph.id.toString())
        } else {
            Eln2.LOGGER.info("Save additional: graph null")
        }
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        if (pTag.contains("GraphID")) {
            savedGraphID = UUID.fromString(pTag.getString("GraphID"))!!
            Eln2.LOGGER.info("Deserialized cell entity at $pos")
        }
        else{
            Eln2.LOGGER.warn("Cell entity at $pos does not have serialized data.")
        }
    }

    override fun onChunkUnloaded() {
        super.onChunkUnloaded()

        if (!level!!.isClientSide) {
            cell!!.onEntityUnloaded()

            // GC reference tracking
            cell!!.entity = null
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
            val graph = graphManager.getGraphWithId(savedGraphID)

            // fetch cell instance
            println("Loading cell at location $pos")

            cell = graph.getCell(getCellPos())

            cellProvider = CellRegistry.getProvider(cell!!.id)

            cell!!.entity = this
            cell!!.onEntityLoaded()
        }
    }

    //#endregion

    fun getHudMap(): Map<String, String> {
        return if (cell == null) {
            Eln2.LOGGER.warn("You're trying to reference cell in getHudMap from the client side, where cell is always null!")
            mapOf()
        } else {
            // fixme: debug data

            val result = cell!!.getHudMap().toMutableMap()
            result["Graph"] = cell?.graph?.id?.toString() ?: "GRAPH NULL"

            result
          }
    }

    private fun getCellSpace() : CellSpaceLocation{
        return CellSpaceLocation(cell!!, cellFace)
    }

    private fun getCellPos() : CellPos{
        return CellPos(pos, cellFace)
    }

    override fun getCells(): ArrayList<CellSpaceLocation> {
        return arrayListOf(getCellSpace())
    }

    override fun query(query: CellSpaceQuery): CellSpaceLocation? {
        return if(canConnectFrom(query.connectionFace)){
            getCellSpace()
        } else{
            null
        }
    }

    override fun queryNeighbors(location: CellSpaceLocation): ArrayList<CellBase> {
        val results = ArrayList<CellBase>()

        Direction.values()
            .filter { !it.isVertical() }
            .forEach { direction ->
                val local = getLocalDirection(direction)

                if(cellProvider.canConnectFrom(local)){
                    val remoteContainer = level!!
                        .getBlockEntity(pos + direction)
                        as? ICellContainer
                        ?: return@forEach

                    val queryResult = remoteContainer.query(CellSpaceQuery(direction.opposite, Direction.UP))

                    if(queryResult != null){
                        results.add(queryResult.cell)
                    }
                }
            }

        return results
    }

    override fun canConnectFrom(location: CellSpaceLocation, direction: Direction): Boolean {
        assert(location.cell == cell!!)

        val local = getLocalDirection(direction)

        return cellProvider.canConnectFrom(local)
    }


    override val manager: CellGraphManager
        get() = CellGraphManager.getFor(serverLevel)
}
