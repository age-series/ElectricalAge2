package org.eln2.mc.client.gui.library.extensions

import net.minecraft.client.gui.GuiComponent

object GuiComponentExtensions {
    fun GuiComponent.useOffset(offset : Int, body : (() -> Unit)) : GuiComponent{
        val oldOffset = this.blitOffset
        this.blitOffset = offset
        body()
        this.blitOffset = oldOffset
        return this
    }
}
