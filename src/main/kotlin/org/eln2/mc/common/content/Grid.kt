@file:Suppress("NOTHING_TO_INLINE")

package org.eln2.mc.common.content

import it.unimi.dsi.fastutil.doubles.Double2DoubleOpenHashMap
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
import net.minecraft.nbt.ListTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkEvent
import org.ageseries.libage.data.MutableMapPairBiMap
import org.ageseries.libage.data.MutableSetMapMultiMap
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.*
import org.eln2.mc.client.render.*
import org.eln2.mc.client.render.foundation.*
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.common.parts.foundation.PartRenderer
import org.eln2.mc.data.DataContainer
import org.eln2.mc.data.Locator
import org.eln2.mc.data.ResistanceField
import org.eln2.mc.data.data
import org.eln2.mc.mathematics.*
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier
import kotlin.collections.HashSet
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.PI
import kotlin.math.min

class GridMaterial(private val spriteSupplier: Lazy<TextureAtlasSprite>, val vertexColor: RGBFloat) {
    val id get() = GridMaterials.getId(this)

    val sprite get() = spriteSupplier.value
}

object GridMaterials {
    private val materials = MutableMapPairBiMap<GridMaterial, ResourceLocation>()

    val NEUTRAL_AS_RUBBER = register(
        "neutral_rubber",
        GridMaterial(
            Sprites.NEUTRAL_CABLE,
            RGBFloat(0.2f, 0.2f, 0.2f)
        )
    )

    val NEUTRAL_AS_STEEL = register(
        "neutral_steel",
        GridMaterial(
            Sprites.NEUTRAL_CABLE,
            RGBFloat(0.9f, 0.9f, 0.9f)
        )
    )

    val COPPER = register(
        "copper",
        GridMaterial(
            Sprites.COPPER_CABLE,
            RGBFloat(1f, 1f, 1f)
        )
    )

    fun register(id: ResourceLocation, material: GridMaterial) = material.also { materials.add(material, id) }
    private fun register(id: String, material: GridMaterial) = register(resource(id), material)
    fun getId(material: GridMaterial) : ResourceLocation = materials.forward[material] ?: error("Failed to get grid material id $material")
    fun getMaterial(resourceLocation: ResourceLocation) : GridMaterial = materials.backward[resourceLocation] ?: error("Failed to get grid material $resourceLocation")
}

data class GridConnectionCatenary(val id: Int, val wireCatenary: CatenaryCable3d, val material: GridMaterial) {
    constructor(catenary: CatenaryCable3d, material: GridMaterial) : this(getUniqueId(), catenary, material)

    fun toNbt() = CompoundTag().also {
        it.putInt(ID, id)
        it.put(CATENARY, wireCatenary.toNbt())
        it.putResourceLocation(MATERIAL, GridMaterials.getId(material))
    }

    companion object {
        private const val ID = "id"
        private const val CATENARY = "catenary"
        private const val MATERIAL = "material"

        fun fromNbt(tag: CompoundTag) = GridConnectionCatenary(
            tag.getInt(ID),
            CatenaryCable3d.fromNbt(tag.get(CATENARY) as CompoundTag),
            GridMaterials.getMaterial(tag.getResourceLocation(MATERIAL))
        )
    }
}

interface GridConnectionHandle {
    val connection: GridConnectionCatenary
    val level: ServerLevel

    fun destroy()
}

// Required that attachment and locator do not change if ID does not change
class GridEndpointInfo(val id: UUID, val attachment: Vector3d, val locator: Locator) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GridEndpointInfo

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun toNbt() = CompoundTag().also {
        it.putUUID(NBT_ID, id)
        it.putVector3d(NBT_ATTACHMENT, attachment)
        it.putLocatorSet(NBT_LOCATOR, locator)
    }

    companion object {
        private const val NBT_ID = "id"
        private const val NBT_ATTACHMENT = "attachment"
        private const val NBT_LOCATOR = "locator"

        fun fromNbt(tag: CompoundTag) = GridEndpointInfo(
            tag.getUUID(NBT_ID),
            tag.getVector3d(NBT_ATTACHMENT),
            tag.getLocatorSet(NBT_LOCATOR)
        )
    }
}

class GridConnectionPair private constructor(val a: GridEndpointInfo, val b: GridEndpointInfo) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GridConnectionPair

        if (a != other.a) return false
        if (b != other.b) return false

        return true
    }

    override fun hashCode(): Int {
        var result = a.hashCode()
        result = 31 * result + b.hashCode()
        return result
    }

    fun toNbt() = CompoundTag().also {
        it.putVector3d(A_ATTACHMENT, a.attachment)
        it.putVector3d(B_ATTACHMENT, b.attachment)
        it.putUUID(A_UUID, a.id)
        it.putUUID(B_UUID, b.id)
        it.putLocatorSet(A_LOCATOR, a.locator)
        it.putLocatorSet(B_LOCATOR, b.locator)
    }

    companion object {
        private const val A_ATTACHMENT = "attachmentA"
        private const val B_ATTACHMENT = "attachmentB"
        private const val A_UUID = "uuidA"
        private const val B_UUID = "uuidB"
        private const val A_LOCATOR = "locatorA"
        private const val B_LOCATOR = "locatorB"

        fun create(a: GridEndpointInfo, b: GridEndpointInfo) : GridConnectionPair {
            require(a.id != b.id) { "End points $a and $b have same UUID ${a.id}"}

            return if(a.id < b.id) {
                GridConnectionPair(a, b)
            }
            else {
                GridConnectionPair(b, a)
            }
        }

        fun fromNbt(tag: CompoundTag) = GridConnectionPair(
            GridEndpointInfo(
                tag.getUUID(A_UUID),
                tag.getVector3d(A_ATTACHMENT),
                tag.getLocatorSet(A_LOCATOR)
            ),
            GridEndpointInfo(
                tag.getUUID(B_UUID),
                tag.getVector3d(B_ATTACHMENT),
                tag.getLocatorSet(B_LOCATOR)
            )
        )
    }
}

data class GridConnectionCreateMessage(val connection: GridConnectionCatenary) {
    companion object {
        fun encode(message: GridConnectionCreateMessage, buf: FriendlyByteBuf) {
            buf.writeNbt(message.connection.toNbt())
        }

        fun decode(buf: FriendlyByteBuf) = GridConnectionCreateMessage(
            GridConnectionCatenary.fromNbt(buf.readNbt()!!)
        )

        fun handle(message: GridConnectionCreateMessage, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                GridConnectionManagerClient.addConnection(message.connection)
            }

            ctx.get().packetHandled = true
        }
    }
}

data class GridConnectionDeleteMessage(val id: Int) {
    companion object {
        fun encode(message: GridConnectionDeleteMessage, buf: FriendlyByteBuf) {
            buf.writeInt(message.id)
        }

        fun decode(buf: FriendlyByteBuf) = GridConnectionDeleteMessage(
            buf.readInt()
        )

        fun handle(message: GridConnectionDeleteMessage, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                GridConnectionManagerClient.removeConnection(message.id)
            }

            ctx.get().packetHandled = true
        }
    }
}

@ServerOnly
object GridConnectionManagerServer {
    fun createGridCatenary(pair: GridConnectionPair, material: GridMaterial) = GridConnectionCatenary(
        CatenaryCable3d(
            pair.a.attachment,
            pair.b.attachment
        ),
        material
    )

    private val levels = HashMap<ServerLevel, LevelGridData>()

    private fun validateUsage() {
        requireIsOnServerThread { "Grid server must be on server thread" }
    }

    fun clear() {
        validateUsage()
        levels.clear()
    }

    private inline fun<T> invoke(level: ServerLevel, action: LevelGridData.() -> T) : T {
        validateUsage()

        val data = levels.computeIfAbsent(level) {
            LevelGridData(level)
        }

        return action.invoke(data)
    }

    fun createHandle(level: ServerLevel, connection: GridConnectionCatenary) : GridConnectionHandle = invoke(level) {
        createHandle(connection)
    }

    fun playerWatch(level: ServerLevel, player: ServerPlayer, chunkPos: ChunkPos) = invoke(level) {
        watch(player, chunkPos)
    }

    fun playerUnwatch(level: ServerLevel, player: ServerPlayer, chunkPos: ChunkPos) = invoke(level) {
        unwatch(player, chunkPos)
    }

    fun createPairIfAbsent(level: ServerLevel, pair: GridConnectionPair, material: GridMaterial) : Boolean = invoke(level) {
        createPairIfAbsent(pair, material)
    }

    fun createPair(level: ServerLevel, pair: GridConnectionPair, connection: GridConnectionCatenary) = invoke(level) {
        createPair(pair, connection)
    }

    fun removeEndpointById(level: ServerLevel, endpointId: UUID) = invoke(level) {
        removeEndpointById(endpointId)
    }

    fun clipsBlock(level: ServerLevel, blockPos: BlockPos) : Boolean = invoke(level) {
        clips(blockPos)
    }

    class LevelGridData(val level: ServerLevel) {
        private val handles = HashSet<Handle>()
        private val handlesByChunk = MutableSetMapMultiMap<ChunkPos, Handle>()
        private val watchedChunksByPlayer = MutableSetMapMultiMap<ServerPlayer, ChunkPos>()

        private val pairMap = PairMap()
        private val handlesByPair = HashMap<GridConnectionPair, GridConnectionHandle>()

        fun watch(player: ServerPlayer, chunkPos: ChunkPos) {
            if(watchedChunksByPlayer[player].add(chunkPos)) {
                handlesByChunk[chunkPos].forEach { handle ->
                    if(handle.addPlayer(player, chunkPos)) {
                        sendConnection(player, handle)
                    }
                }
            }
        }

        fun unwatch(player: ServerPlayer, chunkPos: ChunkPos) {
            if(watchedChunksByPlayer[player].remove(chunkPos)) {
                handlesByChunk[chunkPos].forEach { handle ->
                    handle.removePlayer(player, chunkPos)
                }
            }
        }

        private fun sendConnection(player: ServerPlayer, handle: Handle) {
            Networking.send(GridConnectionCreateMessage(handle.connection), player)
        }

        private fun sendDeletedConnection(player: ServerPlayer, handle: Handle) {
            Networking.send(GridConnectionDeleteMessage(handle.connection.id), player)
        }

        fun clips(blockPos: BlockPos) : Boolean {
            for (handle in handlesByChunk[ChunkPos(blockPos)]) {
                if(handle.connection.wireCatenary.blocks.contains(blockPos)) {
                    return true
                }
            }

            return false
        }

        fun createHandle(connection: GridConnectionCatenary) : GridConnectionHandle {
            val handle = Handle(connection)

            handles.add(handle)

            connection.wireCatenary.chunks.keys.forEach { chunkPos ->
                handlesByChunk[chunkPos].add(handle)
            }

            watchedChunksByPlayer.map.forEach { (player, playerWatchedChunks) ->
                val intersectedChunks = HashSet<ChunkPos>()

                for (catenaryChunk in connection.wireCatenary.chunks.keys) {
                    if(playerWatchedChunks.contains(catenaryChunk)) {
                        intersectedChunks.add(catenaryChunk)
                    }
                }

                if(intersectedChunks.isNotEmpty()) {
                    sendConnection(player, handle)

                    intersectedChunks.forEach { playerChunk ->
                        handle.addPlayer(player, playerChunk)
                    }
                }
            }

            return handle
        }

        fun createPair(pair: GridConnectionPair, connection: GridConnectionCatenary) {
            pairMap.addPair(pair)
            handlesByPair[pair] = createHandle(connection)
        }

        fun createPairIfAbsent(pair: GridConnectionPair, material: GridMaterial) : Boolean {
            if(pairMap.hasPair(pair)) {
                return false
            }

            createPair(pair, createGridCatenary(pair, material))

            return true
        }

        fun removeEndpointById(endPointId: UUID) {
            pairMap.removePairsById(endPointId).forEach { pair ->
                handlesByPair.remove(pair)!!.destroy()
            }
        }

        private inner class Handle(override val connection: GridConnectionCatenary) : GridConnectionHandle {
            private val players = MutableSetMapMultiMap<ServerPlayer, ChunkPos>()

            fun addPlayer(player: ServerPlayer, chunkPos: ChunkPos) : Boolean {
                val result = !players.contains(player)
                players[player].add(chunkPos)
                return result
            }

            fun removePlayer(player: ServerPlayer, chunkPos: ChunkPos) = players.remove(player, chunkPos)

            override val level: ServerLevel
                get() = this@LevelGridData.level

            override fun destroy() {
                validateUsage()

                if(handles.remove(this)) {
                    connection.wireCatenary.chunks.keys.forEach { chunk ->
                        handlesByChunk[chunk].remove(this)
                    }

                    players.keys.forEach { player ->
                        sendDeletedConnection(player, this)
                    }
                }
            }
        }

        private class PairMap {
            val pairs = HashSet<GridConnectionPair>()
            val pairsByEndpoint = MutableSetMapMultiMap<GridEndpointInfo, GridConnectionPair>()
            val endpointsByEndpointId = HashMap<UUID, GridEndpointInfo>()

            private fun putId(endpoint: GridEndpointInfo) {
                val existing = endpointsByEndpointId.put(endpoint.id, endpoint)

                if(existing != null) {
                    require(existing == endpoint) {
                        "Duplicate end point $existing $endpoint"
                    }
                }
            }

            private fun takeId(endpoint: GridEndpointInfo) {
                if(!pairsByEndpoint.contains(endpoint)) {
                    require(endpointsByEndpointId.remove(endpoint.id) == endpoint) {
                        "Failed to remove end point $endpoint"
                    }
                }
            }

            fun hasPair(pair: GridConnectionPair) = pairs.contains(pair)

            fun addPair(pair: GridConnectionPair) {
                check(pairs.add(pair))
                pairsByEndpoint[pair.a].add(pair)
                pairsByEndpoint[pair.b].add(pair)
                putId(pair.a)
                putId(pair.b)
            }

            fun removePair(pair: GridConnectionPair) {
                check(pairs.remove(pair))
                pairsByEndpoint[pair.a].remove(pair)
                pairsByEndpoint[pair.b].remove(pair)
                takeId(pair.a)
                takeId(pair.b)
            }

            fun getPairs(endPoint: GridEndpointInfo) = pairsByEndpoint[endPoint].toList()

            fun removePairsById(endpointId: UUID) : List<GridConnectionPair> {
                val endPoint = endpointsByEndpointId[endpointId] ?: return emptyList()

                val pairs = getPairs(endPoint)

                pairs.forEach {
                    removePair(it)
                }

                return pairs
            }
        }
    }
}

@ClientOnly
object GridConnectionManagerClient {
    private val lock = ReentrantReadWriteLock()
    private val slicesByConnection = MutableSetMapMultiMap<Int, ConnectionSectionSlice>()
    private val slicesBySection = MutableSetMapMultiMap<SectionPos, ConnectionSectionSlice>()

    fun clear() {
        lock.write {
            slicesByConnection.map.clear()
            slicesBySection.map.clear()
        }
    }

    private fun scanUProgression(extrusion: SketchExtrusion, catenary: CatenaryCable3d, u0: Double, u1: Double) : Double2DoubleOpenHashMap {
        var p0 = extrusion.rmfProgression.first()
        var arcLength = 0.0
        val uCoordinates = Double2DoubleOpenHashMap(extrusion.rmfProgression.size)

        extrusion.rmfProgression.forEach { p1 ->
            arcLength += (p0.value.translation .. p1.value.translation)

            val coordinate = if(arcLength.mod(catenary.circumference * 2.0) > catenary.circumference) {
                map(
                    arcLength.mod(catenary.circumference),
                    0.0, catenary.circumference,
                    u1, u0
                )
            }
            else {
                map(
                    arcLength.mod(catenary.circumference),
                    0.0, catenary.circumference,
                    u0, u1
                )
            }

            uCoordinates.put(p1.t, coordinate)

            p0 = p1
        }

        return uCoordinates
    }

    private fun setDirty(sectionPos: SectionPos) {
        Minecraft.getInstance().levelRenderer.setSectionDirty(
            sectionPos.x,
            sectionPos.y,
            sectionPos.z
        )
    }

    fun addConnection(connection: GridConnectionCatenary) {
        val sections = HashSet<SectionPos>()

        lock.write {
            val catenary = connection.wireCatenary
            val (extrusion, quads) = catenary.mesh()
            LOG.info("Generated ${quads.size} quads")
            val sprite = connection.material.sprite

            val uCoordinates = scanUProgression(
                extrusion,
                catenary,
                sprite.u0.toDouble(),
                sprite.u1.toDouble()
            )

            val v0 = sprite.v0.toDouble()
            val v1 = sprite.v1.toDouble()

            val quadsBySection = HashMap<SectionPos, ConnectionSectionSlice>()

            quads.forEach { quad ->
                val quadPos = quad.primitiveCenter.floorBlockPos()
                val processedQuad = Quad()

                quad.vertices.forEachIndexed { vertexIndex, vertex ->
                    val orientation = extrusion.rmfLookup.get(vertex.param)!!.rotation
                    val phase = vertex.normal angle orientation.invoke().c2

                    val u = uCoordinates.get(vertex.param)
                    val v = map(phase, -PI, PI, v0, v1)

                    processedQuad.positions[vertexIndex] = vertex.position
                    processedQuad.normals[vertexIndex] = vertex.normal
                    processedQuad.uvs[vertexIndex] = Pair(u.toFloat(), v.toFloat())
                }

                val sectionPos = SectionPos.of(quadPos)

                val sectionData = quadsBySection.computeIfAbsent(sectionPos) {
                    ConnectionSectionSlice(connection.material, sectionPos)
                }

                sectionData.quads.add(processedQuad)
                sectionData.blocks.add(quadPos)
            }

            quadsBySection.forEach { (sectionPos, sectionSlice) ->
                slicesByConnection[connection.id].add(sectionSlice)
                slicesBySection[sectionPos].add(sectionSlice)
                sections.add(sectionPos)
            }
        }

        sections.forEach {
            setDirty(it)
        }
    }

    fun removeConnection(id: Int) {
        lock.write {
            slicesByConnection[id].forEach { slice ->
                slicesBySection[slice.sectionPos].remove(slice)
                setDirty(slice.sectionPos)
            }

            slicesByConnection.clear(id)
        }
    }

    fun read(sectionPos: SectionPos, user: (GridMaterial, Quad) -> Unit) {
        lock.read {
            slicesBySection[sectionPos].forEach { slice ->
                slice.quads.forEach { quad ->
                    user(slice.material, quad)
                }
            }
        }
    }

    fun containsRange(pStart: BlockPos, pEnd: BlockPos) : Boolean {
        var result = false

        val i = SectionPos.blockToSectionCoord(pStart.x)
        val j = SectionPos.blockToSectionCoord(pStart.y)
        val k = SectionPos.blockToSectionCoord(pStart.z)
        val l = SectionPos.blockToSectionCoord(pEnd.x)
        val m = SectionPos.blockToSectionCoord(pEnd.y)
        val n = SectionPos.blockToSectionCoord(pEnd.z)
        // ~8 sections per range
        lock.read {
            for(sectionPos in SectionPos.betweenClosedStream(i, j, k, l, m, n)) {
                if(slicesBySection.contains(sectionPos)) {
                    result = true
                    break
                }
            }
        }

        return result
    }

    fun clipsBlock(blockPos: BlockPos) : Boolean {
        var result = false

        lock.read {
            for (slice in slicesBySection[SectionPos.of(blockPos)]) {
                if(slice.blocks.contains(blockPos)) {
                    result = true
                    break
                }
            }
        }

        return result
    }

    // todo optimize storage

    class Quad {
        val positions = Array(4) { Vector3d.zero }
        val normals = Array(4) { Vector3d.zero }
        val uvs = Array(4) { Pair(0f, 0f) }
    }

    private class ConnectionSectionSlice(val material: GridMaterial, val sectionPos: SectionPos) {
        val quads = ArrayList<Quad>()
        val blocks = HashSet<BlockPos>()
    }
}

class GridElectricalObject(cell: GridCell) : ElectricalObject(cell), DataContainer {
    private val resistor = ComponentHolder {
        Resistor().also { it.resistance = resistanceExact }
    }

    /**
     * Gets the exact resistance of the [resistor].
     * */
    var resistanceExact: Double = 1.0
        set(value) {
            field = value
            resistor.ifPresent { it.resistance = value }
        }

    fun updateResistance(value: Double, eps: Double = LIBAGE_SET_EPS): Boolean {
        if(resistanceExact.approxEq(value, eps)) {
            return false
        }

        resistanceExact = value

        return true
    }

    override fun offerComponent(neighbour: ElectricalObject) =
        if (neighbour is GridElectricalObject) resistor.offerInternal()
        else resistor.offerExternal()

    override fun clearComponents() {
        resistor.clear()
    }

    override fun addComponents(circuit: Circuit) {
        circuit.add(resistor)
    }

    override val dataNode = data {
        it.withField(ResistanceField {
            resistanceExact
        })
    }
}

class GridCell(ci: CellCreateInfo) : Cell(ci) {
    @SimObject @Inspect
    val grid = GridElectricalObject(this)

    // Same grid:
    val endPoints = HashMap<GridEndpointInfo, GridMaterial>()

    var endpointId: UUID = UUID.randomUUID()
        private set

    override fun onRemoving() {
        requireIsOnServerThread { // Maybe we'll have such a situation in the future...
            "OnRemoving grid is not on the server thread"
        }

        endPoints.keys.forEach { remoteEndPoint ->
            val remoteCell = graph.getCellByLocator(remoteEndPoint.locator)

            remoteCell as GridCell

            val localEndPoint = remoteCell.endPoints.keys.firstOrNull { it.id == this.endpointId }
                .requireNotNull { "Failed to solve grid ${this.endPoints} $this" }

            check(remoteCell.endPoints.remove(localEndPoint) != null)
        }

        GridConnectionManagerServer.removeEndpointById(graph.level, this.endpointId)
    }

    override fun saveCellData() = CompoundTag().also {
        it.putUUID(ENDPOINT_ID, endpointId)

        val endpointList = ListTag()

        endPoints.forEach { (remoteEndPoint, material) ->
            val endpointCompound = CompoundTag()
            endpointCompound.put(REMOTE_END_POINT, remoteEndPoint.toNbt())
            endpointCompound.putResourceLocation(MATERIAL, material.id)
            endpointList.add(endpointCompound)
        }

        it.put(REMOTE_END_POINTS, endpointList)
    }

    override fun loadCellData(tag: CompoundTag) {
        endpointId = tag.getUUID(ENDPOINT_ID)

        tag.getListTag(REMOTE_END_POINTS).forEachCompound { endpointCompound ->
            val remoteEndPoint = GridEndpointInfo.fromNbt(endpointCompound.get(REMOTE_END_POINT) as CompoundTag)
            val material = GridMaterials.getMaterial(endpointCompound.getResourceLocation(MATERIAL))
            endPoints.putUnique(remoteEndPoint, material)
        }
    }

    override fun onLoadedFromDisk() {
        endPoints.keys.forEach { remoteEndPoint ->
            if(!graph.containsCellByLocator(remoteEndPoint.locator)) {
                LOG.error("Invalid end point $remoteEndPoint") // Break point here
            }
        }
    }

    companion object {
        private const val ENDPOINT_ID = "endpointId"
        private const val REMOTE_END_POINT = "remoteEndPoint"
        private const val MATERIAL = "material"
        private const val REMOTE_END_POINTS = "remoteEndPoints"
    }
}

abstract class GridCellPart<R : PartRenderer>(
    id: ResourceLocation,
    placement: PartPlacementInfo,
    provider: CellProvider<GridCell>
) : CellPart<GridCell, R>(id, placement, provider) {
    // Attachment in the fixed frame:

    open val attachment: Vector3d = placement.position.toVector3d() + Vector3d(0.5)
    var stagingCell: GridCell? = null

    fun createEndpointInfo() = GridEndpointInfo(
        cell.endpointId, attachment, cell.locator
    )

    override fun addExtraConnections(results: MutableSet<CellNeighborInfo>) {
        if(stagingCell != null) {
            results.add(CellNeighborInfo.of(stagingCell!!))
        }

        if(cell.hasGraph) {
            cell.endPoints.keys.forEach { remoteEndPoint ->
                results.add(
                    CellNeighborInfo.of(
                        cell.graph.getCellByLocator(remoteEndPoint.locator)
                    )
                )
            }
        }
    }

    override fun onLoaded() {
        super.onLoaded()

        if(!placement.level.isClientSide) {
            // Cell is available now:
            val localEndPoint = createEndpointInfo()

            placement.level as ServerLevel

            cell.endPoints.forEach { (remoteEndPoint, material) ->
                val pair = GridConnectionPair.create(localEndPoint, remoteEndPoint)

                GridConnectionManagerServer.createPairIfAbsent(
                    placement.level,
                    pair,
                    material
                )
            }
        }
    }
}

class GridTapPart(
    id: ResourceLocation,
    placement: PartPlacementInfo,
    provider: CellProvider<GridCell>
) : GridCellPart<BasicPartRenderer>(id, placement, provider) {
    override val partSize: Vec3
        get() = Vec3(1.0, 1.0, 1.0)

    override fun createRenderer(): BasicPartRenderer {
        return BasicPartRenderer(this, PartialModels.GROUND)
    }
}

open class GridConnectItem(val material: GridMaterial) : Item(Properties()) {
    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val actualStack = pPlayer.getItemInHand(pUsedHand)

        fun tell(text: String) {
            if(text.isNotEmpty()) {
                pPlayer.sendSystemMessage(Component.literal(text))
            }
        }

        fun fail(reason: String = "") : InteractionResultHolder<ItemStack> {
            actualStack.tag = null
            tell(reason)
            return InteractionResultHolder.fail(actualStack)
        }

        fun success(message: String = "") : InteractionResultHolder<ItemStack> {
            actualStack.tag = null
            tell(message)
            return InteractionResultHolder.success(actualStack)
        }

        if (pLevel.isClientSide) {
            return fail()
        }

        val hit = getPlayerPOVHitResult(pLevel, pPlayer, ClipContext.Fluid.SOURCE_ONLY)

        if (hit.type != HitResult.Type.BLOCK) {
            return fail("Cannot connect that!")
        }
        // make generic grid game object
        val targetMultipart = pLevel.getBlockEntity(hit.blockPos) as? MultipartBlockEntity
            ?: return fail("Cannot connect that!")

        val targetPart = targetMultipart.pickPart(pPlayer) as? GridCellPart<*>
            ?: return fail("No valid part selected")

        if (actualStack.tag != null && actualStack.tag!!.contains(NBT_POS) && actualStack.tag!!.contains(NBT_FACE)) {
            val remoteMultipart = pLevel.getBlockEntity(actualStack.tag!!.getBlockPos(NBT_POS)) as? MultipartBlockEntity
                ?: return fail("Cannot connect that!")

            val remotePart = remoteMultipart.getPart(actualStack.tag!!.getDirection(NBT_FACE)) as? GridCellPart<*>
                ?: return fail("No valid part selected")

            if(remotePart == targetPart) {
                return fail("Can't connect an endpoint with itself")
            }

            if(targetPart.cell.endPoints.keys.any { it.id == remotePart.cell.endpointId }) {
                check(remotePart.cell.endPoints.keys.any { it.id == targetPart.cell.endpointId })
                return fail("Can't do that!")
            }
            else {
                check(remotePart.cell.endPoints.keys.none { it.id == targetPart.cell.endpointId })
            }

            val pair = GridConnectionPair.create(
                targetPart.createEndpointInfo(),
                remotePart.createEndpointInfo()
            )

            val gridCatenary = GridConnectionManagerServer.createGridCatenary(pair, material)

            val startBlockPos = gridCatenary.wireCatenary.a.floorBlockPos()
            val endBlockPos = gridCatenary.wireCatenary.b.floorBlockPos()

            for (blockPos in gridCatenary.wireCatenary.blocks) {
                if(blockPos == startBlockPos || blockPos == endBlockPos) {
                    continue
                }

                val state = pLevel.getBlockState(blockPos)
                if(!state.isAir) {
                    return fail("Block $state is in the way at $blockPos")
                }
            }

            CellConnections.disconnectCell(targetPart.cell, targetPart.placement.multipart, false)
            targetPart.stagingCell = remotePart.cell
            remotePart.stagingCell = targetPart.cell
            CellConnections.connectCell(targetPart.cell, targetPart.placement.multipart)

            targetPart.stagingCell = null
            remotePart.stagingCell = null

            require(targetPart.cell.graph == remotePart.cell.graph) {
                "Grid staging failed"
            }

            targetPart.cell.endPoints.putUnique(remotePart.createEndpointInfo(), material)
            remotePart.cell.endPoints.putUnique(targetPart.createEndpointInfo(), material)

            GridConnectionManagerServer.createPair(pLevel as ServerLevel, pair, gridCatenary)

            return success("Connected successfully!")
        }

        actualStack.tag = CompoundTag().apply {
            putBlockPos(NBT_POS, hit.blockPos)
            putDirection(NBT_FACE, hit.direction)
        }

        tell("Start recorded")

        return InteractionResultHolder.success(actualStack)
    }

    companion object {
        private const val NBT_POS = "pos"
        private const val NBT_FACE = "face"
    }
}

object GridRenderer {
    fun submitForRenderSection(
        pRenderChunk: ChunkRenderDispatcher.RenderChunk,
        pChunkBufferBuilderPack: ChunkBufferBuilderPack,
        pRenderChunkRegion: RenderChunkRegion,
        pRenderTypeSet: MutableSet<RenderType>,
    ) {
        val renderType = RenderType.solid()

        val builder = pChunkBufferBuilderPack.builder(renderType)

        if(pRenderTypeSet.add(renderType)) {
            pRenderChunk.beginLayer(builder)
        }

        val lightReader = LightReader(pRenderChunkRegion)
        val neighborLights = NeighborLightReader(lightReader)
        val section = SectionPos.of(pRenderChunk.origin)

        val originX = pRenderChunk.origin.x.toDouble()
        val originY = pRenderChunk.origin.y.toDouble()
        val originZ = pRenderChunk.origin.z.toDouble()

        GridConnectionManagerClient.read(section) { material, quad ->
            for (i in 0 until 4) {
                val position = quad.positions[i]
                val blockPosition = position.floorBlockPos()

                val localLight = lightReader.getLightColor(blockPosition)
                val localBlockLight = unpackBlockLight(localLight).toDouble()
                val localSkyLight = unpackSkyLight(localLight).toDouble()

                neighborLights.load(blockPosition)

                val normal = quad.normals[i]
                val normalX = normal.x.toFloat()
                val normalY = normal.y.toFloat()
                val normalZ = normal.z.toFloat()

                val (u, v) = quad.uvs[i]

                val light = LightTexture.pack(
                    combineLight(0, neighborLights, normal, localBlockLight),
                    combineLight(1, neighborLights, normal, localSkyLight)
                )

                val xSection = (position.x - originX).toFloat()
                val ySection = (position.y - originY).toFloat()
                val zSection = (position.z - originZ).toFloat()

                val (r, g, b) = material.vertexColor

                builder.vertex(
                    xSection, ySection, zSection,
                    r, g, b, 1f,
                    u, v,
                    OverlayTexture.NO_OVERLAY, light,
                    normalX, normalY, normalZ
                )
            }
        }
    }
}

/**
 * Models a cable using an arclength-parameterized catenary ([ArcReparamCatenary3d]).
 * @param a First support point.
 * @param b Second support point.
 * @param slack The wire "slack". This is used to calculate the arc length **(slack * d(a, b))**
 * @param splitDistanceHint The maximum distance between consecutive vertex rings (for rendering)
 * @param splitRotIncrementMax The maximum deviation between the tangents at consecutive vertex rings (for rendering)
 * @param radius The radius of the cable (for rendering)
 * */
class CatenaryCable3d(
    val a: Vector3d,
    val b: Vector3d,
    val slack: Double = 0.05,
    val splitDistanceHint: Double = 0.5,
    val splitRotIncrementMax: Double = PI / 16.0,
    val circleVertices: Int = 8,
    val radius: Double = 0.1
) {
    //val ringSegmentSize = Rotation2d.exp(0.0).direction * radius .. Rotation2d.exp(2.0 * PI * (1.0 / circleVertices.toDouble())).direction * radius
    /**
     * Gets the circumference of the catenary, according to [radius].
     * */
    val circumference = 2.0 * PI * radius
    /**
     * Gets the supports [a] and [b], sorted in ascending order by their vertical coordinate.
     * */
    val supports = listOf(a, b).sortedBy { it.y }

    // Should be ~equal to the actual arc length
    /**
     * Gets the arc length of the cable, factoring in the [slack].
     * */
    val arcLength = (a..b) * (1.0 + slack)

    /**
     * Gets the catenary spline that characterises the wire.
     * */
    val spline = Spline3d(
        ArcReparamCatenarySegment3d(
            t0 = 0.0,
            t1 = 1.0,
            p0 = supports[0],
            p1 = supports[1],
            length = arcLength,
            Vector3d.unitY
        )
    )

    /**
     * Gets a set of blocks that are intersected by the spline.
     * */
    val blocks = spline.intersectGrid3d(0.0, 1.0, 0.1, 1024 * 1024)
        .requireNotNull { "Failed to intersect blocks $this" }
        .map { it.toBlockPos() }
        .toHashSet()

    /**
     * Gets a multimap of blocks intersected by the spline, and the chunks they belong to.
     * */
    val chunks = blocks.associateByMulti { ChunkPos(it) }

    /**
     * Creates a mesh of the wire.
     * */
    fun mesh() : CatenaryCableMesh {
        val samples = spline.adaptscan(
            0.0,
            1.0,
            0.1,
            condition = differenceCondition3d(
                distMax = min(splitDistanceHint, circumference),
                rotIncrMax = splitRotIncrementMax
            ),
            iMax = 1024 * 32
        ).requireNotNull { "Failed to mesh $this" }

        val extrusion = extrudeSketch(
            sketchCircle(circleVertices, radius),
            spline,
            samples
        )

        val quads = ArrayList<CatenaryCableQuad>()

        val mesh = extrusion.mesh

        mesh.quadScan { baseQuad ->
            val ptvVerticesParametric = baseQuad.indices.map { mesh.vertices[it] }
            val ptvVerticesPositions = ptvVerticesParametric.map { it.value }

            val ptvCenter = avg(ptvVerticesPositions)
            val ptvParam = avg(ptvVerticesParametric.map { it.t })
            val ptvNormal = (ptvCenter - spline.evaluate(ptvParam)).normalized()
            val ptvNormalWinding = polygralScan(ptvCenter, ptvVerticesPositions).normalized()

            val ptv = if((ptvNormal o ptvNormalWinding) > 0.0) baseQuad
            else baseQuad.rewind()

            fun vert(vertexId: Int) : CatenaryCableVertex {
                val vertexParametric = mesh.vertices[vertexId]
                val vertexPosition = vertexParametric.value
                val vertexNormal = (vertexPosition - (spline.evaluate(vertexParametric.t))).normalized()

                return CatenaryCableVertex(vertexPosition, vertexNormal, vertexParametric.t)
            }

            val vertices = listOf(vert(ptv.a), vert(ptv.b), vert(ptv.c), vert(ptv.d))

            quads.add(CatenaryCableQuad(ptvCenter, vertices))
        }

        return CatenaryCableMesh(extrusion, quads)
    }

    override fun toString() =
        "from $a to $b, " +
        "slack=$slack, " +
        "splitDistance=$splitDistanceHint, " +
        "splitRotIncrMax=$splitRotIncrementMax, " +
        "circleVertices=$circleVertices, " +
        "radius=$radius"

    fun toNbt() = CompoundTag().also {
        it.putVector3d(A, a)
        it.putVector3d(B, b)
        it.putDouble(SLACK, slack)
        it.putDouble(SPLIT_DISTANCE_HINT, splitDistanceHint)
        it.putDouble(SPLIT_ROT_INCR_MAX, splitRotIncrementMax)
        it.putInt(CIRCLE_VERTICES, circleVertices)
        it.putDouble(RADIUS, radius)
    }

    companion object {
        private const val A = "a"
        private const val B = "b"
        private const val SLACK = "slack"
        private const val SPLIT_DISTANCE_HINT = "splitDistanceHint"
        private const val SPLIT_ROT_INCR_MAX = "splitRotIncrMax"
        private const val CIRCLE_VERTICES = "circleVertices"
        private const val RADIUS = "radius"

        fun fromNbt(tag: CompoundTag) = CatenaryCable3d(
            tag.getVector3d(A),
            tag.getVector3d(B),
            tag.getDouble(SLACK),
            tag.getDouble(SPLIT_DISTANCE_HINT),
            tag.getDouble(SPLIT_ROT_INCR_MAX),
            tag.getInt(CIRCLE_VERTICES),
            tag.getDouble(RADIUS)
        )
    }
}

data class CatenaryCableMesh(val extrusion: SketchExtrusion, val quads: ArrayList<CatenaryCableQuad>)
data class CatenaryCableQuad(val primitiveCenter: Vector3d, val vertices: List<CatenaryCableVertex>)
data class CatenaryCableVertex(val position: Vector3d, val normal: Vector3d, val param: Double)
