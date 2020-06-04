package org.eln2.data

/**
 * A class for using the Disjoint Sets "Union-Find" algorithm.
 *
 * There are two ways to use this:
 *
 * - Inherit from it, using the methods on the object itself;
 * - compose it into your class; or
 * - do both.
 *
 * Typical use case is as follows:
 *
 * 1. Choose your method above; for the former, the DisjointSet object is `this`; for the latter, substitute your field.
 * 2. Unify DisjointSets by calling [unite].
 * 3. Determine if two DisjointSets are in the same component by testing their [representative] for equality.
 * 4. Optionally, mutate the data on a DisjointSet subclass' representative, knowing that the mutations are visible from all DisjointSets with the same representative.
 *
 * Note that it is difficult to control which instance will ultimately *be* the representative; in the cases where it can't be avoided, [priority] can be used, but this is advisable only as a last resort (since it reduces the optimality of this algorithm).
*/
open class DisjointSet {

    /**
     * The size of this tree; loosely, how many Sets have been merged, including transitively, with this one.
     *
     * This value is normally only accurate for the [representative].
     */
    var size: Int = 1

    /**
     * The parent of this Set.
     *
     * Following this recursively will lead to the [representative]. All representatives refer to themselves as their parent.
     */
    var parent: DisjointSet = this

    /**
     * The priority of the merge. If set, this overrides the "merge by size" algorithm in [unite].
     *
     * It is recommended you don't do this unless you have a specific need to ensure that a certain Set always ends up being the [representative].
     */
    open val priority: Int = 0

    /**
     * Find the representative of this disjoint set.
     *
     * This instance is the same as all other instances that have been [unite]d, transitively, with this one.
     *
     * This is implemented using the "Path splitting" algorithm.
     */
    val representative: DisjointSet
        get() {
            var cur = this
            while (cur.parent != cur) {
                val next = cur.parent
                cur.parent = next.parent
                cur = next
            }
            return cur
        }

    /**
     * Unite this instance with another instance of DisjointSet.
     *
     * After this is done, both this and [other] will have the same [representative] as each other (and as all other Sets with which they were previously united).
     *
     * This is implemented using the "by size" merge algorithm, adjusted for [priority].
     */
    open fun unite(other: DisjointSet) {
        val trep = representative
        val orep = other.representative
        if (trep == orep) return
        val (bigger, smaller) = when {
            trep.priority > orep.priority -> Pair(trep, orep)
            orep.priority > trep.priority -> Pair(orep, trep)
            else -> if (trep.size < orep.size) Pair(orep, trep) else Pair(trep, orep)
        }
        smaller.parent = bigger.parent
        bigger.size += smaller.size
    }
}
