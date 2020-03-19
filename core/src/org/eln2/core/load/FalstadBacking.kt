package org.eln2.core.load

import org.eln2.core.sim.electrical.mna.Node
import org.eln2.core.sim.electrical.mna.component.*

class FalstadBacking {
    /*
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val falstad = """${'$'} 1 0.000005 10.20027730826997 63 10 62
v 112 368 112 48 0 0 40 10 0 0 0.5
w 112 48 240 48 0
r 240 48 240 208 0 10000
r 240 208 240 368 0 10000
w 112 368 240 368 0
O 240 208 304 208 1
w 240 48 432 48 0
w 240 368 432 368 0
r 432 48 432 128 0 10000
r 432 128 432 208 0 10000
r 432 208 432 288 0 10000
r 432 288 432 368 0 10000
O 432 128 496 128 1
O 432 208 496 208 1
O 432 288 496 288 1
"""
            val falstad2 = """${'$'} 1 0.000005 10.20027730826997 50 5 50
r 256 176 256 304 0 100
172 304 176 304 128 0 7 5 5 0 0 0.5 Voltage
g 256 336 256 352 0
w 256 304 256 336 1
r 352 176 352 304 0 1000
w 352 304 352 336 1
g 352 336 352 352 0
w 304 176 352 176 0
w 256 176 304 176 0
"""

            val parseFalstad = parseFalstad(falstad2)
            parseFalstad.first.forEach { println(it.detail()) }
            parseFalstad.second.forEach { println(it.detail()) }
        }

        /**
         * parseFalstad
         *
         * @param str The input string using the Falstad language
         * @return components and nodes
         */
        fun parseFalstad(str: String): Pair<List<Component>,List<Node>> {

            val cmds = str.split("\n")
            val components = mutableListOf<Component>()
            val nodemap = mutableMapOf<String, Node>()

            val commands = mutableListOf<String>()

            // put all w's first
            cmds.filter {it.isNotEmpty()}.filter{it[0] == 'w'}.forEach { commands.add(it) }
            cmds.filter {it.isNotEmpty()}.filter{it[0] != 'w'}.forEach { commands.add(it) }

            for (c in commands) {
                var cl = c
                if ("//" in c) {
                    cl = cl.split("//")[0]
                }
                if ("%" in c) {
                    cl = cl.split("%")[0]
                }

                var cs = cl.split(" ")
                cs = cs.map { it.trim() }

                when (cs[0]) {
                    // ==== NON COMPONENTS
                    "$" -> {
                        // === Initializer Statement ===
                        // flags = cs[1]
                        // timeStep = cs[2].toInt()
                        // rest of the arguments are unimportant (probably) for our use case
                    }
                    "o" -> {
                        // === Scope Statement ===
                    }
                    "38" -> {
                        // === Adjustable Statement ===
                    }
                    "h" -> {
                        // === Hint Statement ===
                    }
                    else -> {
                        if (cs.size >= 6) {
                            val comp = componentBuilder(cs, nodemap)
                            if (comp != null) components.add(comp)
                        }
                    }
                }
            }

            return Pair(components, nodemap.map { it.value }.distinct())
        }

        fun componentBuilder(c: List<String>, nodemap: MutableMap<String, Node>): Component? {
            val type = c[0]
            val aPin = Pair(c[1].toInt(), c[2].toInt())
            val aPinStr = "${aPin.first}.${aPin.second}"
            val bPin = Pair(c[3].toInt(), c[4].toInt())
            val bPinStr = "${bPin.first}.${bPin.second}"
            when(type) {
                "g" -> {
                    // ground (one pin)
                    if (nodemap[aPinStr] == null) {
                        nodemap[aPinStr] = Node(aPin)
                    }

                    val int = VoltageSource()
                    int.u = 0.0
                    int.nodes.add(nodemap[aPinStr])
                    int.nodes.add(null)

                    return int
                }
                "r" -> {
                    // resistor
                    if (nodemap[aPinStr] == null) {
                        nodemap[aPinStr] = Node(aPin)
                    }
                    if (nodemap[bPinStr] == null) {
                        nodemap[bPinStr] = Node(bPin)
                    }

                    val int = Resistor()
                    int.r = c[6].toDouble()
                    int.nodes.add(nodemap[aPinStr])
                    int.nodes.add(nodemap[bPinStr])

                    return int
                }
                "R" -> {
                    // voltage rail (one pin)
                    if (nodemap[aPinStr] == null) {
                        nodemap[aPinStr] = Node(aPin)
                    }

                    val int = VoltageSource()
                    int.u = c[8].toDouble()
                    int.nodes.add(nodemap[aPinStr])
                    int.nodes.add(null)

                    return int
                }
                "s"-> {
                    // switch
                    if (nodemap[aPinStr] == null) {
                        nodemap[aPinStr] = Node(aPin)
                    }
                    if (nodemap[bPinStr] == null) {
                        nodemap[bPinStr] = Node(bPin)
                    }
                    /*

                    TODO: Uncomment when ResistorSwitch becomes a thing.

                    val int = ResistorSwitch()
                    int.nodes.add(nodemap[aPin])
                    int.nodes.add(nodemap[bPin])

                    return int
                    */
                }
                "S" -> {
                    //switch (but wye)

                }
                "t" -> {
                    // transistor

                }
                "w" -> {
                    // wire
                    // NOTE: This is special because we're connecting the nodes together instead of making a 0 ohm resistor
                    // Optionally, one could instead make this a "cable" with ~0.1 ohms
                    val sharedNode: Node
                    if ((nodemap[aPinStr] == null) and (nodemap[bPinStr] == null)) {
                        sharedNode = Node("node${aPin.first}.${aPin.second}.${bPin.first}.${bPin.second}")
                        nodemap[aPinStr] = sharedNode
                        nodemap[bPinStr] = sharedNode
                    } else if ((nodemap[aPinStr] != null) and (nodemap[bPinStr] == null)) {
                        sharedNode = nodemap[aPinStr]!!
                        nodemap[bPinStr] = sharedNode
                    } else if ((nodemap[aPinStr] == null) and (nodemap[bPinStr] != null)) {
                        sharedNode = nodemap[bPinStr]!!
                        nodemap[aPinStr] = sharedNode
                    } else {
                        // TODO: Ohhh shite. Time to iterate over components.
                        println("Uh oh! Looks like we have placed all components before wires! Falling back to 0.01 ohm resistor as wire..")
                        val int = Resistor()
                        int.r = 0.01
                        int.nodes.add(nodemap[aPinStr])
                        int.nodes.add(nodemap[bPinStr])
                    }

                    return null
                }
                "c" -> {
                    // capacitor
                    if (nodemap[aPinStr] == null) {
                        nodemap[aPinStr] = Node(aPin)
                    }
                    if (nodemap[bPinStr] == null) {
                        nodemap[bPinStr] = Node(bPin)
                    }

                    val int = Capacitor()
                    int.c = c[6].toDouble()
                    int.nodes.add(nodemap[aPinStr])
                    int.nodes.add(nodemap[bPinStr])
                }
                "209" -> {
                    // polar capacitor?
                    // Perhaps this could be a electrolytic capacitor, in which case, program a VoltageWatchdog on a regular cap
                }
                "l" -> {
                    // inductor
                    if (nodemap[aPinStr] == null) {
                        nodemap[aPinStr] = Node(aPin)
                    }
                    if (nodemap[bPinStr] == null) {
                        nodemap[bPinStr] = Node(bPin)
                    }

                    val int = Inductor()
                    int.h = c[6].toDouble()
                    int.nodes.add(nodemap[aPinStr])
                    int.nodes.add(nodemap[bPinStr])

                    return int
                }
                "v" -> {
                    // voltage source (two pin)
                    if (nodemap[aPinStr] == null) {
                        nodemap[aPinStr] = Node(aPin)
                    }
                    if (nodemap[bPinStr] == null) {
                        nodemap[bPinStr] = Node(bPin)
                    }

                    val int = VoltageSource()
                    int.u = c[8].toDouble()
                    int.nodes.add(nodemap[aPinStr])
                    int.nodes.add(nodemap[bPinStr])

                    return int
                }
                "172" -> {
                    // voltage source (one pin, uses Adjustable slider on right
                    if (nodemap[aPinStr] == null) {
                        nodemap[aPinStr] = Node(aPin)
                    }

                    val int = VoltageSource()
                    int.u = c[8].toDouble()
                    int.nodes.add(nodemap[aPinStr])
                    int.nodes.add(null)

                    return int
                }
                "174" -> {
                    // "pot"
                }
                "O" -> {
                    // "output"
                }
                "i" -> {
                    // current source?
                }
                "p" -> {
                    // probe?
                }
                "d" -> {
                    // diode
                }
                "z" -> {
                    // zenner diode
                }
                "170" -> {
                    // sweep
                }
                "162" -> {
                    // LED
                }
                "A" -> {
                    // "antenna"
                }
                "L" -> {
                    // Logic input
                }
                "M" -> {
                    // logic output
                }
                "T" -> {
                    // transformer
                }
                "169" -> {
                    // tapped transformer
                }
                "171" -> {
                    // "trams line
                }
                "178" -> {
                    // relay
                }
                "m" -> {
                    // memristor
                }
                "187" -> {
                    // spark gap
                }
                "200" -> {
                    // AM source?
                }
                "201" -> {
                    // FM source?
                }
                "n" -> {
                    // noise source?
                }
                "181" -> {
                    // Lamp
                }
                "a" -> {
                    // Op Amp
                }
                "f" -> {
                    // Mosfet
                }
                "j" -> {
                    // Jfet
                }
                "159" -> {
                    // "Analog Switch?"
                }
                "160" -> {
                    // "analog switch"
                }
                "180" -> {
                    // " Tri state"
                }
                "182" -> {
                    // Schmitt
                }
                "183" -> {
                    // Inverting Schmitt
                }
                "177" -> {
                    // SCR
                }
                "203" -> {
                    // "Diac"
                }
                "206" -> {
                    // Triac
                }
                "173" -> {
                    // Triode
                }
                "175" -> {
                    // "Tunnel Diode"
                }
                "176" -> {
                    // Varactor
                }
                "179" -> {
                    // ???? CC2
                }
                "I" -> {
                    // Inverter
                    if (nodemap[aPinStr] == null) {
                        nodemap[aPinStr] = Node()
                    }
                    if (nodemap[bPinStr] == null) {
                        nodemap[bPinStr] = Node()
                    }

                    /*

                    TODO: Uncomment when NOT gates exist.
                    val int = NotGate()
                    int.nodes.add(nodemap[aPin])
                    int.nodes.add(nodemap[bPin])

                    return int
                     */

                }
                "151" -> {
                    // NAND
                    val pinList = logicGatePins(aPin, bPin)
                    if (pinList == null) {
                        return null
                    }
                    val pinListStr = pinList.map {"${it.first}.${it.second}"}

                    if (nodemap[pinListStr[0]] == null) {
                        nodemap[pinListStr[0]] = Node()
                    }
                    if (nodemap[pinListStr[1]] == null) {
                        nodemap[pinListStr[1]] = Node()
                    }
                    if (nodemap[pinListStr[2]] == null) {
                        nodemap[pinListStr[2]] = Node()
                    }

                    /*

                    TODO: Uncomment when NAND gates exist.
                    val int = NandGate()
                    int.nodes.add(nodemap[pinList[0])
                    int.nodes.add(nodemap[pinList[1])
                    int.nodes.add(nodemap[pinList[2])

                    return int

                     */
                }
                "153" -> {
                    // NOR
                    val pinList = logicGatePins(aPin, bPin)
                    if (pinList == null) {
                        return null
                    }
                    val pinListStr = pinList.map {"${it.first}.${it.second}"}

                    if (nodemap[pinListStr[0]] == null) {
                        nodemap[pinListStr[0]] = Node()
                    }
                    if (nodemap[pinListStr[1]] == null) {
                        nodemap[pinListStr[1]] = Node()
                    }
                    if (nodemap[pinListStr[2]] == null) {
                        nodemap[pinListStr[2]] = Node()
                    }

                    /*

                    TODO: Uncomment when NOR gates exist.
                    val int = NorGate()
                    int.nodes.add(nodemap[pinList[0])
                    int.nodes.add(nodemap[pinList[1])
                    int.nodes.add(nodemap[pinList[2])

                    return int

                     */
                }
                "150" -> {
                    // AND
                    val pinList = logicGatePins(aPin, bPin)
                    if (pinList == null) {
                        return null
                    }
                    val pinListStr = pinList.map {"${it.first}.${it.second}"}

                    if (nodemap[pinListStr[0]] == null) {
                        nodemap[pinListStr[0]] = Node()
                    }
                    if (nodemap[pinListStr[1]] == null) {
                        nodemap[pinListStr[1]] = Node()
                    }
                    if (nodemap[pinListStr[2]] == null) {
                        nodemap[pinListStr[2]] = Node()
                    }

                    /*

                    TODO: Uncomment when AND gates exist.
                    val int = AndGate()
                    int.nodes.add(nodemap[pinList[0])
                    int.nodes.add(nodemap[pinList[1])
                    int.nodes.add(nodemap[pinList[2])

                    return int

                     */
                }
                "152" -> {
                    // OR
                    val pinList = logicGatePins(aPin, bPin)
                    if (pinList == null) {
                        return null
                    }
                    val pinListStr = pinList.map {"${it.first}.${it.second}"}

                    if (nodemap[pinListStr[0]] == null) {
                        nodemap[pinListStr[0]] = Node()
                    }
                    if (nodemap[pinListStr[1]] == null) {
                        nodemap[pinListStr[1]] = Node()
                    }
                    if (nodemap[pinListStr[2]] == null) {
                        nodemap[pinListStr[2]] = Node()
                    }

                    /*

                    TODO: Uncomment when OR gates exist.
                    val int = OrGate()
                    int.nodes.add(nodemap[pinList[0])
                    int.nodes.add(nodemap[pinList[1])
                    int.nodes.add(nodemap[pinList[2])

                    return int

                     */
                }
                "154" -> {
                    // XOR
                    val pinList = logicGatePins(aPin, bPin)
                    if (pinList == null) {
                        return null
                    }
                    val pinListStr = pinList.map {"${it.first}.${it.second}"}

                    if (nodemap[pinListStr[0]] == null) {
                        nodemap[pinListStr[0]] = Node()
                    }
                    if (nodemap[pinListStr[1]] == null) {
                        nodemap[pinListStr[1]] = Node()
                    }
                    if (nodemap[pinListStr[2]] == null) {
                        nodemap[pinListStr[2]] = Node()
                    }

                    /*

                    TODO: Uncomment when XOR gates exist.
                    val int = XorGate()
                    int.nodes.add(nodemap[pinList[0])
                    int.nodes.add(nodemap[pinList[1])
                    int.nodes.add(nodemap[pinList[2])

                    return int

                     */
                }
                "155" -> {
                    // D-Flip Flop
                }
                "156" -> {
                    // JK-Flip Flop
                }
                "157" -> {
                    // 7 seg
                }
                "184" -> {
                    // Multiplexor
                }
                "185" -> {
                    // demultiplexor
                }
                "189" -> {
                    // "SipoShift
                }
                "186" -> {
                    // "PisoShift
                }
                "161" -> {
                    // phase comp
                }
                "164" -> {
                    // counter
                }
                "163" -> {
                    // RingCounter
                }
                "165" -> {
                    // Timer
                }
                "166" -> {
                    // DAC
                }
                "167" -> {
                    // ADC
                }
                "168" -> {
                    // Latch
                }
                "188" -> {
                    // Sequence Generator?
                }
                "158" -> {
                    // VCO
                }
                "b" -> {
                    // Box?
                }
                "x" -> {
                    // Text?
                }
                "193" -> {
                    // T Flip Flop
                }
                "197" -> {
                    // Seven Segment Decoder
                }
                "196" -> {
                    // Full Adder
                }
                "195" -> {
                    // Half Adder
                }
                "194" -> {
                    // Monostable
                }
                "207" -> {
                    // "labeled node"?
                }
                "208" -> {
                    // Custom Logic Element
                }
                "210" -> {
                    // data recorder?
                }
                "211" -> {
                    // audio output
                }
                "212" -> {
                    // VCVS?
                }
                "213" -> {
                    // VCCS?
                }
                "214" -> {
                    // CCVS?
                }
                "215" -> {
                    // CCCS?
                }
                "216" -> {
                    // Ohm Meter
                }
                "368" -> {
                    // Test Point
                }
                "370" -> {
                    // Ammeter
                }
                "400" -> {
                    // Darlington?
                }
                "401" -> {
                    // comparator?
                }
                "402" -> {
                    // OTAE?
                }
                "403" -> {
                    // scope?
                }
                "404" -> {
                    // fuse
                }
                "405" -> {
                    // LED Array
                }
                "406" -> {
                    // custom transformer
                }
                "407" -> {
                    // optocoupler
                }
                "408" -> {
                    // Stop Trigger?
                }
                "409" -> {
                    // op amp (real?)
                }
                "410" -> {
                    // Custom Composite?
                }
                "411" -> {
                    // Audio Input
                }
            }
            return null
        }

        fun logicGatePins(aPin: Pair<Int, Int>, bPin: Pair<Int, Int>): List<Pair<Int, Int>>? {
            // the ordering is top, bottom, and then bPin
            val pinList = mutableListOf<Pair<Int, Int>>()

            /*

            NOTE: Falstad appears to draw in Q4, meaning that -> ^ are positive x and y

            Might be useful for finding the pins on the logic gates.

            $ 1 0.000005 10.20027730826997 50 5 43
            152 192 192 320 192 0 2 0 5
            r 192 176 112 176 0 1000
            r 192 208 112 208 0 1000
            r 320 192 416 192 0 1000

            The logic gates connection pins are always +16 and -16 perpendicular from the bPin at the location of the aPin.

            The bPin is the output.

             */

            val aX = aPin.first
            val aY = aPin.second
            val bX = aPin.first
            val bY = aPin.second

            if (aX == bX) {
                // the component is in the X plane which means the signals are offset in the Y plane.
                if (aY > bY) {
                    // facing down, first pin is larger than the second
                    pinList.add(Pair(aX + 16, aY))
                    pinList.add(Pair(aX - 16, aY))
                } else {
                    // facing up, first pin is smaller than the second
                    pinList.add(Pair(aX - 16, aY))
                    pinList.add(Pair(aX + 16, aY))
                }

            } else if (aY == bY) {
                if (aX > bX) {
                    // facing left, first pin is larger than the second
                    pinList.add(Pair(aX, aY + 16))
                    pinList.add(Pair(aX, aY - 16))
                } else {
                    // facing right, first pin is smaller than the second
                    pinList.add(Pair(aX, aY - 16))
                    pinList.add(Pair(aX, aY + 16))
                }
            } else {
                println("Error! The positioning of the logic gate makes it impossible to be connected.")
                return null
            }
            pinList.add(bPin)
            return pinList
        }
    }
     */
}
