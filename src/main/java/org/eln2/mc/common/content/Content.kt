@file:Suppress("MemberVisibilityCanBePrivate", "PublicApiImplicitType", "PublicApiImplicitType", "unused", "LongLine")

package org.eln2.mc.common.content

import net.minecraft.world.phys.Vec3
import org.eln2.mc.mathematics.Functions.bbVec
import org.eln2.mc.mathematics.Functions.lerp
import org.eln2.mc.mathematics.Functions.vec3
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.providers.BasicCellProvider
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.foundation.providers.BasicPartProvider
import org.eln2.mc.utility.SelfDescriptiveUnitMultipliers.centimeters
import org.eln2.mc.utility.SelfDescriptiveUnitMultipliers.milliOhms
import org.eln2.mc.utility.UnitConversions.kwHoursInJ
import kotlin.math.abs

/**
 * Joint registry for content classes.
 */
object Content {
    /**
     * Initializes the fields, in order to register the content.
     */
    fun initialize() {}

    val COPPER_WIRE_CELL = CellRegistry.register("wire_cell_copper", BasicCellProvider.fourPin  { a, b ->
        WireCell(a, b, ElectricalWireModels.copper(centimeters(5.0)))
    })
    val COPPER_WIRE_PART = PartRegistry.part("wire_part_copper", BasicPartProvider({ a, b ->
        WirePart(a, b, COPPER_WIRE_CELL.get())
    }, Vec3(0.1, 0.1, 0.1)))

    val RESISTOR_CELL = CellRegistry.register("resistor_cell", BasicCellProvider.polarFB(::ResistorCell))
    val RESISTOR_PART = PartRegistry.part("resistor_part", BasicPartProvider(::ResistorPart, Vec3(1.0, 0.4, 0.4)))

    val VOLTAGE_SOURCE_CELL = CellRegistry.register("voltage_source_cell", BasicCellProvider.monoF(::VoltageSourceCell))
    val VOLTAGE_SOURCE_PART = PartRegistry.part("voltage_source_part", BasicPartProvider(::VoltageSourcePart, Vec3(0.3, 0.3, 0.3)))

    val GROUND_CELL = CellRegistry.register("ground_cell", BasicCellProvider.monoF(::GroundCell))
    val GROUND_PART = PartRegistry.part("ground_part", BasicPartProvider(::GroundPart, Vec3(0.3, 0.3, 0.3)))

    val FURNACE_BLOCK_ENTITY = BlockRegistry.blockEntity("furnace", ::FurnaceBlockEntity) { FURNACE_BLOCK.block.get() }
    val FURNACE_CELL = CellRegistry.register("furnace_cell", BasicCellProvider.polarLR(::FurnaceCell))
    val FURNACE_BLOCK = BlockRegistry.registerBasicBlock("furnace", tab = null) { FurnaceBlock() }

    val BATTERY_CELL_100V = CellRegistry.register("battery_cell_t", BasicCellProvider.polarFB{ pos, id ->
        BatteryCell(pos, id, BatteryModel(
            voltageFunction = VoltageModels.TEST,
            resistanceFunction = { _, _ -> milliOhms(100.0) },
            damageFunction = { battery, dt ->
                val currentThreshold = 7.0 //A

                // Test damage func: passive 0.001% per second
                // if current > threshold, 0.1% per second for every amp
                // if charge < threshold, amplify everything by 10delta

                val passiveTerm = 0.001 / 100.0 // remember: percent

                val absCurrent = abs(battery.current)

                val currentTerm = if(absCurrent > currentThreshold) {
                    (absCurrent - currentThreshold) * (0.1 / 100.0)
                } else 0.0

                val amplification = if(battery.charge < battery.model.damageChargeThreshold){
                    (battery.model.damageChargeThreshold - battery.charge) * 10
                } else 0.0

                (passiveTerm + currentTerm) * dt * (1.0 + amplification)
            },
            capacityFunction = { battery ->
                // Test capacity func: 0% after 5 cycles
                // life has 90% impact.

                val lifeTerm = -(1 - battery.life) * 0.90
                val cyclesTerm = -lerp(0.0, 1.0, battery.cycles / 5.0)

                1 + lifeTerm + cyclesTerm
            },
            energyCapacity = kwHoursInJ(2.2),
            0.5))
        .also { it.energy = it.model.energyCapacity * 0.9 }
    })

    val BATTERY_PART_100V = PartRegistry.part("battery_part_100v", BasicPartProvider({a, b -> BatteryPart(a, b, BATTERY_CELL_100V.get())}, vec3(1.0)))

    val LIGHT_GHOST_BLOCK = BlockRegistry.registerBasicBlock("light_ghost"){GhostLightBlock()}
    val LIGHT_CELL = CellRegistry.register("light_cell", BasicCellProvider.polarLR { pos, id ->
        LightCell(pos, id, LightModels.test())
    })
    val LIGHT_PART = PartRegistry.part("light_part", BasicPartProvider({a, b -> LightPart(a, b, LIGHT_CELL.get())}, bbVec(8.0, 4.0, 5.0)))
}
