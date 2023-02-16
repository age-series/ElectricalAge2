package org.eln2.mc.integration.waila

import mcp.mobius.waila.api.IPluginConfig

/**
 * Implemented by classes that want to export data to WAILA.
 * */
@FunctionalInterface
interface IWailaProvider {
    fun appendBody(builder: TooltipBuilder, config: IPluginConfig?)
}
