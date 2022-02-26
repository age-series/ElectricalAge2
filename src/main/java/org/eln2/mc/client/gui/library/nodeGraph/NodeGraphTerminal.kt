package org.eln2.mc.client.gui.library.nodeGraph

import com.mojang.blaze3d.vertex.PoseStack
import org.eln2.mc.Eln2
import org.eln2.mc.client.gui.library.*
import org.eln2.mc.client.gui.library.controls.AbstractControl
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.fillRect
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.hLine
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.vLine
import org.eln2.mc.utility.McColors
import java.util.*

class NodeTerminal(
        parent : AbstractWindow,
        pos : Point2I,
        var nodeSize : Point2I = Point2I(8, 8),
        val type : TerminalType
    ) : AbstractControl(parent, pos){

    override val size: Point2I
        get() = nodeSize

    private var isDragging = false

    var outgoingConnections = LinkedList<NodeConnection>()

    override fun render(
        poseStack: PoseStack,
        translation: Point2F,
        mouse: Point2I,
        hovered: Boolean,
        partialTime: Float
    ) {
        poseStack.fillRect(surface, McColors.red)

        if(outgoingConnections.isNotEmpty()){
            drawConnections(poseStack)
        }

        if(isDragging){
            drawConnectorTo(poseStack, (mouse / parent.parent.scale.toFloat()).toI())
        }
    }

    private fun drawConnections(poseStack: PoseStack){
        outgoingConnections.forEach{conn ->
            val targetPos = conn.localizedTargetPoint
            drawConnectorTo(poseStack, targetPos)
        }
    }

    override fun onMouseClicked(mouse: Point2I, event: MouseInfo) {
        if(event.action == MouseAction.Click){
            if(outgoingConnections.isNotEmpty() && type.connectionType == ConnectionType.Input){ return }
            isDragging = true
            return
        }

        if(outgoingConnections.isNotEmpty() && type.connectionType == ConnectionType.Input){
            outgoingConnections.clear()
            return
        }

        isDragging = false
        tryConnectTo(mouse)
    }

    private fun drawConnectorTo(poseStack: PoseStack, targetPos : Point2I){
        val root = Point2I(size.x, size.y / 2) // start in the middle of this visual surface
        val target = Point2I(targetPos.x, targetPos.y + size.y / 2)
        val distance = target - root
        val halfW = distance.x / 2
        poseStack.hLine(root, halfW, McColors.white)
        poseStack.hLine(Point2I(root.x + halfW, target.y), halfW, McColors.white)
        poseStack.vLine(Point2I(root.x + halfW, root.y), distance.y, McColors.white)
    }

    private fun tryConnectTo(mouse: Point2I){
        val ourWin = parent
        val viewport = ourWin.parent

        val globalMouse = viewport.lastMouse
        Eln2.LOGGER.info("global mm: $globalMouse")

        val remoteWinInfo = viewport.getHoveredWindow(globalMouse)

        if(remoteWinInfo == null){
            Eln2.LOGGER.info("window not found!")
            return
        }

        if(remoteWinInfo.window !is NodeGraphWindow){
            Eln2.LOGGER.info("window is not node graph window!")
            return
        }

        val remoteWin = remoteWinInfo.window

        val mouseRelativeToWin = viewport.mouseRelativeTo(globalMouse, remoteWin)

        Eln2.LOGGER.info("Relative to remote win: $mouseRelativeToWin")

        val remoteControl = remoteWin.getControlAt(mouseRelativeToWin)

        if(remoteControl !is NodeTerminal){
            Eln2.LOGGER.info("It is not a node terminal!")
            return
        }

        Eln2.LOGGER.info("the node terminal was identified successfully.")

        val connection = NodeConnection(type, this, remoteControl)
        outgoingConnections.add(connection)
    }
}
