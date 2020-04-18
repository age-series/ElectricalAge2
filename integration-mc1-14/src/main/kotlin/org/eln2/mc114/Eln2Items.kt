package org.eln2.mc114

import org.eln2.mc114.singleBlock.FlubberItem

class Eln2Items {
	companion object {
		lateinit var flubberItem: FlubberItem

		fun register() {
			flubberItem = FlubberItem()
		}
	}
}
