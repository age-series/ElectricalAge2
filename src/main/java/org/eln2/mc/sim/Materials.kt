package org.eln2.mc.sim

import org.ageseries.libage.sim.Material

object MaterialMapping {
    private val nameToMaterial = mapOf(
        "iron" to Material.IRON
    )

    private val materialToName = nameToMaterial.entries
        .associate{ (k,v) -> v to k }

    fun getMaterial(name: String): Material {
        return nameToMaterial[name] ?: error("Name $name does not correspond to any material.")
    }

    fun getName(material: Material): String {
        return materialToName[material] ?: error("Material $material does not have a mapping!")
    }
}
