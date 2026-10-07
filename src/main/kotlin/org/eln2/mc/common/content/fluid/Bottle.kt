package org.eln2.mc.common.content.fluid

import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import org.eln2.mc.LOG
import org.eln2.mc.common.fluids.ForgeFluidRegistry
import org.eln2.mc.common.fluids.foundation.appendThermalFluidTooltip
import org.eln2.mc.common.content.modules.Eln2ForgeFluids

/**
 * Bottle holding 250mB of fluid. This item can be emptied into tanks, giving a vanilla glass bottle.
 *
 * The purpose of this item is mainly fluid crafting. I wanted a way to mix chemicals by hand, without a machine that would be clunky.
 * We can't mix liquid buckets (buckets don't stack; for example, if we were combining 2 buckets, 1000 mB of A and 1000 mB of B, and we could only add a single bucket as the result, 1000 mB of result, and this doesn't conserve mass, based on our formalism).
 * This item stacks, so we can conserve mass this way.
 *
 * Vanilla bottles are filled into chemical bottles in [onRightClickBlockEvent].
 * */
class ChemicalBottleItem(val eln2Fluid: ForgeFluidRegistry.ForgeFluidRegistryItem) : Item(Properties().stacksTo(16)) {
    companion object {
        fun onRightClickBlockEvent(event: PlayerInteractEvent.RightClickBlock) {
            val itemInHand = event.itemStack

            if (itemInHand.item != Items.GLASS_BOTTLE) {
                return
            }

            val level = event.level
            val pos = event.pos

            val blockEntity = level.getBlockEntity(pos)
                ?: return

            val capability = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, event.face).resolve()

            if(capability.isEmpty) {
                return
            }

            val handler = capability.get()
            val simulation = handler.drain(250, IFluidHandler.FluidAction.SIMULATE)

            if (simulation.amount < 250) {
                return
            }

            val bottleItem = Eln2ForgeFluids.getChemicalBottle(simulation.fluid)
                ?: return

            event.isCanceled = true
            event.cancellationResult = InteractionResult.SUCCESS

            if (level.isClientSide){
                return
            }

            val drain = handler.drain(250, IFluidHandler.FluidAction.EXECUTE)

            if(drain.fluid != simulation.fluid || drain.amount != 250) {
                /**
                 * Should never happen. We won't produce the item though.
                 * */
                LOG.error("Fluid handler {} is not behaving correctly when filling the bottle! ({}, {})", handler, drain.fluid, drain.amount)
                return
            }

            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f)

            itemInHand.shrink(1)
            val filledBottle = ItemStack(bottleItem)

            if (itemInHand.isEmpty) {
                event.entity.setItemInHand(event.hand, filledBottle)
            }
            else if (!event.entity.inventory.add(filledBottle)) {
                event.entity.drop(filledBottle, false)
            }
        }
    }

    override fun hasCraftingRemainingItem(stack: ItemStack?): Boolean {
        return true
    }

    override fun getCraftingRemainingItem(itemStack: ItemStack?): ItemStack {
        return ItemStack(Items.GLASS_BOTTLE, 1)
    }

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)
        appendThermalFluidTooltip(eln2Fluid.get(), pTooltipComponents)
        pTooltipComponents.add(Component.translatable("tooltip.eln2.fluid_container.deposit").withStyle(ChatFormatting.GRAY))
    }
    override fun useOn(pContext: UseOnContext): InteractionResult {
        val player = pContext.player
            ?: return InteractionResult.FAIL

        val level = pContext.level
        val pos = pContext.clickedPos

        val blockEntity = level.getBlockEntity(pos)
            ?: return InteractionResult.FAIL

        val hand = pContext.hand

        val itemInHand = player.getItemInHand(hand)

        val capability = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, pContext.clickedFace).resolve()

        if(!capability.isPresent) {
            return InteractionResult.FAIL
        }

        val handler = capability.get()

        val stack = FluidStack(eln2Fluid.get(), 250)
        val simulation = handler.fill(stack, IFluidHandler.FluidAction.SIMULATE)

        if (simulation != 250) {
            return InteractionResult.FAIL
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val fill = handler.fill(stack, IFluidHandler.FluidAction.EXECUTE)

        if(fill != 250) {
            /**
             * Should never happen.
             * */
            LOG.error("Fluid handler {} is not behaving correctly when filling from bottle! ({})", handler, fill)
        }

        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)

        if (!player.isCreative) {
            val glassBottle = ItemStack(Items.GLASS_BOTTLE)

            itemInHand.shrink(1)

            if (itemInHand.isEmpty) {
                player.setItemInHand(hand, glassBottle)
            }
            else if (!player.inventory.add(glassBottle)) {
                player.drop(glassBottle, false)
            }
        }

        return InteractionResult.SUCCESS
    }
}
/**
 * Durability-based crafting tool that applies a fluid coating (e.g. creosote, enamel) without consuming a full bottle.
 * Crafting with this item consumes 1 durability instead of the fluid. When durability reaches 0, the brush is destroyed.
 * @param maxUses The number of crafting operations before the brush breaks.
 * */
class BrushItem(val maxUses: Int) : Item(Properties().stacksTo(1).durability(maxUses)) {
    override fun isEnchantable(stack: ItemStack): Boolean {
        return false
    }

    override fun hasCraftingRemainingItem(stack: ItemStack): Boolean {
        return stack.damageValue < stack.maxDamage - 1
    }

    override fun getCraftingRemainingItem(stack: ItemStack): ItemStack {
        val remaining = stack.copy()
        remaining.damageValue = stack.damageValue + 1
        return remaining
    }

    override fun isBarVisible(stack: ItemStack): Boolean {
        return true
    }

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)
        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.brush.uses_remaining")
                .append(": ")
                .append(Component.literal((maxUses - pStack.damageValue).toString()).withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.YELLOW)
        )
    }
}
