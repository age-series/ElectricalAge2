package org.eln2.mc.common.cells.foundation

import org.ageseries.libage.data.mutableMultiMapOf
import org.eln2.mc.extensions.removeAll
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Represents a function that is executed periodically from the simulation thread.
 * */
fun interface Subscriber {
    /**
     * Called when the simulation updates.
     * @param dt The fixed time step.
     * @param phase The update phase, as specified in [SubscriberOptions].
     * */
    fun update(dt: Double, phase: SubscriberPhase)
}

enum class SubscriberPhase {
    Pre,
    Post
}

/**
 * Describes the execution policy of a subscriber.
 * @param interval The interval, in ticks.
 * @param phase The update phase to listen for.
 * */
data class SubscriberOptions(val interval: Int, val phase: SubscriberPhase)

/**
 * The Subscriber Collection is used to manage sets of subscribers, with different execution policies.
 * [SubscriberOptions] will be used to choose a [SubscriberPool].
 * */
class SubscriberPool : SubscriberCollection {
    private val pools = HashMap<SubscriberOptions, SubscriberPool>()
    private val subscribers = mutableMultiMapOf<Subscriber, SubscriberPool>()

    private var iterating = false

    private val updates = ArrayDeque<Update>()

    val poolCount get() = pools.size
    val subscriberCount get() = subscribers.keyMappingSize

    private fun getPool(parameters: SubscriberOptions): SubscriberPool{
        return pools.computeIfAbsent(parameters) { SubscriberPool(parameters) }
    }

    fun hasPool(parameters: SubscriberOptions): Boolean {
        return pools.containsKey(parameters)
    }

    private fun applyUpdate(update: Update) {
        when (update) {
            is AddUpdate -> {
                val subscriber = update.subscriber
                val parameters = update.parameters
                val pool = getPool(parameters)

                pool.add(subscriber)

                subscribers[subscriber].add(pool)
            }

            is RemoveAllUpdate -> {
                val subscriber = update.subscriber

                subscribers[subscriber].forEach { pool ->
                    pool.remove(subscriber)

                    if(pool.isEmpty) {
                        pools.remove(pool.parameters)
                    }
                }

                subscribers.clear(subscriber)
            }

            else -> {
                error("Unknown update $update")
            }
        }
    }

    private fun enqueueOrApply(update: Update){
        if(iterating) {
            updates.add(update)
        }
        else {
            applyUpdate(update)
        }
    }

    override fun addSubscriber(parameters: SubscriberOptions, subscriber: Subscriber)  {
        enqueueOrApply(AddUpdate(subscriber, parameters))
    }

    override fun remove(subscriber: Subscriber){
        enqueueOrApply(RemoveAllUpdate(subscriber))
    }

    fun update(dt: Double, phase: SubscriberPhase) {
        iterating = true

        pools.values
            .filter { it.parameters.phase == phase }
            .forEach { it.update(dt) }

        iterating = false

        updates.removeAll { applyUpdate(it) }
    }

    private interface Update
    private class AddUpdate(val subscriber: Subscriber, val parameters: SubscriberOptions) : Update
    private class RemoveAllUpdate(val subscriber: Subscriber) : Update

    class SubscriberPool(val parameters: SubscriberOptions) {
        private val pool = ArrayList<Subscriber>()

        val isEmpty get() = pool.isEmpty()
        val size get() = pool.size

        private var countdown = parameters.interval

        fun update(dtId: Double): Boolean {
            val dt = dtId * max(parameters.interval, 1)

            if(--countdown <= 0){
                countdown = parameters.interval
                pool.forEach { it.update(dt, parameters.phase) }
                return true
            }

            return false
        }

        fun add(subscriber: Subscriber) {
            if(pool.contains(subscriber)){
                error("Duplicate add $subscriber in $parameters")
            }

            pool.add(subscriber)
        }

        fun remove(subscriber: Subscriber) {
            if(!pool.remove(subscriber)){
                error("Failed to remove $subscriber from $parameters")
            }
        }
    }
}

interface SubscriberCollection {
    fun addSubscriber(parameters: SubscriberOptions, subscriber: Subscriber)

    fun remove(subscriber: Subscriber)
}

/**
 * Adds a subscriber that runs on [SubscriberPhase.Pre] every tick (interval is 0).
 * */
fun SubscriberCollection.addPre(subscriber: Subscriber){
    this.addSubscriber(SubscriberOptions(0, SubscriberPhase.Pre), subscriber)
}

/**
 * Adds a subscriber that runs on [SubscriberPhase.Post] every tick (interval is 0).
 * */
fun SubscriberCollection.addPost(subscriber: Subscriber){
    this.addSubscriber(SubscriberOptions(0, SubscriberPhase.Post), subscriber)
}
