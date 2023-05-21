package org.eln2.mc.common.cells.foundation.objects

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.common.cells.foundation.Conventions
import org.eln2.mc.common.space.*
import kotlin.math.abs

/**
 * Utility class that holds a collection of resistors to be used as contact points for external components.
 * */
class ResistorBundle(var resistance: Double, val obj: ElectricalObject) {
    init {
        obj.cell.pos.descriptor.requireLocator<R3, BlockPosLocator>()
        obj.cell.pos.descriptor.requireLocator<SO3, IdentityDirectionLocator>()
        obj.cell.pos.descriptor.requireLocator<SO3, BlockFaceLocator>()
    }

    private val resistors = HashMap<RelativeRotationDirection, Resistor>()

    private var prepared = false

    /**
     * This must be called once the circuit is made available, in order to register the resistors.
     * This "prepares" the bundle, so future calls to *getOfferedResistor* that result in a new resistor being created will cause an error.
     * @see ElectricalObject.addComponents
     * */
    fun register(connections: List<ElectricalObject>, circuit: Circuit) {
        if (prepared) {
            error("Already prepared")
        }

        connections.forEach {
            val directionActual = obj.cell.pos.descriptor.findDirectionActual(it.cell.pos.descriptor)
                ?: error("Failed to find direction actual to $it")

            val resistor = getResistor(directionActual)

            circuit.add(resistor)
        }

        prepared = true
    }

    /**
     * This must be called after "prepare", to finalize connections.
     * @see ElectricalObject.build
     * */
    fun connect(connections: List<ElectricalObject>, sender: ElectricalObject) {
        if (!prepared) {
            error("Not prepared")
        }

        connections.forEach { remoteObj ->
            val resistor = getResistor(obj.cell.pos.descriptor.findDirectionActual(remoteObj.cell.pos.descriptor)
                ?: error("Failed to find direction actual to $remoteObj")
            )

            val offered = remoteObj.offerComponent(sender)
            resistor.connect(Conventions.EXTERNAL_PIN, offered.component, offered.index)
        }
    }

    private fun getResistor(direction: RelativeRotationDirection): Resistor {
        return resistors.computeIfAbsent(direction) {
            if (prepared) {
                error("Tried to create resistors after bundle was prepared")
            }

            val result = Resistor()
            result.resistance = resistance

            return@computeIfAbsent result
        }
    }

    /**
     * Gets a resistor for the specified direction. Subsequent calls will return the same resistor,
     * unless *clear* is called.
     * If a resistor is not initialized for *direction*, and the bundle was prepared by *register*, an error will be produced.
     * */
    fun getOfferedResistor(direction: RelativeRotationDirection): ElectricalComponentInfo {
        return ElectricalComponentInfo(getResistor(direction), Conventions.EXTERNAL_PIN)
    }

    /**
     * Iterates through all the initialized resistors.
     * Keep in mind that a resistor is initialized __after__ *getOfferedResistor* is called.
     * */
    fun process(action: ((Resistor) -> Unit)) {
        resistors.values.forEach { action(it) }
    }

    /**
     * Clears the resistors and marks the bundle as *unprepared*.
     * @see ElectricalObject.clear
     * */
    fun clear() {
        resistors.clear()
        prepared = false
    }

    val power get() = resistors.values.sumOf { abs(it.power) }
}
