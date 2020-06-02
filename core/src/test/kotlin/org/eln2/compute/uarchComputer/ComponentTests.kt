package org.eln2.compute.uarchComputer

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ComponentTests {
    @ExperimentalUnsignedTypes
    @ExperimentalStdlibApi
    @Test
    fun testALU() {
        val alu = ALU()
        alu.inputB = 0x42u
        val aluOpsToTest = arrayOf(
            ALU.ALUOps.Add, ALU.ALUOps.Adc, ALU.ALUOps.And, ALU.ALUOps.MinusOne, ALU.ALUOps.Nand, ALU.ALUOps.Nor, ALU.ALUOps.Not,
            ALU.ALUOps.One, ALU.ALUOps.Or, ALU.ALUOps.Shl, ALU.ALUOps.Shr, ALU.ALUOps.Sub, ALU.ALUOps.Sbb, ALU.ALUOps.Xnor,
            ALU.ALUOps.Xor, ALU.ALUOps.Zero
        )
        val inputAArray = arrayOf(
            0xFFFFFFFFu, 0u, 0x41u, 0u, 0x41u, 0x41u, 0xFFFF0000u,
            0u, 0x41u, 0x41u, 0x41u, 0x41u, 0x41u, 0x41u,
            0x41u, 0xC0FFEEu
        )
        val expected = arrayOf(
            0x41u, 0x43u, 0x40u, 0xFFFFFFFFu, 0xFFFFFFBFu, 0xFFFFFFBCu, 0x0000FFFFu,
            1u, 0x43u, 0x82u, 0x20u, 0xFFFFFFFFu, 0xFFFFFFFFu, 0xFFFFFFFCu,
            0x03u, 0u
        )
        for (test in aluOpsToTest.zip(inputAArray.zip(expected))) {
            alu.currOP = test.first
            alu.inputA = test.second.first
            alu.step()
            Assertions.assertEquals(alu.inputA, test.second.second)
        }
    }

    @ExperimentalUnsignedTypes
    @ExperimentalStdlibApi
    @Test
    fun testFPU() {
        val fpu = FPU()
        fpu.inputA = 0.2
        fpu.inputBH = fpu.inputAH
        fpu.inputBL = fpu.inputAL
        Assertions.assertEquals(fpu.inputA, fpu.inputB)

        fpu.inputA = 23.45
        fpu.currOp = FPU.FPUOps.Int
        fpu.step()
        fpu.currOp = FPU.FPUOps.ToDouble
        fpu.step()
        Assertions.assertEquals(fpu.inputA, 23.0)

        val FPUOpsToTest = arrayOf(
            FPU.FPUOps.Add, FPU.FPUOps.Cos, FPU.FPUOps.Div, FPU.FPUOps.Exp, FPU.FPUOps.Inv, FPU.FPUOps.Ln,
            FPU.FPUOps.MinusOne, FPU.FPUOps.Mult, FPU.FPUOps.Neg, FPU.FPUOps.One, FPU.FPUOps.Sqre, FPU.FPUOps.Sqrt,
            FPU.FPUOps.Sub, FPU.FPUOps.Zero
        )
        val inputAArray = arrayOf(0.0, 0.0, 2.0, 0.0, 2.0, 1.0, 0.0, 1.0, 1.0, 0.0, 2.0, 4.0, 1.0, 1.0)
        val expected = arrayOf(0.2, 1.0, 10.0, 1.0, 0.5, 0.0, -1.0, 0.2, -1.0, 1.0, 4.0, 2.0, 0.8, 0.0)

        for (test in FPUOpsToTest.zip(inputAArray.zip(expected))) {
            fpu.currOp = test.first
            fpu.inputA = test.second.first
            fpu.step()
            Assertions.assertEquals(fpu.inputA, test.second.second)
        }
    }
}
