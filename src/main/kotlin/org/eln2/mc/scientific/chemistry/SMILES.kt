package org.eln2.mc.scientific.chemistry

import org.ageseries.libage.data.MutableSetMapMultiMap
import org.eln2.mc.associateWithBi
import org.eln2.mc.bind
import org.eln2.mc.data.*
import org.eln2.mc.isLetter
import org.eln2.mc.scientific.chemistry.data.ChemicalElement

/*
* I could not find an easy-to-understand reference implementation.
* I couldn't even find a super consistent source of specifications,
* mostly because there are a few standards that have different ideas about things.
* I tried to follow the rules stated by OpenSMILES, and I've also tried to follow the original paper.
*
* We don't use graph information, so this parser is just a bonus.
*  As such, I have cut corners and did not implement the following concepts:
*   - Conversion to Kekule form
*   - Stereochemistry
*   among other things.
* Also, this implementation doesn't have validation in place and is not particularly tested.
*
* So, bear with me as you read through a SMILES parser:
* */

private data class ParseNode(val atom: ChemicalElement, val index: Int) {
    var isotope = 0
    var charge = 0
    var hCount = 0
    var chiralityClass: ChiralityClass? = null
    var chiralityValue = 0
    var isMarkedAromatic = false
    var isBracket = false
}

private data class ParseBond(val targetIdx: Int, var type: ParseBondType? = null)

private class ParseGraphBuilder {
    val atoms = ArrayList<ParseNode>()
    val bonds = ArrayList<ArrayList<ParseBond>>()
    val groups = ArrayDeque<Int>()

    val subject get() = atoms.last()
    val groupLevel get() = groups.size
    val isStarted get() = atoms.isNotEmpty()
    val chainEnd get() = groups.last()

    fun node(e: ChemicalElement): ParseNode {
        val node = ParseNode(e, atoms.size)

        atoms.add(node)
        bonds.add(ArrayList())

        if(groups.isEmpty()) {
            require(node.index == 0)
            groups.addLast(0)
        }

        return node
    }

    fun bond(a: Int, b: Int, type: ParseBondType?) {
        bonds[a].add(ParseBond(b, type))
        bonds[b].add(ParseBond(a, type))
    }

    fun chainBond(type: ParseBondType?) {
        val a = atoms[chainEnd]
        val b = subject
        bonds[a.index].add(ParseBond(b.index, type))
        bonds[b.index].add(ParseBond(a.index, type))
        groups.removeLast()
        groups.addLast(b.index)
    }

    fun closureBond(start: Int, type: ParseBondType?) {
        val b = chainEnd
        bonds[start].add(ParseBond(b, type))
        bonds[b].add(ParseBond(start, type))
    }

    fun push() {
        groups.addLast(atoms.size - 1)
    }

    fun pop(): Boolean {
        if(groups.isEmpty()) {
            return false
        }

        groups.removeLast()
        return true
    }
}

private val orgSubset = listOf(
    ChemicalElement.Boron,
    ChemicalElement.Carbon,
    ChemicalElement.Nitrogen,
    ChemicalElement.Oxygen,
    ChemicalElement.Phosphorus,
    ChemicalElement.Sulfur,
    ChemicalElement.Fluorine,
    ChemicalElement.Chlorine,
    ChemicalElement.Bromine,
    ChemicalElement.Iodine
).associateWithBi { it.symbol }

private val targetValences = mapOf(
    ChemicalElement.Boron to listOf(3),
    ChemicalElement.Carbon to listOf(4),
    ChemicalElement.Nitrogen to listOf(3, 5),
    ChemicalElement.Oxygen to listOf(2),
    ChemicalElement.Phosphorus to listOf(3, 5),
    ChemicalElement.Sulfur to listOf(2, 4, 6),
    ChemicalElement.Fluorine to listOf(1),
    ChemicalElement.Chlorine to listOf(1),
    ChemicalElement.Bromine to listOf(1),
    ChemicalElement.Iodine to listOf(1)
)

private val aromaticSubset = mapOf(
    'b' to ChemicalElement.Boron,
    'c' to ChemicalElement.Carbon,
    'n' to ChemicalElement.Nitrogen,
    'o' to ChemicalElement.Oxygen,
    'p' to ChemicalElement.Phosphorus,
    's' to ChemicalElement.Sulfur
)

private val bondChars = hashSetOf('.', '-', '=', '#', '$', ':', '/', '\\')

private enum class ParseBondType(val order: Int) {
    S(1),
    D(2),
    T(3),
    Q(4),
    A(1),
    Up(1),
    Down(1),
    Disjoint(1)
}

private val bondMap = ParseBondType
    .values()
    .filter { it != ParseBondType.Disjoint }
    .associateWith { MolecularBondType.valueOf(it.toString()) }

enum class ChiralityClass {
    CW,
    CCW,
    Tetrahedral,
    Allene,
    SquarePlanar,
    TrigonalBipyramidal,
    Octahedral
}

fun smiles(smiles: String): MolecularGraph {
    val builder = ParseGraphBuilder()
    val scanner = StringScanner(smiles)

    // The last bond specified using a symbol
    var specifiedBond: ParseBondType? = null

    data class RingInfo(
        val startIndex: Int,
        val start: Int,
        val closeType: ParseBondType?,
        val level: Int
    )

    // The total rings specified so far, used to make some look-ups
    var ringIdx = 0
    val activeIdRing = HashMap<Int, RingInfo>()
    val activeRingLevels = HashMapHistogram<Int>()
    val rings = MutableSetMapMultiMap<ParseNode, RingInfo>()

    while(!scanner.eof) {
        var current = scanner.peek()
        val currentI = scanner.i

        fun pError(message: String): Nothing = error("$message at $currentI/${scanner.i}")
        fun requireNotEof() {
            if(scanner.eof) {
                pError("Unexpected EOF")
            }
        }
        fun requireStarted() {
            if(!builder.isStarted) {
                pError("Expected initial atom")
            }
        }

        // Implementing an atom is:
        //  1. Extending the current chain by bonding this atom with the previous
        //  2. If this atom is in a ring, that ring is recorded
        fun implementAtom() {
            require(builder.isStarted)

            // Extend ongoing chain:
            if(builder.atoms.size > 1) {
                when (specifiedBond) {
                    ParseBondType.Disjoint -> {
                        // Ignored
                    }
                    else -> {
                        builder.chainBond(specifiedBond)
                    }
                }
            }

            specifiedBond = null

            val subject = builder.subject

            if(activeRingLevels.contains(builder.groupLevel)) {
                require(rings[subject].add(activeIdRing.values.first { it.level == builder.groupLevel }))
            }
        }

        if(current == '[') {
            scanner.pop()

            val atomScanner = buildString {
                while (true) {
                    requireNotEof()

                    val c = scanner.pop()

                    if(c == ']') {
                        break
                    }

                    append(c)
                }
            }.scanner()

            if(atomScanner.eof) {
                pError("Expected element definition")
            }

            val isotope = atomScanner.popInteger()
            var element = atomScanner.popChemicalElement()
            var aromatic = false

            if(element == null) {
                if(atomScanner.eof) {
                    pError("Unexpected end in bracket atom")
                }

                element = aromaticSubset[atomScanner.pop()] ?: pError("Unrecognised element")
                aromatic = true
            }

            var hCount = 0
            var charge = 0
            var chiralityClass: ChiralityClass? = null
            var chiralityValue = 0

            atomScanner.ifNotEof({
                if(it.matchPop("@@")) {
                    chiralityClass = ChiralityClass.CW
                }
                else if(it.matchPop("@TH")) {
                    chiralityClass = ChiralityClass.Tetrahedral
                    chiralityValue = it.popInteger() ?: pError("Expected tetrahedral parametrization")
                }
                else if(it.matchPop("@AL")) {
                    chiralityClass = ChiralityClass.Allene
                    chiralityValue = it.popInteger() ?: pError("Expected allene-like parametrization")
                }
                else if(it.matchPop("@SP")) {
                    chiralityClass = ChiralityClass.SquarePlanar
                    chiralityValue = it.popInteger() ?: pError("Expected square planar parametrization")
                }
                else if(it.matchPop("@TB")) {
                    chiralityClass = ChiralityClass.TrigonalBipyramidal
                    chiralityValue = it.popInteger() ?: pError("Expected trigonal bipyramidal parametrization")
                }
                else if(it.matchPop("@OH")) {
                    chiralityClass = ChiralityClass.Octahedral
                    chiralityValue = it.popInteger() ?: pError("Expected octahedral parametrization")
                }
                else if(it.matchPop("@")) {
                    chiralityClass = ChiralityClass.CCW
                }
            }, {
                if(it.peek() == 'H') {
                    it.pop()
                    hCount = it.popInteger() ?: 1
                }
            }, {
                while (!atomScanner.eof) {
                    val chargeC = atomScanner.pop()
                    if(chargeC == '+') ++charge
                    else if(chargeC == '-') --charge
                    else if(chargeC.isDigit()) {
                        if(charge == 0) {
                            pError("Expected charge sign")
                        }

                        // This would allow having multiple charge signs and a number, but that is fine.

                        charge *= buildString {
                            append(chargeC)
                            while (!atomScanner.eof) {
                                append(atomScanner.pop().also {
                                    if(!it.isDigit()) {
                                        pError("Unexpected digit $it")
                                    }
                                })
                            }
                        }.toInt()
                    }
                }
            })

            builder.node(element).also {
                it.isotope = isotope ?: 0
                it.charge = charge
                it.hCount = hCount
                it.chiralityClass = chiralityClass
                it.chiralityValue = chiralityValue
                it.isMarkedAromatic = aromatic
                it.isBracket = true
            }

            implementAtom()
        }
        else if(bondChars.contains(current)) {
            requireStarted()

            specifiedBond = when(current) {
                '.' -> ParseBondType.Disjoint
                '-' -> ParseBondType.S
                '=' -> ParseBondType.D
                '#' -> ParseBondType.T
                '$' -> ParseBondType.Q
                ':' -> ParseBondType.A
                '/' -> ParseBondType.Up
                '\\'-> ParseBondType.Down
                else -> pError("Unrecognised bond $current") // not really possible
            }

            scanner.pop()
        }
        else if(current.isLetter && current.isUpperCase()) {
            val element = scanner.popChemicalElement()
                ?: pError("Unrecognised element")

            if(!orgSubset.forward.contains(element)) {
                pError("Illegal element $element")
            }

            builder.node(element)
            implementAtom()
        }
        else if(aromaticSubset.containsKey(current)) {
            val element = aromaticSubset[current]!!

            builder.node(element).also {
                it.isMarkedAromatic = true
            }

            implementAtom()
            scanner.pop()
        }
        else if(current == '%' || current.isDigit()) {
            requireStarted()

            val ringLabels = ArrayList<Int>()

            while (!scanner.eof) {
                current = scanner.peek()

                if(current == '%') {
                    scanner.pop()
                    requireNotEof()
                    ringLabels.add(scanner.popString(2).toIntOrNull() ?: pError("Expected ring number"))
                }
                else if(current.isDigit()) {
                    scanner.pop()
                    ringLabels.add(current.digitToInt())
                }
                else break
            }

            require(ringLabels.isNotEmpty())

            ringLabels.forEach { ringId ->
                if(activeIdRing.containsKey(ringId)) {
                    val ringInfo = activeIdRing.remove(ringId)!!
                    builder.closureBond(ringInfo.start, ringInfo.closeType)
                    activeRingLevels -= ringInfo.level
                }
                else {
                    val info = RingInfo(
                        startIndex = ringIdx++,
                        start = builder.atoms.size - 1,
                        closeType = specifiedBond,
                        level = builder.groupLevel
                    )

                    require(rings[builder.subject].add(info))

                    if(activeIdRing.put(ringId, info) != null) {
                        pError("Duplicate ring $ringId")
                    }

                    activeRingLevels += info.level
                }

                specifiedBond = null
            }
        }
        else if(current == '(') {
            if(specifiedBond != null) {
                pError("Illegal bond before group start")
            }

            builder.push()
            scanner.pop()
        }
        else if(current == ')') {
            if(!builder.pop()) {
                pError("Unexpected group close")
            }

            scanner.pop()
        }
        else pError("Unexpected \"$current\"")
    }

    if(activeIdRing.isNotEmpty()) {
        error("Expected ring closure for ${activeIdRing.keys.joinToString(", ")}")
    }

    if(specifiedBond != null) {
        error("Unexpected bond")
    }

    // Fill in bonds:
    builder.atoms.forEach { n1 ->
        val bs1 = builder.bonds[n1.index]

        bs1.forEachIndexed { i1, bond1 ->
            val n2 = builder.atoms[bond1.targetIdx]
            val bs2 = builder.bonds[n2.index]
            val i2 = bs2.indexOfFirst { it.targetIdx == n1.index }

            fun applyBond(type: ParseBondType) {
                bs1[i1].type = type
                bs2[i2].type = type
            }

            if(n1.isMarkedAromatic && n2.isMarkedAromatic) {
                val r1 = rings[n1]
                val r2 = rings[n2]

                if((r1.isEmpty() && r2.isEmpty()) || rings[n1].any { r2.contains(it) }) {
                    applyBond(ParseBondType.A)
                }
                else {
                    applyBond(ParseBondType.S)
                }
            }
            else {
                require(bs1[i1].type == bs2[i2].type)

                if(bs1[i1].type == null) {
                    applyBond(ParseBondType.S)
                }
            }
        }
    }

    // Fill in hydrogen:
    builder.atoms.bind().forEach { node ->
        fun emitHydrogen() {
            builder.bond(
                builder.node(ChemicalElement.Hydrogen).index,
                node.index,
                ParseBondType.S
            )
        }

        if(node.hCount != 0) {
            repeat(node.hCount) {
                emitHydrogen()
            }

            return@forEach
        }

        if(node.isBracket) {
            return@forEach
        }

        if(node.atom == ChemicalElement.Hydrogen) {
            return@forEach
        }

        if(!orgSubset.forward.containsKey(node.atom)) {
            return@forEach
        }

        val actualValence = builder.bonds[node.index].sumOf { it.type!!.order }
        val targetValence = targetValences[node.atom]!!.filter { it >= actualValence }.minOrNull() ?: return@forEach

        val subValence = targetValence - actualValence

        val missing = if(node.isMarkedAromatic) {
            if(subValence > 1) subValence - 1
            else 0
        }
        else {
            subValence
        }

        repeat(missing) {
            emitHydrogen()
        }
    }

    return MolecularGraph(
        builder.atoms.map {
            MolecularGraphNode(
                atom = it.atom,
                isotope = it.isotope,
                charge = it.charge,
                chiralityClass = it.chiralityClass,
                chiralityValue = it.chiralityValue,
                isAromatic = it.isMarkedAromatic,
                index = it.index
            )
        },

        builder.bonds.map { bonds ->
            bonds.map {
                MolecularGraphBond(
                    targetIdx = it.targetIdx,
                    type = bondMap[it.type!!]!!
                )
            }
        }
    )
}
