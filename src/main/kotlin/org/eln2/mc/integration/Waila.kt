package org.eln2.mc.integration

import mcp.mobius.waila.api.*
import mcp.mobius.waila.api.component.PairComponent
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import org.ageseries.libage.sim.electrical.mna.component.Pin
import org.eln2.mc.LARGE_RESISTANCE
import org.eln2.mc.LOG
import org.eln2.mc.MODID
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.data.UnitType
import org.eln2.mc.data.valueText
import org.eln2.mc.formattedPercentN
import org.eln2.mc.integration.WailaTooltipEntryType.Companion.getTooltipEntryType
import org.eln2.mc.integration.WailaTooltipEntryType.Companion.putTooltipEntryType
import org.eln2.mc.mathematics.approxEq

@WailaPlugin(id = "$MODID:waila_plugin")
class Eln2WailaPlugin : IWailaPlugin {
    override fun register(registrar: IRegistrar?) {
        if (registrar == null) {
            return
        }

        registrar.addBlockData(IDataProvider { data, accessor, config ->
            var entity = accessor?.target as? WailaNode

            if (data == null || entity == null) {
                return@IDataProvider
            }

            if(entity is MultipartBlockEntity) {
                val part = entity.pickPart(accessor.player)

                if(part is WailaNode) {
                    entity = part
                }
                else {
                    return@IDataProvider
                }
            }

            val builder = WailaTooltip.builder()

            try {
                entity.appendWaila(builder, config)
            } catch (e : Exception) {
                // Handle errors caused by simulator (invalid access from user code)
                // Make sure you add a breakpoint here if you aren't getting your tooltip properly
                LOG.error("Tooltip error: $e")
            }

            builder.build().toNbt(data.raw())
        }, WailaNode::class.java)

        registrar.addComponent(object : IBlockComponentProvider {
            override fun appendBody(tooltip: ITooltip?, accessor: IBlockAccessor?, config: IPluginConfig?) {
                if (tooltip == null || accessor == null || config == null) {
                    return
                }

                val entries = WailaTooltip.fromNbt(accessor.data.raw())

                entries.values.forEach { entry ->
                    entry.write(tooltip)
                }
            }
        }, TooltipPosition.BODY, WailaNode::class.java)
    }
}

/**
 * Implemented by classes that want to export data to WAILA.
 * */
@FunctionalInterface
interface WailaNode {
    fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?)
}

enum class WailaTooltipEntryType(val id: Int) {
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
        fun CompoundTag.putTooltipEntryType(key: String, type: WailaTooltipEntryType) {
            this.putInt(key, type.id)
        }

        fun CompoundTag.getTooltipEntryType(key: String): WailaTooltipEntryType {
            return when (val data = this.getInt(key)) {
                TranslatableTranslatable.id -> TranslatableTranslatable
                TranslatableText.id -> TranslatableText
                TextText.id -> TextText
                else -> error("Invalid tooltip type $data")
            }
        }
    }
}

data class WailaTooltipEntry(val key: String, val value: String, val type: WailaTooltipEntryType) {
    companion object {
        fun fromNbt(nbt: CompoundTag): WailaTooltipEntry {
            val key = nbt.getString("Key")
            val value = nbt.getString("Value")
            val type = nbt.getTooltipEntryType("Type")

            return WailaTooltipEntry(key, value, type)
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
            WailaTooltipEntryType.TranslatableTranslatable -> tooltip.addLine(
                PairComponent(
                    Component.translatable(key),
                    Component.translatable(value)
                )
            )

            WailaTooltipEntryType.TranslatableText -> tooltip.addLine(
                PairComponent(
                    Component.translatable(key),
                    Component.literal(value)
                )
            )

            WailaTooltipEntryType.TextText -> tooltip.addLine(
                PairComponent(
                    Component.literal(key),
                    Component.literal(value)
                )
            )
        }
    }
}

data class WailaTooltip(val values: List<WailaTooltipEntry>) {
    companion object {
        fun builder(): WailaTooltipBuilder {
            return WailaTooltipBuilder()
        }

        fun fromNbt(nbt: CompoundTag): WailaTooltip {
            val listTag = nbt.get("TooltipEntries") as? ListTag

            if (listTag == null || listTag.size == 0) {
                return WailaTooltip(listOf())
            }

            val results = ArrayList<WailaTooltipEntry>(listTag.size)

            listTag.forEach {
                results.add(WailaTooltipEntry.fromNbt(it as CompoundTag))
            }

            return WailaTooltip(results.toList())
        }
    }

    fun toNbt(tag: CompoundTag) {
        val listTag = ListTag()

        values.forEach { listTag.add(it.createNbt()) }

        tag.put("TooltipEntries", listTag)
    }
}

class WailaTooltipBuilder {
    private val entries = ArrayList<WailaTooltipEntry>()

    private fun getTranslationKey(identifier: String): String {
        return "waila.eln2.$identifier"
    }

    /**
     * Adds an entry with language file [key] and [value].
     * */
    fun translate(key: String, value: String): WailaTooltipBuilder {
        entries.add(
            WailaTooltipEntry(
                getTranslationKey(key),
                getTranslationKey(value),
                WailaTooltipEntryType.TranslatableTranslatable
            )
        )
        return this
    }

    /**
     * Adds an entry with language file [key] and literal [value].
     * */
    fun translateText(key: String, value: String): WailaTooltipBuilder {
        entries.add(WailaTooltipEntry("waila.eln2.$key", value, WailaTooltipEntryType.TranslatableText))
        return this
    }

    /**
     * Adds an entry with literal [key] and [value].
     * */
    fun text(key: String, value: String): WailaTooltipBuilder {
        entries.add(WailaTooltipEntry(key, value, WailaTooltipEntryType.TextText))
        return this
    }

    /**
     * Adds an entry with literal [key] and [value].
     * */
    fun text(key: String, value: Any?): WailaTooltipBuilder {
        entries.add(WailaTooltipEntry(key, value?.toString() ?: "nil", WailaTooltipEntryType.TextText))
        return this
    }

    /**
     * Adds an entry with translated "mode" and value.
     * */
    fun mode(value: String): WailaTooltipBuilder {
        return translateText("mode", value)
    }

    /**
     * Adds an entry with translated "current" and formatted value.
     * */
    fun current(value: Double): WailaTooltipBuilder {
        return translateText("current", valueText(value, UnitType.AMPERE))
    }

    /**
     * Adds an entry with translated "energy" and formatted value.
     * */
    fun energy(value: Double): WailaTooltipBuilder {
        return translateText("energy", valueText(value, UnitType.JOULE))
    }

    /**
     * Adds an entry with translated "mass" and formatted value.
     * */
    fun mass(value: Double): WailaTooltipBuilder {
        return translateText("mass", valueText(value * 1000.0, UnitType.GRAM))
    }

    /**
     * Adds an entry with translated "voltage" and formatted value.
     * */
    fun voltage(value: Double): WailaTooltipBuilder {
        return translateText("voltage", valueText(value, UnitType.VOLT))
    }

    /**
     * Adds an entry with translated "resistance" and formatted value.
     * If the value is [LARGE_RESISTANCE], an infinity symbol is shown.
     * */
    fun resistance(value: Double): WailaTooltipBuilder {
        val adjustedValue = if(value.approxEq(LARGE_RESISTANCE, 1.0))
            Double.POSITIVE_INFINITY
        else value

        return translateText("resistance", valueText(adjustedValue, UnitType.OHM))
    }

    /**
     * Adds an entry with translated "charge" and the value formatted as a percentage.
     * */
    fun charge(value: Double): WailaTooltipBuilder {
        return translateText("charge", value.formattedPercentN())
    }

    /**
     * Adds an entry with translated "life" and the value formatted as a percentage.
     * */
    fun life(value: Double): WailaTooltipBuilder {
        return translateText("life", value.formattedPercentN())
    }

    /**
     * Adds an entry with translated "inductance" and formatted value.
     * */
    fun inductance(value: Double): WailaTooltipBuilder {
        return translateText("inductance", valueText(value, UnitType.HENRY))
    }

    /**
     * Adds an entry with translated "capacitance" and formatted value.
     * */
    fun capacitance(value: Double): WailaTooltipBuilder {
        return translateText("capacitance", valueText(value, UnitType.FARAD))
    }

    /**
     * Adds an entry with translated "power" and formatted value.
     * */
    fun power(value: Double): WailaTooltipBuilder {
        return translateText("power", valueText(value, UnitType.WATT))
    }

    /**
     * Adds an entry with translated "temperature" and formatted value.
     * */
    fun temperature(value: Double): WailaTooltipBuilder {
        return translateText("temperature", valueText(value, UnitType.KELVIN))
    }

    fun pinVoltages(pins: MutableList<Pin>) {
        pins.forEach { voltage(it.node?.potential ?: 0.0) }
    }

    fun build(): WailaTooltip {
        return WailaTooltip(entries.toList())
    }
}
