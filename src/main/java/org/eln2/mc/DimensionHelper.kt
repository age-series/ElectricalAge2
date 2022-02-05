package org.eln2.mc

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraftforge.server.ServerLifecycleHooks

object DimensionHelper {
    fun mapIdToDimension(id : String) : Level? {
        val resourceLocation = ResourceLocation.tryParse(id)
            ?: throw Exception("Invalid ID! It should be a Resource Location.")

        val key = ResourceKey.create(Registry.DIMENSION_REGISTRY, resourceLocation) ?: return null

        return ServerLifecycleHooks.getCurrentServer().getLevel(key)
    }

    fun mapDimensionToId(level : Level) : String {
        return level.dimension().location().toString()
    }
}
