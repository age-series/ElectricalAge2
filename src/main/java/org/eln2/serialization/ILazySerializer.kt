package org.eln2.serialization

/*
NOTE: This code is strictly experimental
 */

interface ILazySerializer {
    var isLazy: Boolean

    fun commit()
}