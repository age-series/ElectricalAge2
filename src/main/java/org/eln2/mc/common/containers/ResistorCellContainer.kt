package org.eln2.mc.common.containers

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerListener
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.types.ResistorCell
import org.eln2.mc.common.containers.ContainerRegistry.RESISTOR_CELL_CONTAINER
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.serverToClient.ResistorOpenPacket

class ResistorCellContainer(id: Int, plyInv: Inventory, private val ply: Player) :
    AbstractContainerMenu(RESISTOR_CELL_CONTAINER.get(), id) {
    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }

    var resistance = 0.0
    var pos: BlockPos = BlockPos.ZERO
    var te: CellTileEntity? = null

    constructor(id: Int, plyInv: Inventory, ply: Player, te: CellTileEntity) : this(id, plyInv, ply) {
        this.te = te

        if (te.cell is ResistorCell) {
            resistance = (te.cell as ResistorCell).resistor.resistance
        }
        pos = te.pos
    }

    fun receiveChanges(resistance: Double, pos: BlockPos) {
        this.resistance = resistance
        this.pos = pos
    }

    private fun sendChangesToAllListeners() {
        containerListeners.forEach { listener ->
            if (listener is ServerPlayer) {
                sendChangesToPlayer(listener)
            }
        }
    }

    private fun sendChangesToPlayer(ply: ServerPlayer) {
        Networking.sendTo(ResistorOpenPacket(resistance, pos), ply)
    }

    override fun broadcastChanges() {
        super.broadcastChanges()
        if (resistance != (te?.cell as? ResistorCell)?.resistor?.resistance || pos != te?.pos) {
            sendChangesToAllListeners()
        }
    }

    override fun addSlotListener(pListener: ContainerListener) {
        super.addSlotListener(pListener)

        sendChangesToAllListeners()
        if (containerListeners.none { it as? ServerPlayer == ply }) {
            sendChangesToPlayer(ply as ServerPlayer)
        }
    }
}
