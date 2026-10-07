@file:Suppress("NOTHING_TO_INLINE")

package org.eln2.mc.common

import it.unimi.dsi.fastutil.ints.*
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.GlassBlock
import net.minecraftforge.fml.ModWorkManager
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.registries.ForgeRegistries
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.mathematics.geometry.Ray3d
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.geometry.Vector3di
import org.ageseries.libage.mathematics.geometry.dda
import org.ageseries.libage.utils.putUnique
import org.eln2.mc.*
import org.eln2.mc.common.GhostLightServer.canCreateHandle
import org.eln2.mc.common.network.Networking
import org.eln2.mc.LinearObjectPool
import org.eln2.mc.Locators
import org.eln2.mc.PooledObjectPolicy
import org.eln2.mc.extensions.*
import org.eln2.mc.mathematics.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.function.Supplier
import kotlin.math.*

/**
 * Handles a "ghost light" (block light override) for a single block.
 * */
interface GhostLight {
    /**
     * Gets the world position of the block.
     * */
    val position: BlockPos
    /**
     * Gets the in-chunk [BlockPosInt]
     * */
    val packedPos: Int
    /**
     * Gets the game level.
     * */
    val level: ServerLevel
    /**
     * If true, the block placed at the position is air.
     * */
    val isOpen: Boolean
    /**
     * Sets the lower bound of the brightness at the target block.
     * If multiple handles are modifying this block, the largest value will be applied.
     * */
    fun setBrightness(brightness: Int)
    /**
     * Destroys the handle. This will reset the brightness override, if no other handles are accessing the block.
     * */
    fun destroy()
}

private fun packKey(blockPos: BlockPos): Int {
    val cx = blockPos.x and 0xF
    val cz = blockPos.z and 0xF
    return BlockPosInt.pack(cx, blockPos.y, cz)
}

private fun unpackKey(chunkPos: ChunkPos, i: Int): BlockPos {
    val p = BlockPosInt(i)
    val x = chunkPos.minBlockX + p.x
    val z = chunkPos.minBlockZ + p.z
    return BlockPos(x, p.y, z)
}

// Of course, it is possible to optimize further.
// You can pack the position and light value in 32 bits (because x and z are [0, 15], so you don't need the 10 bits BlockPosInt uses)
// But that probably isn't worth it. The data representation is already pretty good, so the bulk of the overhead will come from light updates

/**
 * Packed light value. The first 32 bits are used for a [BlockPosInt], and the first byte in the second half for the light value.
 * */
@JvmInline
private value class PackedLightVoxel(val data: Long) {
    constructor(posInt: Int, light: Byte) : this(posInt.toLong() or (light.toLong() shl 32))

    val relativePosition get() = BlockPosInt(data.toInt())

    val lightValue get() = (data shr 32).toByte()

    operator fun not() = data
}

enum class GhostLightUpdateType {
    /**
     * Sent when a block is placed, and it closes the ghost light.
     * */
    Closed,
    /**
     * Sent when a block is removed, and it opens the ghost light.
     * */
    Opened
}

/**
 * Event handler for ghost light updates.
 * */
fun interface GhostLightEventHandler {
    fun onUpdate(handle: GhostLight, type: GhostLightUpdateType)
}

class GhostLightCommandMessage(val chunkPos: ChunkPos, val type: Type) {
    enum class Type(val id: Int) {
        BeginTracking(1),
        StopTracking(2);
    }

    companion object {
        fun encode(message: GhostLightCommandMessage, buf: FriendlyByteBuf) {
            buf.writeChunkPos(message.chunkPos)
            buf.writeByte(message.type.id)
        }

        fun decode(buf: FriendlyByteBuf) = GhostLightCommandMessage(
            buf.readChunkPos(),
            when(val id = buf.readByte().toInt()) {
                Type.BeginTracking.id -> Type.BeginTracking
                Type.StopTracking.id -> Type.StopTracking
                else -> error("Unknown ghost light type id $id")
            }
        )

        fun handle(message: GhostLightCommandMessage, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                when(message.type) {
                    Type.BeginTracking -> GhostLightHackClient.addChunk(message.chunkPos)
                    Type.StopTracking -> GhostLightHackClient.removeChunk(message.chunkPos)
                }
            }

            ctx.get().packetHandled = true
        }
    }
}

class GhostLightChunkDataMessage(val data: ByteArray) {
    companion object {
        private const val HEADER_SIZE = Long.SIZE_BYTES
        private const val ENTRY_SIZE = Int.SIZE_BYTES + Byte.SIZE_BYTES

        fun encodeBinary(chunkPos: ChunkPos, values: LongArray) : ByteArray {
            require(values.isNotEmpty())

            val array = ByteArray(HEADER_SIZE + ENTRY_SIZE * values.size)
            val buffer = ByteBuffer.wrap(array)

            buffer.putLong(chunkPos.toLong())

            for (i in values.indices) {
                val value = PackedLightVoxel(values[i])
                buffer.putInt(value.relativePosition.value)
                buffer.put(value.lightValue)
            }

            return array
        }

        fun encodeBinary(chunkPos: ChunkPos, values: Int2ByteOpenHashMap) : ByteArray {
            require(values.isNotEmpty())

            val array = ByteArray(HEADER_SIZE + ENTRY_SIZE * values.size)
            val buffer = ByteBuffer.wrap(array)

            buffer.putLong(chunkPos.toLong())

            for ((relativePosition, lightValue) in values) {
                buffer.putInt(relativePosition)
                buffer.put(lightValue)
            }

            return array
        }

        fun decodeBinary(array: ByteArray) : Pair<ChunkPos, LongArray> {
            require(array.isNotEmpty())
            val buffer = ByteBuffer.wrap(array)

            val chunk = ChunkPos(buffer.long)
            val count = (array.size - HEADER_SIZE) / ENTRY_SIZE
            val result = LongArray(count)

            for (i in 0 until count) {
                val relativePosition = buffer.int
                val lightValue = buffer.get()
                result[i] = !PackedLightVoxel(relativePosition, lightValue)
            }

            require(buffer.remaining() == 0)

            return chunk to result
        }

        fun encode(message: GhostLightChunkDataMessage, buf: FriendlyByteBuf) {
            buf.writeByteArray(message.data)
        }

        fun decode(buf: FriendlyByteBuf): GhostLightChunkDataMessage {
            return GhostLightChunkDataMessage(buf.readByteArray())
        }

        fun handle(message: GhostLightChunkDataMessage, ctx: Supplier<NetworkEvent.Context>) {
            val (chunk, values) = decodeBinary(message.data)

            ctx.get().enqueueWork {
                GhostLightHackClient.commit(chunk, values)
            }

            ctx.get().packetHandled = true
        }
    }
}

/**
 * *Ghost lights* are overrides applied to block light values.
 * */
@ServerOnly
object GhostLightServer {
    private val levels = HashMap<ServerLevel, LevelLightData>()

    private fun validateUsage() {
        requireIsOnServerThread { "Ghost lights server must be on server thread" }
    }

    fun clear() {
        validateUsage()
        levels.clear()
    }

    private fun validateLevel(level: Level): ServerLevel {
        if(level !is ServerLevel) {
            error("Cannot use non server level $level")
        }

        return level
    }

    private fun getLevelData(level: Level) : LevelLightData {
        val serverLevel = validateLevel(level)
        return levels.computeIfAbsent(serverLevel) { LevelLightData(serverLevel) }
    }

    /**
     * Checks if a handle can be created in [level], at the specified [position].
     * @return True, if a handle can be created. Otherwise, false.
     * */
    fun canCreateHandle(level: Level, position: BlockPos): Boolean {
        validateUsage()
        return getLevelData(validateLevel(level)).canCreateHandle(position)
    }

    /**
     * Creates a handle in [level], at the specified [position].
     * @param handler An optional event handler.
     * @return A ghost light handle, if a ghost light can be created (according to [canCreateHandle]). Otherwise, null.
     * */
    fun createHandle(level: Level, position: BlockPos, handler: GhostLightEventHandler? = null): GhostLight? {
        validateUsage()
        return getLevelData(validateLevel(level)).createHandle(position, handler)
    }

    @JvmStatic
    fun createReader(level: Level) : (BlockPos) -> Int {
        validateUsage()

        return getLevelData(level)::getDesiredBrightness
    }

    fun handleBlockEvent(level: Level, position: BlockPos) {
        validateUsage()
        levels[level]?.handleBlockEvent(position)
    }

    fun playerWatch(level: ServerLevel, player: ServerPlayer, chunkPos: ChunkPos) {
        validateUsage()
        getLevelData(level).watch(player, chunkPos)
    }

    fun playerUnwatch(level: ServerLevel, player: ServerPlayer, chunkPos: ChunkPos) {
        validateUsage()
        getLevelData(level).unwatch(player, chunkPos)
    }

    fun applyChanges() {
        validateUsage()
        levels.values.forEach { it.applyChanges() }
    }

    private class LevelLightData(val gameLevel: ServerLevel) {
        companion object {
            private const val LOOKUP_CACHE_SIZE = 4
        }

        private val chunks = Long2ObjectOpenHashMap<LightChunk>()
        private val dirtyChunks = ArrayList<LightChunk>()

        private val lightChunkCache = Array<LightChunk?>(LOOKUP_CACHE_SIZE) { null }
        private val lightChunkPosCache = LongArray(LOOKUP_CACHE_SIZE) { ChunkPos.INVALID_CHUNK_POS }

        private fun clearCache() {
            lightChunkCache.fill(null)
            lightChunkPosCache.fill(ChunkPos.INVALID_CHUNK_POS)
        }

        private fun getOrCreateChunk(chunkPos: ChunkPos) : LightChunk {
            val key = chunkPos.toLong()

            return chunks.computeIfAbsent(key, Long2ObjectFunction {
                LightChunk(chunkPos, this)
            })
        }

        private fun getOrCreateChunk(blockPos: BlockPos) = getOrCreateChunk(ChunkPos(blockPos))

        private fun getExistingChunk(chunkPos: Long) : LightChunk? {
            for (j in 0 until LOOKUP_CACHE_SIZE) {
                if (lightChunkPosCache[j] == chunkPos) {
                    return this.lightChunkCache[j]
                }
            }

            val value = chunks[chunkPos]

            for (i in (LOOKUP_CACHE_SIZE - 1) downTo 1) {
                this.lightChunkPosCache[i] = this.lightChunkPosCache[i - 1]
                this.lightChunkCache[i] = this.lightChunkCache[i - 1]
            }

            this.lightChunkPosCache[0] = chunkPos
            this.lightChunkCache[0] = value

            return value
        }

        fun watch(player: ServerPlayer, chunkPos: ChunkPos) = getOrCreateChunk(chunkPos).startTrackingPlayer(player)

        fun unwatch(player: ServerPlayer, chunkPos: ChunkPos) = getOrCreateChunk(chunkPos).stopTrackingPlayer(player)

        fun canCreateHandle(pos: BlockPos) = gameLevel.isInWorldBounds(pos)

        fun createHandle(pos: BlockPos, notifier: GhostLightEventHandler? = null): GhostLight? {
            if(!canCreateHandle(pos)) {
                return null
            }

            return getOrCreateChunk(pos).createHandle(pos, notifier)
        }

        fun handleBlockEvent(pos: BlockPos) = getExistingChunk(ChunkPos.asLong(pos))?.handleBlockEvent(pos)

        fun getDesiredBrightness(pos: BlockPos) = getExistingChunk(ChunkPos.asLong(pos))?.getBrightness(pos) ?: 0

        fun setDirty(chunk: LightChunk) {
            dirtyChunks.add(chunk)
        }

        fun applyChanges() {
           dirtyChunks.forEach {
               it.applyChanges()
           }

            dirtyChunks.clear()

            clearCache()
        }
    }

    private class LightChunk(val chunkPos: ChunkPos, val lightLevel: LevelLightData) {
        private val cells = Int2ObjectOpenHashMap<Cell>()
        private val players = HashSet<ServerPlayer>()
        private val dirtyCells = IntArrayList()
        private var isDirty = false

        private fun getOrCreateCellWorld(blockPos: BlockPos) : Cell {
            val packedPos = packKey(blockPos)

            val existing = cells.get(packedPos)

            if(existing != null) {
                return existing
            }

            val cell = Cell(blockPos, packedPos)
            require(cells.put(packedPos, cell) == null)
            return cell
        }

        fun createHandle(pos: BlockPos, notifier: GhostLightEventHandler? = null): GhostLight {
            validateUsage()
            return getOrCreateCellWorld(pos).createHandle(notifier)
        }

        fun handleBlockEvent(pos: BlockPos) = cells.get(packKey(pos))?.handleBlockEvent()

        fun getBrightness(pos: BlockPos) = cells.get(packKey(pos))?.targetBrightness ?: 0

        fun startTrackingPlayer(player: ServerPlayer) {
            if(!(players.add(player))) {
                return
            }

            Networking.send(GhostLightCommandMessage(chunkPos, GhostLightCommandMessage.Type.BeginTracking), player)

            val values = Int2ByteOpenHashMap()

            for (cell in cells.values) {
                if(cell.targetBrightness > 0) {
                    values.put(packKey(cell.position), cell.targetBrightness.toByte())
                }
            }

            if(values.isNotEmpty()) {
                Networking.send(GhostLightChunkDataMessage(GhostLightChunkDataMessage.encodeBinary(chunkPos, values)), player)
            }
        }

        fun stopTrackingPlayer(player: ServerPlayer) {
            if(!players.remove(player)) {
                return
            }

            Networking.send(GhostLightCommandMessage(chunkPos, GhostLightCommandMessage.Type.StopTracking), player)
        }

        fun applyChanges() {
            require(isDirty)

            isDirty = false

            val updates = LongArray(dirtyCells.size)

            for (i in dirtyCells.indices) {
                val packedPos = dirtyCells.getInt(i)
                val cell = cells.get(packedPos)

                if(cell == null) {
                    updates[i] = !PackedLightVoxel(packedPos, 0)
                    lightLevel.gameLevel.chunkSource.onLightUpdate(LightLayer.BLOCK, SectionPos.of(unpackKey(chunkPos, packedPos)))
                } else {
                    updates[i] = !PackedLightVoxel(packedPos, cell.targetBrightness.toByte())
                    cell.resetDirty()
                    lightLevel.gameLevel.chunkSource.onLightUpdate(LightLayer.BLOCK, SectionPos.of(cell.position))
                }
            }

            dirtyCells.clear()

            if(updates.isNotEmpty() && players.isNotEmpty()) {
                val binary = GhostLightChunkDataMessage.encodeBinary(chunkPos, updates)
                val message = GhostLightChunkDataMessage(binary)

                players.forEach {
                    Networking.send(message, it)
                }
            }
        }

        private fun setVoxelDirty(packedPosition: Int) {
            dirtyCells.add(packedPosition)
            if(!this@LightChunk.isDirty) {
                this@LightChunk.isDirty = true
                lightLevel.setDirty(this)
            }
        }

        private fun removeCell(cell: Cell) {
            require(cells.remove(cell.packedPosition) == cell)
            // Set dirty. When sending changes, if we find that the cell isn't there, we send 0.
            // This will make the client reset the override.
            setVoxelDirty(cell.packedPosition)
        }

        private inner class Cell(val position: BlockPos, val packedPosition: Int) {
            var targetBrightness = 0
                private set

            private val handles = ArrayList<Handle>()
            private var isDirty = false

            val isOpen get() = lightLevel.gameLevel.getBlockState(position).isAir

            private fun changeHandleBrightness() {
                val brightness = handles.sumOf { it.handleBrightness }.coerceIn(0, 15)

                if(brightness == targetBrightness) {
                    return
                }

                targetBrightness = brightness

                if(!this@Cell.isDirty) {
                    this@Cell.isDirty = true
                    this@LightChunk.setVoxelDirty(packedPosition)
                }
            }

            private fun destroyHandle(handle: Handle) {
                require(handles.remove(handle))

                if (handles.size == 0) {
                    removeCell(this)
                }
                else {
                    changeHandleBrightness()
                }
            }

            fun createHandle(notifier: GhostLightEventHandler?) = Handle(notifier).also {
                handles.add(it)
            }

            fun handleBlockEvent() {
                if(isOpen) {
                    handles.forEach { it.notifier?.onUpdate(it, GhostLightUpdateType.Opened) }
                }
                else {
                    handles.forEach { it.notifier?.onUpdate(it, GhostLightUpdateType.Closed) }
                }
            }

            fun resetDirty() {
                isDirty = false
            }

            private inner class Handle(val notifier: GhostLightEventHandler?) : GhostLight {
                var handleBrightness: Int = 0
                var isDestroyed = false

                override val position: BlockPos get() = this@Cell.position
                override val packedPos: Int get() = this@Cell.packedPosition
                override val level: ServerLevel get() = this@LightChunk.lightLevel.gameLevel
                override val isOpen: Boolean get() = this@Cell.isOpen

                override fun setBrightness(brightness: Int) {
                    if (isDestroyed) {
                        error("Cannot set brightness, handle destroyed!")
                    }

                    if (brightness == this@Handle.handleBrightness) {
                        return
                    }

                    this@Handle.handleBrightness = brightness
                    this@Cell.changeHandleBrightness()
                }

                override fun destroy() {
                    if (!isDestroyed) {
                        this@Cell.destroyHandle(this)
                    }
                }
            }
        }
    }
}

@ClientOnly
object GhostLightHackClient {
    private val grid = ConcurrentHashMap<SectionPos, ByteArray>()
    private val preparedChunks = HashSet<ChunkPos>()

    private val pool = LinearObjectPool(
        object : PooledObjectPolicy<ByteArray> {
            override fun create(): ByteArray {
                return ByteArray(16 * 16 * 16)
            }

            override fun release(obj: ByteArray): Boolean {
                obj.fill(0)
                return true
            }
        },
        16384
    )

    /**
     * Gets the brightness at [blockPos].
     * Safe to call from multiple threads.
     * */
    @CrossThreadAccess
    @JvmStatic
    fun getBlockBrightness(blockPos: BlockPos): Int {
        val section = grid[SectionPos.of(blockPos)]
            ?: return 0

        return section[blockIndex(blockPos.x, blockPos.y, blockPos.z)].toInt()
    }

    /**
     * Prepares storage to handle ghost light data for the chunk at [chunkPos], if the chunk is not already prepared.
     * */
    @OnClientThread
    fun addChunk(chunkPos: ChunkPos) {
        requireIsOnRenderThread {
            "addChunk called on non-render thread"
        }

        if(!preparedChunks.add(chunkPos)) {
            return // Already prepared
        }

        forEachSection(chunkPos) {
            grid.putUnique(it, pool.get())
        }
    }

    /**
     * Removes the data stores for the chunk at [chunkPos], if it exists.
     * */
    @OnClientThread
    fun removeChunk(chunkPos: ChunkPos) {
        requireIsOnRenderThread {
            "removeChunk called on non-render thread"
        }

        if(!preparedChunks.remove(chunkPos)) {
            return
        }

        forEachSection(chunkPos) {
            val section = grid.remove(it)
            check(section != null)
            pool.release(section)
        }
    }

    /**
     * Updates the given stored [PackedLightVoxel] values for the chunk at [chunkPos] and notifies the game in order to update rendering.
     * The chunk must be prepared prior to calling this.
     * */
    fun commit(chunkPos: ChunkPos, values: LongArray) {
        requireIsOnRenderThread {
            "Tried to commit on non-render thread"
        }
        
        require(preparedChunks.contains(chunkPos)) {
            "Tried to commit unprepared $chunkPos"
        }

        val level = checkNotNull(Minecraft.getInstance().level) {
            "Expected level in commit"
        }

        // FIXME
        //val lightUpdater = LightUpdater.get(level)

        val renderer = checkNotNull(Minecraft.getInstance().levelRenderer) {
            "Expected level renderer in commit"
        }

        val changedSections = LongOpenHashSet()

        values.forEach { value ->
            val packedVoxel = PackedLightVoxel(value)

            val chunkRelativePos = packedVoxel.relativePosition

            val sectionPos = SectionPos.of(
                chunkPos.x,
                SectionPos.blockToSectionCoord(chunkRelativePos.y),
                chunkPos.z
            )

            val section = checkNotNull(grid[sectionPos]) {
                "Expected pre-loaded section at $sectionPos"
            }

            val index = sectionIndex(
                chunkRelativePos.x,
                SectionPos.sectionRelative(chunkRelativePos.y),
                chunkRelativePos.z
            )

            section[index] = packedVoxel.lightValue

            changedSections.add(sectionPos.asLong())
        }

        changedSections.forEach {
            val section = SectionPos.of(it)
            renderer.setSectionDirtyWithNeighbors(section.x, section.y, section.z)

            // flywheel$onLightUpdate -> VisualizationManagerImpl#onLightUpdate
            level.chunkSource.onLightUpdate(LightLayer.BLOCK, section)
        }
    }

    fun clear() {
        grid.values.forEach {
            pool.release(it)
        }

        grid.clear()
        preparedChunks.clear()
    }

    private fun forEachSection(chunkPos: ChunkPos, use: (SectionPos) -> Unit) {
        val level = checkNotNull(Minecraft.getInstance().level) {
            "Level is null in column"
        }

        SectionPos.betweenClosedStream(
            chunkPos.x,
            level.minSection,
            chunkPos.z,
            chunkPos.x,
            level.maxSection,
            chunkPos.z
        ).forEach(use)
    }

    private fun blockIndex(x: Int, y: Int, z: Int) = sectionIndex(
        SectionPos.sectionRelative(x),
        SectionPos.sectionRelative(y),
        SectionPos.sectionRelative(z)
    )

    private fun sectionIndex(x: Int, y: Int, z: Int) = x + 16 * (y + 16 * z)
}

private val originPositionField = Vector3d(0.5, 0.5, 0.5)

/**
 * Traverses the light ray from [x], [y], [z] to the origin (0.5, 0.5, 0.5), and calls [user] with the voxel cell coordinates (minimal coordinates)
 * The origin is not included in the traversal.
 * */
private inline fun lightRayDDA(x: Int, y: Int, z: Int, user: (Int, Int, Int) -> Boolean) {
    if(x == 0 && y == 0 && z == 0) {
        error("Cannot traverse from origin to origin")
    }

    val sourcePositionField = Vector3d(
        x + 0.5,
        y + 0.5,
        z + 0.5
    )

    val ray = Ray3d.fromSourceAndDestination(sourcePositionField, originPositionField)

    dda(ray, withSource = true) { i, j, k ->
        if (i == 0 && j == 0 && k == 0) {
            // Reached origin
            return@dda false
        }

        return@dda user(i, j, k)
    }
}

private inline fun rayOcclusionAnalyzer(voxel: Int, predicate: (Int) -> Boolean) : IntOpenHashSet {
    val minX = BlockPosInt.unpackX(voxel).toDouble()
    val minY = BlockPosInt.unpackY(voxel).toDouble()
    val minZ = BlockPosInt.unpackZ(voxel).toDouble()
    val maxX = minX + 1.0
    val maxY = minY + 1.0
    val maxZ = minZ + 1.0

    val queue = IntArrayFIFOQueue()
    val results = IntOpenHashSet()

    queue.enqueue(voxel)

    while (!queue.isEmpty) {
        val front = queue.dequeueInt()

        if (!results.add(front) || !predicate(front)) {
            continue
        }

        val x = BlockPosInt.unpackX(front)
        val y = BlockPosInt.unpackY(front)
        val z = BlockPosInt.unpackZ(front)

        for (i in 0..5) {
            val nx = x + directionIncrementX(i)
            val ny = y + directionIncrementY(i)
            val nz = z + directionIncrementZ(i)

            val cx = nx + 0.5
            val cy = ny + 0.5
            val cz = nz + 0.5

            val k = -1.0 / sqrt((nx * nx + ny * ny + nz * nz).toDouble())

            val dx = 1.0 / (nx * k)
            val dy = 1.0 / (ny * k)
            val dz = 1.0 / (nz * k)

            val a = (minX - cx) * dx
            val b = (maxX - cx) * dx
            val c = (minY - cy) * dy
            val d = (maxY - cy) * dy
            val e = (minZ - cz) * dz
            val f = (maxZ - cz) * dz

            val tMin = max(max(min(a, b), min(c, d)), min(e, f))
            val tMax = min(min(max(a, b), max(c, d)), max(e, f))

            if (tMax >= 0.0 && (tMax - tMin) > 0.0) {
                queue.enqueue(BlockPosInt.pack(nx, ny, nz))
            }
        }
    }

    return results
}

/**
 * Implements a [LocatorLightVolumeProvider] parameterized by face.
 * It provides one light volume per orientation of the emitter; presumably, the light volume itself is an oriented shape.
 * */
class FaceOrientedLightVolumeProvider(val volumesByFace: Map<Direction, LightVolume>) : LocatorLightVolumeProvider {
    override fun getVolume(locatorSet: Locator): LightVolume {
        val face = locatorSet.requireLocator(Locators.SUBSTRATE_FACE) {
            "Face-oriented lights require a face locator"
        }

        return volumesByFace[face] ?: error("Oriented light volume did not have $face")
    }

    companion object {
        fun createWithoutCopy(variantsByState: Map<Direction, Map<Int, Int2ByteMap>>) = FaceOrientedLightVolumeProvider(
            variantsByState.keys.associateWith {
                LightVolume.createWitoutCopy(variantsByState[it]!!)
            }
        )
    }
}

object LightFieldPrimitives {
    fun coneContentOnly(
        increments: Int,
        strength: Double,
        deviationMax: Double,
        baseRadius: Int
    ): FaceOrientedLightVolumeProvider {
        check(!ModEvents.isFullyLoaded) {
            "Cannot use coneContentOnly now"
        }

        val (tasks, results) = coneTasks(increments, strength, deviationMax, baseRadius)
        val executor = ModWorkManager.parallelExecutor()

        LOG.warn("ELN2 Computing cone with $increments increments, $strength strength with ${tasks.size} tasks")

        val tasksWrapped = tasks.map {
            val latch = CountDownLatch(1)

            executor.execute {
                it.run()
                latch.countDown()
            }

            latch
        }

        tasksWrapped.forEach {
            it.await()
        }

        return FaceOrientedLightVolumeProvider.createWithoutCopy(results)
    }

    private fun coneTasks(
        increments: Int,
        strength: Double,
        deviationMax: Double,
        baseRadius: Int
    ): Pair<List<Runnable>, HashMap<Direction, HashMap<Int, Int2ByteOpenHashMap>>> {
        val variantsByFace = HashMap<Direction, HashMap<Int, Int2ByteOpenHashMap>>()
        val cosDeviationMax = cos(deviationMax)

        val tasks = ArrayList<Runnable>()

        Direction.entries.forEach { face ->
            val baseVectors = let {
                val results = ArrayList<Vector3d>()

                if(baseRadius <= 0) {
                    results.add(Vector3d.zero)
                }
                else {
                    when(face.axis) {
                        Direction.Axis.X -> {
                            for (y in -baseRadius..baseRadius) {
                                for (z in -baseRadius..baseRadius) {
                                    results.add(Vector3d(0.0, y.toDouble(), z.toDouble()))
                                }
                            }
                        }
                        Direction.Axis.Y -> {
                            for (x in -baseRadius..baseRadius) {
                                for (z in -baseRadius..baseRadius) {
                                    results.add(Vector3d(x.toDouble(), 0.0, z.toDouble()))
                                }
                            }
                        }
                        Direction.Axis.Z -> {
                            for (x in -baseRadius..baseRadius) {
                                for (y in -baseRadius..baseRadius) {
                                    results.add(Vector3d(x.toDouble(), y.toDouble(), 0.0))
                                }
                            }
                        }
                        else -> error("Invalid axis ${face.axis}")
                    }
                }

                results
            }

            val normal = face.vector3d

            val variants = HashMap<Int, Int2ByteOpenHashMap>()
            variantsByFace.putUnique(face, variants)

            for (state in 0..increments) {
                tasks.add(Runnable {
                    val fieldBase = Int2DoubleOpenHashMap()
                    val results = Int2ByteOpenHashMap(fieldBase.size)

                    synchronized(variants) {
                        variants.putUnique(state, results)
                    }

                    val radius = strength * (state / increments.toDouble())
                    val radiusUpper = ceil(radius).toInt()

                    if(radiusUpper != 0) {
                        fun set(x: Int, y: Int, z: Int) {
                            val cell = Vector3d(x, y, z)

                            for(baseVector in baseVectors) {
                                val distance = cell distanceTo baseVector

                                if(!distance.approxEq(0.0)) {
                                    if((((cell - baseVector) / distance) cosAngle normal) < cosDeviationMax) {
                                        continue
                                    }
                                }

                                if(distance <= radiusUpper) {
                                    val fz = when(face.axis) {
                                        Direction.Axis.X -> cell.x - baseVector.x
                                        Direction.Axis.Y -> cell.y - baseVector.y
                                        Direction.Axis.Z -> cell.z - baseVector.z
                                        else -> error("Invalid axis ${face.axis}")
                                    }.absoluteValue

                                    val brightness = 15.0 * (1.0 - (fz / radius))

                                    if(brightness >= 1.0) {
                                        val k = BlockPosInt.pack(x, y, z)
                                        fieldBase.put(k, max(fieldBase.get(k), brightness))
                                    }
                                }
                            }
                        }

                        when(face) {
                            Direction.DOWN -> {
                                for (x in -radiusUpper..radiusUpper) {
                                    for (y in -radiusUpper..0) {
                                        for (z in -radiusUpper..radiusUpper) {
                                            set(x, y, z)
                                        }
                                    }
                                }
                            }
                            Direction.UP -> {
                                for (x in -radiusUpper..radiusUpper) {
                                    for (y in 0..radiusUpper) {
                                        for (z in -radiusUpper..radiusUpper) {
                                            set(x, y, z)
                                        }
                                    }
                                }
                            }
                            Direction.NORTH -> {
                                for (x in -radiusUpper..radiusUpper) {
                                    for (y in -radiusUpper..radiusUpper) {
                                        for (z in -radiusUpper..0) {
                                            set(x, y, z)
                                        }
                                    }
                                }
                            }
                            Direction.SOUTH -> {
                                for (x in -radiusUpper..radiusUpper) {
                                    for (y in -radiusUpper..radiusUpper) {
                                        for (z in 0..radiusUpper) {
                                            set(x, y, z)
                                        }
                                    }
                                }
                            }
                            Direction.WEST -> {
                                for (x in -radiusUpper..0) {
                                    for (y in -radiusUpper..radiusUpper) {
                                        for (z in -radiusUpper..radiusUpper) {
                                            set(x, y, z)
                                        }
                                    }
                                }
                            }
                            Direction.EAST -> {
                                for (x in 0..radiusUpper) {
                                    for (y in -radiusUpper..radiusUpper) {
                                        for (z in -radiusUpper..radiusUpper) {
                                            set(x, y, z)
                                        }
                                    }
                                }
                            }
                        }

                        lateralPass(face, fieldBase)

                        if(fieldBase.size > 0) {
                            for ((k, v) in fieldBase) {
                                results.put(k, round(v).toInt().coerceIn(0, 15).toByte())
                            }
                        }
                    }
                })
            }
        }

        return tasks to variantsByFace
    }

    private fun lateralPass(face: Direction, grid: Int2DoubleOpenHashMap) {
        val axis = face.axis

        val increment = Vector3di(face.stepX, face.stepY, face.stepZ)
        var columnPosition = Vector3di.zero

        val queue = IntArrayFIFOQueue()
        val plane = IntOpenHashSet()

        while (true) {
            val kColumn = BlockPosInt.pack(columnPosition)

            if(grid.get(kColumn) < 1) {
                break
            }

            var radiusSqr = 0
            var minBrightness = Double.MAX_VALUE

            queue.enqueue(kColumn)

            while (queue.size() > 0) {
                val front = queue.dequeueInt()
                val brightness = grid.get(front)

                if(brightness < 1) {
                    continue
                }

                if(!plane.add(front)) {
                    continue
                }

                if(brightness < minBrightness) {
                    minBrightness = brightness
                }

                val x = BlockPosInt.unpackX(front)
                val y = BlockPosInt.unpackY(front)
                val z = BlockPosInt.unpackZ(front)

                val dx = x - columnPosition.x
                val dy = y - columnPosition.y
                val dz = z - columnPosition.z

                radiusSqr = max(radiusSqr, dx * dx + dy * dy + dz * dz)

                when(axis) {
                    Direction.Axis.X -> {
                        queue.enqueue(BlockPosInt.pack(x, y + 1, z))
                        queue.enqueue(BlockPosInt.pack(x, y - 1, z))
                        queue.enqueue(BlockPosInt.pack(x, y, z + 1))
                        queue.enqueue(BlockPosInt.pack(x, y, z - 1))
                    }
                    Direction.Axis.Y -> {
                        queue.enqueue(BlockPosInt.pack(x + 1, y, z))
                        queue.enqueue(BlockPosInt.pack(x - 1, y, z))
                        queue.enqueue(BlockPosInt.pack(x, y, z + 1))
                        queue.enqueue(BlockPosInt.pack(x, y, z - 1))
                    }
                    Direction.Axis.Z -> {
                        queue.enqueue(BlockPosInt.pack(x + 1, y, z))
                        queue.enqueue(BlockPosInt.pack(x - 1, y, z))
                        queue.enqueue(BlockPosInt.pack(x, y + 1, z))
                        queue.enqueue(BlockPosInt.pack(x, y - 1, z))
                    }
                    else -> error("Invalid axis $axis")
                }
            }

            plane.remove(kColumn)

            val radius = sqrt(radiusSqr.toDouble())

            val iterator = plane.iterator()
            while (iterator.hasNext()) {
                val k = iterator.nextInt()

                val dx = columnPosition.x - BlockPosInt.unpackX(k)
                val dy = columnPosition.y - BlockPosInt.unpackY(k)
                val dz = columnPosition.z - BlockPosInt.unpackZ(k)

                val distance = sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                grid.put(k, grid.get(k) * (1.0 - (distance / radius).coerceIn(0.0, 1.0)))
            }

            plane.clear()

            columnPosition += increment
        }
    }

    private inline fun iterateSphere(radius: Int, use: (x: Int, y: Int, z: Int) -> Unit) {
        if(radius <= 0) {
            return
        }

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    use(x, y, z)
                }
            }
        }
    }

    private inline fun iterateHemisphere(radius: Int, normal: Direction, start: Int = 0, use: (x: Int, y: Int, z: Int) -> Unit) {
        if(radius <= 0) {
            return
        }

        if(start >= radius) {
            return
        }

        when(normal) {
            Direction.DOWN -> {
                for (x in -radius..radius) {
                    for (y in -radius..-start) {
                        for (z in -radius..radius) {
                            use(x, y, z)
                        }
                    }
                }
            }
            Direction.UP -> {
                for (x in -radius..radius) {
                    for (y in start..radius) {
                        for (z in -radius..radius) {
                            use(x, y, z)
                        }
                    }
                }
            }
            Direction.NORTH -> {
                for (x in -radius..radius) {
                    for (y in -radius..radius) {
                        for (z in -radius..-start) {
                            use(x, y, z)
                        }
                    }
                }
            }
            Direction.SOUTH -> {
                for (x in -radius..radius) {
                    for (y in -radius..radius) {
                        for (z in start..radius) {
                            use(x, y, z)
                        }
                    }
                }
            }
            Direction.WEST -> {
                for (x in -radius..-start) {
                    for (y in -radius..radius) {
                        for (z in -radius..radius) {
                            use(x, y, z)
                        }
                    }
                }
            }
            Direction.EAST -> {
                for (x in start..radius) {
                    for (y in -radius..radius) {
                        for (z in -radius..radius) {
                            use(x, y, z)
                        }
                    }
                }
            }
        }
    }

    private fun buildIntVolume(range: IntRange, build: (state: Int, map: Int2ByteOpenHashMap) -> Unit) : LightVolume {
        val variants = HashMap<Int, Int2ByteOpenHashMap>()

        for (state in range) {
            val map = Int2ByteOpenHashMap()
            build(state, map)
            variants.putUnique(state, map)
        }

        return LightVolume(variants)
    }

    private fun combineIntVolumes(volumes: List<LightVolume>) : LightVolume {
        var increment = 0
        val map = HashMap<Int, Int2ByteMap>()

        volumes.forEach {
            for (i in 0..it.stateIncrements) {
                map.putUnique(increment++, it.getLightField(i))
            }
        }

        return LightVolume(map)
    }

    private fun buildPerFaceVolumes(faces: Base6Direction3dMask, range: IntRange, build: (state: Int, face: Direction, map: Int2ByteOpenHashMap) -> Unit) : FaceOrientedLightVolumeProvider {
        val variantsByFace = HashMap<Direction, LightVolume>()

        faces.directionList.forEach {
            variantsByFace.putUnique(
                it,
                buildIntVolume(range) { state, map ->
                    build(state, it, map)
                }
            )
        }

        return FaceOrientedLightVolumeProvider(variantsByFace)
    }

    fun sourceOnlyStart(incrementsMax: Int) = buildIntVolume(0..min(15, incrementsMax)) { state, map ->
        map[BlockPosInt.zero] = state.toByte()
    }

    private fun sphereIntensity(x: Int, y: Int, z: Int, radius: Double, map: Int2ByteMap) {
        val cell = Vector3d(x, y, z)

        val distanceSqr = cell.normSqr

        if(distanceSqr <= radius * radius) {
            val brightness = 15.0 * (1.0 - (cell.norm / radius))

            if(brightness >= 1.0) {
                map.putUnique(
                    BlockPosInt.pack(x, y, z),
                    brightness.toInt().coerceIn(0, 15).toByte()
                )
            }
        }
    }

    fun sphere(increments: Int, strength: Double, radiusStart: Double = 0.0, skipOrigin: Boolean = false) = ConstantLightVolumeProvider(
        buildIntVolume(0..increments) { state, map ->
            val radius = radiusStart + strength * (state / increments.toDouble())
            val radiusUpper = ceil(radius).toInt()

            iterateSphere(radiusUpper) { x, y, z ->
                if (x == 0 && y == 0 && z == 0 && skipOrigin) {
                    map.putUnique(BlockPosInt.zero, 0)
                } else {
                    sphereIntensity(x, y, z, radius, map)
                }
            }
        }
    )

    fun hemisphere(increments: Int, strength: Double, normal: Direction, radiusStart: Double = 0.0, start: Int = 0, skipOrigin: Boolean = false) = ConstantLightVolumeProvider(
        buildIntVolume(0..increments) { state, map ->
            val radius = radiusStart + strength * (state / increments.toDouble())
            val radiusUpper = ceil(radius).toInt()

            iterateHemisphere(radiusUpper, normal, start) { x, y, z ->
                if (x == 0 && y == 0 && z == 0 && skipOrigin) {
                    map.putUnique(BlockPosInt.zero, 0)
                } else {
                    sphereIntensity(x, y, z, radius, map)
                }
            }
        }
    )

    fun sphereIncremental(increments: Int, strength: Double, skipOrigin: Boolean = false) : ConstantLightVolumeProvider {
        val volumes = mutableListOf<LightVolume>()

        if(!skipOrigin) {
            volumes.add(sourceOnlyStart(increments))
        }

        if(increments > 15) {
            volumes.add(
                sphere(
                    increments - 15,
                    strength,
                    1.0,
                    skipOrigin
                ).volume
            )
        }

        return ConstantLightVolumeProvider(
            combineIntVolumes(volumes)
        )
    }

    fun hemisphereIncremental(increments: Int, strength: Double, normal: Direction, start: Int = 0, skipOrigin: Boolean = false) : ConstantLightVolumeProvider {
        val volumes = mutableListOf<LightVolume>()

        if(!skipOrigin) {
            volumes.add(sourceOnlyStart(increments))
        }

        if(increments > 15) {
            volumes.add(
                hemisphere(
                    increments - 15,
                    strength,
                    normal,
                    1.0,
                    start,
                    skipOrigin
                ).volume
            )
        }

        return ConstantLightVolumeProvider(
            combineIntVolumes(volumes)
        )
    }
}

/**
 * Represents the state of a light emitter.
 * */
interface LightView {
    /**
     * Gets the (raw) electrical power. This may be negative, depending on polarity.
     * */
    val power: Double
    /**
     * Gets the (raw) electrical current. This may be negative, depending on polarity.
     * */
    val current: Double
    /**
     * Gets the (raw) electrical potential across the resistor. This may be negative, depending on polarity.
     * */
    val potential: Double
    /**
     * Gets the life parameter (0-1).
     * */
    val life: Double
    /**
     * Gets the latest model temperature, obtained from the [LightTemperatureFunction].
     * */
    val modelTemperature: Double
    /**
     * Gets the latest state increment.
     * */
    val volumeState: Int
}

/**
 * Computes the light "temperature". This equates to how strong the light is, based on emitter state (e.g. based on power input)
 * */
fun interface LightTemperatureFunction {
    fun computeTemperature(view: LightView): Double
}

/**
 * Computes a damage increment, based on emitter state.
 * */
fun interface LightDamageFunction {
    fun computeDamage(view: LightView, dt: Double): Double
}

/**
 * Computes resistance, based on emitter state.
 * */
fun interface LightResistanceFunction {
    fun computeResistance(view: LightView): Quantity<Resistance>
}

/**
 * Provider for a [LightVolume], parameterized on the spatial configuration of the light emitter.
 * */
fun interface LocatorLightVolumeProvider {
    fun getVolume(locatorSet: Locator) : LightVolume
}

data class ConstantLightVolumeProvider(val volume: LightVolume) : LocatorLightVolumeProvider, Supplier<LightVolume> {
    override fun getVolume(locatorSet: Locator) = volume

    override fun get() = volume
}

/**
 * *Light Volume* (light voxel data) function. This function maps a state increment (light "brightness") to the
 * desired light voxels.
 * */
class LightVolume private constructor(private val variants: Map<Int, Int2ByteMap>, private val mask: IntSet) {
   companion object {
       fun createImmutableStorage(source: Map<Int, Map<Int, Byte>>) : Int2ObjectOpenHashMap<Int2ByteMap> {
           val result = Int2ObjectOpenHashMap<Int2ByteMap>()

           source.forEach { (k, v) ->
               result.put(k, Int2ByteMaps.unmodifiable(Int2ByteOpenHashMap(v)))
           }

           return result
       }

       fun createMask(source: Map<Int, Map<Int, Byte>>) : IntSet {
           val mask = IntOpenHashSet()

           source.values.forEach {
               it.forEach { (k, _) ->
                   mask.add(k)
               }
           }

           return IntSets.unmodifiable(mask)
       }

       fun createWitoutCopy(source: Map<Int, Int2ByteMap>) = LightVolume(source, createMask(source))
   }

    /**
     * Gets the number of "state increments" (number of variants of the light field when the emitter is **powered**)
     * The *state* is given by the [LightTemperatureFunction] (whose co-domain is 0-1).
     * The temperature gets mapped to a discrete state (represented as an integer), and will be in the range [0, [stateIncrements]].
     * This means *[stateIncrements] + 1* states should be handled, the state "0" corresponding to the light when temperature is 0.
     * */
    val stateIncrements: Int

    init {
        require(variants.isNotEmpty()) {
            "Variant map was empty"
        }

        for (i in 0 until variants.size) {
            require(variants.containsKey(i)) {
                "Variant map did not have state increment $i"
            }
        }

        stateIncrements = variants.size - 1
    }

    constructor(variantsByState: Map<Int, Map<Int, Byte>>) : this(
        createImmutableStorage(variantsByState),
        createMask(variantsByState)
    )

    /**
     * Gets the desired light field, based on the current state of the light source.
     * */
    fun getLightField(state: Int) : Int2ByteMap = variants[state] ?: error("Did not have variant $state")

    // In the future, maybe implement an algorithm to generate the mask, that fills in gaps (this would allow light fields with holes)

    /**
     * Gets a set of positions, that includes the positions of all cells (in all states).
     * */
    fun getMask() : IntSet = mask
}

/**
 * Describes the behavior of a light source, and holds the baked light volumes.
 * @param volumeProvider45Deg Volume constrained to a 45 degree cone.
 * @param volumeProviderSphere Volume not constrained (spherical).
 * */
data class LightModel(
    val temperatureFunction: LightTemperatureFunction,
    val resistanceFunction: LightResistanceFunction,
    val damageFunction: LightDamageFunction,
    val volumeProvider45Deg: LocatorLightVolumeProvider,
    val volumeProviderSphere: LocatorLightVolumeProvider
) {
    fun getVolumeProvider(variant: LightVariantType) = when(variant) {
        LightVariantType.Cone45Deg -> volumeProvider45Deg
        LightVariantType.Sphere -> volumeProviderSphere
    }
}

/**
 * Event sent when the state increment changes.
 * @param volume The current volume in use.
 * @param targetState The new state increment.
 * */
data class VolumetricLightChangeEvent(val volume: LightVolume, val targetState: Int) : Event

/**
 * Event sent when the life parameter reaches 0.
 * */
object LightBurnedOutEvent : Event

/**
 * Consumer for the raw temperature of the light.
 * The results are presumably sent over the network.
 * */
fun interface LightTemperatureConsumer {
    fun consume(temperature: Double)
}

interface LightBulbEmitterView {
    var life: Double
    var lightBulb: LightBulbItem?

    /**
     * Called to reset the state of the light emitter. States that should be reset are:
     * - The model temperature
     * - The volume state increment and the volume
     * - The life
     * - The light bulb item
     * */
    fun resetValues()
}


enum class LightVariantType {
    Cone45Deg,
    Sphere
}

class LightBulbItem(
    val model: LightModel) : Item(Properties()) {
    companion object {
        private const val LIFE = "life"
        private const val ID = "id"

        fun fromNbtWithState(tag: CompoundTag) : Pair<LightBulbItem, Double>? {
            var result: Pair<LightBulbItem, Double>? = null

            if(tag.contains(ID)) {
                ForgeRegistries.ITEMS.getValue(tag.getResourceLocation(ID))?.also {
                    if(it is LightBulbItem) {
                        result = Pair(
                            it,
                            tag.getDouble(LIFE)
                        )
                    }
                }
            }

            return result
        }
    }

    fun toNbtWithState(life: Double) = CompoundTag().also {
        ForgeRegistries.ITEMS.getKey(this)?.also { itemId ->
            it.putResourceLocation(ID, itemId)
            it.putDouble(LIFE, life)
        }
    }

    fun createStack(life: Double, count: Int = 1): ItemStack {
        val stack = ItemStack(this, count)

        stack.tag = CompoundTag().also {
            it.putDouble(LIFE, life)
        }

        return stack
    }

    fun getLife(stack: ItemStack): Double {
        return stack.tag?.getDouble(LIFE) ?: 0.0
    }

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)

        val life = getLife(pStack)

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.light_bulb.life")
                .append(": ")
                .append(Component.literal(life.formattedPercentNormalized()))
        )
    }
}

class LightVolumeInstance(val level: ServerLevel, val placementPosition: BlockPos) {
    private val lightCells = Int2ObjectOpenHashMap<Light>()

    var volume: LightVolume? = null
        private set

    var stateIncrement: Int = 0
        private set

    private fun getPositionWorld(x: Int, y: Int, z: Int) = BlockPos(
        placementPosition.x + x,
        placementPosition.y + y,
        placementPosition.z + z
    )

    /**
     * Checks if the block at [position] can transmit a ray.
     * @param position The position to check, in the world frame.
     * @return True, if the block at the specified [position] can transmit the ray. Otherwise, false.
     * */
    private fun transmitsRayWorld(position: BlockPos): Boolean {
        requireIsOnServerThread()

        val state = level.getBlockState(position)

        if(state.isAir) {
            return true
        }

        if(state.block is GlassBlock) {
            return true
        }

        if(!state.isCollisionShapeFullBlock(level, position)) {
            return true
        }

        return false
    }

    fun isTransition(targetVolume: LightVolume, targetIncrement: Int) : Boolean = this.volume != targetVolume || this.stateIncrement != targetIncrement

    /**
     * Checks out the [targetVolume] and its [targetIncrement]th state increment.
     * @return True, if any changes were made. Otherwise, false.
     * */
    fun checkoutState(targetVolume: LightVolume, targetIncrement: Int) : Boolean {
        requireIsOnServerThread {
            "Cannot update light volume instance outside of server thread"
        }

        if (!isTransition(targetVolume, targetIncrement)) {
            return false
        }

        val actualField: Int2ByteMap

        if(this.volume == null) {
            // First use:
            this.volume = targetVolume
            actualField = Int2ByteMaps.EMPTY_MAP // Handle weird case where state 0 is not empty
        }
        else if(this.volume != targetVolume) {
            error("Expected same volume without reset")
        }
        else {
            actualField = targetVolume.getLightField(this.stateIncrement)
        }

        val targetField = targetVolume.getLightField(targetIncrement)
        val newCells : IntSet

        if(actualField.isNotEmpty()) {
            newCells = IntOpenHashSet(targetField.keys)

            val actualFieldIterator = actualField.keys.intIterator()
            while (actualFieldIterator.hasNext()) {
                val k = actualFieldIterator.nextInt()

                newCells.remove(k)

                val newBrightness = targetField.getOrDefault(k, 0)

                val cell = lightCells[k]
                    ?: continue // Out of bounds?

                if(newBrightness < 1) {
                    // No longer present in target brightness field:
                    cell.updateBrightness(0)
                } else {
                    // Present in both, updated:
                    cell.updateBrightness(newBrightness.toInt())
                }
            }
        }
        else {
            newCells = targetField.keys
        }

        val newCellsIterator = newCells.intIterator()
        while (newCellsIterator.hasNext()) {
            val k = newCellsIterator.nextInt()
            val positionWorld = placementPosition + BlockPosInt.unpackBlockPos(k)

            if(!GhostLightServer.canCreateHandle(level, positionWorld)) {
                continue
            }

            var cell = lightCells.get(k)

            if(cell == null) {
                cell = Light(this, k, positionWorld)
                require(lightCells.put(k, cell) == null)
            }

            cell.updateBrightness(targetField.get(k).toInt())
        }

        this.stateIncrement = targetIncrement

        return true
    }

    fun fullReset() {
        destroyCells()
        volume = null
        stateIncrement = 0
    }

    @ServerOnly
    @OnServerThread
    fun destroyCells() {
        requireIsOnServerThread { "Cannot destroy light cells outside of server thread" }

        for (it in lightCells.values) {
            it.destroy()
        }

        lightCells.clear()
    }

    class Light(val instance: LightVolumeInstance, private val positionField: Int, positionWorld: BlockPos) {
        private val handle = GhostLightServer.createHandle(instance.level, positionWorld) { _, type ->
            onUpdate(type)
        }!!

        /**
         * Set of all voxel cells that are obstructing the ray.
         * These positions are in the volume frame.
         * */
        private val obstructingBlocks = IntOpenHashSet()
        private val isObstructed get() = obstructingBlocks.size > 0
        private val isOrigin get() = BlockPosInt(positionField).isZero

        init {
            // Initially, we intersect all blocks in the world, to find the blocks occluding the ray.
            // Then, we'll keep our state updated on events.

            if(!isOrigin) {
                lightRayDDA(
                    BlockPosInt.unpackX(positionField),
                    BlockPosInt.unpackY(positionField),
                    BlockPosInt.unpackZ(positionField)
                ) { x, y, z ->
                    if (!instance.transmitsRayWorld(instance.getPositionWorld(x, y, z))) {
                        // But we store in the volume frame as 1 int, it is way more efficient:
                        obstructingBlocks.add(BlockPosInt.pack(x, y, z))
                    }

                    true
                }
            }
        }

        private var desiredBrightness = 0

        fun updateBrightness(brightness: Int) {
            if(desiredBrightness == brightness) {
                return
            }

            desiredBrightness = brightness

            if(!isObstructed) {
                // Immediately set light, because we are not obstructed:
                handle.setBrightness(brightness)
            }
        }

        private fun onUpdate(type: GhostLightUpdateType) {
            if(isOrigin) {
                // Weird to get updates here, ignore.
                LOG.warn("Got light update at origin")
                return
            }

            // If this block is not an obstruction, it doesn't affect us:
            if(type == GhostLightUpdateType.Closed && instance.transmitsRayWorld(handle.position)) {
                return
            }

            // First of all, we'll get the light cells whose rays intersect this voxel. That includes this one too!

            val mask = instance.volume!!.getMask()

            val intersectionsVolume = rayOcclusionAnalyzer(positionField) { k ->
                mask.contains(k)
            }

            LOG.debug("Intersected ${intersectionsVolume.size}")

            val iterator = intersectionsVolume.intIterator()

            when(type) {
                GhostLightUpdateType.Closed -> {
                    // Add to obstructions list and update:
                    while (iterator.hasNext()) {
                        val cell = instance.lightCells[iterator.nextInt()] ?: continue
                        cell.obstructingBlocks.add(positionField)
                        // If we added one, it automatically means the cell is blocked:
                        cell.handle.setBrightness(0)
                    }
                }
                GhostLightUpdateType.Opened -> {
                    // Remove from list of obstructions and, if empty, set light:
                    while (iterator.hasNext()) {
                        val cell = instance.lightCells[iterator.nextInt()] ?: continue

                        if(cell.obstructingBlocks.remove(positionField)) {
                            if(!cell.isObstructed) {
                                cell.handle.setBrightness(cell.desiredBrightness)
                            }
                        }
                    }
                }
            }
        }

        fun destroy() {
            handle.destroy()
        }
    }

    companion object {
        fun loadLightFromBulb(
            instance: LightVolumeInstance,
            emitter: LightBulbEmitterView,
            stack: ItemStack
        ) : LightLoadResult {
            var result = LightLoadResult.Fail

            val existingItem = emitter.lightBulb

            if(existingItem != null) {
                instance.fullReset()
                instance.level.addItem(instance.placementPosition, existingItem.createStack(emitter.life))
                emitter.resetValues()

                result = LightLoadResult.RemoveExisting
            }
            else {
                if(stack.count > 0 && stack.item is LightBulbItem) {
                    instance.fullReset()

                    val item = stack.item as LightBulbItem
                    emitter.resetValues()
                    emitter.life = item.getLife(stack)
                    emitter.lightBulb = item

                    result = LightLoadResult.AddNew
                }
            }

            return result
        }
    }
}

enum class LightLoadResult {
    RemoveExisting,
    AddNew,
    Fail
}
