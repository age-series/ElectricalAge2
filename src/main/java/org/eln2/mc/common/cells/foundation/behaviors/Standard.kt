package org.eln2.mc.common.cells.foundation.behaviors

import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellBehaviorContainer

fun CellBehaviorContainer.withStandardExplosionBehavior(cell: CellBase, threshold: Double, temperatureAccessor: ITemperatureAccessor): CellBehaviorContainer {
    return withExplosionBehavior(temperatureAccessor, TemperatureExplosionBehaviorOptions(
        threshold,
        0.25,
        0.25
    )) {
        (cell.container as MultipartBlockEntity).getPart(cell.pos.face)!!
    }
}
