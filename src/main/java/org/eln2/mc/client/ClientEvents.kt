package org.eln2.mc.client

import net.minecraft.client.gui.screens.MenuScreens
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.eln2.mc.client.render.FlywheelRegistry
import org.eln2.mc.client.render.RenderTypes
import org.eln2.mc.common.containers.ContainerRegistry
import org.eln2.mc.common.content.Content

@EventBusSubscriber
object ClientEvents {
    @SubscribeEvent
    fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            FlywheelRegistry.initialize()
            RenderTypes.initialize()
        }
    }
}
