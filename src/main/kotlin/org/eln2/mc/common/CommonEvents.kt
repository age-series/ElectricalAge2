package org.eln2.mc.common

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.LevelReader
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.ClientTickEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.event.level.ChunkWatchEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.data.mutableMultiMapOf
import net.minecraft.core.BlockPos
import net.minecraftforge.event.level.LevelEvent
import org.eln2.mc.LOG
import org.eln2.mc.common.blocks.foundation.GhostLight
import org.eln2.mc.common.blocks.foundation.GhostLightHackClient
import org.eln2.mc.common.blocks.foundation.GhostLightServer
import org.eln2.mc.common.cells.foundation.CellGraphManager
import org.eln2.mc.common.events.schedulePost
import org.eln2.mc.data.AveragingList
import org.eln2.mc.plus
import org.eln2.mc.toVec3

data class LevelDataStorage(val map: HashMap<Any, Any>) {
    inline fun <K : Any, reified V : Any> read(key: K): V? {
        val result = map[key] ?: return null
        return result as? V ?: error("Invalid stored type $result for ${V::class}")
    }

    inline fun <K : Any, reified V : Any> remove(key: K): V? {
        val result = read<K, V>(key)

        if (result != null) {
            map.remove(key)
        }

        return result
    }

    inline fun <K : Any, reified V : Any> store(key: K, value: V): V? {
        val old = read<K, V>(key)
        map[key] = value
        return old
    }

    inline fun <reified K : Any, reified V : Any> storeNew(key: K, value: V) = require(store(key, value) == null) {
        "Existing value for ${K::class}"
    }
}

private val dataStorage = HashMap<LevelReader, LevelDataStorage>()

fun getLevelDataStorage(level: LevelReader) = dataStorage.getOrPut(level) {
    require(ServerLifecycleHooks.getCurrentServer().isSameThread) {
        "Cannot access LDS outside the server thread"
    }

    require(!level.isClientSide) {
        "Cannot access LDS outside a server level"
    }

    LevelDataStorage(HashMap())
}

@Mod.EventBusSubscriber
object CommonEvents {
    private val upsAveragingList = AveragingList(100)
    private val tickTimeAveragingList = AveragingList(100)
    private var logCountdown = 0
    private const val logInterval = 100

    @SubscribeEvent @JvmStatic
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            var tickRate = 0.0
            var tickTime = 0.0

            ServerLifecycleHooks.getCurrentServer().allLevels.forEach {
                val graph = CellGraphManager.getFor(it)

                tickRate += graph.sampleTickRate()
                tickTime += graph.totalSpentTime
            }

            upsAveragingList.addSample(tickRate)
            tickTimeAveragingList.addSample(tickTime)

            if (logCountdown-- == 0) {
                logCountdown = logInterval

                LOG.info("Total simulation rate: ${upsAveragingList.calculate()} Updates/Second")
                LOG.info("Total simulation time: ${tickTimeAveragingList.calculate()}")
            }

            GhostLightServer.applyChanges()
        }
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerWatch(event: ChunkWatchEvent.Watch) {
        GhostLightServer.playerWatch(event.level, event.player, event.pos)
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerUnwatch(event: ChunkWatchEvent.UnWatch) {
        GhostLightServer.playerUnwatch(event.level, event.player, event.pos)
    }

    @SubscribeEvent @JvmStatic
    fun onServerStopping(event: ServerStoppingEvent) {
        event.server.allLevels.forEach {
            LOG.info("Stopping simulations for $it")
            CellGraphManager.getFor(it).serverStop()
        }
    }

    private fun scheduleGhostEvent(event: BlockEvent) {
        if(event.level.isClientSide) {
            return
        }

        schedulePost(0) {
            if(!event.isCanceled) {
                GhostLightServer.handleBlockEvent(event.level as ServerLevel, event.pos)
            }
        }
    }

    @SubscribeEvent @JvmStatic
    fun onBlockBreakEvent(event: BlockEvent.BreakEvent) {
        scheduleGhostEvent(event)
    }

    @SubscribeEvent @JvmStatic
    fun onEntityPlaceEvent(event: BlockEvent.EntityPlaceEvent) {
        scheduleGhostEvent(event)
    }

    @SubscribeEvent @JvmStatic
    fun onClientLevelClosed(event: LevelEvent.Unload) {
        if(event.level.isClientSide) {
            GhostLightHackClient.clear()
        }
    }
}
