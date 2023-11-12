package org.eln2.mc.common.cells.foundation

import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Component
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.connect
import org.eln2.mc.data.BlockLocator
import org.eln2.mc.data.FaceLocator
import org.eln2.mc.data.FacingLocator
import org.eln2.mc.data.requireLocator
import kotlin.math.abs

class ComponentHolder<T : Component>(private val factory: () -> T) {
    var value: T? = null
        private set

    val instance: T
        get() {
            if (value == null) {
                value = factory()
            }

            return value!!
        }

    fun connect(pin: Int, component: Component, remotePin: Int) {
        instance.connect(pin, component, remotePin)
    }

    fun connect(pin: Int, componentInfo: ElectricalComponentInfo) {
        instance.connect(pin, componentInfo)
    }

    fun connectInternal(component: Component, remotePin: Int) {
        connect(INTERNAL_PIN, component, remotePin)
    }

    fun connectInternal(componentInfo: ElectricalComponentInfo) {
        connectInternal(componentInfo.component, componentInfo.index)
    }

    fun connectExternal(component: Component, remotePin: Int) {
        connect(EXTERNAL_PIN, component, remotePin)
    }

    fun connectExternal(componentInfo: ElectricalComponentInfo) {
        connectExternal(componentInfo.component, componentInfo.index)
    }

    fun connectExternal(owner: ElectricalObject<*>, connection: ElectricalObject<*>) {
        connectExternal(connection.offerComponent(owner))
    }

    fun connectPositive(component: Component, remotePin: Int) {
        connect(POSITIVE_PIN, component, remotePin)
    }

    fun connectPositive(componentInfo: ElectricalComponentInfo) {
        connectPositive(componentInfo.component, componentInfo.index)
    }

    fun connectPositive(owner: ElectricalObject<*>, connection: ElectricalObject<*>) {
        connectPositive(connection.offerComponent(owner))
    }

    fun connectNegative(component: Component, remotePin: Int) {
        connect(NEGATIVE_PIN, component, remotePin)
    }

    fun connectNegative(componentInfo: ElectricalComponentInfo) {
        connectNegative(componentInfo.component, componentInfo.index)
    }

    fun connectNegative(owner: ElectricalObject<*>, connection: ElectricalObject<*>) {
        connectNegative(connection.offerComponent(owner))
    }

    fun ground(pin: Int) {
        instance.ground(pin)
    }

    fun groundInternal() {
        ground(INTERNAL_PIN)
    }

    fun groundNegative() {
        ground(NEGATIVE_PIN)
    }

    fun groundExternal() {
        ground(EXTERNAL_PIN)
    }

    fun offerInternal(): ElectricalComponentInfo {
        return ElectricalComponentInfo(instance, INTERNAL_PIN)
    }

    fun offerExternal(): ElectricalComponentInfo {
        return ElectricalComponentInfo(instance, EXTERNAL_PIN)
    }

    fun offerPositive(): ElectricalComponentInfo {
        return ElectricalComponentInfo(instance, POSITIVE_PIN)
    }

    fun offerNegative(): ElectricalComponentInfo {
        return ElectricalComponentInfo(instance, NEGATIVE_PIN)
    }

    fun clear() {
        value = null
    }

    val isPresent get() = value != null

    val isNotPresent get() = value == null

    inline fun ifPresent(action: ((T) -> Unit)): Boolean {
        if (value == null) {
            return false
        }

        action(value!!)

        return true
    }
}

/**
 * Utility class that holds a collection of resistors to be used as contact points for external components.
 * */
class ResistorBundle(var resistance: Double, obj: ElectricalObject<*>) {
    init {
        obj.cell.locator.requireLocator<BlockLocator>()
        obj.cell.locator.requireLocator<FacingLocator>()
        obj.cell.locator.requireLocator<FaceLocator>()
    }

    private val resistors = HashMap<ElectricalObject<*>, Resistor>()

    private var prepared = false

    /**
     * This must be called once the circuit is made available, in order to register the resistors.
     * This "prepares" the bundle, so future calls to *getOfferedResistor* that result in a new resistor being created will cause an error.
     * @see ElectricalObject.addComponents
     * */
    fun register(connections: List<ElectricalObject<*>>, circuit: Circuit) {
        if (prepared) {
            error("Already prepared")
        }

        connections.forEach {
            val resistor = getResistor(it)
            circuit.add(resistor)
        }

        prepared = true
    }

    /**
     * This must be called after "prepare", to finalize connections.
     * @see ElectricalObject.build
     * */
    fun connect(connections: List<ElectricalObject<*>>, sender: ElectricalObject<*>) {
        if (!prepared) {
            error("Not prepared")
        }

        connections.forEach { remoteObj ->
            val resistor = getResistor(remoteObj)
            val offered = remoteObj.offerComponent(sender)
            resistor.connect(EXTERNAL_PIN, offered.component, offered.index)
        }
    }

    private fun getResistor(remote: ElectricalObject<*>): Resistor {
        return resistors.computeIfAbsent(remote) {
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
    fun getOfferedResistor(remote: ElectricalObject<*>): ElectricalComponentInfo {
        return ElectricalComponentInfo(getResistor(remote), EXTERNAL_PIN)
    }

    /**
     * Iterates through all the initialized resistors.
     * Keep in mind that a resistor is initialized __after__ *getOfferedResistor* is called.
     * */
    fun forEach(action: ((Resistor) -> Unit)) {
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

    val totalCurrent get() = resistors.values.sumOf { abs(it.current) }
    val totalPower get() = resistors.values.sumOf { abs(it.power) }
}
