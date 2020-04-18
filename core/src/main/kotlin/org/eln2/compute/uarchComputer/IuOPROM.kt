package org.eln2.compute.uarchComputer

@ExperimentalUnsignedTypes
interface IuOPROM {
	fun decode(data: UInt): Short
	fun readROMAt(ptr: Short): UByte
}
