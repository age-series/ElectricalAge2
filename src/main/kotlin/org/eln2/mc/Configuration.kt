package org.eln2.mc

import java.io.File

object Configuration {
    private val CONFIG_FILE: File = File("config/electrical_age.yaml")

    var instance: ElnConfig = ElnConfig()

    /* fun loadConfig(configFile: File = CONFIG_FILE) {
         try {
             if (configFile.isFile) {
                 LOGGER.info("[Electrical Age] Reading config from ${configFile.absoluteFile}")
                 instance = Yaml.default.decodeFromStream(ElnConfig.serializer(), configFile.inputStream())
             } else {
                 instance = ElnConfig()
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
             val configText = Yaml.default.encodeToString(ElnConfig.serializer(), instance)
             if (!configFile.exists()) {
                 configFile.createNewFile()
             }
             configFile.writeText(configText)
         } catch (e: Exception) {
             LOGGER.error("Electrical Age was unable to write the config file, please check filesystem permissions: $e")
         }
     }*/
}

/**
 * ElectricalAgeConfiguration
 *
 * NOTE: ALL FIELDS _MUST_ have default values when called, since the user will likely want a sane default!
 * They may be blank, but MUST be present. Thanks!
 */
//@Serializable
data class ElnConfig(
    var simulationThreads: Int = 8,
)
