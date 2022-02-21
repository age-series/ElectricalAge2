@file:Suppress("INACCESSIBLE_TYPE")

package org.eln2.mc.common.network

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import org.eln2.mc.Eln2
import org.eln2.mc.common.network.clientToServer.CircuitExplorerOpenPacket
import org.eln2.mc.common.network.clientToServer.VoltageSourceUpdatePacket
import org.eln2.mc.common.network.serverToClient.CircuitExplorerContextPacket
import org.eln2.mc.common.network.serverToClient.VoltageSourceOpenPacket

typealias CEOPacket = CircuitExplorerOpenPacket
typealias CECPacket = CircuitExplorerContextPacket

enum class PacketType(val id: Int) {
    CIRCUIT_EXPLORER_OPEN_PACKET(0),
    CIRCUIT_EXPLORER_CONTEXT_PACKET(1),
    VOLTAGE_SOURCE_UPDATE_PACKET(2),
    VOLTAGE_SOURCE_OPEN_PACKET(3),
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
            CEOPacket::class.java,
            CEOPacket::encode,
            ::CEOPacket,
            CEOPacket::handle
        )
        channel.registerMessage(
            PacketType.CIRCUIT_EXPLORER_CONTEXT_PACKET.id,
            CECPacket::class.java,
            CECPacket::encode,
            ::CECPacket,
            CECPacket::handle
        )
        channel.registerMessage(
            PacketType.VOLTAGE_SOURCE_UPDATE_PACKET.id,
            VoltageSourceUpdatePacket::class.java,
            VoltageSourceUpdatePacket::encode,
            ::VoltageSourceUpdatePacket,
            VoltageSourceUpdatePacket::handle
        )
        channel.registerMessage(
            PacketType.VOLTAGE_SOURCE_OPEN_PACKET.id,
            VoltageSourceOpenPacket::class.java,
            VoltageSourceOpenPacket::encode,
            ::VoltageSourceOpenPacket,
            VoltageSourceOpenPacket::handle
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
