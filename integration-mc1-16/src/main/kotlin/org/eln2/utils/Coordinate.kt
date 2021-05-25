package org.eln2.utils

import net.minecraft.nbt.CompoundNBT
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Dimension
import net.minecraft.world.World
import net.minecraftforge.fml.LogicalSide
import net.minecraftforge.fml.LogicalSidedProvider
import org.eln2.node.INodeNBT

class Coordinate(var world: World, var pos: BlockPos): INodeNBT {

    override fun toString(): String {
        return "${world.dimensionKey.location}_${pos.x}_${pos.y}_${pos.z}"
    }

    override fun read(p0: CompoundNBT) {
        val server = LogicalSidedProvider.INSTANCE.get<MinecraftServer>(LogicalSide.SERVER)
        //Minecraft.getInstance().world.server.worlds

        println("Coordinate read function looking at server: ${server.worlds.map { it.dimensionKey.location.toString() }}")

        server.worlds.filter {it.dimensionKey.location.toString() == p0.getString("dim")}
    }

    override fun write(p0: CompoundNBT): CompoundNBT {
        return p0
    }
}
