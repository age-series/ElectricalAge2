package org.eln2.mc.common

import kotlinx.serialization.Serializable
import java.util.*

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
