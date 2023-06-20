package org.eln2.mc.common.content

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.events.ScheduledWork
import org.eln2.mc.common.events.periodicPre
import org.eln2.mc.common.items.eln2Tab
import org.eln2.mc.common.parts.foundation.*
import org.eln2.mc.data.DataNode
import org.eln2.mc.data.TooltipField
import org.eln2.mc.formattedPercentN
import org.eln2.mc.isHorizontal
import org.eln2.mc.mathematics.DirectionMask
import org.eln2.mc.mathematics.SNZE_EPSILON
import org.eln2.mc.mathematics.nz
import org.eln2.mc.mathematics.vec3
import org.eln2.mc.minus

class TreeTapPartProvider : PartProvider() {
    override val placementCollisionSize = TreeTapPart.SIZE
    override fun create(context: PartPlacementInfo) = TreeTapPart(id, context)
    override fun canPlace(context: PartPlacementInfo) = context.face.isHorizontal()
}

class TreeTapPart(id: ResourceLocation, placement: PartPlacementInfo) : Part<BasicPartRenderer>(id, placement) {
    override val sizeActual = SIZE

    var quantity = 0.0

    init {
        require(placement.face.isHorizontal()) {
            "Placed tree tap on invalid face ${placement.face}"
        }
    }

    private var schedule: ScheduledWork? = null

    override fun createRenderer() = BasicPartRenderer(this, PartialModels.BATTERY)

    override fun onAdded() {
        if(!placement.level.isClientSide) {
            require(schedule == null)
            schedule = periodicPre(TREE_TAP_INTERVAL, ::update)
        }
    }

    @ServerOnly
    private fun update(): Boolean {
        val substrate = placement.level.getBlockState(placement.pos - placement.face)

        BLOCKS[substrate.block]?.also {
            quantity += it + SNZE_EPSILON
            invalidateSave()
        }

        return true
    }

    override fun getSaveTag() = CompoundTag().apply { putDouble(NBT_QUANTITY, quantity) }
    override fun loadFromTag(tag: CompoundTag) { quantity = tag.getDouble(NBT_QUANTITY)}

    override fun onUsedBy(context: PartUseInfo): InteractionResult {
        if(placement.level.isClientSide) {
            return InteractionResult.PASS
        }

        if(quantity >= 1.0) {
            if(context.player.addItem(ItemStack(Content.LATEX_SAP.item.get(), 1))) {
                quantity -= 1.0
                invalidateSave()
                return InteractionResult.SUCCESS
            }
        }

        return InteractionResult.FAIL
    }

    override fun onRemoved() {
        schedule?.cancel()
    }

    override val dataNode = super.dataNode.apply {
        data.withField(TooltipField { b -> b.text("Quantity", quantity.formattedPercentN()) })
    }

    companion object {
        val SIZE = vec3(1.0)
        private const val NBT_QUANTITY = "quantity"
        private const val TREE_TAP_INTERVAL = 10

        private val BLOCKS = mapOf(
            Blocks.OAK_LOG to 0.1
        )
    }
}

class LatexSapItem : Item(Properties().stacksTo(64))
