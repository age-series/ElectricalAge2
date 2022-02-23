package org.eln2.mc.client.screens

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraftforge.client.event.ContainerScreenEvent
import net.minecraftforge.common.MinecraftForge
import org.eln2.mc.Eln2
import org.eln2.mc.common.containers.ResistorCellContainer
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.clientToServer.SingleDoubleElementGuiUpdatePacket
import java.awt.Color

class ResistorCellScreen(private val container: ResistorCellContainer, inv: Inventory, name: Component) :
    AbstractContainerScreen<ResistorCellContainer>(container, inv, name) {

    companion object {
        private val guiTexture: ResourceLocation = ResourceLocation(Eln2.MODID, "textures/gui/resistor_gui.png")
        private const val textureWidth = 256
        private const val textureHeight = 128
        private const val guiWidth = 256
        private const val guiHeight = 128

        private const val textboxW = 192
        private const val textboxH = 16
        private const val textboxX = 32
        private const val textboxY = 48

        private const val buttonW = 60
        private const val buttonH = 18
        private const val buttonX = 96
        private const val buttonY = 128 - (buttonW / 2)
    }

    private var relX: Int = 0
    private var relY: Int = 0

    lateinit var textbox: EditBox
    lateinit var saveButton: Button

    private var resistance = 0.0

    override fun init() {
        relX = (this.width - guiWidth) / 2
        relY = (this.height - guiHeight) / 2
        textbox = EditBox(this.font, relX + textboxX, relY + textboxY, textboxW, textboxH, TextComponent("unused"))
        textbox.value = resistance.toString()
        saveButton = Button(relX + buttonX, relY + buttonY, buttonW, buttonH, TranslatableComponent("gui.eln2.save")) {
            try {
                val resistance = textbox.value.toDouble()
                this.container.value = resistance
                Networking.sendToServer(SingleDoubleElementGuiUpdatePacket(resistance, container.pos))
                this.onClose()
            } catch (ex: NumberFormatException) {
                textbox.setTextColor(Color.RED.rgb)
            }
        }

        addRenderableWidget(textbox)
        addRenderableWidget(saveButton)
    }

    override fun renderBg(pPoseStack: PoseStack, pPartialTick: Float, pMouseX: Int, pMouseY: Int) {
        RenderSystem.setShaderTexture(0, guiTexture)
        blit(
            pPoseStack,
            relX,
            relY,
            this.blitOffset,
            0.0f,
            0.0f,
            guiWidth,
            guiHeight,
            textureWidth,
            textureHeight
        )
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        this.renderBackground(pPoseStack)
        this.renderBg(pPoseStack, pPartialTick, pMouseX, pMouseY)
        MinecraftForge.EVENT_BUS.post(ContainerScreenEvent.DrawBackground(this, pPoseStack, pMouseX, pMouseY))

        if (container.value != resistance) {
            resistance = container.value
            textbox.value = resistance.toString()
        }

        //Render foreground
        for (widget in renderables) {
            widget.render(pPoseStack, pMouseX, pMouseY, pPartialTick)
        }

        //Vanilla gets a posestack from the render system rather than re-using the pPoseStack
        //So let's do the same to be safe
        val posestack = RenderSystem.getModelViewStack()
        posestack.pushPose()
        posestack.translate(relX.toDouble(), relY.toDouble(), 0.0)
        RenderSystem.applyModelViewMatrix()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        this.font.draw(pPoseStack, this.title, this.titleLabelX.toFloat(), this.titleLabelY.toFloat(), 4210752)
        posestack.popPose()
        RenderSystem.applyModelViewMatrix()
        RenderSystem.enableDepthTest()
        MinecraftForge.EVENT_BUS.post(ContainerScreenEvent.DrawForeground(this, pPoseStack, pMouseX, pMouseY))

        this.renderTooltip(pPoseStack, pMouseX, pMouseY)
    }
}
