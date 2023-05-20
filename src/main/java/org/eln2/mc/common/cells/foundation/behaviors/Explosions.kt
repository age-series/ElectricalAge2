package org.eln2.mc.common.cells.foundation.behaviors

import mcp.mobius.waila.api.IPluginConfig
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.extensions.formattedPercentN
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder

fun interface ITemperatureAccessor {
    fun get(): Double
}

fun interface IExplosionNotifier {
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
    val temperatureAccessor: ITemperatureAccessor,
    val options: TemperatureExplosionBehaviorOptions,
    val notifier: IExplosionNotifier) :
    ICellBehavior,
    IWailaProvider {

    private var score = 0.0
    private var enqueued = false

    override fun onAdded(container: CellBehaviorContainer) {}

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addSubscriber(SubscriberOptions(10, SubscriberPhase.Post), this::simulationTick)
    }

    override fun destroy(subscribers: SubscriberCollection) {
        subscribers.removeSubscriber(this::simulationTick)
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
                notifier.explode()
            }
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        builder.text("Explode", score.formattedPercentN())
    }
}

fun CellBehaviorContainer.withExplosionBehavior(
    temperatureAccessor: ITemperatureAccessor,
    options: TemperatureExplosionBehaviorOptions,
    explosionNotifier: IExplosionNotifier): CellBehaviorContainer {

    this.add(TemperatureExplosionBehavior(temperatureAccessor, options, explosionNotifier))

    return this
}
