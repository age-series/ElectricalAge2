package org.eln2.mc.common.capabilities

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.CapabilityManager
import net.minecraftforge.common.capabilities.CapabilityToken
import net.minecraftforge.common.util.INBTSerializable
import org.eln2.mc.data.LITERS
import org.eln2.mc.data.Quantity
import org.eln2.mc.data.Volume
import org.eln2.mc.getSymContainer
import org.eln2.mc.putSymContainer
import org.eln2.mc.scientific.chemistry.CompoundContainer

interface CompoundContainerCapability {
    val container: CompoundContainer
    fun canInsert(c: CompoundContainer) = true
    fun getMaxInsertVolume(c: CompoundContainer) = Quantity(1.0, LITERS)
    fun insertFrom(c: CompoundContainer, maxVolume: Quantity<Volume>): Boolean
    fun extractInto(c: CompoundContainer, maxVolume: Quantity<Volume>): Boolean
    val canInsertByPlayer: Boolean
    val canExtractByPlayer: Boolean
}

val COMPOUND_CONTAINER_CAPABILITY: Capability<CompoundContainerCapability> = CapabilityManager.get(
    object : CapabilityToken<CompoundContainerCapability>() {}
)

open class CompoundContainerHandler(var maxV: Quantity<Volume> = Quantity(Double.MAX_VALUE)): CompoundContainerCapability, INBTSerializable<CompoundTag> {
    override val container = CompoundContainer()

    override fun insertFrom(c: CompoundContainer, maxVolume: Quantity<Volume>): Boolean {
        container.transferVolumeSTPFrom(c, maxVolume)
        return true
    }

    override fun extractInto(c: CompoundContainer, maxVolume: Quantity<Volume>): Boolean {
        c.transferVolumeSTPFrom(container, maxVolume)
        return true
    }

    override fun getMaxInsertVolume(c: CompoundContainer) = maxV - container.volumeSTP

    override val canInsertByPlayer: Boolean
        get() = true

    override val canExtractByPlayer: Boolean
        get() = true

    override fun serializeNBT() = CompoundTag().also {
        it.putSymContainer(NBT_CONTAINER, container)
    }

    override fun deserializeNBT(nbt: CompoundTag?) {
        container.clear()

        if(nbt != null) {
            container += nbt.getSymContainer(NBT_CONTAINER)
        }
    }

    companion object {
        private const val NBT_CONTAINER = "container"
    }
}

val BUCKET_VOLUME = Quantity(10.0, LITERS)

fun convertMillibuckets(mb: Double) = BUCKET_VOLUME * (mb / 1000.0)
fun convertMillibuckets(mb: Int) = convertMillibuckets(mb.toDouble())
