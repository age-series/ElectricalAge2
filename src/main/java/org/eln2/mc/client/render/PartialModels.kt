package org.eln2.mc.client.render

import com.jozufozu.flywheel.core.PartialModel
import org.eln2.mc.Eln2
import org.eln2.mc.Mathematics.bbSize

object PartialModels {
    val WIRE_CROSSING_EMPTY = partialBlock("wire/wire_crossing_empty")
    val WIRE_CROSSING_SINGLE_WIRE = partialBlock("wire/wire_crossing_singlewire")
    val WIRE_STRAIGHT = partialBlock("wire/wire_straight")
    val WIRE_CORNER = partialBlock("wire/wire_corner")
    val WIRE_CROSSING = partialBlock("wire/wire_crossing")
    val WIRE_CROSSING_FULL = partialBlock("wire/wire_crossing_full")

    val BATTERY = partialBlock("12v_battery")

    val VOLTAGE_SOURCE = partialBlock("voltage_source")
    val RESISTOR = partialBlock("resistor")
    val GROUND = partialBlock("ground_pin")

    private fun partial(path: String): PartialModel {
        return PartialModel(Eln2.resource(path))
    }

    private fun partialBlock(path: String): PartialModel {
        return PartialModel(Eln2.resource("block/$path"))
    }

    fun initialize() {}

    fun bbOffset(height: Int): Double{
        return bbOffset(height.toDouble())
    }

    fun bbOffset(height: Double): Double{
        return bbSize(height) / 2
    }
}
