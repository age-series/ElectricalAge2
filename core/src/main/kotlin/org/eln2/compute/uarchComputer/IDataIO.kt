package org.eln2.compute.uarchComputer

/*
 * An interface representing a set of IO pins.
 */
@ExperimentalUnsignedTypes
interface IDataIO {
	/*
	 * Imports an integer's worth of data from the IO Pins.
	 */
	fun readData(): UInt

	/*
	 * Sets the IO pins to export the given integer.
	 */
	fun writeData(data: UInt)
}
