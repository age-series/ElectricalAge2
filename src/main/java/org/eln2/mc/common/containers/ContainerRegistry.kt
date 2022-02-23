package org.eln2.mc.common.containers

import net.minecraft.client.gui.screens.MenuScreens
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

    fun setup(bus: IEventBus) = CONTAINER_REGISTRY.register(bus)


    @JvmStatic
    val VOLTAGE_SOURCE_CELL_CONTAINER: RegistryObject<MenuType<VoltageSourceCellContainer>> =
        CONTAINER_REGISTRY.register(BlockRegistry.VOLTAGE_SOURCE_CELL.name) {
            MenuType { id, inv ->
                VoltageSourceCellContainer(id, inv, inv.player)
            }
        }

    @JvmStatic
    val RESISTOR_CELL_CONTAINER: RegistryObject<MenuType<ResistorCellContainer>> =
        CONTAINER_REGISTRY.register(BlockRegistry.RESISTOR_CELL.name) {
            MenuType { id, inv ->
                ResistorCellContainer(id, inv, inv.player)
            }
        }

    @JvmStatic
    val CAPACITOR_CELL_CONTAINER: RegistryObject<MenuType<CapacitorCellContainer>> =
        CONTAINER_REGISTRY.register(BlockRegistry.CAPACITOR_CELL.name) {
            MenuType { id, inv ->
                CapacitorCellContainer(id, inv, inv.player)
            }
        }

    @JvmStatic
    val INDUCTOR_CELL_CONTAINER: RegistryObject<MenuType<InductorCellContainer>> =
        CONTAINER_REGISTRY.register(BlockRegistry.INDUCTOR_CELL.name) {
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
            MenuScreens.register(VOLTAGE_SOURCE_CELL_CONTAINER.get(), ::VoltageSourceCellScreen)
            MenuScreens.register(RESISTOR_CELL_CONTAINER.get(), ::ResistorCellScreen)
            MenuScreens.register(CAPACITOR_CELL_CONTAINER.get(), ::CapacitorCellScreen)
            MenuScreens.register(INDUCTOR_CELL_CONTAINER.get(), ::InductorCellScreen)
        }
    }
}
