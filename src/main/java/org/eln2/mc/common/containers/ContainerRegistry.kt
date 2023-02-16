package org.eln2.mc.common.containers

import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.BlockRegistry

object ContainerRegistry {
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
    private fun <T : AbstractContainerMenu> registerGuiContainer(
        name: String,
        supplier: () -> MenuType<T>
    ): RegistryObject<MenuType<T>> = CONTAINER_REGISTRY.register(name) { supplier() }

    val VOLTAGE_SOURCE_CELL_CONTAINER = registerGuiContainer(BlockRegistry.VOLTAGE_SOURCE_CELL.name) {
        MenuType { id, inv ->
            VoltageSourceCellContainer(id, inv, inv.player)
        }
    }

    val RESISTOR_CELL_CONTAINER = registerGuiContainer(BlockRegistry.RESISTOR_CELL.name) {
        MenuType { id, inv ->
            ResistorCellContainer(id, inv, inv.player)
        }
    }

    val CAPACITOR_CELL_CONTAINER = registerGuiContainer(BlockRegistry.CAPACITOR_CELL.name) {
        MenuType { id, inv ->
            CapacitorCellContainer(id, inv, inv.player)
        }
    }

    val INDUCTOR_CELL_CONTAINER = registerGuiContainer(BlockRegistry.INDUCTOR_CELL.name) {
        MenuType { id, inv ->
            InductorCellContainer(id, inv, inv.player)
        }
    }
}
