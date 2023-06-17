package org.eln2.mc.common.capabilities

import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.CapabilityManager
import net.minecraftforge.common.capabilities.CapabilityToken
import net.minecraftforge.common.capabilities.ICapabilitySerializable
import net.minecraftforge.common.util.LazyOptional
import org.eln2.mc.data.*
import org.eln2.mc.getQuantity
import org.eln2.mc.putQuantity
import org.eln2.mc.utility.nu

class RadiationCapability {
    var absorbedDose: Quantity<RadiationAbsorbedDose> = Quantity(0.0)
    var equivalentDose: Quantity<RadiationDoseEquivalent> = Quantity(0.0)
    var doseRate: Quantity<Radioactivity> = Quantity(0.0)
}

class RadiationCapabilityProvider : ICapabilitySerializable<CompoundTag> {
    private val lazy = LazyOptional.of(::RadiationCapability)

    init {
        println("created cap")
    }

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        return if(cap == CAPABILITY) lazy.cast()
        else LazyOptional.empty()
    }

    override fun serializeNBT() = CompoundTag().apply {
        lazy.ifPresent {
            putQuantity(NBT_ABSORBED, it.absorbedDose)
            putQuantity(NBT_EQUIVALENT, it.equivalentDose)
        }
    }

    override fun deserializeNBT(nbt: CompoundTag?) {
        nbt?.also { tag ->
           lazy.ifPresent {
               it.absorbedDose = tag.getQuantity(NBT_ABSORBED)
               it.equivalentDose = tag.getQuantity(NBT_EQUIVALENT)
           }
        }
    }

    companion object {
        private const val NBT_ABSORBED = "absorbed"
        private const val NBT_EQUIVALENT = "equivalent"

        // Interesting way to get around the type system:
        val CAPABILITY: Capability<RadiationCapability> = CapabilityManager.get(
            object: CapabilityToken<RadiationCapability>(){}
        )
    }
}
