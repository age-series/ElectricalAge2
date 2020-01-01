package org.eln2.mc

import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.*
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.logging.log4j.LogManager


@Mod(modid = "electrical-age")
class ElnMain {
    @SubscribeEvent
    fun onServerPreInit(event: FMLPreInitializationEvent) {
        LOGGER.info("Hello, world!")
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

    companion object {
        val LOGGER = LogManager.getLogger()
    }
}