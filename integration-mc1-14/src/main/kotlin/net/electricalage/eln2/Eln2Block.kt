package net.electricalage.eln2

import net.electricalage.eln2.singleBlock.FlubberBlock

class Eln2Block {
	companion object {
		lateinit var flubberBlock: FlubberBlock

		fun register() {
			flubberBlock = FlubberBlock()
		}
	}
}
