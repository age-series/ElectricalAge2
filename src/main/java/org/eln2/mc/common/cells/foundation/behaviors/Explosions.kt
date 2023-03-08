package org.eln2.mc.common.cells.foundation.behaviors

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.server.level.ServerLevel
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.extensions.LevelExtensions.destroyPart
import org.eln2.mc.extensions.NumberExtensions.formattedPercentN
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder

fun interface ITemperatureAccessor {
    fun get(): Double
}

// todo: generic IBlowable or something along these lines
fun interface IPartAccessor {
    fun get(): Part
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
    val accessor: ITemperatureAccessor,
    val options: TemperatureExplosionBehaviorOptions,
    val partAccessor: IPartAccessor) :
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
        val temperature = accessor.get()

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
                val part = partAccessor.get()
                val level = (part.placementContext.level as ServerLevel)

                level.destroyPart(part)
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
    partAccessor: IPartAccessor): CellBehaviorContainer {

    this.add(TemperatureExplosionBehavior(temperatureAccessor, options, partAccessor))

    return this
}
