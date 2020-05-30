package org.eln2.sim.electrical.mna

/**
 * An interface for producing a brief description useful for debug annotation.
 */
interface IDetail {
    /**
     * Return a brief, debug-friendly description of this object.
     *
     * This is allowed to construct an object; the caller should not assume this will not result in allocations (as opposed to, e.g., a static string). Do not use this in a hotpath (at least in production).
     *
     * The semantics allow this operation to result in a different String value under various circumstances; it is not safe to assume the value is constant for any given object.
     */
    fun detail(): String
}
