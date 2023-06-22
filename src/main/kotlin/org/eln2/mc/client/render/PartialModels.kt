package org.eln2.mc.client.render

import com.jozufozu.flywheel.core.PartialModel
import org.eln2.mc.mathematics.bbSize
import org.eln2.mc.resource

object PartialModels {
    val ELECTRICAL_WIRE_CROSSING_EMPTY = partialBlock("wire/electrical/wire_crossing_empty")
    val ELECTRICAL_WIRE_CROSSING_SINGLE_WIRE = partialBlock("wire/electrical/wire_crossing_single")
    val ELECTRICAL_WIRE_STRAIGHT = partialBlock("wire/electrical/wire_straight")
    val ELECTRICAL_WIRE_CORNER = partialBlock("wire/electrical/wire_corner")
    val ELECTRICAL_WIRE_CROSSING = partialBlock("wire/electrical/wire_crossing")
    val ELECTRICAL_WIRE_CROSSING_FULL = partialBlock("wire/electrical/wire_crossing_full")

    val THERMAL_WIRE_CROSSING_EMPTY = partialBlock("wire/thermal/wire_crossing_empty")
    val THERMAL_WIRE_CROSSING_SINGLE_WIRE = partialBlock("wire/thermal/wire_crossing_single")
    val THERMAL_WIRE_STRAIGHT = partialBlock("wire/thermal/wire_straight")
    val THERMAL_WIRE_CORNER = partialBlock("wire/thermal/wire_corner")
    val THERMAL_WIRE_CROSSING = partialBlock("wire/thermal/wire_crossing")
    val THERMAL_WIRE_CROSSING_FULL = partialBlock("wire/thermal/wire_crossing_full")

    val BATTERY = partialBlock("battery/lead_acid")

    val VOLTAGE_SOURCE = partialBlock("voltage_source")
    val RESISTOR = partialBlock("resistor")
    val GROUND = partialBlock("ground_pin")
    val SMALL_WALL_LAMP_EMITTER = partialBlock("small_wall_lamp_emitter")
    val SMALL_WALL_LAMP_CAGE = partialBlock("small_wall_lamp_cage")

    val PELTIER_ELEMENT = partialBlock("peltier_element")
    val PELTIER_BODY = partialBlock("peltier/body")
    val PELTIER_LEFT = partialBlock("peltier/left")
    val PELTIER_RIGHT = partialBlock("peltier/right")

    val RADIATOR = partialBlock("radiator")

    val SOLAR_PANEL_ONE_BLOCK = partialBlock("solar_panel_one_block")

    private fun partial(path: String): PartialModel {
        return PartialModel(resource(path))
    }

    fun partialBlock(path: String): PartialModel {
        return PartialModel(resource("block/$path"))
    }

    fun initialize() {}

    fun bbOffset(height: Int): Double {
        return bbOffset(height.toDouble())
    }

    fun bbOffset(height: Double): Double {
        return bbSize(height) / 2
    }
}
