package org.eln2.mc.client.events

import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.core.Direction
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.eln2.mc.Eln2
import org.eln2.mc.client.flywheel.FlywheelRegistry
import org.eln2.mc.client.screens.CapacitorCellScreen
import org.eln2.mc.client.screens.InductorCellScreen
import org.eln2.mc.client.screens.ResistorCellScreen
import org.eln2.mc.client.screens.VoltageSourceCellScreen
import org.eln2.mc.common.RelativeRotationDirection
import org.eln2.mc.common.containers.ContainerRegistry.CAPACITOR_CELL_CONTAINER
import org.eln2.mc.common.containers.ContainerRegistry.CONTAINER_REGISTRY
import org.eln2.mc.common.containers.ContainerRegistry.INDUCTOR_CELL_CONTAINER
import org.eln2.mc.common.containers.ContainerRegistry.RESISTOR_CELL_CONTAINER
import org.eln2.mc.common.containers.ContainerRegistry.VOLTAGE_SOURCE_CELL_CONTAINER
import org.eln2.mc.extensions.DirectionExtensions.isVertical

@EventBusSubscriber
object ClientEvents {

    /**
     * Register the client-side equivalents of the GUI containers.
     * This must be done on the client during FMLClientSetupEvent
     * Note: This is done in a different registry than [CONTAINER_REGISTRY]
     */
    @SubscribeEvent
    fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            Direction.values().filter { !it.isVertical() }.forEach { facing ->
                Direction.values().filter { !it.isVertical() }.forEach { face ->
                    Direction.values().forEach { dir ->
                        val relative = RelativeRotationDirection.fromForwardUp(facing, face, dir)

                        Eln2.LOGGER.info("Fwd: $facing, face: $face, dir: $dir -> $relative")
                    }
                }
            }


            MenuScreens.register(VOLTAGE_SOURCE_CELL_CONTAINER.get(), ::VoltageSourceCellScreen)
            MenuScreens.register(RESISTOR_CELL_CONTAINER.get(), ::ResistorCellScreen)
            MenuScreens.register(CAPACITOR_CELL_CONTAINER.get(), ::CapacitorCellScreen)
            MenuScreens.register(INDUCTOR_CELL_CONTAINER.get(), ::InductorCellScreen)

            FlywheelRegistry.initialize()
        }
    }

}
