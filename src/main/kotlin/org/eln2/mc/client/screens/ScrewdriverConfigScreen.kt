package org.eln2.mc.client.screens

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.eln2.mc.common.content.ScrewdriverItem
import org.eln2.mc.common.network.Networking

class ScrewdriverConfigScreen : Screen(TITLE) {
    private lateinit var editBox: EditBox

    override fun init() {
        super.init()

        val boxWidth = 160
        val boxX = width / 2 - boxWidth / 2
        val boxY = height / 2 - 10

        editBox = EditBox(font, boxX, boxY, boxWidth, 18, Component.translatable("item.eln2.screwdriver.config.value"))
        editBox.setFilter { it.isEmpty() || it.toDoubleOrNull() != null }

        val stack = Minecraft.getInstance().player!!.mainHandItem
        val existing = ScrewdriverItem.getConfigValue(stack)

        if(existing.isPresent) {
            editBox.value = "%.3f".format(existing.unwrap())
        }

        addRenderableWidget(editBox)

        val buttonWidth = 80
        val buttonX = width / 2 - buttonWidth / 2
        val buttonY = boxY + 24

        addRenderableWidget(
            Button.builder(BUTTON_TEXT) { _ ->
                val parsed = editBox.value.toDoubleOrNull()

                if(parsed != null) {
                    Networking.sendToServer(ScrewdriverItem.SetConfigValue(parsed))
                }

                onClose()
            }
            .bounds(buttonX, buttonY, buttonWidth, 20)
            .build()
        )
    }

    override fun render(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        renderBackground(pGuiGraphics)
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick)
    }

    override fun keyPressed(pKeyCode: Int, pScanCode: Int, pModifiers: Int): Boolean {
        if(pKeyCode == 257 || pKeyCode == 335) {
            val parsed = editBox.value.toDoubleOrNull()

            if(parsed != null) {
                Networking.sendToServer(ScrewdriverItem.SetConfigValue(parsed))
            }

            onClose()
            return true
        }

        return super.keyPressed(pKeyCode, pScanCode, pModifiers)
    }

    companion object {
        private val TITLE = Component.translatable("item.eln2.screwdriver.config.title")
        private val BUTTON_TEXT = Component.translatable("item.eln2.screwdriver.config.set")

        fun open() {
            Minecraft.getInstance().setScreen(ScrewdriverConfigScreen())
        }
    }
}
