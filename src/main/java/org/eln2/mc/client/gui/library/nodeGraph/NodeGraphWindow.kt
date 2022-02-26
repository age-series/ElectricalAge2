package org.eln2.mc.client.gui.library.nodeGraph

import com.mojang.blaze3d.vertex.PoseStack
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.client.gui.library.*
import org.eln2.mc.client.gui.library.controls.AbstractControl
import org.eln2.mc.client.gui.library.controls.ControlSkin
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.fillRect
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.hLine
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.vLine
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColors

class NodeGraphWindow(initialSurface : Rect4I, parent : Viewport) : DynamicWindow(initialSurface, parent) {

    val leftNodes = ArrayList<NodeTerminal>()
    val rightNodes = ArrayList<NodeTerminal>()

    fun addLeftNode() {
        addNode(leftNodes, 0, NodeTerminal(this, Point2I.zero, type = TerminalType(McColors.white, "a", ConnectionType.Input)))
    }

    fun addRight() {
        addNode(rightNodes, width - 4, NodeTerminal(this, Point2I.zero,  type = TerminalType(McColors.white, "a", ConnectionType.Output)))
    }

    private fun addNode(collection : ArrayList<NodeTerminal>, x : Int, terminal : NodeTerminal){
        collection.add(terminal)
        controls.add(terminal)
        val count = collection.count()
        val margin = 2
        val distance = (height - count * margin) / (count + 1)

        collection.forEachIndexed { index, node ->
            val y = (index + 1) * distance
            node.pos = Point2I(x, y)
        }
    }

    init {
        addControl(ControlSkin(this, title = "Node"))
        addLeftNode()
        addRight()
        addRight()
    }

}
