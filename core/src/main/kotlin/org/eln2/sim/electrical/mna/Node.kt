package org.eln2.sim.electrical.mna

import org.eln2.debug.dprintln

open class Node(var circuit: Circuit) : IDetail {
	open var potential: Double = 0.0
	open var index: Int = -1  // Assigned by Circuit
	open val isGround = false
	var name = "node"

	override fun detail(): String {
		return "[node val: $potential]"
	}

	/* Determine which node should prevail when two are merged in a Circuit.

	   This is mostly so subclasses of Node (if any) can maintain their existence when merged. The Node returning the
	   higher value is chosen; if both are equal (commonly the case), one is chosen arbitrarily.
	 */

	open fun mergePrecedence(other: Node): Int = 0

	fun stampResistor(to: Node, r: Double) {
		dprintln("N.sR $to $r")
		circuit.stampResistor(index, to.index, r)
	}
}

class GroundNode(circuit: Circuit) : Node(circuit) {
	override var potential: Double
		get() = 0.0
		set(value) {}

	override var index: Int
		get() = -1
		set(value) {}

	override val isGround = true

	override fun mergePrecedence(other: Node): Int = 100
}

data class NodeRef(var node: Node)
