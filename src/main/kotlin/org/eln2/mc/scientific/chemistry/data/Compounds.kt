package org.eln2.mc.scientific.chemistry.data

import org.eln2.mc.data.Density
import org.eln2.mc.data.G_PER_CM3
import org.eln2.mc.data.KG_PER_M3
import org.eln2.mc.data.Quantity
import org.eln2.mc.scientific.chemistry.*

// When you add a compound, you take on the obligation of adding all properties that can be useful in the future.

private class RegisteredCompoundBuilder(val symbol: Int, val graph: MolecularGraph) {
    constructor(symbol: Int, smilesString: String) : this(symbol, smiles(smilesString)) {
        this.smiles = smilesString
    }

    var label = graph.composition.toString()
    var preferredIUPACName = ""
    var systematicIUPACName = ""
    var trivialNames = ArrayList<String>()
    var otherNames = ArrayList<String>()
    var densitySTP = Quantity<Density>(0.0)
    var smiles: String? = null

    fun withTrivialNames(vararg names: String) = trivialNames.addAll(names.asList())
    fun withOtherNames(vararg names: String) = otherNames.addAll(names.asList())

    fun build(): StructuredMolecularCompound {
        val compound = StructuredMolecularCompound(
            graph = this.graph,
            properties = CompoundProperties(
                densitySTP = this.densitySTP
            ),
            symbol = this.symbol,
            label = this.label,
            names = MolecularCompoundIdentifier(
                preferredIUPACName = this.preferredIUPACName,
                systematicIUPACName = this.systematicIUPACName,
                trivialNames = this.trivialNames.toList(),
                otherNames = this.otherNames.toList(),
                smiles = this.smiles
            )
        )

        register(compound, symbol)

        return compound
    }
}

private fun StructuredMolecularCompound.builder(symbol: Int) = RegisteredCompoundBuilder(symbol, this.graph).also {
    it.densitySTP = this.properties.densitySTP
    it.label = this.label
    it.preferredIUPACName = this.names.preferredIUPACName
    it.systematicIUPACName = this.names.systematicIUPACName
    it.trivialNames.addAll(this.names.trivialNames)
    it.otherNames.addAll(this.names.otherNames)
}

private val structuredCompounds = HashMap<Int, StructuredMolecularCompound>()
private fun register(c: StructuredMolecularCompound, i: Int) = require(structuredCompounds.put(i, c) == null) { "Duplicate compound $i" }
private fun register(i: Int, s: String, label: String, names: MolecularCompoundIdentifier, properties: CompoundProperties) {
    register(StructuredMolecularCompound(smiles(s), properties, i, label, names), i)
}
fun getStructure(i: Int) = structuredCompounds[i] ?: error("$i is not a registered compound")



val glucosePowder = RegisteredCompoundBuilder(
    0,
    "OC[C@H]1OC(O)[C@H](O)[C@@H](O)[C@@H]1O"
).apply {
    densitySTP = Quantity(1.54, G_PER_CM3)
    label = "D-Glucose" // using D so we don't have issues with fonts
    systematicIUPACName = "(2R,3S,4R,5R)-2,3,4,5,6-pentahydroxyhexanal"
    withTrivialNames(
        "D-Glucose",
        "D-Glc",
        "D-gluco-Hexose",
        "Glc",
        "D-Glucopyranose",
        "D-Glucopyranoside"
    )
    withOtherNames(
        "Glucose",
        "Dextrose",
    )
}.build()

val liquidEthanol = RegisteredCompoundBuilder(
    1,
    "CCO"
).apply {
    densitySTP = Quantity(0.789, G_PER_CM3)
    label = "Ethanol"
    preferredIUPACName = "Ethanol"
    withTrivialNames(
        "Ethanol",
        "EtOH",
        "Ethyl Alcohol",
    )
    withOtherNames(
        "alcohol"
    )
}.build()

val co2Gas = RegisteredCompoundBuilder(
    2,
    "O=C=O"
).apply {
    densitySTP = Quantity(1.98, KG_PER_M3)
    label = "Carbon Dioxide"
    preferredIUPACName = "Carbon Dioxide"
    withTrivialNames(
        "Carbon Dioxide Gas",
        "CO2 Gas"
    )
}.build()

val liquidAceticAcid = RegisteredCompoundBuilder(
    3,
    "CC(O)=O"
).apply {
    densitySTP = Quantity(1.0491, G_PER_CM3)
    label = "Acetic Acid"
    preferredIUPACName = "Acetic Acid"
    systematicIUPACName = "Ethanoic acid"
    withTrivialNames(
        "Acetic Acid"
    )
    withOtherNames(
        "Vinegar"
    )
}.build()

val liquidWater = RegisteredCompoundBuilder(
    4,
    "O"
).apply {
    densitySTP = Quantity(1.0, G_PER_CM3)
    label = "Water"
    preferredIUPACName = "Water"
    systematicIUPACName = "Oxidane"
    withTrivialNames(
        "Water"
    )
}.build()
