package org.eln2.mc.common.capabilities

import net.minecraftforge.common.capabilities.CapabilityManager
import net.minecraftforge.common.capabilities.CapabilityToken
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent
import net.minecraftforge.fml.common.Mod

class CellConnectableToken : CapabilityToken<CellConnectable>()

@Mod.EventBusSubscriber
object CapabilityRegistry {
    val CELL_CONNECTABLE_CAPABILITY = CapabilityManager.get(CellConnectableToken())

    fun registerCapabilities(event : RegisterCapabilitiesEvent){
        event.register(CellConnectable :: class.java)
    }
}
