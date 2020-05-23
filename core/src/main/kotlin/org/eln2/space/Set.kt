package org.eln2.space
// A class for using the Disjoint Sets "Union-Find" algorithm
open class Set() {
	var size: Int = 1
	var parent: Set = this

	// The "Find" algorithm with path splitting
	val representative: Set
		get() {
			var cur = this
			while (cur.parent != cur) {
				val next = cur.parent
				cur.parent = next.parent
				cur = next
			}
			return cur
		}

	// The "Union" algorithm by size
	fun unite(other: Set) {
		val trep = representative
		val orep = other.representative
		if (trep == orep) return

		val (bigger, smaller) = if (trep.size < orep.size) Pair(orep, trep) else Pair(trep, orep)
		smaller.parent = bigger.parent
		bigger.size += smaller.size
	}
}
