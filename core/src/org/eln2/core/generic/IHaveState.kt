package org.eln2.core.generic

/*
NOTE: This code is strictly experimental
 */

interface IHaveState {
    fun save(ss: ISerialize)
    fun load(ss: ISerialize)
}