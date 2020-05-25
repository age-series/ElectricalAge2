package org.eln2.compute.uarchComputer

/**
 * micro Operation ROM Interface - a method to store program data for the uarchComputer
 */
@ExperimentalUnsignedTypes
interface IuOPROM {
    /**
     * decode - Take integer, where to point to into the microcode rom to execute? - function pointer table
     * @param data Place to look up instruction
     * @return A decoded instruction
     */
    fun decode(data: UInt): UShort

    /**
     * readRomAt - Read the rom at a point and return the data stored there.
     * @param ptr The location to read from
     * @return The data stored at that memory location
     */
    fun readROMAt(ptr: UShort): UByte
}
