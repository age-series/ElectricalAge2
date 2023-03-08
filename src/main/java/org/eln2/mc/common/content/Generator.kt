package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.mathematics.HermiteSpline
import org.eln2.mc.mathematics.Functions.bbVec
import org.eln2.mc.mathematics.Functions.lerp
import org.eln2.mc.mathematics.Functions.map
import org.eln2.mc.annotations.CrossThreadAccess
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.cells.foundation.behaviors.withElectricalHeatTransfer
import org.eln2.mc.common.cells.foundation.behaviors.withElectricalPowerConverter
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
import org.eln2.mc.extensions.LibAgeExtensions.setPotentialEpsilon
import org.eln2.mc.extensions.LibAgeExtensions.setResistanceEpsilon
import org.eln2.mc.extensions.NbtExtensions.useSubTag
import org.eln2.mc.extensions.NumberExtensions.formatted
import org.eln2.mc.extensions.NumberExtensions.formattedPercentN
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.sim.ThermalBody
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
            resistor.ifPresent { it.setResistanceEpsilon(value) }
        }

    var potential: Double = 1.0
        set(value){
            field = value
            source.ifPresent { it.setPotentialEpsilon(value) }
        }

    private val resistor = ComponentHolder {
        Resistor().also { it.resistance = internalResistance }
    }

    private val source = ComponentHolder {
        VoltageSource().also { it.potential = potential }
    }

    val hasResistor get() = resistor.isPresent

    val resistorPower get() = resistor.instance.power

    val resistorCurrent get() = resistor.instance.current

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

    val generatorObject get() = electricalObject as GeneratorObject
}

interface IBatteryView {
    val model: BatteryModel
    val energy: Double
    val energyIo: Double
    val current: Double
    val life: Double
    val cycles: Double
    val charge: Double
    val thresholdCharge: Double
}

fun interface IBatteryVoltageFunction {
    fun computeVoltage(battery: IBatteryView, dt: Double): Double
}

fun interface IBatteryResistanceFunction {
    fun computeResistance(battery: IBatteryView, dt: Double): Double
}

fun interface IBatteryDamageFunction {
    fun computeDamage(battery: IBatteryView, dt: Double): Double
}

fun interface IBatteryEnergyCapacityFunction {
    fun computeCapacity(battery: IBatteryView): Double
}

object VoltageModels {
    private val VOLTAGE_12V_LEAD_ACID =
        HermiteSpline.loadSpline("battery_models/12v_lead_acid_voltage.spline")

    val TEST = IBatteryVoltageFunction { view, _ ->
        if(view.charge > view.model.damageChargeThreshold){
            VOLTAGE_12V_LEAD_ACID.evaluate(view.thresholdCharge)
        }
        else{
            val progress = map(
                view.charge,
                0.0,
                view.model.damageChargeThreshold,
                0.0,
                1.0)

            val ceiling = VOLTAGE_12V_LEAD_ACID.evaluate(0.0)

            lerp(0.0, ceiling, progress)
        }
    }
}

object BatterySpecificHeats {
    // https://www.batterydesign.net/thermal/
    val PB_ACID_VENTED_FLOODED = 1080.0
    val PB_ACID_VRLA_GEL = 900.0
    val VRLA_AGM = 792.0
    val LI_ION_NCA = 830.0
    val LI_ION_NMC = 1040.0
    val LI_ION_NFP = 1145.0
}

object BatteryMaterials {
    val PB_ACID_TEST = Material(
        0.0,
        Material.LATEX_RUBBER.thermalConductivity,
        BatterySpecificHeats.PB_ACID_VENTED_FLOODED,
        0.0)
}

data class BatteryModel(
    val voltageFunction: IBatteryVoltageFunction,
    val resistanceFunction: IBatteryResistanceFunction,
    val damageFunction: IBatteryDamageFunction,
    val capacityFunction: IBatteryEnergyCapacityFunction,
    val energyCapacity: Double,
    val damageChargeThreshold: Double,
    val material: Material,
    val mass: Double,
    val surfaceArea: Double)

data class BatteryState(val energy: Double, val life: Double, val energyIo: Double)

class BatteryCell(pos: CellPos, id: ResourceLocation, override val model: BatteryModel) :
    GeneratorCell(pos, id),
    IBatteryView {

    companion object {
        private const val ENERGY = "energy"
        private const val LIFE = "life"
        private const val ENERGY_IO = "energyIo"
    }

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(GeneratorObject(), ThermalWireObject(this).also {
            it.body = ThermalBody(
                ThermalMass(model.material, null, model.mass),
                model.surfaceArea)
        })
    }

    init {
        behaviors.apply {
            withElectricalPowerConverter { generatorObject.resistorPower }
            withElectricalHeatTransfer { thermalWireObject.body }
        }
    }

    override var energy = 0.0

    override var energyIo = 0.0
        private set

    override var life = 1.0
        private set

    override val cycles
        get() = energyIo / model.energyCapacity

    override val current
        get() = generatorObject.resistorCurrent

    private val stateUpdate = AtomicUpdate<BatteryState>()

    override val charge get() = energy / model.energyCapacity

    override val thresholdCharge get() = map(
        charge,
        model.damageChargeThreshold,
        1.0,
        0.0,
        1.0)

    val capacityCoefficient
        get() = model.capacityFunction.computeCapacity(this).coerceIn(0.0, 1.0)

    val adjustedEnergyCapacity
        get() = model.energyCapacity * capacityCoefficient

    @CrossThreadAccess
    fun deserializeNbt(tag: CompoundTag) {
        stateUpdate.setLatest(BatteryState(
            tag.getDouble(ENERGY),
            tag.getDouble(LIFE),
            tag.getDouble(ENERGY_IO)))
    }

    @CrossThreadAccess
    fun serializeNbt(): CompoundTag{
        val tag = CompoundTag()

        tag.putDouble(ENERGY, energy)
        tag.putDouble(LIFE, life)
        tag.putDouble(ENERGY_IO, energyIo)

        return tag
    }

    override fun onGraphChanged() {
        graph.subscribers.addPreInstantaneous(this::simulationTick)
    }

    override fun onRemoving() {
        graph.subscribers.removeSubscriber(this::simulationTick)
    }

    private fun applyExternalUpdates(){
        stateUpdate.consume {
            energy = it.energy
            life = it.life
            energyIo = it.energyIo
        }
    }

    private fun simulateEnergyFlow(elapsed: Double) {
        // Get energy transfer

        val transferredEnergy = abs(generatorObject.resistorPower * elapsed)

        // Update total IO

        energyIo += transferredEnergy

        if(generatorObject.powerFlowDirection == GeneratorPowerDirection.Incoming){
            // Add energy into the system.

            energy += transferredEnergy
        } else {
            // Remove energy from the system.

            energy -= transferredEnergy
        }

        val capacity = adjustedEnergyCapacity

        // Clamp energy
        energy = energy.coerceIn(0.0, capacity)
    }

    private fun simulationTick(elapsed: Double, phase: SubscriberPhase){
        applyExternalUpdates()

        if(!generatorObject.hasResistor){
            return
        }

        simulateEnergyFlow(elapsed)

        generatorObject.potential = model.voltageFunction.computeVoltage(this, elapsed)
        generatorObject.internalResistance = model.resistanceFunction.computeResistance(this, elapsed)
        life -= model.damageFunction.computeDamage(this, elapsed)
        life = life.coerceIn(0.0, 1.0)
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        super.appendBody(builder, config)

        builder.text("Charge", thresholdCharge.formattedPercentN())
        builder.text("Life", life.formattedPercentN())
        builder.text("Cycles", cycles.formatted())
        builder.text("Capacity", capacityCoefficient.formattedPercentN())
        builder.energy(energy)
        builder.current(current)
    }

    val thermalWireObject = thermalObject as ThermalWireObject
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
