package org.eln2.mc.common.cells.foundation

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.registries.ForgeRegistryEntry
import org.eln2.mc.common.space.RelativeRotationDirection

/**
 * The Cell Provider is a factory of cells, and also has connection rules for cells.
 * */
abstract class CellProvider : ForgeRegistryEntry<CellProvider>() {
    val id: ResourceLocation
        get() = this.registryName ?: error("ID not available in CellProvider")

    protected abstract fun createInstance(pos: CellPos, id: ResourceLocation): CellBase

    /**
     * Creates a new Cell, at the specified position.
     * */
    fun create(pos: CellPos): CellBase {
        return createInstance(pos, id)
    }

    /**
     * Applies a connection predicate, to determine if the specified direction is valid for connections.
     * @return True, if this cell may connect from that direction. Otherwise, false.
     * */
    open fun canConnectFrom(direction: RelativeRotationDirection): Boolean {
        return true
    }
}
