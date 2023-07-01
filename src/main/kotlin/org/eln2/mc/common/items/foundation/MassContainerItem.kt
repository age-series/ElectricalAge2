package org.eln2.mc.common.items.foundation

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import org.eln2.mc.common.capabilities.COMPOUND_CONTAINER_CAPABILITY
import org.eln2.mc.common.capabilities.CompoundContainerCapability
import org.eln2.mc.data.*
import org.eln2.mc.getSymContainer
import org.eln2.mc.putSymContainer
import org.eln2.mc.scientific.chemistry.CompoundContainer
import kotlin.jvm.optionals.getOrNull

class MassContainerItem : Item(Properties().stacksTo(1)) {
    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag
    ) {
        val container = getActualContainer(pStack)

        pTooltipComponents.add(Component.literal("Volume: ${valueText(container.volumeSTP .. LITERS, UnitType.LITRE)}"))

        container.content.keys.sortedByDescending { container.content[it]!! }.forEach { comp ->
            pTooltipComponents.add(
                Component.literal(
                    "${valueText(!container.content[comp]!!, UnitType.MOLE)} " +
                        "${comp.label} " +
                        "(${valueText(container.getMass(comp) .. GRAMS, UnitType.GRAM)})"
                )
            )
        }
    }

    override fun useOn(pContext: UseOnContext): InteractionResult {
        val container = getActualContainer(pContext.itemInHand)

        if(pContext.level.isClientSide) {
            return InteractionResult.FAIL
        }

        val target = pContext.level.getBlockEntity(pContext.clickedPos) ?: return InteractionResult.FAIL

        val capability = target.getCapability(COMPOUND_CONTAINER_CAPABILITY)

        val instance = capability.resolve().getOrNull() ?: return InteractionResult.FAIL


        val r = if(pContext.isSecondaryUseActive) {
            useOnExtract(container, instance, pContext)
        } else {
            useOnInsert(container, instance,pContext)
        }

        setContainer(pContext.itemInHand, container)
        return r
    }

    private fun useOnInsert(mutableContainer: CompoundContainer, i: CompoundContainerCapability, pContext: UseOnContext): InteractionResult {
        if(!i.canInsertByPlayer) {
            return InteractionResult.FAIL
        }

        if(!i.canInsert(mutableContainer)) {
            return InteractionResult.FAIL
        }

        val maxInsertV = min(
            i.getMaxInsertVolume(mutableContainer),
            OPERATION_VOLUME
        )

        return if(i.insertFrom(mutableContainer, maxInsertV)) InteractionResult.SUCCESS else InteractionResult.FAIL
    }

    private fun useOnExtract(mutableContainer: CompoundContainer, i: CompoundContainerCapability, pContext: UseOnContext): InteractionResult  {
        if(!i.canExtractByPlayer) {
            return InteractionResult.FAIL
        }

        return if(i.extractInto(mutableContainer, min(OPERATION_VOLUME, VOLUME - mutableContainer.volumeSTP)))
            InteractionResult.SUCCESS
        else InteractionResult.FAIL
    }

    companion object {
        private const val NBT_CONTAINER = "massContainer"

        val VOLUME = Quantity(10.0, LITERS)
        val OPERATION_VOLUME = Quantity(100.0, MILLILITERS)

        fun getActualContainer(s: ItemStack): CompoundContainer {
            val tag = s.tag ?: return CompoundContainer()
            return if(tag.contains(NBT_CONTAINER)) tag.getSymContainer(NBT_CONTAINER)
            else CompoundContainer()
        }

        fun setContainer(s: ItemStack, c: CompoundContainer) {
            if(s.tag == null) {
                s.tag = CompoundTag()
            }

            s.tag!!.putSymContainer(NBT_CONTAINER, c)
        }
    }
}
