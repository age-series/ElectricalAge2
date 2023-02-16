package org.eln2.mc.extensions

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent

object MatrixStackExtensions : GuiComponent() {
    // todo: remove this nonsense

    fun PoseStack.rect4(x: Int, y: Int, width: Int, height: Int, color: Int) {
        hLine(this, x, x + width, y, color)
        hLine(this, x, x + width, y + height, color)
        vLine(this, x, y, y + height, color)
        vLine(this, x + width, y, y + height, color)
    }
}
