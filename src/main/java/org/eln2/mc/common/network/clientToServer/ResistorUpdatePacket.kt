package org.eln2.mc.common.network.clientToServer

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkEvent
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.types.ResistorCell
import java.util.function.Supplier

class ResistorUpdatePacket(val resistance: Double, val pos: BlockPos) {

    companion object {
        fun encode(packet: ResistorUpdatePacket, buffer: FriendlyByteBuf) {
            buffer.writeDouble(packet.resistance)
            buffer.writeBlockPos(packet.pos)
        }

        fun decode(buffer: FriendlyByteBuf): ResistorUpdatePacket {
            return ResistorUpdatePacket(buffer.readDouble(), buffer.readBlockPos())
        }

        fun handle(packet: ResistorUpdatePacket, supplier: Supplier<NetworkEvent.Context>) {
            val ctx = supplier.get()

            ctx.enqueueWork {
                handleServer(ctx.sender!!, packet)
            }
        }

        private fun handleServer(sender: ServerPlayer, packet: ResistorUpdatePacket) {
            val te = sender.level.getBlockEntity(packet.pos)

            if (te is CellTileEntity) {
                if (te.cell is ResistorCell) {
                    (te.cell as ResistorCell).resistor.resistance = packet.resistance
                }
            }
        }
    }

}
