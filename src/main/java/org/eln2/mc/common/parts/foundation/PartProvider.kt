package org.eln2.mc.common.parts.foundation

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraftforge.registries.ForgeRegistryEntry

/**
 * This is a factory for parts. It also has the size used to validate placement (part-part collisions).
 * */
abstract class PartProvider : ForgeRegistryEntry<PartProvider>() {
    val id: ResourceLocation get() = this.registryName ?: error("ID not available in PartProvider")

    /**
     * Used to create a new instance of the part. Called when the part is placed
     * or when the multipart entity is loading from disk.
     * @param context The placement context of this part.
     * @return Unique instance of the part.
     */
    abstract fun create(context: PartPlacementContext): Part

    /**
     * This is the size used to validate placement. This is different from baseSize, because
     * you can implement a visual placement margin here.
     * */
    abstract val placementCollisionSize: Vec3
}
