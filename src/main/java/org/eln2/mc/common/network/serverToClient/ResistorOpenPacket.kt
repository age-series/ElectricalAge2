package org.eln2.mc.common.network.serverToClient

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.network.NetworkEvent
import org.eln2.mc.common.containers.ResistorCellContainer
import java.util.function.Supplier

class ResistorOpenPacket(val resistance: Double, val pos: BlockPos) {

    companion object {
        fun encode(packet: ResistorOpenPacket, buffer: FriendlyByteBuf) {
            buffer.writeDouble(packet.resistance)
            buffer.writeBlockPos(packet.pos)
        }

        fun decode(buffer: FriendlyByteBuf): ResistorOpenPacket {
            return ResistorOpenPacket(buffer.readDouble(), buffer.readBlockPos())
        }

        fun handle(packet: ResistorOpenPacket, supplier: Supplier<NetworkEvent.Context>) {
            val ctx = supplier.get()

            if (Dist.CLIENT == FMLEnvironment.dist) {
                ctx.enqueueWork {
                    handleClient(packet, Minecraft.getInstance())
                }
            }
        }

        private fun handleClient(packet: ResistorOpenPacket, mc: Minecraft) {
            val openContainer = mc.player?.containerMenu

            if (openContainer is ResistorCellContainer) {
                openContainer.receiveChanges(packet.resistance, packet.pos)
            }
        }
    }

}
