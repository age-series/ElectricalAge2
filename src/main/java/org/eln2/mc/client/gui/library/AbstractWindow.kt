package org.eln2.mc.client.gui.library

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiComponent
import org.eln2.mc.client.gui.library.controls.AbstractControl
import java.util.*

abstract class AbstractWindow(var initialSurface : Rect4I,  val parent : Viewport) : GuiComponent() {
    val globalPos get() = initialSurface.pos
    val size get() = initialSurface.size
    val x get() = globalPos.x
    val y get() = globalPos.y
    val width get() = initialSurface.width
    val height get() = initialSurface.height
    val localSurface get() = Rect4I(Point2I.zero, size)

    var scale = 1f

    abstract val controls : LinkedList<AbstractControl>
    abstract val font : Font
    var focused : Boolean = false

    open fun onMouseMoved(mouse : Point2I, focused : Boolean){}
    open fun onMouseClicked(mouse: Point2I, event : MouseInfo){}
    open fun onMouseDragged(mouse: Point2I, button : MouseButton, delta : Point2F){}
    open fun onMouseScrolled(mouse: Point2I, delta: Double, focused: Boolean){}

    open fun getControlAt(mouse: Point2I) : AbstractControl?{
        controls.forEach{ control ->
            val localMouse = mouseRelativeTo(mouse, control)

            if(control.isMouseInsideControl(localMouse)){
                return control
            }
        }

        return null
    }


    open fun mouseRelativeTo(mouse : Point2I, control : AbstractControl) : Point2I{
        return mouse - control.pos.scale(parent.scale.toFloat()).toI()
    }

    open fun isMouseInsideWindow(mouse: Point2I) : Boolean{
        return initialSurface.hasPoint(mouse)
    }

    abstract fun addControl(control : AbstractControl)

    abstract fun render(poseStack: PoseStack, translation : Point2F, mouse: Point2I, hovered : Boolean, focused: Boolean, partialTime: Float)
}
