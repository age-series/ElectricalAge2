package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.eln2.mc.annotations.CrossThreadAccess
import org.eln2.mc.client.render.PartialModels
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
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.extensions.NbtExtensions.useSubTagIfPreset
import org.eln2.mc.extensions.NbtExtensions.withSubTag
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

    override val maxConnections = 1

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return resistor.offerExternal()
    }

    override fun clearComponents() {
        resistor.clear()
        source.clear()
    }

    override fun addComponents(circuit: Circuit) {
        circuit.add(resistor.instance)
        circuit.add(source.instance)
    }

    override fun build() {
        resistor.connectInternal(source.offerExternal())

        source.groundInternal()

        if(connections.size == 0){
            return
        }

        val connectionInfo = connections[0].obj.offerComponent(this)

        resistor.connectExternal(connectionInfo)
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.text("Resistor Current", resistor.instance.current)
        builder.text("Voltage Source Potential", source.instance.potential)
        builder.text("Flow", powerFlowDirection)
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
    var energy = 0.0

    private val energyUpdate = AtomicUpdate<Double>()

    val charge get() = energy / model.energyCapacity

    @CrossThreadAccess
    fun deserializeNbt(tag: CompoundTag) {
        energyUpdate.setLatest(tag.getDouble("energy"))
    }

    @CrossThreadAccess
    fun serializeNbt(): CompoundTag{
        val tag = CompoundTag()

        tag.putDouble("energy", energy)

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

        builder.energy(energy)
    }
}

class BatteryPart(id: ResourceLocation, placementContext: PartPlacementContext, provider: CellProvider):
    CellPart(id, placementContext, provider){
    override val baseSize: Vec3 = Vec3(1.0, 1.0, 1.0)

    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.BATTERY)
    }

    override fun getSaveTag(): CompoundTag? {
        return super.getSaveTag()?.withSubTag("BatteryData"){
            it.put("battery", batteryCell.serializeNbt())
        }
    }

    override fun loadFromTag(tag: CompoundTag) {
        super.loadFromTag(tag)

        tag.useSubTagIfPreset("BatteryData"){
            batteryCell.deserializeNbt(it.getCompound("battery"))
        }
    }

    private val batteryCell get() = cell as BatteryCell
}
