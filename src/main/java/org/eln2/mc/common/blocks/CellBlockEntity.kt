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
import org.eln2.mc.common.PlacementRotation
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.cell.*
import org.eln2.mc.extensions.BlockEntityExtensions.getNeighborEntity
import java.util.*
import kotlin.collections.ArrayList

class CellBlockEntity(var pos : BlockPos, var state: BlockState)
    : BlockEntity(BlockRegistry.CELL_BLOCK_ENTITY.get(), pos, state),
    ICellContainer{
    // Initialized when placed or loading

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

        cell = cellProvider.create(pos)

        cell!!.entity = this
        cell!!.id = cellProvider.registryName!!

        CellConnectionManager.connect(this)

        setChanged()
    }

    fun setDestroyed() {
        if(cell == null){
            // This means we are on the client.
            // Otherwise, something is going on here.

            assert(level!!.isClientSide)
            return
        }

        CellConnectionManager.destroy(this)
    }

    private fun canConnectFrom(dir : Direction) : Boolean {
        val localDirection = getLocalDirection(dir)

        return cellProvider.canConnectFrom(localDirection)
    }

    private fun getNeighborCells() : ArrayList<CellBase>{
        val results = ArrayList<CellBase>()

        Direction.values().forEach { direction ->
            val container = this.getNeighborEntity<ICellContainer>(direction)

            if(container != null){
                val cell = container.getCell(direction.opposite)

                if(cell != null){
                    results.add(cell)
                }
            }
        }

        return results
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

            cell = graph.getCell(pos)

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

    override fun getCells(): ArrayList<CellBase> {
        return arrayListOf(cell!!)
    }

    override fun getCell(direction: Direction): CellBase? {
        assert(cell != null)

        return if(canConnectFrom(direction)){
            cell!!
        } else{
            null
        }
    }

    override fun getNeighbors(cell: CellBase): ArrayList<CellBase> {
        assert(cell == this.cell)

        return getNeighborCells()
    }

    override val manager: CellGraphManager
        get() = CellGraphManager.getFor(serverLevel)
}
