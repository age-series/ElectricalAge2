package org.eln2.mc.extensions

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object LevelExtensions {
    fun Level.playLocalSound(
        pos: Vec3,
        pSound: SoundEvent,
        pCategory: SoundSource,
        pVolume: Float,
        pPitch: Float,
        pDistanceDelay: Boolean
    ) {
        this.playLocalSound(pos.x, pos.y, pos.z, pSound, pCategory, pVolume, pPitch, pDistanceDelay)
    }

    fun Level.addParticle(
        pParticleData: ParticleOptions,
        pos: Vec3,
        pXSpeed: Double,
        pYSpeed: Double,
        pZSpeed: Double
    ) {
        this.addParticle(pParticleData, pos.x, pos.y, pos.z, pXSpeed, pYSpeed, pZSpeed)
    }

    fun Level.addParticle(
        pParticleData: ParticleOptions,
        pos: Vec3,
        speed: Vec3
    ) {
        this.addParticle(pParticleData, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z)
    }
}
