package org.eln2.compute.uarchComputer

@ExperimentalStdlibApi
@ExperimentalUnsignedTypes
class uarchComputer(var ioPinsGroup1: IDataIO, var ioPinsGroup2: IDataIO, val uROM: IuOPROM) {
	private var uOP_IP: Short = 0
	private var busValue = 0u
	private var uOPDecoder = 0u
	private var alu = ALU(0u,0u)
	private var FPU = Array<UInt>(4) {0u}
	private var PMRegs = Array<UInt>(8) {0u}
	private var GPRegs = Array<UInt>(8) {0u}

	enum class uState { Halted, Running0, Running1 }
	var currState = uState.Halted

	enum class FPUState { Zero, Int, Neg, Inv, Sqrt, Sqre, Ln, Exp, Add, Mult, Sub, Div, Cos, ToDouble, One, MinusOne }
	var currFPUState = FPUState.Zero

	private fun getBit(byte: UByte, bit: Int) = byte.and(1.shl(bit).toUByte()) == 1.shl(bit).toUByte()

	private fun die(uOP: UByte) {
		println("Processor died on invalid uOP \"$uOP\"")
		currState = uState.Halted;
	}

	fun step() {
		if (currState == uState.Halted) {
			uOP_IP = 0
			currState = uState.Running0
		}

		val uOP = uROM.readROMAt(uOP_IP)
		if ( !(getBit(uOP, 7) xor (currState == uState.Running1)) ) {
			if(getBit(uOP, 6) && getBit(uOP, 5)) {

			} else if (getBit(uOP, 6)){
				if(getBit(uOP, 4)) {

				} else {

				}
			} else if (getBit(uOP, 5)){
				when (val uOp4to0 = uOP.and(31u).toInt()) {
					1 -> busValue = uOPDecoder
					4 -> busValue = ioPinsGroup1.readData()
					5 -> busValue = ioPinsGroup2.readData()
					6 -> busValue = alu.inputA
					7 -> busValue = alu.inputB
					8 -> busValue = FPU[0]
					9 -> busValue = FPU[1]
					10 -> busValue = FPU[2]
					11 -> busValue = FPU[3]
					12,13,14,15,16,17,18,19 -> busValue = PMRegs[uOp4to0-12]
					20,21,22,23,24,25,26,27 -> busValue = GPRegs[uOp4to0-20]
					else -> die(uOP)
				}
			} else {
				when (val uOp4to0 = uOP.and(31u).toInt()) {
					1 -> uOPDecoder = busValue
					4 -> ioPinsGroup1.writeData(busValue)
					5 -> ioPinsGroup2.writeData(busValue)
					6 -> alu.inputA = busValue
					7 -> alu.inputB = busValue
					8 -> FPU[0] = busValue
					9 -> FPU[1] = busValue
					10 -> FPU[2] = busValue
					11 -> FPU[3] = busValue
					12,13,14,15,16,17,18,19 -> PMRegs[uOp4to0-12] = busValue
					20,21,22,23,24,25,26,27 -> GPRegs[uOp4to0-20] = busValue
					else -> die(uOP)
				}
			}
		}
	}


}
