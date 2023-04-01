package org.eln2.mc

//import io.prometheus.client.exporter.HTTPServer
//import io.prometheus.client.hotspot.DefaultExports
import org.eln2.mc.Eln2.LOGGER

object Metrics {
    /*private val server = HTTPServer
        .Builder()
        .withPort(3141)
        .build()
*/
    fun initialize() {
        //DefaultExports.initialize()

        //LOGGER.info("Initialized metrics server.")
    }

    fun destroy(){
        //server.close()
    }
}
