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
import org.eln2.sim.IElectricalSimulator
import org.eln2.sim.IProcess
import org.eln2.sim.ISimulator
import org.eln2.sim.IThermalSimulator
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.thermal.ThermalEdge
import org.eln2.sim.thermal.ThermalNode
import org.eln2.utils.Coordinate

class NodeBlock(): Block(Properties.create(Material.ROCK)), INodeNBT, ISimulator, IElectricalSimulator, IThermalSimulator {

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

    /*
     TODO: Only include this in nodes that need these simulators to reduce the need to add these lists to the Sim.
    Perhaps we should use reflection for this, or allow the user to inherit it from the top side instead of here.
    Trying to avoid boilerplate higher up.
     */

    // Simulator Stuff
    override var slowPreProcess = mutableListOf<IProcess>()
    override var slowProcess = mutableListOf<IProcess>()
    override var slowPostProcess = mutableListOf<IProcess>()

    // Electrical Simulator
    override var electricalConnections = mutableListOf<Pair<Component, Component>>()
    override var electricalProcess = mutableListOf<IProcess>()
    override var electricalRootProcess = mutableListOf<IProcess>()

    // Thermal Simulator
    override var thermalConnections = mutableListOf<Pair<ThermalNode, ThermalNode>>()
    override var thermalNodeList = mutableListOf<ThermalNode>()
    override var thermalEdgeList = mutableListOf<ThermalEdge>()
    override var thermalSlowProcess = mutableListOf<IProcess>()
    override var thermalFastProcess = mutableListOf<IProcess>()
}
