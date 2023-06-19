package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.*
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.mathematics.DirectionMask
import org.eln2.mc.data.withDirectionActualRule
import org.eln2.mc.data.Energy
import org.eln2.mc.data.Quantity
import org.eln2.mc.data.abs
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.bbVec
import org.eln2.mc.mathematics.evaluate
import org.eln2.mc.mathematics.lerp
import org.eln2.mc.mathematics.map
import org.eln2.mc.scientific.Datasets
import org.eln2.mc.scientific.ThermalBody

interface BatteryView {
    val model: BatteryModel
    /**
     * Gets the total energy stored in the battery/
     * */
    val energy: Quantity<Energy>

    /**
     * Gets the total energy exchanged by this battery.
     * */
    val energyIo: Quantity<Energy>

    /**
     * Gets the battery current. This value's sign depends on the direction of flow.
     * If the current is incoming, it should be positive. If it is outgoing, it should be negative.
     * */
    val current: Double

    /**
     * Gets the life parameter of the battery.
     * */
    val life: Double

    /**
     * Gets the number of charge-discharge cycles this battery has been trough.
     * */
    val cycles: Double

    /**
     * Gets the charge percentage.
     * */
    val charge: Double

    /**
     * Gets the charge percentage, mapped using the battery's threshold parameter, as per [BatteryModel.damageChargeThreshold].
     * This value may be negative if the charge is under threshold.
     * */
    val thresholdCharge: Double

    /**
     * Gets the temperature of the battery.
     * */
    val temperature: Temperature
}

/**
 * The [BatteryVoltageFunction] is used to compute the voltage of the battery based on the battery's state.
 * */
fun interface BatteryVoltageFunction {
    fun computeVoltage(battery: BatteryView, dt: Double): Double
}

/**
 * The [BatteryResistanceFunction] is used to compute the internal resistance of the battery based on the battery's state.
 * It should never be zero, though this is not enforced and will likely result in a simulation error.
 * */
fun interface BatteryResistanceFunction {
    fun computeResistance(battery: BatteryView, dt: Double): Double
}

/**
 * The [BatteryDamageFunction] computes a damage value in time, based on the battery's current state.
 * These values are deducted from the battery's life parameter. The final parameter is clamped.
 * */
fun interface BatteryDamageFunction {
    fun computeDamage(battery: BatteryView, dt: Double): Double
}

/**
 * The [BatteryEnergyCapacityFunction] computes the capacity of the battery based on the battery's state.
 * This must be a value ranging from 0-1. The result is clamped to that range.
 * */
fun interface BatteryEnergyCapacityFunction {
    fun computeCapacity(battery: BatteryView): Double
}

object BatteryVoltageModels {
    /**
     * Gets a 12V Wet Cell Lead Acid Battery voltage function.
     * */
    val WET_CELL_12V = BatteryVoltageFunction { view, _ ->
        val dataset = Datasets.LEAD_ACID_12V_WET
        val temperature = view.temperature.kelvin

        if(view.charge > view.model.damageChargeThreshold){
            dataset.evaluate(view.charge, temperature)
        }
        else{
            val progress = map(
                view.charge,
                0.0,
                view.model.damageChargeThreshold,
                0.0,
                1.0
            )

            val ceiling = dataset.evaluate(view.model.damageChargeThreshold, temperature)

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
        0.0
    )
}

data class BatteryModel(
    val voltageFunction: BatteryVoltageFunction,
    val resistanceFunction: BatteryResistanceFunction,
    val damageFunction: BatteryDamageFunction,
    val capacityFunction: BatteryEnergyCapacityFunction,

    /**
     * The energy capacity of the battery. This is the total energy that can be stored.
     * */
    val energyCapacity: Quantity<Energy>,

    /**
     * The charge percentage where, if the battery continues to discharge, it should start receiving damage.
     * */
    val damageChargeThreshold: Double,

    val material: Material,
    val mass: Double,
    val surfaceArea: Double)

data class BatteryState(val energy: Quantity<Energy>, val life: Double, val energyIo: Quantity<Energy>)

class BatteryCell(ci: CellCreateInfo, override val model: BatteryModel) : Cell(ci), BatteryView {
    companion object {
        private const val ENERGY = "energy"
        private const val LIFE = "life"
        private const val ENERGY_IO = "energyIo"
    }

    @SimObject
    val generatorObj = VRGeneratorObject(this, dirActualMap()).also {
        it.ruleSet.withDirectionActualRule(DirectionMask.FRONT + DirectionMask.BACK)
    }

    @SimObject
    val thermalWireObj = ThermalWireObject(this).also {
        it.body = ThermalBody(
            ThermalMass(model.material, null, model.mass),
            model.surfaceArea
        )
    }

    init {
        ruleSet.withDirectionActualRule(DirectionMask.FRONT + DirectionMask.BACK)
    }

    init {
        behaviorContainer.apply {
            withElectricalPowerConverter { generatorObj.generatorPower }
            withElectricalHeatTransfer { thermalWireObj.body }
        }
    }

    override var energy = Quantity<Energy>(0.0)

    override var energyIo = Quantity<Energy>(0.0)
        private set

    override var life = 1.0
        private set

    override val cycles
        get() = energyIo / model.energyCapacity

    override val current get() = generatorObj.generatorCurrent

    private val stateUpdate = AtomicUpdate<BatteryState>()

    override val charge get() = energy / model.energyCapacity

    override val thresholdCharge get() = map(
        charge,
        model.damageChargeThreshold,
        1.0,
        0.0,
        1.0
    )

    override val temperature: Temperature
        get() = thermalWireObj.body.temp

    /**
     * Gets the capacity coefficient of this battery. It is computed using the [BatteryModel.capacityFunction].
     * */
    val capacityCoefficient
        get() = model.capacityFunction.computeCapacity(this).coerceIn(0.0, 1.0)

    /**
     * Gets the adjusted energy capacity of this battery. It is equal to the base energy capacity [BatteryModel.energyCapacity], scaled by [capacityCoefficient].
     * */
    val adjustedEnergyCapacity
        get() = model.energyCapacity * capacityCoefficient

    @CrossThreadAccess
    fun deserializeNbt(tag: CompoundTag) {
        stateUpdate.setLatest(
            BatteryState(
                tag.getQuantity<Energy>(ENERGY),
                tag.getDouble(LIFE),
                tag.getQuantity<Energy>(ENERGY_IO)
            )
        )
    }

    @CrossThreadAccess
    fun serializeNbt(): CompoundTag {
        val tag = CompoundTag()

        tag.putDouble(ENERGY, !energy)
        tag.putDouble(LIFE, life)
        tag.putDouble(ENERGY_IO, !energyIo)

        return tag
    }

    override fun saveCellData(): CompoundTag {
        return serializeNbt()
    }

    override fun loadCellData(tag: CompoundTag) {
        deserializeNbt(tag)
    }

    override fun onGraphChanged() {
        graph.subscribers.addPre(this::simulationTick)
    }

    override fun onRemoving() {
        graph.subscribers.remove(this::simulationTick)
    }

    private fun applyExternalUpdates(){
        stateUpdate.consume {
            energy = it.energy
            life = it.life
            energyIo = it.energyIo

            graph.setChanged()
        }
    }

    private fun simulateEnergyFlow(elapsed: Double) {
        // Get energy transfer:
        val transfer = Quantity<Energy>(generatorObj.generatorPower * elapsed)

        // Update total IO:
        energyIo += abs(transfer)

        energy -= transfer

        val capacity = adjustedEnergyCapacity

        if(energy < 0.0){
            Eln2.LOGGER.error("Negative battery energy $pos")

            energy = Quantity(0.0)
        }
        else if(energy > capacity) {
            val extraEnergy = energy - capacity

            energy -= extraEnergy

            // Conserve energy by increasing temperature:
            thermalWireObj.body.energy += !extraEnergy
        }
    }

    private fun simulationTick(elapsed: Double, phase: SubscriberPhase){
        applyExternalUpdates()

        if(!generatorObj.hasResistor){
            return
        }

        simulateEnergyFlow(elapsed)

        generatorObj.potential = model.voltageFunction.computeVoltage(this, elapsed)
        generatorObj.resistance = model.resistanceFunction.computeResistance(this, elapsed)
        life -= model.damageFunction.computeDamage(this, elapsed)
        life = life.coerceIn(0.0, 1.0)

        // FIXME: Find condition
        graph.setChanged()
    }

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        super.appendBody(builder, config)
        builder.text("Charge", thresholdCharge.formattedPercentN())
        builder.text("Life", life.formattedPercentN())
        builder.text("Cycles", cycles.formatted())
        builder.text("Capacity", capacityCoefficient.formattedPercentN())
        builder.energy(!energy)
    }
}

class BatteryPart(id: ResourceLocation, placementContext: PartPlacementInfo, provider: CellProvider): CellPart<BasicPartRenderer>(id, placementContext, provider), ItemPersistentPart {
    companion object {
        private const val BATTERY = "battery"
    }

    override val sizeActual = bbVec(6.0, 8.0, 12.0)

    override fun createRenderer() = BasicPartRenderer(this, PartialModels.BATTERY).also {
        it.downOffset = PartialModels.bbOffset(8.0)
    }

    private val batteryCell get() = cell as BatteryCell

    override fun saveItemTag(tag: CompoundTag) {
        tag.put(BATTERY, batteryCell.serializeNbt())
    }

    override fun loadItemTag(tag: CompoundTag?) {
        tag?.useSubTagIfPreset(BATTERY, batteryCell::deserializeNbt)
    }

    override val order: PersistentPartLoadOrder = PersistentPartLoadOrder.AfterSim
}
