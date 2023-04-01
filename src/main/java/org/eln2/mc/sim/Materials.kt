package org.eln2.mc.sim

import org.ageseries.libage.data.biMapOf
import org.ageseries.libage.sim.Material

object MaterialMapping {
    private val map = biMapOf(
        "iron" to Material.IRON
    )

    fun getMaterial(name: String): Material {
        return map.forward[name] ?: error("Name $name does not correspond to any material.")
    }

    fun getName(material: Material): String {
        return map.backward[material] ?: error("Material $material does not have a mapping!")
    }
}
