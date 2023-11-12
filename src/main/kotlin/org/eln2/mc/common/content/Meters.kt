package org.eln2.mc.common.content

import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext

abstract class MeterItem : Item(Properties().stacksTo(1)) {
    override fun useOn(pContext: UseOnContext): InteractionResult {
        if (pContext.level.isClientSide) {
            return InteractionResult.PASS
        }

        val player = pContext.player ?: return InteractionResult.FAIL

        player.sendSystemMessage(read(pContext, player) ?: return InteractionResult.FAIL)

        return InteractionResult.SUCCESS
    }

    abstract fun read(pContext: UseOnContext, player: Player): Component?
}

class UniversalMeter(
    // Default values are provided to make registration easier:
    val readVoltage: Boolean = false,
    val readCurrent: Boolean = false,
    val readTemperature: Boolean = false,
) : MeterItem() {
    override fun read(pContext: UseOnContext, player: Player): Component? {
        TODO()

        class FieldReading(val field: Any, val label: String, val printout: String)

        val readings = ArrayList<FieldReading>()
        // todo
       /* if (readVoltage) {
            target.fieldScan<VoltageField>().forEach {
                readings.add(FieldReading(it, "Voltage", valueText(it.read(), UnitType.VOLT)))
            }
        }

        if (readCurrent) {
            target.fieldScan<CurrentField>().forEach {
                readings.add(FieldReading(it, "Current", valueText(it.read(), UnitType.AMPERE)))
            }
        }

        if (readTemperature) {
            target.fieldScan<TemperatureField>().forEach {
                readings.add(FieldReading(it, "Temperature", valueText(it.read().kelvin, UnitType.KELVIN)))
            }
        }*/

        return if (readings.isEmpty()) null
        else Component.literal(readings.joinToString(", ") { reading ->
            "${reading.label}: ${reading.printout}"
        })
    }
}
