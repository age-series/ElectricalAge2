package org.eln2.mc.common.parts

import net.minecraft.core.BlockPos
import net.minecraftforge.registries.ForgeRegistryEntry

abstract class PartProvider : ForgeRegistryEntry<PartProvider>(){
    /**
     * Used to create a new instance of the part. Called when the part is placed
     * or when the multipart entity is loading from disk.
     * @return Unique instance of the part.
     */
    abstract fun create(pos : BlockPos) : Part
}
