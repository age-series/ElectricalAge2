package org.eln2.mc.common

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.data.MutableSetMapMultiMap
import org.eln2.mc.common.events.Event
import org.eln2.mc.common.events.EventHandlerManager
import org.eln2.mc.common.events.EventManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class LocatedBlockState(val position: BlockPos, val state: BlockState)
data class BlockEventFrame(val set: List<LocatedBlockState>)

interface BlockEventSource {
    val gtEvents: EventHandlerManager
    val chunkPos: ChunkPos
    fun dispose()
}

private val eventSourceProviders = ConcurrentHashMap<LevelReader, BlockStreamProvider>()

fun LevelReader.getEventSourceProvider() = eventSourceProviders.getOrPut(this) {
    BlockStreamProvider(this)
}

data class GTBSUpdateEvent(
    val position: BlockPos,
    val oldState: BlockState?,
    val newState: BlockState,
    val type: Type,
    val source: BlockEventSource,
) : Event {
    enum class Type { Place, Remove }

    fun actual() = BlockEventFrame(
        listOf(
            LocatedBlockState(position, newState)
        )
    )
}

data class GTBSInitialSyncEvent(
    val frame: BlockEventFrame,
    val source: BlockEventSource,
) : Event

class BlockStreamProvider(val level: LevelReader) {
    private val pending = ConcurrentLinkedQueue<Source>()
    private val sources = MutableSetMapMultiMap<ChunkPos, Source>()
    private var id = 0

    fun openEventSource(chunkPos: ChunkPos, init: (BlockEventSource) -> Unit): BlockEventSource {
        synchronized(this) {
            val result = Source(chunkPos, id++, this).also(init)
            sources[chunkPos].add(result)
            pending.add(result)
            return result
        }
    }

    private fun destroySource(s: Source) {
        synchronized(this) {
            pending.remove(s)
            if (!sources[s.chunkPos].remove(s)) {
                error("Invalid source $s")
            }
        }
    }

    fun gtDispatchInitial() {
        while (true) {
            val src = pending.poll() ?: break

            val buffer = createLargeFrameBuffer(
                (level.maxBuildHeight - level.minBuildHeight) * (src.chunkPos.maxBlockZ - src.chunkPos.minBlockZ) * (src.chunkPos.maxBlockX - src.chunkPos.minBlockX)
            )

            for (y in level.minBuildHeight..level.maxBuildHeight) {
                for (z in src.chunkPos.minBlockZ..src.chunkPos.maxBlockZ) {
                    for (x in src.chunkPos.minBlockX..src.chunkPos.maxBlockX) {
                        val pos = BlockPos(x, y, z)

                        buffer.add(
                            LocatedBlockState(
                                pos,
                                level.getBlockState(pos)
                            )
                        )
                    }
                }
            }

            println("Init $src")
            val received = src.gtEvents.send(GTBSInitialSyncEvent(BlockEventFrame(buffer), src))
            println("Done init $received")
        }
    }

    private fun createLargeFrameBuffer(iSize: Int) =
        ArrayList<LocatedBlockState>(iSize) // maybe see if pools would help

    fun gtDispatchEvent(pos: BlockPos, oldState: BlockState?, newState: BlockState, type: GTBSUpdateEvent.Type) {
        val targets: Array<Source>

        synchronized(this) {
            targets = sources[ChunkPos(pos)].toTypedArray()
        }

        gtDispatchInitial()

        targets.forEach {
            it.gtEvents.send(
                GTBSUpdateEvent(
                    position = pos,
                    oldState = oldState,
                    newState = newState,
                    source = it,
                    type = type
                )
            )
        }
    }

    private data class Source(override val chunkPos: ChunkPos, val id: Int, val manager: BlockStreamProvider) :
        BlockEventSource {
        private val destroyed = AtomicBoolean()

        override val gtEvents = EventManager(
            setOf(
                GTBSInitialSyncEvent::class,
                GTBSUpdateEvent::class
            )
        )

        override fun dispose() {
            if (destroyed.getAndSet(true)) {
                return
            }

            manager.destroySource(this)
        }
    }
}
// todo forgot this exists, do we remove?
@Mod.EventBusSubscriber
object BlockStreamEvents {
    @SubscribeEvent
    @JvmStatic
    fun onTick(event: ServerTickEvent) {
        ServerLifecycleHooks.getCurrentServer().allLevels.forEach {
            it.getEventSourceProvider().gtDispatchInitial()
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        if (event.level.isClientSide) {
            return
        }

        event.level.getEventSourceProvider().also { provider ->
            provider.gtDispatchEvent(
                pos = event.pos,
                oldState = null,
                newState = event.state,
                GTBSUpdateEvent.Type.Place
            )
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onFluidCreate(event: BlockEvent.CreateFluidSourceEvent) {
        if (event.level.isClientSide) {
            return
        }

        event.level.getEventSourceProvider().also { provider ->
            provider.gtDispatchEvent(
                pos = event.pos,
                oldState = null,
                newState = event.state,
                type = GTBSUpdateEvent.Type.Place
            )
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onBreak(event: BlockEvent.BreakEvent) {
        if (event.level.isClientSide) {
            return
        }

        event.level.getEventSourceProvider().also { provider ->
            provider.gtDispatchEvent(
                pos = event.pos,
                oldState = event.state,
                newState = Blocks.AIR.defaultBlockState(),
                type = GTBSUpdateEvent.Type.Remove
            )
        }
    }
}
