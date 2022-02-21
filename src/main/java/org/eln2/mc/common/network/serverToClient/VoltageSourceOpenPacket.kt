package org.eln2.mc.common.network.serverToClient

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.network.NetworkEvent
import org.eln2.mc.common.containers.VoltageSourceCellContainer
import java.util.function.Supplier

class VoltageSourceOpenPacket(val voltage: Double, val pos: BlockPos) {

    constructor(buffer: FriendlyByteBuf) : this(buffer.readDouble(), buffer.readBlockPos())

    companion object {
        fun encode(packet: VoltageSourceOpenPacket, buffer: FriendlyByteBuf) {
            buffer.writeDouble(packet.voltage)
            buffer.writeBlockPos(packet.pos)
        }

        fun handle(packet: VoltageSourceOpenPacket, supplier: Supplier<NetworkEvent.Context>) {
            val ctx = supplier.get()

            ctx.enqueueWork {
                if (Dist.CLIENT == FMLEnvironment.dist) {
                    handleClient(packet, Minecraft.getInstance())
                }
            }
        }

        fun handleClient(packet: VoltageSourceOpenPacket, mc: Minecraft) {
            val openContainer = mc.player?.containerMenu

            if (openContainer is VoltageSourceCellContainer) {
                println("Updating the container $openContainer")
                openContainer.receiveChanges(packet.voltage, packet.pos)
            }
        }
    }

}
