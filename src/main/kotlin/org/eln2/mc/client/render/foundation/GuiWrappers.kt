package org.eln2.mc.client.render.foundation

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Matrix4f
import net.minecraft.client.gui.GuiComponent
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.mathematics.Rectangle4F
import org.eln2.mc.mathematics.Rectangle4I
import org.eln2.mc.mathematics.Vector2F
import org.eln2.mc.mathematics.Vector2I
//import org.joml.Matrix4f
import org.joml.Vector4f
import java.lang.Long
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.UByte
import kotlin.error
import kotlin.toUByte

fun renderTextured(
    texture: ResourceLocation,
    poseStack: PoseStack,
    blitOffset: Int = 0,
    color: Vector4f,
    position: Vector2I,
    uvSize: Vector2I,
    uvPosition: Vector2F,
    textureSize: Vector2I,
) {

    RenderSystem.setShader { GameRenderer.getPositionTexShader() }
    RenderSystem.setShaderColor(color.x(), color.y(), color.z(), color.w())
    RenderSystem.setShaderTexture(0, texture)

    GuiComponent.blit(
        poseStack,
        position.x,
        position.y,
        blitOffset,
        uvPosition.x,
        uvPosition.y,
        uvSize.x,
        uvSize.y,
        textureSize.x,
        textureSize.y
    )
}

fun renderColored(poseStack: PoseStack, color: McColor, rectangle: Rectangle4I) {
    RenderSystem.setShader { GameRenderer.getPositionColorShader() }

    GuiComponent.fill(
        poseStack,
        rectangle.left,
        rectangle.top,
        rectangle.right,
        rectangle.bottom,
        color.value
    )
}

fun renderColored(poseStack: PoseStack, color: Vector4f, rectangle: Rectangle4I) {
    renderColored(poseStack, mcColor(color), rectangle)
}

fun renderColored(poseStack: PoseStack, color: McColor, rectangle: Rectangle4F) {
    RenderSystem.setShader { GameRenderer.getPositionColorShader() }

    fill(
        poseStack,
        rectangle.left,
        rectangle.top,
        rectangle.right,
        rectangle.bottom,
        color.value
    )
}

fun renderColored(poseStack: PoseStack, color: Vector4f, rectangle: Rectangle4F) {
    renderColored(poseStack, mcColor(color), rectangle)
}

private fun fill(pPoseStack: PoseStack, pMinX: Float, pMinY: Float, pMaxX: Float, pMaxY: Float, pColor: Int) {
    innerFill(pPoseStack.last().pose(), pMinX, pMinY, pMaxX, pMaxY, pColor)
}

private fun innerFill(
    pMatrix: Matrix4f,
    pMinX: Float,
    pMinY: Float,
    pMaxX: Float,
    pMaxY: Float,
    pColor: Int,
) {

    var minX = pMinX
    var minY = pMinY
    var maxX = pMaxX
    var maxY = pMaxY
    if (minX < maxX) {
        val i = minX
        minX = maxX
        maxX = i
    }
    if (minY < maxY) {
        val j = minY
        minY = maxY
        maxY = j
    }
    val f3 = (pColor shr 24 and 255).toFloat() / 255.0f
    val f = (pColor shr 16 and 255).toFloat() / 255.0f
    val f1 = (pColor shr 8 and 255).toFloat() / 255.0f
    val f2 = (pColor and 255).toFloat() / 255.0f
    val bufferBuilder = Tesselator.getInstance().builder
    RenderSystem.enableBlend()
    RenderSystem.disableTexture()
    RenderSystem.defaultBlendFunc()
    RenderSystem.setShader { GameRenderer.getPositionColorShader() }
    bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
    bufferBuilder.vertex(pMatrix, minX, maxY, 0f).color(f, f1, f2, f3).endVertex()
    bufferBuilder.vertex(pMatrix, maxX, maxY, 0f).color(f, f1, f2, f3).endVertex()
    bufferBuilder.vertex(pMatrix, maxX, minY, 0f).color(f, f1, f2, f3).endVertex()
    bufferBuilder.vertex(pMatrix, minX, minY, 0f).color(f, f1, f2, f3).endVertex()
    bufferBuilder.end()
    BufferUploader.draw(bufferBuilder.end())
    RenderSystem.enableTexture()
    RenderSystem.disableBlend()
}

fun mcColor(color: Vector4f): McColor {
    fun getByte(v: Float): UByte {
        return (v * 255f).toInt().coerceIn(0, 255).toUByte()
    }

    return McColor(
        getByte(color.x()),
        getByte(color.y()),
        getByte(color.z()),
        getByte(color.w())
    )
}

data class McColor(val r: UByte, val g: UByte, val b: UByte, val a: UByte) {
    constructor(r: Int, g: Int, b: Int, a: Int) : this(
        if (r < 256 && r > -1) r.toUByte() else error("R is not within bounds."),
        if (g < 256 && g > -1) g.toUByte() else error("G is not within bounds."),
        if (b < 256 && b > -1) b.toUByte() else error("B is not within bounds."),
        if (a < 256 && a > -1) a.toUByte() else error("A is not within bounds.")
    )

    constructor(r: Int, g: Int, b: Int) : this(r, g, b, 255)

    constructor(r: UByte, g: UByte, b: UByte) : this(r, g, b, 255u)

    constructor(binary: Int) : this(
        (binary shr 16 and 0xFF).toUByte(),
        (binary shr 8 and 0xFF).toUByte(),
        (binary and 0xFF).toUByte(),
        (binary shr 24 and 0xFF).toUByte()
    )

    val value = (b.toUInt() or (g.toUInt() shl 8) or (r.toUInt() shl 16) or (a.toUInt() shl 24)).toInt()

    fun serialize(buffer: FriendlyByteBuf): FriendlyByteBuf {
        buffer.writeInt(value)
        return buffer
    }

    override fun toString(): String {
        return Integer.toHexString(value)
    }

    companion object {
        fun deserialize(buffer: FriendlyByteBuf): McColor {
            return McColor(buffer.readInt())
        }

        fun fromString(hex: String): McColor {
            return McColor(Long.parseLong(hex, 16).toInt())
        }
    }
}

// This really looks like it could be an enum, but honestly it makes the call syntax worse.
object McColors {
    val red = McColor(255u, 0u, 0u)
    val green = McColor(0u, 255u, 0u)
    val blue = McColor(0u, 0u, 255u)
    val white = McColor(255u, 255u, 255u)
    val black = McColor(0u, 0u, 0u)
    val cyan = McColor(0u, 255u, 255u)
    val purple = McColor(172u, 79u, 198u)
    val yellow = McColor(255u, 255u, 0u)
    val lightPink = McColor(255u, 182u, 193u)
}

object McColorValues {
    val red = McColors.red.value
    val green = McColors.green.value
    val blue = McColors.blue.value
    val white = McColors.white.value
    val black = McColors.black.value
    val cyan = McColors.cyan.value
    val purple = McColors.purple.value
    val yellow = McColors.yellow.value
    val lightPink = McColors.lightPink.value
}
