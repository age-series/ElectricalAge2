package org.eln2.compute.uarchComputer

/**
 * uarchComputer - A basic computer with an ALU, FPU, and some load/store instructions.
 */
@ExperimentalStdlibApi
@ExperimentalUnsignedTypes
class uarchComputer(var ioPinsGroup1: IDataIO, var ioPinsGroup2: IDataIO, val uROM: IuOPROM) {
    private var uOP_IP: UShort = 0u
    private var busValue = 0u
    private var uOPDecoder = 0u
    private var alu = ALU()
    private var fpu = FPU()
    private var PMRegs = Array<UInt>(8) { 0u }
    private var GPRegs = Array<UInt>(8) { 0u }

    /**
     * u State - States of the uarchComputer
     */
    enum class uState { Halted, Running0, Running1 }

    var currState = uState.Halted
    var flagCheck = false

    private fun getBit(byte: UByte, bit: Int) = byte.and(1.shl(bit).toUByte()) == 1.shl(bit).toUByte()

    private fun die(uOP: UByte) {
        println("Processor died on invalid uOP \"$uOP\"")
        currState = uState.Halted
    }

    /**
     * step - a step of the uarchComputer. Does a single instruction.
     */
    fun step() {
        if (currState == uState.Halted) {
            uOP_IP = 0u
            currState = uState.Running0
        } else {
            uOP_IP = (uOP_IP + 1u).toUShort()
        }

        val uOP = uROM.readROMAt(uOP_IP)
        if (!(getBit(uOP, 7) xor (currState == uState.Running1))) {
            when (uOP.and(0x70u).div(16u).toInt()) {
                0, 1 -> when (val uOp4to0 = uOP.and(31u).toInt()) {
                    1 -> uOPDecoder = busValue
                    4 -> ioPinsGroup1.writeData(busValue)
                    5 -> ioPinsGroup2.writeData(busValue)
                    6 -> alu.inputA = busValue
                    7 -> alu.inputB = busValue
                    8 -> fpu.inputAH = busValue
                    10 -> fpu.inputBL = busValue
                    11 -> fpu.inputBH = busValue
                    12, 13, 14, 15, 16, 17, 18, 19 -> PMRegs[uOp4to0 - 12] = busValue
                    20, 21, 22, 23, 24, 25, 26, 27 -> GPRegs[uOp4to0 - 20] = busValue
                    else -> die(uOP)
                }
                2, 3 -> when (val uOp4to0 = uOP.and(31u).toInt()) {
                    1 -> busValue = uOPDecoder
                    4 -> busValue = ioPinsGroup1.readData()
                    5 -> busValue = ioPinsGroup2.readData()
                    6 -> busValue = alu.inputA
                    7 -> busValue = alu.inputB
                    8 -> busValue = fpu.inputAL
                    9 -> busValue = fpu.inputAH
                    10 -> busValue = fpu.inputBL
                    11 -> busValue = fpu.inputBH
                    12, 13, 14, 15, 16, 17, 18, 19 -> busValue = PMRegs[uOp4to0 - 12]
                    20, 21, 22, 23, 24, 25, 26, 27 -> busValue = GPRegs[uOp4to0 - 20]
                    else -> die(uOP)
                }
                4 -> when (uOP.and(15u).toInt()) {
                    0 -> {
                        alu.currOP = ALU.ALUOps.Zero; fpu.currOp = FPU.FPUOps.Zero
                    }
                    1 -> {
                        alu.currOP = ALU.ALUOps.And; fpu.currOp = FPU.FPUOps.Int
                    }
                    2 -> {
                        alu.currOP = ALU.ALUOps.Or; fpu.currOp = FPU.FPUOps.Neg
                    }
                    3 -> {
                        alu.currOP = ALU.ALUOps.Xor; fpu.currOp = FPU.FPUOps.Inv
                    }
                    4 -> {
                        alu.currOP = ALU.ALUOps.Not; fpu.currOp = FPU.FPUOps.Sqrt
                    }
                    5 -> {
                        alu.currOP = ALU.ALUOps.Nand; fpu.currOp = FPU.FPUOps.Sqre
                    }
                    6 -> {
                        alu.currOP = ALU.ALUOps.Nor; fpu.currOp = FPU.FPUOps.Ln
                    }
                    7 -> {
                        alu.currOP = ALU.ALUOps.Xnor; fpu.currOp = FPU.FPUOps.Exp
                    }
                    8 -> {
                        alu.currOP = ALU.ALUOps.Add; fpu.currOp = FPU.FPUOps.Add
                    }
                    9 -> {
                        alu.currOP = ALU.ALUOps.Adc; fpu.currOp = FPU.FPUOps.Mult
                    }
                    10 -> {
                        alu.currOP = ALU.ALUOps.Sub; fpu.currOp = FPU.FPUOps.Sub
                    }
                    11 -> {
                        alu.currOP = ALU.ALUOps.Sbb; fpu.currOp = FPU.FPUOps.Div
                    }
                    12 -> {
                        alu.currOP = ALU.ALUOps.Shl; fpu.currOp = FPU.FPUOps.Cos
                    }
                    13 -> {
                        alu.currOP = ALU.ALUOps.Shr; fpu.currOp = FPU.FPUOps.ToDouble
                    }
                    14 -> {
                        alu.currOP = ALU.ALUOps.One; fpu.currOp = FPU.FPUOps.One
                    }
                    15 -> {
                        alu.currOP = ALU.ALUOps.MinusOne; fpu.currOp = FPU.FPUOps.MinusOne
                    }
                }
                5 -> when (uOP.and(15u).toInt()) {
                    0 -> alu.zero = !alu.zero
                    1 -> alu.carry = !alu.carry
                    2 -> alu.parity = !alu.parity
                    3 -> alu.interupt = !alu.interupt
                    7 -> alu.sign = !alu.sign
                    8 -> flagCheck = alu.zero
                    9 -> flagCheck = alu.carry
                    10 -> flagCheck = alu.parity
                    11 -> flagCheck = alu.interupt
                    15 -> flagCheck = alu.sign
                    else -> die(uOP)
                }
                6 -> when (val uOp4to0 = uOP.and(31u).toInt()) {
                    0, 1, 2, 3, 4, 5, 6, 7 -> PMRegs[uOp4to0 - 12]++
                    8, 9, 10, 11, 12, 13, 14, 15 -> PMRegs[uOp4to0 - 12]--
                }
                7 -> when (uOP.and(15u).toInt()) {
                    0 -> uOP_IP = uROM.decode(uOPDecoder)
                    1 -> uOP_IP = uROM.readROMAt(0xFFFBu).toUInt().times(256u).plus(uROM.readROMAt(0xFFFAu)).toUShort()
                    2 -> uOP_IP = uROM.readROMAt(0xFFFDu).toUInt().times(256u).plus(uROM.readROMAt(0xFFFCu)).toUShort()
                    3 -> uOP_IP = uROM.readROMAt(0xFFFFu).toUInt().times(256u).plus(uROM.readROMAt(0xFFFEu)).toUShort()
                    4 -> currState = uState.Running0
                    5 -> currState = uState.Running1
                    6 -> if (flagCheck) {
                        currState = uState.Running0
                    }
                    7 -> if (flagCheck) {
                        currState = uState.Running1
                    }
                    8 -> alu.step()
                    9 -> fpu.step()
                    15 -> currState = uState.Halted
                    else -> die(uOP)
                }
            }
        }
    }
}
