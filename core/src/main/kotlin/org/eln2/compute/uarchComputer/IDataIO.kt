package org.eln2.compute.uarchComputer

/**
 * An interface representing a set of IO pins.
 */
@ExperimentalUnsignedTypes
interface IDataIO {
    /**
     * Imports an integer's worth of data from the IO Pins.
     * @return the imported data
     */
    fun readData(): UInt

    /**
     * Sets the IO pins to export the given integer.
     * @param data the data to export
     */
    fun writeData(data: UInt)
}
