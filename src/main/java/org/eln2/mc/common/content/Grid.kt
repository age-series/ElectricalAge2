package org.eln2.mc.common.content

import com.jozufozu.flywheel.api.MaterialManager
import com.jozufozu.flywheel.backend.instancing.entity.EntityInstance
import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.core.model.Model
import com.jozufozu.flywheel.util.Color
import net.minecraft.Util
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.Connection
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.*
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.client.render.*
import org.eln2.mc.common.blocks.foundation.CellBlock
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.runPre
import org.eln2.mc.data.Distance
import org.eln2.mc.data.DistanceUnits
import org.eln2.mc.mathematics.*
import kotlin.collections.ArrayList
import kotlin.math.PI
import kotlin.random.Random

class GridElectricalObject(cell: GridCell) : ElectricalObject(cell) {
    val resistor = ComponentHolder {
        Resistor()
    }

    override fun offerComponent(neighbour: ElectricalObject) =
        if(neighbour is GridElectricalObject) resistor.offerInternal() else resistor.offerExternal()

    override fun clearComponents() {
        resistor.clear()
    }

    override fun addComponents(circuit: Circuit) {
        circuit.add(resistor)
    }
}

class GridCell(ci: CellCI) : Cell(ci) {
    @SimObject
    val electricalObj = GridElectricalObject(this)
}

open class GridCellBlockEntity(pos: BlockPos, state: BlockState) : CellBlockEntity(pos, state, Content.GRID_TEST_BLOCK_ENTITY.get()) {
    @ServerOnly
    private val gridConnections = ArrayList<GridCellBlockEntity>()

    @ClientOnly
    private var clientConnectionPositions: List<BlockPos>? = null

    fun connect(g: GridCellBlockEntity) {
        if(gridConnections.contains(g)) {
            return
        }

        if(g == this) {
            return
        }

        disconnect()
        gridConnections.add(g)
        g.gridConnections.add(this)
        reconnect()

        setChanged()
        g.setChanged()

        this.sendClientUpdate()
        g.sendClientUpdate()

        val entity = GridConnectionEntity(
            level!!,
            this.blockPos,
            g.blockPos
        )

        entity.moveTo(connectionPos(this.blockPos, g.blockPos))

        level!!.addFreshEntity(entity)
    }

    override fun setDestroyed() {

        gridConnections.forEach { remote ->
            val cp = connectionPos(this.blockPos, remote.blockPos)

            level!!.getEntitiesOfClass(
                GridConnectionEntity::class.java,
                AABB(
                    cp - vec3(1.0),
                    cp + vec3(1.0)
                )
            ).forEach {
                if((it.aPos == this.blockPos && it.bPos == remote.blockPos) || (it.aPos == remote.blockPos && it.bPos == this.blockPos)) {
                    it.kill()
                }
            }

            remote.gridConnections.remove(this)
        }

        super.setDestroyed()
    }

    private fun createConnList() = ListTag().also { listTag ->
        gridConnections.forEach { remoteEntity ->
            listTag.add(
                CompoundTag().apply {
                    putBlockPos(NBT_POS, remoteEntity.blockPos)
                }
            )
        }
    }
    private fun readConnList(tag: ListTag) = tag.map { (it as CompoundTag).getBlockPos(NBT_POS) }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        val level = this.level ?: error("level null in saveAdditional")

        if(level.isClientSide) {
            return
        }

        pTag.put(NBT_CONNECTIONS, createConnList())
    }

    var loadTag: ListTag? = null
    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        loadTag = pTag.get(NBT_CONNECTIONS) as ListTag
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(pLevel.isClientSide) {
            return
        }

        loadTag?.also { tag ->
            val positions = readConnList(tag)

            runPre {
                positions.forEach {
                    val remoteEntity = pLevel.getBlockEntity(it) as? GridCellBlockEntity

                    if(remoteEntity == null) {
                        LOGGER.error("Grid block failed to resolve target $it")

                        return@forEach
                    }

                    gridConnections.add(remoteEntity)
                }
            }

            loadTag = null
        }
    }

    override fun neighborScan(actualCell: Cell) = super.neighborScan(actualCell) + gridConnections.map { CellNeighborInfo(it.gridCell, it) }

    @ServerOnly
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)

    @ClientOnly
    override fun onDataPacket(net: Connection?, pkt: ClientboundBlockEntityDataPacket?) {
        pkt?.also { handleUpdateTag(it.tag) }
    }

    @ServerOnly
    override fun getUpdateTag() = CompoundTag().apply {  put(NBT_CONNECTIONS, createConnList()) }

    @ClientOnly
    override fun handleUpdateTag(tag: CompoundTag?) {
        tag?.also {
            val list = readConnList(it.get(NBT_CONNECTIONS) as ListTag)
            clientConnectionPositions = list
        }
    }

    @ServerOnly
    val gridCell get() = super.cell as GridCell

    companion object {
        private const val NBT_CONNECTIONS = "connections"
        private const val NBT_POS = "pos"

        private fun connectionPos(a: BlockPos, b: BlockPos) = avg(
            a.toVector3d(),
            b.toVector3d()
        ).toVec3()
    }
}

class GridConnectionEntity(pEntityType: EntityType<*>, pLevel: Level) : Entity(pEntityType, pLevel) {
    constructor(pLevel: Level, aPos: BlockPos, bPos: BlockPos) : this(Content.GRID_CONNECTION_ENTITY.get(), pLevel) {
        link(
            aPos,
            bPos
        )
    }

    override fun isInvulnerable() = true
    override fun hurt(pSource: DamageSource, pAmount: Float) = false

    var aPos: BlockPos? = null
    var bPos: BlockPos? = null

    @ClientOnly
    private var renderer: GridConnectionInstanceFlw? = null

    @ClientOnly
    fun bindRenderer(renderer: GridConnectionInstanceFlw) {
        if(aPos != null && bPos != null) {
            renderer.connect(
                aPos!!,
                bPos!!
            )
        }
        else {
            LOGGER.error("A and B unavailable at renderer creation")
        }
    }

    @ClientOnly
    fun unbindRenderer() { }

    @ServerOnly
    fun link(a: BlockPos, b: BlockPos) {
        aPos = a
        bPos = b
        entityData.set(EDA_POSITION_PAIR, createTag())
    }

    override fun defineSynchedData() {
        entityData.define(
            EDA_POSITION_PAIR,
            CompoundTag()
        )
    }

    override fun readAdditionalSaveData(pCompound: CompoundTag) {
        pCompound.useSubTagIfPreset(NBT_CONNECTION) {
            val positions = readTag(it)
            aPos = positions.first
            bPos = positions.second

            link(
                aPos!!,
                bPos!!
            )
        }
    }

    override fun addAdditionalSaveData(pCompound: CompoundTag) {
        pCompound.put(NBT_CONNECTION, createTag())
    }

    private fun createTag() = CompoundTag().also {
        val aPos = this.aPos
        val bPos = this.bPos

        if(aPos == null || bPos == null) {
            error("Uninitialized connection!")
        }

        it.putBlockPos(NBT_A, aPos)
        it.putBlockPos(NBT_B, bPos)
    }

    private fun readTag(tag: CompoundTag) = tag.let {
        Pair(
            it.getBlockPos(NBT_A),
            it.getBlockPos(NBT_B)
        )
    }

    override fun getAddEntityPacket(): Packet<*> = ClientboundAddEntityPacket(this)

    override fun onSyncedDataUpdated(pKey: EntityDataAccessor<*>) {
        if(!level.isClientSide) {
            return
        }

        if(pKey != EDA_POSITION_PAIR) {
            return
        }

        val tag = entityData.get(EDA_POSITION_PAIR)

        if(tag.isEmpty) {
            return
        }

        val pair = readTag(tag)

        aPos = pair.first
        bPos = pair.second

        setupRendering()
    }

    @ClientOnly
    private fun setupRendering() {
        renderer?.connect(aPos!!, bPos!!)
    }

    companion object {
        private val EDA_POSITION_PAIR = SynchedEntityData.defineId(
            GridConnectionEntity::class.java,
            EntityDataSerializers.COMPOUND_TAG
        )

        private const val NBT_CONNECTION = "connection"
        private const val NBT_A = "a"
        private const val NBT_B = "b"
    }
}

class GridConnectionInstanceFlw(materialManager: MaterialManager, entity: GridConnectionEntity): EntityInstance<GridConnectionEntity>(materialManager, entity) {
    var model: ModelData? = null

    override fun init() {
        entity.bindRenderer(this)
    }

    override fun remove() {
        LOGGER.error("Removing renderer")
        model?.delete()
        entity.unbindRenderer()
    }

    fun connect(a: BlockPos, b: BlockPos) {
        LOGGER.info("Setting up renderer for $a $b")

        model?.delete()
        model = materialManager
            .solid(RenderTypes.CABLE)
            .material(Materials.TRANSFORMED)
            .model(Pair(a, b)) {
                createWireModel(
                    a.toVector3d(),
                    b.toVector3d()
                )
            }
            .createInstance()
            .loadIdentity() // The vertices are in the target frame

        model!!.apply {
            setSkyLight(15)
            setBlockLight(15)
        }
    }

    companion object {
        private fun createWireModel(aMc: Vector3d, bMc: Vector3d): GridWireModel {
            // Working in RFU @Grissess
            // More dragons are required to transition over to Minecraft

            val txMcRfu = CoordinateSystem.minecraft .. CoordinateSystem.rfu

            val a = txMcRfu * aMc
            val b = txMcRfu * bMc

            val catenarySupports = listOf(a, b).sortedBy { it.z }
            val targetLength = (a..b) * 1.1

            val l = Distance.from(targetLength, DistanceUnits.METER)
            LOGGER.info("Target length: ${(l .. DistanceUnits.FOOTBALL_FIELDS).rounded(2)} football fields (${(l .. DistanceUnits.LIGHT_NANORELS).rounded(4)} light nanorels)")

            val path = Spline3d(
                splineSegmentMapOf(
                    listOf(
                        ArcReparamCatenary2dRFUProjectionSegment3d(
                            t0 = 0.0,
                            t1 = 1.0,
                            p0 = catenarySupports[0],
                            p1 = catenarySupports[1],
                            length = targetLength
                        )
                    )
                )
            )

            val samples = path.adaptscan(
                0.0,
                1.0,
                0.1,
                condition = diffCondition3d(
                    distMax = 0.5,
                    rotIncrMax = PI / 16.0
                )
            ) ?: error("Failed to sample spline from $aMc to $bMc")

            val builder = extrudeSketch(
                sketchCircle(
                    5,
                    0.1
                ),
                path,
                samples
            )

            // Dispatch vertices in MC:
            val txRfuMc = !txMcRfu

            val mcVtxMc = ArrayList<VertPositionColorNormalUv>(builder.vertices.size)

            builder.quadScan().map { primitive ->
                val vtxParametric = primitive.indices.map { builder.vertices[it] }
                val vtx = vtxParametric.map { it.value }

                val ptvCenter = avg(vtx)
                val ptvParam = avg(vtxParametric.map { it.param })
                val ptvNormal = ((ptvCenter - path.evaluate(ptvParam))).normalized()
                val actualNormal = polygralScan(ptvCenter, vtx).normalized()

                if((ptvNormal o actualNormal) <= 0.0) primitive
                else primitive.rewind()
            }.forEach { primitive ->
                primitive.indices.forEach { vertIdx ->
                    val vertRfuParametric = builder.vertices[vertIdx]

                    val vert = txRfuMc * vertRfuParametric.value

                    val normal = (vert - (txRfuMc * path.evaluate(vertRfuParametric.param))).normalized()

                    mcVtxMc.add(
                        VertPositionColorNormalUv(
                            position = vert,
                            color = Color.WHITE,
                            normal = normal,
                            uv = Vector2d(Random.nextDouble(), Random.nextDouble())
                        )
                    )
                }
            }

            LOGGER.warn("Created model with ${mcVtxMc.size} vertices")

            return GridWireModel(mcVtxMc)
        }

        private data class GridWireModel(val vertices: List<VertPositionColorNormalUv>) : Model {
            val vertArray = ListVertexList(vertices)
            override fun name() = "Wire"
            override fun getReader() = vertArray
            override fun vertexCount() = vertices.size
        }
    }
}

class GridCellBlock : CellBlock() {
    override fun getCellProvider(): ResourceLocation = Content.GRID_CELL.id
    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = GridCellBlockEntity(pPos, pState)
}

open class GridConnectItem : Item(Properties()) {
    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val actualStack = pPlayer.getItemInHand(pUsedHand)

        fun fail() = InteractionResultHolder.fail(actualStack)
        fun success() = InteractionResultHolder.success(actualStack)

        if(pLevel.isClientSide) {
            return fail()
        }

        val hit = getPlayerPOVHitResult(pLevel, pPlayer, ClipContext.Fluid.SOURCE_ONLY)

        if(hit.type != HitResult.Type.BLOCK) {
            return fail()
        }

        val gridEntity = pLevel.getBlockEntity(hit.blockPos) as? GridCellBlockEntity
            ?: return fail()

        if(actualStack.tag != null && actualStack.tag!!.contains(NBT_POS)) {
            val remoteEntity = pLevel.getBlockEntity(actualStack.tag!!.getBlockPos(NBT_POS)) as? GridCellBlockEntity

            if(remoteEntity == null) {
                actualStack.tag = null
                return fail()
            }

            gridEntity.connect(remoteEntity)
            actualStack.tag = null

            pPlayer.sendMessage(TextComponent("Realized connection"), Util.NIL_UUID)

            return success()
        }

        actualStack.tag = CompoundTag().apply {
            putBlockPos(NBT_POS, hit.blockPos)
        }

        return success()
    }

    companion object {
        private const val NBT_POS = "pos"
    }
}
