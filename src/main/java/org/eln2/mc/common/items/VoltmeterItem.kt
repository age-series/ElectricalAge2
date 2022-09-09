package org.eln2.mc.common.items

import net.minecraft.Util
import net.minecraft.network.chat.TextComponent
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import org.eln2.mc.common.blocks.CellTileEntity

class VoltmeterItem(tab: CreativeModeTab?): Item(Item.Properties().also {if(tab != null) it.tab(tab)}) {
    override fun useOn(context: UseOnContext): InteractionResult {
        // If you try to dereference Cell on the client... lol it explodes violently.
        if (!context.level.isClientSide) {
            val clicked = context.level.getBlockEntity(context.clickedPos)
            if (clicked is CellTileEntity) {
                val voltage = clicked.getHudMap()["voltage"]
                val power = clicked.getHudMap()["power"]
                val current = clicked.getHudMap()["current"]

                var text = ""

                if (voltage != null) {
                    text += "Voltage: "+voltage+"  "
                }
                if (power != null) {
                    text += "Power: "+power+"  "
                }
                if (current != null) {
                    text += "Current: "+current+"  "
                }

                context.player?.sendMessage(TextComponent(text), Util.NIL_UUID)
                return InteractionResult.SUCCESS
            }
            return InteractionResult.PASS
        }
        return InteractionResult.PASS
    }
}
