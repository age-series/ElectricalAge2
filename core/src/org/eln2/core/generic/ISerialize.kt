package org.eln2.core.generic

/*
NOTE: This code is strictly experimental
 */

interface ISerialize {

    // If the serialized format contains the value, then it will be returned. If a key does not exist, null is returned.
    fun getInt(key: String): Int?
    fun getBool(key: String): Boolean?
    fun getDouble(key: String): Double?
    fun getString(key: String): String?

    fun getNested(key: String): ISerialize?

    // We only accept values that are known to be not null/invalid
    fun setInt(key: String, value: Int)
    fun setBool(key: String, value: Boolean)
    fun setDouble(key: String, value: Double)
    fun setString(key: String, value: String)

    fun setNested(key: String, value: ISerialize)
}