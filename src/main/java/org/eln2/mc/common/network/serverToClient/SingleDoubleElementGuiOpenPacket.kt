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

/**
 * SingleDoubleElementGuiOpenPacket is sent when the GUI of a [ISingleElementGuiCell] with a Double value
 * is opened by a client. This populates the client container with values from the Block Entity
 * @see [org.eln2.mc.common.network.clientToServer.SingleDoubleElementGuiUpdatePacket]
 */
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
            ctx.packetHandled = true
        }

        private fun handleClient(packet: SingleDoubleElementGuiOpenPacket, mc: Minecraft) {
            val openContainer = mc.player?.containerMenu

            if (openContainer is SingleValueCellContainer<*, *>) {
                @Suppress("UNCHECKED_CAST")
                (openContainer as? SingleValueCellContainer<ISingleElementGuiCell<Double>, Double>)?.setSyncedValue(
                    packet.value,
                    packet.pos
                )
                    ?: error("Attempted to write a double value from GUI to a non-double holding TE")
            }
        }
    }

}
