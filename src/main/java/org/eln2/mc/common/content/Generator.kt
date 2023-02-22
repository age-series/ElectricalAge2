package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.Mathematics.bbVec
import org.eln2.mc.annotations.CrossThreadAccess
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.ComponentHolder
import org.eln2.mc.common.cells.foundation.objects.ElectricalComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.ITickablePart
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.LibAgeExtensions.add
import org.eln2.mc.extensions.NbtExtensions.useSubTag
import org.eln2.mc.extensions.NumberExtensions.formatted
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import kotlin.math.abs

enum class GeneratorPowerDirection {
    Outgoing,
    Incoming
}

/**
 * Represents an Electrical Generator. It is characterised by a voltage and internal resistance.
 * */
class GeneratorObject : ElectricalObject(), IWailaProvider {
    var plusDirection = RelativeRotationDirection.Front
    var minusDirection = RelativeRotationDirection.Back

    var internalResistance: Double = 1.0
        set(value){
            field = value

            resistor.ifPresent {
                it.resistance = value
            }
        }

    var potential: Double = 1.0
        set(value){
            field = value

            source.ifPresent {
                it.potential = value
            }
        }

    private val resistor = ComponentHolder {
        Resistor().also { it.resistance = internalResistance }
    }

    private val source = ComponentHolder {
        VoltageSource().also { it.potential = potential }
    }

    val hasResistor get() = resistor.isPresent

    val resistorPower get() = resistor.instance.power

    val powerFlowDirection get() =
        if(resistor.instance.current > 0) GeneratorPowerDirection.Incoming
        else GeneratorPowerDirection.Outgoing

    override val maxConnections = 2

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return when(val dir = directionOf(neighbour)){
            plusDirection -> resistor.offerExternal()
            minusDirection -> source.offerNegative()
            else -> error("Unhandled neighbor direction $dir")
        }
    }

    override fun clearComponents() {
        resistor.clear()
        source.clear()
    }

    override fun addComponents(circuit: Circuit) {
        circuit.add(resistor)
        circuit.add(source)
    }

    override fun build() {
        resistor.connectInternal(source.offerPositive())

        connections.forEach { connectionInfo ->
            when(val direction = connectionInfo.direction){
                plusDirection -> resistor.connectExternal(this, connectionInfo)
                minusDirection -> source.connectNegative(this, connectionInfo)
                else -> error("Unhandled direction $direction")
            }
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.voltage(source.instance.potential)
        builder.text("Flow", powerFlowDirection)
        builder.power(abs(resistorPower))
    }
}

abstract class GeneratorCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(GeneratorObject())
    }

    val generatorObject = electricalObject as GeneratorObject
}

fun interface IBatteryVoltageFunction {
    fun computeVoltage(charge: Double): Double
}

fun interface IBatteryResistanceFunction {
    fun computeResistance(charge: Double): Double
}

object BatteryFunctions {
    fun linV(maxVoltage: Double): IBatteryVoltageFunction {
        return IBatteryVoltageFunction {
            it * maxVoltage
        }
    }

    fun constR(resistance: Double): IBatteryResistanceFunction{
        return IBatteryResistanceFunction {
            resistance
        }
    }
}

data class BatteryModel(
    val voltageFunction: IBatteryVoltageFunction,
    val resistanceFunction: IBatteryResistanceFunction,
    val energyCapacity: Double)

object BatteryModels {
    fun linVConstR(maxVoltage: Double, resistance: Double, capacity: Double): BatteryModel{
        return BatteryModel(BatteryFunctions.linV(maxVoltage), BatteryFunctions.constR(resistance), capacity)
    }
}

class BatteryCell(pos: CellPos, id: ResourceLocation, val model: BatteryModel) : GeneratorCell(pos, id) {
    companion object {
        private const val ENERGY = "energy"
    }

    var energy = 0.0

    private val energyUpdate = AtomicUpdate<Double>()

    val charge get() = energy / model.energyCapacity

    @CrossThreadAccess
    fun deserializeNbt(tag: CompoundTag) {
        energyUpdate.setLatest(tag.getDouble(ENERGY))
    }

    @CrossThreadAccess
    fun serializeNbt(): CompoundTag{
        val tag = CompoundTag()

        tag.putDouble(ENERGY, energy)

        return tag
    }

    override fun onGraphChanged() {
        super.onGraphChanged()

        graph.addSubscriber(this::simulationTick)
    }

    override fun onRemoving() {
        super.onRemoving()

        graph.removeSubscriber(this::simulationTick)
    }

    private fun applyExternalUpdates(){
        energyUpdate.consume { energy = it }
    }

    private fun simulateEnergyFlow(elapsed: Double) {
        // Get energy transfer

        val transferredEnergy = abs(generatorObject.resistorPower * elapsed)

        if(generatorObject.powerFlowDirection == GeneratorPowerDirection.Incoming){
            // Add energy into the system.

            energy += transferredEnergy
        } else {
            // Remove energy from the system.

            energy -= transferredEnergy
        }

        // Clamp energy
        energy = energy.coerceIn(0.0, model.energyCapacity)
    }

    private fun simulationTick(elapsed: Double){
        applyExternalUpdates()

        if(!generatorObject.hasResistor){
            return
        }

        simulateEnergyFlow(elapsed)

        generatorObject.potential = model.voltageFunction.computeVoltage(charge)
        generatorObject.internalResistance = model.resistanceFunction.computeResistance(charge)
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        super.appendBody(builder, config)
        builder.text("Charge", "${(charge * 100.0).formatted()}%")
        builder.energy(energy)
    }
}

class BatteryPart(id: ResourceLocation, placementContext: PartPlacementContext, provider: CellProvider):
    CellPart(id, placementContext, provider),
    ITickablePart {

    companion object {
        private const val BATTERY = "battery"
    }

    override val baseSize = bbVec(6.0, 8.0, 12.0)

    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.BATTERY).also {
            it.downOffset = bbOffset(8.0)
        }
    }

    override fun saveCustomSimData(): CompoundTag {
        return CompoundTag().also {
            it.put(BATTERY, batteryCell.serializeNbt())
        }
    }

    override fun loadCustomSimData(tag: CompoundTag) {
        tag.useSubTag(BATTERY) { batteryCell.deserializeNbt(it) }
    }

    override fun onAdded() {
        super.onAdded()

        if(!placementContext.level.isClientSide){
            placementContext.multipart.addTicker(this)
        }
    }

    private val batteryCell get() = cell as BatteryCell

    override fun tick() {
        invalidateSave()
    }
}
