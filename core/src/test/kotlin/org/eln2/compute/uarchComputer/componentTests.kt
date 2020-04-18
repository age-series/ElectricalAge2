package org.eln2.compute.uarchComputer

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@ExperimentalUnsignedTypes
@ExperimentalStdlibApi
class componentTests {
	@Test
	fun testALU() {
		val alu = ALU(20u,40u)
		alu.currState = ALU.ALUOps.Add
		alu.step()
		alu.carry = true
		alu.currState = ALU.ALUOps.Adc
		alu.step()
		Assertions.assertEquals(alu.inputA, 101u)
		alu.currState = ALU.ALUOps.Sub
		alu.step()
		alu.currState = ALU.ALUOps.Sbb
		alu.step()
		Assertions.assertEquals(alu.inputA, 21u)
		alu.currState = ALU.ALUOps.Not
		alu.step()
		alu.currState = ALU.ALUOps.And
		alu.step()
		Assertions.assertEquals(alu.inputA, 40u)
		alu.currState = ALU.ALUOps.Zero
		Assertions.assertEquals(alu.inputA, 0u)
		alu.currState = ALU.ALUOps.One
		alu.step()
		alu.currState = ALU.ALUOps.Shr
		alu.step()
		alu.currState = ALU.ALUOps.Shl
		alu.step()
		alu.step()
		Assertions.assertEquals(alu.inputA, 2u)
		alu.currState = ALU.ALUOps.Xor
		alu.step()
		Assertions.assertEquals(alu.inputA 42u)
		alu.currState = ALU.ALUOps.MinusOne
		alu.inputB = 0u
		alu.step()
		alu.currState = ALU.ALUOps.Nor
		alu.step()
		alu.currState = ALU.ALUOps.Nand
		alu.step()
		alu.currState = ALU.ALUOps.Xnor
		alu.step()
		Assertions.assertEquals(alu.inputA, 0u)
	}
}
