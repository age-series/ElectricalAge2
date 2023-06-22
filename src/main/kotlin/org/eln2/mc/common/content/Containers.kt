package org.eln2.mc.common.content

import net.minecraft.world.item.Item
import org.eln2.mc.data.Quantity
import org.eln2.mc.data.Volume

// I don't like this...

class MassContainerItem(val volume: Quantity<Volume>) : Item(Properties())
