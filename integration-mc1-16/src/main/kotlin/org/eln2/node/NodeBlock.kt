package org.eln2.node

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.material.Material
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompoundNBT
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Explosion
import net.minecraft.world.World
import org.eln2.utils.Coordinate

class NodeBlock(): Block(Properties.create(Material.ROCK)), INodeNBT {

    var coordinate: Coordinate? = null

    override fun onBlockPlacedBy(
        world: World,
        pos: BlockPos,
        state: BlockState,
        entity: LivingEntity?,
        stack: ItemStack
    ) {
        super.onBlockPlacedBy(world, pos, state, entity, stack)
        coordinate = Coordinate(world, pos)
        NodeManager.addNode(coordinate!!, this)
    }

    override fun onBlockHarvested(
        world: World,
        pos: BlockPos,
        state: BlockState,
        player: PlayerEntity
    ) {
        super.onBlockHarvested(world, pos, state, player)
        NodeManager.removeNode(coordinate!!)
    }

    override fun onBlockExploded(state: BlockState?, world: World?, pos: BlockPos?, explosion: Explosion?) {
        super.onBlockExploded(state, world, pos, explosion)
        if (world == null || pos == null) {
            throw Error("I have no idea what just happened, but NodeManager can't handle world or position being null.")
        }
        NodeManager.removeNode(coordinate!!)
    }

    override fun read(p0: CompoundNBT) {
        coordinate?.read(p0)
    }

    override fun write(p0: CompoundNBT): CompoundNBT {
        return p0
    }
}
