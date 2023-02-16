package org.eln2.mc.common.cells.foundation

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.registries.ForgeRegistryEntry
import org.eln2.mc.common.space.RelativeRotationDirection

abstract class CellProvider : ForgeRegistryEntry<CellProvider>() {
    val id: ResourceLocation get() = this.registryName ?: error("ID not available in CellProvider")

    val connectableDirections = HashSet<RelativeRotationDirection>()

    /**
     * Used to create a new instance of the cell. Called when the cell block is placed
     * or when the cell manager is loading cells from the disk.
     * @return Unique instance of the cell. If the cell is being created by the block, the setPlaced method will be called.
     * @see CellBase.onPlaced
     */
    protected abstract fun createInstance(pos: CellPos): CellBase

    fun create(pos: CellPos): CellBase {
        val instance = createInstance(pos)

        instance.id = id

        return instance
    }

    /**
     * Used to check if a cell is valid for connection.
     * @return True if the connection is accepted. Otherwise, false.
     */
    abstract fun connectionPredicate(dir: RelativeRotationDirection): Boolean

    fun canConnectFrom(direction: RelativeRotationDirection): Boolean {
        if (!connectableDirections.contains(direction)) {
            return false
        }

        return connectionPredicate(direction)
    }
}
