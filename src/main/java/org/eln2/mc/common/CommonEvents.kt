package org.eln2.mc.common

import com.charleskorn.kaml.Yaml
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.server.ServerLifecycleHooks
import org.eln2.mc.Eln2
import org.eln2.mc.common.cell.CellGraphManager
import org.eln2.mc.utility.AnalyticsAcknowledgementsData
import java.io.IOException

@Mod.EventBusSubscriber
object CommonEvents {
    private const val THIRTY_DAYS_AS_MILLISECONDS: Long = 2_592_000_000L

    @SubscribeEvent
    fun onServerTick(event : TickEvent.ServerTickEvent){
        if(event.phase == TickEvent.Phase.START){
            ServerLifecycleHooks.getCurrentServer().allLevels.forEach{
                CellGraphManager.getFor(it).beginUpdate()
            }
        }
        else if(event.phase == TickEvent.Phase.END){
            ServerLifecycleHooks.getCurrentServer().allLevels.forEach{
                CellGraphManager.getFor(it).endUpdate()
            }
        }
    }

    @SubscribeEvent
    fun onChat(event : ServerChatEvent){
        when (event.message) {
            "build" -> {
                CellGraphManager.getFor(event.player.level as ServerLevel).graphs.values.forEach{ it.buildSolver() }
            }
            "circuits" -> {
                CellGraphManager.getFor(event.player.level as ServerLevel).graphs.values.forEach {
                    Eln2.LOGGER.info("Circuit:")
                    Eln2.LOGGER.info(
                        it.circuit.components.map{
                                comp ->  "\n    ${comp.detail()}${comp.pins.map { pin -> pin.node?.index}}"
                        }
                    )
                }
            }
        }
    }

    @SubscribeEvent
    fun onEntityJoinedWorld(event: EntityJoinWorldEvent) {
        //Warn new users that we collect analytics
        if (event.world.isClientSide && event.entity is Player) {
            if (Eln2.config.enableAnalytics)  {
                val acknowledgementFile = FMLPaths.CONFIGDIR.get().resolve("ElectricalAge/acknowledgements.yml").toFile()
                if (!acknowledgementFile.exists()) {
                    try {
                        acknowledgementFile.parentFile.mkdirs()
                        acknowledgementFile.createNewFile()
                        acknowledgementFile.writeText(Yaml.default.encodeToString(AnalyticsAcknowledgementsData.serializer(), AnalyticsAcknowledgementsData(mutableMapOf())))
                    } catch (ex: Exception) {
                        when (ex) {
                            is IOException, is SecurityException -> {
                                Eln2.LOGGER.warn("Unable to write default analytics acknowledgements config")
                                Eln2.LOGGER.warn(ex.localizedMessage)
                            }
                            else -> throw ex
                        }
                    }
                }
                val uuid: String = event.entity.uuid.toString()
                val acknowledgements: AnalyticsAcknowledgementsData = try {
                    Yaml.default.decodeFromStream(AnalyticsAcknowledgementsData.serializer(), acknowledgementFile.inputStream())
                } catch (ex: IOException) {
                    AnalyticsAcknowledgementsData(mutableMapOf())
                }
                if (acknowledgements.entries.none { (k, _) -> k == uuid } || acknowledgements.entries[uuid]!! < (System.currentTimeMillis()) - THIRTY_DAYS_AS_MILLISECONDS) {
                    event.entity.sendMessage(TranslatableComponent("misc.eln2.acknowledge_analytics").withStyle(ChatFormatting.RED), Util.NIL_UUID)
                    acknowledgements.entries[uuid] = System.currentTimeMillis()
                }

                try {
                    acknowledgementFile.writeText(Yaml.default.encodeToString(AnalyticsAcknowledgementsData.serializer(), acknowledgements))
                } catch (ex: Exception) {
                    when (ex) {
                        is IOException, is SecurityException -> {
                            Eln2.LOGGER.warn("Unable to save analytics acknowledgements")
                            Eln2.LOGGER.warn(ex.localizedMessage)

                            event.entity.sendMessage(TranslatableComponent("misc.eln2.analytics_save_failure").withStyle(ChatFormatting.ITALIC), Util.NIL_UUID)
                        }
                        else -> throw ex
                    }
                }
            }
        }
    }
}
