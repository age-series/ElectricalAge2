package org.eln2.utils

import net.minecraft.client.Minecraft
import net.minecraftforge.fml.loading.FMLEnvironment
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.eln2.Eln2

/*

If you're reading this class, you're probably wondering what data we collect, and what we do with it.

The eln2.org domain is currently registered under jrddunbr's name and an address in the United States of America.
The Eln2 team is more than willing to respect your data rights under GDPR and other privacy laws.
This includes knowing what data we have collected, as well as asking us to delete your data, if requested.
We will never share your data with anyone outside of the organization for any sort of financial or personal gain,
but we do occasionally share statistics with the community that have been anonymized to the best of our ability.

Generally speaking, this can be found in our Discord: https://discord.gg/YjK2JAD

We currently collect the following information:
* Dedicated Server - Whether the user is using the Minecraft client, or if it's a dedicated server
* Language - What language you are using in your game and system language
* Player name and UUID - Player name and UUID
* IP Address - your public IP address is collected by the server (not sent from the client)

We use this data to improve your game experience. Some examples of how we might present the data:
* We use statistics like whether it's a dedicated server or not to determine how many servers and clients we have.
* We make pie charts of the languages used and provide them to the devteam and the community to show interest in
different languages, and to determine if we need to find a way to provide better language support.
* We use the comparison between the system language and the game language to find potential gaps in language support
* We collect the player name and UUID purely for identification purposes. This is so that we can do the following:
    * Delete your data, if requested
    * Send you your data, if requested
* We collect the public IP addresses of your client for identifying spam
    * It is possible in the future that we may use this data at a country GeoIP level to improve language support

If you want a copy of your data, or for the data to be deleted, please send an email to jrddunbr at ja4 dot org.
We will ask that you provide verification of your in-game identity in one way or another (eg, connect to an online
server and then send a message to delete your data, to verify that you have authenticated against Mojang/Microsoft).

If you have any other questions/comments/concerns, don't hesitate to send me an email.

- jrddunbr

 */

object AnalyticsHandler {

    // Variable to see if we've run the analytics handler yet this launch.
    private var hasRun = false

    /**
     * sendAnalyticsData
     *
     * Sends data out to one of our servers.
     */
    fun sendAnalyticsData() {
        Eln2.LOGGER.debug("Trying to send data. Enable Analytics is currently ${GameConfig.enableAnalytics}")
        if(!GameConfig.enableAnalytics) hasRun = true
        if (!hasRun) {
            Eln2.LOGGER.debug("Preparing analytics thread")
            val thread = Thread {
                val analyticsServer = GameConfig.analyticsServer
                Eln2.LOGGER.debug("Analytics thread sees server as ${GameConfig.analyticsServer}")
                val statMap = mutableMapOf<String, Any>()
                statMap["isDedicatedServer"] = FMLEnvironment.dist.isDedicatedServer
                if (!Eln2.logicalServer) {
                    // Only valid on the client side
                    val mcLang = Minecraft.getInstance().languageManager.currentLanguage.javaLocale.language
                    val mcCountry = Minecraft.getInstance().languageManager.currentLanguage.javaLocale.country
                    statMap["mc_lang"] = "${mcLang}_$mcCountry"
                    statMap["player"] = Minecraft.getInstance().player?.name?.unformattedComponentText.toString()
                    statMap["uuid"] = Minecraft.getInstance().player?.uniqueID.toString()
                }
                val lang = System.getProperty("user.language")?: ""
                val country = System.getProperty("user.country")?: ""
                statMap["sys_lang"] = "${lang}_$country" // This is the same format Minecraft uses above, eg en_US
                Eln2.LOGGER.debug("Here's the status map:")
                Eln2.LOGGER.debug(statMap)
                sendRequest(analyticsServer, statMap)
            }
            Eln2.LOGGER.debug("Starting analytics thread")
            thread.start()
            hasRun = true
        }
    }

    /**
     * sendRequest Sends a HTTP GET request to a remote server for analytics purposes.
     *
     * @param url The URL to send to, including http(s), TLD, and path
     * @param data A map of data. If it's not a String, Int, Double, or Boolean, it gets dropped. Please do casts.
     */
    private fun sendRequest(url: String, data: Map<String, Any>) {
        try {
            val client = HttpClientBuilder.create().build()
            val dataList: List<String> = data.map {
                when (it.value) {
                    is String -> "${it.key}=\"${it.value}\""
                    is Int, Double, Boolean -> "${it.key}=${it.value}"
                    else -> ""
                }
            }.filter { it.isEmpty() }
            val requestData = "$url?${dataList.joinToString("&")}"
            val response = client.execute(HttpGet("$url?$requestData"))
            if (response.statusLine.statusCode != 200) {
                Eln2.LOGGER.warn("Unable to send statistical information.")
            }
        }catch (e: Exception) {
            Eln2.LOGGER.warn("Unable to send statistical information.")
        }
    }
}
