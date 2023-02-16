package org.eln2.mc.common.cells.foundation

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.registries.ForgeRegistryEntry
import org.eln2.mc.common.cells.foundation.objects.ISimulationObject
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectMask
import org.eln2.mc.common.space.RelativeRotationDirection

abstract class CellProvider : ForgeRegistryEntry<CellProvider>() {
    val id: ResourceLocation get() = this.registryName ?: error("ID not available in CellProvider")

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

    open fun canConnectFrom(direction: RelativeRotationDirection): Boolean {
        return true
    }
}
