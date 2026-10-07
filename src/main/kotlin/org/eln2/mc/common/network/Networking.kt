@file:Suppress("INACCESSIBLE_TYPE")

package org.eln2.mc.common.network

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.NetworkRegistry
import org.eln2.mc.LOG
import org.eln2.mc.MODID
import org.eln2.mc.common.*
import org.eln2.mc.common.content.DrillPowerMessage
import org.eln2.mc.common.content.FlashlightPowerMessage
import org.eln2.mc.common.content.ScrewdriverItem
import org.eln2.mc.common.grids.GridConnectionCreateMessage
import org.eln2.mc.common.grids.GridConnectionDeleteMessage
import org.eln2.mc.common.grids.GridConnectionUpdateRenderMessage
import org.eln2.mc.common.network.serverToClient.*
import org.eln2.mc.common.specs.foundation.SpecOverlayMessage
import java.util.*

object Networking {
    private const val PROTOCOL_VERSION = "1"
    private const val CHANNEL_NAME = "main"

    private val channel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(MODID, CHANNEL_NAME),
        { PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION })

    private var id = 0
    fun id() = id++

    @Suppress("INFERRED_INVISIBLE_RETURN_TYPE_WARNING") // what?
    fun setup() {
        channel.registerMessage(
            id(),
            BulkDimensionMessagePart::class.java,
            BulkDimensionMessagePart::encode,
            BulkDimensionMessagePart::decode,
            BulkDimensionMessagePart::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )

        channel.registerMessage(
            id(),
            BulkDimensionMessageBlockEntity::class.java,
            BulkDimensionMessageBlockEntity::encode,
            BulkDimensionMessageBlockEntity::decode,
            BulkDimensionMessageBlockEntity::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )

        channel.registerMessage(
            id(),
            DimensionMessageToServerPart::class.java,
            DimensionMessageToServerPart::encode,
            DimensionMessageToServerPart::decode,
            DimensionMessageToServerPart::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
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

        channel.registerMessage(
            id(),
            GridConnectionUpdateRenderMessage::class.java,
            GridConnectionUpdateRenderMessage::encode,
            GridConnectionUpdateRenderMessage::decode,
            GridConnectionUpdateRenderMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )

        channel.registerMessage(
            id(),
            SpecOverlayMessage::class.java,
            SpecOverlayMessage::encode,
            SpecOverlayMessage::decode,
            SpecOverlayMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        )

        channel.registerMessage(
            id(),
            ScrewdriverItem.Scroll::class.java,
            ScrewdriverItem.Scroll::encode,
            ScrewdriverItem.Scroll::decode,
            ScrewdriverItem.Scroll::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        )

        channel.registerMessage(
            id(),
            ScrewdriverItem.SetConfigValue::class.java,
            ScrewdriverItem.SetConfigValue::encode,
            ScrewdriverItem.SetConfigValue::decode,
            ScrewdriverItem.SetConfigValue::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER)
        )

        channel.registerMessage(
            id(),
            DrillPowerMessage::class.java,
            DrillPowerMessage::encode,
            DrillPowerMessage::decode,
            DrillPowerMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )

        channel.registerMessage(
            id(),
            FlashlightPowerMessage::class.java,
            FlashlightPowerMessage::encode,
            FlashlightPowerMessage::decode,
            FlashlightPowerMessage::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        )

        LOG.info("Network packets registered")
    }

    /**
     * Sends a message from the server to the client.
     * @param player The player to send the message to.
     */
    fun send(message: Any?, player: ServerPlayer) {
        channel.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT)
    }

    /**
     * Sends a message from the server to all players tracking [entity] (and [entity] itself if it is a player).
     * */
    fun sendToTrackingAndSelf(message: Any?, entity: net.minecraft.world.entity.Entity) {
        channel.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with { entity }, message)
    }

    /**
     * Sends the message from the client to the server.
     */
    fun sendToServer(message: Any?) {
        channel.sendToServer(message)
    }
}
