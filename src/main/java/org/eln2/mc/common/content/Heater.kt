package org.eln2.mc.common.content

import com.google.common.util.concurrent.AtomicDouble
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.RaceCondition
import org.eln2.mc.common.blocks.foundation.*
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.sim.ThermalBody
import kotlin.math.absoluteValue

class HeaterHeatPortCell(pos: CellPos, id: ResourceLocation) : Cell(pos, id) {
    @RaceCondition
    var provider: HeaterPowerPortCell? = null

    private val output = lazy {
        // Lazy because we are using getEnvironmentTemp which requires the level
        HeatOutputObject(this)
    }

    override fun onGraphChanged() {
        graph.subscribers.addPre(this::simulationTick)
    }

    override fun onRemoving() {
        graph.subscribers.remove(this::simulationTick)
    }

    private fun simulationTick(dt: Double, subscriberPhase: SubscriberPhase) {
        val provider = this.provider

        if(provider != null) {
            output.value.body.thermalEnergy += provider.getIncr()
        }
    }

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        super.appendBody(builder, config)

        builder.text("Provider", provider ?: "none")
    }

    override fun createObjSet() = SimulationObjectSet(output.value)

    private var loadTag: CompoundTag? = null

    override fun loadCellData(tag: CompoundTag) {
        loadTag = tag
    }

    override fun onWorldLoadedPreSolver() {
        loadTag?.also {
            if(it.contains(LINK_GRAPH)) {
                val linkGraph = it.getUUID(LINK_GRAPH)
                val targetPos = it.getCellPos(LINK_POS)
                val targetGraph = graph.manager.getGraph(linkGraph)
                this.provider = targetGraph.getCell(targetPos) as HeaterPowerPortCell
            }

            loadTag = null
        }
    }

    override fun saveCellData(): CompoundTag {
        return CompoundTag().also {
            val provider = this.provider

            if(provider != null) {
                it.putUUID(LINK_GRAPH, provider.graph.id)
                it.putCellPos(LINK_POS, provider.pos)
            }
        }
    }

    class HeatOutputObject(cell: Cell) : ThermalObject(cell) {
        val body = ThermalBody.createDefault().also {
            it.temperature = cell.getEnvironmentTemp()
        }

        override fun offerComponent(neighbour: ThermalObject) = ThermalComponentInfo(body)
        override fun addComponents(simulator: Simulator) = simulator.add(body)
    }

    companion object {
        private const val LINK_GRAPH = "LinkGraph"
        private const val LINK_POS = "LinkPos"
    }
}

class HeaterPowerPortCell(pos: CellPos, id: ResourceLocation, val onResistance: Double = 1.0, val offResistance: Double = 10e10) : Cell(pos, id) {
    private val port = PowerPortObject(this)

    private var active = false

    init { behaviors.withElectricalPowerConverter { port.power.absoluteValue } }

    private val atomicIncr = AtomicDouble()

    fun getIncr() = atomicIncr.getAndSet(0.0)

    fun setActive() {
        active = true
        port.resistance = onResistance
        setChanged()
    }

    fun setInactive() {
        active = false
        port.resistance = offResistance
        setChanged()
    }

    init {
        setInactive()
    }

    override fun onGraphChanged() {
        graph.subscribers.addPre(this::simulationTick)
    }

    override fun onRemoving() {
        graph.subscribers.remove(this::simulationTick)
    }

    private fun simulationTick(d: Double, subscriberPhase: SubscriberPhase) {
        if(active) {
            atomicIncr.addAndGet(behaviors.get<ElectricalPowerConverterBehavior>().deltaEnergy)
        }
    }

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        super.appendBody(builder, config)
        builder.text("Active", active)
    }

    override fun createObjSet() = SimulationObjectSet(port)

    class PowerPortObject(cell: Cell) : ElectricalObject(cell) {
        var resistance = 1.0
            set(value) {
                field = value

                if(resistor.isPresent) {
                    resistor.instance.resistance = value
                }
            }

        val power get() =
            if(resistor.isPresent) resistor.instance.power
            else 0.0

        private val resistor = ElectricalComponentHolder {
            Resistor().also { it.resistance = this.resistance }
        }

        override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
            return resistor.offerExternal()
        }

        override fun clearComponents() {
            resistor.clear()
        }

        override fun addComponents(circuit: Circuit) {
            circuit.add(resistor)
        }

        override fun build() {
            resistor.groundInternal()

            super.build()
        }
    }

    override fun loadCellData(tag: CompoundTag) {
        if(tag.getBoolean(ACTIVE)) {
            setActive()
        }
    }

    override fun saveCellData(): CompoundTag {
        return CompoundTag().also { it.putBoolean(ACTIVE, active) }
    }

    companion object {
        private const val ACTIVE = "active"
    }
}

class HeaterCtrlBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(Content.MB_HEATER_CTRL_BLOCK_ENTITY.get(), pos, state), MultiblockControllerEntity {
    override val manager = MultiblockManager(pos, state.facing(), vars.keys)

    override fun onMultiblockFormed(variant: MultiblockDefinition) {
        val powerPort = level!!.getCell<HeaterPowerPortCell>(manager, vars[variant]!!.powerPortId)
        val heatPort = level!!.getCell<HeaterHeatPortCell>(manager, vars[variant]!!.heatPortId)

        runSuspended(powerPort, heatPort) {
            heatPort.provider = powerPort
            powerPort.setActive()
        }
    }

    override fun onMultiblockDestroyed(variant: MultiblockDefinition) {
        unlink(variant)
    }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)
        manager.enqueueUserScan(pLevel)
    }

    private fun unlink(v: MultiblockDefinition) {
        level!!.getCellOrNull<HeaterHeatPortCell>(manager, vars[v]!!.heatPortId)?.also {
            it.graph.runSuspended { it.provider = null }
        }

        level!!.getCellOrNull<HeaterPowerPortCell>(manager, vars[v]!!.powerPortId)?.also {
            it.graph.runSuspended { it.setInactive() }
        }
    }

    override fun onDestroyed() {
        manager.getVariant(level ?: return)?.also {
            unlink(it)
        }
    }

    companion object {
        private data class PortSet(val powerPortId: BlockPos, val heatPortId: BlockPos)

        private val vars = mapOf(
            MultiblockDefinition.load("heater/power_left") to PortSet(
                powerPortId = BlockPos(1, 0, -1),
                heatPortId = BlockPos(1, 0, 1)
            ),
            MultiblockDefinition.load("heater/heat_left") to PortSet(
                powerPortId = BlockPos(1, 0, 1),
                heatPortId = BlockPos(1, 0, -1)
            )
        )
    }
}

