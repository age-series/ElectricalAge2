package org.eln2.mc.client.render.foundation

import com.jozufozu.flywheel.core.PartialModel
import org.eln2.mc.Eln2

object PartialModels {
    val WIRE_CROSSING_EMPTY = partial("block/wire/wire_crossing_empty")
    val WIRE_CROSSING_SINGLE_WIRE = partial("block/wire/wire_crossing_singlewire")
    val WIRE_STRAIGHT = partial("block/wire/wire_straight")
    val WIRE_CORNER = partial("block/wire/wire_corner")
    val WIRE_CROSSING = partial("block/wire/wire_crossing")
    val WIRE_CROSSING_FULL = partial("block/wire/wire_crossing_full")

    val BATTERY = partial("block/12v_battery")

    private fun partial(path : String) : PartialModel{
        return PartialModel(Eln2.resource(path))
    }

    fun initialize(){}
}
