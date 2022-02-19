package org.eln2.mc.common.network

import kotlinx.serialization.Serializable
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import java.util.*
import kotlin.concurrent.thread

object ModStatistics {
    var sent = false

    private fun collectData(): AnalyticsData {
        try {
            return AnalyticsData(
                "",
                "electrical_age",
                "",
                "",
                Locale.getDefault().toLanguageTag()
            )
        } catch (e: Exception) {
            LOGGER.warn("Tried to get analytics and failed :/")
            error("Tried to get analytics and failed :/")
        }
    }

    fun sendAnalytics() {
        if (sent) { return }
        thread (start = true, isDaemon = true, name = "Electrical Age Analytics") {
            try {
                val data = collectData()
                val serialized = """
                    {
                       "uuid": "${data.uuid}",
                       "mod_id": "${data.mod_id}",
                       "avg_sim_performance": "${data.avg_sim_performance}",
                       "game_lang": "${data.game_lang}",
                       "os_lang": "${data.os_lang}"
                    }
                """.trimIndent()
                LOGGER.debug("Sending HTTPS Post request to :\n$")
                val httpClient = HttpClientBuilder.create().build()
                val request = HttpPost(Eln2.config.analyticsEndpoint)
                val params = StringEntity(serialized)
                request.addHeader("content-type", "application/json")
                request.entity = params
                val resp = httpClient.execute(request)
                if (resp.statusLine.statusCode != 200) {
                    LOGGER.warn(resp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                LOGGER.warn("Failed to send analytics information to server")
            }
        }
        sent = true
    }
}

@Serializable
data class AnalyticsData(
    var uuid: String = "",
    var mod_id: String = "electrical_age",
    var avg_sim_performance: String = "",
    var game_lang: String = "",
    var os_lang: String = Locale.getDefault().toLanguageTag()
)
