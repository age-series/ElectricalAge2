package net.electricalage.eln2

import net.electricalage.eln2.singleBlock.FlubberItem

class Eln2Items {
	companion object {
		lateinit var flubberItem: FlubberItem

		fun register() {
			flubberItem = FlubberItem()
		}
	}
}
