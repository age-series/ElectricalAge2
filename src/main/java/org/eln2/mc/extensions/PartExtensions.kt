package org.eln2.mc.extensions

import org.eln2.mc.common.parts.foundation.ConnectionMode
import org.eln2.mc.common.parts.foundation.IPartCellContainer

object PartExtensions {
    fun IPartCellContainer.allows(mode: ConnectionMode): Boolean {
        return when(mode) {
            ConnectionMode.Planar -> this.allowPlanarConnections
            ConnectionMode.Inner -> this.allowInnerConnections
            ConnectionMode.Wrapped -> this.allowWrappedConnections
        }
    }
}
