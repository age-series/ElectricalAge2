package org.eln2.mc.common.cells.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.server.level.ServerLevel
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.common.space.BlockFaceLocator
import org.eln2.mc.common.space.SO3
import org.eln2.mc.common.space.requireLocator
import org.eln2.mc.data.DataNode
import org.eln2.mc.data.DataEntity
import org.eln2.mc.extensions.destroyPart
import org.eln2.mc.extensions.formattedPercentN
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.sim.ThermalBody

interface CellBehavior {
    fun onAdded(container: CellBehaviorContainer) { }
    fun subscribe(subscribers: SubscriberCollection) { }
    fun destroy() { }
}

class CellBehaviorContainer(private val cell: Cell) : DataEntity {
    val behaviors = ArrayList<CellBehavior>()

    fun forEach(action: ((CellBehavior) -> Unit)) {
        behaviors.forEach(action)
    }

    inline fun <reified T : CellBehavior> getOrNull(): T? {
        return behaviors.first { it is T } as? T
    }

    inline fun <reified T : CellBehavior> get(): T {
        return getOrNull() ?: error("Failed to get behavior")
    }

    inline fun <reified T : CellBehavior> add(behavior: T): CellBehaviorContainer {
        if(behaviors.any { it is T }){
            error("Duplicate add behavior $behavior")
        }

        behaviors.add(behavior)

        if(behavior is DataEntity) {
            dataNode.withChild(behavior.dataNode)
        }

        behavior.onAdded(this)

        return this
    }

    fun changeGraph(){
        behaviors.forEach { it.subscribe(cell.subscribers) }
    }

    fun destroy() {
        behaviors.forEach {
            if(it is DataEntity) {
                dataNode.children.removeIf { access -> access == it.dataNode }
            }

            it.destroy()
        }
    }

    override val dataNode: DataNode = DataNode()
}

fun interface ElectricalPowerAccessor {
    fun get(): Double
}

/**
 * Integrates electrical power into energy.
 * */
class ElectricalPowerConverterBehavior(private val accessor: ElectricalPowerAccessor): CellBehavior {
    var energy: Double = 0.0
    var deltaEnergy: Double = 0.0

    override fun onAdded(container: CellBehaviorContainer) {}

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addPre(this::simulationTick)
    }

    private fun simulationTick(dt: Double, p: SubscriberPhase){
        deltaEnergy = accessor.get() * dt
        energy += deltaEnergy
    }
}

fun interface ThermalBodyAccessor {
    fun get(): ThermalBody
}

/**
 * Converts dissipated electrical energy to thermal energy.
 * */
class ElectricalHeatTransferBehavior(private val bodyAccessor: ThermalBodyAccessor) : CellBehavior {
    private lateinit var converterBehavior: ElectricalPowerConverterBehavior

    override fun onAdded(container: CellBehaviorContainer) {
        converterBehavior = container.get()
    }

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addPre(this::simulationTick)
    }

    private fun simulationTick(dt: Double, p: SubscriberPhase){
        bodyAccessor.get().energy += converterBehavior.deltaEnergy
        converterBehavior.energy -= converterBehavior.deltaEnergy
    }
}

fun CellBehaviorContainer.withElectricalPowerConverter(accessor: ElectricalPowerAccessor): CellBehaviorContainer =
    this.add(ElectricalPowerConverterBehavior(accessor))

fun CellBehaviorContainer.withElectricalHeatTransfer(getter: ThermalBodyAccessor): CellBehaviorContainer =
    this.add(ElectricalHeatTransferBehavior(getter))

fun interface TemperatureAccessor {
    fun get(): Double
}

fun interface ExplosionConsumer {
    fun explode()
}

data class TemperatureExplosionBehaviorOptions(
    /**
     * If the temperature is above this threshold, [increaseSpeed] will be used to increase the explosion score.
     * Otherwise, [decayRate] will be used to decrease it.
     * */
    val temperatureThreshold: Double,

    /**
     * The score increase speed.
     * This value is scaled by the difference between the temperature and the threshold.
     * */
    val increaseSpeed: Double,

    /**
     * The score decrease speed. This value is not controlled by temperature.
     * */
    val decayRate: Double,
)

/**
 * The [TemperatureExplosionBehavior] will destroy the game object if a temperature is held
 * above a threshold for a certain time period, as specified in [TemperatureExplosionBehaviorOptions]
 * A **score** is used to determine if the object should blow up. The score is increased when the temperature is above threshold
 * and decreased when the temperature is under threshold. Once a score of 1 is reached, the part explosion is enqueued
 * using the [EventScheduler]
 * */
class TemperatureExplosionBehavior(
    val temperatureAccessor: TemperatureAccessor,
    val options: TemperatureExplosionBehaviorOptions,
    val consumer: ExplosionConsumer
) : CellBehavior, WailaEntity {
    private var score = 0.0
    private var enqueued = false

    override fun onAdded(container: CellBehaviorContainer) {}

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addSubscriber(SubscriberOptions(10, SubscriberPhase.Post), this::simulationTick)
    }

    private fun simulationTick(dt: Double, phase: SubscriberPhase) {
        val temperature = temperatureAccessor.get()

        if(temperature > options.temperatureThreshold) {
            val difference = temperature - options.temperatureThreshold

            score += options.increaseSpeed * difference * dt
        }
        else {
            score -= options.decayRate * dt
        }

        if(score >= 1) {
            blowUp()
        }

        score = score.coerceIn(0.0, 1.0)
    }

    private fun blowUp() {
        if(!enqueued) {
            enqueued = true

            EventScheduler.scheduleWorkPre(1) {
                consumer.explode()
            }
        }
    }

    override fun appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.text("Explode", score.formattedPercentN())
    }
}

fun CellBehaviorContainer.withExplosionBehavior(
    temperatureAccessor: TemperatureAccessor,
    options: TemperatureExplosionBehaviorOptions,
    explosionNotifier: ExplosionConsumer
): CellBehaviorContainer {

    this.add(TemperatureExplosionBehavior(temperatureAccessor, options, explosionNotifier))

    return this
}

fun CellBehaviorContainer.withStandardExplosionBehavior(cell: Cell, threshold: Double, temperatureAccessor: TemperatureAccessor): CellBehaviorContainer {
    return withExplosionBehavior(temperatureAccessor, TemperatureExplosionBehaviorOptions(threshold, 0.1, 0.25)) {
        val container = cell.container ?: return@withExplosionBehavior

        if(container is MultipartBlockEntity) {
            if(container.isRemoved){
                return@withExplosionBehavior
            }

            val part = container.getPart(cell.posDescr.requireLocator<SO3, BlockFaceLocator>().faceWorld)
                ?: return@withExplosionBehavior

            val level = (part.placement.level as ServerLevel)

            level.destroyPart(part)
        }
        else {
            error("Cannot explode $container")
        }
    }
}

/**
 * Registers a set of standard cell behaviors:
 * - [ElectricalPowerConverterBehavior]
 *      - converts power into energy
 * - [ElectricalHeatTransferBehavior]
 *      - moves energy to the heat mass from the electrical converter
 * - [TemperatureExplosionBehavior]
 *      - explodes part when a threshold temperature is held for a certain time period
 * */
fun CellBehaviorContainer.withStandardBehavior(cell: Cell, power: ElectricalPowerAccessor, thermal: ThermalBodyAccessor): CellBehaviorContainer = this
    .withElectricalPowerConverter(power)
    .withElectricalHeatTransfer(thermal)
    .withStandardExplosionBehavior(cell, 600.0) { thermal.get().tempK }
