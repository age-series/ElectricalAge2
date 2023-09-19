package org.eln2.mc.common.cells.foundation

import org.eln2.mc.ThermalBody
import org.eln2.mc.mathematics.approxEq
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.*

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Replicator

/**
 * Represents a specialized [CellBehavior], that only exists when the game object also exists.
 * */
interface ReplicatorBehavior : CellBehavior

object Replicators {
    fun replicatorScan(cellK: KClass<*>, containerK: KClass<*>, cellInst: Any, containerInst: Any) = getReplicators(cellK, containerK).mapNotNull { it.create(cellInst, containerInst) }

    private fun interface ReplicatorFactory {
        fun create(cellInst: Any, containerInst: Any): ReplicatorBehavior?
    }

    private val replicators = ConcurrentHashMap<Pair<KClass<*>, KClass<*>>, List<ReplicatorFactory>>()

    private fun getReplicators(cellK: KClass<*>, containerK: KClass<*>): List<ReplicatorFactory> =
        replicators.getOrPut(Pair(cellK, containerK)) {
            val functions = cellK.memberFunctions.filter { it.hasAnnotation<Replicator>() }

            val results = ArrayList<ReplicatorFactory>()

            functions.forEach {
                if (!(it.returnType.classifier as KClass<*>).isSubclassOf(ReplicatorBehavior::class)) {
                    error("Invalid return type of $it")
                }

                if (it.parameters.size != 2) {
                    error("Invalid parameter count of $it")
                }
            }

            functions.forEach {
                val containerParam = it.parameters[1]

                if ((containerParam.type.classifier as KClass<*>).isSuperclassOf(containerK)) {
                    results.add { self, rxContainerTarget ->
                        it.call(self, rxContainerTarget) as? ReplicatorBehavior
                    }
                }
            }

            results
        }
}

fun interface ThermalReplicator {
    fun streamTemperatureChanges(bodies: List<ThermalBody>, dirty: List<ThermalBody>)
}

// todo maybe find an event based system instead of polling

/**
 * Generalized behavior for sending temperature changes to clients (for e.g. rendering hot bodies)
 * @param bodies The list of bodies to track.
 * @param replicator The consumer for the changes.
 * */
class ThermalReplicatorBehavior(val bodies: List<ThermalBody>, val replicator: ThermalReplicator) : ReplicatorBehavior {
    /**
     * Gets or sets the interval (in simulation ticks) for polling.
     * */
    var scanInterval: Int = 10
    var scanPhase: SubscriberPhase = SubscriberPhase.Pre
    var toleranceK: Double = 1.0

    private val tracked = bodies.associateWith { it.tempK }.toMutableMap()

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addSubscriber(SubscriberOptions(scanInterval, scanPhase), this::scan)
    }

    private fun scan(dt: Double, phase: SubscriberPhase) {
        val dirty = bodies.filter { !tracked[it]!!.approxEq(it.tempK, toleranceK) }

        if (dirty.isEmpty()) {
            return
        }

        dirty.forEach { tracked[it] = it.tempK }

        replicator.streamTemperatureChanges(
            bodies,
            dirty
        )
    }
}
