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
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.core.BlockPos
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
import org.eln2.mc.data.*
import org.eln2.mc.mathematics.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min

data class GridConnection(val id: Int, val catenary: WireCatenary) {
    constructor(catenary: WireCatenary) : this(ID_ATOMIC.getAndIncrement(), catenary)

    fun toNbt() = CompoundTag().also {
        it.putInt(ID, id)
        it.put(CATENARY, catenary.toNbt())
    }

    companion object {
        private val ID_ATOMIC = AtomicInteger()

        private const val ID = "id"
        private const val CATENARY = "catenary"

        fun fromNbt(tag: CompoundTag) = GridConnection(
            tag.getInt(ID),
            WireCatenary.fromNbt(tag.get(CATENARY) as CompoundTag)
        )
    }
}

interface GridConnectionHandle {
    val connection: GridConnection
    val level: ServerLevel

    fun destroy()
}

fun interface GridConnectionNotifier {
    fun onObstructed(handle: GridConnectionHandle)
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

data class GridConnectionCreateMessage(val connection: GridConnection) {
    companion object {
        fun encode(message: GridConnectionCreateMessage, buf: FriendlyByteBuf) {
            buf.writeNbt(message.connection.toNbt())
        }

        fun decode(buf: FriendlyByteBuf) = GridConnectionCreateMessage(
            GridConnection.fromNbt(buf.readNbt()!!)
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
    private val levels = HashMap<ServerLevel, LevelGridData>()

    private fun validateUsage() {
        requireIsOnServerThread { "Grid server must be on server thread" }
    }

    private fun validateLevel(level: Level): ServerLevel {
        if(level !is ServerLevel) {
            error("Cannot use non server level $level")
        }

        return level
    }

    private fun getLevelData(level: Level) : LevelGridData {
        val serverLevel = validateLevel(level)
        return levels.computeIfAbsent(serverLevel) { LevelGridData(serverLevel) }
    }

    fun createConnection(level: Level, connection: GridConnection, notifier: GridConnectionNotifier? = null) : GridConnectionHandle {
        validateUsage()
        return getLevelData(validateLevel(level)).createConnection(connection, notifier)
    }

    fun playerWatch(level: ServerLevel, player: ServerPlayer, chunkPos: ChunkPos) {
        validateUsage()
        getLevelData(level).watch(player, chunkPos)
    }

    fun playerUnwatch(level: ServerLevel, player: ServerPlayer, chunkPos: ChunkPos) {
        validateUsage()
        getLevelData(level).unwatch(player, chunkPos)
    }

    fun createPairIfAbsent(level: ServerLevel, pair: GridConnectionPair) : Boolean {
        validateUsage()
        return getLevelData(level).createPairIfAbsent(pair)
    }

    fun removeEndpointById(level: ServerLevel, endpointId: UUID) {
        validateUsage()
        getLevelData(level).removeEndpointById(endpointId)
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

        fun createConnection(connection: GridConnection, notifier: GridConnectionNotifier?) : GridConnectionHandle {
            val handle = Handle(connection, notifier)

            handles.add(handle)

            connection.catenary.chunks.keys.forEach { chunkPos ->
                handlesByChunk[chunkPos].add(handle)
            }

            watchedChunksByPlayer.map.forEach { (player, playerWatchedChunks) ->
                val intersectedChunks = HashSet<ChunkPos>()

                for (catenaryChunk in connection.catenary.chunks.keys) {
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

        fun createPairIfAbsent(pair: GridConnectionPair) : Boolean {
            if(pairMap.hasPair(pair)) {
                return false
            }

            pairMap.addPair(pair)
            handlesByPair[pair] = createConnection(
                GridConnection(
                    WireCatenary(
                        pair.a.attachment,
                        pair.b.attachment
                    )
                ),
                null
            )

            return true
        }

        fun removeEndpointById(endPointId: UUID) {
            pairMap.removePairsById(endPointId).forEach { pair ->
                handlesByPair.remove(pair)!!.destroy()
            }
        }

        private inner class Handle(override val connection: GridConnection, val notifier: GridConnectionNotifier?) : GridConnectionHandle {
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
                    connection.catenary.chunks.keys.forEach { chunk ->
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

    private fun scanUProgression(extrusion: SketchExtrusion, catenary: WireCatenary, u0: Double, u1: Double) : Double2DoubleOpenHashMap {
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

    fun addConnection(connection: GridConnection) {
        val sections = HashSet<SectionPos>()

        lock.write {
            val catenary = connection.catenary
            val (extrusion, quads) = catenary.mesh()
            LOG.info("Generated ${quads.size} quads")
            val sprite = GridRenderer.WIRE_TEXTURE.value

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

                val sectionData = quadsBySection.computeIfAbsent(
                    SectionPos.of(quadPos),
                    ::ConnectionSectionSlice
                )

                sectionData.quads.add(processedQuad)
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

    fun read(sectionPos: SectionPos, user: (Quad) -> Unit) {
        lock.read {
            slicesBySection[sectionPos].forEach { slice ->
                slice.quads.forEach { quad ->
                    user(quad)
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

    // todo optimize storage

    class Quad {
        val positions = Array(4) { Vector3d.zero }
        val normals = Array(4) { Vector3d.zero }
        val uvs = Array(4) { Pair(0f, 0f) }
    }

    private class ConnectionSectionSlice(val sectionPos: SectionPos) {
        val quads = ArrayList<Quad>()
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
    val endPoints = ArrayList<GridEndpointInfo>()

    var endpointId = UUID.randomUUID()
        private set

    override fun onRemoving() {
        requireIsOnServerThread { // Maybe we'll have such a situation in the future...
            "OnRemoving grid is not on the server thread"
        }

        endPoints.forEach { remoteEndPoint ->
            val remoteCell = graph.getCell(remoteEndPoint.locator)

            remoteCell as GridCell

            require(remoteCell.endPoints.removeIf { it.id == this.endpointId }) {
                "Failed to clean up remote end point"
            }
        }

        GridConnectionManagerServer.removeEndpointById(graph.level, this.endpointId)
    }

    override fun saveCellData() = CompoundTag().also {
        it.putUUID(ENDPOINT_ID, endpointId)

        val endpointList = ListTag()

        endPoints.forEach { remoteEndPoint ->
            endpointList.add(remoteEndPoint.toNbt())
        }

        it.put(REMOTE_END_POINTS, endpointList)
    }

    override fun loadCellData(tag: CompoundTag) {
        endpointId = tag.getUUID(ENDPOINT_ID)

        (tag.get(REMOTE_END_POINTS) as? ListTag)?.forEach { endpointTag ->
            endPoints.add(GridEndpointInfo.fromNbt(endpointTag as CompoundTag))
        }
    }

    override fun onLoadedFromDisk() {
        endPoints.forEach { remoteEndPoint ->
            if(!graph.hasCell(remoteEndPoint.locator)) {
                LOG.error("Invalid end point $remoteEndPoint") // Break point here
            }
        }
    }

    companion object {
        private const val ENDPOINT_ID = "endpointId"
        private const val REMOTE_END_POINTS = "remoteEndPoints"
    }
}

abstract class GridCellPart<R : PartRenderer>(
    id: ResourceLocation,
    placement: PartPlacementInfo,
    provider: CellProvider<GridCell>
) : CellPart<GridCell, R>(id, placement, provider) {
    val attachment = placement.position.toVector3d() + Vector3d(0.5)

    var stagingCell: Cell? = null

    fun createEndpointInfo() = GridEndpointInfo(
        cell.endpointId, attachment, cell.locator
    )

    override fun addExtraConnections(results: MutableSet<CellNeighborInfo>) {
        if(stagingCell != null) {
            results.add(CellNeighborInfo.of(stagingCell!!))
        }

        if(cell.hasGraph) {
            cell.endPoints.forEach { remoteEndPoint ->
                results.add(
                    CellNeighborInfo.of(
                        cell.graph.getCell(remoteEndPoint.locator)
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

            cell.endPoints.forEach { remoteEndPoint ->
                val pair = GridConnectionPair.create(localEndPoint, remoteEndPoint)
                GridConnectionManagerServer.createPairIfAbsent(placement.level, pair)
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

open class GridConnectItem : Item(Properties()) {
    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val actualStack = pPlayer.getItemInHand(pUsedHand)

        fun fail() = InteractionResultHolder.fail(actualStack)
        fun success() = InteractionResultHolder.success(actualStack)

        if (pLevel.isClientSide) {
            return fail()
        }

        val hit = getPlayerPOVHitResult(pLevel, pPlayer, ClipContext.Fluid.SOURCE_ONLY)

        if (hit.type != HitResult.Type.BLOCK) {
            return fail()
        }

        val targetMultipart = pLevel.getBlockEntity(hit.blockPos) as? MultipartBlockEntity
            ?: return fail()

        val targetPart = targetMultipart.pickPart(pPlayer) as? GridCellPart<*>
            ?: return fail()

        if (actualStack.tag != null && actualStack.tag!!.contains(NBT_POS) && actualStack.tag!!.contains(NBT_FACE)) {
            val remoteMultipart = pLevel.getBlockEntity(actualStack.tag!!.getBlockPos(NBT_POS)) as? MultipartBlockEntity

            if (remoteMultipart == null) {
                actualStack.tag = null
                return fail()
            }

            val remotePart = remoteMultipart.getPart(actualStack.tag!!.getDirection(NBT_FACE)) as? GridCellPart<*>

            if(remotePart == null) {
                actualStack.tag = null
                return fail()
            }

            CellConnections.disconnectCell(targetPart.cell, targetPart.placement.multipart, false)

            targetPart.stagingCell = remotePart.cell
            remotePart.stagingCell = targetPart.cell

            CellConnections.connectCell(targetPart.cell, targetPart.placement.multipart)

            if(targetPart.cell.graph != remotePart.cell.graph) {
                noop()
            }

            targetPart.stagingCell = null
            remotePart.stagingCell = null

            targetPart.cell.endPoints.add(remotePart.createEndpointInfo())
            remotePart.cell.endPoints.add(targetPart.createEndpointInfo())

            check(
                GridConnectionManagerServer.createPairIfAbsent(
                    pLevel as ServerLevel,
                    GridConnectionPair.create(
                        targetPart.createEndpointInfo(),
                        remotePart.createEndpointInfo()
                    )
                )
            )

            actualStack.tag = null
            pPlayer.sendSystemMessage(Component.literal("Realized connection"))

            return success()
        }

        actualStack.tag = CompoundTag().apply {
            putBlockPos(NBT_POS, hit.blockPos)
            putDirection(NBT_FACE, hit.direction)
        }

        return success()
    }

    companion object {
        private const val NBT_POS = "pos"
        private const val NBT_FACE = "face"
    }
}

object GridRenderer {
    val ATLAS_ID = InventoryMenu.BLOCK_ATLAS
    val TEXTURE_ID = resource("cable/copper_cable")

    val WIRE_TEXTURE = lazy {
        Minecraft.getInstance()
            .modelManager
            .getAtlas(ATLAS_ID)
            .getSprite(TEXTURE_ID)
    }

    fun submitForChunk(
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

        GridConnectionManagerClient.read(section) { quad ->
            for (i in 0 until 4) {
                val position = quad.positions[i]
                val blockPosition = position.floorBlockPos()

                val localLight = lightReader.getLightColor(blockPosition)
                val localBlockLight = unpackBlockLight(localLight).toDouble()
                val localSkyLight = unpackSkyLight(localLight).toDouble()

                neighborLights.load(blockPosition)

                val normal = quad.normals[i]
                val (u, v) = quad.uvs[i]

                val light = LightTexture.pack(
                    combineLight(0, neighborLights, normal, localBlockLight),
                    combineLight(1, neighborLights, normal, localSkyLight)
                )

                builder.vertex(
                    (position.x - originX).toFloat(),
                    (position.y - originY).toFloat(),
                    (position.z - originZ).toFloat(),
                    1.0f,
                    1.0f,
                    1.0f,
                    1.0f,
                    u,
                    v,
                    OverlayTexture.NO_OVERLAY,
                    light,
                    normal.x.toFloat(),
                    normal.y.toFloat(),
                    normal.z.toFloat()
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
class WireCatenary(
    val a: Vector3d,
    val b: Vector3d,
    val slack: Double = 0.1,
    val splitDistanceHint: Double = 0.2,
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
    val arcLength = (a .. b) * (1.0 + slack)

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
    fun mesh() : WireMeshResult {
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

        val quads = ArrayList<WireQuad>()

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

            fun vert(vertexId: Int) : WireVertex {
                val vertexParametric = mesh.vertices[vertexId]
                val vertexPosition = vertexParametric.value
                val vertexNormal = (vertexPosition - (spline.evaluate(vertexParametric.t))).normalized()

                return WireVertex(vertexPosition, vertexNormal, vertexParametric.t)
            }

            val vertices = listOf(vert(ptv.a), vert(ptv.b), vert(ptv.c), vert(ptv.d))

            quads.add(WireQuad(ptvCenter, vertices))
        }

        return WireMeshResult(extrusion, quads)
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

        fun fromNbt(tag: CompoundTag) = WireCatenary(
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
data class WireMeshResult(
    val extrusion: SketchExtrusion,
    val quads: ArrayList<WireQuad>
)

data class WireVertex(
    val position: Vector3d,
    val normal: Vector3d,
    val param: Double
)

data class WireQuad(
    val primitiveCenter: Vector3d,
    val vertices: List<WireVertex>
)
