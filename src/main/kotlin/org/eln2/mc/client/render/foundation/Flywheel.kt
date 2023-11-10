package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.api.InstanceData
import com.jozufozu.flywheel.api.struct.Instanced
import com.jozufozu.flywheel.api.struct.StructType
import com.jozufozu.flywheel.backend.gl.buffer.VecBuffer
import com.jozufozu.flywheel.backend.instancing.InstancedRenderRegistry
import com.jozufozu.flywheel.backend.struct.UnsafeBufferWriter
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.layout.BufferLayout
import com.jozufozu.flywheel.core.layout.CommonItems
import com.jozufozu.flywheel.core.layout.MatrixItems
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelType
import com.jozufozu.flywheel.util.Color
import com.jozufozu.flywheel.util.MatrixWrite
import com.jozufozu.flywheel.util.transform.Transform
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Matrix3f
import com.mojang.math.Matrix4f
import com.mojang.math.Quaternion
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import org.eln2.mc.bind
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.mathematics.Vector3d
import org.eln2.mc.resource
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer

//import org.eln2.mc.common.content.GridConnectionInstanceFlw

object FlywheelRegistry {
    fun initialize() {
        InstancedRenderRegistry.configure(BlockRegistry.MULTIPART_BLOCK_ENTITY.get())
            .alwaysSkipRender()
            .factory { manager, entity -> MultipartBlockEntityInstance(manager, entity) }
            .apply()

      /*  InstancedRenderRegistry.configure(Content.GRID_CONNECTION_ENTITY.get())
            .alwaysSkipRender()
            .factory { manager, entity -> GridConnectionInstanceFlw(manager, entity) }
            .apply()*/
    }
}

object ModelLightOverrideType : ModelType() {
    private val PROGRAM_SPEC: ResourceLocation = resource("block_light_override")

    override fun getProgramSpec() = PROGRAM_SPEC
}

object PolarType : Instanced<PolarData> {
    private val FORMAT: BufferLayout = BufferLayout.builder()
        .addItems(CommonItems.LIGHT)
        .addItems(CommonItems.RGBA, CommonItems.RGBA)
        .addItems(MatrixItems.MAT4, MatrixItems.MAT3)
        .build()

    private val PROGRAM_SPEC: ResourceLocation = resource("polar")

    override fun create() = PolarData()
    override fun getLayout() = FORMAT
    override fun getWriter(backing: VecBuffer) = PolarWriterUnsafe(backing, this)
    override fun getProgramSpec() = PROGRAM_SPEC
}

class PolarData : InstanceData(), FlatLit<PolarData>, Transform<PolarData> {
    var blockLight = 0
    var skyLight = 0
    var color1 = Color.WHITE
    var color2 = Color.WHITE
    val model = Matrix4f()
    val normal = Matrix3f()

    override fun setBlockLight(blockLight: Int): PolarData {
        markDirty()
        this.blockLight = blockLight
        return this
    }

    override fun setSkyLight(skyLight: Int): PolarData {
        markDirty()
        this.skyLight = skyLight
        return this
    }

    override fun getPackedLight() = LightTexture.pack(blockLight, skyLight)

    fun setColor1(value: Color) : PolarData {
        markDirty()
        color1 = value
        return this
    }

    fun setColor2(value: Color) : PolarData {
        markDirty()
        color2 = value
        return this
    }

    fun setTransform(stack: PoseStack): PolarData {
        markDirty()
        model.load(stack.last().pose())
        normal.load(stack.last().normal())
        return this
    }

    fun loadIdentity(): PolarData {
        markDirty()
        model.setIdentity()
        normal.setIdentity()
        return this
    }

    override fun multiply(quaternion: Quaternion): PolarData {
        markDirty()
        model.multiply(quaternion)
        normal.mul(quaternion)
        return this
    }

    override fun scale(pX: Float, pY: Float, pZ: Float): PolarData {
        markDirty()
        model.multiply(Matrix4f.createScaleMatrix(pX, pY, pZ))
        if (pX == pY && pY == pZ) {
            if (pX > 0.0f) {
                return this
            }
            normal.mul(-1.0f)
        }
        val f = 1.0f / pX
        val f1 = 1.0f / pY
        val f2 = 1.0f / pZ
        val f3 = Mth.fastInvCubeRoot(f * f1 * f2)
        normal.mul(Matrix3f.createScaleMatrix(f3 * f, f3 * f1, f3 * f2))
        return this
    }

    override fun translate(x: Double, y: Double, z: Double): PolarData {
        markDirty()
        model.multiplyWithTranslation(x.toFloat(), y.toFloat(), z.toFloat())
        return this
    }

    override fun mulPose(pose: Matrix4f): PolarData {
        model.multiply(pose)
        return this
    }

    override fun mulNormal(normal: Matrix3f): PolarData {
        this.normal.mul(normal)
        return this
    }
}

class PolarWriterUnsafe(backingBuffer: VecBuffer, vertexType: StructType<PolarData>) : UnsafeBufferWriter<PolarData>(backingBuffer, vertexType) {
    override fun writeInternal(s: PolarData) {
        val ptr = writePointer
        MemoryUtil.memPutByte(ptr + 0, (s.blockLight shl 4).toByte())
        MemoryUtil.memPutByte(ptr + 1, (s.skyLight shl 4).toByte())

        // Todo blit in one operation? I tried to write the int value but it is not laid out like we need and I don't have time to fix it right now
        MemoryUtil.memPutByte(ptr + 2, s.color1.red.toByte())
        MemoryUtil.memPutByte(ptr + 3, s.color1.green.toByte())
        MemoryUtil.memPutByte(ptr + 4, s.color1.blue.toByte())
        MemoryUtil.memPutByte(ptr + 5, s.color1.alpha.toByte())

        MemoryUtil.memPutByte(ptr + 6, s.color2.red.toByte())
        MemoryUtil.memPutByte(ptr + 7, s.color2.green.toByte())
        MemoryUtil.memPutByte(ptr + 8, s.color2.blue.toByte())
        MemoryUtil.memPutByte(ptr + 9, s.color2.alpha.toByte())

        (s.model as MatrixWrite).`flywheel$writeUnsafe`(ptr + 10)
        (s.normal as MatrixWrite).`flywheel$writeUnsafe`(ptr + 74)
    }
}

/**
 * Loads a baked model, and applies a post-processing step. The model must:
 * - Not have quads oriented towards north and south
 * - Be like a tube
 *
 * The vertex data is rotated so that, when it gets written to the vertex buffer, a special ordering of vertices is obtained:
 * Vertices 0, 1 are on one "pole" of the model (min z) and vertices 2, 3 are on the other (max z)
 * */
open class PolarModel(modelLocation: ResourceLocation) : PartialModel(modelLocation) {
    override fun set(bakedModel: BakedModel) {
        @Suppress("NAME_SHADOWING")
        val bakedModel = bakedModel.bind()

        val quadPositionAttributes = HashMap<BakedQuad, ArrayList<Vector3d>>()

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        bakedModel.getQuads(null, null, null).forEach { quad ->
            if(quad.direction == Direction.NORTH || quad.direction == Direction.SOUTH) {
                error("Invalid connection model")
            }

            val positionList = ArrayList<Vector3d>()
            quadPositionAttributes[quad] = positionList

            require(quad.vertices.size == 32)

            val buffer = ByteBuffer.allocate(32)
            val intView = buffer.asIntBuffer()

            for (i in 0 until 4) {
                intView.clear()
                intView.put(quad.vertices, i * 8, 8)

                positionList.add(
                    Vector3d(
                        buffer.getFloat(0).toDouble(),
                        buffer.getFloat(4).toDouble(),
                        buffer.getFloat(8).toDouble(),
                    )
                )
            }
        }

        quadPositionAttributes.forEach { (quad, positions) ->
            require(positions.size == 4)

            data class VertexNode(
                val index: Int,
                val position: Vector3d,
                val next: Int
            )

            val nodes = positions.mapIndexed { index, position ->
                val nextIndex = if(index == 3) {
                    0 // wrap around (circular linked list)
                }
                else {
                    index + 1
                }

                VertexNode(
                    index,
                    position,
                    nextIndex
                )
            }

            val headNode = nodes
                .sortedBy { it.position.z }
                .take(2)
                .let {
                    if(it[0].next == it[1].index) {
                        it[0]
                    }
                    else if(it[1].next == it[0].index) {
                        it[1]
                    }
                    else {
                        error("Disjoint head vertices")
                    }
                }

            val indices = headNode.let { head ->
                val nodeList = arrayListOf(head)

                repeat(3) {
                    nodeList.add(nodes[nodeList.last().next])
                }

                require(nodeList.distinct().size == nodeList.size && nodeList.size == 4)

                nodeList.map { it.index }
            }

            val vertexRecords = let {
                val buffer = ByteBuffer.allocate(8 * 4)
                val intView = buffer.asIntBuffer()
                val results = ArrayList<IntArray>(4)

                for (i in 0 until 4) {
                    intView.clear()
                    intView.put(quad.vertices, i * 8, 8)

                    val array = IntArray(8)

                    intView.rewind()
                    intView.get(array)
                    results.add(array)
                }

                results
            }

            val writer = IntBuffer.wrap(quad.vertices)
            writer.clear()

            indices.forEach { index ->
                writer.put(vertexRecords[index])
            }
        }

        super.set(bakedModel)
    }
}
