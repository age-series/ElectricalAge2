@file:Suppress("MemberVisibilityCanBePrivate")

package org.ageseries.libage.sim

import org.ageseries.libage.data.*
import java.util.*
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.pow

/**
 * The Stefan-Boltzmann Constant, in W/m^2K^4, the proportionality constant of emission power of a black-body radiator.
 */
const val STEFAN_BOLTZMANN_CONSTANT: Double = 5.670373e-8

private const val MAX_THERMAL_CONDUCTANCE: Double = 1e10

/**
 * The number of steradians subtended by a sphere--the solid angle of a perfectly-isotropic transmitter.
 */
const val FULL_STERADIANS: Double = PI * 4

/**
 * Emission power of a surface with the given area at this temperature, in W.
 *
 * This isn't going to be equivalent to the "brightness", since one needs to take the spectral density and map it
 * through a "luminosity function". However, if one is feeling lazy, you can just equate this to lumens, and divide
 * through by [FULL_STERADIANS] to get candela.
 */
fun Quantity<Temperature>.emissionPower(surfaceArea: Double): Double = value.pow(4) * surfaceArea * STEFAN_BOLTZMANN_CONSTANT

/**
 * Emission color of a black body with this temperature.
 *
 * No correction for power is performed, so the result is always at maximum brightness. Any real use should consider
 * scaling brightness appropriately by [emissionPower].
 */
val Quantity<Temperature>.emissionColor: CIE.XYZ31 get() {
    val t = value.coerceIn(1000.0, 15000.0)
    val t2 = t * t

    val u1 = 0.860117757 + 1.54118254e-4 * t + 1.28641212e-7 * t2
    val u2 = 1 + 8.42420235e-4 * t + 7.08145163e-7 * t2
    val v1 = 0.317398726 + 4.22806245e-5 * t + 4.20481691e-8 * t2
    val v2 = 1f - 2.89741816e-5 * t + 1.61456063e-7 * t2

    return CIE.UVW60.fromuvY(
        u1 / u2,
        v1 / v2,
        1.0,
    ).asXYZ31
}

/**
 * "Standard" temperature, in Kelvin.
 *
 * This is the "ST" of STP, as reported in laboratory conditions.
 *
 * This is used as the default temperature for thermal masses.
 *
 * It is equal to 0 degrees Celsius.
 *
 * A little history: since the founding of IUPAC in 1919, 0 centigrade, or 273.15 Kelvin, has been the "standard
 * temperature" according to that organization--even though the "standard pressure" changed from 101.3kPa to 100kPa in
 * 1982 . Fortunately, IUPAC has been somewhat of a _de facto_ standard as of late, though it certainly isn't the only
 * organization to have made a standard, with, e.g. NIST at 20 centigrade, ICAO at 15 centigrade, the US EPA at 25
 * centigrade, and so forth. Current best practice is to document which "standard" is actually adhered to, so consider
 * this comment to declare our adherence to IUPAC 1982 :)
 */
val STANDARD_TEMPERATURE = Quantity(273.15, KELVIN)

/**
 * Represents a *homogenous mass* of [material].
 * @param material The material that best reflects the desired properties of this mass.
 * @param energy The initial energy. Leave `null` to set it such that [temperature] is [STANDARD_TEMPERATURE].
 * @param mass The mass of the body.
 * */
class ThermalMass(
    var material: Material,
    energy: Quantity<Energy>? = null,
    var mass: Quantity<Mass> = Quantity(1.0, KILOGRAM),
    var emissiveSurfaceArea: Quantity<Area> = Quantity(1.0, METER2),
    var emissivity: Double = 0.0
) {
    var energy: Quantity<Energy> = energy ?: Quantity(!STANDARD_TEMPERATURE * !mass * !material.specificHeat)

    /**
     * Gets the [Temperature] this mass would have if [ThermalMass.energy] were set to [energy].
     *
     * This does not mutate anything.
     * */
    fun temperatureAt(energy: Quantity<Energy>) = Quantity(!energy / !mass / !material.specificHeat, KELVIN)

    /**
     * The [Temperature] of this mass.
     *
     * Setting this changes the [energy].
     */
    var temperature: Quantity<Temperature>
        get() = temperatureAt(energy)
        set(value) { energy = Quantity(!value * !mass * !material.specificHeat, JOULE) }

    override fun toString() = "ThermalMass[$material, ${mass.classify()}, ${energy.classify()}, ${temperature.classify()}]"
}

/**
 * Factory for [ThermalMass] instances. Includes all information necessary to create a new instance.
 * */
data class ThermalMassDefinition(
    val material: Material,
    val energy: Quantity<Energy>? = null,
    val mass: Quantity<Mass> = Quantity(1.0, KILOGRAM),
    var emissiveSurfaceArea: Quantity<Area> = Quantity(1.0, METER2),
    var emissivity: Double = 0.0
) : Supplier<ThermalMass> {
    /**
     * Creates a new instance of [ThermalMass] with the properties specified in this definition.
     * */
    override fun get() = ThermalMass(material, energy, mass, emissiveSurfaceArea, emissivity)

    override fun toString() = "Def ${this()}"

    /**
     * Calls [get] to create a new instance of [ThermalMass].
     * I quite like this; I don't particularly appreciate *`.get()`* semantics for a factory.
     * */
    operator fun invoke() = get()
}

data class ConnectionParameters(
    /** Conductance to the environment with no distance effect. */
    val conductance: Quantity<ThermalConductance> = Quantity(1.0, WATT_PER_KELVIN),
    /** Conductance of the contact point between two masses with no distance effect. Infinite means ideal contact. */
    val contactConductance: Quantity<ThermalConductance> = Quantity(Double.POSITIVE_INFINITY, WATT_PER_KELVIN),
    /** How long the connection is between the two masses--which affects the transfer rate. */
    val distance: Quantity<Distance> = Quantity(1.0, METER),
    /** The "scale" of power conducted along this connection. If constant density is assumed, such that mass and volume are related, this is linear in contact area, thus the name. */
    val area: Double = 1.0, // Do we use SurfaceArea or nah?
    /** How far along the line segment from [MassConnection.a] to [MassConnection.b] the contact point is. Affects how much [MassConnection.a]'s conductance dominates over [MassConnection.b]'s. Keep this between 0.0 and 1.0 inclusive. Meaningless in [EnvironmentConnection]s. */
    val contactPoint: Double = 0.5,
    /**
     * Energy lost "to the environment" from this connection. Implements the Second Law, and has a direct impact on the stability of the simulation.
     */
    val efficiency: Double = 0.99,
) {
    override fun toString(): String =
        "<ConnParam (${contactPoint})${distance}m ${area}m^2 ${conductance}W/K contact=${contactConductance}W/K ~$efficiency>"

    companion object {
        val DEFAULT = ConnectionParameters()
    }
}

private val ZERO_TRANSFER: Pair<Quantity<Energy>, Quantity<Energy>> = Quantity(0.0, JOULE) to Quantity(0.0, JOULE)

private fun thermalResistance(length: Double, conductivity: Double, area: Double): Double = when {
    length <= 0.0 -> 0.0
    conductivity > 0.0 -> length / (conductivity * area)
    else -> Double.POSITIVE_INFINITY
}

private fun contactResistance(conductance: Double): Double = when {
    conductance == Double.POSITIVE_INFINITY -> 0.0
    conductance > 0.0 -> 1.0 / conductance
    else -> Double.POSITIVE_INFINITY
}

private fun conductanceFromResistance(resistance: Double): Double = when {
    resistance == 0.0 -> MAX_THERMAL_CONDUCTANCE
    resistance.isFinite() && resistance > 0.0 -> (1.0 / resistance).coerceAtMost(MAX_THERMAL_CONDUCTANCE)
    else -> 0.0
}

private fun heatCapacity(mass: ThermalMass): Double {
    val capacity = !mass.mass * !mass.material.specificHeat

    return if(capacity.isFinite() && capacity > 0.0) capacity else 0.0
}

private fun clampEnergyToEquilibrium(unrestrictedEnergy: Double, maxEnergy: Double): Double = when {
    !maxEnergy.isFinite() -> 0.0
    !unrestrictedEnergy.isFinite() -> maxEnergy
    maxEnergy > 0.0 -> unrestrictedEnergy.coerceIn(0.0, maxEnergy)
    maxEnergy < 0.0 -> unrestrictedEnergy.coerceIn(maxEnergy, 0.0)
    else -> 0.0
}

/**
 * Represents a connection between a [ThermalMass] and something else.
 *
 * The current implementations involve another ThermalMass and the environment.
 */
interface Connection {
    /**
     * Compute the heat transfer, in Joules, to do to each side of the connection over [dt] seconds.
     *
     * By convention, the returned energy transfers are in argument order; whereas the first is usually the
     * [ThermalMass], the second may or may not be relevant depending on whether there is a heat sink.
     */
    fun transfer(dt: Double): Pair<Quantity<Energy>, Quantity<Energy>>

    /**
     * [ThermalMass]es owned by this connection, so the [Simulator] can manage them.
     */
    val masses: List<ThermalMass>
}

class MassConnection(
    /** One of the connected masses. */
    val a: ThermalMass,
    /** The other connected mass. */
    val b: ThermalMass,
    /** Thermal parameters of this connection. */
    val params: ConnectionParameters = ConnectionParameters.DEFAULT,
): Connection {
    override fun toString() = "<MassConn $a $b $params>"

    /**
     * Returns the energies to add to [a] and [b] (in that order), which would attempt to equilibriate the connected thermal masses over the given period of time [dt] (in s).
     *
     * For this to be stable, [dt] must be held relatively small, since this is a linear approximation to an exponential curve--otherwise, overshoot may be observed.
     */
    override fun transfer(dt: Double): Pair<Quantity<Energy>, Quantity<Energy>> {
        if(dt <= 0.0 || !dt.isFinite() || params.area <= 0.0 || !params.area.isFinite()) {
            return ZERO_TRANSFER
        }

        val distance = !params.distance
        if(!distance.isFinite()) {
            return ZERO_TRANSFER
        }

        val clampedContactPoint = params.contactPoint.coerceIn(0.0, 1.0)
        val clampedDistance = distance.coerceAtLeast(0.0)
        val lengthA = clampedContactPoint * clampedDistance
        val lengthB = (1.0 - clampedContactPoint) * clampedDistance

        val resistanceA = thermalResistance(lengthA, !a.material.thermalConductivity, params.area)
        val resistanceB = thermalResistance(lengthB, !b.material.thermalConductivity, params.area)
        val resistanceContact = contactResistance(!params.contactConductance)
        val totalResistance = resistanceA + resistanceB + resistanceContact
        val overallCond = conductanceFromResistance(totalResistance)

        if(overallCond <= 0.0 || !overallCond.isFinite()) {
            return ZERO_TRANSFER
        }

        val deltaT = !b.temperature - !a.temperature
        if(!deltaT.isFinite()) {
            return ZERO_TRANSFER
        }

        val power = deltaT * overallCond
        val unrestrictedEnergy = power * dt

        val capacityA = heatCapacity(a)
        val capacityB = heatCapacity(b)
        if(capacityA <= 0.0 || capacityB <= 0.0) {
            return ZERO_TRANSFER
        }

        val maxEnergy = deltaT * (capacityA * capacityB) / (capacityA + capacityB)
        val energy = clampEnergyToEquilibrium(unrestrictedEnergy, maxEnergy)
        var toA = energy
        var toB = -energy

        if(toA > 0.0) {
            toA *= params.efficiency
        }

        if(toB > 0.0) {
            toB *= params.efficiency
        }

        return Quantity(toA, JOULE) to Quantity(toB, JOULE)
    }

    override val masses: List<ThermalMass>
        get() = listOf(a, b)
}

class EnvironmentConnection(
    /** The [ThermalMass] to affect. */
    val a: ThermalMass,
    /** The [Temperature] of the environment--assumed to have infinite energy. */
    var temperature: Quantity<Temperature>,
    /** The parameters of this contact. Not all fields are meaningful in this application. */
    val params: ConnectionParameters,
): Connection {
    override fun toString() = "<EnvConn $a $temperature $params>"

    override fun transfer(dt: Double): Pair<Quantity<Energy>, Quantity<Energy>> {
        if(dt <= 0.0 || !dt.isFinite() || params.area <= 0.0 || !params.area.isFinite()) {
            return ZERO_TRANSFER
        }

        val distance = !params.distance
        if(!distance.isFinite()) {
            return ZERO_TRANSFER
        }

        val lengthA = distance.coerceAtLeast(0.0)
        val resistanceA = thermalResistance(lengthA, !a.material.thermalConductivity, params.area)
        val resistanceLeak = contactResistance(!params.conductance)
        val totalResistance = resistanceA + resistanceLeak
        val overallCond = conductanceFromResistance(totalResistance)

        if(overallCond <= 0.0 || !overallCond.isFinite()) {
            return ZERO_TRANSFER
        }

        val deltaT = !temperature - !a.temperature
        if(!deltaT.isFinite()) {
            return ZERO_TRANSFER
        }

        val radiativePower = if (a.emissiveSurfaceArea > 0.0 && a.emissivity > 0.0) {
            val tEnv = !temperature
            val tMass = !a.temperature
            a.emissivity * STEFAN_BOLTZMANN_CONSTANT * !a.emissiveSurfaceArea * ((tEnv * tEnv) * (tEnv * tEnv) - (tMass * tMass) * (tMass * tMass))
        } else {
            0.0
        }

        val power = deltaT * overallCond + radiativePower
        val unrestrictedEnergy = power * dt

        val capacityA = heatCapacity(a)
        if(capacityA <= 0.0) {
            return ZERO_TRANSFER
        }

        val maxEnergy = deltaT * capacityA
        var energy = clampEnergyToEquilibrium(unrestrictedEnergy, maxEnergy)
        if(energy > 0.0) { energy *= params.efficiency }
        return Quantity(energy, JOULE) to Quantity(0.0, JOULE)
    }

    override val masses: List<ThermalMass>
        get() = listOf(a)
}

class Simulator {
    val connections = mutableSetOf<Connection>()
    val masses = mutableSetOf<ThermalMass>()
    val connectionMap = mutableMultiMapOf<ThermalMass, Connection>()

    /**
     * Add a body to the simulation.
     *
     * Without connection, this body will still conduct from/to the environment.
     */
    fun add(mass: ThermalMass) {
        masses.add(mass)
    }

    /**
     * Add a connection to the simulation.
     *
     * Generally, you would use [connect] instead.
     */
    fun add(connection: Connection) {
        connections.add(connection)
        connection.masses.forEach {
            add(it)
            connectionMap[it] = connection
        }
    }

    /**
     * Connect two [ThermalMass]es, with the given connection parameters for the underlying [Connection].
     *
     * The connection is automatically [add]ed to the simulation, as well as returned. The object can be used to [remove] it later.
     */
    fun connect(
        a: ThermalMass,
        b: ThermalMass,
        params: ConnectionParameters = ConnectionParameters.DEFAULT
    ): Connection =
        MassConnection(a, b, params).also {
            add(it)
        }

    /**
     * Connect a [ThermalMass] to the environment at a given temperature.
     *
     * The resulting connection is [add]ed and returned, so that it can be [remove]d later.
     */
    fun connect(
        a: ThermalMass,
        temperature: Quantity<Temperature>,
        params: ConnectionParameters = ConnectionParameters.DEFAULT,
    ): Connection =
        EnvironmentConnection(a, temperature, params).also {
            add(it)
        }

    /**
     * Remove the mass from the simulation.
     *
     * If this mass is involved in any [Connection]s, those connections will be removed as well. This can affect the flux on other masses.
     */
    fun remove(mass: ThermalMass) {
        masses.remove(mass)
        connectionMap[mass].toList().forEach {
            remove(it)
        }
    }

    /**
     * Remove the [Connection] from the simulation.
     *
     * This does not affect the membership of the underlying [ThermalBodies](ThermalBody).
     */
    fun remove(connection: Connection) {
        connections.remove(connection)
        connection.masses.forEach {
            connectionMap[it].remove(connection)
        }
    }

    /**
     * The "delta E" cache, a "double buffer" for the thermal step.
     *
     * This is held because the simulation can be run repeatedly in a given game loop, and it is best to keep it warm for that reason. Furthermore, while this could be stored on the [Body] itself, that would constitute an unnecessary implementation detail, subject to change.
     *
     * Keep this weak, so its persistence does not hold alive any ThermalMasses that have been removed.
     */
    private val deltaE = WeakHashMap<ThermalMass, Double>()

    /**
     * Run the simulation for a step.
     *
     * This does a discrete step of [dt] seconds on all bodies and connections in the simulation. Due to the approximations involved in the model (and the primitive integration used here), smaller step sizes are generally more stable.
     *
     * Flux between bodies (via [Connection]s) and with the environment are considered "simultaneously"--energy updates are deferred until the end.
     */
    fun step(dt: Double) {
        connections.forEach { connection ->
            val (toA, toB) = connection.transfer(dt)
            val masses = connection.masses

            masses.getOrNull(0)?.let { a ->
                deltaE[a] = deltaE.getOrDefault(a, 0.0) + !toA
            }

            masses.getOrNull(1)?.let { b ->
                deltaE[b] = deltaE.getOrDefault(b, 0.0) + !toB
            }
        }

        deltaE.entries.forEach { (mass, delta) ->
            mass.energy += Quantity(delta, JOULE)
        }

        deltaE.clear()
    }
}
