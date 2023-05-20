package org.eln2.mc.common.content

import net.minecraft.Util
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import org.ageseries.libage.sim.thermal.Temperature
import org.eln2.mc.common.items.eln2Tab
import org.eln2.mc.data.INameField
import org.eln2.mc.extensions.LevelExtensions.getDataAccess
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.valueText

fun interface VoltageField { fun readVoltage(): Double }
fun interface CurrentField { fun readCurrent(): Double }
fun interface TemperatureField { fun readTemperature(): Temperature }

abstract class MeterItem : Item(Properties().tab(eln2Tab).stacksTo(1)) {
    override fun useOn(pContext: UseOnContext): InteractionResult {
        if(pContext.level.isClientSide) {
            return InteractionResult.PASS
        }

        val player = pContext.player ?: return InteractionResult.FAIL

        player.sendMessage(read(pContext, player) ?: return InteractionResult.FAIL, Util.NIL_UUID)

        return InteractionResult.SUCCESS
    }

    abstract fun read(pContext: UseOnContext, player: Player): Component?
}

class UniversalMeter(
    // Default values are provided to make registration easier:
    val readVoltage: Boolean = false,
    val readCurrent: Boolean = false,
    val readTemperature: Boolean = false) : MeterItem() {
    override fun read(pContext: UseOnContext, player: Player): Component? {
        val target = pContext.level.getDataAccess(pContext.clickedPos)
            ?: return null

        class FieldReading(val field: Any, val label: String, val printout: String)

        val readings = ArrayList<FieldReading>()

        if(readVoltage) {
            target.fieldScan<VoltageField>().forEach {
                readings.add(FieldReading(it, "Voltage", valueText(it.readVoltage(), UnitType.VOLT)))
            }
        }

        if(readCurrent) {
            target.fieldScan<CurrentField>().forEach {
                readings.add(FieldReading(it, "Current", valueText(it.readCurrent(), UnitType.AMPERE)))
            }
        }

        if(readTemperature) {
            target.fieldScan<TemperatureField>().forEach {
                readings.add(FieldReading(it, "Temperature", valueText(it.readTemperature().kelvin, UnitType.KELVIN)))
            }
        }

        return if(readings.isEmpty()) null
        else TextComponent(readings.joinToString(", ") { reading ->
            if (reading.field is INameField) "${reading.field.name}: ${reading.printout}"
            else "${reading.label}: ${reading.printout}"
        })
    }
}
