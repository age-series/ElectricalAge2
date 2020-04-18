package org.eln2.compute.uarchComputer

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@ExperimentalUnsignedTypes
@ExperimentalStdlibApi
class componentTests {
	@Test
	fun testALU() {
		val alu = ALU(0u,0x42u)
		val aluOpsToTest = arrayOf(
			ALU.ALUOps.Add, ALU.ALUOps.Adc, ALU.ALUOps.And, ALU.ALUOps.MinusOne, ALU.ALUOps.Nand, ALU.ALUOps.Nor, ALU.ALUOps.Not,
			ALU.ALUOps.One, ALU.ALUOps.Shl, ALU.ALUOps.Shr, ALU.ALUOps.Sub, ALU.ALUOps.Sbb, ALU.ALUOps.Xnor, ALU.ALUOps.Xor,
			ALU.ALUOps.Zero
		)
		val inputAArray = arrayOf(
			0xFFFFFFFFu, 0u, 0x41u, 0u, 0x41u, 0x41u, 0xFFFF0000u,
			0u, 0x41u, 0x41u, 0x41u, 0x41u, 0x41u, 0x41u,
			0xC0FFEEu
		)
		val expected = arrayOf(
			0x41u, 0x43u, 0x40u, 0xFFFFFFFFu, 0xFFFFFFBFu, 0xFFFFFFBCu, 0x0000FFFFu,
			1u, 0x82u, 0x20u, 0xFFFFFFFFu, 0xFFFFFFFFu, 0xFFFFFFFCu, 0x03u,
			0u
		)
		for ( test in aluOpsToTest.zip(inputAArray.zip(expected))) {
			alu.currState = test.first
			alu.inputA = test.second.first
			alu.step()
			Assertions.assertEquals(alu.inputA, test.second.second)
		}

	}
}
