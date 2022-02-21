package org.eln2.mc.common.containers

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerListener
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.types.VoltageSourceCell
import org.eln2.mc.common.containers.ContainerRegistry.VOLTAGE_SOURCE_CELL_CONTAINER
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.serverToClient.VoltageSourceOpenPacket

@Suppress("UNCHECKED_CAST")
class VoltageSourceCellContainer(id: Int, plyInv: Inventory, val ply: Player) :
    AbstractContainerMenu(VOLTAGE_SOURCE_CELL_CONTAINER.get(), id) {
    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }

    var voltage = 0.0
    var pos = BlockPos.ZERO

    var te: CellTileEntity? = null

    constructor(id: Int, plyInv: Inventory, ply: Player, te: CellTileEntity) : this(id, plyInv, ply) {
        this.te = te

        voltage = (te.cell as VoltageSourceCell).source.potential
        pos = te.pos
    }

    override fun broadcastChanges() {
        if (voltage != (te?.cell as? VoltageSourceCell)?.source?.potential || pos != te?.pos) {
            voltage = (te?.cell as VoltageSourceCell).source.potential
            pos = te?.pos ?: BlockPos.ZERO
            this.sendDataToAllListeners()
        }
    }

    override fun broadcastFullState() {
        this.sendDataToAllListeners()
    }

    private fun sendDataToAllListeners() {
        containerListeners.forEach { listener ->
            if (listener is ServerPlayer) {
                sendDataToClient(listener)
            }
        }
    }

    private fun sendDataToClient(ply: ServerPlayer) {
        Networking.sendTo(VoltageSourceOpenPacket(voltage, pos), ply)
    }

    fun receiveChanges(voltage: Double, pos: BlockPos) {
        this.voltage = voltage
        this.pos = pos
    }

    override fun addSlotListener(pListener: ContainerListener) {
        super.addSlotListener(pListener)

        //Ideally this would be a cast of pListener to ServerPlayer, but pListener is a ServerPlayer$2,
        //which is some sort of uncastable wrapper around ServerPlayer
        sendDataToClient(ply as ServerPlayer)
    }
}
