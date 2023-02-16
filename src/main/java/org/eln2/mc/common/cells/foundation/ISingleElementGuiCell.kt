package org.eln2.mc.common.cells.foundation

/**
 * ISingleElementGuiCell is a Cell with a single numerical value that will be synced to the client for GUI use
 */
interface ISingleElementGuiCell<N : Number> {

    /**
     * Get the current value from the electrical component for sending to the client
     */
    fun getGuiValue(): N

    /**
     * Set the current value to a number provided by the client
     */
    fun setGuiValue(value: N)

}
