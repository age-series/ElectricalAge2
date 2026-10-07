package org.eln2.mc.common.content.processing

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.shapes.Shapes
import net.minecraftforge.registries.ForgeRegistries
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.utils.putUnique
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.LOG
import org.eln2.mc.OnServerThread
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.partTransformation
import org.eln2.mc.common.blocks.foundation.MultipartVisualizationContext
import org.eln2.mc.common.content.modules.Eln2Ingredients
import org.eln2.mc.common.parts.foundation.AbstractPartVisual
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.common.parts.foundation.PartProvider
import org.eln2.mc.common.parts.foundation.PartUseInfo
import org.eln2.mc.common.parts.foundation.TickablePart
import org.eln2.mc.extensions.addItem
import org.eln2.mc.extensions.getDouble
import org.eln2.mc.extensions.getResourceLocation
import org.eln2.mc.extensions.minus
import org.eln2.mc.extensions.vector3d
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.BlockPosInt
import java.util.HashMap
import kotlin.math.PI
import kotlin.random.Random

object TreeExtractionManager : SimpleJsonResourceReloadListener(GsonBuilder().create(), "tree_extraction") {
    private var refs = References(HashMap())

    override fun apply(pObject: Map<ResourceLocation, JsonElement>, pResourceManager: ResourceManager, pProfiler: ProfilerFiller) {
        val results = HashMap<Block, Entry>()

        pObject.forEach { (id, json) ->
            json as JsonObject

            val logBlockId = json.getResourceLocation("logBlock")
            val logBlock = ForgeRegistries.BLOCKS.getValue(logBlockId)
                ?: error(DEBUGGER_BREAK("Invalid log block $logBlockId"))

            val leavesBlockId = json.getResourceLocation("leavesBlock")
            val leavesBlock = ForgeRegistries.BLOCKS.getValue(leavesBlockId)
                ?: error(DEBUGGER_BREAK("Invalid leaves block $leavesBlockId"))

            val minBaseProgress = json.getDouble("minBaseProgress", 0.1)
            val maxBaseProgress = json.getDouble("maxBaseProgress", 0.25)

            val resultItemId = json.getResourceLocation("resultItem")
            val resultItem = ForgeRegistries.ITEMS.getValue(resultItemId)
                ?: error("Invalid result item $resultItemId")

            val entry = Entry(id, logBlock, leavesBlock, minBaseProgress, maxBaseProgress, resultItem)

            results.putUnique(logBlock, entry) {
                "Duplicate tree extraction log block $logBlockId"
            }
        }

        refs = References(results)
    }

    private class References(val entries: Map<Block, Entry>)

    fun getExtractionByLog(log: Block) = refs.entries[log]

    fun getEntries(): Collection<Entry> = refs.entries.values

    /**
     * @param log The log block.
     * @param leaves The leaves block, used to validate the structure of the tree.
     * @param minBaseProgress Minimum progress increment every step.
     * @param maxBaseProgress Maximum progress increment every step.
     * */
    data class Entry(
        val fileId: ResourceLocation,
        val log: Block,
        val leaves: Block,
        val minBaseProgress: Double,
        val maxBaseProgress: Double,
        val resultItem: Item
    )
}

class TreeTapPartProvider : PartProvider() {
    override fun createCore(context: PartPlacementInfo) = TreeTapPart(
        PartCreateInfo(id, context)
    )

    override val placementCollisionSize: Vector3d
        get() = Vector3d.Companion.one

    override fun canPlace(level: Level, substratePos: BlockPos, face: Direction): Boolean {
        return Base6Direction3dMask.Companion.HORIZONTALS.has(face)
    }
}

class TreeTapPart(ci: PartCreateInfo) : Part(ci), TickablePart, ComponentDisplay {
    companion object {
        private const val MIN_INTERVAL = 20 * 3
        private const val MAX_INTERVAL = 20 * 5

        /**
         * The number of logs needed to consider the tree valid.
         * */
        private const val REQUIRED_LOGS = 5

        /**
         * The number of leaves needed to consider the tree valid.
         * */
        private const val REQUIRED_LEAVES = 5

        /**
         * The maximum distance between the collector and any searched blocks.
         * */
        private const val MAX_SPATIAL_DISTANCE = 5

        /**
         * The maximum nodes between the collector and any searched blocks.
         * */
        private const val MAX_GRAPH_DISTANCE = 15

        @OnServerThread
        private val visited = IntOpenHashSet()

        @OnServerThread
        private val queue = LongArrayFIFOQueue()
    }

    private var scanCountdown = 0

    /**
     * On both the client ([handleSyncTag]) and the server.
     * */
    var progress = 0.0
        private set

    init {
        updateShape(
            Shapes.create(
                getModelBoundingBox(Vector3d.Companion.zero, Vector3d(0.5, 0.315, 0.32))
                    .move(0.0, -0.245, 0.0)
            )
        )
    }

    override fun createVisual(ctx: MultipartVisualizationContext) = TreeTapPartVisual(ctx, this)

    override fun onAdded() {
        if(!placement.level.isClientSide) {
            placement.multipart.addTicker(this)
        }
    }

    data class ExtractionInfo(val substratePos: BlockPos, val recipe: TreeExtractionManager.Entry)

    private fun getRecipeFromSubstrate() : ExtractionInfo? {
        val substratePos = placement.position - placement.face
        val substrateBlock = placement.level.getBlockState(substratePos).block
        val recipe = TreeExtractionManager.getExtractionByLog(substrateBlock)
            ?: return null

        return ExtractionInfo(substratePos, recipe)
    }

    /**
     * Scans the substrate block to find the extraction from [TreeExtractionManager].
     * If found, it checks the structure of the tree to make sure it is valid.
     * */
    override fun serverTick() {
        if(progress == 1.0) {
            placement.multipart.markRemoveTicker(this)
            return // Waits for the item to be removed.
        }

        if(--scanCountdown > 0) {
            return
        }

        scanCountdown = Random.nextInt(MIN_INTERVAL, MAX_INTERVAL)

        val (substratePos, recipe) = getRecipeFromSubstrate()
            ?: return

        visited.clear()
        queue.clear()
        queue.enqueue(0L)

        val mutableBlockPos = BlockPos.MutableBlockPos()

        var foundLogs = 0
        var foundLeaves = 0
        var validated = false

        while (!queue.isEmpty) {
            val front = queue.dequeueLong()
            val frontKey = front.toInt()

            val dx = BlockPosInt.unpackX(frontKey)
            val dy = BlockPosInt.unpackY(frontKey)
            val dz = BlockPosInt.unpackZ(frontKey)

            if(dx * dx + dy * dy + dz * dz > MAX_SPATIAL_DISTANCE * MAX_SPATIAL_DISTANCE) {
                continue
            }

            if(!visited.add(frontKey)) {
                continue
            }

            mutableBlockPos.x = dx + substratePos.x
            mutableBlockPos.y = dy + substratePos.y
            mutableBlockPos.z = dz + substratePos.z

            val blockState = placement.level.getBlockState(mutableBlockPos)

            when(blockState.block) {
                recipe.log -> {
                    foundLogs++
                }

                recipe.leaves -> {
                    foundLeaves++
                }

                // Can also check for other taps in the vicinity, to reduce the collection rate.

                else -> {
                    continue
                }
            }

            if(foundLogs >= REQUIRED_LOGS && foundLeaves >= REQUIRED_LEAVES) {
                validated = true
                break
            }

            val graphDistance = 1 + (front shr 32).toInt()

            if(graphDistance > MAX_GRAPH_DISTANCE) {
                continue
            }

            Direction.entries.forEach { direction ->
                val childKey = BlockPosInt.Companion.pack(
                    dx + direction.stepX,
                    dy + direction.stepY,
                    dz + direction.stepZ
                )

                queue.enqueue((graphDistance.toLong() shl 32) or (childKey.toLong() and 0xFFFF_FFFFL))
            }
        }

        if(!validated) {
            return
        }

        progress += Random.Default.nextDouble(recipe.minBaseProgress, recipe.maxBaseProgress)

        if(progress >= 1.0) {
            progress = 1.0
        }

        setSaveDirty()
        setSyncDirty()
    }

    override fun onUsedBy(context: PartUseInfo): InteractionResult {
        if(progress != 1.0) {
            return InteractionResult.FAIL
        }

        val recipe = getRecipeFromSubstrate()?.recipe

        if(recipe == null) {
            LOG.error("Got progress $progress, but the extraction for substrate at ${placement.position} doesn't exist")
            return InteractionResult.FAIL
        }

        if(placement.level.isClientSide) {
            return InteractionResult.PASS
        }

        val level = placement.level as ServerLevel

        val (x, y, z) = placement.mountingPointWorld + placement.face.vector3d * 0.4

        level.addItem(x, y, z, ItemStack(recipe.resultItem, 1))

        progress = 0.0

        setSaveDirty()
        setSyncDirty()

        placement.multipart.addTicker(this)

        return InteractionResult.SUCCESS
    }

    override fun getServerSaveTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putDouble("progress", progress)
        return tag
    }

    override fun loadServerSaveTag(tag: CompoundTag) {
        progress = tag.getDouble("progress")
    }

    override fun getSyncTag() = getServerSaveTag()

    override fun handleSyncTag(tag: CompoundTag) = loadServerSaveTag(tag)

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Countdown: $scanCountdown" }
        builder.progress(progress)
    }
}

class TreeTapPartVisual(visualizationContext: MultipartVisualizationContext, part: TreeTapPart) : AbstractPartVisual<TreeTapPart>(visualizationContext, part),
    SimpleDynamicVisual {
    private val body = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(FlwModels.RUBBER_TAP))
        .createInstance()
        .also {
            it.translateY(-0.4f)
            it.partTransformation(visualizationContext.parent, part)
            it.translateZ(0.5f)
            it.rotateX((-PI / 2.0).toFloat())
        }

    private val latex = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(FlwModels.RUBBER_TAP_LATEX))
        .createInstance()

    private var progress = -1.0

    override fun beginFrame(p0: DynamicVisual.Context?) {
        val targetProgress = part.progress

        if(targetProgress != progress) {
            progress = targetProgress

            latex.setIdentityTransform()

            latex.translateY(-0.4f)
            latex.translateY(progress.toFloat() * 0.1875f)

            latex.partTransformation(visualizationContext.parent, part)

            latex.translateZ(0.5f)
            latex.rotateX((-PI / 2.0).toFloat())

            latex.setChanged()
        }
    }

    override fun updateLight(p0: Float) {
        visualizationContext.parent.relightInstances(body, latex)
    }

    override fun _delete() {
        body.delete()
        latex.delete()
    }
}
