package org.eln2.mc.common

import com.charleskorn.kaml.Yaml
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.LevelReader
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.server.ServerLifecycleHooks
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.blocks.foundation.GhostLightBlock
import org.eln2.mc.common.cells.foundation.CellGraphManager
import org.eln2.mc.common.events.Scheduler
import org.eln2.mc.common.events.schedulePost
import org.eln2.mc.data.AveragingList
import org.eln2.mc.utility.AnalyticsAcknowledgementsData
import java.io.IOException

data class LevelDataStorage(val map: HashMap<Any, Any>) {
    inline fun <K : Any, reified V : Any> read(key: K): V? {
        val result = map[key] ?: return null
        return result as? V ?: error("Invalid stored type $result for ${V::class}")
    }

    inline fun <K : Any, reified V : Any> remove(key: K): V? {
        val result = read<K, V>(key)

        if(result != null) {
            map.remove(key)
        }

        return result
    }

    inline fun <K : Any, reified V : Any> store(key: K, value: V): V? {
        val old = read<K, V>(key)
        map[key] = value
        return old
    }

    inline fun<reified K : Any, reified V : Any> storeNew(key: K, value: V) = require(store(key, value) == null) {
        "Existing value for ${K::class}"
    }
}

private val dataStorage = HashMap<LevelReader, LevelDataStorage>()

fun getLevelDataStorage(level: LevelReader) = dataStorage.getOrPut(level) {
    require(ServerLifecycleHooks.getCurrentServer().isSameThread) {
        "Cannot access LDS outside the server thread"
    }

    require(!level.isClientSide){
        "Cannot access LDS outside a server level"
    }

    LevelDataStorage(HashMap())
}

@Mod.EventBusSubscriber
object CommonEvents {
    private const val THIRTY_DAYS_AS_MILLISECONDS: Long = 2_592_000_000L
    private val upsAveragingList = AveragingList(100)
    private val tickTimeAveragingList = AveragingList(100)
    private var logCountdown = 0
    private const val logInterval = 100

    @SubscribeEvent
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

                LOGGER.info("Total simulation rate: ${upsAveragingList.calculate()} Updates/Second")
                LOGGER.info("Total simulation time: ${tickTimeAveragingList.calculate()}")
            }
        }
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        LOGGER.info("DESTROYING SIMULATION")

        event.server.allLevels.forEach {
            CellGraphManager.getFor(it).serverStop()
        }
    }

    @SubscribeEvent
    fun onBlockEvent(event: BlockEvent){
        if(event.level.isClientSide){
            return
        }

        schedulePost(1){
            GhostLightBlock.refreshGhost(event.level as ServerLevel, event.pos)
        }
    }
}
