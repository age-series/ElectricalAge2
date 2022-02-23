package org.eln2.mc.client.screens

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraftforge.client.event.ContainerScreenEvent.DrawBackground
import net.minecraftforge.client.event.ContainerScreenEvent.DrawForeground
import net.minecraftforge.common.MinecraftForge
import org.eln2.mc.Eln2.MODID
import org.eln2.mc.common.containers.VoltageSourceCellContainer
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.clientToServer.SingleDoubleElementGuiUpdatePacket
import java.awt.Color

class VoltageSourceCellScreen(private val container: VoltageSourceCellContainer, inv: Inventory, name: Component) :
    AbstractContainerScreen<VoltageSourceCellContainer>(container, inv, name) {

    private val guiTexture: ResourceLocation = ResourceLocation(MODID, "textures/gui/voltage_source_gui.png")
    private val textureWidth = 320
    private val textureHeight = 128
    private val guiWidth = 256
    private val guiHeight = 128

    private var relX: Int = 0
    private var relY: Int = 0

    private val textboxW = 192
    private val textboxH = 16
    private val textboxX = 32
    private val textboxY = 48

    private val buttonW = 60
    private val buttonH = 18
    private val buttonX = 96
    private val buttonY = 128 - (buttonW / 2)

    lateinit var textbox: EditBox
    lateinit var saveButton: Button

    private var voltage = 0.0

    override fun init() {
        relX = (this.width - guiWidth) / 2
        relY = (this.height - guiHeight) / 2
        textbox = EditBox(this.font, relX + textboxX, relY + textboxY, textboxW, textboxH, TextComponent("unused"))
        textbox.value = voltage.toString()
        saveButton = Button(relX + buttonX, relY + buttonY, buttonW, buttonH, TranslatableComponent("gui.eln2.save")) {
            try {
                val voltage = textbox.value.toDouble()
                this.container.value = voltage
                Networking.sendToServer(SingleDoubleElementGuiUpdatePacket(voltage, container.pos))
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
        GuiComponent.blit(
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
        MinecraftForge.EVENT_BUS.post(DrawBackground(this, pPoseStack, pMouseX, pMouseY))

        if (container.value != voltage) {
            voltage = container.value
            textbox.value = voltage.toString()
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
        MinecraftForge.EVENT_BUS.post(DrawForeground(this, pPoseStack, pMouseX, pMouseY))

        this.renderTooltip(pPoseStack, pMouseX, pMouseY)
    }

}
