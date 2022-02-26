package org.eln2.mc.client.gui.library.controls

import com.mojang.blaze3d.vertex.PoseStack
import org.eln2.mc.client.gui.library.*
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.fillRect
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.rect
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.text
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColors

class ControlSkin(
    parent: AbstractWindow,
    pos: Point2I = Point2I.zero,

    // form background
    var withClient : Boolean = true,
    var clientColor: McColor = McColor(39, 39, 39),

    // title panel
    var withHeader : Boolean = true,
    var headerHeight: Int = 20,
    var headerColor: McColor = McColor(35, 29, 41),
    var title : String,
    var titlePos : Point2I = Point2I(5, 6),
    var titleColor: McColor = McColors.white,

    // borders
    var withWindowBorder : Boolean = true,
    var windowBorderColor : McColor = McColor(87, 87, 87),
    var withHeaderBorder : Boolean = true,
    var headerBorderColor : McColor = McColor(87, 87, 87),
    var withClientBorder : Boolean = true,
    var clientBorderColor: McColor = McColor(87, 87, 87)) : AbstractControl(parent, pos) {

    // header collision
    override val size: Point2I get() = Point2I(parent.width, headerHeight)

    override fun render(poseStack: PoseStack, translation : Point2F, mouse: Point2I, hovered: Boolean, partialTime: Float) {
        val headerArea = Rect4I(Point2I.zero, size)

        if(withHeader){
            poseStack.fillRect(headerArea, headerColor)
            poseStack.text(titlePos, title, titleColor, parent.font)
        }

        if(withHeaderBorder){ poseStack.rect(headerArea, headerBorderColor) }

        val clientArea = Rect4I(0, headerHeight, size.x, parent.height - headerHeight)

        if(withClient){ poseStack.fillRect(clientArea, clientColor) }

        if(withClientBorder){ poseStack.rect(clientArea, clientBorderColor) }

        if(withWindowBorder){ poseStack.rect(pos, parent.size, windowBorderColor) }
    }

    private var posF = parent.globalPos.toF()

    override fun onMouseDragged(mouse: Point2I, button: MouseButton, delta: Point2F) {
        if(button != MouseButton.left){ return }
        posF += delta
        parent.initialSurface = Rect4I(posF.toI(), parent.size)
    }
}
