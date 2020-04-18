package org.eln2.mc114

import org.eln2.mc114.singleBlock.FlubberBlock

class Eln2Block {
	companion object {
		lateinit var flubberBlock: FlubberBlock

		fun register() {
			flubberBlock = FlubberBlock()
		}
	}
}
