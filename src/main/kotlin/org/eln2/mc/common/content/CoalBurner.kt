@file:Suppress("unused")

package org.eln2.mc.common.content

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual
import dev.engine_room.flywheel.api.visual.ShaderLightVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.ItemStackHandler
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.client.extensions.common.IClientBlockExtensions
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.mathematics.geometry.Vector2di
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.ThermalMass
import org.ageseries.libage.sim.ThermalMassDefinition
import org.eln2.mc.*
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.*
import org.eln2.mc.client.screens.ProgressSupplierMenu
import org.eln2.mc.common.blocks.foundation.BigBlockRepresentativeBlockEntity
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.blocks.foundation.MultiblockDelegateBlockEntity
import org.eln2.mc.common.blocks.foundation.MultiblockDelegateMap
import org.eln2.mc.common.blocks.foundation.UprightHorizontalDirectionCellBlock
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.content.modules.Eln2ConventionTags
import org.eln2.mc.common.content.modules.Eln2HeatGenerators
import org.eln2.mc.common.containers.ContainerHelper
import org.eln2.mc.common.containers.ProgressContainerData
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicate
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.network.serverToClient.BulkPacketHandlerBlockEntity
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.sendBulkPacket
import org.eln2.mc.common.sounds.foundation.SimpleLoopingBlockEntitySoundInstance
import org.eln2.mc.common.sounds.foundation.SoundInfo
import org.eln2.mc.common.sounds.foundation.SoundInstanceTickEvent
import org.ageseries.libage.mathematics.FramerateIndependentSmoother1d
import org.eln2.mc.client.render.FlwMaterials
import org.eln2.mc.common.blocks.foundation.ReplaceVanillaParticlesBlockExtension
import org.eln2.mc.extensions.*
import org.eln2.mc.mathematics.toHorizontalFacing
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

//#region Simulation

/**
 * @param conductivityGasCoal The conductivity between the input gas and the coal. This, most importantly, determines how much heat the coal can absorb from the chemical reaction.
 * @param conductivityGasHull The conductivity between the input gas and the hull.
 * @param conductanceCoalCoal The conductance between each coal slice. This is basically the internal thermal conductance of the coal body.
 * @param conductivityCoalHull The conductance between the coal slices and the hull. This controls how much heat can be pulled away by the hull from the coal body.
 * @param temperatureParameter The temperature-based parameter for the reaction rate. Given a test temperature, the smaller [temperatureParameter] is, the larger the reaction rate.
 * @param rateParameter Independent scale factor for the entire reaction rate.
 * @param gasCaptureFactor Determines how much of the packet can interact with a layer (this parameter is also scaled with the quantity left in the cell). `1` means a perfect interaction (the layer can use up all the oxygen, if the temperature rate allows it).
 * */
data class BurnerDeviceDescription(
    val conductivityGasCoal: Quantity<ThermalConductivity>,
    val conductivityGasHull: Quantity<ThermalConductivity>,
    val conductanceCoalCoal: Quantity<ThermalConductance>,
    val conductivityCoalHull: Quantity<ThermalConductivity>,
    val temperatureParameter: Quantity<Temperature> = Quantity(681.0195, CELSIUS),
    val rateParameter: Double = 0.05,
    val gasCaptureFactor: Double = 0.25,
)

class BurnerSimulation(val ambientTemperature: Quantity<Temperature>, val hull: ThermalMass, val options: BurnerDeviceDescription) {
    /**
     * Represents the entire content of gas inside the reaction chamber. Constructed by [tick] when air is injected for reaction.
     * @param airMass The injected amount of air.
     * @param airTemperature The temperature of the injected air.
     * */
    @Suppress("PropertyName")
    class AirPacket(val ambientTemperature: Quantity<Temperature>, airMass: Double, airTemperature: Double) {
        companion object {
            const val N2_FRACTION = 0.764
            const val O2_FRACTION = 0.235
            const val CO2_FRACTION = 0.001

            const val CP_N2 = 1040.0
            const val CP_O2 = 919.0
            const val CP_CO2 = 844.0
        }

        /**
         * The mass of nitrogen remaining in the packet.
         * */
        var N2 = airMass * N2_FRACTION

        /**
         * The mass of oxygen remaining in the packet.
         * */
        var O2 = airMass * O2_FRACTION

        /**
         * The mass of carbon dioxide remaining in the packet.
         * */
        var CO2 = airMass * CO2_FRACTION

        /**
         * Gets the total mass remaining in the packet.
         * */
        val mass: Double get() = N2 + O2 + CO2

        /**
         * Gets the heat capacity of the mixture.
         * */
        val heatCapacity: Double get() = (N2 * CP_N2) + (O2 * CP_O2) + (CO2 * CP_CO2)

        /**
         * The energy of the air inside the packet.
         * */
        var totalEnergy: Double = heatCapacity * airTemperature

        /**
         * Gets the temperature of the mixture. Returns [ambientTemperature] if [heatCapacity] is `0`.
         * */
        val temperature: Double get() {
            val thermalMass = heatCapacity

            if (thermalMass <= 0.0) {
                return !ambientTemperature
            }

            return totalEnergy / thermalMass
        }
    }

    class CoalGrade(
        val energyDensity: Quantity<MassEnergyDensity> = Quantity(24.0, MEGA * JOULE_PER_KILOGRAM),
        val specificHeat: Quantity<SpecificHeatCapacity> = Quantity(1200.0, JOULE_PER_KILOGRAM_KELVIN),
        val characteristicSurfaceArea: Double = 17.6,
        val ignitionThreshold: Quantity<Temperature> = Quantity(126.01, CELSIUS),
        val ignitionWindow: Quantity<Temperature> = Quantity(50.0, CELSIUS),
        val airToCoalRatio: Double = 11.5,
    )

    /**
     * One cell in the simulation's domain. Represents a small, homogenous (in temperature and composition) volume of coal.
     * @param initialMass The initial mass added to this slice.
     * @param energy The internal energy of the slice.
     * */
    class CoalSlice(val grade: CoalGrade, var initialMass: Double, var energy: Double) {
        /**
         * The mass remaining in the slice.
         * */
        var mass = initialMass

        /**
         * The temperature of the coal in the slice. Undefined around [mass]` = 0`
         * */
        var temperature get() = energy / (mass * !grade.specificHeat)
            set(value) {
                energy = value * (mass * !grade.specificHeat)
            }
    }

    var slices: Array<CoalSlice> = emptyArray()

    /**
     * Adds coal to the simulation. If active slices exist, the mass is distributed across them; otherwise fresh slices are created.
     * */
    fun addCoal(grade: CoalGrade, mass: Double, sliceCount: Int) {
        val activeSlices = slices.filter { !it.mass.approxEq(0.0) }

        if (activeSlices.isEmpty()) {
            slices = Array(sliceCount) {
                val sliceMass = mass / sliceCount

                CoalSlice(
                    grade,
                    sliceMass,
                    sliceMass * !grade.specificHeat * !ambientTemperature
                )
            }
        } else {
            val massPerSlice = mass / activeSlices.size

            activeSlices.forEach { slice ->
                slice.initialMass += massPerSlice
                slice.mass += massPerSlice
                slice.energy += massPerSlice * !grade.specificHeat * !ambientTemperature
            }

            slices = activeSlices.toTypedArray()
        }
    }

    private fun clipEnergyTransfer(transfer: Double, from: Double, to: Double) =
        if(transfer > 0.0) {
            transfer.coerceAtMost(from)
        }
        else {
            transfer.coerceAtLeast(-to)
        }

    val isDepleted get() = slices.isEmpty() || slices.all { it.mass.approxEq(0.0) }
    val totalMass get() = slices.sumOf { it.mass }

    /**
     * Calculates the mass flow rate due to the chimney effect.
     * @param chimneyGasTemperature The temperature of the gas inside the device. Usually, you should use [lastChimneyTemperature].
     * @param draftStrength A combined area + friction + other things term.
     * */
    fun calculateNaturalDraftFlowRate(chimneyGasTemperature: Double, draftStrength: Double): Double {
        if (chimneyGasTemperature <= !ambientTemperature + 0.1) {
            return 0.0
        }

        val buoyancyTerm = 1.0 - (!ambientTemperature / chimneyGasTemperature)

        return if (buoyancyTerm > 0.0) {
            draftStrength * sqrt(buoyancyTerm)
        }
        else {
            0.0
        }
    }

    /**
     * Gets the last temperature of the output gas.
     * */
    var lastChimneyTemperature = !ambientTemperature

    fun tick(dt: Double, inputAirMass: Double, inputAirTemperature: Double) : AirPacket {
        val options = options

        val packet = AirPacket(ambientTemperature, inputAirMass, inputAirTemperature)

        var isDepleted = true
        slices.forEach { layer ->
            if(layer.mass.approxEq(0.0)) {
                return@forEach
            }

            val grade = layer.grade

            isDepleted = false

            val ignitionState = ((layer.temperature - !grade.ignitionThreshold) / !grade.ignitionWindow).coerceIn(0.0, 1.0)

            /**
             * Temperature-dependent reaction rate calculation. Not Arrhenius because it's harder to tune and more unstable.
             * */
            val temperatureFactor = ignitionState * (layer.temperature / !options.temperatureParameter).pow(2).coerceAtMost(5.0)

            /**
             * Maximum burnable mass based on the physical construction of the device and its temperature:
             * */
            val maxBurnByBurnerState = options.rateParameter * (layer.grade.characteristicSurfaceArea * layer.mass.pow(2.0 / 3.0)) * temperatureFactor * dt

            /**
             * Maximum burnable mass based on available oxygen:
             * */
            val maxBurnByOxygen = (options.gasCaptureFactor * packet.O2 * (layer.mass / layer.initialMass)) / (grade.airToCoalRatio * AirPacket.O2_FRACTION)

            val carbonMassBurned = min(layer.mass, min(maxBurnByBurnerState, maxBurnByOxygen))

            /**
             * Releases the chemical energy. We actually add this energy to the gas itself, not the coal.
             * This is more stable and realistic; if we add the energy directly to the coal, the reaction is much more violent in testing.
             * */
            if (carbonMassBurned > 0.0) {
                layer.mass -= carbonMassBurned

                val oxygenConsumed = carbonMassBurned * (grade.airToCoalRatio * AirPacket.O2_FRACTION)
                val co2Produced = carbonMassBurned + oxygenConsumed

                packet.O2 -= oxygenConsumed
                packet.CO2 += co2Produced

                val energyReleased = carbonMassBurned * !grade.energyDensity
                packet.totalEnergy += energyReleased
            }

            /**
             * Heat exchange between the gas and the coal:
             * */
            val qGasCoal = clipEnergyTransfer(
                (packet.temperature - layer.temperature) * packet.heatCapacity *! options.conductivityGasCoal * layer.mass.pow(2.0 / 3.0) * dt,
                packet.totalEnergy,
                layer.energy
            )

            packet.totalEnergy -= qGasCoal
            layer.energy += qGasCoal

            /**
             * Heat exchange between the coal and the hull:
             * */
            val qCoalHull = clipEnergyTransfer(
                (layer.temperature - !hull.temperature) * layer.mass * !grade.specificHeat * !options.conductivityCoalHull * dt,
                layer.energy,
                !hull.energy
            )

            layer.energy -= qCoalHull
            hull.energy += Quantity(qCoalHull)
        }

        if(!isDepleted) {
            /**
             * Heat exchange between the coal slices:
             * */
            for (i in 1 until slices.size) {
                val s0 = slices[i - 1]
                val s1 = slices[i]

                if(s0.mass.approxEq(0.0) || s1.mass.approxEq(0.0)) {
                    continue
                }

                val q = clipEnergyTransfer(
                    (s0.temperature - s1.temperature) * !options.conductanceCoalCoal * dt,
                    s0.energy,
                    s1.energy
                )

                s0.energy -= q
                s1.energy += q
            }
        }

        /**
         * Heat exchange between the gas and the hull:
         * */
        val qGasHull = clipEnergyTransfer(
            (packet.temperature - !hull.temperature) * packet.heatCapacity * !options.conductivityGasHull * dt * 10.0,
            packet.totalEnergy,
            !hull.energy
        )
        packet.totalEnergy -= qGasHull
        hull.energy += Quantity(qGasHull)

        lastChimneyTemperature = if(isDepleted) {
            !ambientTemperature
        } else {
            packet.temperature
        }

        return packet
    }
}

/**
 * Options for the [BurnerCell].
 * @param deviceDescription The options for the [BurnerSimulation].
 * @param hullDef The thermal body that actually is a part of the thermal simulation. The coal simulation will exchange temperature with this body.
 * @param leakageParameters Leakage connection between the hull thermal body and the environment.
 * @param substeps The burner simulation does an undamped explicit step for the exponentially-decaying heat equations and whatnot. We substep the simulation to make sure it's good.
 * */
data class BurnerCellOptions(
    val deviceDescription: BurnerDeviceDescription,
    val hullDef: ThermalMassDefinition,
    val leakageParameters: ConnectionParameters,
    val substeps: Int = 16,
    val coalCapacity: Quantity<Mass> = Quantity(10.0, KILOGRAM),
)

object CoalGradeRegistry {
    val REGISTRY = MutableMapPairBiMap<ResourceLocation, BurnerSimulation.CoalGrade>()

    fun register(id: ResourceLocation, grade: BurnerSimulation.CoalGrade) : BurnerSimulation.CoalGrade {
        REGISTRY.add(id, grade)
        return grade
    }

    val DEFAULT = register(resource("default_coal"), BurnerSimulation.CoalGrade())

    fun getId(grade: BurnerSimulation.CoalGrade) = REGISTRY.backward[grade] ?: error("Coal grade is not registered!")
    fun getById(id: ResourceLocation) = REGISTRY.forward[id] ?: error("Could not get coal grade \"$id\"")
}

/**
 * Base for a cell that burns fuel to get energy.
 * Only handles integrating the burning simulation with the cells.
 * */
abstract class BurnerCell(ci: CellCreateInfo, val burnerCellOptions: BurnerCellOptions) : Cell(ci) {
    @SimObject
    val hull = ThermalWireObject(
        this,
        burnerCellOptions.hullDef(),
        burnerCellOptions.leakageParameters
    )

    @Behavior
    val explosion = ThermalBreakdownBehavior.create(
        Quantity(1800.0, CELSIUS),
        this,
        hull.thermalBody::temperature
    )

    val simulation = BurnerSimulation(
        ci.environment.ambientTemperature,
        hull.thermalBody,
        burnerCellOptions.deviceDescription
    )

    @Replicator
    fun hullTemperatureReplicator(target: InternalMultiThermalBodyTemperatureConsumer) =
        InternalMultiThermalBodyTemperatureReplicatorBehavior(listOf(hull.thermalBody), target)

    @Replicator
    fun externalTemperatureReplicator(target: ExternalTemperatureConsumer) =
        ExternalTemperatureReplicatorBehavior(this, target)

    /**
     * Represents an ignition time interval.
     * @param ticksLeft The number of simulation ticks left for applying this ignition.
     * @param targetTemperature The minimum temperature to enforce for the bottom slice.
     * @param airInjectionRate The extra air injection rate (mass flow rate).
     * */
    private class Ignition(var ticksLeft: Int, var targetTemperature: Quantity<Temperature>, var airInjectionRate: Double)

    /**
     * Ignition to apply:
     * */
    private var ignition: Ignition? = null
    private val ignitionUpdate = AtomicUpdate<Ignition>()

    /**
     * Ignites the device.
     * */
    fun ignite() {
        ignitionUpdate.setLatest(
            Ignition(
                200,
                Quantity(300.0, CELSIUS),
                !Quantity(2.5, GRAM)
            )
        )
    }

    val coalCapacity get() = burnerCellOptions.coalCapacity

    val totalMass get() = simulation.totalMass

    fun canAddCoal(amount: Quantity<Mass>): Boolean {
        return totalMass + !amount <= !coalCapacity
    }

    val coalFillLevel: Float
        get() = (totalMass / !coalCapacity).toFloat().coerceIn(0.0f, 1.0f)

    /**
     * Adds coal to the device. If active slices exist, the mass is distributed across them; otherwise fresh slices are created.
     * */
    fun addCoal(grade: BurnerSimulation.CoalGrade, amount: Quantity<Mass>) {
        // Atomic operation wrt the simulation method.
        // However, it is not atomic wrt to the cell's tick method.
        simulation.addCoal(grade, !amount, 16)
        setChanged()
    }

    /**
     * The air inflow rate for the tick.
     * This is only the base value; if an [ignition] is present, more air may be inserted into the simulation.
     * */
    var injectionRate: Double = 0.0
        protected set

    /**
     * The temperature of the flue gas.
     * Ambient if the simulation is not running.
     * */
    var lastOutputGasTemperature = simulation.ambientTemperature
        private set

    /**
     * The oxygen output rate, in kilograms per second.
     * This is basically the oxygen that did not manage to react with the coal.
     * */
    var lastOutputOxygenMassFlowRate = 0.0
        private set

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPre(this::tick)
    }

    /**
     * Called by [tick] so the specific implementation can calculate the [injectionRate].
     * */
    protected abstract fun calculateFlow(dt: Double)

    protected fun tick(dt: Double, phase: SimulationPhase) {
        ignitionUpdate.consume {
            ignition = it
        }

        val ignition = ignition

        /**
         * Ticks down the ignition:
         * */
        if(ignition != null) {
            ignition.ticksLeft--

            if(ignition.ticksLeft <= 0) {
                this.ignition = null
            }

            setChanged()
        }

        calculateFlow(dt)

        /**
         * The air injection rate:
         * */
        val massFlowRate = injectionRate + (ignition?.airInjectionRate ?: 0.0)

        /**
         * The quantity of air to inject each substep:
         * */
        val substepInjection = (massFlowRate * dt) / burnerCellOptions.substeps

        val substepDt = dt / burnerCellOptions.substeps

        for (substep in 0 until burnerCellOptions.substeps) {
            if(simulation.isDepleted) {
                if(simulation.slices.isNotEmpty()) {
                    simulation.slices = emptyArray()
                    setChanged()
                }

                break
            }

            /**
             * Enforces the minimum temperature of the bottom slice when igniting:
             * */
            if(ignition != null && simulation.slices[0].temperature < !ignition.targetTemperature) {
                simulation.slices[0].temperature = !ignition.targetTemperature
            }

            val output = simulation.tick(
                substepDt,
                substepInjection,
                !simulation.ambientTemperature
            )

            lastOutputGasTemperature = Quantity(output.temperature, KELVIN)
            lastOutputOxygenMassFlowRate = output.O2 / substepDt

            setChanged() // Just sets a flag, it's fine
        }
    }

    override fun saveCellData(): CompoundTag {
        val tag = CompoundTag()

        tag.putDouble(LAST_CHIMNEY_TEMPERATURE, simulation.lastChimneyTemperature)

        val ignition = ignition

        tag.putBoolean(HAS_IGNITION, ignition != null)

        if(ignition != null) {
            tag.putInt(IGNITION_TICKS_LEFT, ignition.ticksLeft)
            tag.putQuantity(IGNITION_TARGET_TEMPERATURE, ignition.targetTemperature)
            tag.putDouble(IGNITION_AIR_INJECTION_RATE, ignition.airInjectionRate)
        }

        val list = ListTag()
        simulation.slices.forEach { slice ->
            val sliceTag = CompoundTag()

            sliceTag.putResourceLocation(COAL_GRADE, CoalGradeRegistry.getId(slice.grade))
            sliceTag.putDouble(INITIAL_MASS, slice.initialMass)
            sliceTag.putDouble(ENERGY, slice.energy)
            sliceTag.putDouble(MASS, slice.mass)

            list.add(sliceTag)
        }

        tag.put(SLICE_LIST, list)

        return tag
    }

    override fun loadCellData(tag: CompoundTag) {
        simulation.lastChimneyTemperature = tag.getDouble(LAST_CHIMNEY_TEMPERATURE)

        if(tag.getBoolean(HAS_IGNITION)) {
            val ticksLeft = tag.getInt(IGNITION_TICKS_LEFT)
            val targetTemperature = tag.getQuantity<Temperature>(IGNITION_TARGET_TEMPERATURE)
            val airInjectionRate = tag.getDouble(IGNITION_AIR_INJECTION_RATE)

            ignition = Ignition(ticksLeft, targetTemperature, airInjectionRate)
        }

        val list = tag.getListTag(SLICE_LIST)
        val slices = ArrayList<BurnerSimulation.CoalSlice>(list.size)

        list.forEachCompound { sliceTag ->
            val coalGrade = CoalGradeRegistry.getById(sliceTag.getResourceLocation(COAL_GRADE))
            val initialMass = sliceTag.getDouble(INITIAL_MASS)
            val energy = sliceTag.getDouble(ENERGY)
            val mass = sliceTag.getDouble(MASS)

            val slice = BurnerSimulation.CoalSlice(
                coalGrade,
                initialMass,
                energy
            )

            slice.mass = mass

            slices.add(slice)
        }

        simulation.slices = slices.toTypedArray()
    }

    companion object {
        private const val LAST_CHIMNEY_TEMPERATURE = "lastChimneyTemperature"
        private const val HAS_IGNITION = "hasIgnition"
        private const val IGNITION_TICKS_LEFT = "ignitionTicksLeft"
        private const val IGNITION_TARGET_TEMPERATURE = "ignitionTargetTemperature"
        private const val IGNITION_AIR_INJECTION_RATE = "ignitionInjectionRate"

        private const val SLICE_LIST = "sliceList"
        private const val COAL_GRADE = "coalGrade"
        private const val INITIAL_MASS = "initialMass"
        private const val ENERGY = "energy"
        private const val MASS = "mass"

    }
}

//#endregion

/**
 * The mass of coal loaded per item.
 * */
private val COAL_ITEM_MASS = Quantity(1.0, KILOGRAM)

private data class BurnerActivityPacket(val injectionRate: Double) {
    companion object {
        fun serialize(packet: BurnerActivityPacket, buffer: FriendlyByteBuf) {
            buffer.writeDouble(packet.injectionRate)
        }

        fun deserialize(buffer: FriendlyByteBuf) = BurnerActivityPacket(
            buffer.readDouble(),
        )
    }
}

private data class HullTemperaturePacket(val hullTemperature: Double) {
    companion object {
        fun serialize(packet: HullTemperaturePacket, buffer: FriendlyByteBuf) {
            buffer.writeDouble(packet.hullTemperature)
        }

        fun deserialize(buffer: FriendlyByteBuf) = HullTemperaturePacket(
            buffer.readDouble()
        )
    }
}

//#region Primitive Burner

/**
 * Burner fed by the chimney effect.
 * Has only one output side.
 * */
class PrimitiveBurnerCell(
    ci: CellCreateInfo,
    burnerCellOptions: BurnerCellOptions,
    override val thermalMap: MonopoleMap,
    val maxDraftStrength: Double,
) : BurnerCell(ci, burnerCellOptions), SidedThermalMonoMapped<PrimitiveBurnerCell> {
    override val thermalSize: ThermalSize
        get() = ThermalSize.Standard

    /**
     * The control parameter, in the range `[0, 1]`.
     * */
    var controlParameter = 0.0

    /**
     * Sets the injection rate based on the control parameter
     * */
    override fun calculateFlow(dt: Double) {
        injectionRate = if(simulation.isDepleted) {
            0.0
        } else {
            simulation.calculateNaturalDraftFlowRate(
                max(
                    max(
                        simulation.lastChimneyTemperature,
                        !hull.thermalBody.temperature
                    ),
                    if(simulation.isDepleted) 0.0 else simulation.slices.maxOf { it.temperature }
                ),
                controlParameter * maxDraftStrength
            )
        }
    }

    override fun saveCellData(): CompoundTag {
        val tag = super.saveCellData()
        tag.putDouble(CONTROL_PARAMETER, controlParameter)
        return tag
    }

    override fun loadCellData(tag: CompoundTag) {
        super.loadCellData(tag)
        controlParameter = tag.getDouble(CONTROL_PARAMETER)
    }

    companion object {
        private const val CONTROL_PARAMETER = "controlParameter"
    }
}

class PrimitiveBurnerBlock : UprightHorizontalDirectionCellBlock<PrimitiveBurnerCell>() {
    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction): Boolean = true

    override fun getCellProvider() = Eln2HeatGenerators.PRIMITIVE_BURNER_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = PrimitiveBurnerBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T?>
    ): BlockEntityTicker<T?> {
        if (pLevel.isClientSide) {
            return BlockEntityTicker { _, _, _, pBlockEntity ->
                if (pBlockEntity is PrimitiveBurnerBlockEntity) {
                    pBlockEntity.clientTick()
                }
            }
        }

        return BlockEntityTicker { _, _, _, pBlockEntity ->
            if (pBlockEntity is PrimitiveBurnerBlockEntity) {
                pBlockEntity.serverTick()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        val blockEntity = pLevel.getBlockEntity(pPos) as? PrimitiveBurnerBlockEntity
            ?: return InteractionResult.FAIL

        if(pHand != InteractionHand.MAIN_HAND) {
            return InteractionResult.FAIL
        }

        return blockEntity.interact(pPlayer)
    }

    override fun animateTick(pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) {
        val blockEntity = pLevel.getBlockEntity(pPos) as? PrimitiveBurnerBlockEntity
            ?: return

        if (blockEntity.renderState?.injectionRate?.let { it > 0.0 } != true) {
            return
        }

        val facing = pState.getValue(HorizontalDirectionalBlock.FACING)
        val centerX = pPos.x + 0.5
        val baseY = pPos.y.toDouble()
        val centerZ = pPos.z + 0.5

        if (pRandom.nextDouble() < 0.1) {
            pLevel.playLocalSound(
                centerX,
                baseY + 0.5,
                centerZ,
                SoundEvents.FURNACE_FIRE_CRACKLE,
                SoundSource.BLOCKS,
                pRandom.nextDouble(0.9, 1.1).toFloat(),
                pRandom.nextDouble(0.9, 1.1).toFloat(),
                false
            )
        }

        repeat(3) {
            val offset = pRandom.nextDouble() * 0.6 - 0.3
            val offsetX = if (facing.axis === Direction.Axis.X) facing.stepX.toDouble() * 0.52 else offset
            val offsetY = pRandom.nextDouble() * 6.0 / 16.0
            val offsetZ = if (facing.axis === Direction.Axis.Z) facing.stepZ.toDouble() * 0.52 else offset

            pLevel.addParticle(ParticleTypes.SMOKE, centerX + offsetX, baseY + offsetY, centerZ + offsetZ, 0.0, 0.0, 0.0)
        }
    }
}

class PrimitiveBurnerBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity<PrimitiveBurnerCell>(pos, state, Eln2HeatGenerators.PRIMITIVE_BURNER_BLOCK_ENTITY.get()),
    BulkPacketHandlerBlockEntity,
    InternalMultiThermalBodyTemperatureConsumer,
    ExternalTemperatureConsumer,
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ComponentDisplay
{
    @ServerOnly
    private var lastSentInjectionRate = Double.NaN

    @ServerOnly
    fun interact(player: Player) : InteractionResult {
        val stack = player.mainHandItem

        if(stack.count <= 0) {
            return InteractionResult.FAIL
        }

        when(stack.item) {
            Items.FLINT_AND_STEEL -> {
                if(player.level().isClientSide) {
                    return InteractionResult.SUCCESS
                }

                cell.ignite()
                stack.takeDurability(player)

                return InteractionResult.CONSUME_PARTIAL
            }

            Items.COAL -> {
                if(player.level().isClientSide) {
                    return InteractionResult.SUCCESS
                }

                if(!cell.canAddCoal(COAL_ITEM_MASS)) {
                    return InteractionResult.FAIL
                }

                cell.addCoal(
                    CoalGradeRegistry.DEFAULT,
                    COAL_ITEM_MASS
                )

                stack.eln2Consume(player)
                return InteractionResult.CONSUME
            }

            else -> {
                return InteractionResult.FAIL
            }
        }
    }

    @ServerOnly
    override fun scrollScrewdriver(player: ServerPlayer, delta: Double): Boolean {
        val parameter = cell.controlParameter
        val newParameter = (parameter + delta / 10.0).coerceIn(0.0, 1.0)

        if(newParameter != parameter) {
            cell.controlParameter = newParameter
            cell.setChanged()
            sendBulkPacket(ControlValvePacket::serialize, ControlValvePacket(newParameter))
            return true
        }

        return false
    }


    @ServerOnly
    override fun applyScrewdriver(screwdriver: ScrewdriverItem, context: UseOnContext, configValue: OptionalDouble) {
        if (!configValue.isPresent) {
            context.player!!.sendSystemMessage(Component.translatable("waila.eln2.injection_rate"))
            return
        }

        val clamped = configValue.unwrap().coerceIn(0.0, 1.0)

        if (clamped != cell.controlParameter) {
            cell.controlParameter = clamped
            cell.setChanged()
            sendBulkPacket(ControlValvePacket::serialize, ControlValvePacket(clamped))
        }

        context.player!!.sendSystemMessage(Component.translatable("waila.eln2.injection_rate").append(": ").append(String.format("%.3f", clamped)))
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Input rate: ${(cell.injectionRate * 1000.0).rounded()} g/s" }
        builder.debugInIDE { "Output gas temperature: ${cell.lastOutputGasTemperature.classify()}"}
        builder.debugInIDE { "Output flow rate O2: ${(cell.lastOutputOxygenMassFlowRate * 1000.0).rounded()} g/s" }
        builder.quantity(cell.hull.thermalBody.temperature)
        builder.quantity(Quantity(cell.simulation.slices.sumOf { it.mass }, KILOGRAM))
    }

    //#region Client-Side Rendering State

    override val clientSidePacketHandlerLazy = createClientSideHandler()

    class RenderState {
        var hullTemperature = 0.0
        var externalTemperature = OptionalDouble.EMPTY
        var controlParameter = 0.0
        var injectionRate = 0.0

        val draftSmoother = FramerateIndependentSmoother1d(0.3)

        var draftSound: SimpleLoopingBlockEntitySoundInstance<PrimitiveBurnerBlockEntity>? = null
    }

    var renderState: RenderState? = null
        private set

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if(pLevel.isClientSide) {
            renderState = RenderState()
        }
    }

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        fun modifyState(action: RenderState.() -> Unit) {
            val renderState = renderState
                ?: return

            action(renderState)
        }

        handler.withHandler<HullTemperaturePacket>(HullTemperaturePacket::deserialize) {
            modifyState {
                hullTemperature = it.hullTemperature
            }
        }

        handler.withHandler<ExternalTemperaturePacket>(ExternalTemperaturePacket::deserialize) {
            modifyState {
                externalTemperature = it.externalTemperature
            }
        }

        handler.withHandler<ControlValvePacket>(ControlValvePacket::deserialize) {
            modifyState {
                controlParameter = it.controlParameter
            }
        }

        handler.withHandler<BurnerActivityPacket>(BurnerActivityPacket::deserialize) {
            modifyState {
                injectionRate = it.injectionRate
            }
        }
    }

    @ClientOnly
    fun clientTick() {
        val state = renderState ?: return

        if (state.draftSound == null) {
            state.draftSound = SimpleLoopingBlockEntitySoundInstance(this, Eln2HeatGenerators.BURNER_DRAFT_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    state.draftSmoother.update(state.injectionRate)
                    it.soundInfo = SoundInfo.draft(state.draftSmoother.value, Eln2HeatGenerators.PRIMITIVE_BURNER_MAX_DRAFT)
                }

                it.registerOnAudioManager()
            }
        }
    }

    @ServerOnly
    fun serverTick() {
        val currentInjectionRate = cell.injectionRate

        if (!currentInjectionRate.approxEq(lastSentInjectionRate, 0.001)) {
            lastSentInjectionRate = currentInjectionRate

            sendBulkPacket(
                BurnerActivityPacket::serialize,
                BurnerActivityPacket(currentInjectionRate)
            )
        }
    }

    //#endregion

    //#region Sync

    @ServerOnly
    override fun onInternalTemperatureChanges(dirty: List<ThermalMass>) {
        sendBulkPacket(HullTemperaturePacket::serialize, HullTemperaturePacket(!dirty[0].temperature))
    }

    @ServerOnly
    override fun onExternalTemperatureChanges(
        removed: HashSet<ThermalObject<*>>,
        dirty: HashMap<ThermalObject<*>, Double>,
        all: HashMap<ThermalObject<*>, Double>,
    ) {
        sendBulkPacket(
            ExternalTemperaturePacket::serialize,
            if (removed.isNotEmpty()) {
                ExternalTemperaturePacket(OptionalDouble.EMPTY)
            }
            else {
                ExternalTemperaturePacket(OptionalDouble.wrap(dirty.values.first()))
            }
        )

    }

    // onSyncSuggested
    @ServerOnly
    override fun getUpdateTag(): CompoundTag {
        sendBulkPacket(
            HullTemperaturePacket::serialize,
            HullTemperaturePacket(!cell.hull.thermalBody.temperature)
        )

        sendBulkPacket(
            ExternalTemperaturePacket::serialize,
            ExternalTemperaturePacket(
                if (cell.hull.connections.isNotEmpty()) {
                    OptionalDouble.wrap(!cell.hull.getContactTemperature(cell.hull.connections[0].cell))
                }
                else {
                    OptionalDouble.EMPTY
                }
            )
        )

        sendBulkPacket(
            ControlValvePacket::serialize,
            ControlValvePacket(cell.controlParameter)
        )

        val currentInjectionRate = cell.injectionRate
        lastSentInjectionRate = currentInjectionRate

        sendBulkPacket(
            BurnerActivityPacket::serialize,
            BurnerActivityPacket(currentInjectionRate)
        )

        return super.getUpdateTag()
    }

    data class ExternalTemperaturePacket(val externalTemperature: OptionalDouble) {
        companion object {
            fun serialize(packet: ExternalTemperaturePacket, buffer: FriendlyByteBuf) {
                buffer.writeOptionalDouble(packet.externalTemperature)
            }

            fun deserialize(buffer: FriendlyByteBuf) = ExternalTemperaturePacket(
                buffer.readOptionalDouble()
            )
        }
    }

    data class ControlValvePacket(val controlParameter: Double) {
        companion object {
            fun serialize(packet: ControlValvePacket, buffer: FriendlyByteBuf) {
                buffer.writeDouble(packet.controlParameter)
            }

            fun deserialize(buffer: FriendlyByteBuf) = ControlValvePacket(
                buffer.readDouble()
            )
        }
    }

    //#endregion
}

class ThermalConduitConnection(
    val level: Level,
    val position: BlockPos,
    val context: VisualizationContext,
    val model: PolarModel,
) {
    var instance: TransformedPolarInstance? = null

    fun create(transform: (TransformedPolarInstance) -> Unit) {
        check(instance == null) {
            "Cannot re-create conduit instance"
        }

        instance = context.instancerProvider()
            .instancer(FlwInstanceTypes.TRANSFORMED_POLAR, FlwModels.PRIMITIVE_COAL_BURNER_CONDUIT.get())
            .createInstance()

        transform(instance!!)
    }

    fun createIfNeeded(transform: (TransformedPolarInstance) -> Unit) {
        if(instance == null) {
            create(transform)
        }
    }

    fun update(coreTemperature: Double, remoteTemperature: Double, remotePositionWorld: BlockPos) {
        val instance = instance
            ?: error("Cannot update thermal conduit instance: not created!")

        val coreColor = run {
            val lightLevel = LightTexture.block(
                LevelRenderer.getLightColor(
                    level,
                    position
                )
            )

            ThermalTint.DEFAULT.evaluateRGBL(
                Quantity(coreTemperature), lightLevel.toDouble()
            )
        }

        val remoteColor = run {
            val remoteLightLevel = level.getBrightness(
                LightLayer.BLOCK,
                remotePositionWorld
            )

            ThermalTint.DEFAULT.evaluateRGBL(
                Quantity(remoteTemperature),
                remoteLightLevel.toDouble()
            )
        }

        instance.light(
            0, LightTexture.sky(
                LevelRenderer.getLightColor(
                level,
                remotePositionWorld
            )
        ))
        instance.color1 = coreColor
        instance.color2 = MyColor.lerp(coreColor, remoteColor, 0.5f)
        instance.setChanged()
    }

    fun delete() {
        instance?.delete()
        instance = null
    }
}

class PrimitiveBurnerBlockEntityVisual(ctx: VisualizationContext, blockEntity: PrimitiveBurnerBlockEntity, partialTick: Float) : AbstractBlockEntityVisual<PrimitiveBurnerBlockEntity>(ctx, blockEntity, partialTick), SimpleDynamicVisual {
    companion object {
        const val SLIDING_RANGE = 3.5 / 16.0
    }

    val body: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(FlwModels.PRIMITIVE_COAL_BURNER_BODY))
        .createInstance()
        .transformFacingBlock(visualPos, blockEntity)

    val hull: TransformedLightOverrideInstance = visualizationContext.instancerProvider()
        .instancer(FlwInstanceTypes.TRANSFORMED_LIGHT_OVERRIDE, Models.partial(FlwModels.PRIMITIVE_COAL_BURNER_HULL))
        .createInstance()
        .transformFacingBlock(visualPos, blockEntity)

    var conduit = ThermalConduitConnection(level, blockEntity.blockPos, ctx, FlwModels.PRIMITIVE_COAL_BURNER_CONDUIT)

    val door: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, Models.partial(FlwModels.PRIMITIVE_COAL_BURNER_DOOR))
        .createInstance()

    var hullTemperature = 0.0
    var externalTemperature = OptionalDouble.EMPTY
    var controlParameter = 0.0

    private fun applyHullTemperature() {
        val color = ThermalTint.DEFAULT_LIGHT_OVERRIDE.evaluate(Quantity(hullTemperature, KELVIN))
        hull.color(color.r, color.g, color.b)
        hull.lightOverride = color.a / 255.0f
        hull.setChanged()
    }

    private fun updateConduit() {
        if(!externalTemperature.isPresent) {
            conduit.delete()
            return
        }

        conduit.createIfNeeded {
            it.transformFacingBlock(visualPos, blockEntity)
        }

        conduit.update(
            hullTemperature,
            externalTemperature.unwrap(),
            blockEntity.blockPos - blockState.getValue(HorizontalDirectionalBlock.FACING)
        )
    }

    private fun applyControlParameter() {
        door.setIdentityTransform()
        door.transformFacingBlock(visualPos, blockEntity)
        door.translateX(-(SLIDING_RANGE * controlParameter).toFloat())
        door.setChanged()
    }

    init {
        applyHullTemperature()
        updateConduit()
        applyControlParameter()
    }

    override fun beginFrame(p0: DynamicVisual.Context?) {
        val renderState = blockEntity.renderState
            ?: return

        val targetHullTemperature = renderState.hullTemperature
        val targetExternalTemperature = renderState.externalTemperature
        val targetControlParameter = renderState.controlParameter

        if(targetHullTemperature != hullTemperature && targetExternalTemperature != externalTemperature) {
            hullTemperature = targetHullTemperature
            externalTemperature = targetExternalTemperature
            applyHullTemperature()
            updateConduit()
        }
        else {
            if(targetHullTemperature != hullTemperature) {
                hullTemperature = targetHullTemperature
                applyHullTemperature()
                updateConduit()
            }

            if(targetExternalTemperature != externalTemperature) {
                externalTemperature = targetExternalTemperature
                updateConduit()
            }
        }

        if(targetControlParameter != controlParameter) {
            controlParameter = targetControlParameter
            applyControlParameter()
        }
    }

    override fun updateLight(p0: Float) {
        relight(body, hull, door)
        updateConduit()
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        p0.accept(body)
        p0.accept(hull)
    }

    override fun _delete() {
        body.delete()
        hull.delete()
        conduit.delete()
        door.delete()
    }
}

//#endregion

//#region Advanced Coal Burner

private const val ADVANCED_BURNER_FUEL_SLOT_COUNT = 1

/**
 * Advanced burner cell with PID temperature setpoint control.
 * Uses a [PIDController] to modulate the draft strength to maintain a target hull temperature.
 * @param maxDraftStrength The maximum draft strength (mass flow rate of air).
 * @param pidGains The PID gains for the temperature controller.
 * */
class AdvancedBurnerCell(
    ci: CellCreateInfo,
    burnerCellOptions: BurnerCellOptions,
    override val thermalMap: MonopoleMap,
    val maxDraftStrength: Double,
    val pidGains: PIDGains,
) : BurnerCell(ci, burnerCellOptions), SidedThermalMonoMapped<AdvancedBurnerCell> {

    override val thermalSize: ThermalSize
        get() = ThermalSize.Standard

    /**
     * The target hull temperature, in Kelvin.
     * Set via screwdriver scroll on the block entity.
     * */
    var targetTemperature: Quantity<Temperature> = Quantity(800.0, KELVIN)
        private set

    /**
     * Updates the target temperature. Called from the block entity when the screwdriver is scrolled.
     * */
    fun setTargetTemperature(value: Quantity<Temperature>) {
        targetTemperature = value
        controller.setPoint = !value
        setChanged()
    }

    /**
     * The PID controller that drives the draft strength.
     * Its output is clamped to [0, maxDraftStrength] via [PIDController.minControl] and [PIDController.maxControl].
     * */
    private val controller = PIDController(pidGains.kP, pidGains.kI, pidGains.kD).also {
        it.setPoint = !targetTemperature
        it.minControl = 0.0
        it.maxControl = maxDraftStrength
    }

    override fun calculateFlow(dt: Double) {
        if (simulation.isDepleted) {
            injectionRate = 0.0
            controller.reset()
            return
        }

        val hullTemp = !hull.thermalBody.temperature

        val controlledDraft = controller.update(hullTemp, dt)

        val draftTemp = max(
            max(
                simulation.lastChimneyTemperature,
                hullTemp
            ),
            if (simulation.isDepleted) 0.0 else simulation.slices.maxOf { it.temperature }
        )

        injectionRate = simulation.calculateNaturalDraftFlowRate(
            draftTemp,
            controlledDraft
        )
    }

    override fun saveCellData(): CompoundTag {
        val tag = super.saveCellData()
        tag.putQuantity(TARGET_TEMPERATURE, targetTemperature)
        tag.putDouble(CONTROLLER_ERROR_SUM, controller.errorSum)
        tag.putDouble(CONTROLLER_LAST_ERROR, controller.lastError)
        return tag
    }

    override fun loadCellData(tag: CompoundTag) {
        super.loadCellData(tag)
        targetTemperature = tag.getQuantity(TARGET_TEMPERATURE)
        controller.setPoint = !targetTemperature
        controller.errorSum = tag.getDouble(CONTROLLER_ERROR_SUM)
        controller.lastError = tag.getDouble(CONTROLLER_LAST_ERROR)
    }

    companion object {
        private const val TARGET_TEMPERATURE = "targetTemperature"
        private const val CONTROLLER_ERROR_SUM = "controllerErrorSum"
        private const val CONTROLLER_LAST_ERROR = "controllerLastError"
    }
}

/**
 * Representative block for the Advanced Coal Burner.
 * This is the bottom block (combustion chamber). The top block is a delegate (fuel hopper / chimney).
 * */
class AdvancedCoalBurnerBlock : UprightHorizontalDirectionCellBlock<AdvancedBurnerCell>() {
    companion object {
        fun constructMenu(pLevel: Level, pPos: BlockPos, pPlayer: Player) =
            pLevel.constructMenuHelper2<AdvancedCoalBurnerBlockEntity>(
                pPos,
                pPlayer,
                Component.translatable("menu.$MODID.advanced_coal_burner"),
                ::AdvancedCoalBurnerMenu
            )

        /**
         * The chimney opening in model space (BlockBench 16x coordinates), authored for the SOUTH-facing model.
         * The visual applies [TransformedInstance.rotateToFace] with the facing's opposite, so we replicate that rotation here.
         * */
        private const val CHIMNEY_MODEL_X = 2.0 / 16.0
        private const val CHIMNEY_MODEL_Y = 27.0 / 16.0
        private const val CHIMNEY_MODEL_Z = 9.0 / 16.0

        private fun rotatedChimneyOffset(facing: Direction): Vector3d {
            val pivotX = 0.5
            val pivotZ = 0.5
            val localX = CHIMNEY_MODEL_X - pivotX
            val localZ = CHIMNEY_MODEL_Z - pivotZ

            val angle = facing.opposite.toHorizontalFacing().angle
            val cos = cos(angle)
            val sin = sin(angle)

            val worldX = pivotX + cos * localX + sin * localZ
            val worldZ = pivotZ - sin * localX + cos * localZ

            return Vector3d(worldX, CHIMNEY_MODEL_Y, worldZ)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction): Boolean = true

    override fun getCellProvider() = Eln2HeatGenerators.ADVANCED_COAL_BURNER_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = AdvancedCoalBurnerBlockEntity(pPos, pState)

    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T?>
    ): BlockEntityTicker<T> {
        if (pLevel.isClientSide) {
            return BlockEntityTicker { _, _, _, pBlockEntity ->
                if (pBlockEntity is AdvancedCoalBurnerBlockEntity) {
                    pBlockEntity.clientTick()
                }
            }
        }

        return BlockEntityTicker { _, _, _, pBlockEntity ->
            if (pBlockEntity is AdvancedCoalBurnerBlockEntity) {
                pBlockEntity.serverTick()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        val blockEntity = pLevel.getBlockEntity(pPos) as? AdvancedCoalBurnerBlockEntity
            ?: return InteractionResult.FAIL

        if (pHand != InteractionHand.MAIN_HAND) {
            return InteractionResult.FAIL
        }

        val result = blockEntity.interact(pPlayer)

        if (result == InteractionResult.FAIL) {
            return constructMenu(pLevel, pPos, pPlayer)
        }

        return result
    }

    override fun animateTick(pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) {
        val blockEntity = pLevel.getBlockEntity(pPos) as? AdvancedCoalBurnerBlockEntity
            ?: return

        if (blockEntity.renderState?.injectionRate?.let { it > 0.0 } != true) {
            return
        }

        val facing = pState.getValue(HorizontalDirectionalBlock.FACING)
        val chimneyOffset = rotatedChimneyOffset(facing)

        val smokeX = pPos.x + chimneyOffset.x
        val smokeY = pPos.y + chimneyOffset.y
        val smokeZ = pPos.z + chimneyOffset.z

        if (pRandom.nextDouble() < 0.1) {
            pLevel.playLocalSound(
                smokeX,
                smokeY,
                smokeZ,
                SoundEvents.FURNACE_FIRE_CRACKLE,
                SoundSource.BLOCKS,
                pRandom.nextDouble(0.9, 1.1).toFloat(),
                pRandom.nextDouble(0.9, 1.1).toFloat(),
                false
            )
        }

        repeat(4) {
            val spreadX = pRandom.nextDouble() * 0.4 - 0.2
            val spreadZ = pRandom.nextDouble() * 0.4 - 0.2

            pLevel.addParticle(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                smokeX + spreadX,
                smokeY,
                smokeZ + spreadZ,
                0.0,
                0.05 + pRandom.nextDouble() * 0.02,
                0.0
            )
        }
    }
}

/**
 * Delegate block for the Advanced Coal Burner (top block, fuel hopper / chimney).
 * Forwards item handler capabilities and interaction to the representative.
 * */
class AdvancedCoalBurnerDelegateBlockEntity(pos: BlockPos, state: BlockState) : MultiblockDelegateBlockEntity(pos, state, Eln2HeatGenerators.ADVANCED_COAL_BURNER_DELEGATE_BLOCK_ENTITY.get()) {
    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        val representativePos = this.representativePos
            ?: return super.getCapability(cap, side)

        val level = this.level
            ?: return super.getCapability(cap, side)

        if (!level.isLoaded(representativePos)) {
            return LazyOptional.empty()
        }

        val representative = level.getBlockEntity(representativePos) as? AdvancedCoalBurnerBlockEntity
            ?: return super.getCapability(cap, side)

        return representative.getCapability(cap, null)
    }
}

/**
 * Representative block entity for the Advanced Coal Burner.
 * Lives at the bottom block. Owns the [AdvancedBurnerCell], fuel inventory, and menu.
 * The device is insulated: it only exchanges heat through its thermal port, not radiantly to neighbors.
 * */
class AdvancedCoalBurnerBlockEntity(pos: BlockPos, state: BlockState) :
    CellBlockEntity<AdvancedBurnerCell>(pos, state, Eln2HeatGenerators.ADVANCED_COAL_BURNER_BLOCK_ENTITY.get()),
    BigBlockRepresentativeBlockEntity<AdvancedCoalBurnerBlockEntity>,
    BulkPacketHandlerBlockEntity,
    InternalMultiThermalBodyTemperatureConsumer,
    ScrewdriverScrollable,
    ScrewdriverInteractable,
    ComponentDisplay
{
    companion object {
        private const val FUEL_INVENTORY = "fuelInventory"
    }

    @ServerOnly
    private var lastSentInjectionRate = Double.NaN

    override val delegateMap: MultiblockDelegateMap
        get() = Eln2HeatGenerators.ADVANCED_COAL_BURNER_DELEGATE_MAP.value

    override fun setDestroyed() {
        destroyDelegates()
        super.setDestroyed()
    }

    override fun onDelegateUse(
        delegate: BlockEntity,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult
    ) = AdvancedCoalBurnerBlock.constructMenu(level!!, blockPos, pPlayer)

    val cellProgressData = ProgressContainerData()

    //#region Inventory

    class FuelInventoryHandler(val blockEntity: AdvancedCoalBurnerBlockEntity) :
        ItemStackHandler(ADVANCED_BURNER_FUEL_SLOT_COUNT) {

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return stack.`is`(Eln2ConventionTags.COAL_EQUIVALENT)
        }

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if (!stack.`is`(Eln2ConventionTags.COAL_EQUIVALENT)) {
                return stack
            }

            return super.insertItem(slot, stack, simulate)
        }

        override fun onContentsChanged(slot: Int) {
            blockEntity.setChanged()
        }
    }

    val fuelHandler = FuelInventoryHandler(this)
    val fuelHandlerLazy: LazyOptional<FuelInventoryHandler> = LazyOptional.of { fuelHandler }

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return fuelHandlerLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        fuelHandlerLazy.invalidate()
    }

    //#endregion

    @ServerOnly
    fun interact(player: Player): InteractionResult {
        val stack = player.mainHandItem

        if (stack.item == Items.FLINT_AND_STEEL) {
            if (player.level().isClientSide) {
                return InteractionResult.SUCCESS
            }

            cell.ignite()
            stack.takeDurability(player)

            return InteractionResult.CONSUME_PARTIAL
        }

        return InteractionResult.FAIL
    }

    @ServerOnly
    override fun scrollScrewdriver(player: ServerPlayer, delta: Double): Boolean {
        val current = !cell.targetTemperature
        val step = 5.0
        val newTemp = (current + delta * step).coerceIn(300.0, 1100.0)

        if (newTemp != current) {
            cell.setTargetTemperature(Quantity(newTemp, KELVIN))
            setChanged()
            sendBulkPacket(SetpointPacket::serialize, SetpointPacket(newTemp))
            return true
        }

        return false
    }

    @ServerOnly
    override fun applyScrewdriver(screwdriver: ScrewdriverItem, context: UseOnContext, configValue: OptionalDouble) {
        if (!configValue.isPresent) {
            context.player!!.sendSystemMessage(Component.translatable("waila.eln2.Temperature_setpoint_implicit"))
            return
        }

        val clamped = configValue.unwrap().coerceIn(300.0, 1500.0)

        if (clamped != !cell.targetTemperature) {
            cell.setTargetTemperature(Quantity(clamped, KELVIN))
            setChanged()
            sendBulkPacket(SetpointPacket::serialize, SetpointPacket(clamped))
        }

        context.player!!.sendSystemMessage(Component.translatable("waila.eln2.Temperature_setpoint_implicit").append(": ").append(String.format("%.3f", clamped)))
    }

    @ServerOnly
    fun serverTick() {
        cellProgressData.progress = cell.coalFillLevel

        if (cell.canAddCoal(COAL_ITEM_MASS)) {
            val fuelStack = fuelHandler.getStackInSlot(0)

            if (!fuelStack.isEmpty) {
                fuelHandler.extractItem(0, 1, false)

                cell.addCoal(
                    CoalGradeRegistry.DEFAULT,
                    COAL_ITEM_MASS
                )

                setChanged()
            }
        }
        val currentInjectionRate = cell.injectionRate

        if (!currentInjectionRate.approxEq(lastSentInjectionRate, 0.001)) {
            lastSentInjectionRate = currentInjectionRate
            sendBulkPacket(
                BurnerActivityPacket::serialize,
                BurnerActivityPacket(currentInjectionRate)
            )
        }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        pTag.put(FUEL_INVENTORY, fuelHandler.serializeNBT())
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        fuelHandler.deserializeNBT(pTag.getCompound(FUEL_INVENTORY))
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        builder.debugInIDE { "Input rate: ${(cell.injectionRate * 1000.0).rounded()} g/s" }
        builder.debugInIDE { "Output gas temperature: ${cell.lastOutputGasTemperature.classify()}" }
        builder.debugInIDE { "Output flow rate O2: ${(cell.lastOutputOxygenMassFlowRate * 1000.0).rounded()} g/s" }
        builder.quantity(cell.hull.thermalBody.temperature)
        builder.quantity(cell.targetTemperature)
        builder.quantity(Quantity(cell.totalMass, KILOGRAM))
    }

    //#region Client-Side Rendering State

    override val clientSidePacketHandlerLazy = createClientSideHandler()

    class RenderState {
        var hullTemperature = 0.0
        var setpointTemperature = 800.0
        var injectionRate = 0.0

        val draftSmoother = FramerateIndependentSmoother1d(0.3)
        var draftSound: SimpleLoopingBlockEntitySoundInstance<AdvancedCoalBurnerBlockEntity>? = null
    }

    var renderState: RenderState? = null
        private set

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (pLevel.isClientSide) {
            renderState = RenderState()
        }
    }

    @ClientOnly
    override fun setupPacketsOnClient(handler: ClientSidePacketHandlerBuilder) {
        fun modifyState(action: RenderState.() -> Unit) {
            val renderState = renderState ?: return
            action(renderState)
        }

        handler.withHandler<HullTemperaturePacket>(HullTemperaturePacket::deserialize) {
            modifyState {
                hullTemperature = it.hullTemperature
            }
        }

        handler.withHandler<SetpointPacket>(SetpointPacket::deserialize) {
            modifyState {
                setpointTemperature = it.setpointTemperature
            }
        }

        handler.withHandler<BurnerActivityPacket>(BurnerActivityPacket::deserialize) {
            modifyState {
                injectionRate = it.injectionRate
            }
        }
    }

    @ClientOnly
    fun clientTick() {
        val state = renderState ?: return

        if (state.draftSound == null) {
            state.draftSound = SimpleLoopingBlockEntitySoundInstance(this, Eln2HeatGenerators.BURNER_DRAFT_SOUND.get()).also {
                it.events.registerHandler<SoundInstanceTickEvent> { _ ->
                    state.draftSmoother.update(state.injectionRate)
                    it.soundInfo = SoundInfo.draft(state.draftSmoother.value, Eln2HeatGenerators.ADVANCED_COAL_BURNER_MAX_DRAFT)
                }

                it.registerOnAudioManager()
            }
        }
    }

    //#endregion
    //#region Sync

    @ServerOnly
    override fun onInternalTemperatureChanges(dirty: List<ThermalMass>) {
        sendBulkPacket(HullTemperaturePacket::serialize, HullTemperaturePacket(!dirty[0].temperature))
    }

    @ServerOnly
    override fun getUpdateTag(): CompoundTag {
        sendBulkPacket(
            HullTemperaturePacket::serialize,
            HullTemperaturePacket(!cell.hull.thermalBody.temperature)
        )

        sendBulkPacket(
            SetpointPacket::serialize,
            SetpointPacket(!cell.targetTemperature)
        )

        val currentInjectionRate = cell.injectionRate
        lastSentInjectionRate = currentInjectionRate

        sendBulkPacket(
            BurnerActivityPacket::serialize,
            BurnerActivityPacket(currentInjectionRate)
        )

        return super.getUpdateTag()
    }

    data class SetpointPacket(val setpointTemperature: Double) {
        companion object {
            fun serialize(packet: SetpointPacket, buffer: FriendlyByteBuf) {
                buffer.writeDouble(packet.setpointTemperature)
            }

            fun deserialize(buffer: FriendlyByteBuf) = SetpointPacket(
                buffer.readDouble()
            )
        }
    }

    //#endregion
}

/**
 * Menu for the Advanced Coal Burner.
 * Shows a fuel slot and a progress bar representing the coal fill level.
 * */
class AdvancedCoalBurnerMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: ProgressContainerData,
    val access: ContainerLevelAccess,
    val level: Level,
) : AbstractContainerMenu(Eln2HeatGenerators.ADVANCED_COAL_BURNER_MENU.get(), pContainerId), ProgressSupplierMenu {

    companion object {
        private val FUEL_SLOT_POS = Vector2di(80, 43)
    }

    @ServerOnly
    constructor(entity: AdvancedCoalBurnerBlockEntity, id: Int, inventory: Inventory) : this(
        id,
        inventory,
        entity.fuelHandler,
        entity.cellProgressData,
        ContainerLevelAccess.create(entity.level!!, entity.blockPos),
        entity.level!!
    )

    @ClientOnly
    constructor(pContainerId: Int, playerInventory: Inventory) : this(
        pContainerId,
        playerInventory,
        ItemStackHandler(ADVANCED_BURNER_FUEL_SLOT_COUNT),
        ProgressContainerData(),
        ContainerLevelAccess.NULL,
        playerInventory.player.level()
    )

    init {
        addSlot(
            SlotItemHandlerWithPlacePredicate(handler, 0, FUEL_SLOT_POS.x, FUEL_SLOT_POS.y) {
                it.`is`(Eln2ConventionTags.COAL_EQUIVALENT)
            }
        )

        addDataSlots(containerData)

        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) =
        stillValid(access, pPlayer, Eln2HeatGenerators.ADVANCED_COAL_BURNER_BLOCK.get())

    override fun quickMoveStack(pPlayer: Player, pIndex: Int) =
        ContainerHelper.quickMove(slots, pPlayer, pIndex)

    override fun getProgressForRender() = containerData.progress
}

class AdvancedCoalBurnerMainBlockEntityVisual(
    ctx: VisualizationContext,
    blockEntity: AdvancedCoalBurnerBlockEntity,
    partialTick: Float
) : AbstractBlockEntityVisual<AdvancedCoalBurnerBlockEntity>(ctx, blockEntity, partialTick), ShaderLightVisual {
    val body: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(FlwModels.ADVANCED_COAL_BURNER, FlwMaterials.CUTOUT_SMOOTH_LIT))
        .createInstance()
        .also {
            it.translate(visualPosition)
            it.center()
            it.rotateToFace(blockEntity.representativeFacing.opposite)
            it.uncenter()
        }

    override fun updateLight(p0: Float) {
        // NOOP
    }

    override fun setSectionCollector(sectionCollector: SectionTrackedVisual.SectionCollector) {
        this.lightSections = sectionCollector

        sectionCollector.sections(
            blockEntity.delegateMap.getTotalSpannedSectionsFat(
                blockState.getValue(HorizontalDirectionalBlock.FACING),
                pos
            )
        )
    }

    override fun collectCrumblingInstances(p0: Consumer<Instance?>) {
        p0.accept(body)
    }

    override fun _delete() {
        body.delete()
    }
}

//#endregion
