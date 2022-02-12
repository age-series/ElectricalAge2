package org.eln2.mc.client.overlay.plotter

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiComponent
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.client.gui.ForgeIngameGui
import net.minecraftforge.client.gui.IIngameOverlay
import org.eln2.mc.Eln2
import org.eln2.mc.common.cell.CellGraph
import org.eln2.mc.common.cell.CellRegistry
import java.lang.Math.*

// do not use object because it will make the graph linger
object PlotterOverlay : IIngameOverlay {
    var latestGraph : CellGraph? = null

    override fun render(gui: ForgeIngameGui, mStack: PoseStack, partialTicks: Float, width: Int, height: Int) {
        if(latestGraph == null){
            return
        }
        if(latestGraph!!.cells.isEmpty()){
            latestGraph = null
            return
        }

        val minX = latestGraph!!.cells.minOf { it.pos.x }
        val minY = latestGraph!!.cells.minOf { it.pos.z }
        val largestX = latestGraph!!.cells.maxOf { it.pos.x }
        val largestY = latestGraph!!.cells.maxOf { it.pos.z }

        val texSize = 32

        val overlayWidth = largestX - minX
        val overlayHeight = largestY - minY
        val offX = if(minX < 0) kotlin.math.abs(minX) else -minX
        val offY = if(minY < 0) kotlin.math.abs(minY) else -minY

        val overlaySize = overlayWidth.coerceAtLeast(overlayHeight).toFloat() // number of tiles
        val screenSize = width.coerceAtLeast(height) / texSize.toFloat()      // max number of tiles

        val wantedSize = screenSize / 3f // wanted number of tiles for size
        val targetSize = (wantedSize / overlaySize).coerceAtMost(1f) * texSize

        val scaledSize = targetSize / texSize.toFloat() // scaling factor

        mStack.pushPose()
        mStack.scale(scaledSize, scaledSize, scaledSize)

        val cells = ArrayList(latestGraph!!.cells)
        cells.sortBy { it.id.toString() }

        var latestTypeTex = ""

        latestGraph!!.cells.forEach{ cell ->
            val idStr = cell.id.toString()

            if(idStr != latestTypeTex){
                gui.setupOverlayRenderState(true, true, when(idStr){
                    CellRegistry.GROUND_CELL.id!!.toString() -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/ground.png")
                    CellRegistry.RESISTOR_CELL.id!!.toString() -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/resistor.png")
                    CellRegistry.VOLTAGE_SOURCE_CELL.id!!.toString() -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/voltage_source.png")
                    else -> ResourceLocation(Eln2.MODID, "textures/overlay/plotter/wire.png")
                })

                latestTypeTex = idStr
            }

            val baseX = cell.pos.x + offX
            val baseY = cell.pos.z + offY

            val x = baseX * texSize + width / 100
            val y = baseY * texSize + width / 100

            GuiComponent.blit(
                mStack,
                x,
                y,
                gui.blitOffset,
                0.0f,
                0.0f,
                texSize,
                texSize,
                texSize,
                texSize)
        }

        mStack.popPose()

        GuiComponent.drawString(mStack, gui.font, "Count: ${cells.count()}", 5, 5, 0xFFFFFF)
    }
}
