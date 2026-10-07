package org.eln2.mc.common.sounds.foundation

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.entity.BlockEntity
import org.ageseries.libage.data.Event
import org.ageseries.libage.data.EventBus
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.requireIsOnRenderThread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

data class SoundInfo(val pitch: Double, val volume: Double) {
    companion object {
        val QUIET = SoundInfo(1.0, 0.0)

        fun standardWithProcessingSpeed(speed: Double) : SoundInfo {
            if(speed < 0.01) {
                return QUIET
            }

            return SoundInfo(
                pitch = 0.6 + speed * 0.4,
                volume = speed.pow(3) + 0.5
            )
        }

        fun standardWithKineticScraping(omega: Double, refOmega: Double) : SoundInfo {
            val speed = abs(omega) / abs(refOmega)

            if(speed < 1e-8) {
                return QUIET
            }

            val base = 0.1

            return SoundInfo(
                pitch = (speed.pow(2) + 0.6).coerceIn(0.0, 2.0),
                volume = base * speed * (1.0 + sin(speed.coerceIn(0.0, 1.0) * (PI / 2.0))).coerceIn(0.0, 3.0)
            )
        }

        fun electromagnetic(
            power: Double,
            nominalPower: Double,
            basePitch: Double = 0.9,
            pitchRange: Double = 1.6,
            powExponent: Double = 0.6
        ): SoundInfo {
            if (power <= 1e-9 || nominalPower <= 0.0) {
                return QUIET
            }

            val p = abs(power / nominalPower)

            val saturation = p / (1.0 + p)
            val pitch = (basePitch + pitchRange * saturation.pow(powExponent)).coerceIn(0.4, 4.0)

            val volume = p.pow(0.7).coerceIn(0.0, 3.0)

            return SoundInfo(
                pitch = pitch,
                volume = volume
            )
        }

        fun steamFlow(flow: Double, maxFlow: Double): SoundInfo {
            if (flow <= 1e-9 || maxFlow <= 0.0) {
                return QUIET
            }

            val ratio = (flow / maxFlow).coerceIn(0.0, 1.0)

            if (ratio < 0.01) {
                return QUIET
            }

            return SoundInfo(
                pitch = (0.7 + ratio * 0.5).coerceIn(0.5, 2.0),
                volume = (ratio * 0.8 + 0.2).coerceIn(0.0, 2.5)
            )
        }

        fun turbineFriction(omega: Double, refOmega: Double): SoundInfo {
            val speed = abs(omega) / abs(refOmega)

            if (speed < 0.01) {
                return QUIET
            }

            val clamped = speed.coerceIn(0.0, 2.0)

            return SoundInfo(
                pitch = (0.5 + clamped * 0.8).coerceIn(0.0, 2.5),
                volume = (0.3 * clamped.coerceIn(0.0, 1.0) + 0.1 * clamped).coerceIn(0.0, 2.0)
            )
        }

        fun wind(angularVelocity: Double, refAngularVelocity: Double): SoundInfo {
            val speed = abs(angularVelocity) / abs(refAngularVelocity)

            if (speed < 0.01) {
                return QUIET
            }

            val clamped = speed.coerceIn(0.0, 1.5)

            return SoundInfo(
                pitch = (0.6 + clamped * 0.6).coerceIn(0.4, 2.0),
                volume = ((clamped.pow(1.5) * 0.5 + 0.1)).coerceIn(0.0, 1.0)
            )
        }

        fun burning(intensity: Double): SoundInfo {
            if (intensity <= 1e-6) {
                return QUIET
            }

            val clamped = intensity.coerceIn(0.0, 1.0)

            return SoundInfo(
                pitch = (0.8 + clamped * 0.4).coerceIn(0.6, 1.5),
                volume = (clamped * 1.2 + 0.15).coerceIn(0.0, 2.0)
            )
        }

        fun draft(draftStrength: Double, maxDraft: Double): SoundInfo {
            if (draftStrength <= 1e-9 || maxDraft <= 0.0) {
                return QUIET
            }

            val ratio = (draftStrength / maxDraft).coerceIn(0.0, 1.0)

            if (ratio < 0.01) {
                return QUIET
            }

            return SoundInfo(
                pitch = 1.0,
                volume = (ratio * 0.15 + 0.05).coerceIn(0.0, 1.5)
            )
        }

        fun distillation(activity: Double): SoundInfo {
            if (activity <= 0.01) {
                return QUIET
            }

            val clamped = activity.coerceIn(0.0, 1.0)

            return SoundInfo(
                pitch = (0.7 + clamped * 0.3).coerceIn(0.5, 1.5),
                volume = (clamped * 0.6).coerceIn(0.0, 1.0)
            )
        }
    }
}

/**
 * Sent when the sound instance is about to tick.
 * */
object SoundInstanceTickEvent : Event

abstract class SimpleLoopingMachineSoundInstance<T>(soundEvent: SoundEvent) :
    AbstractTickableSoundInstance(
        soundEvent,
        SoundSource.BLOCKS,
        SoundInstance.createUnseededRandom()
) {
    init {
        super.looping = true
        super.delay = 0
        super.volume = 0.0f
    }

    // We set volume to 0 initially:
    override fun canStartSilent(): Boolean {
        return true
    }

    var initialized = false
        private set

    fun registerOnAudioManager() {
        requireIsOnRenderThread {
            DEBUGGER_BREAK("SimpleLoopingMachineSoundInstance#register")
        }

        Minecraft.getInstance().soundManager.queueTickingSound(this)
    }

    var soundInfo = SoundInfo(1.0, 0.0)
    val events = EventBus(setOf(SoundInstanceTickEvent::class))

    /**
     * Called in the first tick to set the position of the sound.
     * */
    abstract fun getPosition() : Vector3d

    /**
     * Called per-tick to see if the sound instance should be removed.
     * */
    open fun shouldRemove() : Boolean {
        return false
    }

    override fun tick() {
        if(!initialized) {
            val position = getPosition()
            super.x = position.x
            super.y = position.y
            super.z = position.z
            initialized = true
        }

        events.send(SoundInstanceTickEvent)

        if(shouldRemove()) {
            this.stop()
            return
        }

        val options = soundInfo
        pitch = options.pitch.toFloat()
        volume = options.volume.toFloat()
    }

    fun remove() {
        this.stop()
    }
}

class SimpleLoopingBlockEntitySoundInstance<T : BlockEntity>(val machine: T, soundEvent: SoundEvent) : SimpleLoopingMachineSoundInstance<T>(soundEvent) {
    override fun getPosition(): Vector3d {
        val pos = machine.blockPos ?: error(DEBUGGER_BREAK("BlockEntity#blockPos null for SimpleLoopingBlockEntitySoundInstance"))
        return Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
    }

    override fun shouldRemove(): Boolean {
        return machine.isRemoved
    }
}

class SimpleLoopingPartSoundInstance<T : Part>(val machine: T, soundEvent: SoundEvent) : SimpleLoopingMachineSoundInstance<T>(soundEvent) {
    override fun getPosition(): Vector3d {
        return machine.placement.mountingPointWorld
    }

    override fun shouldRemove(): Boolean {
        return machine.isRemoved
    }
}
