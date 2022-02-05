package org.eln2.mc.common.blocks

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.mc.Eln2
import thedarkcolour.kotlinforforge.forge.registerObject

object ItemRegistry {
    private val LOGGER : Logger = LogManager.getLogger()

    private val REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Eln2.MODID)

    fun setup(bus : IEventBus){
        REGISTRY.register(bus)
        LOGGER.info("Prepared item registry.")
    }
}
