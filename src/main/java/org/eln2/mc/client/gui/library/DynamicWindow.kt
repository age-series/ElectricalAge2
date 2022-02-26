package org.eln2.mc.client.gui.library

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import org.eln2.mc.client.gui.library.controls.AbstractControl
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.withPose
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.translate
import org.eln2.mc.Eln2.LOGGER
import java.util.*
import javax.swing.text.View

open class DynamicWindow(initialSurface : Rect4I, parent : Viewport) : AbstractWindow(initialSurface, parent){
    override val font: Font get() = parent.mcFont
    private val controlCollection = LinkedList<AbstractControl>()
    override val controls: LinkedList<AbstractControl>
        get() = controlCollection

    private var clickingControl : AbstractControl? = null

    override fun render(poseStack: PoseStack, translation : Point2F, mouse: Point2I, hovered: Boolean, focused: Boolean, partialTime: Float) {
        controlCollection.forEach{ ctrl ->
            poseStack.withPose {
                val location = ctrl.pos.toF()
                poseStack.translate(location)
                val localMousePos = mouseRelativeTo(mouse, ctrl)
                ctrl.render(poseStack, translation + location, localMousePos, ctrl.isMouseInsideControl(localMousePos), partialTime)
            }
        }
    }

    override fun onMouseClicked(mouse: Point2I, event: MouseInfo) {
        if(event.action == MouseAction.Release){
            if(clickingControl == null){
                return
            }

            clickingControl!!.onMouseClicked(mouseRelativeTo(mouse, clickingControl!!), event)
            clickingControl = null

            return
        }

        clickingControl = getControlAt(mouse) ?: return

        val localMouse = mouseRelativeTo(mouse, clickingControl!!)
        clickingControl!!.onMouseClicked(localMouse, event)
    }

    override fun onMouseDragged(mouse: Point2I, button: MouseButton, delta: Point2F) {
        if(clickingControl == null){
            LOGGER.info("No control is being dragged!")
            return
        }

        clickingControl!!.onMouseDragged(mouseRelativeTo(mouse, clickingControl!!), button, delta)
    }

    override fun addControl(control: AbstractControl) {
        controlCollection.add(control)
    }
}
