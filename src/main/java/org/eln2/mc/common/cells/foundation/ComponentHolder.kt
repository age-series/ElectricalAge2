package org.eln2.mc.common.cells.foundation

import org.ageseries.libage.sim.electrical.mna.component.Component
import org.eln2.mc.common.cells.foundation.objects.ElectricalComponentInfo
import org.eln2.mc.extensions.LibAgeExtensions.connect

fun interface IComponentFactory<T> {
    fun create(): T
}

class ComponentHolder<T : Component>(private val factory: IComponentFactory<T>) {
    private var value: T? = null

    val instance: T get() {
        if(value == null) {
            value = factory.create()
        }

        return value!!
    }

    fun connect(pin: Int, component: Component, remotePin: Int){
        instance.connect(pin, component, remotePin)
    }

    fun connect(pin: Int, componentInfo: ElectricalComponentInfo){
        instance.connect(pin, componentInfo)
    }

    fun connectInternal(component: Component, remotePin: Int){
        connect(Conventions.INTERNAL_PIN, component, remotePin)
    }

    fun connectInternal(componentInfo: ElectricalComponentInfo){
        connectInternal(componentInfo.component, componentInfo.index)
    }

    fun connectExternal(component: Component, remotePin: Int){
        connect(Conventions.EXTERNAL_PIN, component, remotePin)
    }

    fun connectExternal(componentInfo: ElectricalComponentInfo){
        connectExternal(componentInfo.component, componentInfo.index)
    }

    fun ground(pin: Int){
        instance.ground(pin)
    }

    fun groundInternal(){
        ground(Conventions.INTERNAL_PIN)
    }

    fun groundExternal(){
        ground(Conventions.EXTERNAL_PIN)
    }

    fun offerInternal(): ElectricalComponentInfo{
        return ElectricalComponentInfo(instance, Conventions.INTERNAL_PIN)
    }

    fun offerExternal(): ElectricalComponentInfo{
        return ElectricalComponentInfo(instance, Conventions.EXTERNAL_PIN)
    }

    fun clear() {
        value = null
    }

    val isPresent get() = value != null

    fun ifPresent(action: ((T) -> Unit)): Boolean {
        if(value == null){
            return false
        }

        action(value!!)

        return true
    }
}
