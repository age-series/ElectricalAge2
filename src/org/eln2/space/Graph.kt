package org.eln2.space

import java.util.*

open class Object(val locator: Locator) {
    var concom: ConComRef? = null
    // Only safe to use after this has been added to a space
    val space: Space get() = concom!!.cc.space

    fun canConnectTo(other: Object, isA: Boolean): Boolean = true
    fun connectTo(other: Object, isA: Boolean) {
        if(isA) {
            other.concom = concom
        }
    }
}

data class ConComRef(var cc: ConnectedComponent)

class ConnectedComponent(val space: Space) {
    var objects: MutableSet<Object> = mutableSetOf()

    fun add(obj: Object) {
        obj.concom = ConComRef(this)
    }
    
    fun add(vararg obj: Object) {
        obj.forEach { add(it) }
    }
}

open class Space {
    val components: WeakHashMap<ConnectedComponent, Unit> = WeakHashMap()
    val objectLocators: MutableMap<Locator, Object> = mutableMapOf()
    
    fun add(obj: Object) {
        objectLocators[obj.locator] = obj
        val concom = ConnectedComponent(this)
        concom.add(obj)
        components[concom] = Unit
        merge(obj)
    }
    
    fun add(vararg obj: Object) {
        obj.forEach { add(it) }
    }
    
    protected fun merge(obj: Object) {
        obj.locator.neighbors().forEach {
            val objb = objectLocators.get(it)
            if(objb != null && obj.locator.canConnect(it) && it.canConnect(obj.locator) && obj.canConnectTo(objb, true) && objb.canConnectTo(obj, false)) {
                obj.connectTo(objb, true)
                objb.connectTo(obj, false)
            }
        }
    }
}