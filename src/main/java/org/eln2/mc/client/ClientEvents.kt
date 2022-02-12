package org.eln2.mc.client

import net.minecraftforge.client.gui.OverlayRegistry
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import org.apache.logging.log4j.LogManager
import org.eln2.mc.client.input.Input
import org.eln2.mc.client.overlay.plotter.PlotterOverlay

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object ClientEvents {
    @SubscribeEvent
    fun clientSetup(event : FMLClientSetupEvent){
        OverlayRegistry.registerOverlayTop("Plotter", PlotterOverlay)
        Input.setup()
    }
}
