package org.eln2.mc.common.containers

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.cells.ResistorCell
import org.eln2.mc.common.containers.ContainerRegistry.RESISTOR_CELL_CONTAINER
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.serverToClient.SingleDoubleElementGuiOpenPacket

class ResistorCellContainer(id: Int, plyInv: Inventory, ply: Player) :
    SingleValueCellContainer<ResistorCell, Double>(id, plyInv, ply, RESISTOR_CELL_CONTAINER.get()) {
    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }

    override var value: Double = 0.0

    constructor(id: Int, plyInv: Inventory, ply: Player, te: CellBlockEntity) : this(id, plyInv, ply) {
        this.te = te

        value = (te.cell as ResistorCell).getGuiValue()
        pos = te.pos
    }

    override fun sendDataToClient(ply: ServerPlayer) {
        Networking.sendTo(SingleDoubleElementGuiOpenPacket(value, pos), ply)
    }

}
