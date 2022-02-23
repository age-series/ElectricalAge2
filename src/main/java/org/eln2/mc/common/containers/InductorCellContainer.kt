package org.eln2.mc.common.containers

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.types.InductorCell
import org.eln2.mc.common.containers.ContainerRegistry.RESISTOR_CELL_CONTAINER
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.serverToClient.SingleDoubleElementGuiOpenPacket

class InductorCellContainer(id: Int, plyInv: Inventory, ply: Player) :
    SingleValueCellContainer<InductorCell, Double>(id, plyInv, ply, RESISTOR_CELL_CONTAINER.get()) {

    override var value: Double = 0.0

    constructor(id: Int, plyInv: Inventory, ply: Player, te: CellTileEntity) : this(id, plyInv, ply) {
        this.te = te

        this.value = (te.cell as InductorCell).inductor.inductance
        this.pos = te.pos
    }

    override fun getSyncedValue(): Double {
        return value
    }

    override fun sendDataToClient(ply: ServerPlayer) {
        Networking.sendTo(SingleDoubleElementGuiOpenPacket(value, pos), ply)
    }

    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }

}
