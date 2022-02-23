@file:Suppress("INACCESSIBLE_TYPE")

package org.eln2.mc.common.network

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import org.eln2.mc.Eln2
import org.eln2.mc.common.network.clientToServer.CircuitExplorerOpenPacket
import org.eln2.mc.common.network.clientToServer.SingleDoubleElementGuiUpdatePacket
import org.eln2.mc.common.network.serverToClient.CircuitExplorerContextPacket
import org.eln2.mc.common.network.serverToClient.SingleDoubleElementGuiOpenPacket

enum class PacketType(val id: Int) {
    CIRCUIT_EXPLORER_OPEN_PACKET(0),
    CIRCUIT_EXPLORER_CONTEXT_PACKET(1),
    SINGLE_DOUBLE_ELEMENT_GUI_UPDATE_PACKET(2),
    SINGLE_DOUBLE_ELEMENT_GUI_OPEN_PACKET(3),
}

object Networking {
    private const val protocolVersion = "1"
    private const val channelName = "main"
    private val channel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(Eln2.MODID, channelName),
        { protocolVersion },
        { it == protocolVersion },
        { it == protocolVersion })


    fun setup() {
        Eln2.LOGGER.info("Registering network packets")
        channel.registerMessage(
            PacketType.CIRCUIT_EXPLORER_OPEN_PACKET.id,
            CircuitExplorerOpenPacket::class.java,
            CircuitExplorerOpenPacket::encode,
            ::CircuitExplorerOpenPacket,
            CircuitExplorerOpenPacket::handle
        )
        channel.registerMessage(
            PacketType.CIRCUIT_EXPLORER_CONTEXT_PACKET.id,
            CircuitExplorerContextPacket::class.java,
            CircuitExplorerContextPacket::encode,
            ::CircuitExplorerContextPacket,
            CircuitExplorerContextPacket::handle
        )
        channel.registerMessage(
            PacketType.SINGLE_DOUBLE_ELEMENT_GUI_UPDATE_PACKET.id,
            SingleDoubleElementGuiUpdatePacket::class.java,
            SingleDoubleElementGuiUpdatePacket::encode,
            SingleDoubleElementGuiUpdatePacket::decode,
            SingleDoubleElementGuiUpdatePacket::handle
        )
        channel.registerMessage(
            PacketType.SINGLE_DOUBLE_ELEMENT_GUI_OPEN_PACKET.id,
            SingleDoubleElementGuiOpenPacket::class.java,
            SingleDoubleElementGuiOpenPacket::encode,
            SingleDoubleElementGuiOpenPacket::decode,
            SingleDoubleElementGuiOpenPacket::handle
        )
        Eln2.LOGGER.info("Network packets registered")
    }

    /**
     * Sends a message from the server to the client.
     * @param player The player to send the message to.
     */
    fun sendTo(message: Any?, player: ServerPlayer) {
        channel.sendTo(message, player.connection.getConnection(), NetworkDirection.PLAY_TO_CLIENT)
    }

    /**
     * Sends the message from the client to the server.
    */
    fun sendToServer(message: Any?) {
        channel.sendToServer(message)
    }
}
