@file:Suppress("INACCESSIBLE_TYPE")

package org.eln2.mc.common.network

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import org.eln2.mc.LOG
import org.eln2.mc.MODID
import org.eln2.mc.common.blocks.foundation.GhostLightChunkDataMessage
import org.eln2.mc.common.blocks.foundation.GhostLightCommandMessage
import org.eln2.mc.common.content.GridConnectionCreateMessage
import org.eln2.mc.common.content.GridConnectionDeleteMessage
import org.eln2.mc.common.network.serverToClient.*
import java.util.*

object Networking {
    private const val protocolVersion = "1"
    private const val channelName = "main"

    private val channel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(MODID, channelName),
        { protocolVersion },
        { it == protocolVersion },
        { it == protocolVersion })

    private var id = 0
    fun id() = id++

    fun setup() {
        LOG.info("Network packets registered")

        channel.registerMessage(
            id(),
            BulkPartMessage::class.java,
            BulkPartMessage::encode,
            BulkPartMessage::decode,
            BulkPartMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )

        channel.registerMessage(
            id(),
            GhostLightCommandMessage::class.java,
            GhostLightCommandMessage::encode,
            GhostLightCommandMessage::decode,
            GhostLightCommandMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )

        channel.registerMessage(
            id(),
            GhostLightChunkDataMessage::class.java,
            GhostLightChunkDataMessage::encode,
            GhostLightChunkDataMessage::decode,
            GhostLightChunkDataMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )

        channel.registerMessage(
            id(),
            GridConnectionCreateMessage::class.java,
            GridConnectionCreateMessage::encode,
            GridConnectionCreateMessage::decode,
            GridConnectionCreateMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )

        channel.registerMessage(
            id(),
            GridConnectionDeleteMessage::class.java,
            GridConnectionDeleteMessage::encode,
            GridConnectionDeleteMessage::decode,
            GridConnectionDeleteMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )
    }

    /**
     * Sends a message from the server to the client.
     * @param player The player to send the message to.
     */
    fun send(message: Any?, player: ServerPlayer) {
        channel.sendTo(message, player.connection.getConnection(), NetworkDirection.PLAY_TO_CLIENT)
    }

    /**
     * Sends the message from the client to the server.
     */
    fun sendToServer(message: Any?) {
        channel.sendToServer(message)
    }
}
