@file:Suppress("ObjectPropertyName")

package org.eln2.mc.scientific.chemistry

import org.eln2.mc.*
import org.eln2.mc.data.*
import org.eln2.mc.mathematics.approxEq
import java.util.*
import kotlin.math.min
import kotlin.math.pow

private enum class MolecularFormulaSymbolType { Group, Parenthesis }

private class MolecularFormulaSymbol(val type: MolecularFormulaSymbolType) {
    val composition = LinkedHashMap<ChemicalElement, Int>()

    fun insert(e: ChemicalElement, count: Int) =
        if (composition.containsKey(e)) composition[e] = composition[e]!! + count
        else composition[e] = count

    fun insert(b: MolecularFormulaSymbol) = b.composition.forEach { (k, v) -> this.insert(k, v) }
    fun fold(f: Int) = composition.forEach { (k, v) -> composition[k] = v * f }
}

private enum class MolecularFormulaTokenType {
    LeftParenthesis,
    RightParenthesis,
    Element,
    Multiplier,
    Eof
}

private data class MolecularFormulaToken(val type: MolecularFormulaTokenType, val data: Any?)

private data class MolecularFormulaLexer(val formula: String) {
    var index = 0
        private set

    val eof get() = index >= formula.length

    fun fetch(): MolecularFormulaToken {
        if (eof) return MolecularFormulaToken(MolecularFormulaTokenType.Eof, null)

        val char = formula[index++]

        if (char.isLetter) return MolecularFormulaToken(MolecularFormulaTokenType.Element, parseElement(char))
        else if (char.isDigitOrSubscriptDigit) return MolecularFormulaToken(
            MolecularFormulaTokenType.Multiplier,
            parseNumber(char)
        )
        else if (char == '(' || char == '[') return MolecularFormulaToken(
            MolecularFormulaTokenType.LeftParenthesis,
            null
        )
        else if (char == ')' || char == ']') return MolecularFormulaToken(
            MolecularFormulaTokenType.RightParenthesis,
            null
        )
        error("Unexpected $char at $index")
    }

    private fun parseNumber(current: Char): Int {
        val builder = StringBuilder(charDigitValue(current).toString())

        while (!eof) {
            val char = formula[index]

            if (char.isDigitOrSubscriptDigit) {
                builder.append(charDigitValue(char))
                index++
            } else {
                break
            }
        }

        return builder.toString().toIntOrNull() ?: error("Failed to parse number $builder")
    }

    private fun parseElement(current: Char): ChemicalElement {
        val prefix = current.toString()
        if (index >= formula.length) return getChemicalElement(prefix) ?: error("Unexpected element $prefix")
        val bi = prefix + formula[index]
        val biElement = getChemicalElement(bi)

        return if (biElement != null) {
            index++
            biElement
        } else getChemicalElement(prefix) ?: error("Unexpected element $bi")
    }
}

data class MolecularComposition(val components: Map<ChemicalElement, Int>, val label: String?) {
    constructor(components: Map<ChemicalElement, Int>) : this(components, null)

    val hash = components.hashCode()
    override fun hashCode() = hash

    val molecularWeight = components.keys.sumOf { it.A * components[it]!! }

    val effectiveZRough = let {
        val electrons = components.keys.sumOf { it.Z * components[it]!! }.toDouble()
        var dragon = 0.0
        components.forEach { (atom, count) ->
            dragon += atom.Z.toDouble().pow(2.94) * ((atom.Z * count).toDouble() / electrons)
        }
        dragon.pow(1.0 / 2.94)
    }

    fun thummel(e: Quantity<Energy>) =
        Quantity(
            15.2 * ((effectiveZRough.pow(3.0 / 4.0)) / molecularWeight) * (1.0 / (e..MeV).pow(1.485)),
            CM2_PER_G
        )

    fun constituentWeight(element: ChemicalElement): Double {
        val count = components[element] ?: error("Molecular composition does not have $element\n$this")

        return (count.toDouble() * element.A)
    }

    fun constituentWeightFraction(element: ChemicalElement) = constituentWeight(element) / molecularWeight

    operator fun plus(b: MolecularComposition) = MolecularComposition(
        LinkedHashMap<ChemicalElement, Int>().also { map ->
            this.components.forEach { (e, n) ->
                if (!map.containsKey(e)) map[e] = n
                else map[e] = map[e]!! + n
            }

            b.components.forEach { (e, n) ->
                if (!map.containsKey(e)) map[e] = n
                else map[e] = map[e]!! + n
            }
        }
    )

    operator fun not() = MolecularMassMixture(mapOf(this to 1.0))
    operator fun rangeTo(k: Double) = !this..k

    override fun toString(): String {
        if (label != null) {
            return label
        }

        val sb = StringBuilder()

        components.forEach { (element, count) ->
            sb.append(element.symbol)

            if (count != 1) {
                sb.append(count.toStringSubscript())
            }
        }

        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MolecularComposition

        if (components != other.components) return false

        return true
    }
}

infix fun MolecularComposition.x(n: Int) = MolecularComposition(components.mapValues { (_, count) -> count * n })

enum class ConstituentAnalysisType {
    MolecularMass,
    MolecularMassContribution
}

data class MolecularMassMixture(val massComposition: Map<MolecularComposition, Double>) {
    init {
        require(massComposition.isNotEmpty()) {
            "Empty weight composition"
        }

        val c = massComposition.values.sum()
        require(c.approxEq(1.0)) {
            "Weight composition $c doesn't add up:\n$this"
        }
    }

    fun atomicConstituentAnalysis(type: ConstituentAnalysisType): LinkedHashMap<ChemicalElement, Double> {
        val composition = HashMap<ChemicalElement, Double>()

        var mass = 0.0

        massComposition.keys.forEach { molecular ->
            molecular.components.keys.forEach { element ->
                val k = constituentWeight(molecular, element)
                if (!composition.containsKey(element)) composition[element] = k
                else composition[element] = composition[element]!! + k

                mass += k
            }
        }

        val results = LinkedHashMap<ChemicalElement, Double>()

        composition.keys.sortedBy { composition[it]!! }.forEach {
            results[it] = when (type) {
                ConstituentAnalysisType.MolecularMass -> composition[it]!!
                ConstituentAnalysisType.MolecularMassContribution -> composition[it]!! / mass
            }

        }

        return results
    }

    fun molecularConstituentAnalysis(type: ConstituentAnalysisType): LinkedHashMap<MolecularComposition, Double> {
        val results = LinkedHashMap<MolecularComposition, Double>()

        massComposition.keys.sortedBy { massComposition[it]!! }.forEach { x ->
            results[x] = when (type) {
                ConstituentAnalysisType.MolecularMass -> massComposition[x]!! * x.molecularWeight
                ConstituentAnalysisType.MolecularMassContribution -> massComposition[x]!!
            }
        }

        return results
    }

    operator fun rangeTo(k: Double) = this to k

    override fun toString(): String {
        val sb = StringBuilder()
        massComposition.keys.sortedByDescending { massComposition[it]!! }.forEach { element ->
            sb.append((massComposition[element]!!).formattedPercentN()).append(" ")
            sb.appendLine(element)
        }

        return sb.toString()
    }

    fun constituentWeight(composition: MolecularComposition, element: ChemicalElement): Double {
        val compositionWeight = massComposition[composition] ?: error("Mixture does not have $composition\n$this")
        return compositionWeight * composition.constituentWeight(element)
    }

    val hash = hashCode()

    companion object {
        fun normalize(massComposition: Map<MolecularComposition, Double>): LinkedHashMap<MolecularComposition, Double> {
            val result = LinkedHashMap<MolecularComposition, Double>()
            val n = massComposition.values.sum()

            massComposition.forEach { (e, w) ->
                result[e] = w / n
            }

            return result
        }
    }
}

operator fun Double.rangeTo(m: MolecularMassMixture) = m..this
operator fun Double.rangeTo(m: MolecularComposition) = !m..this

fun createSolution(
    components: List<Pair<MolecularMassMixture, Double>>,
    normalize: Boolean = true,
): MolecularMassMixture {
    var map = LinkedHashMap<MolecularComposition, Double>()

    components.forEach { (xq, xqWeight) ->
        xq.massComposition.forEach { (molecular, molecularWeight) ->
            if (!map.containsKey(molecular)) map[molecular] = molecularWeight * xqWeight
            else map[molecular] = map[molecular]!! + molecularWeight * xqWeight
        }
    }

    if (normalize) {
        map = MolecularMassMixture.normalize(map)
    }

    return MolecularMassMixture(map)
}

fun solutionOf(vararg components: Pair<MolecularMassMixture, Double>, normalize: Boolean = true) =
    createSolution(components.asList(), normalize)

fun percentageSolutionOf(vararg components: Pair<MolecularMassMixture, Double>, normalize: Boolean = true) =
    createSolution(
        components.asList().map { (a, b) -> a to (b / 100.0) }, normalize
    )

fun molecular(formula: String): MolecularComposition {
    val lexer = MolecularFormulaLexer(formula)
    val stack = ArrayList<MolecularFormulaSymbol>()

    while (true) {
        val token = lexer.fetch()

        when (token.type) {
            MolecularFormulaTokenType.LeftParenthesis -> {
                stack.add(MolecularFormulaSymbol(MolecularFormulaSymbolType.Parenthesis))
            }

            MolecularFormulaTokenType.RightParenthesis -> {
                val item = MolecularFormulaSymbol(MolecularFormulaSymbolType.Group)

                while (stack.last().type == MolecularFormulaSymbolType.Group) {
                    item.insert(stack.removeLast())
                }

                require(stack.removeLastOrNull()?.type == MolecularFormulaSymbolType.Parenthesis) {
                    error("Unexpected parenthesis at ${lexer.index}")
                }

                require(item.composition.isNotEmpty()) {
                    "Expected elements at ${lexer.index}"
                }

                stack.add(item)
            }

            MolecularFormulaTokenType.Element -> {
                stack.add(MolecularFormulaSymbol(MolecularFormulaSymbolType.Group).apply {
                    insert(token.data as ChemicalElement, 1)
                })
            }

            MolecularFormulaTokenType.Multiplier -> {
                val last = stack.removeLast()
                require(last.type == MolecularFormulaSymbolType.Group) { "Expected element group before ${lexer.index}" }

                last.fold((token.data as Int).also {
                    require(it > 0) { "Cannot fold group by 0 elements" }
                })

                stack.add(last)
            }

            MolecularFormulaTokenType.Eof -> break
        }
    }

    val evaluation = MolecularFormulaSymbol(MolecularFormulaSymbolType.Group)

    while (stack.isNotEmpty()) {
        val last = stack.removeLast()
        require(last.type == MolecularFormulaSymbolType.Group) {
            "Unexpected ${last.type}"
        }
        evaluation.insert(last)
    }

    return MolecularComposition(evaluation.composition.let {
        val ordered = LinkedHashMap<ChemicalElement, Int>()

        it.keys.reversed().forEach { k ->
            ordered.put(k, it[k]!!)
        }

        ordered
    }, formula)
}

val O2 = molecular("O₂")
val N2 = molecular("N₂")

val H2O = molecular("H₂O")
val water = H2O
val dihydrogenMonoxide = H2O
val CO = molecular("CO")
val carbonMonoxide = CO
val CO2 = molecular("CO₂")
val carbonDioxide = CO2
val N2O = molecular("N₂O")
val dinitrogenMonoxide = N2O
val nitrousOxide = N2O
val NO = molecular("NO")
val nitrogenMonoxide = NO
val nitricOxide = NO
val NO2 = molecular("NO₂")
val nitrogenDioxide = NO2
val N2O5 = molecular("N₂O₅")
val dinitrogenPentaoxide = N2O5
val P2O5 = molecular("P₂O₅")
val phosphorusPentaoxide = P2O5
val SO2 = molecular("SO₂")
val sulfurDioxide = SO2
val sulfurousOxide = SO2
val SO3 = molecular("SO₃")
val sulfurTrioxide = SO3
val sulfuricOxide = SO3
val CaO = molecular("CaO")
val calciumOxide = CaO
val lime = CaO
val MgO = molecular("MgO")
val magnesiumOxide = MgO
val K2O = molecular("K₂O")
val potassiumOxide = K2O
val Na2O = molecular("Na₂O")
val sodiumOxide = Na2O
val Al2O3 = molecular("Al₂O₃")
val aluminiumOxide = Al2O3
val alumina = Al2O3
val SiO2 = molecular("SiO₂")
val siliconDioxide = SiO2
val silica = SiO2
val ZnO = molecular("ZnO")
val zincOxide = ZnO
val Cu2O = molecular("Cu₂O")
val `Copper (I) Oxide` = Cu2O
val cuprousOxide = Cu2O
val CuO = molecular("CuO")
val `Copper (II) Oxide` = CuO
val cupricOxide = CuO
val FeO = molecular("FeO")
val `Iron (II) Oxide` = FeO
val ferrousOxide = FeO
val Fe2O3 = molecular("Fe₂O₃")
val `Iron (III) Oxide` = Fe2O3
val ferricOxide = Fe2O3
val CrO3 = molecular("CrO₃")
val chromiumTrioxide = CrO3
val `Chromium (VI) Oxide` = CrO3
val MnO2 = molecular("MnO₂")
val manganeseDioxide = MnO2
val `Manganese (IV) Oxide` = MnO2
val Mn2O7 = molecular("Mn₂O₇")
val dimanganeseHeptoxide = Mn2O7
val manganeseHeptoxide = Mn2O7
val `Manganese (VII) Oxide` = Mn2O7
val TiO2 = molecular("TiO₂")
val titaniumDioxide = TiO2
val H2O2 = molecular("H₂O₂")
val hydrogenPeroxide = H2O2

val NaOH = molecular("NaOH")
val sodiumHydroxide = NaOH
val KOH = molecular("KOH")
val potassiumHydroxide = KOH
val `Ca(OH)2` = molecular("Ca(OH)₂")
val calciumHydroxide = `Ca(OH)2`
val `Ba(OH)2` = molecular("Ba(OH)₂")
val bariumHydroxide = `Ba(OH)2`
val `Al(OH)3` = molecular("Al(OH)₃")
val aluminiumHydroxide = `Al(OH)3`
val `Fe(OH)2` = molecular("Fe(OH)₂")
val `iron (II) Hydroxide` = `Fe(OH)2`
val ferrousHydroxide = `Fe(OH)2`
val `Fe(OH)3` = molecular("Fe(OH)₃")
val `iron (III) Hydroxide` = `Fe(OH)3`
val ferricHydroxide = `Fe(OH)3`
val `Cu(OH)2` = molecular("Cu(OH)₂")
val copperHydroxide = `Cu(OH)2`
val NH4OH = molecular("NH₄OH")
val ammoniumHydroxide = NH4OH
val aqueousAmmonia = NH4OH

val HF = molecular("HF")
val hydrofluoricAcid = HF
val hydrogenFluoride = HF
val HCl = molecular("HCl")
val hydrochloricAcid = HCl
val hydrogenChloride = HCl
val HBr = molecular("HBr")
val hydrobromicAcid = HBr
val hydrogenBromide = HBr
val HI = molecular("HI")
val hydroiodicAcid = HI
val hydrogenIodide = HI
val HCN = molecular("HCN")
val hydrocyanicAcid = HCN
val hydrogenCyanide = HCN
val H2S = molecular("H₂S")
val hydrosulfuricAcid = H2S
val hydrogenSulfide = H2S
val H3BO3 = molecular("H₃BO₃")
val boricAcid = H3BO3
val H2CO3 = molecular("H₂CO₃")
val carbonicAcid = H2CO3
val HOCN = molecular("HOCN")
val cyanicAcid = HOCN
val HSCN = molecular("HSCN")
val thiocyanicAcid = HSCN
val HNO2 = molecular("HNO₂")
val nitrousAcid = HNO2
val HNO3 = molecular("HNO₃")
val nitricAcid = HNO3
val H3PO4 = molecular("H₃PO₄")
val phosphoricAcid = H3PO4
val H2SO3 = molecular("H₂SO₃")
val sulfurousAcid = H2SO3
val H2SO4 = molecular("H₂SO₄")
val sulfuricAcid = H2SO4
val H2S2O3 = molecular("H₂S₂O₃")
val thiosulfuricAcid = H2S2O3
val HClO = molecular("HClO")
val hypochlorousAcid = HClO
val HClO2 = molecular("HClO₂")
val chlorousAcid = HClO2
val HClO3 = molecular("HClO₃")
val chloricAcid = HClO3
val HClO4 = molecular("HClO₄")
val perchloricAcid = HClO4
val H2CrO4 = molecular("H₂CrO₄")
val chromicAcid = H2CrO4
val H2Cr2O7 = molecular("H₂Cr₂O₇")
val dichromicAcid = H2Cr2O7
val HMnO4 = molecular("HMnO₄")
val permanganicAcid = HMnO4

val `Al2Si2O2(OH)2` = molecular("Al₂Si₂O₅(OH)₄")
val kaolinite = `Al2Si2O2(OH)2`

val C6H10O5 = molecular("C₆H₁₀O₅")
val celluloseUnit = C6H10O5

val C18H13N3Na2O8S2 = molecular("C₁₈H₁₃N₃Na₂O₈S₂")
val ligninUnit = C18H13N3Na2O8S2

val C24H42O21 = molecular("C₂₄H₄₂O₂₁")
val glucomannanUnit = C24H42O21
val glucomannoglycanUnit = C24H42O21

val C21H33O19 = molecular("C₂₁H₃₃O₁₉")
val glucuronoxylanDGlucuronate = C21H33O19

val C6H12O = molecular("C₆H₁₂O₆")
val glucose = C6H12O

val C2H5OH = molecular("C₂H₅OH")
val C2H6O = C2H5OH
val ethanol = C2H5OH

val CH3COOH = molecular("CH₃COOH")
val aceticAcid = CH3COOH

// ₁ ₂ ₃ ₄ ₅ ₆ ₇ ₈ ₉ ₁₀

data class KnownPhysicalProperties(
    val density: Quantity<Density>,
)

private val knownProperties = mapOf(
    H2O to KnownPhysicalProperties(Quantity(1.0, G_PER_CM3)),
    SiO2 to KnownPhysicalProperties(Quantity(2.65, G_PER_CM3))
)

val MolecularComposition.properties
    get() = knownProperties[this]
        ?: error("Molecular composition $this does not have known properties")

class MassContainer {
    val content = HashMap<MolecularComposition, Quantity<Substance>>()

    operator fun get(m: MolecularComposition) = content[m] ?: Quantity(0.0)
    operator fun set(m: MolecularComposition, value: Quantity<Substance>) {
        content[m] = value
    }

    fun getMass(m: MolecularComposition) = (this[m] * m.molecularWeight).reparam<Mass>()
    fun addMass(m: MolecularComposition, value: Quantity<Mass>) {
        this[m] += (value / m.molecularWeight).reparam()
    }

    fun removeMass(m: MolecularComposition, value: Quantity<Mass>) = addMass(m, -value)
    fun setMass(m: MolecularComposition, value: Quantity<Mass>) {
        this[m] = (value / m.molecularWeight).reparam()
    }

    override fun toString(): String {
        val sb = StringBuilder()

        content.keys.sortedByDescending { !content[it]!! }.forEach { composition ->
            sb.append(composition).append(": ").appendLine(valueText(!content[composition]!!, UnitType.MOLE))
        }

        return sb.toString()
    }
}

// What I really want is a proper physics simulation (actual reactions using datasets, real equations, molecular structure, simulations, etc.)
// That is difficult to pull off. Maybe with external support, we'll get around to doing that.
// Currently, we have a crude (rather trashy) system of representing molecules (we don't have any structural information, only molecular formulas)
// This is probably enough for the game

data class ChemicalEquationTermInfo(
    val elementsIn: Map<ChemicalElement, Int>,
    val elementsOut: Map<ChemicalElement, Int>,
    val isBalanced: Boolean,
)

// No quantity because just using integers as coefficients is easier than dealing with fractional terms
data class ChemicalEquation(val lhs: Map<MolecularComposition, Int>, val rhs: Map<MolecularComposition, Int>) {
    fun analyze(): ChemicalEquationTermInfo {
        val elementsIn = HashMap<ChemicalElement, Int>()
        val elementsOut = HashMap<ChemicalElement, Int>()

        fun scan(m: HashMap<ChemicalElement, Int>, e: Map<MolecularComposition, Int>) {
            e.forEach { (comp, c1) ->
                comp.components.forEach { (element, c2) ->
                    m.addIncr(element, c1 * c2)
                }
            }
        }

        scan(elementsIn, lhs)
        scan(elementsOut, rhs)

        return ChemicalEquationTermInfo(
            elementsIn,
            elementsOut,
            elementsIn == elementsOut
        )
    }
}

fun reaction(equation: String): ChemicalEquation {
    fun parseSide(side: String): LinkedHashMap<MolecularComposition, Int> {
        val terms = side.split("+").map { it.trim() }
        require(terms.isNotEmpty())

        val results = LinkedHashMap<MolecularComposition, Int>()

        terms.forEach {
            require(!it.isEmpty())

            var i = 0
            val coefficientSb = StringBuilder()
            while (i < it.length) {
                val c = it[i]

                if (!c.isDigit) {
                    break
                }

                i++

                coefficientSb.append(c)
            }

            require(i < it.length)

            val coefficient = if (coefficientSb.isEmpty()) 1 else coefficientSb.toString().toInt()
            val comp = molecular(it.substring(i).trim())

            require(results.put(comp, coefficient) == null)
        }

        return results
    }

    val s = equation.split("->")
    require(s.size == 2)

    return ChemicalEquation(
        parseSide(s[0]),
        parseSide(s[1])
    )
}

fun MassContainer.applyReaction(equation: ChemicalEquation, rate: Double) {
    var u = rate

    equation.lhs.forEach { (composition, count) -> u = min(u, !this[composition] / count.toDouble()) }
    equation.lhs.forEach { (c, x) -> this[c] -= Quantity(u * x) }
    equation.rhs.forEach { (c, x) -> this[c] += Quantity(u * x) }
}

val glucoseFermentationReaction = reaction("C₆H₁₂O₆ -> 2 C₂H₅OH + 2 CO₂")
val aceticFermentationReaction = reaction("C₂H₅OH + O₂ -> CH₃COOH + H₂O")

val airMix = percentageSolutionOf(
    75.52..!!ChemicalElement.Nitrogen,
    23.14..!!ChemicalElement.Oxygen,
    1.29..!!ChemicalElement.Argon,
    0.051..CO2,
)

val clayMix = percentageSolutionOf(
    41.9..SiO2,
    22.3..Al2O3,
    11.1..CaO,
    8.0..Fe2O3,
    4.1..K2O,
    3.4..MgO,
    2.8..SO3,
    0.9..TiO2
)

val stoneMix = percentageSolutionOf(
    72.04..SiO2,
    14.42..Al2O3,
    4.12..K2O,
    3.69..Na2O,
    1.82..CaO,
    1.68..FeO,
    1.22..Fe2O3,
    0.71..MgO,
    0.30..TiO2,
    0.12..P2O5,
)

val ktSoilMix = percentageSolutionOf(
    46.35..SiO2,
    20.85..Al2O3,
    2.19..TiO2,
    2.06..Fe2O3,
    1.79..CaO,
    1.79..K2O,
    0.22..MgO,
    1.97..percentageSolutionOf(
        0.5..Na2O,
        0.5..K2O
    )
)

// other polysaccharides and structure not covered (not worth it)

val pinusSylvestrisMix = percentageSolutionOf(
    40.0..celluloseUnit,
    28.0..ligninUnit,
    16.0..glucomannoglycanUnit,
    9.0..glucuronoxylanDGlucuronate,
)

val piceaGlaucaMix = percentageSolutionOf(
    39.5..celluloseUnit,
    27.5..ligninUnit,
    17.2..glucomannoglycanUnit,
    10.4..glucuronoxylanDGlucuronate,
)

val betulaVerrucosaMix = percentageSolutionOf(
    41.1..celluloseUnit,
    22.0..ligninUnit,
    2.3..glucomannoglycanUnit,
    27.5..glucuronoxylanDGlucuronate,
)

val quercusFagaceaeMix = percentageSolutionOf(
    46.1..celluloseUnit,
    22.5..ligninUnit,
    14.2..glucomannoglycanUnit,
    12.4..glucuronoxylanDGlucuronate,
)

val sodaLimeGlassMix = percentageSolutionOf(
    73.1..SiO2,
    15.0..Na2O,
    7.0..CaO,
    4.1..MgO,
    1.0..Al2O3
)

val obsidianSpecimenMix = percentageSolutionOf(
    75.48..SiO2,
    11.75..Al2O3,
    3.47..Na2O,
    0.1..MgO,
    0.05..P2O5,
    5.41..K2O,
    0.9..CaO,
    0.1..TiO2,
    2.87..Fe2O3
)

enum class MolecularBondType { Disjoint, S, D, T, Q, Aromatic }
data class MolecularBond(val type: MolecularBondType)
data class MolecularGraphNode(val atom: ChemicalElement, val index: Int)
data class MolecularGraph(val graph: Graph<MolecularGraphNode, MolecularBond>)
