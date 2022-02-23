package org.eln2.mc.common.containers

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.types.VoltageSourceCell
import org.eln2.mc.common.containers.ContainerRegistry.VOLTAGE_SOURCE_CELL_CONTAINER
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.serverToClient.SingleDoubleElementGuiOpenPacket

@Suppress("UNCHECKED_CAST")
class VoltageSourceCellContainer(id: Int, plyInv: Inventory, ply: Player) :
    SingleValueCellContainer<VoltageSourceCell, Double>(id, plyInv, ply, VOLTAGE_SOURCE_CELL_CONTAINER.get()) {
    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }

    override var value: Double = 0.0

    constructor(id: Int, plyInv: Inventory, ply: Player, te: CellTileEntity) : this(id, plyInv, ply) {
        this.te = te

        value = (te.cell as VoltageSourceCell).source.potential
        pos = te.pos
    }

    override fun sendDataToClient(ply: ServerPlayer) {
        Networking.sendTo(SingleDoubleElementGuiOpenPacket(value, pos), ply)
    }

}
