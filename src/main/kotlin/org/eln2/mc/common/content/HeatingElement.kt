package org.eln2.mc.common.content

import net.minecraft.world.item.Item
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.InterpolationFunction
import org.ageseries.libage.mathematics.InterpolatorBuilder
import org.eln2.mc.Eln2Config
import org.eln2.mc.common.items.ItemRegistry
import kotlin.math.PI
import kotlin.math.pow

/**
 * A heating element installed in electrical furnaces and other thermal machines.
 *
 * The [resistivityCurve] stores the material's electrical resistivity **ρ(T)** in **Ω·m** as a function of temperature **(K)**.
 * Total resistance is computed from the wire geometry: **R(T) = ρ(T) × L / A**.
 *
 * @param maxTemperature The temperature at which the element degrades or fails.
 * @param resistivityCurve Spline mapping temperature (K) → resistivity (Ω·m).
 * @param length Length of the heating wire in meters.
 * @param diameter Diameter of the heating wire in meters (assumes round cross-section).
 */
class HeatingElementItem(
    val maxTemperature: Quantity<Temperature>,
    val resistivityCurve: InterpolationFunction<Double, Double>,
    val length: Quantity<Distance>,
    val diameter: Quantity<Distance>,
) : Item(Properties().stacksTo(1)) {

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)

        val yellow = ChatFormatting.YELLOW
        val gray = ChatFormatting.GRAY

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.heating_element.max_temp")
                .append(": ")
                .append(Component.literal(Eln2Config.clientConfig.classifyWithOverride(maxTemperature)).withStyle(gray))
                .withStyle(yellow)
        )
    }

    /** Cross-sectional area from diameter (round wire). */
    private val crossSectionArea: Double = PI * (!diameter / 2.0).pow(2)

    /**
     * Returns the total resistance at the given [temperature],
     * computed as **ρ(T) × L / A**.
     */
    fun resistanceAt(temperature: Quantity<Temperature>): Double {
        val rho = resistivityCurve.evaluate(!temperature)
        return rho * !length / crossSectionArea
    }

    /**
     * True if the given [temperature] exceeds the element's [maxTemperature].
     */
    fun isExceedingMaxTemperature(temperature: Quantity<Temperature>): Boolean {
        return !temperature > !maxTemperature
    }
}

class BurntHeatingElementItem : Item(Properties().stacksTo(1)) {
    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)
        pTooltipComponents.add(Component.translatable("tooltip.eln2.heating_element.burnt").withStyle(ChatFormatting.DARK_RED))
    }
}

class HeatingElementBuilder(val id: String) {
    /**
     * Maximum safe operating temperature.
     * */
    var maxTemperature: Quantity<Temperature> = Quantity(1000.0, CELSIUS)

    /**
     * Length of the heating wire.
     * */
    var length: Quantity<Distance> = Quantity(0.05, METER)

    /**
     * Diameter of the heating wire.
     * */
    var diameter: Quantity<Distance> = Quantity(1.0, MILLI * METER)

    private var curveBlock: (ResistivityCurveBuilder.() -> Unit)? = null

    fun setMaxTemperature(temperature: Quantity<Temperature>) = apply {
        maxTemperature = temperature
    }

    fun setGeometry(length: Quantity<Distance>, diameter: Quantity<Distance>) = apply {
        this.length = length
        this.diameter = diameter
    }

    /**
     * Defines the resistivity-temperature curve **ρ(T)**.
     * The curve stores values in **Ω·m**.
     * */
    fun setResistivityCurve(block: ResistivityCurveBuilder.() -> Unit) = apply {
        curveBlock = block
    }

    fun register(): RegistryObject<HeatingElementItem> {
        val curve = curveBlock?.let { block ->
            ResistivityCurveBuilder().apply(block).build()
        } ?: error("Resistivity curve not defined for $id")

        return ItemRegistry.item(id) {
            HeatingElementItem(
                maxTemperature = maxTemperature,
                resistivityCurve = curve,
                length = length,
                diameter = diameter
            )
        }
    }

    class ResistivityCurveBuilder {
        private val points = mutableListOf<Pair<Double, Double>>()

        /**
         * Adds a control point on the resistivity-temperature curve.
         * The resistivity **ρ(T)** is temperature-dependent; this spline is evaluated
         * against [ThermalMass.temperature][org.ageseries.libage.sim.ThermalMass.temperature],
         * which is in **Kelvin**.
         *
         * @param temperatureKelvin  Temperature in **K**.
         * @param resistivityOhmMeter Resistivity in **Ω·m** at that temperature.
         */
        fun withPoint(temperatureKelvin: Double, resistivityOhmMeter: Double) {
            points.add(temperatureKelvin to resistivityOhmMeter)
        }

        internal fun build(): InterpolationFunction<Double, Double> {
            require(points.size >= 2) { "Resistivity curve needs at least 2 control points, got ${points.size}" }

            val sorted = points.sortedBy { it.first }

            val spline = InterpolatorBuilder().apply {
                sorted.forEach { (t, r) -> with(t, r) }
            }.buildCubic()

            val min = sorted.first().component1()
            val max = sorted.last().component1()

            return InterpolationFunction { param ->
                spline.evaluate(param.coerceIn(min, max))
            }
        }
    }
}
