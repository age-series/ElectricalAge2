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
        val result = computer.doubleRegisters["dx"]?.contents ?: 0.0
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
        val result = computer.doubleRegisters["dx"]?.contents ?: 0.0
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
        computer.step()
        computer.step()
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

    @Test
    fun copyStringPart() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "strp sy sx 6 11"
        computer.stringRegisters["sx"]?.contents = "Hello World"
        computer.step()
        Assertions.assertEquals(true, computer.stringRegisters["sy"]?.contents == "World")
    }

    @Test
    fun stringLength() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "strl ia sx"
        computer.stringRegisters["sx"]?.contents = "Hello!"
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ia"]?.contents == 6)
    }

    @Test
    fun labelJump() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "noop\nnoop\nlabl \"doot\"\naddi ia 1\nnoop\njump \"doot\"\nnoop"
        computer.ptr = 5
        computer.step()
        computer.step()
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ia"]?.contents == 1)
    }

    @Test
    fun indexJump() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "noop\nnoop\nlabl \"doot\"\naddi ia 1\naddi ia 30\njump 3\nnoop"
        computer.ptr = 5
        computer.step()
        computer.step()
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ia"]?.contents == 1)
    }

    @Test
    fun whitespaceOK() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "        addi ia 3"
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ia"]?.contents == 3)
    }

    // TODO: Make better tests, these are trash
    @Test
    fun greaterThanJump() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "addi ic 1\naddi ic 2\naddi ic 3\njpgt ia ib 1"
        computer.ptr = 3
        computer.intRegisters["ia"]?.contents = 3
        computer.intRegisters["ib"]?.contents = 1
        computer.step()
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ic"]?.contents == 2)
    }

    @Test
    fun lessThanJump() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "addi ic 1\naddi ic 2\naddi ic 3\naddi ic 4\njplt ia ib 2"
        computer.ptr = 4
        computer.intRegisters["ia"]?.contents = 2
        computer.intRegisters["ib"]?.contents = 4
        computer.step()
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ic"]?.contents == 3)
    }

    @Test
    fun lessThanEqualJump() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "addi ic 1\n addi ic 2\naddi ic 3\njple ia ib 1"
        computer.ptr = 3
        computer.intRegisters["ia"]?.contents = 2
        computer.intRegisters["ib"]?.contents = 2
        computer.step()
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ic"]?.contents == 2)
    }

    @Test
    fun greaterThanEqualJump() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "addi ic 1\naddi ic 2\n addi ic 3\n jpge ia ib 1"
        computer.ptr = 3
        computer.intRegisters["ia"]?.contents = 3
        computer.intRegisters["ib"]?.contents = 2
        computer.step()
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ic"]?.contents == 2)
    }

    @Test
    fun equalToInt() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "addi ic 1\naddi ic 2\n addi ic 3\n jpeq ia ib 1"
        computer.ptr = 3
        computer.intRegisters["ia"]?.contents = 3
        computer.intRegisters["ib"]?.contents = 3
        computer.step()
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ic"]?.contents == 2)
    }

    @Test
    fun equalToDouble() {
        // Heh. This is a terrible thing as it is. Good news though, we accept anything within 0.0001...
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "addi ic 1\naddi ic 2\naddi ic 3\njpeq dx dy 1"
        computer.doubleRegisters["dx"]?.contents = Math.PI
        computer.doubleRegisters["dy"]?.contents = 3.1415926
        computer.ptr = 3
        computer.step()
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ic"]?.contents == 2)
    }

    @Test
    fun equalToString() {
        val computer = AsmComputer()
        computer.stringRegisters["cra"]?.contents = "addi ic 1\naddi ic 2\n addi ic 3\njpeq sx sy 1"
        computer.stringRegisters["sx"]?.contents = "Hello, world!"
        computer.stringRegisters["sy"]?.contents = "Hello, world!"
        computer.ptr = 3
        computer.step()
        computer.step()
        Assertions.assertEquals(true, computer.intRegisters["ic"]?.contents == 2)
    }
}
