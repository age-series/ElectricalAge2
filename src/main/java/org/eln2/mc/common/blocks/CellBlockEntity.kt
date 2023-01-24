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
import kotlin.system.measureNanoTime

class CellBlockEntity(var pos : BlockPos, var state: BlockState): BlockEntity(BlockRegistry.CELL_BLOCK_ENTITY.get(), pos, state) {
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

        cell = CellEntityNetworkManager.place(this)

        setChanged()
    }

    fun setDestroyed() {
        if(cell == null){
            // This means we are on the client.
            // Otherwise, something is going on here.

            assert(level!!.isClientSide)
            return
        }

        CellEntityNetworkManager.destroy(this)
    }

    private fun canConnectFrom(dir : Direction) : Boolean {
        val localDirection = getLocalDirection(dir)

        return cellProvider.canConnectFrom(localDirection)
    }

    private fun getCandidateNeighborEntities() : ArrayList<CellBlockEntity>{
        val results = ArrayList<CellBlockEntity>()

        Direction.values().forEach { direction ->
            val entity = this.getNeighborEntity<CellBlockEntity>(direction)

            if(entity != null && entity.canConnectFrom(direction.opposite)){
                results.add(entity)
            }
        }

        return results
    }

    fun getNeighborCells() : ArrayList<CellBase>{
        val results = ArrayList<CellBase>();

        getCandidateNeighborEntities().forEach { entity ->
            if(entity.cell == null){
                // The method may be called when the entity is being placed, and the circuits are being built.
                // The cell will be null. We ignore it here.

                return@forEach
            }

            results.add(entity.cell!!)
        }

        return results;
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
            cell!!.tileUnloaded()

            // GC reference tracking
            cell!!.tile = null
        }
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (level!!.isClientSide) {
            return
        }

        // here, we can get our manager. We have the level at this point.

        graphManager = CellGraphManager.getFor(serverLevel)

        if (this::savedGraphID.isInitialized && graphManager.containsGraphWithId(savedGraphID)) {
            // fetch graph with ID
            val graph = graphManager.getGraphWithId(savedGraphID)

            // fetch cell instance
            println("Loading cell at location $pos")

            cell = graph.getCellAt(pos)

            cellProvider = CellRegistry.getProvider(cell!!.id)

            cell!!.tile = this
            cell!!.tileLoaded()
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
}
