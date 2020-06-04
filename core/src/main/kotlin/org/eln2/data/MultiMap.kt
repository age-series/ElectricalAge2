package org.eln2.data

/**
 * A helper class for representing a mapping as a Map.Entry.
 */
data class MultiMapEntry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>

/**
 * A MultiMap: a data structure that maps one key (K) to zero or more values (V).
 *
 * This is in contrast to the usual Map, which maps one key to at most one value.
 *
 */
interface MultiMap<K, V> {
    /**
     * Tests whether a key [k] is in the MultiMap.
     *
     * This is true if k is associated with at least one value.
     */
    operator fun contains(k: K): Boolean = get(k).isNotEmpty()

    /**
     * Get the set of all values associated with key [k].
     */
    operator fun get(k: K): Set<V>

    /**
     * Get a single, arbitrary value associated with key [k].
     *
     * If that association is empty, returns null.
     */
    fun one(k: K): V? {
        val iter = get(k).iterator()
        return if (iter.hasNext()) iter.next() else null
    }

    /**
     * An iterator over every Entry<K, Set<V>>, of size keySize.
     */
    val keyMapping: Iterable<Map.Entry<K, Set<V>>>

    /**
     * The number of keys, value sets, and keyMapping entries in this MultiMap.
     *
     * A naive, O(n) implementation might be keyMapping.count(), but you are encouraged to implement a faster one if at all possible. Implementors should document their complexity.
     */
    val keyMappingSize: Int

    /**
     * The set of all keys.
     */
    val keys: Set<K> get() = keyMapping.map { (k, _) -> k }.toSet()

    /**
     * The set of all sets of values.
     *
     * This will have the same size as keySize.
     */
    val valueSets: Collection<Set<V>> get() = keyMapping.map { (_, vs) -> vs }

    /**
     * The unique values in this MultiMap.
     *
     * This is often at least O(n * UNION), where UNION is the cost of set union.
     */
    val uniqueValues: Set<V>

    /**
     * The number of unique values in this MultiMap.
     */
    val uniqueValuesSize: Int get() = uniqueValues.size

    /**
     * An iterator over every Entry<K, V>, where K may be repeated for each value.
     */
    val entries: Iterable<Map.Entry<K, V>>

    /**
     * The number of pairings in this MultiMap.
     *
     * A naive, O(n) implementation might be entries.count(), but you are encouraged to implement a faster one if at all possible. Implementors should document their complexity.
     */
    val entriesSize: Int
}

/**
 * A Mutable version of the [MultiMap] interface.
 */
interface MutableMultiMap<K, V> : MultiMap<K, V> {
    /**
     * Get the set of values for the key [k].
     *
     * Note that the return is mutable; one can add or remove entries from this map by mutating it.
     */
    override operator fun get(k: K): MutableSet<V>

    /**
     * Add an association from [k] to [v] (in addition to any other associations involving [k] or [v]).
     */
    operator fun set(k: K, v: V) = get(k).add(v)

    /**
     * Clear all values associated with the key [k].
     */
    fun clear(k: K)

    /**
     * Remove an association between [k] and [v].
     *
     * If the map no longer contains [k], [clear] is invoked (as it usually performs some implementation-specific cleanup).
     */
    fun remove(k: K, v: V) {
        val s = get(k)
        s.remove(v)
        if (s.isEmpty()) clear(k)
    }
}

/**
 * An implementor of [MutableMultiMap] using the Kotlin stdlib.
 *
 * This class is defined here using a MutableMap<K, MutableSet<V>> idiom which is compatible with the Kotlin stdlib. Other, more performant implementations exist, and can be swapped in here if needed.
 */
class MutableSetMapMultiMap<K, V>(iter: Iterator<Pair<K, V>>) : MutableMultiMap<K, V> {
    val map: MutableMap<K, MutableSet<V>> = mutableMapOf()

    constructor() : this(emptyList<Pair<K, V>>().iterator())

    init {
        iter.forEach { (k, v) -> set(k, v) }
    }

    override operator fun get(k: K): MutableSet<V> = map.getOrPut(k, { mutableSetOf() })

    override fun clear(k: K) {
        map.remove(k)
    }

    override val keyMapping: Iterable<Map.Entry<K, Set<V>>> = map.entries

    /** This implementation is O(1). */
    override val keyMappingSize: Int get() = map.size
    override val keys get() = map.keys
    override val valueSets: Collection<Set<V>> = map.values
    override val uniqueValues: Set<V> get() = map.values.fold(emptySet()) { next, acc -> acc.union(next.toSet()) }
    override val entries: Iterable<Map.Entry<K, V>>
        get() = map.entries.flatMap { (k, vs) ->
            vs.map {
                MultiMapEntry(
                    k,
                    it
                )
            }
        }

    /** This implementation is O(keyMappingSize). */
    override val entriesSize: Int
        get() = map.entries.map { (_, vs) -> vs.size }.sum()

    override fun toString() = map.toString()
}

/**
 * View a MutableMultiMap as a MultiMap, essentially removing mutability from the interface.
 */
class ImmutableMultiMapView<K, V>(val inner: MutableMultiMap<K, V>) : MultiMap<K, V> {
    override inline fun contains(k: K): Boolean = k in inner
    override inline fun get(k: K): Set<V> = inner[k]
    override inline fun one(k: K): V? = inner.one(k)
    override val keyMapping: Iterable<Map.Entry<K, Set<V>>> inline get() = inner.keyMapping
    override val keyMappingSize: Int inline get() = inner.keyMappingSize
    override val keys: Set<K> inline get() = inner.keys
    override val valueSets: Collection<Set<V>> inline get() = inner.valueSets
    override val uniqueValues: Set<V> inline get() = inner.uniqueValues
    override val uniqueValuesSize: Int inline get() = inner.uniqueValuesSize
    override val entries: Iterable<Map.Entry<K, V>> inline get() = inner.entries
    override val entriesSize: Int inline get() = inner.entriesSize
}

/** An [ImmutableMultiMapView] implemented using a [MutableSetMapMultiMap], constructed with an initial mapping.
 *
 * Note that this does not name a type.
 */
fun <K, V> SetMapMultiMap(iter: Iterator<Pair<K, V>>) = ImmutableMultiMapView(MutableSetMapMultiMap(iter))

/**
 * An [ImmutableMultiMapView] implemented using a [MutableSetMapMultiMap], constructed empty.
 */
fun <K, V> SetMapMultiMap() = ImmutableMultiMapView(MutableSetMapMultiMap<K, V>())

/**
 * Create an empty MultiMap.
 *
 * The choice of map is an implementation detail, but chosen to be a good compromise for general use.
 */
fun <K, V> emptyMultiMap(): MultiMap<K, V> = SetMapMultiMap<K, V>()

/**
 * Creates a MultiMap over the given pairs (`a to b`).
 *
 * The choice of map is an implementation detail, but chosen to be a good compromise for general use.
 */
fun <K, V> multiMapOf(vararg pairs: Pair<K, V>): MultiMap<K, V> = SetMapMultiMap(pairs.iterator())

/**
 * Creates a MutableMultiMap over the given pairs (`a to b`).
 *
 * The choice of map is an implementation detail, but chosen to be a good compromise for general use.
 */
fun <K, V> mutableMultiMapOf(vararg pairs: Pair<K, V>): MutableMultiMap<K, V> = MutableSetMapMultiMap(pairs.iterator())

/**
 * Creates a MultiMap from the iterator over pairs (`a to b`).
 */
fun <K, V> Iterator<Pair<K, V>>.toMultiMap(): MultiMap<K, V> = SetMapMultiMap(this)

/**
 * Creates a MutableMultiMap from the iterator over pairs.
 */
fun <K, V> Iterator<Pair<K, V>>.toMutableMultiMap(): MutableMultiMap<K, V> = MutableSetMapMultiMap(this)

/**
 * Creates a MultiMap from an iterable over pairs.
 */
fun <K, V> Iterable<Pair<K, V>>.toMultiMap(): MultiMap<K, V> = iterator().toMultiMap()

/**
 * Creates a MutableMultiMap from an iterable over pairs.
 */
fun <K, V> Iterable<Pair<K, V>>.toMutableMultiMap(): MutableMultiMap<K, V> = iterator().toMutableMultiMap()

/**
 * Creates a MultiMap from a Map, with each key associated to a set of size at most one.
 */
fun <K, V> Map<K, V>.toMultiMap(): MultiMap<K, V> = entries.map { it.toPair() }.toMultiMap()

/**
 * Creates a MutableMultiMap from a Map, with each key associated to a set of size at most one.
 */
fun <K, V> Map<K, V>.toMutableMultiMap(): MutableMultiMap<K, V> = entries.map { it.toPair() }.toMutableMultiMap()
