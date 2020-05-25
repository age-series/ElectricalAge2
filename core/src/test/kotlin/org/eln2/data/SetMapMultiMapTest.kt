package org.eln2.data

import org.eln2.debug.dprintln
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

val TEST_MAP: Map<Int, Int> = mapOf(1 to 2, 2 to 4, 4 to 8, 8 to 16, 16 to 32)
val TEST_MULTIMAP: List<Pair<Int, Int>> = listOf(
    Pair(1, 2),
    Pair(2, 1), Pair(2, 2),
    Pair(4, 1), Pair(4, 2), Pair(4, 3), Pair(4, 4),
    Pair(8, 1), Pair(8, 2), Pair(8, 3), Pair(8, 4), Pair(8, 5), Pair(8, 6), Pair(8, 7), Pair(8, 8)
)
val TEST_MULTIMAP_KEYS = 4
val TEST_MULTIMAP_UNIQUE_VALUES = 8
val TEST_NONKEY = 7

// FIXME: abstract this so that the Mutable version below isn't LITERALLY an unmaintainable copy of this
internal class SetMapMultiMapTest {

    // FIXME: Kotlin can't seem to understand Pair<K,V> in the parameter below, so concrete types only for now
    protected fun makeImplementation(iter: List<Pair<Int, Int>>): MultiMap<Int, Int> = SetMapMultiMap(iter.iterator())

    @Test
    fun emptySizes() {
        val map = makeImplementation(emptyList())

        assertEquals(map.entriesSize, 0)
        assertEquals(map.keyMappingSize, 0)
        assertEquals(map.uniqueValuesSize, 0)
    }

    @Test
    fun emptyCollections() {
        val map = makeImplementation(emptyList())

        assertEquals(map.entries.count(), 0)
        assertEquals(map.keyMapping.count(), 0)
        assertEquals(map.uniqueValues.size, 0)
    }

    @Test
    fun singleMapConsistency() {
        val map = makeImplementation(TEST_MAP.entries.map { it.toPair() })

        assertEquals(map.entriesSize, TEST_MAP.size)
        assertEquals(map.entriesSize, map.entries.count())
        assertEquals(map.keyMappingSize, TEST_MAP.size)
        assertEquals(map.keyMappingSize, map.keyMapping.count())
        assertEquals(map.uniqueValuesSize, TEST_MAP.size)
        assertEquals(map.uniqueValuesSize, map.uniqueValues.size)

        TEST_MAP.entries.forEach { (k, v) ->
            assert(k in map)
            val set = map[k]
            assertEquals(set.size, 1)
            assertEquals(set.iterator().next(), v)
        }

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())
    }

    @Test
    fun multiMapConsistency() {
        val map = makeImplementation(TEST_MULTIMAP)

        dprintln("multimap keymappings ${map.keyMapping} entries: ${map.entries} size ${map.entriesSize}")
        assertEquals(map.entriesSize, TEST_MULTIMAP.size)
        assertEquals(map.entriesSize, map.entries.count())
        assertEquals(map.keyMappingSize, TEST_MULTIMAP_KEYS)
        assertEquals(map.keyMappingSize, map.keyMapping.count())
        assertEquals(map.uniqueValuesSize, TEST_MULTIMAP_UNIQUE_VALUES)
        assertEquals(map.uniqueValuesSize, map.uniqueValues.size)

        TEST_MULTIMAP.forEach { (k, v) ->
            assert(k in map)
            val set = map[k]
            assert(v in set)
        }

        map.keyMapping.forEach { (k, vs) ->
            assertEquals(k, vs.size)
        }

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())
    }
}

internal class MutableSetMapMultiMapTest {
    // ------------- START PASTING THE BODY OF THE CLASS ABOVE HERE --------------

    // FIXME: Kotlin can't seem to understand Pair<K,V> in the parameter below, so concrete types only for now
    protected fun makeImplementation(iter: List<Pair<Int, Int>>): MutableMultiMap<Int, Int> =
        MutableSetMapMultiMap(iter.iterator())

    @Test
    fun emptySizes() {
        val map = makeImplementation(emptyList())

        assertEquals(map.entriesSize, 0)
        assertEquals(map.keyMappingSize, 0)
        assertEquals(map.uniqueValuesSize, 0)
    }

    @Test
    fun emptyCollections() {
        val map = makeImplementation(emptyList())

        assertEquals(map.entries.count(), 0)
        assertEquals(map.keyMapping.count(), 0)
        assertEquals(map.uniqueValues.size, 0)
    }

    @Test
    fun singleMapConsistency() {
        val map = makeImplementation(TEST_MAP.entries.map { it.toPair() })

        assertEquals(map.entriesSize, TEST_MAP.size)
        assertEquals(map.entriesSize, map.entries.count())
        assertEquals(map.keyMappingSize, TEST_MAP.size)
        assertEquals(map.keyMappingSize, map.keyMapping.count())
        assertEquals(map.uniqueValuesSize, TEST_MAP.size)
        assertEquals(map.uniqueValuesSize, map.uniqueValues.size)

        TEST_MAP.entries.forEach { (k, v) ->
            assert(k in map)
            val set = map[k]
            assertEquals(set.size, 1)
            assertEquals(set.iterator().next(), v)
        }

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())
    }

    @Test
    fun multiMapConsistency() {
        val map = makeImplementation(TEST_MULTIMAP)

        assertEquals(map.entriesSize, TEST_MULTIMAP.size)
        assertEquals(map.entriesSize, map.entries.count())
        assertEquals(map.keyMappingSize, TEST_MULTIMAP_KEYS)
        assertEquals(map.keyMappingSize, map.keyMapping.count())
        assertEquals(map.uniqueValuesSize, TEST_MULTIMAP_UNIQUE_VALUES)
        assertEquals(map.uniqueValuesSize, map.uniqueValues.size)

        TEST_MULTIMAP.forEach { (k, v) ->
            assert(k in map)
            val set = map[k]
            assert(v in set)
        }

        map.keyMapping.forEach { (k, vs) ->
            assertEquals(k, vs.size)
        }

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())
    }
    // ------------ STOP PASTING HERE --------------

    @Test
    fun buildSingleMapWithSetOperator() {
        val map = makeImplementation(emptyList())

        TEST_MAP.entries.forEach { (k, v) ->
            map[k] = v
        }

        assertEquals(map.entriesSize, TEST_MAP.size)
        assertEquals(map.entriesSize, map.entries.count())
        assertEquals(map.keyMappingSize, TEST_MAP.size)
        assertEquals(map.keyMappingSize, map.keyMapping.count())
        assertEquals(map.uniqueValuesSize, TEST_MAP.size)
        assertEquals(map.uniqueValuesSize, map.uniqueValues.size)

        TEST_MAP.entries.forEach { (k, v) ->
            assert(k in map)
            val set = map[k]
            assertEquals(set.size, 1)
            assertEquals(set.iterator().next(), v)
        }

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())
    }

    @Test
    fun buildSingleMapMutatingSet() {
        val map = makeImplementation(emptyList())

        TEST_MAP.entries.forEach { (k, v) ->
            map[k].add(v)
        }

        assertEquals(map.entriesSize, TEST_MAP.size)
        assertEquals(map.entriesSize, map.entries.count())
        assertEquals(map.keyMappingSize, TEST_MAP.size)
        assertEquals(map.keyMappingSize, map.keyMapping.count())
        assertEquals(map.uniqueValuesSize, TEST_MAP.size)
        assertEquals(map.uniqueValuesSize, map.uniqueValues.size)

        TEST_MAP.entries.forEach { (k, v) ->
            assert(k in map)
            val set = map[k]
            assertEquals(set.size, 1)
            assertEquals(set.iterator().next(), v)
        }

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())
    }

    @Test
    fun buildMultiMapWithSetOperator() {
        val map = makeImplementation(emptyList())

        TEST_MULTIMAP.forEach { (k, v) ->
            map[k] = v
        }

        assertEquals(map.entriesSize, TEST_MULTIMAP.size)
        assertEquals(map.entriesSize, map.entries.count())
        assertEquals(map.keyMappingSize, TEST_MULTIMAP_KEYS)
        assertEquals(map.keyMappingSize, map.keyMapping.count())
        assertEquals(map.uniqueValuesSize, TEST_MULTIMAP_UNIQUE_VALUES)
        assertEquals(map.uniqueValuesSize, map.uniqueValues.size)

        TEST_MULTIMAP.forEach { (k, v) ->
            assert(k in map)
            val set = map[k]
            assert(v in set)
        }

        map.keyMapping.forEach { (k, vs) ->
            assertEquals(k, vs.size)
        }

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())
    }

    @Test
    fun buildMultiMapMutatingSet() {
        val map = makeImplementation(emptyList())

        TEST_MULTIMAP.forEach { (k, v) ->
            map[k].add(v)
        }

        assertEquals(map.entriesSize, TEST_MULTIMAP.size)
        assertEquals(map.entriesSize, map.entries.count())
        assertEquals(map.keyMappingSize, TEST_MULTIMAP_KEYS)
        assertEquals(map.keyMappingSize, map.keyMapping.count())
        assertEquals(map.uniqueValuesSize, TEST_MULTIMAP_UNIQUE_VALUES)
        assertEquals(map.uniqueValuesSize, map.uniqueValues.size)

        TEST_MULTIMAP.forEach { (k, v) ->
            assert(k in map)
            val set = map[k]
            assert(v in set)
        }

        map.keyMapping.forEach { (k, vs) ->
            assertEquals(k, vs.size)
        }

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())
    }

    @Test
    fun nonUniqueValues() {
        val map = makeImplementation(emptyList())
        val (key, value) = Pair(1, 3)

        assert(key !in map)
        assert(map[key].isEmpty())
        assertEquals(map.uniqueValuesSize, 0)

        map[key] = value

        assert(key in map)
        assertEquals(map[key].size, 1)
        assertEquals(map.one(key), value)
        assertEquals(map.uniqueValuesSize, 1)

        map[key] = value

        assert(key in map)
        assertEquals(map[key].size, 1)
        assertEquals(map.one(key), value)
        assertEquals(map.uniqueValuesSize, 1)

        map[key].add(value)

        assert(key in map)
        assertEquals(map[key].size, 1)
        assertEquals(map.one(key), value)
        assertEquals(map.uniqueValuesSize, 1)

        map.remove(key, value)

        assert(key !in map)
        assert(map[key].isEmpty())
        assertEquals(map.uniqueValuesSize, 0)
    }

    @Test
    fun additionRemoval() {
        val map = makeImplementation(TEST_MULTIMAP)
        val value = 5

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())

        // After this point, the name becomes a bit of a lie...
        map[TEST_NONKEY] = value

        assert(TEST_NONKEY in map)
        assertEquals(map[TEST_NONKEY].size, 1)
        assertEquals(map.one(TEST_NONKEY), value)

        map.remove(TEST_NONKEY, value)

        assert(TEST_NONKEY !in map)
        assert(map[TEST_NONKEY].isEmpty())
    }
}
