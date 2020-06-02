package org.eln2.parsers.scam

import org.eln2.data.DisjointSet
import org.eln2.parsers.scam.components.*
import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.component.Component

data class PinSet(val pos: String) : DisjointSet()

interface IComponentConstructor {
    companion object {
        val TYPE_CONSTRUCTORS: MutableMap<Char, IComponentConstructor> = mutableMapOf(
            'R' to ResistorConstructor(),
            'L' to InductorConstructor(),
            'C' to CapacitorConstructor(),
            'V' to VoltageSourceConstructor(),
            'I' to CurrentSourceConstructor()
        )

        fun buildFromString(sourceLine: String, circuit: Circuit) {
            val all = sourceLine.split("\n")
            if (all.isEmpty()) return
            val data = all[0].trim().split(" ").toTypedArray()
            val type = data[0][0]
            if (type in TYPE_CONSTRUCTORS) {
                return TYPE_CONSTRUCTORS[type]!!.construct(circuit, data)
            }
        }
    }

    fun construct(circuit: Circuit, data: Array<String>)
}

abstract class PoleConstructor : IComponentConstructor {
    abstract fun component(data: Array<String>): Component
    abstract fun configure(component: Component, data: Array<String>)
    override fun construct(circuit: Circuit, data: Array<String>) {
        val comp = component(data)
        circuit.add(comp)
        configure(comp, data)
        // TODO: Connect pins
        val n1 = data[1]
        val n2 = data[2]

    }
}

class Scam(val source: String) {

    val roots: MutableMap<String, PinSet> = mutableMapOf()

    fun getPin(pin: String) = roots.getOrPut(pin, { PinSet(pin) })

    val circuit = Circuit()

    init {

    }

    companion object {
        @JvmStatic
        fun main(vararg args: String) {

        }
    }
}
