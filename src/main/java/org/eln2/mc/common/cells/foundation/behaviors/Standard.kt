package org.eln2.mc.common.cells.foundation.behaviors

import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellBehaviorContainer

fun CellBehaviorContainer.withStandardExplosionBehavior(cell: CellBase, threshold: Double, temperatureAccessor: ITemperatureAccessor): CellBehaviorContainer {
    return withExplosionBehavior(temperatureAccessor, TemperatureExplosionBehaviorOptions(
        threshold,
        0.1,
        0.25
    )) {

        // todo maybe an IExplodable?
        (cell.container as MultipartBlockEntity).getPart(cell.pos.face)!!
    }
}

/**
 * Registers a set of standard cell behaviors:
 * - [ElectricalPowerConverterBehavior]
 *      - converts power into energy
 * - [ElectricalHeatTransferBehavior]
 *      - moves energy to the heat mass from the electrical converter
 * - [TemperatureExplosionBehavior]
 *      - explodes part when a threshold temperature is held for a certain time period
 * */
fun CellBehaviorContainer.withStandardBehavior(cell: CellBase, power: IElectricalPowerAccessor, thermal: IThermalBodyAccessor): CellBehaviorContainer {
    return this
        .withElectricalPowerConverter(power)
        .withElectricalHeatTransfer(thermal)
        .withStandardExplosionBehavior(cell, 250.0) {
            thermal.get().temperatureK
        }
}
