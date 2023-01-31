package org.eln2.mc.common.parts.part

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.client.render.PartialModelPartRenderer
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.common.parts.*
import java.util.concurrent.atomic.AtomicInteger

class MyPart(id : ResourceLocation, context : PartPlacementContext) : CellPart(id, context, CellRegistry.WIRE_CELL.get()) {
    override val baseSize: Vec3
        get() = Vec3(0.5, 0.25, 0.5)

    override fun onUsedBy(entity: LivingEntity) {
        Eln2.LOGGER.info("Test part used by $entity")
    }

    override fun createRenderer(): IPartRenderer {
        return PartialModelPartRenderer(this, PartialModels.MY_MODEL)
    }

    override fun onPlaced() {
        super.onPlaced()

        if(!placementContext.level.isClientSide){
            syncChanges()
        }
    }

    override fun getSyncTag(): CompoundTag? {
        val tag = CompoundTag()

        tag.putInt("Id", testId.incrementAndGet())

        return tag
    }

    override fun handleSyncTag(tag: CompoundTag) {
        assert(placementContext.level.isClientSide)

        val id = tag.getInt("Id")

        Eln2.LOGGER.info("Test id: $id")
    }

    companion object{
        var testId = AtomicInteger()
    }

    override val allowPlanarConnections = true
    override val allowInnerConnections = true
    override val allowWrappedConnections = true
}
