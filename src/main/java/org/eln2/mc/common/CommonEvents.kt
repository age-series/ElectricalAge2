package org.eln2.mc.common

import com.charleskorn.kaml.Yaml
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.LevelReader
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.server.ServerLifecycleHooks
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.blocks.foundation.GhostLightBlock
import org.eln2.mc.common.cells.foundation.CellGraphManager
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.data.AveragingList
import org.eln2.mc.sim.VoxelDDAThreadedRadiationSystem
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
    fun onEntityJoinedWorld(event: EntityJoinWorldEvent) {
        //Warn new users that we collect analytics
        if (event.world.isClientSide && event.entity is Player) {
            if (Eln2.config.enableAnalytics) {
                val acknowledgementFile =
                    FMLPaths.CONFIGDIR.get().resolve("ElectricalAge/acknowledgements.yml").toFile()
                if (!acknowledgementFile.exists()) {
                    try {
                        acknowledgementFile.parentFile.mkdirs()
                        acknowledgementFile.createNewFile()
                        acknowledgementFile.writeText(
                            Yaml.default.encodeToString(
                                AnalyticsAcknowledgementsData.serializer(),
                                AnalyticsAcknowledgementsData(mutableMapOf())
                            )
                        )
                    } catch (ex: Exception) {
                        when (ex) {
                            is IOException, is SecurityException -> {
                                LOGGER.warn("Unable to write default analytics acknowledgements config")
                                LOGGER.warn(ex.localizedMessage)
                            }

                            else -> throw ex
                        }
                    }
                }
                val uuid: String = event.entity.uuid.toString()
                val acknowledgements: AnalyticsAcknowledgementsData = try {
                    Yaml.default.decodeFromStream(
                        AnalyticsAcknowledgementsData.serializer(),
                        acknowledgementFile.inputStream()
                    )
                } catch (ex: IOException) {
                    AnalyticsAcknowledgementsData(mutableMapOf())
                }
                if (acknowledgements.entries.none { (k, _) -> k == uuid } || acknowledgements.entries[uuid]!! < (System.currentTimeMillis()) - THIRTY_DAYS_AS_MILLISECONDS) {
                    event.entity.sendMessage(
                        TranslatableComponent("misc.eln2.acknowledge_analytics").withStyle(
                            ChatFormatting.RED
                        ), Util.NIL_UUID
                    )
                    acknowledgements.entries[uuid] = System.currentTimeMillis()
                }

                try {
                    acknowledgementFile.writeText(
                        Yaml.default.encodeToString(
                            AnalyticsAcknowledgementsData.serializer(),
                            acknowledgements
                        )
                    )
                } catch (ex: Exception) {
                    when (ex) {
                        is IOException, is SecurityException -> {
                            LOGGER.warn("Unable to save analytics acknowledgements")
                            LOGGER.warn(ex.localizedMessage)

                            event.entity.sendMessage(
                                TranslatableComponent("misc.eln2.analytics_save_failure").withStyle(
                                    ChatFormatting.ITALIC
                                ), Util.NIL_UUID
                            )
                        }

                        else -> throw ex
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onBlockEvent(event: BlockEvent){
        if(event.world.isClientSide){
            return
        }

        EventScheduler.scheduleWorkPost(1){
            GhostLightBlock.refreshGhost(event.world as ServerLevel, event.pos)
        }
    }
}
