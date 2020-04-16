package org.eln2.compute.asmComputer

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AsmComputerTest {

	@Test
	fun testAddI() {
		val computer = AsmComputer()
		computer.stringRegisters["cra"]?.contents = "addi ia 2\naddi ia 4\naddi ia ia ia"
		computer.step()
		computer.step()
		computer.step()
		Assertions.assertEquals(true, computer.intRegisters["ia"]?.contents == 0 + 2 + 4 + 6 + 6)
	}

	@Test
	fun testSubI() {
		val computer = AsmComputer()
		computer.stringRegisters["cra"]?.contents = "subi ib 2\nsubi ib 2"
		computer.step()
		computer.step()
		Assertions.assertEquals(true, computer.intRegisters["ib"]?.contents == 0 - 2 - 2)
	}

	@Test
	fun testAddD() {
		val computer = AsmComputer()
		computer.stringRegisters["cra"]?.contents = "addd dx 3.14\naddd dx dx"
		computer.step()
		computer.step()
		val testTopRange = (0.0 + 3.14 + 3.14) + 0.01
		val testBottomRange = (0.0 + 3.14 + 3.14) - 0.01
		val result = computer.doubleRegisters["dx"]?.contents?: 0.0
		Assertions.assertEquals(true, (result < testTopRange) && (result > testBottomRange))
	}

	@Test
	fun testSubD() {
		val computer = AsmComputer()
		computer.stringRegisters["cra"]?.contents = "subd dx 3.14\nsubd dx 5.5"
		computer.step()
		computer.step()
		val testTopRange = (0.0 - 3.14 - 5.5) + 0.01
		val testBottomRange = (0.0 - 3.14 - 5.5) - 0.01
		val result = computer.doubleRegisters["dx"]?.contents?: 0.0
		Assertions.assertEquals(true, (result < testTopRange) && (result > testBottomRange))
	}

	@Test
	fun testMoveInt() {
		val computer = AsmComputer()
		computer.stringRegisters["cra"]?.contents = "move ia 1\nmove ib ia"
		computer.step()
		computer.step()
		Assertions.assertEquals(true, computer.intRegisters["ib"]?.contents == 1)
	}

	@Test
	fun testMoveDouble() {
		val computer = AsmComputer()
		computer.stringRegisters["cra"]?.contents = "addd dx 5.0\nmove dx 1.0\nmove dy dx"
		computer.step()
		println(computer.doubleRegisters)
		computer.step()
		println(computer.doubleRegisters)
		computer.step()
		println(computer.doubleRegisters)
		Assertions.assertEquals(true, computer.doubleRegisters["dy"]?.contents == 1.0)
	}

	@Test
	fun testMoveString() {
		val computer = AsmComputer()
		computer.stringRegisters["cra"]?.contents = "move sx \"Hello\"\nmove sy sx"
		computer.step()
		computer.step()
		Assertions.assertEquals(true, computer.stringRegisters["sy"]?.contents == "Hello")
	}

	@Test
	fun codeFromVariable() {
		val computer = AsmComputer()
		computer.stringRegisters["cra"]?.contents = "move crb sy\nswch 0"
		computer.stringRegisters["sy"]?.contents = "move ia 1"
		computer.step()
		computer.step()
		computer.step()
		Assertions.assertEquals(true, computer.intRegisters["ia"]?.contents == 1)
	}
}
