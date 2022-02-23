package org.eln2.mc.common.cell

interface ISingleElementGuiCell<N : Number> {

    fun getGuiValue(): N
    fun setGuiValue(value: N)

}
