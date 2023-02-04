package org.eln2.mc.common.parts

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.registries.ForgeRegistryEntry

/**
 * This is a factory for parts.
 * */
abstract class PartProvider : ForgeRegistryEntry<PartProvider>(){
    val id : ResourceLocation get() = this.registryName ?: error("ID not available in PartProvider")

    /**
     * Used to create a new instance of the part. Called when the part is placed
     * or when the multipart entity is loading from disk.
     * @param context The placement context of this part.
     * @return Unique instance of the part.
     */
    abstract fun create(context: PartPlacementContext) : Part
}
