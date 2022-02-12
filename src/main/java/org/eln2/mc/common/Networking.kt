@file:Suppress("INACCESSIBLE_TYPE")

package org.eln2.mc.common

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import org.eln2.mc.Eln2
import org.eln2.mc.common.packets.clientToServer.CircuitExplorerOpenPacket
import org.eln2.mc.common.packets.serverToClient.CircuitExplorerContextPacket

typealias CEOPacket = CircuitExplorerOpenPacket
typealias CECPacket = CircuitExplorerContextPacket

object Networking {
    private const val protocolVersion = "1"
    private const val channelName = "main"
    private val channel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(Eln2.MODID, channelName),
        { protocolVersion},
        {it == protocolVersion},
        {it == protocolVersion})


    fun setup(){
        var id = -1

        channel.registerMessage(++id, CEOPacket::class.java, CEOPacket::encode, ::CEOPacket, CEOPacket::handle)
        channel.registerMessage(++id, CECPacket::class.java, CECPacket::encode, ::CECPacket, CECPacket::handle)
    }

    /**
     * Sends a message from the server to the client.
     * @param player The player to send the message to.
    */
    @In(Side.LogicalServer)
    fun sendTo(message: Any?, player: ServerPlayer) {
        channel.sendTo(message, player.connection.getConnection(), NetworkDirection.PLAY_TO_CLIENT)
    }

    /**
     * Sends the message from the client to the server.
    */
    @In(Side.PhysicalClient)
    fun sendToServer(message: Any?) {
        channel.sendToServer(message)
    }
}
