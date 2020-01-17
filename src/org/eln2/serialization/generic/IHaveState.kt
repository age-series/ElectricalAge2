package org.eln2.serialization.generic

/*
NOTE: This code is strictly experimental
 */

interface IHaveState {
    fun save(ss: ISerialize)
    fun load(ss: ISerialize)
}