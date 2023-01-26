package org.eln2.mc.common.parts.part

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import org.eln2.mc.Eln2
import org.eln2.mc.common.parts.Part

class MyPart(pos: BlockPos, face: Direction) : Part(pos, face) {
    override val size: Double
        get() = 0.5

    override fun onUsedBy(entity: LivingEntity) {
        Eln2.LOGGER.info("Test part used by $entity")
    }
}
