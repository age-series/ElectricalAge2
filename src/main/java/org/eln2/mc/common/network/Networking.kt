@file:Suppress("INACCESSIBLE_TYPE")

package org.eln2.mc.common.network

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import org.eln2.mc.Eln2

enum class PacketType(val id: Int) {

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
