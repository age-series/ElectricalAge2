package org.eln2.mc.common.entities

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.Level
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.MODID

object EntityRegistry {
    val ENTITY_REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID)!!

    fun setup(bus: IEventBus) {
        ENTITY_REGISTRY.register(bus)
    }

    inline fun <reified T : Entity> entity(
        id: String,
        category: MobCategory,
        crossinline supplier: (b: EntityType<T>, l: Level) -> T,
        crossinline configure: (EntityType.Builder<T>) -> Unit,
    ): RegistryObject<EntityType<T>> = ENTITY_REGISTRY.register(id) {
        EntityType.Builder.of({ t, l -> supplier(t, l) }, category).also(configure).build(id)
    }

    inline fun <reified T : Entity> entity(
        id: String,
        category: MobCategory,
        crossinline supplier: (b: EntityType<T>, l: Level) -> T,
    ) = entity(id, category, supplier) { }
}
