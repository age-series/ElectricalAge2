@file:Suppress("MemberVisibilityCanBePrivate", "PublicApiImplicitType", "PublicApiImplicitType", "unused", "LongLine")

package org.eln2.mc.common.content

import net.minecraft.world.phys.Vec3
import org.eln2.mc.Mathematics.vec3
import org.eln2.mc.Mathematics.vec3One
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.providers.BasicCellProvider
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.parts.foundation.providers.BasicPartProvider
import org.eln2.mc.utility.SelfDescriptiveUnitMultipliers.milliOhms
import org.eln2.mc.utility.UnitConversions.kwHoursInJ
import org.eln2.mc.utility.UnitConversions.wattHoursInJ

/**
 * Joint registry for content classes.
 */
object Content {
    /**
     * Initializes the fields, in order to register the content.
     */
    fun initialize() {}

    val WIRE_CELL = CellRegistry.register("wire_cell", BasicCellProvider.fourPin(::WireCell))
    val WIRE_PART = PartRegistry.part("wire_part", BasicPartProvider(::WirePart, Vec3(0.1, 0.1, 0.1)))

    val RESISTOR_CELL = CellRegistry.register("resistor_cell", BasicCellProvider.polarFB(::ResistorCell))
    val RESISTOR_PART = PartRegistry.part("resistor_part", BasicPartProvider(::ResistorPart, Vec3(1.0, 0.4, 0.4)))

    val VOLTAGE_SOURCE_CELL = CellRegistry.register("voltage_source_cell", BasicCellProvider.monoF(::VoltageSourceCell))
    val VOLTAGE_SOURCE_PART = PartRegistry.part("voltage_source_part", BasicPartProvider(::VoltageSourcePart, Vec3(0.3, 0.3, 0.3)))

    val GROUND_CELL = CellRegistry.register("ground_cell", BasicCellProvider.monoF(::GroundCell))
    val GROUND_PART = PartRegistry.part("ground_part", BasicPartProvider(::GroundPart, Vec3(0.3, 0.3, 0.3)))

    val FURNACE_BLOCK_ENTITY = BlockRegistry.blockEntity("furnace", ::FurnaceBlockEntity) { FURNACE_BLOCK.block.get() }
    val FURNACE_CELL = CellRegistry.register("furnace_cell", BasicCellProvider.polarLR(::FurnaceCell))
    val FURNACE_BLOCK = BlockRegistry.registerBasicBlock("furnace", tab = null) { FurnaceBlock() }

    val BATTERY_CELL_10V = CellRegistry.register("battery_cell_10v", BasicCellProvider.polarFB{ pos, id ->
        BatteryCell(pos, id, BatteryModels.linVConstR(10.0, milliOhms(50.0), wattHoursInJ(100.0)))
        .also { it.energy = it.model.energyCapacity / 2.0 }
    })

    val BATTERY_CELL_100V = CellRegistry.register("battery_cell_100v", BasicCellProvider.polarFB{ pos, id ->
        BatteryCell(pos, id, BatteryModels.linVConstR(100.0, milliOhms(100.0), kwHoursInJ(1.2)))
        .also { it.energy = it.model.energyCapacity / 2.0 }
    })

    val BATTERY_PART_10V = PartRegistry.part("battery_part_10v", BasicPartProvider({a, b -> BatteryPart(a, b, BATTERY_CELL_10V.get())}, vec3(1.0)))
    val BATTERY_PART_100V = PartRegistry.part("battery_part_100v", BasicPartProvider({a, b -> BatteryPart(a, b, BATTERY_CELL_100V.get())}, vec3(1.0)))

    val LIGHT_GHOST_BLOCK = BlockRegistry.registerBasicBlock("light_ghost"){GhostLightBlock()}
    val LIGHT_CELL = CellRegistry.register("light_cell", BasicCellProvider.polarFB { pos, id ->
        LightCell(pos, id, LightModels.test())
    })
    val LIGHT_PART = PartRegistry.part("light_part", BasicPartProvider({a, b -> LightPart(a, b, LIGHT_CELL.get())}, vec3One()))
}
