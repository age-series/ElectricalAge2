package org.eln2.apps.compute

import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.CState
import org.eln2.compute.asmComputer.StringRegister
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.*

fun main() {
	val computer = AsmComputer()
	computer.stringRegisters["so0"] = StringRegister(32)
	computer.stringRegisters["si0"] = StringRegister(32)

	val frame = JFrame("Computer Simulator")
	frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
	frame.setSize(600, 400)

	val mb = JMenuBar()

	val centerPanel = JPanel()

	val serialOut = JTextArea(32,16)
	val cra = JTextArea(32,16)
	val crb = JTextArea(32,16)
	centerPanel.add(serialOut)
	centerPanel.add(cra)
	centerPanel.add(crb)

	val bottomPanel = JPanel()

	val label = JLabel("ttyS0")
	val serialIn = JTextField(32)
	val step = JButton("Step")
	val run = JButton("Run")
	bottomPanel.add(label)
	bottomPanel.add(serialIn)
	bottomPanel.add(step)
	bottomPanel.add(run)

	frame.contentPane.add(BorderLayout.NORTH, mb)
	frame.contentPane.add(BorderLayout.CENTER, centerPanel)
	frame.contentPane.add(BorderLayout.SOUTH, bottomPanel)

	cra.addKeyListener(CraListener(cra, computer))
	crb.addKeyListener(CrbListener(crb, computer))
	serialIn.addKeyListener(SerialInputListenr(serialIn, computer))
	step.addActionListener(StepComputer(computer, cra, crb, serialOut))
	run.addActionListener(RunComputer(computer, cra, crb, serialOut))

	frame.isVisible = true
}

class CraListener(val cra: JTextArea, val asmComputer: AsmComputer): KeyListener {
	override fun keyTyped(p0: KeyEvent?) {
		update()
	}

	override fun keyPressed(p0: KeyEvent?) {
		update()
	}

	override fun keyReleased(p0: KeyEvent?) {
		update()
	}

	private fun update() {
		asmComputer.stringRegisters["cra"]?.contents = cra.text
		if (asmComputer.codeRegister == "cra") {
			asmComputer.ptr = 0
		}
	}
}

class CrbListener(val crb: JTextArea, val asmComputer: AsmComputer): KeyListener {
	override fun keyTyped(p0: KeyEvent?) {
		update()
	}

	override fun keyPressed(p0: KeyEvent?) {
		update()
	}

	override fun keyReleased(p0: KeyEvent?) {
		update()
	}

	private fun update() {
		asmComputer.stringRegisters["crb"]?.contents = crb.text
		if (asmComputer.codeRegister == "crb") {
			asmComputer.ptr = 0
		}
	}
}

class SerialInputListenr(val input: JTextField, val asmComputer: AsmComputer): KeyListener {
	override fun keyTyped(p0: KeyEvent?) {
		update()
	}

	override fun keyPressed(p0: KeyEvent?) {
		update()
	}

	override fun keyReleased(p0: KeyEvent?) {
		update()
	}

	private fun update() {
		if ("\n" in input.text) {
			asmComputer.stringRegisters["si0"]?.contents = input.text
			input.text = ""
		}
	}
}

class StepComputer(val computer: AsmComputer, val cra: JTextArea, val crb: JTextArea, val serialOutput: JTextArea): ActionListener {
	override fun actionPerformed(p0: ActionEvent?) {
        stepComputer(computer, cra, crb, serialOutput)
	}
}

class RunComputer(val computer: AsmComputer, val cra: JTextArea, val crb: JTextArea, val serialOutput: JTextArea): ActionListener {
	override fun actionPerformed(p0: ActionEvent?) {
		val t = Thread() {
			while (computer.currState != CState.Errored) {
                stepComputer(computer, cra, crb, serialOutput)
			}
		}
		t.start()
	}
}

fun stepComputer(computer: AsmComputer, cra: JTextArea, crb: JTextArea, serialOutput: JTextArea) {
	val actionList = computer.stringRegisters[computer.codeRegister]?.contents!!.split("\n")
	var action = ""
	if (computer.ptr < actionList.size && computer.ptr >= 0) {
		action = actionList[computer.ptr]
	} else if (computer.ptr == actionList.size) {
		// computer.step() will move the pointer back to 0 and run that if the pointer is at the end of the list.
		action = actionList[0]
	} else {
		println("Not sure what's up but ptr: ${computer.ptr}")
	}
	computer.step()
	cra.text = computer.stringRegisters["cra"]?.contents
	crb.text = computer.stringRegisters["crb"]?.contents
	val output = computer.stringRegisters["so0"]?.contents?: ""
	if (output.isNotEmpty()) {
		serialOutput.text += output + "\n"
		computer.stringRegisters["so0"]?.contents = ""
	}
	println("ran asm `$action`, current state: ${computer.currState}, reasoning: ${computer.currStateReasoning}")
	println("Int Registers: ${computer.intRegisters}")
	println("Double Registers: ${computer.doubleRegisters}")
	println("String Registers: ${computer.stringRegisters}")
}
