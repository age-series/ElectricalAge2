package org.eln2.mc.common.capabilities

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER

@Mod.EventBusSubscriber
object CapabilityRegistry {
    val RADIATION_LOCATION = Eln2.resource("radiation")

    fun setup(bus: IEventBus) {
        bus.addListener(this::registerCaps)

        LOGGER.info("Prepared capability registry.")
    }

    private fun registerCaps(event: RegisterCapabilitiesEvent) {
        event.register(RadiationCapability::class.java)

        LOGGER.info("Registered capabilities")
    }

    @SubscribeEvent
    fun attachCapability(event: AttachCapabilitiesEvent<Entity>) {
        if (event.getObject() !is Player) {
            return
        }

        event.addCapability(RADIATION_LOCATION, RadiationCapabilityProvider())
    }
}
