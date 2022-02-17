package org.eln2.mc.common.packets.clientToServer

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraftforge.network.NetworkEvent
import org.eln2.mc.Eln2
import org.eln2.mc.client.gui.CellInfo
import org.eln2.mc.common.Networking
import org.eln2.mc.common.blocks.CellBlockEntity
import org.eln2.mc.common.packets.serverToClient.CircuitExplorerContextPacket
import java.util.function.Supplier

class CircuitExplorerOpenPacket() {
    constructor(buffer : FriendlyByteBuf) : this() { }

    companion object{
        @Suppress("UNUSED_PARAMETER") // This will be useful later
        fun encode(packet : CircuitExplorerOpenPacket, buffer : FriendlyByteBuf){ }
        @Suppress("UNUSED_PARAMETER") // This will be useful later
        fun handle(packet : CircuitExplorerOpenPacket, supplier : Supplier<NetworkEvent.Context>){
            val context = supplier.get()

            context.enqueueWork {
                handleServer(context.sender!!)
            }
        }

        private fun handleServer(player : ServerPlayer){
            val pickResult = player.pick(100.0, 0f, false)

            if(pickResult.type != HitResult.Type.BLOCK){
                return
            }

            val blockHit = pickResult as BlockHitResult
            val tile = player.level.getBlockEntity(blockHit.blockPos) as CellBlockEntity?

            if(tile !is CellBlockEntity){
                return
            }

            val cells = ArrayList(tile.cell.graph.cells.map { CellInfo(it.id.toString(), it.createDataPrint(), it.pos) })

            Networking.sendTo(CircuitExplorerContextPacket(cells, tile.cell.graph.latestSolveTime), player)
        }
    }
}
