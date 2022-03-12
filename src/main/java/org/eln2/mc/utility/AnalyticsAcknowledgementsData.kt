package org.eln2.mc.utility

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsAcknowledgementsData (
    val entries: MutableMap<String, Long>
)
