package org.eln2.mc.extensions

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.network.NetworkHooks
import org.eln2.mc.Eln2
import org.eln2.mc.ServerOnly
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.data.DataNode
import org.eln2.mc.data.DataEntity

fun interface IContainerFactory<T: BlockEntity> {
    fun create(id: Int, inventory: Inventory, player: Player, entity: T): AbstractContainerMenu
}

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
    val pos = part.placement.pos

    val multipart = this.getBlockEntity(pos)
        as? MultipartBlockEntity

    if (multipart == null) {
        Eln2.LOGGER.error("Multipart null at $pos")

        return
    }

    val saveTag = CompoundTag()

    multipart.breakPart(part, saveTag)

    val itemEntity = ItemEntity(
        this,
        pos.x.toDouble(),
        pos.y.toDouble(),
        pos.z.toDouble(),
        Part.createPartDropStack(part.id, saveTag)
    )

    this.addFreshEntity(itemEntity)

    if (multipart.isEmpty) {
        this.destroyBlock(pos, false)
    }
}

inline fun<reified TEntity: BlockEntity> Level.constructMenu(
    pos: BlockPos,
    player: Player,
    crossinline title: (() -> Component),
    factory: IContainerFactory<TEntity>
): InteractionResult {

    if(!this.isClientSide) {
        val entity = this.getBlockEntity(pos) as? TEntity
            ?: return InteractionResult.FAIL

        val containerProvider = object : MenuProvider {
            override fun getDisplayName(): Component {
                return title()
            }

            override fun createMenu(
                pContainerId: Int,
                pInventory: Inventory,
                pPlayer: Player
            ): AbstractContainerMenu {
                return factory.create(
                    pContainerId,
                    pInventory,
                    pPlayer,
                    entity
                )
            }
        }

        NetworkHooks.openGui(player as ServerPlayer, containerProvider, entity.blockPos)
        return InteractionResult.SUCCESS
    }

    return InteractionResult.SUCCESS
}

inline fun<reified TEntity: BlockEntity> Level.constructMenu(
    pos: BlockPos,
    player: Player,
    crossinline title: (() -> Component),
    crossinline factory: ((Int, Inventory, ItemStackHandler) -> AbstractContainerMenu),
    crossinline accessor: ((TEntity) -> ItemStackHandler)): InteractionResult {

    return this.constructMenu<TEntity>(pos, player, title) {
            id, inventory, _, entity -> factory(id, inventory, accessor(entity))
    }
}

fun Level.getDataAccess(pos: BlockPos): DataNode? {
    return ((this.getBlockEntity(pos) ?: return null)
            as? DataEntity ?: return null)
        .dataNode
}
