package org.eln2.mc.common.parts

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.Vec3
import org.eln2.mc.Eln2
import org.eln2.mc.client.render.WirePartRenderer
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.extensions.NbtExtensions.getRelativeDirection
import org.eln2.mc.extensions.NbtExtensions.setRelativeDirection

class WirePart(id : ResourceLocation, context : PartPlacementContext) : CellPart(id, context, CellRegistry.WIRE_CELL.get()) {
    override val baseSize: Vec3 get() = Vec3(0.5, 0.25, 0.5)

    val connectedDirections = HashSet<RelativeRotationDirection>()
    private var wireRenderer : WirePartRenderer? = null

    override fun createRenderer(): IPartRenderer {
        wireRenderer = WirePartRenderer(this)

        applyRendererState()

        Eln2.LOGGER.info("Created wire renderer: $wireRenderer")

        return wireRenderer!!
    }

    override fun destroyRenderer() {
        super.destroyRenderer()

        Eln2.LOGGER.info("Destroying wire renderer: $wireRenderer")

        wireRenderer = null
    }

    override fun onPlaced() {
        super.onPlaced()

        if(!placementContext.level.isClientSide){
            syncChanges()
        }
    }

    override fun loadFromTag(tag: CompoundTag) {
        super.loadFromTag(tag)

        val wireData = tag.get("WireData") as CompoundTag

        loadTag(wireData)
    }

    override fun getSaveTag(): CompoundTag? {
        val tag = super.getSaveTag() ?: return null

        val wireData = createTag()

        tag.put("WireData", wireData)

        return tag
    }

    override fun getSyncTag(): CompoundTag {
        return createTag()
    }

    override fun handleSyncTag(tag: CompoundTag) {
        loadTag(tag)
    }

    private fun createTag() : CompoundTag{
        val tag = CompoundTag()

        val directionList = ListTag()

        connectedDirections.forEach{ direction ->
            val directionTag = CompoundTag()

            directionTag.setRelativeDirection("Direction", direction)

            directionList.add(directionTag)
        }

        tag.put("Directions", directionList)

        return tag
    }

    private fun loadTag(tag : CompoundTag){
        connectedDirections.clear()

        val directionList = tag.get("Directions") as ListTag

        directionList.forEach { directionTag ->
            val direction = (directionTag as CompoundTag).getRelativeDirection("Direction")

            connectedDirections.add(direction)
        }

        if(placementContext.level.isClientSide){
            applyRendererState()
        }
    }

    private fun applyRendererState(){
        wireRenderer?.applyDirections(connectedDirections.toList())
    }

    override fun recordConnection(direction: RelativeRotationDirection, mode : ConnectionMode) {
        Eln2.LOGGER.error("Wire $this record $direction : $mode")
        connectedDirections.add(direction)
        syncChanges()
        invalidateSave()
    }

    override fun recordDeletedConnection(direction: RelativeRotationDirection) {
        connectedDirections.remove(direction)
        syncChanges()
        invalidateSave()
    }

    override fun onUsedBy(context: PartUseContext): InteractionResult {
        Eln2.LOGGER.info("Wire Part used by ${context.player}")

        return super.onUsedBy(context)
    }

    override val allowPlanarConnections = true
    override val allowInnerConnections = true
    override val allowWrappedConnections = true
}
