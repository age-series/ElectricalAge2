package org.eln2.mc.common.containers

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.types.ResistorCell
import org.eln2.mc.common.containers.ContainerRegistry.RESISTOR_CELL_CONTAINER
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.serverToClient.SingleDoubleElementGuiOpenPacket

@Suppress("UNCHECKED_CAST")
class ResistorCellContainer(id: Int, plyInv: Inventory, ply: Player) :
    SingleValueCellContainer<ResistorCell, Double>(id, plyInv, ply, RESISTOR_CELL_CONTAINER.get()) {
    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }

    override var value: Double = 0.0
    override var pos: BlockPos = BlockPos.ZERO
    override var te: CellTileEntity? = null

    constructor(id: Int, plyInv: Inventory, ply: Player, te: CellTileEntity) : this(id, plyInv, ply) {
        this.te = te

        value = (te.cell as ResistorCell).resistor.potential
        pos = te.pos
    }

    override fun sendDataToClient(ply: ServerPlayer) {
        Networking.sendTo(SingleDoubleElementGuiOpenPacket(value, pos), ply)
    }

    override fun getSyncedValue(): Double {
        return value
    }

    override fun setSyncedValue(value: Double, pos: BlockPos): Boolean {
        this.value = value
        this.pos = pos

        return true
    }


}
