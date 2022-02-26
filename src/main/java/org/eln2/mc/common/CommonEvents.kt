package org.eln2.mc.common

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.Util
import net.minecraft.ChatFormatting
import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLConfig
import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.server.ServerLifecycleHooks
import org.eln2.mc.Eln2
import org.eln2.mc.utility.AnalyticsAcknowledgementsData
import org.eln2.mc.common.cell.CellGraphManager
import java.io.File
import kotlin.io.path.Path
import com.charleskorn.kaml.Yaml

@Mod.EventBusSubscriber
object CommonEvents {

    const val THIRTY_DAYS: Long = 2_592_000L

    @SubscribeEvent
    fun onServerTick(event : TickEvent.ServerTickEvent){
        if(event.phase == TickEvent.Phase.END){
            ServerLifecycleHooks.getCurrentServer().allLevels.forEach{
                CellGraphManager.getFor(it).tickAll()
            }
        }
    }

    @SubscribeEvent
    fun onChat(event : ServerChatEvent){
        when (event.message) {
            "build" -> {
                CellGraphManager.getFor(event.player.level as ServerLevel).graphs.values.forEach{ it.build() }
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
                println("Checking for file $acknowledgementFile")
                if (!acknowledgementFile.exists()) {
                    acknowledgementFile.parentFile.mkdirs()
                    acknowledgementFile.createNewFile()
                    acknowledgementFile.writeText(Yaml.default.encodeToString(AnalyticsAcknowledgementsData.serializer(), AnalyticsAcknowledgementsData(mutableMapOf())))
                }
                val uuid: String = event.entity.uuid.toString()
                val acknowledgements: AnalyticsAcknowledgementsData = Yaml.default.decodeFromStream(AnalyticsAcknowledgementsData.serializer(), acknowledgementFile.inputStream())
                if (acknowledgements.entries.none { (k, _) -> k == uuid } || acknowledgements.entries[uuid]!! < (System.currentTimeMillis() / 1000L) - THIRTY_DAYS) {
                    event.entity.sendMessage(TranslatableComponent("misc.eln2.acknowledge_analytics").withStyle(ChatFormatting.RED), Util.NIL_UUID)
                    acknowledgements.entries[uuid] = System.currentTimeMillis() / 1000L
                }

                acknowledgementFile.writeText(Yaml.default.encodeToString(AnalyticsAcknowledgementsData.serializer(), acknowledgements))
            }
        }
    }
}
