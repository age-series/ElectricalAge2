package org.eln2.core.generic

/*
NOTE: This code is strictly experimental
 */

interface ISerializeLazily {
    // This should always be initialized to False and changed by the end user to True.
    var isLazy: Boolean

    fun commit()
}