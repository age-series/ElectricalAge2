package org.eln2.mc.common.containers

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.Eln2

object ContainerRegistry {
    private val setups = ArrayList<Runnable>()

    @JvmStatic
    val CONTAINER_REGISTRY: DeferredRegister<MenuType<*>> = DeferredRegister.create(
        ForgeRegistries.CONTAINERS,
        Eln2.MODID
    )

    fun setup(bus: IEventBus) = CONTAINER_REGISTRY.register(bus)

    /**
     * This really only exists so that I don't have to specify the type on every GUI to
     * avoid unchecked nullability issues
     */
    fun <T : AbstractContainerMenu> registerMenuType(
        name: String,
        supplier: () -> MenuType<T>
    ): RegistryObject<MenuType<T>> = CONTAINER_REGISTRY.register(name) { supplier() }

    fun <T : AbstractContainerMenu> registerMenu(
        name: String,
        supplier: (Int, Inventory) -> T
    ): RegistryObject<MenuType<T>> = registerMenuType(name) {  MenuType(supplier) }
}
