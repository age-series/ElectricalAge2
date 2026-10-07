@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.eln2.mc.common.content

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.approxEq
import org.eln2.mc.requireIsOnServerThread
import java.util.UUID

/**
 * Consumers and producers register/unregister dynamically (e.g. a drill registers while held and active).
 * The manager is the single authority. Consumers ask the manager for power, never producers directly.
 * Distribution is demand-driven spillover: for each consumer (in priority order), the manager walks the player's producers in inventory order, drawing from each until demand is met or producers are exhausted.
 * This gives sequential draining for low-draw consumers (longevity) and parallel contribution for high-draw consumers, without arbitrary per-device battery caps.
 * */
object PlayerPowerManager {
    private const val TICKS_PER_SECOND = 20.0
    private const val DT = 1.0 / TICKS_PER_SECOND

    private class PlayerPowerState {
        val consumers = LinkedHashMap<Any, InventoryPowerConsumer>()
        val producers = LinkedHashMap<ItemStack, InventoryPowerProducer>()
    }

    private val states = HashMap<UUID, PlayerPowerState>()

    fun registerConsumer(player: ServerPlayer, consumer: InventoryPowerConsumer) {
        val state = getState(player)
        state.consumers[consumer] = consumer
    }

    fun unregisterConsumer(player: ServerPlayer, consumer: InventoryPowerConsumer) {
        requireIsOnServerThread()
        val state = states[player.uuid] ?: return
        state.consumers.remove(consumer)
    }

    /**
     * Called when the player's inventory changes or a producer item is added/removed, so the producer set is rebuilt on the next tick.
     * */
    fun invalidateProducers(player: ServerPlayer) {
        requireIsOnServerThread()
        states[player.uuid]?.producers?.clear()
    }

    /**
     * The per-tick distribution pass. Called from [org.eln2.mc.common.ForgeEvents] on PlayerTickEvent END.
     *
     * 1. Rebuilds the producer set from the player's inventory if invalidated.
     * 2. Collects consumer demands, sorts by priority descending.
     * 3. For each consumer, walks producers in inventory order, drawing until demand is met.
     * 4. Delivers granted energy to the consumer.
     * */
    fun tick(player: ServerPlayer) {
        requireIsOnServerThread()
        val state = states[player.uuid] ?: return

        if (state.consumers.isEmpty()) {
            return
        }

        for (consumer in state.consumers.values) {
            consumer.preTick()
        }

        state.producers.clear()
        scanInventoryForProducers(player.inventory, state)

        if (state.producers.isEmpty()) {
            for (consumer in state.consumers.values) {
                consumer.postTick(DT)
            }
            return
        }

        PowerDistributor.distribute(
            consumers = state.consumers.values.toList(),
            producers = state.producers.values.toList(),
            dt = DT,
        )

        for (consumer in state.consumers.values) {
            consumer.postTick(DT)
        }
    }

    private fun rebuildProducersIfEmpty(player: ServerPlayer, state: PlayerPowerState) {
        if (state.producers.isNotEmpty()) {
            return
        }

        val inventory = player.inventory

        scanInventoryForProducers(inventory, state)
    }

    private fun scanInventoryForProducers(inventory: Inventory, state: PlayerPowerState) {
        scanItemsForProducers(inventory.items, state)
        scanItemsForProducers(inventory.armor, state)
        scanItemsForProducers(inventory.offhand, state)
    }

    private fun scanItemsForProducers(items: List<ItemStack>, state: PlayerPowerState) {
        for (stack in items) {
            if (stack.isEmpty) {
                continue
            }

            val item = stack.item
            if (item is PowerCellItem) {
                val producer = makeProducer(item, stack)
                if (producer != null) {
                    state.producers[stack] = producer
                }
            }
        }
    }

    /**
     * Creates an [InventoryPowerProducer] view over a [PowerCellItem] stack.
     * Delegates to the stack's [IEln2EnergyStorage] capability (which enforces maxOutput and efficiency).
     * */
    private fun makeProducer(item: PowerCellItem, stack: ItemStack): InventoryPowerProducer? {
        val storage = item.getStorage(stack)
            ?: return null

        return object : InventoryPowerProducer {
            override val maxOutput: Quantity<Power>
                get() = storage.maxPowerOutput

            override val availableEnergy: Quantity<Energy>
                get() = storage.energyJoules

            override fun draw(request: Quantity<Energy>): Quantity<Energy> {
                return storage.extractEnergyJoules(request, simulate = false)
            }
        }
    }

    private fun getState(player: ServerPlayer): PlayerPowerState {
        return states.computeIfAbsent(player.uuid) { PlayerPowerState() }
    }

    fun clear(player: ServerPlayer) {
        requireIsOnServerThread()
        states.remove(player.uuid)
    }

    /**
     * Returns the total available energy across all of a player's producers.
     * Useful for HUD/tooltip display. Does not mutate state.
     * */
    fun totalAvailableEnergy(player: ServerPlayer): Quantity<Energy> {
        requireIsOnServerThread()
        val state = states[player.uuid] ?: return Quantity(0.0, JOULE)
        if (state.producers.isEmpty()) {
            rebuildProducersIfEmpty(player, state)
        }
        var total = 0.0
        for ((_, producer) in state.producers) {
            total += !producer.availableEnergy
        }
        return Quantity(total, JOULE)
    }

    /**
     * Returns the total max output across all of a player's producers.
     * Represents the maximum power the player can deliver this tick.
     * */
    fun totalMaxOutput(player: ServerPlayer): Quantity<Power> {
        requireIsOnServerThread()
        val state = states[player.uuid] ?: return Quantity(0.0, WATT)

        if (state.producers.isEmpty()) {
            rebuildProducersIfEmpty(player, state)
        }

        var total = 0.0

        for ((_, producer) in state.producers) {
            total += !producer.maxOutput
        }

        return Quantity(total, WATT)
    }
}

/**
 * Distribution algorithm, extracted for testing without gametest.
 * */
object PowerDistributor {
    /**
     * Distributes energy from [producers] to [consumers] over a tick of [dt] seconds.
     *
     * Consumers are sorted by priority descending. For each consumer, the requested energy is
     * `powerDemand * dt`. Producers are drawn from in list order (spillover): the first producer
     * is drained first; if it cannot meet demand, the next is tried, and so on.
     *
     * Granted energy is delivered via [InventoryPowerConsumer.receivePower].
     * */
    fun distribute(consumers: List<InventoryPowerConsumer>, producers: List<InventoryPowerProducer>, dt: Double, ) {
        if (consumers.isEmpty() || producers.isEmpty()) {
            return
        }

        val demands = consumers
            .map { it to it.powerDemand() }
            .filter { !it.second.value.approxEq(0.0) }
            .sortedByDescending { it.first.priority.ordinal }

        if (demands.isEmpty()) {
            return
        }

        for ((consumer, demand) in demands) {
            val requestedEnergy = Quantity(!demand * dt, JOULE)
            var remaining = !requestedEnergy

            for (producer in producers) {
                if (remaining.approxEq(0.0)) {
                    break
                }

                val drawn = producer.draw(Quantity(remaining, JOULE))
                remaining -= !drawn
            }

            val granted = !requestedEnergy - remaining

            if (!granted.approxEq(0.0)) {
                consumer.receivePower(Quantity(granted, JOULE))
            }
        }
    }
}
