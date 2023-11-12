package org.eln2.mc.common.cells.foundation

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.ageseries.libage.data.KELVIN
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.Temp
import org.eln2.mc.*
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.events.Scheduler
import org.eln2.mc.common.events.schedulePre
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaNode
import org.eln2.mc.integration.WailaTooltipBuilder

/**
 * *A cell behavior* manages routines ([Subscriber]) that run on the simulation thread.
 * It is attached to a cell.
 * */
interface CellBehavior {
    /**
     * Called when the behavior is added to the container.
     * */
    fun onAdded(container: CellBehaviorContainer) {}

    /**
     * Called when the subscriber collection is being set up.
     * Subscribers can be added here.
     * */
    fun subscribe(subscribers: SubscriberCollection) {}

    /**
     * Called when the behavior is destroyed.
     * This can be caused by the cell being destroyed.
     * It can also be caused by the game object being detached, in the case of [ReplicatorBehavior]s.
     * */
    fun destroy() {}
}

// to remove
/**
 * Temporary staging storage for behaviors.
 * */
class CellBehaviorSource : CellBehavior {
    private val behaviors = ArrayList<CellBehavior>()

    fun add(behavior: CellBehavior): CellBehaviorSource {
        if (behavior is CellBehaviorSource) {
            behaviors.addAll(behavior.behaviors)
        } else {
            behaviors.add(behavior)
        }

        return this
    }

    override fun onAdded(container: CellBehaviorContainer) {
        behaviors.forEach { behavior ->
            if (container.behaviors.any { it.javaClass == behavior.javaClass }) {
                error("Duplicate behavior")
            }

            container.behaviors.add(behavior)
            behavior.onAdded(container)
        }

        require(container.behaviors.remove(this)) { "Failed to clean up behavior source" }
    }
}

operator fun CellBehavior.times(b: CellBehavior) = CellBehaviorSource().also { it.add(this).add(b) }

operator fun CellBehaviorSource.times(b: CellBehavior) = this.add(b)

/**
 * Container for multiple [CellBehavior]s. It is a Set. As such, there may be one instance of each behavior type.
 * */
class CellBehaviorContainer(private val cell: Cell) {
    val behaviors = ArrayList<CellBehavior>()

    fun addToCollection(b: CellBehavior) {
        if (behaviors.any { it.javaClass == b.javaClass }) {
            error("Duplicate behavior $b")
        }

        behaviors.add(b)
        b.onAdded(this)
    }

    fun forEach(action: ((CellBehavior) -> Unit)) = behaviors.forEach(action)
    inline fun <reified T : CellBehavior> getOrNull(): T? = behaviors.first { it is T } as? T
    inline fun <reified T : CellBehavior> get(): T = getOrNull() ?: error("Failed to get behavior")

    fun destroy(behavior: CellBehavior) {
        require(behaviors.remove(behavior)) { "Illegal behavior remove $behavior" }
        behavior.destroy()
    }

    fun destroy() {
        behaviors.toList().forEach { destroy(it) }
    }
}

fun interface ElectricalPowerAccessor {
    fun get(): Double
}

fun interface ThermalBodyAccessor {
    fun get(): ThermalBody
}

fun ThermalBodyAccessor.temperature(): TemperatureAccessor = TemperatureAccessor {
    this.get().temperatureKelvin
}

/**
 * Converts dissipated electrical energy to thermal energy.
 * */
class PowerHeatingBehavior @Inj constructor(private val accessor: ElectricalPowerAccessor, private val body: ThermalBody) : CellBehavior {
    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addPre(this::simulationTick)
    }

    private fun simulationTick(dt: Double, p: SubscriberPhase) {
        body.energy += accessor.get() * dt
    }
}

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
    val temperatureThreshold: Quantity<Temp> = Quantity(350.0, KELVIN),

    /**
     * The score increase speed.
     * This value is scaled by the difference between the temperature and the threshold.
     * */
    val increaseSpeed: Double = 0.1,

    /**
     * The score decrease speed. This value is not controlled by temperature.
     * */
    val decayRate: Double = 0.25,
)

/**
 * The [TemperatureExplosionBehavior] will destroy the game object if a temperature is held
 * above a threshold for a certain time period, as specified in [TemperatureExplosionBehaviorOptions]
 * A **score** is used to determine if the object should blow up. The score is increased when the temperature is above threshold
 * and decreased when the temperature is under threshold. Once a score of 1 is reached, the explosion is enqueued
 * using the [Scheduler]
 * The explosion uses an [ExplosionConsumer] to access the game object. [ExplosionConsumer.explode] is called from the game thread.
 * If no consumer is specified, a default one is used. Currently, only [CellPart] is implemented.
 * Injection is supported using [TemperatureAccessor], [TemperatureField]
 * */
class TemperatureExplosionBehavior(
    val temperatureAccessor: TemperatureAccessor,
    val options: TemperatureExplosionBehaviorOptions,
    val consumer: ExplosionConsumer,
) : CellBehavior, WailaNode {
    private var score = 0.0
    private var enqueued = false

    @Inj
    constructor(temperatureAccessor: TemperatureAccessor, options: TemperatureExplosionBehaviorOptions, cell: Cell) :
        this(temperatureAccessor, options, { defaultNotifier(cell) })

    @Inj
    constructor(temperatureAccessor: TemperatureAccessor, consumer: ExplosionConsumer) :
        this(temperatureAccessor, TemperatureExplosionBehaviorOptions(), consumer)

    override fun onAdded(container: CellBehaviorContainer) {}

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addSubscriber(SubscriberOptions(10, SubscriberPhase.Post), this::simulationTick)
    }

    private fun simulationTick(dt: Double, phase: SubscriberPhase) {
        val temperature = temperatureAccessor.get()

        if (temperature > !options.temperatureThreshold) {
            val difference = temperature - !options.temperatureThreshold
            score += options.increaseSpeed * difference * dt
        } else {
            score -= options.decayRate * dt
        }

        if (score >= 1.0) {
            blowUp()
        }

        score = score.coerceIn(0.0, 1.0)
    }

    private fun blowUp() {
        if (!enqueued) {
            enqueued = true

            schedulePre(0) {
                consumer.explode()
            }
        }
    }

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.text("Explode", score.formattedPercentN())
    }

    companion object {
        fun defaultNotifier(cell: Cell) {
            val container = cell.container ?: return

            if (container is MultipartBlockEntity) {
                if (container.isRemoved) {
                    return
                }

                val part = container.getPart(cell.locator.requireLocator<FaceLocator>())
                    ?: return

                val level = (part.placement.level as ServerLevel)

                level.destroyPart(part, true)

                level.playSound(
                    null,
                    part.placement.position.x + 0.5,
                    part.placement.position.y + 0.5,
                    part.placement.position.z + 0.5,
                    SoundEvents.GENERIC_EXPLODE,
                    SoundSource.BLOCKS,
                    randomFloat(0.9f, 1.1f),
                    randomFloat(0.9f, 1.1f)
                )
            } else {
                error("Cannot explode $container")
            }
        }
    }
}
