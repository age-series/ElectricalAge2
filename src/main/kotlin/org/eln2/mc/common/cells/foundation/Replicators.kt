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

fun interface InternalTemperatureConsumer {
    fun onInternalTemperatureChanges(dirty: List<ThermalBody>)
}

fun interface ExternalTemperatureConsumer {
    fun onExternalTemperatureChanges(removed: HashSet<ThermalObject<*>>, dirty: HashMap<ThermalObject<*>, Double>)
}

/**
 * Generalized behavior for sending temperature changes to clients (for e.g. rendering hot bodies)
 * @param bodies The list of bodies to track.
 * @param consumer The consumer for the changes.
 * */
class InternalTemperatureReplicatorBehavior(
    val bodies: List<ThermalBody>,
    val consumer: InternalTemperatureConsumer
) : ReplicatorBehavior {
    var scanInterval: Int = 5
    var scanPhase: SubscriberPhase = SubscriberPhase.Pre
    var toleranceK: Double = 1.0

    private val tracked = bodies.associateWith { it.temperatureKelvin }.toMutableMap()

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addSubscriber(SubscriberOptions(scanInterval, scanPhase), this::scan)
    }

    private fun scan(dt: Double, phase: SubscriberPhase) {
        val dirty = bodies.filter { !tracked[it]!!.approxEq(it.temperatureKelvin, toleranceK) }

        if (dirty.isEmpty()) {
            return
        }

        dirty.forEach { tracked[it] = it.temperatureKelvin }

        consumer.onInternalTemperatureChanges(dirty)
    }
}

/**
 * Generalized behavior for sending temperature changes of connected thermal objects to clients.
 * The temperatures are read from the [TemperatureField] of neighbor objects.
 * @param cell The cell that owns this behavior.
 * @param consumer The consumer for the changes.
 * */
class ExternalTemperatureReplicatorBehavior(
    val cell: Cell,
    val consumer: ExternalTemperatureConsumer
) : ReplicatorBehavior {
    var scanInterval: Int = 5
    var scanPhase: SubscriberPhase = SubscriberPhase.Pre
    var tolerance: Double = 1.0

    private val tracked = HashMap<ThermalObject<*>, Double>()
    private val unmarked = HashSet<ThermalObject<*>>()

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addSubscriber(SubscriberOptions(scanInterval, scanPhase), this::scan)
    }

    private fun scan(dt: Double, phase: SubscriberPhase) {
        unmarked.addAll(tracked.keys)

        val dirty = HashMap<ThermalObject<*>, Double>()

        scanNeighbors(cell) { remoteThermalObject, actualTemperature ->
            unmarked.remove(remoteThermalObject)

            val previousTemperature = tracked[remoteThermalObject]

            if(previousTemperature == null || !previousTemperature.approxEq(actualTemperature, tolerance)) {
                tracked[remoteThermalObject] = actualTemperature
                dirty[remoteThermalObject] = actualTemperature
            }
        }

        if(unmarked.size > 0 || dirty.size > 0) {
            consumer.onExternalTemperatureChanges(HashSet(unmarked), dirty)

            unmarked.forEach {
                tracked.remove(it)
            }
        }

        unmarked.clear()
    }

    companion object {
        inline fun scanNeighbors(cell: Cell, crossinline consumer: (ThermalObject<*>, Double) -> Unit) {
            for(remoteCell in cell.connections) {
                val remoteThermalObject = remoteCell.objects.getObjectOrNull(SimulationObjectType.Thermal) as? ThermalObject
                    ?: continue

                val contactInfo: ThermalContactInfo

                if(remoteThermalObject is ThermalContactInfo) {
                    contactInfo = remoteThermalObject
                }
                else if(remoteThermalObject.cell is ThermalContactInfo) {
                    contactInfo = remoteThermalObject.cell
                }
                else {
                    continue
                }

                val temperature = contactInfo.getContactTemperature(cell.locator)

                if(temperature != null) {
                    consumer(remoteThermalObject, temperature)
                }
            }
        }
    }
}
