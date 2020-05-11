package org.eln2.sim.electrical.mna

/**
 * An interface for producing a brief description useful for debug annotation.
 */
interface IDetail {
	/**
	 * Produce a brief, debug-friendly description of this object.
	 */
	fun detail(): String
}
