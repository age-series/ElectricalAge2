@file:Suppress("ObjectPropertyName")

package org.eln2.mc.scientific.chemistry

import org.eln2.mc.*
import org.eln2.mc.data.*
import org.eln2.mc.mathematics.approxEq
import org.eln2.mc.mathematics.nanZero
import org.eln2.mc.scientific.chemistry.data.ChemicalElement
import org.eln2.mc.scientific.chemistry.data.getChemicalElement
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.math.min
import kotlin.math.pow

typealias CompoundContainer = MassContainer<StructuredMolecularCompound>

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

interface AtomicComposite {
    val components: Map<ChemicalElement, Int>
}

data class MolecularComposition(override val components: Map<ChemicalElement, Int>, val label: String?) : AtomicComposite {
    constructor(components: Map<ChemicalElement, Int>) : this(components, null)

    val hash = components.hashCode()

    override fun hashCode() = hash

    val molecularWeight = components.keys.sumOf { it.A * components[it]!! }

    val formula = let {
        if(label != null) {
            label
        }
        else {
            val components = this.components.toMutableMap()
            val sb = StringBuilder()

            fun appendElement(element: ChemicalElement, count: Int) {
                sb.append(element.symbol)

                if (count != 1) {
                    sb.append(count.toStringSubscript())
                }
            }

            components.remove(ChemicalElement.Carbon)?.also { c -> appendElement(ChemicalElement.Carbon, c) }
            components.remove(ChemicalElement.Hydrogen)?.also { c -> appendElement(ChemicalElement.Hydrogen, c) }

            components.keys.sortedBy { it.symbol }.forEach { element ->
                appendElement(element, components[element]!!)
            }

            sb.toString()
        }
    }

    val effectiveZApprox = let {
        val electrons = components.keys.sumOf { it.Z * components[it]!! }.toDouble()
        var dragon = 0.0
        components.forEach { (atom, count) ->
            dragon += atom.Z.toDouble().pow(2.94) * ((atom.Z * count).toDouble() / electrons)
        }
        dragon.pow(1.0 / 2.94)
    }

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

    override fun toString() = formula

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MolecularComposition
        if (hash != other.hash) return false
        return components == other.components
    }
}

infix fun MolecularComposition.x(n: Int) = MolecularComposition(components.mapValues { (_, count) -> count * n })

data class CrystalComposition(val molecularComponents: Map<MolecularComposition, Int>) : AtomicComposite {
    constructor(c: MolecularComposition) : this(mapOf(c to 1))

    override val components = let {
        val results = LinkedHashMap<ChemicalElement, Int>()

        molecularComponents.forEach { (m, c1) ->
            m.components.forEach { (e, c2) ->
                results.addIncr(e, c1 * c2)
            }
        }

        results
    }

    operator fun not(): MolecularMassMixture {
        val weights = molecularComponents.mapValues { (comp, cnt) -> cnt * comp.molecularWeight }
        val total = weights.values.sum()

        val results = LinkedHashMap<MolecularComposition, Double>()

        molecularComponents.keys.forEach { c ->
            results[c] = weights[c]!! / total
        }

        return MolecularMassMixture(results)
    }

    companion object {
        fun hydrate(c: MolecularComposition, n: Int) = CrystalComposition(
            linkedMapOf(c to 1, H2O to n)
        )
    }
}

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

fun createSolution(components: List<Pair<MolecularMassMixture, Double>>, normalize: Boolean = true) : MolecularMassMixture {
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

fun molecularCrystallographic(formula: String, tsp: Char = '·'): CrystalComposition {
    val tokens = formula.split(tsp)

    if(tokens.size > 2) error("Unhandled uniform molecular crystallographic formula $formula")
    else if(tokens.size == 1) return CrystalComposition(molecular(tokens[0]))

    val principled = molecular(tokens[0])

    val coeffSb = StringBuilder()

    var i = 0
    val completedTk = tokens[1]
    while (true) {
        val c = completedTk[i]

        if(!c.isDigit()) {
            break
        }

        i++

        coeffSb.append(c)
    }

    val coeff = if(coeffSb.isEmpty()) 1 else coeffSb.toString().toInt()

    val completed = molecular(completedTk.drop(i))

    return CrystalComposition(
        mapOf(
            principled to 1,
            completed to coeff
        )
    )
}

// Molecular compositions that are sometimes useful in mixes (for the rad system):
val H2O = molecular("H₂O")
val CO = molecular("CO")
val CO2 = molecular("CO₂")
val N2O = molecular("N₂O")
val NO = molecular("NO")
val NO2 = molecular("NO₂")
val N2O5 = molecular("N₂O₅")
val P2O5 = molecular("P₂O₅")
val SO2 = molecular("SO₂")
val SO3 = molecular("SO₃")
val CaO = molecular("CaO")
val MgO = molecular("MgO")
val K2O = molecular("K₂O")
val Na2O = molecular("Na₂O")
val Al2O3 = molecular("Al₂O₃")
val SiO2 = molecular("SiO₂")
val ZnO = molecular("ZnO")
val Cu2O = molecular("Cu₂O")
val CuO = molecular("CuO")
val FeO = molecular("FeO")
val Fe2O3 = molecular("Fe₂O₃")
val CrO3 = molecular("CrO₃")
val MnO2 = molecular("MnO₂")
val Mn2O7 = molecular("Mn₂O₇")
val TiO2 = molecular("TiO₂")
val H2O2 = molecular("H₂O₂")
val NaOH = molecular("NaOH")
val KOH = molecular("KOH")
val `Ca(OH)2` = molecular("Ca(OH)₂")
val `Ba(OH)2` = molecular("Ba(OH)₂")
val `Al(OH)3` = molecular("Al(OH)₃")
val `Fe(OH)2` = molecular("Fe(OH)₂")
val `Fe(OH)3` = molecular("Fe(OH)₃")
val `Cu(OH)2` = molecular("Cu(OH)₂")
val NH4OH = molecular("NH₄OH")
val HF = molecular("HF")
val HCl = molecular("HCl")
val HBr = molecular("HBr")
val HI = molecular("HI")
val HCN = molecular("HCN")
val H2S = molecular("H₂S")
val H3BO3 = molecular("H₃BO₃")
val H2CO3 = molecular("H₂CO₃")
val HOCN = molecular("HOCN")
val HSCN = molecular("HSCN")
val HNO2 = molecular("HNO₂")
val HNO3 = molecular("HNO₃")
val H3PO4 = molecular("H₃PO₄")
val H2SO3 = molecular("H₂SO₃")
val H2SO4 = molecular("H₂SO₄")
val H2S2O3 = molecular("H₂S₂O₃")
val HClO = molecular("HClO")
val HClO2 = molecular("HClO₂")
val HClO3 = molecular("HClO₃")
val HClO4 = molecular("HClO₄")
val H2CrO4 = molecular("H₂CrO₄")
val H2Cr2O7 = molecular("H₂Cr₂O₇")
val HMnO4 = molecular("HMnO₄")
val `Al2Si2O2(OH)2` = molecular("Al₂Si₂O₅(OH)₄")
val C6H10O5 = molecular("C₆H₁₀O₅")
val celluloseUnit = C6H10O5
val C18H13N3Na2O8S2 = molecular("C₁₈H₁₃N₃Na₂O₈S₂")
val ligninUnit = C18H13N3Na2O8S2
val C24H42O21 = molecular("C₂₄H₄₂O₂₁")
val C21H33O19 = molecular("C₂₁H₃₃O₁₉")
val C6H12O = molecular("C₆H₁₂O₆")
val C2H5OH = molecular("C₂H₅OH")
val CH3COOH = molecular("CH₃COOH")
val CCl4 = molecular("CCl₄")

data class CompoundProperties(
    val densitySTP: Quantity<Density>
)

interface Compound {
    val molecularWeight: Double
    val properties: CompoundProperties
}

data class UnstructuredMolecularCompound(val composition: MolecularComposition, override val properties: CompoundProperties) : Compound, AtomicComposite {
    override val molecularWeight by composition::molecularWeight
    override val components by composition::components
}

class MassContainer<C : Compound>(val content: HashMap<C, Quantity<Substance>>) {
    constructor() : this(HashMap<C, Quantity<Substance>>())

    val total get() = Quantity(content.values.sumOf { !it }, MOLE)

    fun clear() = content.clear()

    fun isEmpty() = content.isEmpty()
    fun isNotEmpty() = content.isNotEmpty()

    fun trim(c: C, eps: Double = 10e-8) {
        if(this[c] < eps) {
            content.remove(c)
        }
    }

    operator fun get(m: C) = content[m] ?: Quantity(0.0)
    operator fun set(m: C, value: Quantity<Substance>) {
        content[m] = value
    }

    fun getMass(m: C) = (this[m] * m.molecularWeight).reparam<Mass>()
    fun addMass(m: C, value: Quantity<Mass>) { this[m] += (value / m.molecularWeight).reparam() }
    fun removeMass(m: C, value: Quantity<Mass>) = addMass(m, -value)
    fun setMass(m: C, value: Quantity<Mass>) { this[m] = (value / m.molecularWeight).reparam() }
    fun setVolumeSTP(m: C, value: Quantity<Volume>) = setMass(m, (m.properties.densitySTP * !value).reparam())
    fun getVolumeSTP(m: C) = Quantity(!getMass(m) / !m.properties.densitySTP, M3)
    val mass get() = Quantity(content.keys.sumOf { !getMass(it) }, KILOGRAMS)
    val volumeSTP get() = Quantity(content.keys.sumOf { !getVolumeSTP(it) }, M3)
    fun massConcentrationSTP(c: C) = Quantity(!getMass(c) / !this.volumeSTP, KG_PER_M3)
    fun molarConcentrationSTP(c: C) = Quantity(!this[c] / !this.volumeSTP, MOLE_PER_M3)
    fun volumeConcentrationSTP(c: C) = !getVolumeSTP(c) / !this.volumeSTP
    fun molarFraction(c: C) = (get(c) / this.total).nanZero()
    fun massFractionSTP(c: C) = getMass(c) / this.mass
    fun bind() = MassContainer(content.bind())
    fun trimAll() { content.keys.toList().forEach(::trim) }

    fun normalizeMolar() {
        if(this.isEmpty()) {
            return
        }

        val q = content.values.sumOf { !it }

        content.keys.forEach { k ->
            this[k] = this[k] / q
        }
    }

    fun normalizeVolumeSTP() {
        if(this.isEmpty()) {
            return
        }

        val q = this.volumeSTP

        content.keys.forEach { k ->
            this.setMass(k, Quantity((getVolumeSTP(k) / q) * !k.properties.densitySTP))
        }
    }

    fun add(c: MassContainer<C>) {
        c.content.forEach { (m, q) ->
            this[m] += q
        }
    }

    fun remove(c: MassContainer<C>) {
        c.content.forEach { (m, q) ->
            this[m] -= q
        }
    }

    operator fun plus(b: MassContainer<C>) = this.bind().apply { add(b) }
    operator fun plusAssign(b: MassContainer<C>) { this.add(b) }

    fun scaleMolar(scalar: Double) {
        content.keys.forEach {
            this[it] = this[it] * scalar
        }
    }

    fun scaleVolumeSTP(scalar: Double) {
        content.keys.forEach {
            this.setVolumeSTP(it, this.getVolumeSTP(it) * scalar)
        }
    }

    fun normalizedMolar() = this.bind().apply { normalizeMolar() }
    fun normalizedVolume() = this.bind().apply { normalizeVolumeSTP() }

    fun transferVolumeSTPFrom(source: MassContainer<C>, maxTransferV: Quantity<Volume>, removeFromSource: Boolean = true) {
        if(source.content.isEmpty()) {
            return
        }

        val v = min(maxTransferV, source.volumeSTP)

        if((!v).approxEq(0.0)) {
            return
        }

        val dx = source.normalizedVolume().apply {
            scaleVolumeSTP(!v)
        }

        this.add(dx)

        if(removeFromSource) {
            source.remove(dx)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()

        content.keys.sortedByDescending { !content[it]!! }.forEach { composition ->
            sb.append(composition).append(": ").appendLine(valueText(!content[composition]!!, UnitType.MOLE))
        }

        return sb.toString()
    }
}



fun<C : Compound> massContainerOf(values: List<Pair<C, Quantity<Substance>>>) = MassContainer<C>().apply {
    values.forEach { (c, amt) ->
        this[c] += amt
    }
}

fun<C : Compound> massContainerOf(vararg values: Pair<C, Quantity<Substance>>) = massContainerOf(values.asList())
fun<C : Compound> massContainerOfMass(vararg values: Pair<C, Quantity<Mass>>) = massContainerOf(
    values.map { (c, mass) ->
        c to (mass / c.molecularWeight).reparam()
    }
)

data class ChemicalEquationInfo(
    val elementsIn: Map<ChemicalElement, Int>,
    val elementsOut: Map<ChemicalElement, Int>,
    val isBalanced: Boolean,
)

data class ChemicalEquation<U : AtomicComposite>(
    val lhs: Map<U, Int>,
    val rhs: Map<U, Int>
) {
    fun analyze(): ChemicalEquationInfo {
        val elementsIn = HashMap<ChemicalElement, Int>()
        val elementsOut = HashMap<ChemicalElement, Int>()

        fun scan(m: HashMap<ChemicalElement, Int>, e: Map<U, Int>) {
            e.forEach { (comp, c1) ->
                comp.components.forEach { (element, c2) ->
                    m.addIncr(element, c1 * c2)
                }
            }
        }

        scan(elementsIn, lhs)
        scan(elementsOut, rhs)

        return ChemicalEquationInfo(
            elementsIn,
            elementsOut,
            elementsIn == elementsOut
        )
    }
}

data class MolecularCrystallographicEquation(
    val lhs: Map<CrystalComposition, Int>,
    val rhs: Map<CrystalComposition, Int>
)

fun compositionEquation(equation: String) : ChemicalEquation<MolecularComposition> = parseSubstituteEquation(equation, ::molecular).let { (a, b) -> ChemicalEquation(a, b) }
fun crystallographicCompositionEquation(equation: String) = parseSubstituteEquation(equation, ::molecularCrystallographic).let { (a, b) -> MolecularCrystallographicEquation(a, b) }
fun<T : AtomicComposite> substituteEquation(equation: String, parse: (String) -> T) = parseSubstituteEquation(equation, parse).let { ChemicalEquation(it.first, it.second) }
fun <T : AtomicComposite> substituteEquation(equation: String, map: Map<String, T>) = parseSubstituteEquation(equation, map).let { ChemicalEquation(it.first, it.second) }
fun<T> parseSubstituteEquation(equation: String, parse: (String) -> T): Pair<LinkedHashMap<T, Int>, LinkedHashMap<T, Int>> {
    fun parseSide(side: String): LinkedHashMap<T, Int> {
        val terms = side.split("+").map { it.trim() }
        require(terms.isNotEmpty())

        val results = LinkedHashMap<T, Int>()

        terms.forEach {
            require(it.isNotEmpty())

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
            val comp = parse(it.substring(i).trim())

            require(results.put(comp, coefficient) == null)
        }

        return results
    }

    val s = equation.split("->")
    require(s.size == 2)

    return Pair(
        parseSide(s[0]),
        parseSide(s[1])
    )
}
fun<T> parseSubstituteEquation(equation: String, mapping: Map<String, T>): Pair<LinkedHashMap<T, Int>, LinkedHashMap<T, Int>> = parseSubstituteEquation(equation) { s -> mapping[s] ?: error("Failed to map $s") }

fun<C> MassContainer<C>.applyReaction(equation: ChemicalEquation<C>, rate: Double) where C : AtomicComposite, C : Compound {
    var u = rate
    equation.lhs.forEach { (composition, count) -> u = min(u, !this[composition] / count.toDouble()) }
    equation.lhs.forEach { (c, x) -> this[c] -= Quantity(u * x) }
    equation.rhs.forEach { (c, x) -> this[c] += Quantity(u * x) }
}

enum class MolecularBondType { S, D, T, Q, A, Up, Down }

data class MolecularGraphBond(
    val targetIdx: Int,
    val type: MolecularBondType
)

data class MolecularGraphNode(
    val atom: ChemicalElement,
    val isotope: Int,
    val charge: Int,
    // Specified instead of adjusting topology. We don't even use the graph data, so no need to invest time in that:
    val chiralityClass: ChiralityClass?,
    val chiralityValue: Int,
    var isAromatic: Boolean,
    val index: Int
)

data class MolecularGraph(val nodes: List<MolecularGraphNode>, val bonds: List<List<MolecularGraphBond>>) {
    val composition = let {
        val results = LinkedHashMap<ChemicalElement, Int>()
        nodes.forEach { vert -> results.addIncr(vert.atom, 1) }
        MolecularComposition(results)
    }

    val hash = let {
        var result = nodes.hashCode()
        result = 31 * result + bonds.hashCode()
        result = 31 * result + composition.hashCode()
        result
    }

    override fun hashCode() = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MolecularGraph

        if(hash != other.hash) return false
        if (composition != other.composition) return false
        if (nodes != other.nodes) return false
        if (bonds != other.bonds) return false

        return true
    }

    operator fun not() = composition
}

data class MolecularCompoundIdentifier(
    val preferredIUPACName: String,
    val systematicIUPACName: String,
    val trivialNames: List<String>,
    val otherNames: List<String>,
    val smiles: String?
)

interface Symbol<T> {
    val symbol: T
}

data class StructuredMolecularCompound(
    val graph: MolecularGraph,
    override val properties: CompoundProperties,
    override val symbol: Int,
    val label: String,
    val names: MolecularCompoundIdentifier
) : Compound, AtomicComposite, Symbol<Int> {
    override val molecularWeight by graph.composition::molecularWeight
    override val components by graph.composition::components

    private val simplified = UnstructuredMolecularCompound(
        graph.composition,
        properties
    )

    val hash = let {
        var result = graph.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + symbol
        result
    }

    override fun hashCode() = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StructuredMolecularCompound

        if(hash != other.hash) return false
        if (graph != other.graph) return false
        if (properties != other.properties) return false
        if (symbol != other.symbol) return false

        return true
    }

    // Label should always be available for registered compounds
    override fun toString() = label
    operator fun not() = simplified
}
