package org.eln2.mc.client.render

import com.jozufozu.flywheel.core.PartialModel
import org.eln2.mc.Eln2

object PartialModels {
    val WIRE_CROSSING_EMPTY = PartialModel(Eln2.resource("block/wire/wire_crossing_empty"))
    val WIRE_CROSSING_SINGLE_WIRE = PartialModel(Eln2.resource("block/wire/wire_crossing_singlewire"))
    val WIRE_STRAIGHT = PartialModel(Eln2.resource("block/wire/wire_straight"))
    val WIRE_CORNER = PartialModel(Eln2.resource("block/wire/wire_corner"))
    val WIRE_CROSSING = PartialModel(Eln2.resource("block/wire/wire_crossing"))
    val WIRE_CROSSING_FULL = PartialModel(Eln2.resource("block/wire/wire_crossing_full"))

    fun initialize(){}
}
