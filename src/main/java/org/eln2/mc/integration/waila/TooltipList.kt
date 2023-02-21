package org.eln2.mc.integration.waila

import mcp.mobius.waila.api.ITooltip
import mcp.mobius.waila.api.component.PairComponent
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import org.ageseries.libage.sim.electrical.mna.component.Pin
import org.eln2.mc.integration.waila.TooltipEntryType.Companion.getTooltipEntryType
import org.eln2.mc.integration.waila.TooltipEntryType.Companion.putTooltipEntryType
import org.eln2.mc.utility.UnitType
import org.eln2.mc.utility.ValueText

enum class TooltipEntryType(val id: Int) {
    TranslatableTranslatable(0),
    TranslatableText(1),
    TextText(2);

    companion object {
        fun CompoundTag.putTooltipEntryType(key: String, type: TooltipEntryType) {
            this.putInt(key, type.id)
        }

        fun CompoundTag.getTooltipEntryType(key: String): TooltipEntryType {
            return when (val data = this.getInt(key)) {
                TranslatableTranslatable.id -> TranslatableTranslatable
                TranslatableText.id -> TranslatableText
                TextText.id -> TextText
                else -> error("Invalid tooltip type $data")
            }
        }
    }
}

data class TooltipEntry(val key: String, val value: String, val type: TooltipEntryType) {
    companion object {
        fun fromNbt(nbt: CompoundTag): TooltipEntry {
            val key = nbt.getString("Key")
            val value = nbt.getString("Value")
            val type = nbt.getTooltipEntryType("Type")

            return TooltipEntry(key, value, type)
        }
    }

    fun createNbt(): CompoundTag {
        val result = CompoundTag()

        result.putString("Key", key)
        result.putString("Value", value)
        result.putTooltipEntryType("Type", type)

        return result
    }

    fun write(tooltip: ITooltip) {
        when (type) {
            TooltipEntryType.TranslatableTranslatable -> tooltip.addLine(
                PairComponent(
                    TranslatableComponent(key),
                    TranslatableComponent(value)
                )
            )

            TooltipEntryType.TranslatableText -> tooltip.addLine(
                PairComponent(
                    TranslatableComponent(key),
                    TextComponent(value)
                )
            )

            TooltipEntryType.TextText -> tooltip.addLine(PairComponent(TextComponent(key), TextComponent(value)))
        }
    }
}

data class TooltipList(val values: List<TooltipEntry>) {
    companion object {
        fun builder(): TooltipBuilder {
            return TooltipBuilder()
        }

        fun fromNbt(nbt: CompoundTag): TooltipList {
            val listTag = nbt.get("TooltipEntries") as? ListTag

            if (listTag == null || listTag.size == 0) {
                return TooltipList(listOf())
            }

            val results = ArrayList<TooltipEntry>(listTag.size)

            listTag.forEach {
                results.add(TooltipEntry.fromNbt(it as CompoundTag))
            }

            return TooltipList(results.toList())
        }
    }

    fun toNbt(tag: CompoundTag) {
        val listTag = ListTag()

        values.forEach { listTag.add(it.createNbt()) }

        tag.put("TooltipEntries", listTag)
    }
}

class TooltipBuilder {
    private val entries = ArrayList<TooltipEntry>()

    private fun getTranslationKey(identifier: String): String {
        return "waila.eln2.$identifier"
    }

    fun translate(key: String, value: String): TooltipBuilder {
        entries.add(
            TooltipEntry(
                getTranslationKey(key),
                getTranslationKey(value),
                TooltipEntryType.TranslatableTranslatable
            )
        )
        return this
    }

    fun translateText(key: String, value: String): TooltipBuilder {
        entries.add(TooltipEntry("waila.eln2.$key", value, TooltipEntryType.TranslatableText))
        return this
    }

    fun text(key: String, value: String): TooltipBuilder {
        entries.add(TooltipEntry(key, value, TooltipEntryType.TextText))
        return this
    }

    fun text(key: String, value: Any): TooltipBuilder {
        entries.add(TooltipEntry(key, value.toString(), TooltipEntryType.TextText))
        return this
    }

    fun mode(value: String): TooltipBuilder {
        return translateText("mode", value)
    }

    fun current(value: Double): TooltipBuilder {
        return translateText("current", ValueText.valueText(value, UnitType.AMPERE))
    }

    fun energy(value: Double): TooltipBuilder {
        return translateText("energy", ValueText.valueText(value, UnitType.JOULE))
    }

    fun voltage(value: Double): TooltipBuilder {
        return translateText("voltage", ValueText.valueText(value, UnitType.VOLT))
    }

    fun resistance(value: Double): TooltipBuilder {
        return translateText("resistance", ValueText.valueText(value, UnitType.OHM))
    }

    fun inductance(value: Double): TooltipBuilder {
        return translateText("inductance", ValueText.valueText(value, UnitType.HENRY))
    }

    fun capacitance(value: Double): TooltipBuilder {
        return translateText("capacitance", ValueText.valueText(value, UnitType.FARAD))
    }

    fun power(value: Double): TooltipBuilder {
        return translateText("power", ValueText.valueText(value, UnitType.WATT))
    }

    fun temperature(value: Double): TooltipBuilder {
        return translateText("temperature", ValueText.valueText(value, UnitType.KELVIN))
    }

    fun pinVoltages(pins: MutableList<Pin>) {
        pins.forEach { voltage(it.node?.potential ?: 0.0) }
    }

    fun build(): TooltipList {
        return TooltipList(entries.toList())
    }
}
