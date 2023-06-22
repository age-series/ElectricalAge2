package org.eln2.mc.common.capabilities

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.mc.LOG
import org.eln2.mc.resource

@Mod.EventBusSubscriber
object CapabilityRegistry {
    val RADIATION_LOCATION = resource("radiation")

    fun setup(bus: IEventBus) {
        bus.addListener(this::registerCaps)

        LOG.info("Prepared capability registry.")
    }

    private fun registerCaps(event: RegisterCapabilitiesEvent) {
        event.register(RadiationCapability::class.java)

        LOG.info("Registered capabilities")
    }

    @SubscribeEvent
    @JvmStatic
    fun attachCapability(event: AttachCapabilitiesEvent<Entity>) {
        if (event.getObject() !is Player) {
            return
        }

        event.addCapability(RADIATION_LOCATION, RadiationCapabilityProvider())
    }
}
