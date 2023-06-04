package org.eln2.mc.common.entities

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.Eln2
import org.stringtemplate.v4.misc.Misc

object EntityRegistry {
    val ENTITY_REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITIES, Eln2.MODID)!!

    fun setup(bus: IEventBus) {
        ENTITY_REGISTRY.register(bus)
    }

    inline fun <reified T : Entity> entity(
        id: String,
        category: MobCategory,
        crossinline supplier: (b: EntityType<T>, l: Level) -> T,
        crossinline configure: (EntityType.Builder<T>) -> Unit
    ): RegistryObject<EntityType<T>> = ENTITY_REGISTRY.register("grid_connection_entity") {
        EntityType.Builder.of<T>(
            { t, l -> supplier(t, l) }, category
        ).also(configure).build(id)
    }

    inline fun <reified T : Entity> entity(
        id: String,
        category: MobCategory,
        crossinline supplier: (b: EntityType<T>, l: Level) -> T) = entity(id, category, supplier) { }
}
