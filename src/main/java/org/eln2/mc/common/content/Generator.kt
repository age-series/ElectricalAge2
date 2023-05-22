package org.eln2.mc.common.content

import com.mojang.blaze3d.vertex.PoseStack
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.ageseries.libage.sim.thermal.Simulator
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalMass
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.annotations.CrossThreadAccess
import org.eln2.mc.annotations.RaceCondition
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.client.render.renderTextured
import org.eln2.mc.common.blocks.foundation.CellBlock
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.cells.foundation.behaviors.IThermalBodyAccessor
import org.eln2.mc.common.cells.foundation.behaviors.withElectricalHeatTransfer
import org.eln2.mc.common.cells.foundation.behaviors.withElectricalPowerConverter
import org.eln2.mc.common.cells.foundation.objects.*
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.common.space.*
import org.eln2.mc.control.pid
import org.eln2.mc.data.DataAccessNode
import org.eln2.mc.data.IDataEntity
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import org.eln2.mc.mathematics.*
import org.eln2.mc.mathematics.avg
import org.eln2.mc.mathematics.bbVec
import org.eln2.mc.mathematics.lerp
import org.eln2.mc.mathematics.map
import org.eln2.mc.mathematics.vec4fOne
import org.eln2.mc.sim.BiomeEnvironments
import org.eln2.mc.sim.Datasets
import org.eln2.mc.sim.ThermalBody
import org.eln2.mc.utility.SelfDescriptiveUnitMultipliers.megaJoules
import kotlin.math.*

enum class GeneratorPowerDirection {
    Outgoing,
    Incoming
}

/**
 * Represents an Electrical Generator. It is characterised by a voltage and internal resistance.
 * */
class GeneratorObject(cell: CellBase, val plusDir: RelativeDirection = RelativeDirection.Front, val minusDir: RelativeDirection = RelativeDirection.Back) : ElectricalObject(cell), IWailaProvider, IDataEntity {
    init {
        ruleSet.withDirectionActualRule(DirectionMask.ofRelatives(plusDir, minusDir))
    }

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

    private val resistor = ElectricalComponentHolder {
        Resistor().also { it.resistance = internalResistance }
    }

    private val source = ElectricalComponentHolder {
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
        val dirActual = cell.posDescr.findDirActualOrNull(neighbour.cell.posDescr)
            ?: error("Furnace Cell requires a relative direction")

        return when(dirActual){
            plusDir -> resistor.offerExternal()
            minusDir -> source.offerNegative()
            else -> error("Unhandled neighbor direction $dirActual")
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

        connections.forEach { conn ->
            when(val direction = cell.posDescr.findDirActual(conn.cell.posDescr)){
                plusDir -> resistor.connectExternal(this, conn)
                minusDir -> source.connectNegative(this, conn)
                else -> error("Unhandled direction $direction")
            }
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.voltage(source.instance.potential)
        builder.text("Flow", powerFlowDirection)
        builder.power(abs(resistorPower))
    }

    override val dataAccessNode = DataAccessNode().also {
        it.data.withField {
            VoltageField { potential }
        }

        it.data.withField {
            CurrentField { resistorCurrent }
        }
    }
}

abstract class GeneratorCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(GeneratorObject(this))
    }

    val generatorObject get() = electricalObject as GeneratorObject
}

interface IBatteryView {
    val model: BatteryModel
    /**
     * Gets the total energy stored in the battery/
     * */
    val energy: Double

    /**
     * Gets the total energy exchanged by this battery.
     * */
    val energyIo: Double

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
 * The [IBatteryVoltageFunction] is used to compute the voltage of the battery based on the battery's state.
 * */
fun interface IBatteryVoltageFunction {
    fun computeVoltage(battery: IBatteryView, dt: Double): Double
}

/**
 * The [IBatteryResistanceFunction] is used to compute the internal resistance of the battery based on the battery's state.
 * It should never be zero, though this is not enforced and will likely result in a simulation error.
 * */
fun interface IBatteryResistanceFunction {
    fun computeResistance(battery: IBatteryView, dt: Double): Double
}

/**
 * The [IBatteryDamageFunction] computes a damage value in time, based on the battery's current state.
 * These values are deducted from the battery's life parameter. The final parameter is clamped.
 * */
fun interface IBatteryDamageFunction {
    fun computeDamage(battery: IBatteryView, dt: Double): Double
}

/**
 * The [IBatteryEnergyCapacityFunction] computes the capacity of the battery based on the battery's state.
 * This must be a value ranging from 0-1. The result is clamped to that range.
 * */
fun interface IBatteryEnergyCapacityFunction {
    fun computeCapacity(battery: IBatteryView): Double
}

object VoltageModels {
    /**
     * Gets a 12V Wet Cell Lead Acid Battery voltage function.
     * */
    val WET_CELL_12V = IBatteryVoltageFunction { view, _ ->
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
                1.0)

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
        0.0)
}

data class BatteryModel(
    val voltageFunction: IBatteryVoltageFunction,
    val resistanceFunction: IBatteryResistanceFunction,
    val damageFunction: IBatteryDamageFunction,
    val capacityFunction: IBatteryEnergyCapacityFunction,

    /**
     * The energy capacity of the battery. This is the total energy that can be stored.
     * */
    val energyCapacity: Double,

    /**
     * The charge percentage where, if the battery continues to discharge, it should start receiving damage.
     * */
    val damageChargeThreshold: Double,

    val material: Material,
    val mass: Double,
    val surfaceArea: Double)

data class BatteryState(val energy: Double, val life: Double, val energyIo: Double)

class BatteryCell(pos: CellPos, id: ResourceLocation, override val model: BatteryModel) : GeneratorCell(pos, id), IBatteryView {
    companion object {
        private const val ENERGY = "energy"
        private const val LIFE = "life"
        private const val ENERGY_IO = "energyIo"
    }

    init {
        ruleSet.withDirectionActualRule(DirectionMask.FRONT + DirectionMask.BACK)
    }

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(GeneratorObject(this), ThermalWireObject(this).also {
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
        1.0
    )

    override val temperature: Temperature
        get() = thermalWireObject.body.temperature

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

    override fun saveCellData(): CompoundTag {
        return serializeNbt()
    }

    override fun loadCellData(tag: CompoundTag) {
        deserializeNbt(tag)
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

            graph.setChanged()
        }
    }

    private fun simulateEnergyFlow(elapsed: Double) {
        // Get energy transfer:
        val transferredEnergy = abs(generatorObject.resistorPower * elapsed)

        // Update total IO:
        energyIo += transferredEnergy

        if(generatorObject.powerFlowDirection == GeneratorPowerDirection.Incoming){
            // Add energy into the system.
            energy += transferredEnergy
        } else {
            // Remove energy from the system.
            energy -= transferredEnergy
        }

        val capacity = adjustedEnergyCapacity

        if(energy < 0){
            LOGGER.error("Negative battery energy $pos")
            energy = 0.0
        }
        else if(energy > capacity) {
            val extraEnergy = energy - capacity
            energy -= extraEnergy

            // Conserve energy by increasing temperature:
            thermalWireObject.body.thermalEnergy += extraEnergy
        }
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

        // FIXME: Find condition
        graph.setChanged()
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

    private val thermalWireObject get() = thermalObject as ThermalWireObject
}

class BatteryPart(id: ResourceLocation, placementContext: PartPlacementContext, provider: CellProvider): CellPart(id, placementContext, provider), IItemPersistentPart {
    companion object {
        private const val BATTERY = "battery"
    }

    override val baseSize = bbVec(6.0, 8.0, 12.0)

    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.BATTERY).also {
            it.downOffset = bbOffset(8.0)
        }
    }

    private val batteryCell get() = cell as BatteryCell

    override fun saveItemTag(tag: CompoundTag) {
        tag.put(BATTERY, batteryCell.serializeNbt())
    }

    override fun loadItemTag(tag: CompoundTag?) {
        tag?.useSubTagIfPreset(BATTERY, batteryCell::deserializeNbt)
    }

    override val order: ItemPersistentPartLoadOrder = ItemPersistentPartLoadOrder.AfterSim
}

/**
 * Thermal body with two connection sides.
 * */
class ThermalBipoleObject(cell: CellBase, val b1Dir: RelativeDirection = RelativeDirection.Front, val b2Dir: RelativeDirection = RelativeDirection.Back) : ThermalObject(cell), IWailaProvider {
    var b1 = ThermalBody.createDefault().also { it.temperature = cell.getEnvironmentTemp() }
    var b2 = ThermalBody.createDefault().also { it.temperature = cell.getEnvironmentTemp() }

    init {
        ruleSet.withDirectionActualRule(DirectionMask.ofRelatives(b1Dir, b2Dir))
    }

    override fun offerComponent(neighbour: ThermalObject): ThermalComponentInfo {
        val dirActual = cell.posDescr.findDirActualOrNull(neighbour.cell.posDescr)
            ?: error("Thermal Bipole requires a direction")

        return ThermalComponentInfo(when(dirActual){
            b1Dir -> b1
            b2Dir -> b2
            else -> error("Unhandled bipole direction $dirActual")
        })
    }

    override fun addComponents(simulator: Simulator) {
        simulator.add(b1)
        simulator.add(b2)
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.temperature(b1.temperatureK)
        builder.temperature(b2.temperatureK)
    }
}

fun interface IThermalBodyProvider {
    fun get(): ThermalBody
}

interface IThermocoupleView {
    val model: ThermocoupleModel

    /**
     * Gets the absolute temperature difference between the hot and cold sides.
     * */
    val deltaTemperature: Temperature
}

/**
 * The [IThermocoupleVoltageFunction] computes a voltage based on the state of the thermocouple.
 * */
fun interface IThermocoupleVoltageFunction {
    fun compute(view: IThermocoupleView): Double
}

data class ThermocoupleModel(
    val efficiency: Double,
    val voltageFunction: IThermocoupleVoltageFunction
)

fun interface IGeneratorGetAccessor {
    fun get(): GeneratorObject
}

class ThermocoupleBehavior(
    private val generatorAccessor: IGeneratorGetAccessor,
    private val coldAccessor: IThermalBodyProvider,
    private val hotAccessor: IThermalBodyProvider,
    override val model: ThermocoupleModel):
    ICellBehavior,
    IThermocoupleView,
    IWailaProvider {

    private data class BodyPair(val hot: ThermalBody, val cold: ThermalBody, val inverted: Boolean)

    override fun onAdded(container: CellBehaviorContainer) {}

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addPreInstantaneous(this::preTick)
        subscribers.addPostInstantaneous(this::postTick)
    }

    override fun destroy(subscribers: SubscriberCollection) {
        subscribers.removeSubscriber(this::preTick)
        subscribers.removeSubscriber(this::postTick)
    }

    private fun getSortedBodyPair(): BodyPair {
        var cold = coldAccessor.get()
        var hot = hotAccessor.get()

        var inverted = false

        if(cold.temperatureK > hot.temperatureK) {
            val temp = cold
            cold = hot
            hot = temp
            inverted = true
        }

        return BodyPair(hot, cold, inverted)
    }

    private var energyTransfer = 0.0

    private var totalConverted = 0.0
    private var totalRemoved = 0.0

    private fun preTick(dt: Double, phase: SubscriberPhase) {
        preTickThermalElectrical(dt)
    }

    private fun preTickThermalElectrical(dt: Double) {
        val bodies = getSortedBodyPair()
        val generator = generatorAccessor.get()

        energyTransfer =
            (bodies.hot.temperatureK - bodies.cold.temperatureK) * // DeltaT

                // P.S. I expect the dipoles to have the same material. What to do here?
                avg(bodies.hot.thermalMass.material.specificHeat, bodies.cold.thermalMass.material.specificHeat) * // Specific Heat

                systemMass // Mass

        // Maximum energy that can be converted into electrical energy.

        val convertibleEnergy = energyTransfer * model.efficiency

        val targetVoltage = model.voltageFunction.compute(this)
        val generatorResistance = generator.internalResistance

        val targetCurrent = targetVoltage / generatorResistance
        val targetPower = targetVoltage * targetCurrent

        val maximumPower = convertibleEnergy / dt

        val power = min(maximumPower, targetPower)

        var voltage = sqrt(power * generatorResistance)

        if(bodies.inverted) {
            voltage = -voltage
        }

        generator.potential = voltage
    }

    private fun postTick(dt: Double, phase: SubscriberPhase) {
        val bodies = getSortedBodyPair()

        val transferredEnergy = abs(generatorAccessor.get().resistorPower * dt)

        totalConverted += transferredEnergy

        val wastedEnergy = (transferredEnergy / model.efficiency) * (1 - model.efficiency)

        val removedEnergy = wastedEnergy + transferredEnergy

        totalRemoved += removedEnergy

        // We converted some thermal energy into electrical energy.
        // Wasted energy is energy that was moved into the cold body. We removed (wasted + transferred)
        // from the hot side, and added (wasted) to the cold side.
        bodies.hot.thermalEnergy -= removedEnergy
        bodies.cold.thermalEnergy += wastedEnergy
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        val pair = getSortedBodyPair()

        builder.text("Hot E", pair.hot.thermalEnergy.formatted())
        builder.text("Cold E", pair.cold.thermalEnergy.formatted())
        builder.text("Hot T", pair.hot.temperatureK.formatted())
        builder.text("Cold T", pair.cold.temperatureK.formatted())
        builder.text("dE", energyTransfer.formatted())
        builder.text("Total wasted", totalRemoved.formatted())
        builder.text("Total converted", totalConverted.formatted())
    }

    override val deltaTemperature: Temperature
        get() {
            val bodies = getSortedBodyPair()

            return bodies.hot.temperature - bodies.cold.temperature
        }

    /**
     * Gets the total mass of the system (hot body + cold body)
     * */
    private val systemMass get() = coldAccessor.get().thermalMass.mass + hotAccessor.get().thermalMass.mass
}

class ThermocoupleCell(pos: CellPos, id: ResourceLocation) : GeneratorCell(pos, id) {
    init {
        behaviors.add(ThermocoupleBehavior(
            { generatorObject },
            { thermalBiPole.b1 },
            { thermalBiPole.b2 },
            ThermocoupleModel(0.5) { view ->
                val temperature = view.deltaTemperature.kelvin.coerceIn(0.0, 20.0)

                map(temperature, 0.0, 20.0, 0.0, 100.0)
            }))

        ruleSet.withDirectionActualRule(DirectionMask.HORIZONTALS)
    }

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(
            GeneratorObject(this).also {
                it.potential = 100.0
            },

            ThermalBipoleObject(this, RelativeDirection.Left, RelativeDirection.Right)
        )
    }

    private val thermalBiPole get() = thermalObject as ThermalBipoleObject

    val b1Temperature get() = thermalBiPole.b1.temperature
    val b2Temperature get() = thermalBiPole.b2.temperature
}

class ThermocouplePart(id: ResourceLocation, placementContext: PartPlacementContext) : CellPart(id, placementContext, Content.THERMOCOUPLE_CELL.get()) {
    companion object {
        private const val LEFT_TEMP = "left"
        private const val RIGHT_TEMP = "right"
    }

    override val baseSize: Vec3
        get() = Vec3(1.0, 15.0 / 16.0, 1.0)

    override fun createRenderer(): IPartRenderer {
        return RadiantBipoleRenderer(
            this,
            PartialModels.PELTIER_BODY,
            PartialModels.PELTIER_LEFT,
            PartialModels.PELTIER_RIGHT,
            bbOffset(15.0),
            0f
        )
    }

    override fun getSyncTag(): CompoundTag {
        return CompoundTag().also { tag ->
            val cell = cell as ThermocoupleCell

            tag.putTemperature(LEFT_TEMP, cell.b2Temperature)
            tag.putTemperature(RIGHT_TEMP, cell.b1Temperature)
        }
    }

    override fun handleSyncTag(tag: CompoundTag) {
        val renderer = renderer as? RadiantBipoleRenderer
            ?: return

        renderer.updateLeftSideTemperature(tag.getTemperature(LEFT_TEMP))
        renderer.updateRightSideTemperature(tag.getTemperature(RIGHT_TEMP))
    }

    override fun onCellAcquired() {
        sendTemperatureUpdates()
    }

    private fun sendTemperatureUpdates() {
        if(!isAlive) {
            return
        }

        syncChanges()

        EventScheduler.scheduleWorkPre(20, this::sendTemperatureUpdates)
    }

    val thermocoupleCell get() = cell as ThermocoupleCell
}

data class HeatGeneratorFuelMass(
    @RaceCondition
    var fuelAmount: Double,         // Unit
    val fuelEnergyCapacity: Double, // Energy/Unit
    val suggestedBurnRate: Double,  // Unit/Second
    val fuelTemperature: Double
) {
    companion object {
        private const val AMOUNT = "amount"
        private const val ENERGY_CAPACITY = "energyCapacity"
        private const val SUGGESTED_BURN_RATE = "suggestedBurnRate"
        private const val FUEL_TEMPERATURE = "fuelTemperature"

        fun fromNbt(tag: CompoundTag): HeatGeneratorFuelMass {
            return HeatGeneratorFuelMass(
                tag.getDouble(AMOUNT),
                tag.getDouble(ENERGY_CAPACITY),
                tag.getDouble(SUGGESTED_BURN_RATE),
                tag.getDouble(FUEL_TEMPERATURE)
            )
        }
    }

    val availableEnergy get() = fuelAmount * fuelEnergyCapacity

    /**
     * Gets the maximum energy that can be produced in the specified time [dt], using the specified mass [burnRate].
     * */
    private fun getMaxTransfer(dt: Double, burnRate: Double): Double {
        return burnRate * fuelEnergyCapacity * dt
    }

    /**
     * Gets the energy produced in the specified time [dt], using the specified mass [burnRate], taking into account the
     * amount of fuel remaining.
     * */
    fun getTransfer(dt: Double, burnRate: Double): Double {
        return min(getMaxTransfer(dt, burnRate), availableEnergy)
    }

    /**
     * Removes the amount of mass corresponding to [energy] from the system.
     * */
    fun removeEnergy(energy: Double) {
        fuelAmount -= energy / fuelEnergyCapacity
    }

    fun toNbt(): CompoundTag {
        return CompoundTag().also {
            it.putDouble(AMOUNT, fuelAmount)
            it.putDouble(ENERGY_CAPACITY, fuelEnergyCapacity)
            it.putDouble(SUGGESTED_BURN_RATE, suggestedBurnRate)
            it.putDouble(FUEL_TEMPERATURE, fuelTemperature)
        }
    }
}

object Fuels {
    /**
     * Gets a coal fuel mass.
     * */
    fun coal(mass: Double): HeatGeneratorFuelMass {
        return HeatGeneratorFuelMass(
            fuelAmount = mass,
            fuelEnergyCapacity = megaJoules(24.0),
            suggestedBurnRate = 0.005,
            fuelTemperature = 2000.0
        )
    }
}

private class FuelBurnerBehavior(val cell: CellBase, val bodyGetter: IThermalBodyAccessor): ICellBehavior, IWailaProvider {
    companion object {
        private const val FUEL = "fuel"
    }

    private var fuel: HeatGeneratorFuelMass? = null

    private val pid = pid(
        kP = 10.0,
        kI = 0.01,
        kD = 0.1
    )

    /**
     * Replaces the fuel in this burner and resets the control system.
     * */
    fun replaceFuel(fuel: HeatGeneratorFuelMass) {
        this.fuel = fuel
        pid.unwind()

        pid.minControl = 0.0
        pid.maxControl = fuel.suggestedBurnRate
        pid.setPoint = fuel.fuelTemperature

        cell.setChanged()
    }

    /**
     * Gets the available energy in this burner.
     * */
    val availableEnergy get() = fuel?.availableEnergy ?: 0.0

    private var burnRateSignal = 0.0

    override fun onAdded(container: CellBehaviorContainer) { }

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addPreInstantaneous(this::simulationTick)
    }

    override fun destroy(subscribers: SubscriberCollection) {
        subscribers.removeSubscriber(this::simulationTick)
    }

    private fun simulationTick(dt: Double, phase: SubscriberPhase) {
        val fuel = this.fuel
            ?: return

        val body = bodyGetter.get()

        burnRateSignal = pid.update(body.temperatureK, dt)

        val energyTransfer = fuel.getTransfer(dt, burnRateSignal)

        fuel.removeEnergy(energyTransfer)

        body.thermalEnergy += energyTransfer

        // FIXME: Implement condition here
        cell.setChanged()
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.text("Control Signal x1000", (burnRateSignal * 1000).formatted(2))
        builder.text("Fuel", (fuel?.fuelAmount ?: 0.0).formatted())
    }

    fun saveNbt(): CompoundTag {
        return CompoundTag().withSubTagOptional(FUEL, fuel?.toNbt())
    }

    fun loadNbt(tag: CompoundTag) {
        tag.useSubTagIfPreset(FUEL) {
            replaceFuel(HeatGeneratorFuelMass.fromNbt(it))
        }
    }
}

class HeatGeneratorCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    companion object {
        const val BURNER_BEHAVIOR = "burner"
    }

    init {
        behaviors.add(
            FuelBurnerBehavior(this) { thermalWireObject.body }
        )

        ruleSet.withDirectionActualRule(DirectionMask.HORIZONTALS)
    }

    /**
     * If true, this burner needs more fuel to continue burning. Internally, this checks if the available energy is less than a threshold value.
     * */
    val needsFuel get() = behaviors.getBehavior<FuelBurnerBehavior>().availableEnergy approxEq 0.0

    fun replaceFuel(mass: HeatGeneratorFuelMass) {
        behaviors.getBehavior<FuelBurnerBehavior>().replaceFuel(mass)
    }

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(ThermalWireObject(this))
    }

    override fun loadCellData(tag: CompoundTag) {
        tag.useSubTagIfPreset(BURNER_BEHAVIOR, behaviors.getBehavior<FuelBurnerBehavior>()::loadNbt)
    }

    override fun saveCellData(): CompoundTag {
        return CompoundTag().withSubTag(BURNER_BEHAVIOR, behaviors.getBehavior<FuelBurnerBehavior>().saveNbt())
    }

    val thermalWireObject get() = thermalObject as ThermalWireObject
}

class HeatGeneratorBlockEntity(pos: BlockPos, state: BlockState): CellBlockEntity(pos, state, Content.HEAT_GENERATOR_BLOCK_ENTITY.get()) {
    companion object {
        const val FUEL_SLOT = 0

        private const val INVENTORY = "inventory"

        fun tick(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pLevel == null || pBlockEntity == null) {
                return
            }

            if (pBlockEntity !is HeatGeneratorBlockEntity) {
                LOGGER.error("Got $pBlockEntity instead of heat generator")
                return
            }

            if (!pLevel.isClientSide) {
                pBlockEntity.serverTick()
            }
        }
    }

    class InventoryHandler(val entity: HeatGeneratorBlockEntity) : ItemStackHandler(1) {
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if(stack.item != Items.COAL) {
                return ItemStack.EMPTY
            }

            return super.insertItem(slot, stack, simulate).also {
                entity.inputChanged()
            }
        }
    }

    val inventoryHandler = InventoryHandler(this)
    private val inventoryHandlerLazy = LazyOptional.of { inventoryHandler }

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if(cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return inventoryHandlerLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)

        pTag.put(INVENTORY, inventoryHandler.serializeNBT())
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)

        pTag.useSubTagIfPreset(INVENTORY, inventoryHandler::deserializeNBT)
    }

    fun serverTick() {
        val cell = heatGeneratorCell
            ?: return

        if(!cell.needsFuel) {
            return
        }

        val stack = inventoryHandler.extractItem(FUEL_SLOT, 1, false)

        if(stack.isEmpty) {
            return
        }

        cell.replaceFuel(Fuels.coal(1.0))

        // Inventory changed:
        setChanged()
    }

    fun inputChanged(){
        setChanged()
    }

    private val heatGeneratorCell get() = cell as? HeatGeneratorCell
}

class HeatGeneratorMenu(pContainerId: Int, playerInventory: Inventory, handler: ItemStackHandler) :
    AbstractContainerMenu(Content.HEAT_GENERATOR_MENU.get(), pContainerId){

    companion object {
        fun create(id: Int, inventory: Inventory, player: Player, entity: HeatGeneratorBlockEntity): HeatGeneratorMenu {
            return HeatGeneratorMenu(
                id,
                inventory,
                entity.inventoryHandler)
        }
    }

    constructor(pContainerId: Int, playerInventory: Inventory): this(
        pContainerId,
        playerInventory,
        ItemStackHandler(1),
    )

    private val playerGridStart: Int
    private val playerGridEnd: Int

    init {
        addSlot(SlotItemHandler(handler, HeatGeneratorBlockEntity.FUEL_SLOT, 56, 35))

        playerGridStart = 1
        playerGridEnd = playerGridStart + this.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun quickMoveStack(pPlayer: Player, pIndex: Int): ItemStack {
        val slot = slots[pIndex]

        if(!slot.hasItem()) {
            return ItemStack.EMPTY
        }

        val stack = slot.item

        if(pIndex == HeatGeneratorBlockEntity.FUEL_SLOT) {
            // Quick move from input to player

            if (!moveItemStackTo(stack, playerGridStart, playerGridEnd, true)) {
                return ItemStack.EMPTY
            }
        }
        else {
            // Only move into input slot

            if(!moveItemStackTo(stack, HeatGeneratorBlockEntity.FUEL_SLOT, HeatGeneratorBlockEntity.FUEL_SLOT + 1, true)){
                return ItemStack.EMPTY
            }
        }

        slot.setChanged()

        return stack
    }

    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }
}

class HeatGeneratorScreen(menu: HeatGeneratorMenu, playerInventory: Inventory, title: Component) : AbstractContainerScreen<HeatGeneratorMenu>(menu, playerInventory, title) {
    companion object {
        private val TEXTURE = Eln2.resource("textures/gui/container/furnace_test.png")
        private val TEX_SIZE = Vector2I(256, 256)
        private val BACKGROUND_UV_SIZE = Vector2I(176, 166)
    }

    override fun renderBg(pPoseStack: PoseStack, pPartialTick: Float, pMouseX: Int, pMouseY: Int) {
        renderTextured(
            texture = TEXTURE,
            poseStack = pPoseStack,
            blitOffset = 0,
            color = vec4fOne(),
            position = Vector2I(leftPos, topPos),
            uvSize = BACKGROUND_UV_SIZE,
            uvPosition = Vector2F.zero(),
            textureSize = TEX_SIZE
        )
    }
}

class HeatGeneratorBlock : CellBlock() {
    override fun getCellProvider(): ResourceLocation {
        return Content.HEAT_GENERATOR_CELL.id
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return HeatGeneratorBlockEntity(pPos, pState)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T> {
        return BlockEntityTicker(HeatGeneratorBlockEntity::tick)
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult
    ): InteractionResult {
        return pLevel.constructMenu(pPos, pPlayer, { TextComponent("Test") }, HeatGeneratorMenu::create)
    }
}

/**
 * Represents a view over a solar illuminated body.
 * */
interface IIlluminatedBodyView {
    /**
     * Gets the current angle of the sun.
     * @see ServerLevel.getSunAngle
     * */
    val sunAngle: Double

    /**
     * Gets whether this body's view to the sun is obstructed.
     * @see ServerLevel.canSeeSky
     * */
    val isObstructed: Boolean

    /**
     * Gets the normal direction of the surface.
     * */
    val normal: Direction
}

abstract class SolarIlluminationBehavior(private val cell: CellBase): ICellBehavior, IIlluminatedBodyView {
    // Is it fine to access these from our simulation threads?
    override val sunAngle: Double
        get() = cell.graph.level.getSunAngle(0f).toDouble()

    override val isObstructed: Boolean
        get() = !cell.graph.level.canSeeSky(cell.posDescr.requireBlockPosLoc { "Solar Behaviors require block pos locator" })
}

abstract class SolarGeneratorBehavior(val generatorCell: GeneratorCell): SolarIlluminationBehavior(generatorCell) {
    override fun onAdded(container: CellBehaviorContainer) { }

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addSubscriber(SubscriberOptions(100, SubscriberPhase.Pre), this::simulationTick)
    }

    override fun destroy(subscribers: SubscriberCollection) {
        subscribers.removeSubscriber(this::simulationTick)
    }

    private fun simulationTick(dt: Double, phase: SubscriberPhase) {
        if(!generatorCell.hasGraph){
            // weird.
            return
        }

        update()
    }

    abstract fun update()
}

/**
 * The [IPhotovoltaicVoltageFunction] computes a voltage based on the photovoltaic panel's state.
 * */
fun interface IPhotovoltaicVoltageFunction {
    fun compute(view: IIlluminatedBodyView): Double
}

data class PhotovoltaicModel(
    val voltageFunction: IPhotovoltaicVoltageFunction,
    val panelResistance: Double
)

object PhotovoltaicModels {
    // We map angle difference to a voltage coefficient. 0 - directly overhead, 1 - under horizon
    private val TEST_SPLINE = hermiteMappedCubic().apply {
        point(0.0, 1.0)
        point(0.95, 0.8)
        point(1.0, 0.0)
    }.buildHermite()

    private fun voltageTest(maximumVoltage: Double): IPhotovoltaicVoltageFunction {
        return IPhotovoltaicVoltageFunction { view ->
            if(view.isObstructed) {
                return@IPhotovoltaicVoltageFunction 0.0
            }

            val passDirectionWorld = Rotation2d.exp(Math.PI * when (val sunAngle = Math.toDegrees(view.sunAngle)) {
                in 270.0..360.0 -> {
                    map(sunAngle, 270.0, 360.0, 0.0, 0.5)
                }
                in 0.0..90.0 -> {
                    map(sunAngle, 0.0, 90.0, 0.5, 1.0)
                }
                else -> {
                    // Under horizon

                    return@IPhotovoltaicVoltageFunction 0.0
                }
            }).direction

            // Sun moves around Z

            val actualSunWorld = Vector3D(passDirectionWorld.x, passDirectionWorld.y, 0.0)
            val normalWorld = view.normal.toVector3D()

            val actualDifferenceActual = map(
                Math.toDegrees(Vector3D.angle(actualSunWorld, normalWorld)),
                0.0,
                90.0,
                0.0,
                1.0
            )

            val value = TEST_SPLINE.evaluate(actualDifferenceActual)

            return@IPhotovoltaicVoltageFunction value * maximumVoltage
        }
    }

    fun test24Volts(): PhotovoltaicModel {
        //https://www.todoensolar.com/285w-24-volt-AmeriSolar-Solar-Panel
        return PhotovoltaicModel(voltageTest(32.0), 3.5)
    }
}

class PhotovoltaicBehavior(cell: GeneratorCell, val model: PhotovoltaicModel) : SolarGeneratorBehavior(cell) {
    override fun update() {
        generatorCell.generatorObject.potential = model.voltageFunction.compute(this)
    }

    override val normal: Direction
        get() = generatorCell.posDescr.requireBlockFaceLoc { "Photovoltaic behavior requires a face locator" }
}

class PhotovoltaicGeneratorCell(pos: CellPos, id: ResourceLocation, model: PhotovoltaicModel) : GeneratorCell(pos, id) {
    init {
        behaviors.add(PhotovoltaicBehavior(this, model))
        ruleSet.withDirectionActualRule(DirectionMask.FRONT + DirectionMask.BACK)
    }
}
