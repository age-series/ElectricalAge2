package org.eln2.mc.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.resources.language.I18n.get
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.extensions.ByteBufferExtensions.readString
import org.eln2.mc.extensions.ByteBufferExtensions.writeString
import org.eln2.mc.extensions.PoseStackExtensions
import org.eln2.mc.extensions.PoseStackExtensions.blitMultiple
import kotlin.math.max
import kotlin.system.measureNanoTime

class CellInfo(val type : String, val info : String, val pos : BlockPos){
    constructor(buffer : FriendlyByteBuf) : this(buffer.readString(), buffer.readString(), buffer.readBlockPos())

    fun serialize(buffer : FriendlyByteBuf){
        buffer.writeString(type)
        buffer.writeString(info)
        buffer.writeBlockPos(pos)
    }
}

class PlotterScreen(private val cells : ArrayList<CellInfo>, val solveTime : Long) : Screen(TextComponent("Plotter")) {
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
        drawCenteredString(poseStack, font, "Solve time: $solveTime",width / 2 , 1, 0xFFFFFF)
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

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        val drawList = ArrayList<PoseStackExtensions.Union4i>()
        renderBlueprintBackground(pPoseStack)

        RenderSystem.setShader { GameRenderer.getPositionTexShader() }

        pPoseStack.pushPose()

        pPoseStack.scale(scale, scale, scale)
        pPoseStack.translate(posX, posY, 1.0)

        var toolTipCell : CellInfo? = null
        var latestTypeTex = ""

        var drawNanoseconds = 0L
        var culled = 0

        cellInfoCollection.forEach {
            val xMin = (it.pos.x + posX) * scale
            val yMin = (it.pos.y + posY) * scale
            val xMax = scale * texSize.toFloat() + xMin
            val yMax = scale * texSize.toFloat() + yMin

            // perf: culling
            if(xMax < 0 || yMax < 0 || xMin > width || yMin > height){
                ++culled
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

                RenderSystem.setShaderTexture(0, texLocation)
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
            }

            drawNanoseconds += measureNanoTime {
                blit(pPoseStack, it.pos.x, it.pos.y, 0f, 0f, texSize, texSize, texSize, texSize)
            }

            drawList.add(PoseStackExtensions.Union4i(it.pos.x, it.pos.y, texSize, texSize))

            // check if the mouse is hovering over the cell
            if(pMouseY > yMin && pMouseY < yMax && pMouseX > xMin && pMouseX < xMax) {
                toolTipCell = it
            }
        }

        /*
        if(drawList.isNotEmpty()){
            drawNanoseconds = measureNanoTime {
                pPoseStack.blitMultiple(drawList)
            }
        }*/

        pPoseStack.popPose()

        if(toolTipCell!=null) {
            drawCenteredString(pPoseStack, font, toolTipCell!!.info, pMouseX, pMouseY, (0xC9365A7Fu).toInt())
        }

        val block = try {
            BlockRegistry.BLOCK_REGISTRY.entries.first {
                it.id.path == toolTipCell?.type?.split(':')?.get(1)
            }.get()
        } catch (e: Exception) {
            // There's no name, and we're just going to catch instead of doing logic to figure arrays..
            // TODO: Actually write decent code here if we care?
            null
        }

        val localizedName = if (block != null) {
            get(block.descriptionId)
        } else {
            toolTipCell?.type
        }

        val textList = mutableListOf<String>()

        textList.add("${get(TranslatableComponent("plotter.cell_count").key)}: ${cells.count()}")

        if (localizedName != null) {
            textList.add("${get(TranslatableComponent("plotter.highlighted").key)}: $localizedName")
        }

        GuiComponent.drawString(pPoseStack, font, textList.joinToString("    "), 5, height - 10, 0xFF0000)
        renderHeader(pPoseStack)

       //Eln2.LOGGER.info("Plotter draw nanoseconds: $drawNanoseconds, culls: $culled")
    }

    override fun keyPressed(pKeyCode: Int, pScanCode: Int, pModifiers: Int): Boolean {
        Eln2.LOGGER.info("keycode: $blueprintTex scan: $pScanCode mod: $pModifiers")

        return super.keyPressed(pKeyCode, pScanCode, pModifiers)
    }

    override fun mouseScrolled(pMouseX: Double, pMouseY: Double, pDelta: Double): Boolean {
        val oldScale = scale

        if(pDelta < 0){
            scale = max(0.2f, scale + pDelta.toFloat() / 20f)
        }
        else {
            scale += pDelta.toFloat() / 20f
        }

        return super.mouseScrolled(pMouseX, pMouseY, pDelta)
    }

    override fun mouseDragged(pMouseX: Double, pMouseY: Double, pButton: Int, pDragX: Double, pDragY: Double): Boolean {
        posX += pDragX.toFloat() / scale
        posY += pDragY.toFloat() / scale

        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY)
    }
}
