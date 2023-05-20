package org.eln2.mc.extensions

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot

fun AbstractContainerMenu.addPlayerGrid(playerInventory: Inventory, addSlot: ((Slot) -> Unit)): Int {
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
