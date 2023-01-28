package org.eln2.mc.common.parts.part

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.client.render.parts.MyPartRenderer
import org.eln2.mc.common.parts.IPartRenderer
import org.eln2.mc.common.parts.Part

class MyPart(pos: BlockPos, face: Direction, id : ResourceLocation, level : Level) : Part(pos, face, id, level) {
    override val baseSize: Vec3
        get() = Vec3(0.5, 0.25, 0.5)

    override fun onUsedBy(entity: LivingEntity) {
        Eln2.LOGGER.info("Test part used by $entity")
    }

    override fun createRenderer(): IPartRenderer {
        return MyPartRenderer(this)
    }
}
