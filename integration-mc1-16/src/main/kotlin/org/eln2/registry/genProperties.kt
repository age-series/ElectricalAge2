package org.eln2.registry

import net.minecraft.item.Item

fun genProperties(maxStack: Int): Item.Properties {
    val prop = Item.Properties()
    prop.maxStackSize(maxStack)
    return prop
}
