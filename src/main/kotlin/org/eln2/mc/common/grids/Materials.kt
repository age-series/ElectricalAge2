package org.eln2.mc.common.grids

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ChunkBufferBuilderPack
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher
import net.minecraft.client.renderer.chunk.RenderChunkRegion
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.ChunkPos
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.*
import org.ageseries.libage.mathematics.geometry.*
import org.ageseries.libage.sim.Material
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.client.render.*
import org.eln2.mc.client.render.foundation.*
import org.eln2.mc.common.items.ItemRegistry
import org.eln2.mc.extensions.getVector3d
import org.eln2.mc.extensions.putVector3d
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.mathematics.floorBlockPos
import org.eln2.mc.resource
import java.util.HashSet
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.floor

/**
 * Category of the grid connection material - used to filter which terminals accept which materials.
 * */
enum class GridMaterialCategory {
    /**
     * Micro grids - used by standard size specs.
     * */
    MicroGrid,

    /**
     * Power grids - used by poles.
     * */
    PowerGrid,

    /**
     * Signals only - doesn't allow connections to any other type.
     * */
    SignalGrid
}

/**
 * Grid connection material.
 * @param spriteSupplier Supplier for the texture. It must be in the block atlas.
 * @param vertexColor Per-vertex color, applied when rendering.
 * @param physicalMaterial The physical properties of the grid cable.
 * @param shape The shape of the cable. This is either [Straight] or [Catenary].
 * */
class GridMaterial(
    private val spriteSupplier: Supplier<TextureAtlasSprite>,
    val physicalMaterial: Material,
    val shape: Shape,
    val category: GridMaterialCategory,
    val meltingTemperature: Quantity<Temperature>,
    val explosionParticlesPerMeter: Int,
    val vertexColor: MyColor = MyColor.WHITE,
    val thermalColor: ThermalTint = ThermalTint.DEFAULT
) {
    val id get() = GridMaterials.getId(this)

    val sprite get() = spriteSupplier.get()

    private val factory = when(shape) {
        is Catenary -> {
            { a: Vector3d, b: Vector3d ->
                Cable3dA.Catenary(
                    a, b,
                    shape.circleVertices, shape.radius,
                    shape.splitDistanceHint, shape.splitParameterHint,
                    shape.slack,
                    shape.splitRotIncrementMax
                )
            }
        }
        is Straight -> {
            { a: Vector3d, b: Vector3d ->
                Cable3dA.Straight(
                    a, b,
                    shape.circleVertices, shape.radius,
                    shape.splitDistanceHint, shape.splitParameterHint
                )
            }
        }
        else -> error("Invalid shape $shape")
    }

    abstract class Shape(
        val circleVertices: Int,
        val radius: Double,
        val splitDistanceHint: Double,
        val splitParameterHint: Double,
    )

    class Catenary(
        circleVertices: Int,
        radius: Double,
        splitDistanceHint: Double,
        splitParameterHint: Double,
        val slack: Double = 0.01,
        val splitRotIncrementMax: Double = PI / 16.0,
    ) : Shape(circleVertices, radius, splitDistanceHint, splitParameterHint)

    class Straight(
        circleVertices: Int,
        radius: Double,
        splitDistanceHint: Double,
        splitParameterHint: Double,
    ) : Shape(circleVertices, radius, splitDistanceHint, splitParameterHint)

    fun create(a: Vector3d, b: Vector3d) = factory(a, b)
}

data class GridRegistryItem(
    val material: GridMaterial,
    val item: RegistryObject<GridCableItem>
)

object GridMaterials {
    private val atlas by lazy {
        Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
    }

    private val materials = MutableMapPairBiMap<GridMaterial, ResourceLocation>()
    private val items = MutableMapPairBiMap<GridMaterial, RegistryObject<GridCableItem>>()

    @Suppress("UNCHECKED_CAST")
    fun gridAtlasSprite(name: String) = Supplier<Any> {
        checkNotNull(atlas.apply(resource("grid/$name"))) {
            "Did not find $name"
        }
    } as Supplier<TextureAtlasSprite>

    fun gridMaterial(id: ResourceLocation, material: GridMaterial) = material.also { materials.add(it, id) }
    fun gridMaterial(id: String, material: GridMaterial) = gridMaterial(resource(id), material)
    fun getId(material: GridMaterial) : ResourceLocation = materials.forward[material] ?: error("Failed to get grid material id $material")
    fun getMaterial(resourceLocation: ResourceLocation) : GridMaterial = materials.backward[resourceLocation] ?: error("Failed to get grid material $resourceLocation")

    fun gridConnect(id: String, material: GridMaterial, itemsPerMeter: Int = 1) : GridRegistryItem {
        val item = ItemRegistry.item(id) {
            GridCableItem(material, itemsPerMeter)
        }

        materials.add(material, resource(id))
        items.add(material, item)

        return GridRegistryItem(material, item)
    }

    fun getGridConnectOrNull(material: GridMaterial) = items.forward[material]
}

@FunctionalInterface
fun interface GridRendererVertexConsumer {
    fun vertex(
        pX: Float, pY: Float, pZ: Float,
        pRed: Float, pGreen: Float, pBlue: Float,
        pTexU: Float, pTexV: Float,
        pOverlayUV: Int, pLightmapUV: Int,
        pNormalX: Float, pNormalY: Float, pNormalZ: Float,
    )
}

object GridRenderer {
    @JvmStatic
    fun submitForRebuildSection(
        pRenderChunk: ChunkRenderDispatcher.RenderChunk,
        pChunkBufferBuilderPack: ChunkBufferBuilderPack,
        pRenderChunkRegion: RenderChunkRegion,
        pRenderTypeSet: MutableSet<RenderType>,
    ) {
        val renderType = RenderType.solid()
        val vertexConsumer = pChunkBufferBuilderPack.builder(renderType)

        if(pRenderTypeSet.add(renderType)) {
            pRenderChunk.beginLayer(vertexConsumer)
        }

        val lightReader = CachingLightReader(pRenderChunkRegion)
        val neighborLights = NeighborLightReader(lightReader)
        val section = SectionPos.of(pRenderChunk.origin)

        submitSection(
            section,
            lightReader,
            neighborLights
        ) { pX, pY, pZ, pRed, pGreen, pBlue, pTexU, pTexV, pOverlayUV, pLightmapUV, pNormalX, pNormalY, pNormalZ ->
            vertexConsumer.vertex(
                pX, pY, pZ,
                pRed, pGreen, pBlue, 1.0f,
                pTexU, pTexV,
                pOverlayUV, pLightmapUV,
                pNormalX, pNormalY, pNormalZ
            )
        }
    }

    @JvmStatic
    fun submitSection(section: SectionPos, lightReader: CachingLightReader, neighborLights: NeighborLightReader, consumer: GridRendererVertexConsumer) {
        GridConnectionManagerClient.read(section) { material, vertexList, data ->
            val mr = material.vertexColor.rF
            val mg = material.vertexColor.gF
            val mb = material.vertexColor.bF

            val tr = data.tint.rF
            val tg = data.tint.gF
            val tb = data.tint.bF

            val r = mr * tr
            val g = mg * tg
            val b = mb * tb

            val originX = vertexList.originX
            val originY = vertexList.originY
            val originZ = vertexList.originZ

            var i = 0
            val storage = vertexList.storage
            val storageSize = storage.size

            while (i < storageSize) {
                val px = storage.getFloat(i + 0)
                val py = storage.getFloat(i + 1)
                val pz = storage.getFloat(i + 2)
                val nx = storage.getFloat(i + 3)
                val ny = storage.getFloat(i + 4)
                val nz = storage.getFloat(i + 5)
                val u = storage.getFloat(i + 6)
                val v = storage.getFloat(i + 7)
                i += 8

                val blockX = floor(px).toInt() + originX
                val blockY = floor(py).toInt() + originY
                val blockZ = floor(pz).toInt() + originZ
                neighborLights.load(blockX, blockY, blockZ)

                val localLight = lightReader.getLightColor(
                    BlockPos.asLong(blockX, blockY, blockZ)
                )

                val localBlock = unpackBlockLight(localLight)
                val localSky = unpackSkyLight(localLight)

                val blockLight = combineLight(0, neighborLights, nx, ny, nz, localBlock.toDouble())
                val skyLight = combineLight(1, neighborLights, nx, ny, nz, localSky.toDouble())

                val overrideLight = (data.brightnessOverride * 15).toInt().coerceIn(0, 15)

                val light = LightTexture.pack(
                    kotlin.math.max(blockLight, overrideLight),
                    skyLight
                )

                consumer.vertex(
                    px, py, pz,
                    r, g, b,
                    u, v,
                    OverlayTexture.NO_OVERLAY, light,
                    nx, ny, nz
                )
            }
        }
    }
}

data class CableMesh3d(val extrusion: SketchExtrusion, val quads: ArrayList<CableQuad3d>)
data class CableQuad3d(val principal: BlockPos, val vertices: List<CableVertex3d>)
data class CableVertex3d(val position: Vector3d, val normal: Vector3d, val param: Double)

/**
 * Models a cable using an arclength-parameterized catenary ([ArcReparamCatenary3d]) or a straight tube ([LinearSplineSegment3d])
 * @param a First support point.
 * @param b Second support point.
 * @param circleVertices The number of vertices in the mesh of a circle cross-section.
 * @param radius The radius of the cable (for rendering)
 * @param splitDistanceHint The maximum distance between consecutive vertex rings (for rendering to look ~good and to have enough segments to map the texture)
 * @param splitParameterHint The maximum parametric increments between consecutive vertex rings.
 * */
abstract class Cable3dA(
    val a: Vector3d,
    val b: Vector3d,
    val circleVertices: Int,
    val radius: Double,
    val splitDistanceHint: Double = 0.5,
    val splitParameterHint: Double = 0.1,
) {
    /**
     * Gets the circumference of the tube, according to [radius].
     * */
    val circumference get() = 2.0 * PI * radius

    /**
     * Gets the surface area of a cross-section.
     * */
    val crossSectionArea get() = PI * radius * radius

    /**
     * Gets the supports [a] and [b], sorted in ascending order by their vertical coordinate.
     * */
    val supports = listOf(a, b).sortedBy { it.y }

    /**
     * Gets the arc length of the cable.
     * */
    abstract val arcLength: Double

    /**
     * Gets the surface area of the cable.
     * */
    val surfaceArea get() = 2.0 * PI * radius * (arcLength + radius)

    /**
     * Gets the volume of the cable.
     * */
    val volume get() = PI * radius * radius * arcLength

    /**
     * Gets the spline that characterises the wire.
     * */
    abstract val spline: Spline3d

    /**
     * Gets a set of blocks that are intersected by the spline.
     * */
    abstract val blocks: HashSet<BlockPos>

    /**
     * Gets a multimap of blocks intersected by the spline, and the chunks they belong to.
     * */
    abstract val chunks: MultiMap<ChunkPos, BlockPos>

    /**
     * Gets cylindrical segments of the cable.
     * This is funny; we approximate the shape of cylinders with these segments, and here, we approximate the shape of these segments with cylinders.
     * */
    abstract val segments: List<Cylinder3d>

    protected fun sketchCrossSection() = sketchCircle(circleVertices, radius)

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()

        tag.putVector3d(A, a)
        tag.putVector3d(B, b)
        tag.putInt(CIRCLE_VERTICES, circleVertices)
        tag.putDouble(RADIUS, radius)
        tag.putDouble(SPLIT_DISTANCE_HINT, splitDistanceHint)
        tag.putDouble(SPLIT_PARAMETER_HINT, splitParameterHint)

        val type = Type.determine(this)
        tag.putInt(TYPE, type.ordinal)

        when(type) {
            Type.Catenary -> {
                this as Catenary

                tag.putDouble(SLACK, slack)
                tag.putDouble(SPLIT_ROT_INCR_MAX, splitRotIncrementMax)
            }
            Type.Straight -> {
                // empty
            }
        }

        return tag
    }

    abstract fun mesh() : CableMesh3d

    private enum class Type {
        Catenary,
        Straight;

        companion object {
            fun determine(instance: Cable3dA) = when(instance) {
                is Cable3dA.Catenary -> Catenary
                is Cable3dA.Straight -> Straight
                else -> error("Invalid cable 3d implementation $instance")
            }
        }
    }

    companion object {
        private const val A = "a"
        private const val B = "b"
        private const val CIRCLE_VERTICES = "circleVertices"
        private const val RADIUS = "radius"
        private const val TYPE = "type"

        private const val SLACK = "slack"
        private const val SPLIT_DISTANCE_HINT = "splitDistanceHint"
        private const val SPLIT_PARAMETER_HINT = "splitParameterHint"
        private const val SPLIT_ROT_INCR_MAX = "splitRotIncrMax"

        fun fromNbt(tag: CompoundTag) : Cable3dA {
            val a = tag.getVector3d(A)
            val b = tag.getVector3d(B)
            val circleVertices = tag.getInt(CIRCLE_VERTICES)
            val radius = tag.getDouble(RADIUS)
            val splitDistanceHint = tag.getDouble(SPLIT_DISTANCE_HINT)
            val splitParameterHint = tag.getDouble(SPLIT_PARAMETER_HINT)

            return when(Type.entries[tag.getInt(TYPE)]) {
                Type.Catenary -> {
                    Catenary(
                        a, b,
                        circleVertices, radius,
                        splitDistanceHint, splitParameterHint,
                        tag.getDouble(SLACK), tag.getDouble(SPLIT_ROT_INCR_MAX)
                    )
                }
                Type.Straight -> {
                    Straight(
                        a, b,
                        circleVertices, radius,
                        splitDistanceHint, splitParameterHint,
                    )
                }
            }
        }

        private fun getBlocksFromSpline(radius: Double, spline: Spline3d) : HashSet<BlockPos> {
            val blocks = HashSet<BlockPos>()
            val radiusSqr = radius * radius

            require(
                spline.intersectGrid3d(0.0, 1.0, 0.1, 1024 * 1024) {
                    val ordinate = spline.evaluate(it)
                    val block = ordinate.floorBlockPos()

                    if(blocks.add(block)) {
                        for(i in -1..1) {
                            val x = block.x + i

                            for(j in -1..1) {
                                val y = block.y + j

                                for (k in -1..1) {
                                    if(i == 0 && j == 0 && k == 0) {
                                        continue
                                    }

                                    val z = block.z + k

                                    val dx = ordinate.x - ordinate.x.coerceIn(x.toDouble(), x + 1.0)
                                    val dy = ordinate.y - ordinate.y.coerceIn(y.toDouble(), y + 1.0)
                                    val dz = ordinate.z - ordinate.z.coerceIn(z.toDouble(), z + 1.0)

                                    val distanceSqr = dx * dx + dy * dy + dz * dz

                                    if(distanceSqr < radiusSqr) {
                                        blocks.add(BlockPos(x, y, z))
                                    }
                                }
                            }
                        }
                    }

                }
            ) { "Failed to intersect $this" }

            return blocks
        }

        private fun getChunksFromBlocks(blocks: HashSet<BlockPos>) = blocks.associateByMulti { ChunkPos(it) }

        private fun meshExtrusion(spline: Spline3d, extrusion: SketchExtrusion) : CableMesh3d {
            val quads = ArrayList<CableQuad3d>()

            val mesh = extrusion.mesh

            mesh.quadScan { baseQuad ->
                val ptvVerticesParametric = baseQuad.indices.map { mesh.vertices[it] }
                val ptvVerticesPositions = ptvVerticesParametric.map { it.value }

                val ptvCenter = avg(ptvVerticesPositions)
                val ptvParam = avg(ptvVerticesParametric.map { it.t })
                val ordinate = spline.evaluate(ptvParam)
                val ptvNormal = (ptvCenter - ordinate).normalized()
                val ptvNormalWinding = polygralScan(ptvCenter, ptvVerticesPositions).normalized()

                val ptv = if((ptvNormal o ptvNormalWinding) > 0.0) baseQuad
                else baseQuad.rewind()

                fun vert(vertexId: Int) : CableVertex3d {
                    val vertexParametric = mesh.vertices[vertexId]
                    val vertexPosition = vertexParametric.value
                    val vertexNormal = (vertexPosition - (spline.evaluate(vertexParametric.t))).normalized()

                    return CableVertex3d(vertexPosition, vertexNormal, vertexParametric.t)
                }

                val vertices = listOf(vert(ptv.a), vert(ptv.b), vert(ptv.c), vert(ptv.d))

                quads.add(CableQuad3d(ordinate.floorBlockPos(), vertices))
            }

            return CableMesh3d(extrusion, quads)
        }
    }

    /**
     * Models a cable using an arclength-parameterized catenary ([ArcReparamCatenary3d]) or a straight tube ([LinearSplineSegment3d]), if the catenary cannot be used (is degenerate).
     * @param a First support point.
     * @param b Second support point.
     * @param circleVertices The number of vertices in the mesh of a circle cross-section.
     * @param radius The radius of the cable (for rendering)
     * @param splitDistanceHint The maximum distance between consecutive vertex rings (for rendering to look ~good and to have enough segments to map the texture)
     * @param splitParameterHint The maximum parametric increments between consecutive vertex rings.
     * @param slack Cable slack. Arclength will be d(a, b) * (1 + slack).
     * @param splitRotIncrementMax Maximum tangent deviation between consecutive rings.
     * */
    class Catenary(
        a: Vector3d,
        b: Vector3d,
        circleVertices: Int,
        radius: Double,
        splitDistanceHint: Double,
        splitParameterHint: Double,
        val slack: Double,
        val splitRotIncrementMax: Double,
    ) : Cable3dA(a, b, circleVertices, radius, splitDistanceHint, splitParameterHint) {
        override val arcLength: Double
        override val spline: Spline3d
        override val blocks: HashSet<BlockPos>
        override val chunks: MultiMap<ChunkPos, BlockPos>

        /**
         * True, if the connection was represented as a catenary.
         * Otherwise, the connection was represented as a linear segment. This may happen if the catenary is degenerate and cannot be modeled.
         * */
        val isCatenary: Boolean

        init {
            val distance = a..b
            val catenaryLength = distance * (1.0 + slack)

            val catenarySegment = ArcReparamCatenarySegment3d(
                t0 = 0.0,
                t1 = 1.0,
                p0 = supports[0],
                p1 = supports[1],
                length = catenaryLength,
                Vector3d.unitY
            )

            if(catenarySegment.catenary.matchesParameters()) {
                isCatenary = true
                spline = Spline3d(catenarySegment)
                arcLength = catenaryLength // ~approximately
            }
            else {
                isCatenary = false
                spline = Spline3d(
                    LinearSplineSegment3d(
                        t0 = 0.0,
                        t1 = 1.0,
                        p0 = supports[0],
                        p1 = supports[1],
                    )
                )
                arcLength = distance
            }

            blocks = getBlocksFromSpline(radius, spline)
            chunks = getChunksFromBlocks(blocks)
        }

        private val samples = checkNotNull(
            spline.adaptscan(
                0.0,
                1.0,
                splitParameterHint,
                condition = differenceCondition3d(
                    distMax = splitDistanceHint, //min(splitDistanceHint, circumference),
                    rotIncrMax = splitRotIncrementMax
                ),
                iMax = 1024 * 32 // way too generous...
            )
        ) { "Failed to get samples for catenary cable3d $this" }

        override val segments = run {
            if(samples.size < 2) {
                // what?
                DEBUGGER_BREAK()
                return@run emptyList<Cylinder3d>()
            }

            val cylinders = ArrayList<Cylinder3d>(samples.size - 1)

            var previous = spline.evaluate(samples[0])

            for (i in 1 until samples.size) {
                val p0 = previous
                val p1 = spline.evaluate(samples[i])
                previous = p1

                cylinders.add(
                    Cylinder3d(
                        Line3d.fromStartEnd(p0, p1),
                        radius
                    )
                )
            }

            cylinders
        }

        override fun mesh(): CableMesh3d {
            val extrusion = if(isCatenary) {
                extrudeSketchFrenet(
                    sketchCrossSection(),
                    spline,
                    samples
                )
            }
            else {
                Straight.linearExtrusion(
                    sketchCrossSection(),
                    spline,
                    samples,
                    supports
                )
            }

            return meshExtrusion(spline, extrusion)
        }

        override fun toString() =
            "from $a to $b, " +
                "slack=$slack, " +
                "splitDistance=$splitDistanceHint, " +
                "splitParam=$splitParameterHint, " +
                "splitRotIncrMax=$splitRotIncrementMax, " +
                "circleVertices=$circleVertices, " +
                "radius=$radius"
    }

    /**
     * Models a cable using a straight tube ([LinearSplineSegment3d])
     * @param a First support point.
     * @param b Second support point.
     * @param circleVertices The number of vertices in the mesh of a circle cross-section.
     * @param radius The radius of the cable (for rendering)
     * @param splitDistanceHint The maximum distance between consecutive vertex rings (for rendering to look ~good and to have enough segments to map the texture)
     * @param splitParameterHint The maximum parametric increments between consecutive vertex rings.
     * */
    class Straight(
        a: Vector3d,
        b: Vector3d,
        circleVertices: Int,
        radius: Double,
        splitDistanceHint: Double,
        splitParameterHint: Double,
    ) : Cable3dA(a, b, circleVertices, radius, splitDistanceHint, splitParameterHint) {
        override val arcLength: Double
        override val spline: Spline3d
        override val blocks: HashSet<BlockPos>
        override val chunks: MultiMap<ChunkPos, BlockPos>

        init {
            arcLength = a..b

            spline = Spline3d(
                LinearSplineSegment3d(
                    t0 = 0.0,
                    t1 = 1.0,
                    p0 = supports[0],
                    p1 = supports[1],
                )
            )

            blocks = getBlocksFromSpline(radius, spline)
            chunks = getChunksFromBlocks(blocks)
        }

        override val segments = listOf(
            Cylinder3d(
                Line3d.fromStartEnd(a, b),
                radius
            )
        )

        override fun mesh(): CableMesh3d {
            val splitDistanceMax = splitDistanceHint * splitDistanceHint

            val samples = spline.adaptscan(
                0.0,
                1.0,
                splitParameterHint,
                condition = { s, t0, t1 ->
                    val a = s.evaluate(t0)
                    val b = s.evaluate(t1)

                    (a distanceToSqr b) > splitDistanceMax

                },
                iMax = 1024 * 32 // way too generous...
            )

            checkNotNull(samples) {
                "Failed to get samples for linear cable3d $this"
            }

            val extrusion = linearExtrusion(sketchCrossSection(), spline, samples, supports)

            return meshExtrusion(spline, extrusion)
        }

        override fun toString() =
            "from $a to $b, " +
                "splitDistance=$splitDistanceHint, " +
                "splitParam=$splitParameterHint, " +
                "circleVertices=$circleVertices, " +
                "radius=$radius"

        companion object {
            fun linearExtrusion(
                crossSectionSketch: Sketch,
                spline: Spline3d,
                samples: ArrayList<Double>,
                supports: List<Vector3d>,
            ) : SketchExtrusion {
                val t = (supports[1] - supports[0]).normalized()
                val n = t.perpendicular()
                val b = (t x n).normalized()

                val wx = Rotation3d.fromRotationMatrix(
                    Matrix3x3(
                        t, n, b
                    )
                )

                return extrudeSketch(
                    crossSectionSketch,
                    spline,
                    samples,
                    Pose3d(supports[0], wx),
                    Pose3d(supports[1], wx)
                )
            }
        }
    }
}
