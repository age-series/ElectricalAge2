package org.eln2.mc.common.content

import net.minecraft.world.level.Level
import org.eln2.mc.*
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.data.*
import org.eln2.mc.mathematics.*
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos

fun evaluateSunlight(rainLevel: Double, thunderLevel: Double, timeOfDay: Double) : Double {
    val d0 = 1.0 - rainLevel * 5.0 / 16.0
    val d1 = 1.0 - thunderLevel * 5.0 / 16.0
    val d2 = 0.5 + 2.0 * cos((2.0 * PI) * timeOfDay).coerceIn(-0.25, 0.25)

    return (d0 * d1 * d2).nanZero().coerceIn(0.0, 1.0)
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

interface IlluminatedBodyView {
    val sunAngle: Double
    val isObstructed: Boolean
    val normal: Vector3d

    fun celestialPass() = celestialPass(sunAngle)
}

abstract class SolarIlluminationBehavior(val cell: Cell) : CellBehavior, IlluminatedBodyView {
    init {
        cell.locator.requireLocator<BlockLocator> {
            "Solar illumination behavior needs block position"
        }
    }

    // Is it fine to access these from our simulation threads?
    override val sunAngle: Double get() =
        cell.graph.level.getSunAngle(0f).toDouble()

    override val isObstructed: Boolean
        get() = !cell.graph.level.canSeeSky(cell.locator.requireLocator<BlockLocator>())
}

fun interface PhotovoltaicVoltageFunction {
    fun compute(view: IlluminatedBodyView): Double
}

data class PhotovoltaicModel(val voltageFunction: PhotovoltaicVoltageFunction, val panelResistance: Double)

object PhotovoltaicModels {
    // We map angle difference to a voltage coefficient. 0 - directly overhead, 1 - under horizon
    private val TEST_SPLINE = InterpolatorBuilder()
        .with(0.0, 1.0)
        .with(0.95, 0.8)
        .with(1.0, 0.0)
        .buildCubic()

    private fun voltageTest(maximumVoltage: Double): PhotovoltaicVoltageFunction {
        return PhotovoltaicVoltageFunction { view ->
            if (view.isObstructed) {
                return@PhotovoltaicVoltageFunction 0.0
            }

            val pass = celestialPass(view.sunAngle)
            if(pass.im < 0.0) {
                return@PhotovoltaicVoltageFunction 0.0
            }

            val angle = Vector3d(pass.re, pass.im, 0.0) angle view.normal

            val value = TEST_SPLINE.evaluate(
                map(
                    angle.absoluteValue.coerceIn(0.0, PI / 2.0),
                    0.0,
                    PI / 2.0,
                    0.0,
                    1.0
                )
            )

            return@PhotovoltaicVoltageFunction value * maximumVoltage
        }
    }

    fun test24Volts(): PhotovoltaicModel {
        //https://www.todoensolar.com/285w-24-volt-AmeriSolar-Solar-Panel
        return PhotovoltaicModel(voltageTest(32.0), 3.5)
    }
}

/**
 * Photovoltaic generator behavior. Works by modulating the voltage of [generator], based on the [model].
 * */
class PhotovoltaicBehavior(cell: Cell, val generator: VRGeneratorObject<*>, val model: PhotovoltaicModel, normalSupplier: () -> Vector3d) : SolarIlluminationBehavior(cell) {
    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addSubscriber(SubscriberOptions(100, SubscriberPhase.Pre), this::update)
    }

    private fun update(dt: Double, phase: SubscriberPhase) {
        generator.updatePotential(model.voltageFunction.compute(this))
    }

    override val normal = normalSupplier()
}

class PhotovoltaicGeneratorCell(ci: CellCreateInfo, model: PhotovoltaicModel) : Cell(ci) {
    @SimObject
    @Inspect
    val generator = VRGeneratorObject<Cell>(
        this,
        directionPoleMapPlanar()
    ).also { it.potentialExact = 0.0 }

    @Behavior
    val photovoltaic = PhotovoltaicBehavior(this, generator, model) {
        locator.requireLocator<FaceLocator> {
            "Photovoltaic behavior requires a face locator"
        }.toVector3d() // temporary
    }

    init {
        ruleSet.withDirectionRulePlanar(Base6Direction3d.Front + Base6Direction3d.Back)
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
