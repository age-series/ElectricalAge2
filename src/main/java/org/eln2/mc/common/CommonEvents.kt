package org.eln2.mc.common

import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber
object CommonEvents {
    @SubscribeEvent
    fun onServerStarted(event : ServerStartedEvent) {
        // we can now start the electrical simulator
        event.server.allLevels.forEach{
            /*val nodeManager = it.getNodeManager()
            nodeManager.prepare()*/
        }
    }
}
