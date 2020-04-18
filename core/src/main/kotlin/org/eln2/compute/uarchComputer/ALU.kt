package org.eln2.compute.uarchComputer

@ExperimentalStdlibApi
@ExperimentalUnsignedTypes
class ALU(var inputA: UInt, var inputB: UInt) {
	enum class ALUOps { Zero, And, Or, Xor, Not, Nand, Nor, Xnor, Add, Adc, Sub, Sbb, Shl, Shr, One, MinusOne }
	var currState = ALUOps.Zero
	
	var zero: Boolean = false
	var carry: Boolean = false
	var parity: Boolean = false
	var interupt: Boolean = false
	var sign: Boolean = false

	fun flagsAsUInt() : UInt {
		var r = 0
		if (zero) { r = 2 * r + 1 } else { r *= 2 }
		if (carry) { r = 2 * r + 1 } else { r *= 2 }
		if (parity) { r = 2 * r + 1 } else { r *= 2 }
		if (interupt) { r = 2 * r + 1 } else { r *= 2 }
		r *= 2
		r *= 2
		r *= 2
		if (sign) { r = 2 * r + 1 } else { r *= 2 }
		return r.toUInt()
	}

	fun step() {
		when (currState) {
			ALUOps.Adc -> {
				inputA += inputB + if (carry) { 1u } else { 0u }
				carry = inputA < inputB
			}
			ALUOps.Add -> {
				inputA += inputB
				carry = inputA < inputB
			}
			ALUOps.And -> inputA = inputA and inputB
			ALUOps.Or -> inputA = inputA or inputB
			ALUOps.MinusOne -> inputA = 0xFFFFFFFFu
			ALUOps.Nand -> inputA = (inputA and inputB).inv()
			ALUOps.Nor -> inputA = (inputA or inputB).inv()
			ALUOps.Not -> inputA = inputA.inv()
			ALUOps.One -> inputA = 1u
			ALUOps.Sbb -> {
				inputA = (inputA - inputB + if (carry) { 0x100000000u } else { 0u }).toUInt()
				carry = carry.xor(inputA > inputB)
			}
			ALUOps.Shl -> {
				if(inputA >= 0x80000000u) {
					inputA = inputA * 2u + if (carry) { 1u } else { 0u }
					carry = true
				} else {
					inputA = inputA * 2u + if (carry) { 1u } else { 0u }
					carry = false
				}
			}
			ALUOps.Shr -> {
				if(inputA.and(1u) == 1u) {
					inputA = inputA / 2u + if (carry) { 0x80000000u } else { 0u }
					carry = true
				} else {
					inputA = inputA * 2u + if (carry) { 0x80000000u } else { 0u }
					carry = false
				}
			}
			ALUOps.Sub -> {
				inputA = inputA - inputB
				carry = inputA > inputB
			}
			ALUOps.Xor -> inputA = inputA xor inputB
			ALUOps.Zero -> inputA = 0u
		}

		zero = inputA == 0u
		sign = inputA >= 0x80000000u
		parity = inputA.countOneBits() == 1
	}
}
