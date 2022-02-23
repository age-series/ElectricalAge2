package org.eln2.mc.common.containers

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.types.CapacitorCell
import org.eln2.mc.common.containers.ContainerRegistry.CAPACITOR_CELL_CONTAINER
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.serverToClient.SingleDoubleElementGuiOpenPacket

class CapacitorCellContainer(id: Int, plyInv: Inventory, ply: Player) :
    SingleValueCellContainer<CapacitorCell, Double>(id, plyInv, ply, CAPACITOR_CELL_CONTAINER.get()) {

    override var value: Double = 0.0

    constructor(id: Int, plyInv: Inventory, ply: Player, te: CellTileEntity) : this(id, plyInv, ply) {
        this.te = te

        this.value = (te.cell as CapacitorCell).capacitor.capacitance
        this.pos = te.pos
    }

    override fun getSyncedValue(): Double {
        return this.value
    }

    override fun setSyncedValue(value: Double, pos: BlockPos): Boolean {
        this.pos = pos
        this.value = value

        return true
    }

    override fun sendDataToClient(ply: ServerPlayer) {
        Networking.sendTo(SingleDoubleElementGuiOpenPacket(value, pos), ply)
    }

    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }
}
