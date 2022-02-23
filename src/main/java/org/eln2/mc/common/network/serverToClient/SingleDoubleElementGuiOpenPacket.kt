package org.eln2.mc.common.network.serverToClient

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.network.NetworkEvent
import org.eln2.mc.common.cell.ISingleElementGuiCell
import org.eln2.mc.common.containers.SingleValueCellContainer
import java.util.function.Supplier

class SingleDoubleElementGuiOpenPacket(val value: Double, val pos: BlockPos) {

    companion object {
        fun encode(packet: SingleDoubleElementGuiOpenPacket, buffer: FriendlyByteBuf) {
            buffer.writeDouble(packet.value)
            buffer.writeBlockPos(packet.pos)
        }

        fun decode(buffer: FriendlyByteBuf): SingleDoubleElementGuiOpenPacket {
            return SingleDoubleElementGuiOpenPacket(buffer.readDouble(), buffer.readBlockPos())
        }

        fun handle(packet: SingleDoubleElementGuiOpenPacket, supplier: Supplier<NetworkEvent.Context>) {
            val ctx = supplier.get()

            ctx.enqueueWork {
                if (Dist.CLIENT == FMLEnvironment.dist) {
                    handleClient(packet, Minecraft.getInstance())
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun handleClient(packet: SingleDoubleElementGuiOpenPacket, mc: Minecraft) {
            val openContainer = mc.player?.containerMenu

            if (openContainer is SingleValueCellContainer<*, *>) {
                (openContainer as? SingleValueCellContainer<ISingleElementGuiCell<Double>, Double>)?.setSyncedValue(
                    packet.value,
                    packet.pos
                )
                    ?: error("Attempted to write a double value from GUI to a non-double holding TE")
            }
        }
    }

}
