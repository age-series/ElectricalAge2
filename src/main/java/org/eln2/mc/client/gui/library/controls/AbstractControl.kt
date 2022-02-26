package org.eln2.mc.client.gui.library.controls

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent
import org.eln2.mc.client.gui.library.*

abstract class AbstractControl(val parent : AbstractWindow, var pos : Point2I) : GuiComponent() {
    abstract val size : Point2I
    open val surface get() = Rect4I(size)
    open val sizeScaled get() = size.scale(parent.scale)
    open val surfaceScaled get() = surface.scale(parent.scale)

    abstract fun render(poseStack: PoseStack, translation : Point2F, mouse: Point2I, hovered: Boolean, partialTime: Float)

    open fun isMouseInsideControl(point2I: Point2I) : Boolean{
        return surface.scale(parent.scale).hasPoint(point2I.toF())
    }

    open fun onMouseClicked(mouse: Point2I, event : MouseInfo){}
    open fun onMouseDragged(mouse: Point2I, button : MouseButton, delta : Point2F){}
}
