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
    /**
     * Tooltip entry with both key and value found in the language file.
     * */
    TranslatableTranslatable(0),
    /**
     * Tooltip entry with key found in the language file and literal value.
     * */
    TranslatableText(1),
    /**
     * Tooltip entry with literal key and value.
     * */
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

    /**
     * Adds an entry with language file [key] and [value].
     * */
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

    /**
     * Adds an entry with language file [key] and literal [value].
     * */
    fun translateText(key: String, value: String): TooltipBuilder {
        entries.add(TooltipEntry("waila.eln2.$key", value, TooltipEntryType.TranslatableText))
        return this
    }

    /**
     * Adds an entry with literal [key] and [value].
     * */
    fun text(key: String, value: String): TooltipBuilder {
        entries.add(TooltipEntry(key, value, TooltipEntryType.TextText))
        return this
    }

    /**
     * Adds an entry with literal [key] and [value].
     * */
    fun text(key: String, value: Any): TooltipBuilder {
        entries.add(TooltipEntry(key, value.toString(), TooltipEntryType.TextText))
        return this
    }

    /**
     * Adds an entry with translated "mode" and value.
     * */
    fun mode(value: String): TooltipBuilder {
        return translateText("mode", value)
    }

    /**
     * Adds an entry with translated "current" and formatted value.
     * */
    fun current(value: Double): TooltipBuilder {
        return translateText("current", ValueText.valueText(value, UnitType.AMPERE))
    }

    /**
     * Adds an entry with translated "energy" and formatted value.
     * */
    fun energy(value: Double): TooltipBuilder {
        return translateText("energy", ValueText.valueText(value, UnitType.JOULE))
    }

    /**
     * Adds an entry with translated "mass" and formatted value.
     * */
    fun mass(value: Double): TooltipBuilder {
        return translateText("mass", ValueText.valueText(value * 1000.0, UnitType.GRAM))
    }

    /**
     * Adds an entry with translated "voltage" and formatted value.
     * */
    fun voltage(value: Double): TooltipBuilder {
        return translateText("voltage", ValueText.valueText(value, UnitType.VOLT))
    }

    /**
     * Adds an entry with translated "resistance" and formatted value.
     * */
    fun resistance(value: Double): TooltipBuilder {
        return translateText("resistance", ValueText.valueText(value, UnitType.OHM))
    }

    /**
     * Adds an entry with translated "inductance" and formatted value.
     * */
    fun inductance(value: Double): TooltipBuilder {
        return translateText("inductance", ValueText.valueText(value, UnitType.HENRY))
    }

    /**
     * Adds an entry with translated "capacitance" and formatted value.
     * */
    fun capacitance(value: Double): TooltipBuilder {
        return translateText("capacitance", ValueText.valueText(value, UnitType.FARAD))
    }

    /**
     * Adds an entry with translated "power" and formatted value.
     * */
    fun power(value: Double): TooltipBuilder {
        return translateText("power", ValueText.valueText(value, UnitType.WATT))
    }

    /**
     * Adds an entry with translated "temperature" and formatted value.
     * */
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
