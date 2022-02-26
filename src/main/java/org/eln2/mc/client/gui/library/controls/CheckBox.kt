package org.eln2.mc.client.gui.library.controls

import com.mojang.blaze3d.vertex.PoseStack
import org.eln2.mc.client.gui.library.*
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.fillRect
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.rect
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.text
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColors

class CheckBox(
        parent : AbstractWindow,
        pos : Point2I,
        text : String,
        var textColor : McColor = McColors.white,
        var boxSize : Int = parent.font.lineHeight,
        var margin : Int = 2,
        checked : Boolean = false,
        var useBorder : Boolean = true,
        var borderColor : McColor = McColor(86, 86, 86),
        var fillColor : McColor = McColor(255, 255, 100)
    ) : AbstractCheckBox(parent, pos, text, checked) {
    override val size: Point2I
        get() = Point2I(boxSize, boxSize)

    override fun render(
        poseStack: PoseStack,
        translation: Point2F,
        mouse: Point2I,
        hovered: Boolean,
        partialTime: Float
    ) {
        if(useBorder){ poseStack.rect(Rect4I(Point2I.zero, size), borderColor) }
        if(checked){ poseStack.fillRect(Rect4I(Point2I(1, 1), size - Point2I(1,1)), fillColor) }
        if(text.isNotBlank()) poseStack.text(Point2I(boxSize + margin, 1), text, textColor, parent.font)
    }

    override fun onMouseClicked(mouse: Point2I, event: MouseInfo) {
        if(event.action != MouseAction.Release){
            return
        }

        checked = !checked
    }
}
