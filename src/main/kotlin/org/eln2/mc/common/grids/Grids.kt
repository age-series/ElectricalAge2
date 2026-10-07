package org.eln2.mc.common.grids

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Item
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Simulator
import org.ageseries.libage.sim.ThermalMass
import org.ageseries.libage.sim.electrical.ElectricalPin
import org.ageseries.libage.sim.electrical.Resistor
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.schedulePre
import org.eln2.mc.Locators
import org.eln2.mc.SortedUUIDPair
import org.eln2.mc.plusAssign
import org.eln2.mc.extensions.*
import org.eln2.mc.randomFloat
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Encapsulates information about a grid connection to a remote end point.
 * @param material The material of the grid connection.
 * @param resistance The electrical resistance of the connection, a value dependent on the physical configuration of the game object.
 * */
data class GridConnectionDescription(val material: GridMaterial, val resistance: Double)

/**
 * Cell node that manages a set of terminals, which receive separate grid endpoints.
 * */
class GridNode(val cell: Cell) : UniqueCellNode {
    init {
        cell.lifetimeEvents += this::onBeginDestroy
    }

    /**
     * Gets the terminals used by this grid node cell. The integer is the terminal ID, and the UUID is the endpoint ID of the terminal.
     * */
    val terminals = MutableMapPairBiMap<Int, GridEndpointInfo>()

    /**
     * Takes the terminals from the [gridTerminalSystem] for usage within this cell.
     * This must be done once, when the cell is created. This data is saved and must be the same the next time the grid terminal system is created.
     * */
    fun mapFromGridTerminalSystem(gridTerminalSystem: GridTerminalSystem) {
        check(terminals.size == 0) {
            "Multiple map from grid terminal system"
        }

        gridTerminalSystem.instances.forEach { (terminalID, terminal) ->
            terminals.add(terminalID, terminal.gridEndpointInfo)
        }

        cell.setChanged()
    }

    /**
     * Iterates over every [GridConnectionCell].
     * */
    inline fun forEachConnectionCell(use: (GridConnectionCell) -> Unit) {
        cell.connections.forEach {
            if (it is GridConnectionCell) {
                use(it)
            }
        }
    }

    /**
     * Gets a list of all [GridConnectionCell]s.
     * */
    fun getConnectionCells() = cell.connections.filterIsInstance<GridConnectionCell>()

    /**
     * Checks if a connection to [remoteEndpointID] exists, between this cell's [terminal] and the other cell's [remoteTerminal].
     * */
    fun hasAnyConnectionWith(terminal: Int, remoteEndpointID: UUID, remoteTerminal: Int) = cell.connections.any {
        val connectionCell = it as? GridConnectionCell
            ?: return@any false

        if(connectionCell.getFullMetadata(cell).terminal != terminal) {
            return@any false
        }

        val other = connectionCell.getOtherCellWithFullMetadata(cell)

        other.terminal == remoteTerminal && other.endpointInfo.id == remoteEndpointID
    }

    /**
     * Destroys all [GridConnectionCell]s.
     * */
    private fun onBeginDestroy(event: Cell_onBeginDestroy) {
        val cells = getConnectionCells()

        cells.forEach {
            it.isRemoving = true
        }

        cells.forEach {
            CellConnections.destroy(it, it.container!!)
        }
    }

    override fun saveNodeData(): CompoundTag {
        val tag = CompoundTag()
        val listTag = ListTag()

        terminals.forward.forEach { (terminal, endpoint) ->
            val entryCompound = CompoundTag()

            entryCompound.putInt(TERMINAL, terminal)
            entryCompound.putUUID(ENDPOINT_ID, endpoint.id)
            entryCompound.putVector3d(ENDPOINT_ATTACHMENT, endpoint.attachment)
            entryCompound.putLocator(ENDPOINT_LOCATOR, endpoint.locator)

            listTag.add(entryCompound)
        }

        tag.put(TERMINALS, listTag)

        return tag
    }

    override fun loadNodeData(tag: CompoundTag) {
        val listTag = tag.getListTag(TERMINALS)

        listTag.forEachCompound { entryCompound ->
            val terminal = entryCompound.getInt(TERMINAL)
            val endpointId = entryCompound.getUUID(ENDPOINT_ID)
            val attachment = entryCompound.getVector3d(ENDPOINT_ATTACHMENT)
            val endpointLocator = entryCompound.getLocator(ENDPOINT_LOCATOR)

            terminals.add(terminal, GridEndpointInfo(endpointId, attachment, endpointLocator))
        }
    }

    companion object {
        private const val TERMINALS = "entries"
        private const val TERMINAL = "terminal"
        private const val ENDPOINT_ID = "endpointID"
        private const val ENDPOINT_LOCATOR = "endpointLocator"
        private const val ENDPOINT_ATTACHMENT = "attachment"
    }
}

class GridConnectionElectricalObject(cell: GridConnectionCell) : ElectricalObject<GridConnectionCell>(cell) {
    private var resistorInternal : Resistor? = null

    val resistor get() = checkNotNull(this.resistorInternal) {
        "Resistor was not set!"
    }

    fun initialize() {
        check(resistorInternal == null) {
            "Re-initialization of electrical grid connection object"
        }

        val resistor = Resistor()
        resistor.resistance = cell.state.connection.resistance
        this.resistorInternal = resistor
    }

    override fun offerPolar(remote: ElectricalObject<*>): ElectricalPin {
        val remoteCell = remote.cell

        return if(remoteCell === cell.cellA) {
            resistor.positive
        }
        else if(remoteCell === cell.cellB) {
            resistor.negative
        }
        else {
            error(DEBUGGER_BREAK("Unrecognised remote cell"))
        }
    }
}

class GridConnectionThermalObject(cell: GridConnectionCell) : ThermalObject<GridConnectionCell>(cell) {
    private var massInternal: ThermalMass? = null

    val mass get() = checkNotNull(this.massInternal) {
        "Thermal mass is not initialized!"
    }

    fun initialize() {
        check(massInternal == null) {
            "Re-initialization of thermal grid connection object"
        }

        val mass = ThermalMass(
            material = cell.state.connection.material.physicalMaterial,
            mass = Quantity(cell.state.connection.material.physicalMaterial.density.value * cell.state.connection.cable.volume)
        )

        cell.environmentData.loadTemperature(mass)

        this.massInternal = mass
    }

    override fun offerComponent(remote: ThermalObject<*>) = ThermalComponentInfo(mass)

    override fun addComponents(simulator: Simulator) {
        simulator.add(mass)

        cell.environmentData.connect(
            simulator,
            ConnectionParameters(
                area = cell.state.connection.cable.surfaceArea
            ),
            mass
        )
    }

    override fun getParameters(remote: ThermalObject<*>) = ConnectionParameters(area = cell.state.connection.cable.crossSectionArea)
}

/**
 * Represents a connection between two cells that have [GridNode].
 * This is not owned by a game object. It owns its own container.
 * This cell owns the [GridConnection].
 * It will check if [GridConnectionManagerServer] already has a connection between the two endpoints, and if so, it will error out.
 * */
class GridConnectionCell(ci: CellCreateInfo) : Cell(ci), GridConnectionOwner {
    private var stateField: State? = null

    /**
     * Set to true when the connection is being removed or one of the endpoints is being removed.
     * This will prevent validation that will fail.
     * */
    var isRemoving = false

    val state get() = checkNotNull(stateField) {
        "Grid connection cell state was not initialized!"
    }

    private var handle: GridConnectionHandle? = null

    private fun initializeFromStaging(
        level: ServerLevel,
        a: Cell, terminalA: Int,
        b: Cell, terminalB: Int,
        cable: GridConnection,
    ) {
        check(stateField == null) {
            "Multiple initializations"
        }

        check(container == null) {
            "Expected container to not be set when initializing from staging"
        }

        container = StagingContainer(level, a, b)

        val ax = a.requireNode<GridNode>()
        val bx = b.requireNode<GridNode>()

        val endpointA = ax.terminals.forward[terminalA]!!
        val endpointB = bx.terminals.forward[terminalB]!!

        val pair = GridEndpointPair.create(endpointA, endpointB)

        stateField = if(pair.a == endpointA) {
            State(
                cable, pair,
                terminalA, terminalB
            )
        }
        else {
            State(
                cable, pair,
                terminalB, terminalA
            )
        }

        initializeObjects()
    }

    private var cellAField: Cell? = null
    private var cellBField: Cell? = null

    /**
     * Gets the cell of the first endpoint.
     * */
    val cellA get() = checkNotNull(cellAField) {
        "Cell A was not initialized"
    }

    /**
     * Gets the cell of the second endpoint.
     * */
    val cellB get() = checkNotNull(cellBField) {
        "Cell B was not initialized"
    }

    /**
     * Gets the other cell in the pair. [cell] must be either [cellA] or [cellB].
     * @return [cellA], if [cell] is [cellB]. [cellB] is [cell] is [cellA]. Otherwise, error.
     * */
    fun getOtherCell(cell: Cell) = if (cellA === cell) {
        cellB
    } else if (cellB === cell) {
        cellA
    } else {
        error("Expected cell to be one from the pair")
    }

    /**
     * Gets the other cell in the pair.
     * @return [cellA], if [cell] is [cellB]. [cellB] is [cell] is [cellA]. Otherwise, null.
     * */
    fun getOtherCellOrNull(cell: Cell) = if (cellA === cell) {
        cellB
    } else if (cellB === cell) {
        cellA
    } else {
        null
    }

    /**
     * Gets the other cell in the pair, along with grid information. [cell] must be either [cellA] or [cellB].
     * @return [cellA], if [cell] is [cellB]. [cellB] is [cell] is [cellA]. Otherwise, error.
     * */
    fun getOtherCellWithFullMetadata(cell: Cell) = if (cellA === cell) {
        NodeInfo(cellB, state.terminalB, state.pair.b)
    } else if (cellB === cell) {
        NodeInfo(cellA, state.terminalA, state.pair.a)
    } else {
        error("Expected cell $cell to be one from the pair ($cellA or $cellB) to get other")
    }

    /**
     * Gets the information associated with [cell]. The [cell] must be either [cellA] or [cellB].
     * */
    fun getFullMetadata(cell: Cell) = if (cellA === cell) {
        NodeInfo(cellA, state.terminalA, state.pair.a)
    } else if (cellB === cell) {
        NodeInfo(cellB, state.terminalB, state.pair.b)
    } else {
        error("Expected cell $cell to be one from the pair ($cellA or $cellB) to get metadata")
    }

    override fun onBuildStarted() {
        super.onBuildStarted()

        if(isRemoving) {
            return
        }

        check(connections.size == 2) {
            "Grid connection cell did not have the 2 connections!"
        }

        val cell1 = connections[0]
        val cell2 = connections[1]

        val cell1x = cell1.requireNode<GridNode> {
            "$cell1 did not have grid node for connection"
        }

        val cell2x = cell2.requireNode<GridNode> {
            "$cell2 did not have grid node for connection"
        }

        val cell1a = cell1x.terminals.backward[state.pair.a]
        val cell1b = cell1x.terminals.backward[state.pair.b]
        val cell2a = cell2x.terminals.backward[state.pair.a]
        val cell2b = cell2x.terminals.backward[state.pair.b]

        check(cell1a == null || cell1b == null) {
            "Cell 1 had both endpoints"
        }

        check(cell2a == null || cell2b == null) {
            "Cell 2 had both endpoints"
        }

        check(cell1a != null || cell2a != null) {
            "None of the cells had endpoint A"
        }

        check(cell1b != null || cell2b != null) {
            "None of the cells had endpoint B"
        }

        val state = this.state

        cellAField = if(cell1a != null) {
            check(cell1a == state.terminalA) {
                "Mismatched terminals"
            }
            cell1
        }
        else {
            check(cell2a == state.terminalA) {
                "Mismatched terminals"
            }
            cell2
        }

        cellBField = if(cell1b != null) {
            check(cell1b == state.terminalB) {
                "Mismatched terminals"
            }
            cell1
        }
        else {
            check(cell2b == state.terminalB) {
                "Mismatched terminals"
            }
            cell2
        }
    }

    private fun setupPermanent() {
        this.container = PermanentContainer()
        handle = GridConnectionManagerServer.registerPair(graph.level, state.pair, state.connection, this)
    }

    private fun sendRenderUpdates() {
        val handle = checkNotNull(handle) {
            "Expected to have handle to send updates!"
        }

        val color = state.connection.material.thermalColor.evaluateRGBL(
            thermal.mass.temperature
        )

        handle.options = GridRenderOptions(
            tint = MyColor(
                color.r,
                color.g,
                color.b
            ),
            brightnessOverride = (color.a / 255.0)
        )
    }

    override fun onWorldLoadedPreSolver() {
        super.onWorldLoadedPreSolver()
        check(this.container == null)
        check(this.handle == null)
        setupPermanent()
        sendRenderUpdates()
    }

    override fun saveCellData(): CompoundTag {
        val state = this.state

        val tag = CompoundTag()

        tag.put(PAIR, state.pair.toNbt())
        tag.putResourceLocation(MATERIAL, state.connection.material.id)
        tag.putInt(TERMINAL_A, state.terminalA)
        tag.putInt(TERMINAL_B, state.terminalB)
        tag.putQuantity(TEMPERATURE, thermal.mass.temperature)

        return tag
    }

    override fun loadCellData(tag: CompoundTag) {
        check(stateField == null) {
            "Loading but already initialized!"
        }

        val pair = GridEndpointPair.fromNbt(tag.getCompound(PAIR))
        val material = GridMaterials.getMaterial(tag.getResourceLocation(MATERIAL))
        val terminalA = tag.getInt(TERMINAL_A)
        val terminalB = tag.getInt(TERMINAL_B)

        this.stateField = State(
            GridConnection.create(pair, material),
            pair,
            terminalA,
            terminalB
        )

        initializeObjects()

        thermal.mass.temperature = tag.getQuantity(TEMPERATURE)
        savedTemperature = thermal.mass.temperature
        sentTemperature = thermal.mass.temperature
    }

    @SimObject
    val electrical = GridConnectionElectricalObject(this)

    @SimObject
    val thermal = GridConnectionThermalObject(this)

    private fun initializeObjects() {
        electrical.initialize()
        thermal.initialize()
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPre(this::simulationTick)
    }

    private var savedTemperature: Quantity<Temperature> = Quantity(-1.0, KELVIN)
    private var sentTemperature: Quantity<Temperature> = Quantity(-1.0, KELVIN)
    private var isMelting = false

    private fun simulationTick(dt: Double, phase: SimulationPhase) {
        if(isMelting) {
            return
        }

        thermal.mass.energy += abs(electrical.resistor.power) * dt

        val temperature = thermal.mass.temperature

        setChangedIf(!temperature.value.approxEq(!savedTemperature, TEMPERATURE_SAVE_EPS)) {
            savedTemperature = temperature
        }

        if(handle != null && !temperature.value.approxEq(!sentTemperature, TEMPERATURE_SEND_EPS)) {
            sentTemperature = temperature
            sendRenderUpdates()
        }

        if(thermal.mass.temperature > state.connection.material.meltingTemperature) {
            melt()
        }
    }

    private fun melt() {
        isMelting = true
        val material = state.connection.material
        val cable = state.connection.cable
        val level = graph.level

        schedulePre(0) {
            if(!isBeingRemoved) {
                isRemoving = true
                CellConnections.destroy(this, this.container!!)

                val numberOfParticles = ceil(cable.arcLength * material.explosionParticlesPerMeter).toInt()

                repeat(numberOfParticles) {
                    fun add(type: SimpleParticleType) {
                        val (px, py, pz) = cable.spline.evaluate(Random.nextDouble(0.0, 1.0))

                        level.sendParticles(
                            type,
                            px, py, pz,
                            1,
                            0.0, 0.0, 0.0,
                            Random.nextDouble(0.5, 2.0)
                        )
                    }

                    add(ParticleTypes.FLAME)
                    add(ParticleTypes.LARGE_SMOKE)
                }

                cable.blocks.forEach {
                    level.playSound(
                        null,
                        it,
                        SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                        randomFloat(0.9f, 1.0f), randomFloat(0.8f, 1.1f),
                    )
                }
            }
        }
    }

    override fun cutWithPliers(player: ServerPlayer, pliers: GridCablePliersItem) : Boolean {
        isRemoving = true
        CellConnections.destroy(this, this.container!!)
        return true
    }

    override fun onDestroyed() {
        super.onDestroyed()
        handle?.remove()
    }

    data class State(
        val connection: GridConnection,
        val pair: GridEndpointPair,
        val terminalA: Int,
        val terminalB: Int
    )

    data class NodeInfo(
        val cell: Cell,
        val terminal: Int,
        val endpointInfo: GridEndpointInfo
    )

    // Skip the sided checks:

    override fun electricalObjectPredicate(remote: ElectricalObject<*>): Boolean {
        return true
    }

    override fun cellConnectionPredicate(remote: Cell): Boolean {
        return true
    }

    /**
     * Fake container used when staging the connections.
     * */
    inner class StagingContainer(val level: ServerLevel, val a: Cell, val b: Cell) : CellContainer {
        override fun getCells() = listOf(this@GridConnectionCell)

        override fun neighborScan(actualCell: Cell) = listOf(
            CellAndContainerHandle.captureInScope(a),
            CellAndContainerHandle.captureInScope(b)
        )

        override val manager = CellGraphManager.getFor(level)
    }

    /**
     * Fake container used throughout the lifetime of the cell.
     * */
    inner class PermanentContainer : CellContainer {
        override fun getCells() = listOf(this@GridConnectionCell)

        override fun neighborScan(actualCell: Cell) = listOf(
            CellAndContainerHandle.captureInScope(this@GridConnectionCell.cellA),
            CellAndContainerHandle.captureInScope(this@GridConnectionCell.cellB)
        )

        override val manager: CellGraphManager
            get()  {
                check(this@GridConnectionCell.hasGraph) {
                    "Requires graph for permanent container"
                }

                return this@GridConnectionCell.graph.manager
            }
    }

    companion object {
        private const val MATERIAL = "material"
        private const val TERMINAL_A = "termA"
        private const val TERMINAL_B = "termB"
        private const val TEMPERATURE = "temperature"
        private const val PAIR = "pair"

        private const val TEMPERATURE_SAVE_EPS = 0.1
        private const val TEMPERATURE_SEND_EPS = 5.0

        fun createStaging(
            cellA: Cell,
            cellB: Cell,
            level: ServerLevel,
            endpointA: UUID,
            terminalA: Int,
            endpointB: UUID,
            terminalB: Int,
            cable: GridConnection,
        ) : GridConnectionCell {
            val locator =  Locators.buildLocator {
                it.put(GRID_ENDPOINT_PAIR, SortedUUIDPair.create(endpointA, endpointB))
                it.put(
                    BLOCK_RANGE, Pair(
                        cellA.locator.requireLocator(BLOCK) {
                            "Grid connection requires block locator A"
                        },
                        cellB.locator.requireLocator(BLOCK) {
                            "Grid connection requires block locator B"
                        }
                    )
                )
            }

            val cell = CellRegistry.GRID_CONNECTION.get().create(locator, CellEnvironment.evaluate(level, locator))

            cell.initializeFromStaging(
                level,
                cellA, terminalA,
                cellB, terminalB,
                cable
            )

            return cell
        }

        fun beginStaging(
            terminalA: CellTerminal,
            terminalB: CellTerminal,
            level: ServerLevel,
            cable: GridConnection,
        ) {
            val cell = createStaging(
                terminalA.cell,
                terminalB.cell,
                level,
                terminalA.gridEndpointInfo.id,
                terminalA.terminalID,
                terminalB.gridEndpointInfo.id,
                terminalB.terminalID,
                cable
            )

            terminalA.stagingCell = cell
            terminalB.stagingCell = cell
        }

        fun endStaging(
            terminalA: CellTerminal,
            terminalB: CellTerminal,
        ) {
            check(terminalA.stagingCell === terminalB.stagingCell)
            val cell = checkNotNull(terminalA.stagingCell)
            check(cell.container is StagingContainer)
            CellConnections.insertFresh(cell.container!!, cell)
            terminalA.stagingCell = null
            terminalB.stagingCell = null
            cell.setupPermanent()

            check(terminalA.cell.graph === terminalB.cell.graph) {
                "Staging failed - terminals did not have same graph"
            }

            check(terminalA.cell.graph === cell.graph) {
                "Staging failed - connection did not have expected graph"
            }
        }
    }
}

/**
 * Holds information about a remote [GridTerminal], in captured form (doesn't keep a reference to the [GridTerminal]
 * @param endpointInfo The [GridTerminal.gridEndpointInfo] of the remote terminal.
 * @param snapshot A capture of the remote terminal, that can be restored to fetch the terminal from the world.
 * */
data class GridPeer(val endpointInfo: GridEndpointInfo, val snapshot: CompoundTag)

/**
 * Item used to make a grid cable between two terminals.
 * @param material The material used by the connection.
 * @param itemsPerMeter The number of items per meter (along arc) of cable.
 * */
open class GridCableItem(val material: GridMaterial, val itemsPerMeter: Int) : Item(Properties()) {
    /**
     * Gets the number of cable items needed for the connection with [arcLength].
     * */
    fun getNumberOfItems(arcLength: Double) = max(1, ceil(arcLength * itemsPerMeter).toInt())

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

        fun successConsume(message: String, consume: Int) : InteractionResultHolder<ItemStack> {
            actualStack.tag = null
            tell(message)
            return InteractionResultHolder.success(actualStack.copyWithCount(actualStack.count - consume))
        }

        if (pLevel.isClientSide) {
            return fail()
        }

        pLevel as ServerLevel

        val hTarget = GridTerminalHandle.pick(pLevel, pPlayer)
            ?: return fail("No valid terminal selected!")

        if(!(hTarget.terminal as GridTerminalServer).acceptsMaterial(pPlayer, material)) {
            return fail("Incompatible material!")
        }

        if (actualStack.tag != null && !actualStack.tag!!.isEmpty) {
            val tag = actualStack.tag!!

            val hRemote = GridTerminalHandle.restoreImmediate(pLevel, tag)
                ?: return fail("The remote terminal has disappeared!")

            if (hTarget.isSameContainer(hRemote)) {
                return fail("Can't really do that!")
            }

            if (hRemote.terminal === hTarget.terminal) {
                return fail("Can't connect a terminal with itself!")
            }

            hRemote.terminal as GridTerminalServer

            if (hTarget.terminal.storage.hasConnectionWith(hRemote.terminal.gridEndpointInfo.id, hRemote.terminal.terminalID)) {
                check(hRemote.terminal.storage.hasConnectionWith(hTarget.terminal.gridEndpointInfo.id, hTarget.terminal.terminalID)) {
                    "Invalid reciprocal state - missing remote"
                }

                return fail("Can't do that!")
            } else {
                check(!hRemote.terminal.storage.hasConnectionWith(hTarget.terminal.gridEndpointInfo.id, hTarget.terminal.terminalID)) {
                    "Invalid reciprocal state - unexpected remote"
                }
            }

            val pair = GridEndpointPair.create(hTarget.terminal.gridEndpointInfo, hRemote.terminal.gridEndpointInfo)
            val gridCatenary = GridConnection.create(pair, material)

            val numberOfItems = getNumberOfItems(gridCatenary.cable.arcLength)

            if(!pPlayer.isCreative && numberOfItems > actualStack.count) {
                return fail("You need $numberOfItems items!")
            }

            if(GridCollisions.cablePlacementIntersects(pLevel, gridCatenary.cable, hTarget, hRemote)) {
                return fail("Stuff in the way!")
            }

            val connectionInfo = GridConnectionDescription(
                gridCatenary.material,
                gridCatenary.resistance
            )

            hTarget.terminal.beginConnectStaging(RemoteTerminalConnection(hRemote, connectionInfo))
            hRemote.terminal.beginConnectStaging(RemoteTerminalConnection(hTarget, connectionInfo))

            if(hTarget.terminal is CellTerminal && hRemote.terminal is CellTerminal) {
                GridConnectionCell.beginStaging(hTarget.terminal, hRemote.terminal, pLevel, gridCatenary)
            }

            hTarget.terminal.addConnection()
            hRemote.terminal.addConnection()

            if(hTarget.terminal is CellTerminal && hRemote.terminal is CellTerminal) {
                GridConnectionCell.endStaging(hTarget.terminal, hRemote.terminal)
            }

            hTarget.terminal.endConnectStaging()
            hRemote.terminal.endConnectStaging()

            return if(pPlayer.isCreative) {
                success("Connected successfully!")
            }
            else {
                successConsume("Connected successfully!", numberOfItems)
            }
        }

        actualStack.tag = hTarget.toNbt()

        tell("Start recorded!")

        return InteractionResultHolder.success(actualStack)
    }
}

open class GridCablePliersItem : Item(Properties()) {
    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)
        pTooltipComponents.add(Component.translatable("tooltip.eln2.grid_pliers.use").withStyle(ChatFormatting.GRAY))
    }

    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val itemStack = pPlayer.getItemInHand(pUsedHand)

        if(pLevel.isClientSide || pUsedHand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.pass(itemStack)
        }

        val pick = GridCollisions.pick(pLevel, pPlayer.getViewLine())
            ?: return InteractionResultHolder.fail(itemStack)

        val owner = GridConnectionManagerServer.getOwner(pLevel as ServerLevel, pick.first.cable.netID)
            ?: return InteractionResultHolder.fail(itemStack)

        val success = owner.cutWithPliers(pPlayer as ServerPlayer, this)

        return if(success) {
            if(!pPlayer.isCreative) {
                val item = GridMaterials.getGridConnectOrNull(pick.first.cable.material)

                if(item != null) {
                    val count = min(item.get().getNumberOfItems(pick.first.cable.cable.arcLength), 64)

                    if(count > 0) {
                        val stack = ItemStack(item.get(), count)
                        pLevel.addItem(pPlayer.position().x, pPlayer.position().y, pPlayer.position().z, stack)
                    }
                }
            }
            InteractionResultHolder(InteractionResult.SUCCESS, itemStack)
        }
        else {
            InteractionResultHolder(InteractionResult.FAIL, itemStack)
        }
    }
}
