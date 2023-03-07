package org.eln2.mc.extensions

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.annotations.ServerOnly
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.foundation.Part

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

    @ServerOnly
    fun ServerLevel.destroyPart(part: Part) {
        val pos = part.placementContext.pos

        val multipart = this.getBlockEntity(pos)
            as? MultipartBlockEntity

        if (multipart == null) {
            Eln2.LOGGER.error("Multipart null at $pos")

            return
        }

        val item = PartRegistry.getPartItem(part.id)

        multipart.breakPart(part)

        val itemEntity = ItemEntity(
            this,
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            ItemStack(item)
        )

        this.addFreshEntity(itemEntity)

        if (multipart.isEmpty) {
            this.destroyBlock(pos, false)
        }
    }
}
