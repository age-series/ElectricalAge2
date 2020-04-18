package org.eln2.mc114

import cam72cam.mod.item.CreativeTab
import cam72cam.mod.item.ItemStack

class Eln2CreativeTabs {
	companion object {
		val MAIN_TAB = CreativeTab(Eln2.MODID + ".main") { ItemStack(Eln2Items.flubberItem, 1) }

		fun register() {}
	}
}
