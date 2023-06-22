package org.eln2.mc.scientific

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.eln2.mc.data.*
import org.eln2.mc.getResourceString
import org.eln2.mc.mathematics.*
import org.eln2.mc.resource
import org.eln2.mc.scientific.chemistry.ChemicalElement
import org.eln2.mc.scientific.chemistry.ConstituentAnalysisType
import org.eln2.mc.scientific.chemistry.MolecularMassMixture
import org.eln2.mc.sumOfDual
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

// If you are using the data without the game running, set this to false
private const val USE_GAME = true

val xcomDataset = Json.decodeFromString<XCOMDataset>(
    if (USE_GAME)
        getResourceString(resource("datasets/xcom/pcs.json"))
    else
        Files.readString(Path("./src/main/resources/assets/eln2/datasets/xcom/pcs.json")),
)

fun fetchElementNode(z: Int): ElementNode {
    if (z < 0 || z > 100) {
        error("XCOM data $z out of range")
    }

    return xcomDataset.nodes[(if (z < 10) "Z00$z" else {
        if (z < 100) "Z0$z" else "Z$z"
    })]!!
}

fun fetchElementNode(e: ChemicalElement) = fetchElementNode(e.Z)

@Serializable
data class XCOMDataset(
    val nodes: Map<String, ElementNode>,
)

@Serializable
data class ElementNode(val absorptionEdge: XCOMElementAbsorptionEdgeNode, val entries: List<XCOMElementDataNode>) {
    @Transient
    val orderedEntries = entries.sortedBy { it.energy }

    @Transient
    val energyRangeEv = entries.minOf { it.energy }..entries.maxOf { it.energy }

    @Transient
    val energyRange = Quantity(energyRangeEv.start, eV)..Quantity(energyRangeEv.endInclusive, eV)

    @Transient
    val coherentProcess = lnRemapScattering(orderedEntries.map { it.energy to it.coherent })

    @Transient
    val incoherentProcess = lnRemapScattering(orderedEntries.map { it.energy to it.incoherent })

    @Transient
    val photoelectricProcess = lnRemapScattering(orderedEntries.map { it.energy to it.photoelectric })

    @Transient
    val nuclearFieldPairProductionProcess =
        lnRemapPairProduction(orderedEntries.map { it.energy to it.pairAtom }, 1.022007e6)

    @Transient
    val electronFieldPairProductionProcess =
        lnRemapPairProduction(orderedEntries.map { it.energy to it.pairElectron }, 2.044014e6)

    companion object {
        private fun lnRemapScattering(entries: List<Pair<Double, Double>>): InterpolationFunctionDual<Quantity<Energy>, Double, Dual> {
            val builder = InterpolatorBuilder()

            entries.forEach { builder.with(ln(it.first), ln(it.second)) }

            return builder
                .buildCubic()
                .reparamV { ln(it) }
                .reordV({ exp(it) }) { exp(it) }
                .reparamU { it..eV }
        }

        private fun lnRemapPairProduction(
            entries: List<Pair<Double, Double>>,
            pairProductionThreshold: Double,
        ): InterpolationFunctionDual<Quantity<Energy>, Double, Dual> {
            val cs = InterpolatorBuilder().apply {
                var x = entries.map { it.first }.toDoubleArray()
                var y = entries.map { it.second }.toDoubleArray()

                x.indices.forEach { i ->
                    x[i] = (1.0 - (x[i] / pairProductionThreshold)).pow(3)
                }

                val mask = y.map { it > 0.0 }.toBooleanArray()
                x = x.filterIndexed { i, _ -> mask[i] }.reversed().toDoubleArray()
                y = y.filterIndexed { i, _ -> mask[i] }.map { ln(it) }.reversed().toDoubleArray()

                x.zip(y).forEach(::with)
            }.buildCubic(t = -0.1)

            return object : InterpolationFunctionDual<Quantity<Energy>, Double, Dual> {
                fun reparam(t: Quantity<Energy>) = (1.0 - ((t..eV) / pairProductionThreshold)).pow(3)

                override fun evaluate(t: Quantity<Energy>): Double {
                    val u = reparam(t)

                    if (u > 0.0) {
                        return 0.0
                    }

                    return exp(cs.evaluate(u))
                }

                override fun evaluateDual(t: Quantity<Energy>, n: Int): Dual {
                    val u = reparam(t)

                    if (u > 0.0) {
                        return Dual.const(0.0, n)
                    }

                    return exp(cs.evaluateDual(u, n))
                }
            }
        }


    }
}

@Serializable
data class XCOMElementDataNode(
    val energy: Double,
    val coherent: Double,
    val incoherent: Double,
    val photoelectric: Double,
    val pairAtom: Double,
    val pairElectron: Double,
) {
    @Transient
    val energyQuantity = Quantity(energy, eV)
}

@Serializable
data class XCOMElementAbsorptionEdgeNode(
    val layers: Map<String, List<XCOMAbsorptionEdgeLayerNode>>,
    val info: XCOMAbsorptionEdgeInfoNode,
)

@Serializable
data class XCOMAbsorptionEdgeInfoNode(
    val entries: List<XCOMAbsorptionEdgeLayerInfoNode>,
)

@Serializable
data class XCOMAbsorptionEdgeLayerNode(
    val energy: Double,
    val photoelectric: Double,
)

@Serializable
data class XCOMAbsorptionEdgeLayerInfoNode(
    val index: Int,
    val name: String,
    val edge: Double,
)

// We seriously cannot represent these numbers in SI using Double, so no Quantity<T> for results. Maybe defining a separate units with AMU, Barns, Angstrom, Electron Volts would be appropriate?
// Electron volts haven't given me much trouble (so far).
fun computePhotonCrossSectionBarns(element: ChemicalElement, e: Quantity<Energy>): Double {
    val node = fetchElementNode(element.Z)

    return node.coherentProcess.evaluate(e) +
        node.incoherentProcess.evaluate(e) +
        node.photoelectricProcess.evaluate(e) + // without edge energy phenomena, already a bit overkill for a quick game of Minecraft
        node.nuclearFieldPairProductionProcess.evaluate(e) +
        node.electronFieldPairProductionProcess.evaluate(e)
}

fun computePhotonCrossSectionBarnsDual(element: ChemicalElement, e: Quantity<Energy>, n: Int = 1): Dual {
    val node = fetchElementNode(element.Z)

    return node.coherentProcess.evaluateDual(e, n) +
        node.incoherentProcess.evaluateDual(e, n) +
        node.photoelectricProcess.evaluateDual(
            e,
            n
        ) + // without edge energy phenomena, already a bit overkill for a quick game of Minecraft
        node.nuclearFieldPairProductionProcess.evaluateDual(e, n) +
        node.electronFieldPairProductionProcess.evaluateDual(e, n)
}

private const val NA = 0.60221367

fun computeMassAttenuation(solution: MolecularMassMixture, e: Quantity<Energy>): Quantity<ReciprocalArealDensity> {
    val composition = solution.atomicConstituentAnalysis(ConstituentAnalysisType.MolecularMassContribution)
    return Quantity(
        composition.keys.sumOf {
            computePhotonCrossSectionBarns(it, e) * NA / it.A * composition[it]!!
        },
        CM2_PER_G
    )
}

fun computeMassAttenuationDual(
    solution: MolecularMassMixture,
    e: Quantity<Energy>,
    n: Int = 1,
): QuantityDual<ReciprocalArealDensity> {
    val composition = solution.atomicConstituentAnalysis(ConstituentAnalysisType.MolecularMassContribution)
    return QuantityDual(
        composition.keys.sumOfDual(n) {
            computePhotonCrossSectionBarnsDual(it, e, n) * NA / it.A * composition[it]!!
        },
        CM2_PER_G
    )
}

fun computeLinearAttenuation(solution: MolecularMassMixture, e: Quantity<Energy>, ro: Quantity<Density>) =
    Quantity(!computeMassAttenuation(solution, e) * !ro, RECIP_METER)

fun computeLinearAttenuationDual(
    solution: MolecularMassMixture,
    e: Quantity<Energy>,
    ro: Quantity<Density>,
    n: Int = 1,
) =
    QuantityDual(!computeMassAttenuationDual(solution, e, n) * !ro, RECIP_METER)
