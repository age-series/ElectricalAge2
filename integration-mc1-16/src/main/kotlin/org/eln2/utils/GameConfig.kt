package org.eln2.utils

import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import org.eln2.Eln2

@Mod.EventBusSubscriber(modid = "eln2", bus = Mod.EventBusSubscriber.Bus.MOD)
object GameConfig {
    @JvmField
    var CLIENT: ClientConfig? = null
    @JvmField
    var CLIENT_SPEC: ForgeConfigSpec? = null
    var enableAnalytics = true
    var analyticsServer = "https://stat.eln2.org/analytics"

    @SubscribeEvent
    fun onModConfigEvent(configEvent: ModConfig.ModConfigEvent) {
        if (configEvent.config.spec === CLIENT_SPEC) {
            bakeConfig()
        }
    }

    private fun bakeConfig() {
        enableAnalytics = CLIENT!!.enableAnalytics.get()
        analyticsServer = CLIENT!!.analyticsServer.get()
    }

    class ClientConfig(builder: ForgeConfigSpec.Builder) {
        val enableAnalytics: ForgeConfigSpec.BooleanValue
        val analyticsServer: ForgeConfigSpec.ConfigValue<String>

        init {
            builder.push("analytics")
            enableAnalytics = builder
                .comment("Enable Analytics")
                .translation("${Eln2.MODID}.config.enableAnalytics")
                .define("enable", true)
            analyticsServer = builder
                .comment("Analytics Server")
                .translation("${Eln2.MODID}.config.analyticsServer")
                .define("endpoint", "https://stat.eln2.org/analytics")
            builder.pop()
        }
    }

    init {
        val specPair = ForgeConfigSpec.Builder().configure { builder: ForgeConfigSpec.Builder -> ClientConfig(builder) }
        CLIENT_SPEC = specPair.right
        CLIENT = specPair.left
    }
}

