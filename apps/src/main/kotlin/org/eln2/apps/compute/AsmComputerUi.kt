package org.eln2.apps.compute

import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import org.eln2.compute.asmComputer.AsmComputer
import org.eln2.compute.asmComputer.CState
import org.eln2.compute.asmComputer.StringRegister

/**
 * Asm Computer UI. Provides a user interface for the ASM Computer. I know, it's a terrible UI.
 */
fun main() {
    val computer = AsmComputer()
    computer.stringRegisters["so0"] = StringRegister(32)
    computer.stringRegisters["si0"] = StringRegister(32)

    val frame = JFrame("Computer Simulator")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(600, 400)

    // Top menu bar
    val mb = JMenuBar()

    // Center Stuff
    val centerPanel = JPanel()

    val serialOut = JTextArea(32, 16)
    val cra = JTextArea(32, 16)
    val crb = JTextArea(32, 16)
    centerPanel.add(serialOut)
    centerPanel.add(cra)
    centerPanel.add(crb)

    // Bottom Stuff
    val bottomPanel = JPanel()

    val label = JLabel("ttyS0")
    val serialIn = JTextField(32)
    val step = JButton("Step")
    val run = JButton("Run")
    bottomPanel.add(label)
    bottomPanel.add(serialIn)
    bottomPanel.add(step)
    bottomPanel.add(run)

    // Bring it all together
    frame.contentPane.add(BorderLayout.NORTH, mb)
    frame.contentPane.add(BorderLayout.CENTER, centerPanel)
    frame.contentPane.add(BorderLayout.SOUTH, bottomPanel)

    // Listeners for all of the good stuff. Classes below.
    cra.addKeyListener(CraListener(cra, computer))
    crb.addKeyListener(CrbListener(crb, computer))
    serialIn.addKeyListener(SerialInputListenr(serialIn, computer))
    step.addActionListener(StepComputer(computer, cra, crb, serialOut))
    run.addActionListener(RunComputer(computer, cra, crb, serialOut))

    // Show to the user, run.
    frame.isVisible = true
}

/**
 * CraListener
 * Called when the CRA text area is touched
 * @param cra CRA text area
 * @param asmComputer The computer
 */
class CraListener(val cra: JTextArea, val asmComputer: AsmComputer) : KeyListener {
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

/**
 * CrbListener
 * Called when the CRB text area is touched
 * @param crb CRB text area
 * @param asmComputer The computer
 */
class CrbListener(val crb: JTextArea, val asmComputer: AsmComputer) : KeyListener {
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

/**
 * Serial Input Listener
 * Called when the serial input text area is touched
 * @param input serial text area
 * @param asmComputer The computer
 */

class SerialInputListenr(val input: JTextField, val asmComputer: AsmComputer) : KeyListener {
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

/**
 * StepComputer
 * Steps the computer once
 *
 * @param computer The computer
 * @param cra The CRA text area
 * @param crb The CRB text area
 * @param serialOutput The serial output panel
 */
class StepComputer(val computer: AsmComputer, val cra: JTextArea, val crb: JTextArea, val serialOutput: JTextArea) :
    ActionListener {
    override fun actionPerformed(p0: ActionEvent?) {
        stepComputer(computer, cra, crb, serialOutput)
    }
}

/**
 * RunComputer
 * Runs the computer until it errors. Not extremely useful.
 *
 * @param computer The computer
 * @param cra The CRA text area
 * @param crb The CRB text area
 * @param serialOutput The serial output panel
 */
class RunComputer(val computer: AsmComputer, val cra: JTextArea, val crb: JTextArea, val serialOutput: JTextArea) :
    ActionListener {
    override fun actionPerformed(p0: ActionEvent?) {
        val t = Thread() {
            while (computer.currState != CState.Errored) {
                stepComputer(computer, cra, crb, serialOutput)
            }
        }
        t.start()
    }
}

/**
 * stepComputer
 *
 * Function that steps the computer forwards one step.
 * @param computer The computer
 * @param cra text area
 * @param crb text area
 * @param serialOutput the output from the run if any
 */
fun stepComputer(computer: AsmComputer, cra: JTextArea, crb: JTextArea, serialOutput: JTextArea) {
    // This gets us the list of things that the computer may run this step
    val actionList = computer.stringRegisters[computer.codeRegister]?.contents!!.split("\n")
    // store the ASM we're about to run here
    var action = ""
    if (computer.ptr < actionList.size && computer.ptr >= 0) {
        // Cool, the pointer is valid, this is where we're at and set the action to this line for later.
        action = actionList[computer.ptr]
    } else if (computer.ptr == actionList.size) {
        // computer.step() will move the pointer back to 0 and run that if the pointer is at the end of the list.
        action = actionList[0]
    } else {
        println("Not sure what's up but ptr: ${computer.ptr}")
    }
    // Do the actual computer step
    computer.step()
    // The computer can modify these registers, so put the contents back to the text areas
    cra.text = computer.stringRegisters["cra"]?.contents
    crb.text = computer.stringRegisters["crb"]?.contents
    // This is the output from the serial string register
    val output = computer.stringRegisters["so0"]?.contents ?: ""
    // If it actually does something, clear it but put it in the serial out box followed by a newline
    if (output.isNotEmpty()) {
        serialOutput.text += output + "\n"
        computer.stringRegisters["so0"]?.contents = ""
    }
    // Some information for the user.
    println("ran asm `$action`, current state: ${computer.currState}, reasoning: ${computer.currStateReasoning}")
    println("Int Registers: ${computer.intRegisters}")
    println("Double Registers: ${computer.doubleRegisters}")
    println("String Registers: ${computer.stringRegisters}")
}
