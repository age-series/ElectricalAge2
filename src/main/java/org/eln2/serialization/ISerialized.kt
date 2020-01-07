package org.eln2.serialization

/*
NOTE: This code is strictly experimental
 */

interface ISerialized {
    fun save(): StateSerializer
    fun load(ss: StateSerializer)
}