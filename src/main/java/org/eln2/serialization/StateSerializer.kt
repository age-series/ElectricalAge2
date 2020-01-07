package org.eln2.serialization

/*
NOTE: This code is strictly experimental
 */

abstract class StateSerializer {

    // If the serialized format contains the value, then it will be returned. If a key does not exist, null is returned.
    abstract fun getInt(key: String): Int?
    abstract fun getBool(key: String): Boolean?
    abstract fun getDouble(key: String): Double?
    abstract fun getString(key: String): String?

    abstract fun getNested(key: String): StateSerializer?

    // We only accept values that are known to be not null/invalid
    abstract fun setInt(key: String, value: Int)
    abstract fun setBool(key: String, value: Boolean)
    abstract fun setDouble(key: String, value: Double)
    abstract fun setString(key: String, value: String)

    abstract fun setNested(key: String, value: StateSerializer)
}