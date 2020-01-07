package org.eln2.serialization

interface ILazySerializer {
    var isLazy: Boolean

    fun commit()
}