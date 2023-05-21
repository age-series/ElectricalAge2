package org.eln2.mc.common.cells.foundation.objects

/**
 * This represents an immutable set of simulation objects.
 * All possible simulation objects are stored in fields, because there won't be many (probably only electrical, thermal and mechanical),
 * so there is no reason not to skip an allocation for a map/array.
 * */
class SimulationObjectSet(objects: List<SimulationObject>) {
    constructor(vararg objects: SimulationObject) : this(objects.asList())

    private val objects = HashMap<SimulationObjectType, SimulationObject>()

    private val mask: SimulationObjectMask

    init {
        if (objects.isEmpty()) {
            error("Tried to create empty simulation object set.")
        }

        var objectMask = SimulationObjectMask.EMPTY

        objects.forEach {
            if(this.objects.put(it.type, it) != null) {
                error("Duplicate object of type ${it.type}")
            }

            objectMask += it.type
        }

        mask = objectMask
    }

    fun hasObject(type: SimulationObjectType): Boolean {
        return mask.hasFlag(type)
    }

    private fun getObject(type: SimulationObjectType): SimulationObject {
        return objects[type] ?: error("Object set does not have $type")
    }

    val electricalObject get() = getObject(SimulationObjectType.Electrical) as ElectricalObject
    val thermalObject get() = getObject(SimulationObjectType.Thermal) as ThermalObject

    fun process(function: ((SimulationObject) -> Unit)) {
        objects.values.forEach(function)
    }

    operator fun get(type: SimulationObjectType): SimulationObject {
        return objects[type] ?: error("Object set does not have $type")
    }
}
