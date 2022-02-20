package org.eln2.mc.common.cell

import net.minecraft.core.BlockPos
import net.minecraftforge.registries.ForgeRegistryEntry
import org.eln2.mc.common.RelativeRotationDirection

abstract class CellProvider : ForgeRegistryEntry<CellProvider>() {
    val connectableDirections = HashSet<RelativeRotationDirection>()

    /**
     * Used to create a new instance of the cell. Called when the cell block is placed
     * or when the cell manager is loading cells from the disk.
     * @return Unique instance of the cell. If the cell is being created by the block, the setPlaced method will be called.
     * @see CellBase.setPlaced
    */
    abstract fun create(pos : BlockPos) : CellBase

    /**
     * Used to check if a cell is valid for connection.
     * @return True if the connection is accepted. Otherwise, false.
    */
    abstract fun connectionPredicate(dir : RelativeRotationDirection) : Boolean
}
