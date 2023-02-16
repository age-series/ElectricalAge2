package org.eln2.mc.client

import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.eln2.mc.client.render.FlywheelRegistry

@EventBusSubscriber
object ClientEvents {
    @SubscribeEvent
    fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            FlywheelRegistry.initialize()
        }
    }
}
