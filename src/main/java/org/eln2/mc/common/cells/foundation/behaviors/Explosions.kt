package org.eln2.mc.common.cells.foundation.behaviors

import net.minecraft.server.level.ServerLevel
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.extensions.LevelExtensions.destroyPart

fun interface ITemperatureAccessor {
    fun get(): Double
}

fun interface IPartAccessor {
    fun get(): Part
}

data class TemperatureExplosionBehaviorOptions(
    val temperatureThreshold: Double,
    val increaseSpeed: Double,
    val decayRate: Double,
)

class TemperatureExplosionBehavior(
    val accessor: ITemperatureAccessor,
    val options: TemperatureExplosionBehaviorOptions,
    val partAccessor: IPartAccessor) : ICellBehavior {

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
}

fun CellBehaviorContainer.withExplosionBehavior(
    temperatureAccessor: ITemperatureAccessor,
    options: TemperatureExplosionBehaviorOptions,
    partAccessor: IPartAccessor): CellBehaviorContainer {

    this.add(TemperatureExplosionBehavior(temperatureAccessor, options, partAccessor))

    return this
}
