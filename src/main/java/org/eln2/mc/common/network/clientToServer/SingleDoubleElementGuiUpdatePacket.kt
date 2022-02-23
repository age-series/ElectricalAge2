package org.eln2.mc.common.network.clientToServer

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkEvent
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.ISingleElementGuiCell
import java.util.function.Supplier

class SingleDoubleElementGuiUpdatePacket(val value: Double, val pos: BlockPos) {

    companion object {
        fun encode(packet: SingleDoubleElementGuiUpdatePacket, buffer: FriendlyByteBuf) {
            buffer.writeDouble(packet.value)
            buffer.writeBlockPos(packet.pos)
        }

        fun decode(buffer: FriendlyByteBuf): SingleDoubleElementGuiUpdatePacket {
            return SingleDoubleElementGuiUpdatePacket(buffer.readDouble(), buffer.readBlockPos())
        }

        fun handle(packet: SingleDoubleElementGuiUpdatePacket, supplier: Supplier<NetworkEvent.Context>) {
            val ctx = supplier.get()

            ctx.enqueueWork {
                handleServer(ctx.sender!!, packet)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun handleServer(sender: ServerPlayer, packet: SingleDoubleElementGuiUpdatePacket) {
            val te = sender.level.getBlockEntity(packet.pos)

            if (te is CellTileEntity) {
                if (te.cell is ISingleElementGuiCell<*>) {
                    (te.cell as? ISingleElementGuiCell<Double>)?.setGuiValue(packet.value)
                        ?: error("Attempted to set double value from GUI for non-double-holding TE")
                }
            }
        }
    }

}
