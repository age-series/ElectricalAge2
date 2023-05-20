package org.eln2.mc.extensions

import com.jozufozu.flywheel.core.materials.model.ModelData
import net.minecraft.world.phys.Vec3

/**
 * This removes the translation I observed in BlockBench models.
 * Useful for applying transformations like rotation and scale.
 * */
fun ModelData.zeroCenter(): ModelData {
    return this.translate(Vec3(-0.5, 0.0, -0.5))
}

fun ModelData.blockCenter(): ModelData {
    return this.translate(Vec3(0.5, 0.0, 0.5))
}
