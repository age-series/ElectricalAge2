package org.eln2.mc.utility

import net.minecraft.network.chat.BaseComponent
import net.minecraft.network.chat.TextComponent

object UnitComponent {
    fun unitComponent(translationKey: String, value: Double, baseUnit: UnitType): BaseComponent {
        return TextComponent("")
    }
}
