package org.eln2.mc.common.containers

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerListener
import net.minecraft.world.inventory.MenuType
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.cells.foundation.ISingleElementGuiCell

abstract class SingleValueCellContainer<C : ISingleElementGuiCell<N>, N : Number>(
    id: Int,
    plyInv: Inventory,
    private val ply: Player,
    type: MenuType<*>
) :
    AbstractContainerMenu(type, id) {

    abstract var value: N
    var pos: BlockPos = BlockPos.ZERO
    var te: CellBlockEntity? = null

    open fun getSyncedValue(): N {
        return this.value
    }

    open fun setSyncedValue(value: N, pos: BlockPos): Boolean {
        this.value = value
        this.pos = pos

        return true
    }

    abstract fun sendDataToClient(ply: ServerPlayer)

    open fun sendDataToAllClients() {
        containerListeners.forEach { listener ->
            if (listener is ServerPlayer) {
                sendDataToClient(listener)
            }
        }
    }

    override fun addSlotListener(pListener: ContainerListener) {
        super.addSlotListener(pListener)

        //Ideally this would be a cast of pListener to ServerPlayer, but pListener is a ServerPlayer$2,
        //which is some sort of uncastable wrapper around ServerPlayer
        sendDataToClient(ply as ServerPlayer)
    }

    @Suppress("UNCHECKED_CAST")
    override fun broadcastChanges() {
        if (this.value != (te?.cell as? C)?.getGuiValue() || this.pos != te?.pos) {
            this.sendDataToAllClients()
        }
    }

    override fun broadcastFullState() {
        this.sendDataToAllClients()
    }
}
