package org.eln2.mc.client.gui.library.controls

import com.mojang.blaze3d.vertex.PoseStack
import org.eln2.mc.client.gui.library.*
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.fillRect
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.text
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColors

class Button(
        parent : AbstractWindow,
        pos : Point2I,
        text : String,
        marginVertical : Int = 2,
        marginHorizontal : Int = 2,
        var color : McColor = McColors.cyan,
        var useBackground : Boolean = true,
        var useClickHighlight : Boolean = true,
        var clickHighlightColor : McColor = McColor(255, 100, 100, 100),
        var backgroundColor : McColor = McColor(20, 20, 20, 200),
        click : ((ClickEvent) -> Unit)
    ) : AbstractButton(parent, pos, text, marginVertical, marginHorizontal, click) {

    private val backgroundSurface get() =
        Rect4I(Point2I.zero, Point2I(2 * marginHorizontal + parent.font.width(text), 2 * marginVertical + parent.font.lineHeight))

    override val size: Point2I get() =
        backgroundSurface.size

    var drawHighlight = false

    override fun render(
        poseStack: PoseStack,
        translation: Point2F,
        mouse: Point2I,
        hovered: Boolean,
        partialTime: Float
    ) {
        if(useBackground){ poseStack.fillRect(backgroundSurface, backgroundColor) }
        if(drawHighlight){ poseStack.fillRect(backgroundSurface, clickHighlightColor) }
        if(text.isNotEmpty() && text.isNotBlank()){ poseStack.text(Point2I(marginHorizontal, marginVertical), text, color, parent.font) }
    }

    override fun onMouseClicked(mouse: Point2I, event: MouseInfo) {
        if(event.action == MouseAction.Click && useClickHighlight){
            drawHighlight = true
            return
        }

        if(backgroundSurface.scale(parent.scale).hasPoint(mouse.toF())){
            click(ClickEvent(event, mouse))
        }

        drawHighlight = false
    }
}
