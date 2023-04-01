package org.eln2.mc.common.cells.foundation.behaviors

import net.minecraft.server.level.ServerLevel
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellBehaviorContainer
import org.eln2.mc.extensions.LevelExtensions.destroyPart

fun CellBehaviorContainer.withStandardExplosionBehavior(cell: CellBase, threshold: Double, temperatureAccessor: ITemperatureAccessor): CellBehaviorContainer {
    return withExplosionBehavior(temperatureAccessor, TemperatureExplosionBehaviorOptions(threshold, 0.1, 0.25)) {
        val container = cell.container ?: return@withExplosionBehavior

        if(container is MultipartBlockEntity) {
            if(container.isRemoved){
                return@withExplosionBehavior
            }

            val part = container.getPart(cell.pos.face)
                ?: return@withExplosionBehavior

            val level = (part.placementContext.level as ServerLevel)

            level.destroyPart(part)
        }
        else {
            error("Cannot explode $container")
        }
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
        .withStandardExplosionBehavior(cell, 600.0) {
            thermal.get().temperatureK
        }
}
