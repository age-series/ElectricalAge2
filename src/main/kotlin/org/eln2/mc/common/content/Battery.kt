@file:Suppress("LocalVariableName")

package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import org.ageseries.libage.data.*
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.thermal.Temperature
import org.eln2.mc.*
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.client.render.foundation.PartRendererSupplier
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.data.*
import org.eln2.mc.data.abs
import org.eln2.mc.integration.WailaNode
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.*
import kotlin.math.abs
import kotlin.math.pow

interface BatteryView {
    val model: BatteryModel
    /**
     * Gets the total amount of energy stored in the battery/
     * */
    val energy: Quantity<Energy>
    /**
     * Gets the total energy exchanged by this battery.
     * */
    val totalEnergyTransferred: Quantity<Energy>
    /**
     * Gets the battery current. This value's sign depends on the direction of flow.
     * By convention, if the current is incoming, it is positive. If it is outgoing, it is negative.
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
    val safeCharge: Double
    /**
     * Gets the temperature of the battery.
     * */
    val temperature: Temperature
    /**
     * Gets the current power. If positive, this is power going out. Otherwise, this is power coming in.
     * */
    val sourcePower: Quantity<Power>
    /**
     * Gets the energy increment this tick. It is equal to **[sourcePower] * dT**.
     * The signs are as per [sourcePower]
     * */
    val energyIncrement: Quantity<Energy>
}

/**
 * Computes the voltage of the battery based on the battery's state.
 * */
fun interface BatteryVoltageFunction {
    fun computeVoltage(battery: BatteryView): Quantity<Voltage>
}

/**
 * Computes the internal resistance of the battery based on the battery's state.
 * It should never be zero, though this is not enforced and will likely result in a simulation error.
 * */
fun interface BatteryResistanceFunction {
    fun computeResistance(battery: BatteryView): Quantity<Resistance>
}

/**
 * Computes a damage increment, based on the battery's current state.
 * These values are deducted from the battery's life parameter (so they should be positive). The final parameter is clamped.
 * */
fun interface BatteryDamageFunction {
    fun computeDamage(battery: BatteryView, dt: Double): Double
}

/**
 * Computes the capacity of the battery based on the battery's state.
 * This must be a value ranging from 0-1. The result is clamped.
 * */
fun interface BatteryEnergyCapacityFunction {
    fun computeCapacity(battery: BatteryView): Double
}

private val LEAD_ACID_12V_WET_VOLTAGE = loadCsvGrid2("lead_acid_12v/ds_wet.csv")

object BatteryVoltageModels {
    val WET_CELL_12V = BatteryVoltageFunction { view ->
        val dataset = LEAD_ACID_12V_WET_VOLTAGE
        val temperature = view.temperature.kelvin

        if (view.charge > view.model.damageChargeThreshold) {
            Quantity(dataset.evaluate(view.charge, temperature), VOLT)
        } else {
            val datasetCeiling = dataset.evaluate(view.model.damageChargeThreshold, temperature)

            Quantity(lerp(
                0.0,
                datasetCeiling,
                map(
                    view.charge,
                    0.0,
                    view.model.damageChargeThreshold,
                    0.0,
                    1.0
                )
            ), VOLT)
        }
    }
}

object BatteryModels {
    val LEAD_ACID_12V = BatteryModel(
        voltageFunction = BatteryVoltageModels.WET_CELL_12V,
        resistanceFunction = { _ -> Quantity(20.0, MILLIOHM)},
        damageFunction = { battery, dt ->
            var damage = 0.0

            damage += dt * (1.0 / 3.0) * 1e-6 // 1 month
            damage += !(abs(battery.energyIncrement) / (!battery.model.energyCapacity * 50.0))
            damage += dt * abs(battery.current).pow(1.12783256261) * 1e-7 *
                if(battery.safeCharge > 0.0) 1.0
                else map(battery.charge, 0.0, battery.model.damageChargeThreshold, 1.0, 5.0)

            //println("T: ${battery.life / (damage / dt)}")

            damage
        },
        capacityFunction = { battery ->
            battery.life.pow(0.5)
        },
        energyCapacity = Quantity(2.2, kWh),
        0.5,
        BatteryMaterials.PB_ACID_TEST,
        Quantity(10.0, KG),
        Quantity(6.0, M2)
    )
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
     * The energy capacity of the battery. This is the total amount of energy that can be stored.
     * */
    val energyCapacity: Quantity<Energy>,
    /**
     * The charge percentage at which, if the battery continues to discharge, it should start receiving extra damage.
     * */
    val damageChargeThreshold: Double,
    /**
     * Gets the "material" the battery is made of. Since the simulation treats the battery as one homogenous mass,
     * a material should be chosen, that closely resembles the properties of the battery, as seen from the outside.
     * */
    val material: Material,
    /**
     * Gets the mass of the battery, in kilograms.
     * */
    val mass: Quantity<Mass>,
    /**
     * Gets the surface area of the battery, used in thermal connections.
     * */
    val surfaceArea: Quantity<Area>,
)

data class BatteryState(
    /**
     * The total amount of energy stored in the battery.
     * */
    val energy: Quantity<Energy>,
    /**
     * The life parameter of the battery.
     * */
    val life: Double,
    /**
     * The total amount of energy received and sent.
     * */
    val totalEnergyTransferred: Quantity<Energy>
)

class BatteryCell(
    ci: CellCreateInfo,
    override val model: BatteryModel,
    plusDir: Base6Direction3d = Base6Direction3d.Front,
    minusDir: Base6Direction3d = Base6Direction3d.Back
) : Cell(ci), BatteryView {
    companion object {
        private const val ENERGY = "energy"
        private const val LIFE = "life"
        private const val ENERGY_IO = "energyIo"
        private const val VOLTAGE_EPS = 1e-4
        private const val RESISTANCE_EPS = 1e-2
        private const val LIFE_EPS = 1e-3
    }

    @SimObject
    val generator = VRGeneratorObject<Cell>(this, directionPoleMapPlanar(plusDir, minusDir))

    @SimObject
    val thermalWire = ThermalWireObject(this, ThermalBodyDef(model.material, !model.mass, !model.surfaceArea, null))

    @Behavior
    val heater = PowerHeatingBehavior(
        { generator.resistorPower },
        thermalWire.thermalBody
    )

    override var energy = Quantity<Energy>(0.0)

    init {
        ruleSet.withDirectionRulePlanar(plusDir + minusDir)
    }

    override var totalEnergyTransferred = Quantity<Energy>(0.0)
        private set

    override var life = 1.0
        private set

    private var savedLife = life

    override val cycles get() = totalEnergyTransferred / model.energyCapacity
    override val current get() = generator.resistorCurrent
    override val charge get() = energy / model.energyCapacity
    override val sourcePower get() = Quantity(generator.sourcePower, WATT)

    override var energyIncrement = Quantity(0.0, JOULE)

    private val stateUpdate = AtomicUpdate<BatteryState>()

    override val safeCharge
        get() = map(
            charge,
            model.damageChargeThreshold,
            1.0,
            0.0,
            1.0
        )

    override val temperature: Temperature get() = thermalWire.thermalBody.temperature

    val capacityCoefficient get() = model.capacityFunction.computeCapacity(this).coerceIn(0.0, 1.0)
    val adjustedEnergyCapacity get() = model.energyCapacity * capacityCoefficient

    fun deserializeNbt(tag: CompoundTag) {
        stateUpdate.setLatest(
            BatteryState(
                tag.getQuantity(ENERGY),
                tag.getDouble(LIFE),
                tag.getQuantity(ENERGY_IO)
            )
        )
    }

    fun serializeNbt(): CompoundTag {
        val tag = CompoundTag()

        tag.putDouble(ENERGY, !energy)
        tag.putDouble(LIFE, life)
        tag.putDouble(ENERGY_IO, !totalEnergyTransferred)

        return tag
    }

    override fun saveCellData() = serializeNbt()

    override fun loadCellData(tag: CompoundTag) = deserializeNbt(tag)

    override fun subscribe(subs: SubscriberCollection) = graph.simulationSubscribers.addPre(this::simulationTick)

    private fun appliesExternalUpdates() = stateUpdate.consume {
        energy = it.energy
        life = it.life
        totalEnergyTransferred = it.totalEnergyTransferred
        graph.setChanged()
    }

    private fun transfersEnergy(elapsed: Double): Boolean {
        // Get energy transfer:
        energyIncrement = Quantity(generator.sourcePower * elapsed)

        if(energyIncrement.value.approxEq(0.0)) {
            return false
        }

        // Update total IO:
        totalEnergyTransferred += abs(energyIncrement)

        energy -= energyIncrement

        val capacity = adjustedEnergyCapacity

        if (energy < 0.0) {
            LOG.error("Negative battery energy $locator")
            energy = Quantity(0.0)
        } else if (energy > capacity) {
            // Battery received more energy than capacity
            val extraEnergy = energy - capacity
            energy -= extraEnergy
            // Conserve energy by increasing temperature:
            thermalWire.thermalBody.energy += !extraEnergy
        }

        return true
    }

    private fun simulationTick(elapsed: Double, phase: SubscriberPhase) {
        setChangedIf(appliesExternalUpdates())

        if (!generator.hasResistor) {
            return
        }

        setChangedIf(transfersEnergy(elapsed))

        // Update with tolerance (likely, the resistance is ~constant and the voltage will update sparsely):
        generator.updatePotential(!model.voltageFunction.computeVoltage(this), VOLTAGE_EPS)
        generator.updateResistance(!model.resistanceFunction.computeResistance(this), RESISTANCE_EPS)

        life -= model.damageFunction.computeDamage(this, elapsed)
        life = life.coerceIn(0.0, 1.0)

        setChangedIf(!life.approxEq(savedLife, LIFE_EPS)) {
            savedLife = life
        }
    }
}

class BatteryPart(
    ci: PartCreateInfo,
    provider: CellProvider<BatteryCell>,
    private val rendererSupplier: PartRendererSupplier<BatteryPart, BasicPartRenderer>
) : CellPart<BatteryCell, BasicPartRenderer>(ci, provider), ItemPersistentPart, WrenchRotatablePart, WailaNode {
    companion object {
        private const val BATTERY = "battery"
    }

    override fun createRenderer() = rendererSupplier.create(this)

    override fun saveToItemNbt(tag: CompoundTag) {
        tag.put(BATTERY, cell.serializeNbt())
    }

    override fun loadFromItemNbt(tag: CompoundTag?) {
        tag?.useSubTagIfPreset(BATTERY, cell::deserializeNbt)
    }

    override val order get() = ItemPersistentPartLoadOrder.AfterSim

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        runIfCell {
            builder.voltage(cell.generator.potentialExact)
            builder.current(cell.generator.sourceCurrent)
            builder.power(cell.generator.sourcePower)
            builder.charge(cell.charge)
            builder.life(cell.life)
        }
    }
}
