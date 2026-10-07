package org.eln2.mc.common.containers

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.SlotItemHandler
import kotlin.math.min

object ContainerHelper {
    fun addPlayerGrid(playerInventory: Inventory, addSlot: ((Slot) -> Unit)): Int {
        var slots = 0

        for (i in 0..2) {
            for (j in 0..8) {
                addSlot(Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18))
                slots++
            }
        }
        for (k in 0..8) {
            addSlot(Slot(playerInventory, k, 8 + k * 18, 142))
            slots++
        }

        return slots
    }

    fun quickMove(pSlots: List<Slot>, pPlayer: Player, pSlotIndex: Int, pDestinationPredicate: ((Int) -> Boolean)? = null): ItemStack {
        val sourceSlot = pSlots[pSlotIndex]

        val sourceStack = sourceSlot.item
            ?: return ItemStack.EMPTY

        val playerIsSource = sourceSlot.container === pPlayer.inventory
        val sourceCopy = sourceStack.copy()

        if (playerIsSource) {
            val flag = merge(
                pPlayer.inventory,
                false,
                sourceSlot,
                pSlots,
                false,
                pDestinationPredicate
            )

            return if (flag) {
                sourceCopy
            } else {
                ItemStack.EMPTY
            }
        }

        val flag = merge(
            pPlayer.inventory,
            true,
            sourceSlot,
            pSlots,
            !sourceSlot.mayPlace(sourceStack),
            pDestinationPredicate
        )

        return if (flag) {
            sourceCopy
        } else {
            ItemStack.EMPTY
        }
    }

    private fun merge(
        pPlayerInventory: Inventory,
        pMergeIntoPlayerInventory: Boolean,
        pSourceSlot: Slot,
        pSlots: List<Slot>,
        pReverse: Boolean,
        pSkip: ((Int) -> Boolean)?,
    ): Boolean {
        val sourceStack = pSourceSlot.item
        val sourceSize = sourceStack.count

        val dI = if(pReverse) {
            -1
        } else {
            +1
        }

        val start = if (pReverse) {
            pSlots.size - 1
        } else {
            0
        }

        var i = 0

        fun loop() = if (pReverse) {
            i >= 0
        } else {
            i < pSlots.size
        }

        fun skip() = if(pSkip == null) {
            false
        }
        else {
            val result = pSkip(i)

            if(result) {
                i++
            }

            result
        }

        if (sourceStack.isStackable) {
            i = start

            while (sourceStack.count > 0 && loop()) {
                if(skip()) {
                    continue
                }

                val slot = pSlots[i]

                if ((slot.container == pPlayerInventory) == pMergeIntoPlayerInventory) {
                    val target = slot.item

                    if (sourceStack.item == target.item) {
                        val targetMax = min(
                            slot.maxStackSize,
                            target.maxStackSize
                        )

                        val toTransfer = min(
                            sourceStack.count,
                            targetMax - target.count
                        )

                        if (toTransfer > 0) {
                            target.count += toTransfer
                            sourceStack.count -= toTransfer
                            slot.setChanged()
                        }
                    }
                }

                i += dI
            }
            if (sourceStack.count == 0) {
                pSourceSlot.set(ItemStack.EMPTY)
                return true
            }
        }

        i = start
        while (loop()) {
            if(skip()) {
                continue
            }

            val targetSlot = pSlots[i]

            val flag = ((targetSlot.container == pPlayerInventory) == pMergeIntoPlayerInventory) &&
                !targetSlot.hasItem() &&
                targetSlot.mayPlace(sourceStack)

            if (flag) {
                targetSlot.set(sourceStack.copy())
                pSourceSlot.set(ItemStack.EMPTY)
                sourceStack.count = 0

                return true
            }

            i += dI
        }

        if (sourceStack.count != sourceSize) {
            pSourceSlot.setChanged()
            return true
        }

        return false
    }
}

// Keen Software House much?

abstract class MyAbstractContainerScreen<T : AbstractContainerMenu>(pMenu: T, pPlayerInventory: Inventory, pTitle: Component): AbstractContainerScreen<T>(pMenu, pPlayerInventory, pTitle) {
    protected override fun renderLabels(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int) {
        pGuiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false)
    }

    override fun render(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        renderBackground(pGuiGraphics)
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick)
        renderTooltip(pGuiGraphics, pMouseX, pMouseY)
    }

    protected fun blitHelper(pGuiGraphics: GuiGraphics, pTexture: ResourceLocation) {
        pGuiGraphics.blit(pTexture, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    }
}

class SlotItemHandlerWithPlacePredicate(itemHandler: IItemHandler?, index: Int, xPosition: Int, yPosition: Int, private val predicate: (ItemStack) -> Boolean) : SlotItemHandler(itemHandler, index, xPosition, yPosition) {
    override fun mayPlace(stack: ItemStack): Boolean {
        return super.mayPlace(stack) && predicate(stack)
    }
}

class SlotItemHandlerWithPlacePredicateAndSkipPickupCheck(itemHandler: IItemHandler?, index: Int, xPosition: Int, yPosition: Int, private val predicate: (ItemStack) -> Boolean) : SlotItemHandler(itemHandler, index, xPosition, yPosition) {
    override fun mayPlace(stack: ItemStack): Boolean {
        return super.mayPlace(stack) && predicate(stack)
    }

    override fun mayPickup(playerIn: Player): Boolean {
        return true
    }

    override fun remove(amount: Int): ItemStack {
        val stackInSlot = this.item

        if (stackInSlot.isEmpty) {
            return ItemStack.EMPTY
        }

        val takeAmount = amount.coerceAtMost(stackInSlot.count)
        val result = stackInSlot.split(takeAmount)

        this.setChanged()
        return result
    }
}

class ProgressContainerData : SimpleContainerData(1) {
    companion object {
        private const val PROGRESS = 0
    }

    var progress: Float
        get() = Float.fromBits(this.get(PROGRESS))
        set(value) { this.set(PROGRESS, value.toBits()) }
}
