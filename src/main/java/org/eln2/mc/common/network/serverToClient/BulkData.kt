package org.eln2.mc.common.network.serverToClient

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.server.ServerLifecycleHooks
import org.eln2.mc.CrossThreadAccess
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.events.Event
import org.eln2.mc.common.network.Networking
import org.eln2.mc.utility.reflectId
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Supplier

fun ByteBuffer.putBlockPos(pos: BlockPos) {
    this.putInt(pos.x)
    this.putInt(pos.y)
    this.putInt(pos.z)
}

fun ByteBuffer.getBlockPos() = BlockPos(this.int, this.int, this.int)

fun ByteBuffer.putDirection(dir: Direction) = this.putInt(dir.get3DDataValue())
fun ByteBuffer.getDirection() = Direction.from3DDataValue(this.int)

fun ByteBuffer.putArray(array: ByteArray) {
    this.putInt(array.size)

    if(array.isNotEmpty()) {
        this.put(array)
    }
}

fun ByteBuffer.getArray(): ByteArray {
    val result = ByteArray(this.int)

    if(result.isNotEmpty()) {
        this.get(result)
    }

    return result
}

fun Int.encode(): ByteArray {
    val result = ByteArray(4)
    result[0] = (this shr 0).toByte()
    result[1] = (this shr 8).toByte()
    result[2] = (this shr 16).toByte()
    result[3] = (this shr 24).toByte()
    return result
}

fun ByteArray.decodeInt(): Int =
    (this[3].toInt() shl 24) or
    (this[2].toInt() and 0xff shl 16) or
    (this[1].toInt() and 0xff shl 8) or
    (this[0].toInt() and 0xff)

infix fun ByteBuffer.with(i: Int): ByteBuffer {
    this.putInt(i)
    return this
}

infix fun ByteBuffer.with(d: Double): ByteBuffer {
    this.putDouble(d)
    return this
}

fun saveByteArrays(messages: List<ByteArray>): ByteBuffer {
    val buffer = ByteBuffer.allocate(messages.sumOf { it.size } + messages.size * 4)

    buffer.putInt(messages.size)

    messages.forEach { message ->
        buffer.putArray(message)
    }

    return buffer
}

fun loadByteArrays(buffer: ByteBuffer): ArrayList<ByteArray> {
    val cnt = buffer.int
    val results = ArrayList<ByteArray>(cnt)

    repeat(cnt) {
        results.add(buffer.getArray())
    }

    return results
}

class PartMessage(val pos: BlockPos, val face: Direction, val payload: ByteArray) {
    val size get() =
        3 * 4 +
        1 * 4 +
        (4 + payload.size)

    fun save(buffer: ByteBuffer) {
        buffer.putBlockPos(pos)
        buffer.putDirection(face)
        buffer.putArray(payload)
    }

    companion object {
        fun load(buffer: ByteBuffer) = PartMessage(
            buffer.getBlockPos(),
            buffer.getDirection(),
            buffer.getArray()
        )
    }
}

class BulkPartMessage(val dim: Int, val messages: List<PartMessage>) {
    val size get() =
        1 * 4 +
        1 * 4 +
        messages.sumOf { it.size }

    fun save(): ByteArray {
        val result = ByteArray(size)
        val buffer = ByteBuffer.wrap(result)

        buffer.putInt(dim)
        buffer.putInt(messages.size)

        messages.forEach {
            it.save(buffer)
        }

        return result
    }

    companion object {
        fun load(data: ByteArray): BulkPartMessage {
            val buffer = ByteBuffer.wrap(data)

            val dim = buffer.int
            val cnt = buffer.int

            val results = ArrayList<PartMessage>(cnt)

            repeat(cnt) {
                results.add(PartMessage.load(buffer))
            }

            return BulkPartMessage(dim, results)
        }
    }
}

fun ResourceLocation.id(): Int = this.hashCode()

@Mod.EventBusSubscriber
object BulkMessages {
    fun encodeBulkPartMessage(message: BulkPartMessage, buf: FriendlyByteBuf): FriendlyByteBuf = buf.writeByteArray(message.save())
    fun decodeBulkPartMessage(buf: FriendlyByteBuf): BulkPartMessage = BulkPartMessage.load(buf.readByteArray())

    fun handleBulkPartMessage(bulkMsg: BulkPartMessage, ctx: Supplier<NetworkEvent.Context>) {
        ctx.get().enqueueWork {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
                Runnable {
                    val actualLevel = Minecraft.getInstance().level

                    if(actualLevel == null) {
                        LOGGER.error("Got bulk message, but level is null")
                        return@Runnable
                    }

                    if(actualLevel.dimension().registryName.id() != bulkMsg.dim) {
                        // Cheap check to make sure we don't get a badly timed packet
                        return@Runnable
                    }

                    bulkMsg.messages.forEach { msg ->
                        val entity = actualLevel.getBlockEntity(msg.pos)

                        if(entity !is MultipartBlockEntity) {
                            LOGGER.error("Rogue multipart message $msg")
                            return@forEach
                        }

                        val part = entity.getPart(msg.face)

                        if(part == null) {
                            LOGGER.error("Lingering multipart $msg")
                            return@forEach
                        }

                        part.handleBulkMessage(msg.payload)
                    }
                }
            }
        }

        ctx.get().packetHandled = true
    }

    @CrossThreadAccess
    private val bulkPartMessages = ConcurrentHashMap<ServerLevel, ConcurrentLinkedDeque<PartMessage>>()
    fun enqueuePartMessage(level: ServerLevel, msg: PartMessage) = bulkPartMessages.getOrPut(level, ::ConcurrentLinkedDeque).add(msg)

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            flushPartData()
        }
    }

    private val bulkPartMessageBuffer = ArrayList<PartMessage>()
    private fun flushPartData() {
        bulkPartMessages.forEach { (level, queue) ->
            while(true) {
                val msg = queue.poll() ?: break
                bulkPartMessageBuffer.add(msg)
            }

            val message = BulkPartMessage(
                level.dimension().registryName.id(),
                bulkPartMessageBuffer.toList()
            )

            ServerLifecycleHooks.getCurrentServer().playerList.players.forEach { player ->
                if(player.level == level) {
                    Networking.sendTo(message, player)
                }
            }

            bulkPartMessageBuffer.clear()
        }
    }
}

fun interface DeserializeHandler {
    fun handle(binary: ByteArray)
}

class PacketHandlerBuilder {
    val registeredIds = HashMap<Int, DeserializeHandler>()

    inline fun<reified P> withHandler(crossinline consume: (P) -> Unit): PacketHandlerBuilder {
        registeredIds[P::class.reflectId] = DeserializeHandler {
            val instance = Json.decodeFromString<P>(it.decodeToString())

            try {
                consume(instance)
            }
            catch (t: Throwable) {
                LOGGER.error("Failed to handle ${P::class}: $t")
            }
        }

        return this
    }

    fun build() = PacketHandler(registeredIds.toMap())
}

class PacketHandler(private val registeredIds: Map<Int, DeserializeHandler>) {
    fun handle(data: ByteArray): Boolean {
        val buffer = ByteBuffer.wrap(data)

        val id = buffer.int

        val handler = registeredIds[id]

        if(handler == null) {
            LOGGER.error("Unhandled packet $id")
            return false
        }

        val payload = ByteArray(data.size - 4)
        buffer.get(payload)
        handler.handle(payload)

        return true
    }
    
    companion object {
        inline fun<reified P> encode(packet: P): ByteArray {
            val data = Json.encodeToString(packet).encodeToByteArray()

            val sendBuffer = ByteArray(4 + data.size)
            val result = ByteBuffer.wrap(sendBuffer)

            result.putInt(P::class.reflectId)
            result.put(data)

            return sendBuffer
        }
    }
}
