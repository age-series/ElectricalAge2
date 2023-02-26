package org.eln2.mc.common.cells.foundation

import org.ageseries.libage.data.mutableMultiMapOf
import org.eln2.mc.extensions.CollectionExtensions.removeAll

fun interface ISubscriber{
    fun update(dt: Double, phase: SubscriberPhase)
}

enum class SubscriberPhase {
    Pre,
    Post
}

/**
 * This describes the execution policy of a subscriber.
 * @param interval The interval, in ticks, when the subscriber shall be updated.
 * @param phase The update phase to listen for.
 * */
data class SubscriberOptions(val interval: Int, val phase: SubscriberPhase)

/**
 * The Subscriber Collection is used to manage sets of subscribers, with different execution policies.
 * [SubscriberOptions] will be used to choose a [SubscriberPool].
 * */
class SubscriberCollection {
    private val pools = HashMap<SubscriberOptions, SubscriberPool>()
    private val subscribers = mutableMultiMapOf<ISubscriber, SubscriberPool>()

    private var iterating = false

    private val updates = ArrayDeque<IUpdate>()

    val poolCount get() = pools.size
    val subscriberCount get() = subscribers.keyMappingSize

    private fun getPool(parameters: SubscriberOptions): SubscriberPool{
        return pools.computeIfAbsent(parameters) { SubscriberPool(parameters) }
    }

    fun hasPool(parameters: SubscriberOptions): Boolean {
        return pools.containsKey(parameters)
    }

    private fun applyUpdate(update: IUpdate) {
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

    private fun enqueueOrApply(update: IUpdate){
        if(iterating) {
            updates.add(update)
        }
        else {
            applyUpdate(update)
        }
    }

    fun addSubscriber(parameters: SubscriberOptions, subscriber: ISubscriber)  {
        enqueueOrApply(AddUpdate(subscriber, parameters))
    }

    fun removeSubscriber(subscriber: ISubscriber){
        enqueueOrApply(RemoveAllUpdate(subscriber))
    }

    fun update(dt: Double, phase: SubscriberPhase) {
        iterating = true

        pools.values.filter { it.parameters.phase == phase }.forEach { pool ->
            pool.update(dt)
        }

        iterating = false

        updates.removeAll { applyUpdate(it) }
    }

    private interface IUpdate
    private class AddUpdate(val subscriber: ISubscriber, val parameters: SubscriberOptions) : IUpdate
    private class RemoveAllUpdate(val subscriber: ISubscriber) : IUpdate

    class SubscriberPool(val parameters: SubscriberOptions) {
        private val pool = ArrayList<ISubscriber>()

        val isEmpty get() = pool.isEmpty()
        val size get() = pool.size

        private var countdown = parameters.interval

        fun update(dt: Double): Boolean {
            if(--countdown <= 0){
                countdown = parameters.interval
                pool.forEach { it.update(dt, parameters.phase) }
                return true
            }

            return false
        }

        fun add(subscriber: ISubscriber) {
            if(pool.contains(subscriber)){
                error("Duplicate add $subscriber in $parameters")
            }

            pool.add(subscriber)
        }

        fun remove(subscriber: ISubscriber) {
            if(!pool.remove(subscriber)){
                error("Failed to remove $subscriber from $parameters")
            }
        }
    }
}
