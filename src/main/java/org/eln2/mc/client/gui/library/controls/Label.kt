package org.eln2.mc.client.gui.library.controls

import com.mojang.blaze3d.vertex.PoseStack
import org.eln2.mc.client.gui.library.AbstractWindow
import org.eln2.mc.client.gui.library.Point2F
import org.eln2.mc.client.gui.library.Point2I
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.centeredText
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.text
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColors

class Label(
        parent : AbstractWindow,
        pos : Point2I,
        text : String,
        color : McColor = McColors.cyan,
        mode : LabelAlignMode = LabelAlignMode.FromLeft
    ) : AbstractLabel(parent, pos, text, color, mode) {

    override val size: Point2I
        get() = Point2I(parent.font.width(text), parent.font.lineHeight)

    override fun render(
        poseStack: PoseStack,
        translation: Point2F,
        mouse: Point2I,
        hovered: Boolean,
        partialTime: Float
    ) {
        if(mode == LabelAlignMode.FromCenter){
            poseStack.centeredText(pos, text, color, parent.font)
        }
        else{
            poseStack.text(Point2I.zero, text, color, parent.font)
        }
    }
}
