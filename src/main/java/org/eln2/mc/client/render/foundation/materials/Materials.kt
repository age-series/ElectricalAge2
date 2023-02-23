package org.eln2.mc.client.render.foundation.materials

import com.jozufozu.flywheel.api.Material
import com.jozufozu.flywheel.api.MaterialManager
import com.jozufozu.flywheel.api.struct.Batched
import com.jozufozu.flywheel.api.struct.Instanced
import com.jozufozu.flywheel.api.struct.StructType
import com.jozufozu.flywheel.api.struct.StructWriter
import com.jozufozu.flywheel.backend.gl.buffer.VecBuffer
import com.jozufozu.flywheel.core.layout.BufferLayout
import com.jozufozu.flywheel.core.layout.CommonItems
import com.jozufozu.flywheel.core.layout.MatrixItems
import com.jozufozu.flywheel.core.materials.BasicWriterUnsafe
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.core.model.ModelTransformer
import com.jozufozu.flywheel.util.MatrixWrite
import com.mojang.math.Matrix3f
import com.mojang.math.Matrix4f
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.client.render.foundation.materials.Constants.FLOAT_SIZE
import org.eln2.mc.client.render.foundation.materials.Constants.MATRIX3F_SIZE
import org.eln2.mc.client.render.foundation.materials.Constants.MATRIX4F_SIZE
import org.eln2.mc.client.render.foundation.materials.Extensions.writeUnsafe
import org.lwjgl.system.MemoryUtil
import javax.annotation.CheckReturnValue

object MaterialStructs {
    val BLENDING: StructType<BlendingData> = BlendingType()
}

//#region Blending

// Test implementation:

class BlendingData : ModelData(){
    var blend: Float = 0f

    fun withBlending(blend: Float): BlendingData{
        return this.also { it.blend = blend }
    }
}

class BlendingType : Instanced<BlendingData>, Batched<BlendingData> {
    override fun create(): BlendingData {
        return BlendingData()
    }

    override fun getLayout(): BufferLayout {
        return FORMAT
    }

    override fun getWriter(backing: VecBuffer): StructWriter<BlendingData> {
        return BlendingWriterUnsafe(backing, this)
    }

    override fun getProgramSpec(): ResourceLocation {
        return Shaders.BLENDING
    }

    override fun transform(d: BlendingData, b: ModelTransformer.Params) {
        b.transform(d.model, d.normal)
            .color(d.r, d.g, d.b, d.a)
            .light(d.packedLight)
    }

    companion object {
        val FORMAT: BufferLayout = BufferLayout.builder()
            .addItems(CommonItems.LIGHT, CommonItems.RGBA)
            .addItems(MatrixItems.MAT4, MatrixItems.MAT3)
            .addItems(CommonItems.FLOAT) // Blend
            .build()
    }
}

class BlendingWriterUnsafe(backingBuffer: VecBuffer, vertexType: StructType<BlendingData>) :
    BasicWriterUnsafe<BlendingData>(backingBuffer, vertexType) {

    override fun writeInternal(d: BlendingData) {
        super.writeInternal(d)

        var ptr = writePointer + 6

        ptr += d.model.writeUnsafe(ptr)
        ptr += d.normal.writeUnsafe(ptr)
        ptr += d.blend.writeUnsafe(ptr)
    }
}

//#endregion

object MaterialManagerExtensions {
    fun MaterialManager.blending(): Material<BlendingData> {
        return this.defaultSolid().material(MaterialStructs.BLENDING)
    }
}

private object Constants {
    const val FLOAT_SIZE = 4
    const val MATRIX4F_SIZE = FLOAT_SIZE * 4 * 4
    const val MATRIX3F_SIZE = FLOAT_SIZE * 3 * 3
}

@Suppress("CAST_NEVER_SUCCEEDS")
private object Extensions {
    @CheckReturnValue
    fun Matrix4f.writeUnsafe(qwPtr: Long): Int {
        (this as MatrixWrite).`flywheel$writeUnsafe`(qwPtr)
        return MATRIX4F_SIZE
    }

    @CheckReturnValue
    fun Matrix3f.writeUnsafe(qwPtr: Long): Int {
        (this as MatrixWrite).`flywheel$writeUnsafe`(qwPtr)
        return MATRIX3F_SIZE
    }

    @CheckReturnValue
    fun Float.writeUnsafe(qwPtr: Long): Int {
        MemoryUtil.memPutFloat(qwPtr, this)
        return FLOAT_SIZE
    }
}
