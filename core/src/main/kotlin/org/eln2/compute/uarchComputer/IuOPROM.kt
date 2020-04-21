package org.eln2.compute.uarchComputer

@ExperimentalUnsignedTypes
interface IuOPROM {
	fun decode(data: UInt): UShort
	fun readROMAt(ptr: UShort): UByte
}
