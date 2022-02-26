package org.eln2.mc.client.gui.library.extensions

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.renderer.GameRenderer
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import org.eln2.mc.client.gui.library.Point2F
import org.eln2.mc.client.gui.library.Point2I
import org.eln2.mc.client.gui.library.Rect4I
import org.eln2.mc.utility.McColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object PoseStackExtensions : GuiComponent() {
    fun PoseStack.rect(x : Int, y : Int, width : Int, height : Int, color : McColor) : PoseStack{
        val colorValue = color.value
        hLine(this, x, x + width, y, colorValue)
        hLine(this, x, x + width, y + height, colorValue)
        vLine(this, x, y, y + height, colorValue)
        vLine(this, x + width, y, y + height, colorValue)
        return this
    }

    fun PoseStack.quad(p1 : Point2I, p2: Point2I, p3 : Point2I, p4: Point2I, color: McColor) : PoseStack{
        return quadf(p1.toF(), p2.toF(), p3.toF(), p4.toF(), color)
    }

    // todo: test
    fun PoseStack.quadf(p1 : Point2F, p2 : Point2F, p3 : Point2F, p4 : Point2F, color : McColor) : PoseStack{
        val matrix = this.last().pose()

        val r = color.rNorm
        val g = color.gNorm
        val b = color.bNorm
        val a = color.aNorm

        val builder = Tesselator.getInstance().builder
        RenderSystem.enableBlend()
        RenderSystem.disableTexture()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader { GameRenderer.getPositionColorShader() }
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)

        builder.vertex(matrix, p1.x, p1.y, 0.0f).color(r, g, b, a).endVertex()
        builder.vertex(matrix, p2.x, p2.y, 0.0f).color(r, g, b, a).endVertex()
        builder.vertex(matrix, p3.x, p3.y, 0.0f).color(r, g, b, a).endVertex()
        builder.vertex(matrix, p4.x, p4.y, 0.0f).color(r, g, b, a).endVertex()

        builder.end()
        BufferUploader.end(builder)
        RenderSystem.enableTexture()
        RenderSystem.disableBlend()

        return this
    }

    fun PoseStack.rect(rect : Rect4I, color : McColor) : PoseStack{
        return rect(rect.x, rect.y, rect.width, rect.height, color)
    }

    fun PoseStack.rect(pos : Point2I, size : Point2I, color : McColor) : PoseStack{
        return rect(pos.x, pos.y, size.x, size.y, color)
    }

    fun PoseStack.rect(pos : Point2I, width: Int, height: Int, color: McColor) : PoseStack{
        return rect(pos.x, pos.y, width, height, color)
    }

    fun PoseStack.fillRect(x : Int, y : Int, width: Int, height: Int, color: McColor) : PoseStack{
        fill(this, x, y, x + width, y + height, color.value)
        return this
    }

    fun PoseStack.fillRect(rect : Rect4I, color: McColor) : PoseStack{
        fillRect(rect.x, rect.y, rect.width, rect.height, color)
        return this
    }

    fun PoseStack.fillRect(pos : Point2I, size : Point2I, color : McColor) : PoseStack{
        return fillRect(pos.x, pos.y, size.x, size.y, color)
    }

    fun PoseStack.fillRect(pos : Point2I, width: Int, height: Int, color: McColor) : PoseStack{
        return fillRect(pos.x, pos.y, width, height, color)
    }

    fun PoseStack.withPose(body : (() -> Unit)) : PoseStack{
        this.pushPose()
        body()
        this.popPose()
        return this
    }

    fun PoseStack.withBlend(body: (() -> Unit)) : PoseStack{
        RenderSystem.enableBlend()
        body()
        RenderSystem.disableBlend()
        return this
    }

    fun PoseStack.withDefaultBlend(body: (() -> Unit)) : PoseStack {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        body()
        RenderSystem.disableBlend()
        return this
    }

    fun PoseStack.withoutBlend(body: (() -> Unit)) : PoseStack{
        RenderSystem.disableBlend()
        body()
        RenderSystem.enableBlend()
        return this
    }

    fun PoseStack.withTexture(body: (() -> Unit)) : PoseStack{
        RenderSystem.enableTexture()
        body()
        RenderSystem.disableBlend()
        return this
    }

    fun PoseStack.withoutTexture(body: (() -> Unit)) : PoseStack{
        RenderSystem.disableBlend()
        body()
        RenderSystem.enableTexture()
        return this
    }

    fun PoseStack.withDepthTest(body: (() -> Unit)) : PoseStack{
        RenderSystem.enableDepthTest()
        body()
        RenderSystem.disableDepthTest()
        return this
    }

    fun PoseStack.withoutDepthTest(body: (() -> Unit)) : PoseStack{
        RenderSystem.disableDepthTest()
        body()
        RenderSystem.enableDepthTest()
        return this
    }

    fun PoseStack.useTesselator(body: ((BufferBuilder) -> Unit)) : PoseStack{
        val tesselator = Tesselator.getInstance()
        val builder = tesselator.builder
        body(builder)
        tesselator.end()
        return this
    }

    fun PoseStack.usePositionColor(body: (() -> Unit)) : PoseStack{
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        body()
        return this
    }

    fun PoseStack.translate(p : Point2F) : PoseStack{
        this.translate(p.x.toDouble(), p.y.toDouble(), 0.0)
        return this
    }

    fun PoseStack.translate(p : Point2I) : PoseStack{
        return translate(p.toF())
    }

    fun PoseStack.scale(p : Point2F) : PoseStack{
        this.scale(p.x, p.y, 0f)
        return this
    }

    fun PoseStack.scale(p : Point2I) : PoseStack{
        return scale(p.toF())
    }

    fun PoseStack.scale(scale : Float) : PoseStack{
        this.scale(scale, scale, 0f)
        return this
    }

    fun PoseStack.text(pos : Point2I, text : String, color : McColor, font : Font) : PoseStack{
        drawString(this, font, text, pos.x, pos.y, color.value)
        return this
    }

    fun PoseStack.centeredText(pos : Point2I, text : String, color : McColor, font : Font) : PoseStack{
        drawCenteredString(this, font, text, pos.x, pos.y, color.value)
        return this
    }

    fun PoseStack.vLine(pos : Point2I, size : Int, color : McColor, thickness : Int = 1){
        var minY = pos.y
        var maxY = minY + size

        if (maxY < minY) {
            val i = minY
            minY = maxY
            maxY = i
        }

        fill(this, pos.x, minY + 1, pos.x + thickness, maxY, color.value)
    }

    fun PoseStack.hLine(pos : Point2I, size : Int, color : McColor, thickness: Int = 1){
        var minX = pos.x
        var maxX = minX + size

        if (maxX < minX) {
            val i = minX
            minX = maxX
            maxX = i
        }

        fill(this, minX, pos.y, maxX + 1, pos.y + thickness, color.value)
    }
}
