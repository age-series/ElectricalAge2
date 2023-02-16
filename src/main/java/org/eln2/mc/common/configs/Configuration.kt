package org.eln2.mc.common.configs

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import org.eln2.mc.Eln2.LOGGER
import java.io.File
import java.util.*

object Configuration {
    private val CONFIG_FILE: File = File("config/electrical_age.yaml")
    var config: ElectricalAgeConfiguration = ElectricalAgeConfiguration()

    fun loadConfig(configFile: File = CONFIG_FILE) {
        try {
            if (configFile.isFile) {
                LOGGER.info("[Electrical Age] Reading config from ${configFile.absoluteFile}")
                config = Yaml.default.decodeFromStream(ElectricalAgeConfiguration.serializer(), configFile.inputStream())
            } else {
                config = ElectricalAgeConfiguration()
                saveConfig()
            }
        } catch (e: Exception) {
            LOGGER.error("Electrical Age had an issue with loading the configuration file, please check the file for errors.")
            LOGGER.error("Check that 1) You have valid YAML 2) the config directives are spelled correctly (see documentation)")
        }
    }

    private fun saveConfig(configFile: File = CONFIG_FILE) {
        try {
            LOGGER.info("[Electrical Age] Writing config to ${configFile.absoluteFile}")
            val configText = Yaml.default.encodeToString(ElectricalAgeConfiguration.serializer(), config)
            if (!configFile.exists()) {
                configFile.createNewFile()
            }
            configFile.writeText(configText)
        } catch (e: Exception) {
            LOGGER.error("Electrical Age was unable to write the config file, please check filesystem permissions: $e")
        }
    }
}

/**
 * ElectricalAgeConfiguration
 *
 * NOTE: ALL FIELDS _MUST_ have default values when called, since the user will likely want a sane default!
 * They may be blank, but MUST be present. Thanks!
 */
@Serializable
data class ElectricalAgeConfiguration (
    var enableAnalytics: Boolean = true,
    // TODO: Replace with stats.age-series.org (but needs CAA certificates)
    var analyticsEndpoint: String = "https://ingz5drycg.execute-api.us-east-1.amazonaws.com/",
    var analyticsUuid: String = UUID.randomUUID().toString()
)
