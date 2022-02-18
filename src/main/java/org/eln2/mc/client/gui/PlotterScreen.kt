package org.eln2.mc.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.resources.language.I18n.get
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.extensions.ByteBufferExtensions.readDataLabelBuilder
import org.eln2.mc.extensions.ByteBufferExtensions.readString
import org.eln2.mc.extensions.ByteBufferExtensions.writeDataLabelBuilder
import org.eln2.mc.extensions.ByteBufferExtensions.writeString
import org.eln2.mc.extensions.ColoredStringFormatterExtensions.contentOf
import org.eln2.mc.extensions.ColoredStringFormatterExtensions.getColorsOrDefault
import org.eln2.mc.utility.ColoredStringFormatter
import org.eln2.mc.extensions.MatrixStackExtensions.rect4
import org.eln2.mc.utility.*
import kotlin.math.max

class CellInfo(val type : String, val label : DataBuilder, val pos : BlockPos){
    constructor(buffer : FriendlyByteBuf) :
        this(buffer.readString(), buffer.readDataLabelBuilder(), buffer.readBlockPos())

    fun serialize(buffer : FriendlyByteBuf){
        buffer.writeString(type)
        buffer.writeDataLabelBuilder(label)
        buffer.writeBlockPos(pos)
    }
}

class PlotterScreen(private val cells : ArrayList<CellInfo>, val solveTime : Long) : Screen(TextComponent("Plotter")) {
    private var scale = 1.0f
    private var posX = 0.0
    private var posY = 0.0

    override fun init() {
        prepareCells()
    }

    private fun renderGridBackground(poseStack: PoseStack){
        fill(poseStack, 0, 0, width, height, McColor(43u, 43u, 43u).value)

        val size = (texSize * scale).toInt()

        val color = McColor(60u, 60u, 60u).value

        val cornerX = ((posX * scale) % size).toInt()
        val cornerY = ((posY * scale) % size).toInt()

        for(x in cornerX - size..width step size) vLine(poseStack, x, 0, height, color)
        for(y in cornerY - size..width step size) hLine(poseStack, 0, width, y, color)
    }

    // the size of the cell icon textures
    private val texSize = 32

    private val cellInfoCollection = ArrayList<CellInfo>()

    private fun prepareCells() {
        val minX = cells.minOf { it.pos.x }
        val minY = cells.minOf { it.pos.z }
        val offX = if(minX < 0) kotlin.math.abs(minX) else -minX
        val offY = if(minY < 0) kotlin.math.abs(minY) else -minY

        cells.forEach{ cellInfo ->

            val baseX = cellInfo.pos.x + offX
            val baseY = cellInfo.pos.z + offY

            val x = baseX * texSize + width / 100
            val y = baseY * texSize + width / 100

            cellInfoCollection.add(CellInfo(cellInfo.type, cellInfo.label, BlockPos(x, y, 0)))
        }

        // perf: less texture switches
        cellInfoCollection.sortBy { it.type }
    }

    private fun blockFor(id : String) : Block{
        return try {
            BlockRegistry.BLOCK_REGISTRY.entries.first {
                it.id.path == id.split(':')[1]
            }.get()
        } catch (e: Exception) {
            throw Exception("Fatal error! Mismatched block ID and cell ID!")
        }
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        renderGridBackground(pPoseStack)

        pPoseStack.pushPose()

        pPoseStack.scale(scale, scale, scale)
        pPoseStack.translate(posX, posY, 1.0)

        var toolTipCell : CellInfo? = null
        var latestTypeTex = ""

        cellInfoCollection.forEach {
            val xMin = (it.pos.x + posX) * scale
            val yMin = (it.pos.y + posY) * scale
            val xMax = scale * texSize.toFloat() + xMin
            val yMax = scale * texSize.toFloat() + yMin

            // perf: culling
            if(xMax < 0 || yMax < 0 || xMin > width || yMin > height){
                return@forEach
            }

            if(it.type != latestTypeTex){
                val texLocation = when(it.type){
                    CellRegistry.GROUND_CELL.id!!.toString() -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/ground.png")
                    CellRegistry.RESISTOR_CELL.id!!.toString() -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/resistor.png")
                    CellRegistry.VOLTAGE_SOURCE_CELL.id!!.toString() -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/voltage_source.png")
                    else -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/wire.png")
                }

                latestTypeTex = it.type

                RenderSystem.setShader { GameRenderer.getPositionTexShader() }
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
                RenderSystem.setShaderTexture(0, texLocation)
            }

            blit(pPoseStack, it.pos.x, it.pos.y, blitOffset, 0f, 0f, texSize, texSize, texSize, texSize)

            // check if the mouse is hovering over the cell
            if(pMouseY > yMin && pMouseY < yMax && pMouseX > xMin && pMouseX < xMax) {
                toolTipCell = it
            }
        }

        if(toolTipCell != null){
            // draw the outline of the cell

            val pos = toolTipCell!!.pos
            pPoseStack.rect4(pos.x, pos.y, texSize - 1, texSize - 1, McColorValues.white)
        }

        pPoseStack.popPose()

        if(toolTipCell!=null) {
            drawInfoCard(pPoseStack, pMouseX, pMouseY, toolTipCell!!)
        }

        // finally, render the header and footer on top of everything
        renderHeader(pPoseStack)
        renderFooter(pPoseStack, toolTipCell)
    }

    private fun drawInfoCard(poseStack: PoseStack, mouseX : Int, mouseY : Int, cell : CellInfo){
        val data = cell.label

        val marginX = 2
        val marginY = 2

        val width = data.entries.maxOf {
            font.width("${ColoredStringFormatter.contentOf(it.label)}: ${ColoredStringFormatter.contentOf(it.value)}")
        } + marginX * 2

        val height = data.entries.count() * font.lineHeight + marginY * 2

        // generate background
        fill(poseStack, mouseX, mouseY, mouseX + width, mouseY + height, McColor(70u, 72u, 74u).value)

        data.entries.forEachIndexed { index, entry ->
            val vertical = index * font.lineHeight

            val labelData = ColoredStringFormatter.getColorsOrDefault(entry.label)
            val valueData = ColoredStringFormatter.getColorsOrDefault(entry.value,
                if(labelData.color != McColors.white) labelData.color else McColor(160u, 150u, 150u))

            // draw the label
            drawString(poseStack, font, "${labelData.string}: ", mouseX + marginX, mouseY + vertical + marginY, labelData.color.value)

            // draw the value
            drawString(poseStack, font, valueData.string, mouseX + marginX + font.width("${labelData.string}: "), mouseY + vertical + marginY, valueData.color.value)
        }

        poseStack.rect4(mouseX, mouseY, width, height, McColor(86u, 86u, 86u).value)
    }

    private fun renderHeader(poseStack: PoseStack){
        val h = 20
        fill(poseStack, 0, 0, width, h, McColor(60u, 63u, 65u, 150u).value)
        drawString(poseStack, font, "Solve time: ${SuffixConverter.convert(
            solveTime.toDouble() / 1000000000.0,
            "s",
            2,
        )}",2 , 6, McColor(255u, 255u, 200u, 200u).value)

        poseStack.rect4(0, 0, width - 1, h, McColor(95u, 95u, 95u, 150u).value)
    }

    private fun renderFooter(poseStack: PoseStack, toolTipCell : CellInfo?){
        val localizedName =
            if (toolTipCell != null) get(blockFor(toolTipCell.type).descriptionId)
            else toolTipCell?.type

        val sb = StringBuilder()

        sb.append("${get(TranslatableComponent("plotter.cell_count").key)}: ${cells.count()}")

        sb.append("     ")

        if (localizedName != null) {
            sb.append("${get(TranslatableComponent("plotter.highlighted").key)}: $localizedName")
        }

        val h = 20
        fill(poseStack, 0, height - h, width, height, McColor(60u, 63u, 65u, 100u).value)
        poseStack.rect4(0, height - h, width - 1, height, McColor(95u, 95u, 95u, 150u).value)
        drawString(poseStack, font, sb.toString(),2 , height - h + 6, McColor(255u, 255u, 200u, 100u).value)
    }

    // zoom
    override fun mouseScrolled(pMouseX: Double, pMouseY: Double, pDelta: Double): Boolean {
        if(pDelta < 0){
            scale = max(0.2f, scale + pDelta.toFloat() / 20f)
        }
        else {
            scale += pDelta.toFloat() / 20f
        }

        return super.mouseScrolled(pMouseX, pMouseY, pDelta)
    }

    // pan
    override fun mouseDragged(pMouseX: Double, pMouseY: Double, pButton: Int, pDragX: Double, pDragY: Double): Boolean {
        posX += pDragX.toFloat() / scale
        posY += pDragY.toFloat() / scale

        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY)
    }
}
