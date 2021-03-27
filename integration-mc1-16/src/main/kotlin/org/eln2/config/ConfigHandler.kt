package org.eln2.config

class ConfigHandler {
    init {
        val serverConfigPath = "${System.getProperty("user.dir")}/config/eln2-server.json"
        val clientConfigPath = "${System.getProperty("user.dir")}/config/eln2-client.json"
    }
}
