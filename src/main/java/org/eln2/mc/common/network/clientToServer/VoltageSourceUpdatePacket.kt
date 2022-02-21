package org.eln2.mc.common.network.clientToServer

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkEvent
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.types.VoltageSourceCell
import java.util.function.Supplier

class VoltageSourceUpdatePacket(val voltage: Double, val pos: BlockPos) {

    constructor(buffer: FriendlyByteBuf) : this(buffer.readDouble(), buffer.readBlockPos())

    companion object {
        fun encode(packet: VoltageSourceUpdatePacket, buffer: FriendlyByteBuf) {
            buffer.writeDouble(packet.voltage)
            buffer.writeBlockPos(packet.pos)
        }

        fun handle(packet: VoltageSourceUpdatePacket, supplier: Supplier<NetworkEvent.Context>) {
            val ctx = supplier.get()

            ctx.enqueueWork {
                val voltage = packet.voltage
                val pos = packet.pos
                handleServer(ctx.sender!!, voltage, pos)
            }
        }

        fun handleServer(sender: ServerPlayer, voltage: Double, pos: BlockPos) {
            val te = sender.level.getBlockEntity(pos)
            if (te is CellTileEntity) {
                if (te.cell is VoltageSourceCell) {
                    (te.cell as VoltageSourceCell).source.potential = voltage
                }
            }
        }
    }

}
