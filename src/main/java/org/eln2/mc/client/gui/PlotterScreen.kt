package org.eln2.mc.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.TextComponent
import net.minecraft.resources.ResourceLocation
import org.apache.logging.log4j.LogManager
import org.eln2.mc.Eln2
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.extensions.ByteBufferExtensions.readString
import org.eln2.mc.extensions.ByteBufferExtensions.writeString
import kotlin.math.max

class CellInfo(val type : String, val info : String, val pos : BlockPos){
    constructor(buffer : FriendlyByteBuf) : this(buffer.readString(), buffer.readString(), buffer.readBlockPos())

    fun serialize(buffer : FriendlyByteBuf){
        buffer.writeString(type)
        buffer.writeString(info)
        buffer.writeBlockPos(pos)
    }
}

class PlotterScreen(private val cells : ArrayList<CellInfo>) : Screen(TextComponent("Plotter")) {
    private var scale = 1.0f
    private var posX = 0.0
    private var posY = 0.0

    override fun init() {
        prepare()
    }

    private val blueprintTex = ResourceLocation(Eln2.MODID, "textures/gui/blueprint_tile.png")

    private fun renderBlueprintBackground(poseStack: PoseStack) {
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderTexture(0, blueprintTex)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)

        val texSize = 144
        val targetSize = 144 / 4

        poseStack.pushPose()
        val scale = targetSize / texSize.toFloat()
        val multiplier = texSize / targetSize

        poseStack.scale(scale, scale, scale)
        for(x in 0..width * multiplier step targetSize){
            for(y in 0..height * multiplier step targetSize){
                blit(poseStack, x, y, 0f, 0f, texSize, texSize, texSize, texSize)
            }
        }


        poseStack.popPose()
    }

    private fun renderHeader(poseStack: PoseStack){
        drawCenteredString(poseStack, font, "Circuit Explorer",width / 2 , 1, 0xFFFFFF)
    }

    private val texSize = 32

    private val cellInfoCollection = ArrayList<CellInfo>()

    private fun prepare() {
        val minX = cells.minOf { it.pos.x }
        val minY = cells.minOf { it.pos.z }
        val offX = if(minX < 0) kotlin.math.abs(minX) else -minX
        val offY = if(minY < 0) kotlin.math.abs(minY) else -minY

        cells.forEach{ cellInfo ->

            val baseX = cellInfo.pos.x + offX
            val baseY = cellInfo.pos.z + offY

            val x = baseX * texSize + width / 100
            val y = baseY * texSize + width / 100

            cellInfoCollection.add(CellInfo(cellInfo.type, cellInfo.info, BlockPos(x, y, 0)))
        }
    }

    private fun drawInfoToolTip(poseStack : PoseStack, x : Int, y : Int, info : String){
        drawCenteredString(poseStack, font, info, x, y, 0xFFFFFF)
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        renderBlueprintBackground(pPoseStack)

        RenderSystem.setShader { GameRenderer.getPositionTexShader() }

        pPoseStack.pushPose()

        pPoseStack.scale(scale, scale, scale)
        pPoseStack.translate(posX, posY, 1.0)

        var toolTipCell : CellInfo? = null
        var latestTypeTex = ""

        cellInfoCollection.forEach {
            if(it.type != latestTypeTex){
                val texLocation = when(it.type){
                    CellRegistry.GROUND_CELL.id!!.toString() -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/ground.png")
                    CellRegistry.RESISTOR_CELL.id!!.toString() -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/resistor.png")
                    CellRegistry.VOLTAGE_SOURCE_CELL.id!!.toString() -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/voltage_source.png")
                    else -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/wire.png")
                }

                latestTypeTex = it.type

                RenderSystem.setShaderTexture(0, texLocation)
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
            }

            blit(pPoseStack, it.pos.x, it.pos.y, 0f, 0f, 32, 32, 32, 32)

            val xMin = (it.pos.x + posX) * scale
            val yMin = (it.pos.y + posY) * scale
            val xMax = scale * texSize.toFloat() + xMin
            val yMax = scale * texSize.toFloat() + yMin
            if(pMouseY > yMin && pMouseY < yMax && pMouseX > xMin && pMouseX < xMax){
                toolTipCell = it
                return@forEach
            }
        }

        pPoseStack.popPose()

        if(toolTipCell!=null)
        {
            drawInfoToolTip(pPoseStack,
                pMouseX, pMouseY, toolTipCell!!.info)
        }

        GuiComponent.drawString(pPoseStack, font, "Cells: ${cells.count()} selected: ${toolTipCell?.type?.split('/')?.last() ?: "none"}", 5, height - 10, 0xFF0000)
        renderHeader(pPoseStack)
    }

    override fun keyPressed(pKeyCode: Int, pScanCode: Int, pModifiers: Int): Boolean {
        LogManager.getLogger().info("keycode: $blueprintTex scan: $pScanCode mod: $pModifiers")

        return super.keyPressed(pKeyCode, pScanCode, pModifiers)
    }

    override fun mouseScrolled(pMouseX: Double, pMouseY: Double, pDelta: Double): Boolean {
        if(pDelta < 0){
            scale = max(0.2f, scale + pDelta.toFloat() / 20f)
        }
        else {
            scale += pDelta.toFloat() / 20f
        }

        return super.mouseScrolled(pMouseX, pMouseY, pDelta)
    }

    override fun mouseDragged(pMouseX: Double, pMouseY: Double, pButton: Int, pDragX: Double, pDragY: Double): Boolean {
        posX += pDragX
        posY += pDragY

        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY)
    }
}
