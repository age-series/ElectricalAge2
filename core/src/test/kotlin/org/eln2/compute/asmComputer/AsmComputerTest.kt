package org.eln2.compute.asmComputer

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AsmComputerTest {

	@Test
	fun testAddI() {
		val computer = AsmComputer()
		computer.code = "addi ia 2\naddi ia 4\naddi ia ia ia"
		computer.step()
		computer.step()
		computer.step()
		Assertions.assertEquals(true, computer.intRegisters["ia"] == 0 + 2 + 4 + 6 + 6)
	}

	@Test
	fun testSubI() {
		val computer = AsmComputer()
		computer.code = "subi ib 2\nsubi ib 2"
		computer.step()
		computer.step()
		Assertions.assertEquals(true, computer.intRegisters["ib"] == 0 - 2 - 2)
	}

	@Test
	fun testAddD() {
		val computer = AsmComputer()
		computer.code = "addd dx 3.14\naddd dx dx"
		computer.step()
		computer.step()
		val testTopRange = (0.0 + 3.14 + 3.14) + 0.01
		val testBottomRange = (0.0 + 3.14 + 3.14) - 0.01
		val result = computer.doubleRegisters["dx"]?: 0.0
		Assertions.assertEquals(true, (result < testTopRange) && (result > testBottomRange))
	}

	@Test
	fun testSubD() {
		val computer = AsmComputer()
		computer.ptr = 0
		computer.code = "subd dx 3.14\nsubd dx 5.5"
		computer.step()
		computer.step()
		val testTopRange = (0.0 - 3.14 - 5.5) + 0.01
		val testBottomRange = (0.0 - 3.14 - 5.5) - 0.01
		val result = computer.doubleRegisters["dx"]?: 0.0
		Assertions.assertEquals(true, (result < testTopRange) && (result > testBottomRange))
	}
}
