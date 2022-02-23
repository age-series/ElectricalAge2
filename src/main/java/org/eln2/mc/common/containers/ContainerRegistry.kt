package org.eln2.mc.common.containers

import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.Eln2
import org.eln2.mc.client.screens.CapacitorCellScreen
import org.eln2.mc.client.screens.InductorCellScreen
import org.eln2.mc.client.screens.ResistorCellScreen
import org.eln2.mc.client.screens.VoltageSourceCellScreen
import org.eln2.mc.common.blocks.BlockRegistry

object ContainerRegistry {
    @JvmStatic
    val CONTAINER_REGISTRY: DeferredRegister<MenuType<*>> = DeferredRegister.create(
        ForgeRegistries.CONTAINERS,
        Eln2.MODID
    )
    private val CLIENTSIDE_GUI_QUEUE = mutableListOf<ContainerRegistryItem<*>>()

    fun setup(bus: IEventBus) = CONTAINER_REGISTRY.register(bus)

    data class ContainerRegistryItem<T: AbstractContainerMenu>(
        val name: String,
        val screenConstructor: (T, Inventory, Component) -> AbstractContainerScreen<T>,
        val container: RegistryObject<MenuType<T>>
    )

    /**
     * registerGuiContainer will handle both the server-side and client-side registration of our GUIs
     * It places a container in the container registry and registers a MenuScreen during ClientSetup
     */
    private fun <T : AbstractContainerMenu> registerGuiContainer(name: String, screenConstructor: (T, Inventory, Component) -> AbstractContainerScreen<T>, supplier: () -> MenuType<T>): ContainerRegistryItem<T> {
        val container = CONTAINER_REGISTRY.register(name) { supplier() }
        val registryItem = ContainerRegistryItem(name, screenConstructor, container)
        CLIENTSIDE_GUI_QUEUE.add(registryItem)
        return registryItem
    }

    val VOLTAGE_SOURCE_CELL_CONTAINER = registerGuiContainer(BlockRegistry.VOLTAGE_SOURCE_CELL.name, ::VoltageSourceCellScreen) {
        MenuType {id, inv ->
            VoltageSourceCellContainer(id, inv, inv.player)
        }
    }

    val RESISTOR_CELL_CONTAINER = registerGuiContainer(BlockRegistry.RESISTOR_CELL.name, ::ResistorCellScreen) {
        MenuType { id, inv ->
            ResistorCellContainer(id, inv, inv.player)
        }
    }

    val CAPACITOR_CELL_CONTAINER = registerGuiContainer(BlockRegistry.CAPACITOR_CELL.name, ::CapacitorCellScreen) {
        MenuType { id, inv ->
            CapacitorCellContainer(id, inv, inv.player)
        }
    }

    val INDUCTOR_CELL_CONTAINER = registerGuiContainer(BlockRegistry.INDUCTOR_CELL.name, ::InductorCellScreen) {
        MenuType { id, inv ->
            InductorCellContainer(id, inv, inv.player)
        }
    }

    /**
     * Register the client-side equivalents of the GUI containers.
     * This must be done on the client during FMLClientSetupEvent
     * Note: This is done in a different registry than [CONTAINER_REGISTRY]
     */
    @SubscribeEvent
    fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            CLIENTSIDE_GUI_QUEUE.forEach { gui ->
                @Suppress("UNCHECKED_CAST")
                MenuScreens.register(gui.container.get(), (gui as ContainerRegistryItem<AbstractContainerMenu>).screenConstructor)
            }
        }
    }
}
