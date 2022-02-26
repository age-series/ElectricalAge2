package org.eln2.mc.client.gui.library.nodeGraph

class NodeConnection(val type : TerminalType, val from : NodeTerminal, val to : NodeTerminal) {
    val localizedTargetPoint get() = ((to.parent.globalPos + to.pos) - (from.parent.globalPos + from.pos))
}
