package org.eln2.mc.client.overlays

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraftforge.client.gui.overlay.ForgeGui
import net.minecraftforge.client.gui.overlay.IGuiOverlay
import org.eln2.mc.ClientOnly
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.pickGameObject

/**
 * Implemented by game objects (block entities, parts, specs) that want to show a short, translated hint when the player shift-looks at them.
 * */
interface HoverDetailSupplier {
    fun getHoverDetail(side: Direction?): Component?
}

@ClientOnly
object HoverDetailOverlayClient : IGuiOverlay {
    private val BACKGROUND_COLOR = MyColor(180, 0, 0, 0).data
    private val BORDER_COLOR = MyColor(180, 60, 60, 60).data
    private val TEXT_COLOR = MyColor(255, 255, 255, 255).data

    private const val PADDING = 4
    private const val OFFSET_X = 8
    private const val OFFSET_Y = 8

    override fun render(
        gui: ForgeGui,
        guiGraphics: GuiGraphics,
        partialTick: Float,
        screenWidth: Int,
        screenHeight: Int,
    ) {
        val player = Minecraft.getInstance().player ?: return

        if(!player.isShiftKeyDown) {
            return
        }

        val pick = pickGameObject<HoverDetailSupplier>(player) ?: return

        val detail = pick.target.getHoverDetail(pick.side) ?: return

        val font = gui.font

        val textWidth = font.width(detail)
        val textHeight = font.lineHeight

        val centerX = screenWidth / 2
        val centerY = screenHeight / 2

        var x = centerX + OFFSET_X
        var y = centerY + OFFSET_Y

        val boxRight = x + textWidth + PADDING * 2
        if(boxRight > screenWidth) {
            x = centerX - OFFSET_X - textWidth - PADDING * 2
        }

        val boxBottom = y + textHeight + PADDING * 2
        if(boxBottom > screenHeight) {
            y = centerY - OFFSET_Y - textHeight - PADDING * 2
        }

        guiGraphics.fill(x, y, x + textWidth + PADDING * 2, y + textHeight + PADDING * 2, BACKGROUND_COLOR)
        guiGraphics.fill(x, y, x + textWidth + PADDING * 2, y + 1, BORDER_COLOR)
        guiGraphics.fill(x, y + textHeight + PADDING * 2 - 1, x + textWidth + PADDING * 2, y + textHeight + PADDING * 2, BORDER_COLOR)
        guiGraphics.fill(x, y, x + 1, y + textHeight + PADDING * 2, BORDER_COLOR)
        guiGraphics.fill(x + textWidth + PADDING * 2 - 1, y, x + textWidth + PADDING * 2, y + textHeight + PADDING * 2, BORDER_COLOR)

        guiGraphics.drawString(font, detail, x + PADDING, y + PADDING, TEXT_COLOR, false)
    }
}
