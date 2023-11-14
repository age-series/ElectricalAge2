package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.world.level.Level
import net.minecraft.core.BlockPos
import org.ageseries.libage.data.Area
import org.ageseries.libage.data.Quantity
import org.eln2.mc.*
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaNode
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

fun evaluateSunlight(rainLevel: Double, thunderLevel: Double, timeOfDay: Double) : Double {
    val d0 = 1.0 - rainLevel * 5.0 / 16.0
    val d1 = 1.0 - thunderLevel * 5.0 / 16.0
    val d2 = 0.5 + 2.0 * cos((2.0 * PI) * timeOfDay).coerceIn(-0.25, 0.25) // We are also compounding this with our own calculation. Maybe remove this?

    return (d0 * d1 * d2).coerceIn(0.0, 1.0)
}

fun Level.evaluateSunlight() = evaluateSunlight(
    this.getRainLevel(1.0f).toDouble(),
    this.getThunderLevel(1.0f).toDouble(),
    this.getTimeOfDay(1.0f).toDouble()
)

fun Level.evaluateDiffuseIrradianceFactor(normal: Vector3d) : Double {
    val pass = this.celestialPass()
    val sunlight = this.evaluateSunlight()

    val u = !Vector3d(pass.re, pass.im, 0.0) cosAngle !normal

    return if(u >= 0.0) {
        sunlight * u
    }
    else {
        0.0
    }
}

fun Level.evaluateDiffuseIrradianceFactor(normal: Vector3d, blockPos: BlockPos) : Double {
    if(!this.canSeeSky(blockPos)) {
        return 0.0
    }

    return this.evaluateDiffuseIrradianceFactor(normal)
}

val LEVEL_INTENSITY = Quantity(1000.0, WATT_PER_M2) // evaluate from level

data class PhotovoltaicModel(
    val idealVoltage: Quantity<Voltage>,
    val b: Double,
    val p: Double,
    val d: Double,
    val efficiency: Double,
)

class PhotovoltaicBehavior(
    val cell: Cell,
    val source: PVSObject<*>,
    val surfaceArea: Quantity<Area>,
    val model: PhotovoltaicModel,
    val normalSupplier: () -> Vector3d
) : CellBehavior {
    init {
        cell.locator.requireLocator<BlockLocator>()
    }

    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addPre(::update)
    }

    private fun update(dt: Double, phase: SubscriberPhase) {
        val irradiance = !LEVEL_INTENSITY * cell.graph.level.evaluateDiffuseIrradianceFactor(
            normalSupplier(),
            cell.locator.requireLocator<BlockLocator>()
        )

        source.updatePotentialMax(!model.idealVoltage * ((irradiance / model.b).pow(model.p) / model.d))
        source.updatePowerIdeal(irradiance * !surfaceArea * model.efficiency)
    }
}

class PhotovoltaicGeneratorCell(
    ci: CellCreateInfo,
    surfaceArea: Quantity<Area>,
    model: PhotovoltaicModel,
    val normalSupplier: (PhotovoltaicGeneratorCell) -> Vector3d
) : Cell(ci) {
    val normal get() = normalSupplier(this)

    @SimObject @Inspect
    val generator = PVSObject<Cell>(this, directionPoleMapPlanar())

    @Behavior
    val photovoltaic = PhotovoltaicBehavior(this, generator, surfaceArea, model, this::normal)

    init {
        ruleSet.withDirectionRulePlanar(Base6Direction3d.Front + Base6Direction3d.Back)
    }
}

class PhotovoltaicPanelPart(ci: PartCreateInfo, provider: CellProvider<PhotovoltaicGeneratorCell>) : CellPart<PhotovoltaicGeneratorCell, BasicPartRenderer>(ci, provider), WailaNode {
    override fun createRenderer() =  BasicPartRenderer(this, PartialModels.SOLAR_PANEL_ONE_BLOCK)

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        runIfCell {
            builder.voltage(cell.generator.potentialMaxExact)
            builder.power(cell.generator.sourcePower)
        }
    }
}

fun solarScan(normal: Vector3d) : Double {
    var sum = 0.0

    // Replaced integralScan with this because it is more representative of the discrete ticks
    repeat(12000) {
        val a = frac(it / 24000.0 - 0.25)
        val b = 0.5 - cos(a * PI) / 2.0
        val c = (a * 2.0 + b) / 3.0
        val d = celestialPass(2.0 * PI * c)

        sum += !Vector3d(d.re, d.im, 0.0) cosAngle normal
    }

    return 1.0 / sum
}
