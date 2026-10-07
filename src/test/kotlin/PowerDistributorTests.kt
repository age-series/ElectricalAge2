package org.eln2.mc.common.content

import org.ageseries.libage.data.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.min

/**
 * Tests for the demand-driven spillover distribution algorithm in [PowerDistributor].
 * */
class PowerDistributorTests {
    private val dt = 1.0 / 20.0

    private class FakeProducer(
        var stored: Double,
        val maxOutputWatts: Double,
        val efficiency: Double = 1.0,
    ) : InventoryPowerProducer {
        override val maxOutput: Quantity<Power>
            get() = Quantity(maxOutputWatts, WATT)

        override val availableEnergy: Quantity<Energy>
            get() = Quantity(stored, JOULE)

        var totalDrawn: Double = 0.0
            private set

        override fun draw(request: Quantity<Energy>): Quantity<Energy> {
            val perTickMax = maxOutputWatts / 20.0
            val cap = min(min(!request, perTickMax), stored)
            stored -= cap / efficiency
            totalDrawn += cap
            return Quantity(cap, JOULE)
        }
    }

    private class FakeConsumer(
        override val priority: InventoryPowerPriority,
        val demand: Double,
    ) : InventoryPowerConsumer {
        var received: Double = 0.0
            private set

        override fun powerDemand(): Quantity<Power> = Quantity(demand, WATT)

        override fun receivePower(granted: Quantity<Energy>): Quantity<Energy> {
            received += !granted
            return granted
        }
    }

    private fun approx(a: Double, b: Double, eps: Double = 1e-6): Boolean {
        return (a - b).let { it < 0 && -it < eps || it >= 0 && it < eps }
    }

    @Test
    fun `single producer fully meets single consumer demand`() {
        val producer = FakeProducer(stored = 1000.0, maxOutputWatts = 1000.0)
        val consumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 50.0)

        PowerDistributor.distribute(listOf(consumer), listOf(producer), dt)

        val expected = 50.0 / 20.0
        assertTrue(approx(consumer.received, expected), "Expected $expected, got ${consumer.received}")
        assertTrue(approx(producer.totalDrawn, expected), "Producer drew ${producer.totalDrawn}, expected $expected")
    }

    @Test
    fun `empty producers delivers nothing`() {
        val consumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 50.0)

        PowerDistributor.distribute(listOf(consumer), emptyList(), dt)

        assertTrue(approx(consumer.received, 0.0))
    }

    @Test
    fun `empty consumers draws nothing`() {
        val producer = FakeProducer(stored = 1000.0, maxOutputWatts = 1000.0)

        PowerDistributor.distribute(emptyList(), listOf(producer), dt)

        assertTrue(approx(producer.totalDrawn, 0.0))
    }

    @Test
    fun `zero demand consumer receives nothing`() {
        val producer = FakeProducer(stored = 1000.0, maxOutputWatts = 1000.0)
        val consumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 0.0)

        PowerDistributor.distribute(listOf(consumer), listOf(producer), dt)

        assertTrue(approx(consumer.received, 0.0))
        assertTrue(approx(producer.totalDrawn, 0.0))
    }

    @Test
    fun `spillover drains second producer when first is exhausted`() {
        val producer1 = FakeProducer(stored = 1.0, maxOutputWatts = 1000.0)
        val producer2 = FakeProducer(stored = 1000.0, maxOutputWatts = 1000.0)
        val consumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 100.0)

        PowerDistributor.distribute(listOf(consumer), listOf(producer1, producer2), dt)

        val expected = 100.0 / 20.0
        assertTrue(approx(consumer.received, expected), "Consumer got ${consumer.received}, expected $expected")
        assertTrue(approx(producer1.totalDrawn, 1.0), "Producer1 drew ${producer1.totalDrawn}, expected 1.0")
        assertTrue(approx(producer2.totalDrawn, expected - 1.0), "Producer2 drew ${producer2.totalDrawn}")
    }

    @Test
    fun `maxOutput cap limits per-producer draw`() {
        val producer = FakeProducer(stored = 100000.0, maxOutputWatts = 10.0)
        val consumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 1000.0)

        PowerDistributor.distribute(listOf(consumer), listOf(producer), dt)

        val expectedMaxDraw = 10.0 / 20.0
        assertTrue(approx(consumer.received, expectedMaxDraw), "Consumer got ${consumer.received}, expected $expectedMaxDraw")
    }

    @Test
    fun `multiple producers contribute in parallel when demand exceeds single maxOutput`() {
        val producer1 = FakeProducer(stored = 100000.0, maxOutputWatts = 50.0)
        val producer2 = FakeProducer(stored = 100000.0, maxOutputWatts = 50.0)
        val consumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 100.0)

        PowerDistributor.distribute(listOf(consumer), listOf(producer1, producer2), dt)

        val expected = 100.0 / 20.0
        assertTrue(approx(consumer.received, expected), "Consumer got ${consumer.received}, expected $expected")
        assertTrue(approx(producer1.totalDrawn, expected / 2.0), "Producer1 drew ${producer1.totalDrawn}")
        assertTrue(approx(producer2.totalDrawn, expected / 2.0), "Producer2 drew ${producer2.totalDrawn}")
    }

    @Test
    fun `priority order - critical consumer served before normal`() {
        val producer = FakeProducer(stored = 1.0, maxOutputWatts = 1000.0)
        val normalConsumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 100.0)
        val criticalConsumer = FakeConsumer(InventoryPowerPriority.Critical, demand = 100.0)

        PowerDistributor.distribute(listOf(normalConsumer, criticalConsumer), listOf(producer), dt)

        val criticalExpected = 100.0 / 20.0
        assertTrue(criticalExpected > 1.0, "Test setup: critical demand should exceed producer storage")
        assertTrue(approx(criticalConsumer.received, 1.0), "Critical got ${criticalConsumer.received}, expected 1.0 (all available)")
        assertTrue(approx(normalConsumer.received, 0.0), "Normal got ${normalConsumer.received}, expected 0.0")
    }

    @Test
    fun `low-demand consumer drains only first producer (sequential longevity)`() {
        val producer1 = FakeProducer(stored = 10000.0, maxOutputWatts = 1000.0)
        val producer2 = FakeProducer(stored = 10000.0, maxOutputWatts = 1000.0)
        val consumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 2.0)

        PowerDistributor.distribute(listOf(consumer), listOf(producer1, producer2), dt)

        val expected = 2.0 / 20.0
        assertTrue(approx(consumer.received, expected), "Consumer got ${consumer.received}, expected $expected")
        assertTrue(approx(producer2.totalDrawn, 0.0), "Producer2 should not be touched, drew ${producer2.totalDrawn}")
    }

    @Test
    fun `efficiency cost exceeds delivered energy`() {
        val producer = FakeProducer(stored = 10000.0, maxOutputWatts = 1000.0, efficiency = 0.5)
        val consumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 100.0)

        PowerDistributor.distribute(listOf(consumer), listOf(producer), dt)

        val deliveredExpected = 100.0 / 20.0
        assertTrue(approx(consumer.received, deliveredExpected), "Consumer got ${consumer.received}")
        val costExpected = deliveredExpected / 0.5
        assertTrue(approx(producer.stored, 10000.0 - costExpected), "Producer stored ${producer.stored}, expected ${10000.0 - costExpected}")
    }

    @Test
    fun `depleted producer contributes nothing`() {
        val producer1 = FakeProducer(stored = 0.0, maxOutputWatts = 1000.0)
        val producer2 = FakeProducer(stored = 1000.0, maxOutputWatts = 1000.0)
        val consumer = FakeConsumer(InventoryPowerPriority.Normal, demand = 50.0)

        PowerDistributor.distribute(listOf(consumer), listOf(producer1, producer2), dt)

        val expected = 50.0 / 20.0
        assertTrue(approx(consumer.received, expected), "Consumer got ${consumer.received}, expected $expected")
        assertTrue(approx(producer1.totalDrawn, 0.0))
        assertTrue(approx(producer2.totalDrawn, expected))
    }

    @Test
    fun `multiple consumers all priority-served`() {
        val producer = FakeProducer(stored = 100000.0, maxOutputWatts = 10000.0)
        val low = FakeConsumer(InventoryPowerPriority.Low, demand = 50.0)
        val critical = FakeConsumer(InventoryPowerPriority.Critical, demand = 50.0)
        val normal = FakeConsumer(InventoryPowerPriority.Normal, demand = 50.0)

        PowerDistributor.distribute(listOf(low, critical, normal), listOf(producer), dt)

        val expectedEach = 50.0 / 20.0
        assertTrue(approx(critical.received, expectedEach), "Critical got ${critical.received}")
        assertTrue(approx(normal.received, expectedEach), "Normal got ${normal.received}")
        assertTrue(approx(low.received, expectedEach), "Low got ${low.received}")
    }

    @Test
    fun `insufficient total power serves higher priority first`() {
        val producer = FakeProducer(stored = 1.0, maxOutputWatts = 100000.0)
        val critical = FakeConsumer(InventoryPowerPriority.Critical, demand = 100.0)
        val normal = FakeConsumer(InventoryPowerPriority.Normal, demand = 100.0)
        val low = FakeConsumer(InventoryPowerPriority.Low, demand = 100.0)

        PowerDistributor.distribute(listOf(critical, normal, low), listOf(producer), dt)

        assertTrue(approx(critical.received, 1.0), "Critical got ${critical.received}, expected 1.0")
        assertTrue(approx(normal.received, 0.0), "Normal got ${normal.received}, expected 0.0")
        assertTrue(approx(low.received, 0.0), "Low got ${low.received}, expected 0.0")
    }
}
