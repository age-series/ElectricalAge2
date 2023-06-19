package org.eln2.mc.common.content

import com.google.common.util.concurrent.AtomicDouble
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.thermal.Simulator
import org.eln2.mc.*
import org.eln2.mc.common.blocks.foundation.MultiblockControllerEntity
import org.eln2.mc.common.blocks.foundation.MultiblockDefinition
import org.eln2.mc.common.blocks.foundation.MultiblockManager
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.data.DataNode
import org.eln2.mc.data.PowerField
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.scientific.ThermalBody
import kotlin.math.absoluteValue

class HeaterHeatPortCell(ci: CellCreateInfo) : Cell(ci) {
    @RaceCondition
    var provider: HeaterPowerPortCell? = null

    @SimObject
    val outputObj = HeatOutputObject(this)

    override fun onGraphChanged() {
        graph.subscribers.addPre(this::simulationTick)
    }

    override fun onRemoving() {
        graph.subscribers.remove(this::simulationTick)
    }

    private fun simulationTick(dt: Double, subscriberPhase: SubscriberPhase) {
        val provider = this.provider

        if(provider != null) {
            outputObj.body.energy += provider.getIncr()
        }
    }

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        super.appendBody(builder, config)

        builder.text("Provider", provider ?: "none")
    }

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
        val body = ThermalBody.createDefault(cell.envFldMap)
        override fun offerComponent(neighbour: ThermalObject) = ThermalComponentInfo(body)
        override fun addComponents(simulator: Simulator) = simulator.add(body)
    }

    companion object {
        private const val LINK_GRAPH = "LinkGraph"
        private const val LINK_POS = "LinkPos"
    }
}

class HeaterPowerPortCell(ci: CellCreateInfo, val onResistance: Double = 1.0, val offResistance: Double = 10e8) : Cell(ci) {
    override val dataNode = DataNode().also {
        it.data.withField(PowerField {
            portObj.power.absoluteValue
        })
    }

    @SimObject
    val portObj = activate<PowerPortObject>()

    @Behavior
    val converterBehavior = activate<ElectricalPowerConverterBehavior>()

    private var active = false

    private val atomicIncr = AtomicDouble()

    fun getIncr() = atomicIncr.getAndSet(0.0)

    fun setActive() {
        active = true
        portObj.resistance = onResistance
        setChanged()
    }

    fun setInactive() {
        active = false
        portObj.resistance = offResistance
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
            atomicIncr.addAndGet(behaviorContainer.get<ElectricalPowerConverterBehavior>().deltaEnergy)
        }
    }

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        super.appendBody(builder, config)
        builder.text("Active", active)
    }

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

        private val resistor = ComponentHolder {
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
