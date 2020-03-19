package org.eln2.mc

import net.minecraftforge.fml.common.FMLLog
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.*
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

@Mod(modid = "electrical-age")
class ElnMain {
    @SubscribeEvent
    fun onServerPreInit(event: FMLPreInitializationEvent) {
        FMLLog.log.info("Hello, world!")
    }

    @SubscribeEvent
    fun onServerInit(event: FMLInitializationEvent) {

    }

    @SubscribeEvent
    fun onServerPostInit(event: FMLPostInitializationEvent) {

    }

    @SubscribeEvent
    fun onServerStarting(event: FMLServerStartingEvent) {

    }

    @SubscribeEvent
    fun onServerStopping(event: FMLServerStoppingEvent) {

    }
}