package org.eln2.mc.client.gui.library.nodeGraph

import org.eln2.mc.utility.McColor

enum class ConnectionType {
    Input,
    Output
}

class TerminalType(val color : McColor, val typeId : String, val connectionType: ConnectionType)

