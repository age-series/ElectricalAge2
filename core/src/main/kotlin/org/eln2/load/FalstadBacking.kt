package org.eln2.load

import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node
import org.eln2.sim.electrical.mna.NodeRef
import org.eln2.sim.electrical.mna.component.*
import org.eln2.space.Vec2i
import java.lang.Exception

class FalstadBacking {

    companion object {

        var id: Int = 0

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
            print(parseFalstad)
        }

        /**
         * parseFalstad
         *
         * @param str The input string using the Falstad language
         * @return circuit
         */
        fun parseFalstad(str: String): Circuit {

            val lines = str.split("\n")
            val componentDefs = mutableListOf<String>()

            val circuit = Circuit()
            val componentMap = mutableMapOf<Vec2i, MutableMap<Component, Int>>()
            val pinLookupTable = mutableMapOf<Vec2i, Int>()

            // put all w's first
            lines.filter {it.isNotEmpty()}.filter{it[0] == 'w'}.forEach { componentDefs.add(it) }
            lines.filter {it.isNotEmpty()}.filter{it[0] != 'w'}.forEach { componentDefs.add(it) }

            for (componentDef in componentDefs) {
                var componentDefLocal = componentDef
                if ("//" in componentDef) {
                    componentDefLocal = componentDefLocal.split("//")[0]
                }
                if ("%" in componentDef) {
                    componentDefLocal = componentDefLocal.split("%")[0]
                }

                val componentPropertyList = componentDefLocal.split(" ").map { it.trim() }

                when (componentPropertyList[0]) {
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
                        if (componentPropertyList.size >= 6) {
                            componentBuilder(componentPropertyList, circuit, componentMap, pinLookupTable)
                        }
                    }
                }
            }
            /*for (node in componentMap) {
                val components = node.value
                println(components)
                for (comp in components) {
                    for (comp2 in components) {
                        if (comp2 != comp) {
                            println(comp.value)
                            println(comp.value)
                            comp.key.connect(comp.value, comp2.key, comp2.value)
                        }
                    }
                }
            }*/
            for (component in componentMap.map{it.value}.map {it.keys}) {
                component.forEach{println(it)}
            }
            for (component in circuit.components) {
                println(component)
            }
            return circuit
        }

        fun getPinId(pin: Vec2i, lt: MutableMap<Vec2i, Int>): Int {
            if (pin !in lt) {
                lt[pin] = id
                id += 1
            }
            return lt[pin]?: throw Exception("What the heck! You ATE my ID")
        }

        fun componentBuilder(c: List<String>, circuit: Circuit, nodeMap: MutableMap<Vec2i,MutableMap<Component, Int>>, lt: MutableMap<Vec2i, Int>): Set<Pair<Vec2i,Vec2i>> {
            val type = c[0]
            val aPin = Vec2i(c[1].toInt(), c[2].toInt())
            val bPin = Vec2i(c[3].toInt(), c[4].toInt())
            if (nodeMap[aPin] == null) nodeMap[aPin] = mutableMapOf()
            val aId = getPinId(aPin, lt)
            val bId = getPinId(bPin, lt)
            if (nodeMap[bPin] == null) nodeMap[bPin] = mutableMapOf()
            val sameNodeList = mutableSetOf<Pair<Vec2i,Vec2i>>()
            when(type) {
                "g" -> {
                    // ground (one pin)
                    if (nodeMap[aPin] == null) {
                        nodeMap[aPin]!!
                    }
                }
                "r" -> {
                    // resistor
                    val resistor = Resistor()
                    resistor.r = c[6].toDouble()
                    circuit.add(resistor)

                    nodeMap[aPin]!![resistor] = aId
                    resistor.connect(0, NodeRef(Node(circuit)))
                    nodeMap[bPin]!![resistor] = bId
                    resistor.connect(1, NodeRef(Node(circuit)))
                }
                "R" -> {
                    // voltage rail (one pin)
                    val voltageSource = VoltageSource()
                    voltageSource.u = c[8].toDouble()
                    circuit.add(voltageSource)

                    nodeMap[aPin]!![voltageSource] = aId
                }
                "s"-> {
                    // switch

                    // TODO: Change to ResistorSwitch
                    val switch = DynamicResistor()
                    switch.r = 1.0
                    circuit.add(switch)

                    nodeMap[aPin]!![switch] = aId
                    nodeMap[bPin]!![switch] = bId
                }
                "S" -> {
                    //switch (but wye)

                }
                "t" -> {
                    // transistor

                }
                "w" -> {
                    // wire

                    sameNodeList.add(Pair(aPin, bPin))
                }
                "c" -> {
                    // capacitor

                    val capacitor = Capacitor()
                    capacitor.c = c[6].toDouble()
                    circuit.add(capacitor)

                    nodeMap[aPin]!![capacitor] = aId
                    nodeMap[bPin]!![capacitor] = bId
                }
                "209" -> {
                    // polar capacitor?
                    // Perhaps this could be a electrolytic capacitor, in which case, program a VoltageWatchdog on a regular cap

                    val capacitor = Capacitor()
                    capacitor.c = c[6].toDouble()
                    circuit.add(capacitor)

                    nodeMap[aPin]!![capacitor] = aId
                    nodeMap[bPin]!![capacitor] = bId
                }
                "l" -> {
                    // inductor

                    val inductor = Inductor()
                    inductor.h = c[6].toDouble()
                    circuit.add(inductor)

                    nodeMap[aPin]!![inductor] = aId
                    nodeMap[bPin]!![inductor] = bId
                }
                "v" -> {
                    // voltage source (two pin)

                    val voltageSource = VoltageSource()
                    voltageSource.u = c[8].toDouble()
                    circuit.add(voltageSource)

                    nodeMap[aPin]!![voltageSource] = aId
                    nodeMap[bPin]!![voltageSource] = bId
                }
                "172" -> {
                    // voltage source (one pin, uses Adjustable slider on right

                    val voltageSource = VoltageSource()
                    voltageSource.u = c[8].toDouble()
                    circuit.add(voltageSource)

                    nodeMap[aPin]!![voltageSource] = aId
                }

                /*
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
                }*/
            }
            return sameNodeList
        }

        fun logicGatePins(aPin: Vec2i, bPin: Vec2i): List<Vec2i>? {
            // the ordering is top, bottom, and then bPin
            val pinList = mutableListOf<Vec2i>()

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

            val aX = aPin.x
            val aY = aPin.y
            val bX = aPin.x
            val bY = aPin.y

            if (aX == bX) {
                // the component is in the X plane which means the signals are offset in the Y plane.
                if (aY > bY) {
                    // facing down, first pin is larger than the second
                    pinList.add(Vec2i(aX + 16, aY))
                    pinList.add(Vec2i(aX - 16, aY))
                } else {
                    // facing up, first pin is smaller than the second
                    pinList.add(Vec2i(aX - 16, aY))
                    pinList.add(Vec2i(aX + 16, aY))
                }

            } else if (aY == bY) {
                if (aX > bX) {
                    // facing left, first pin is larger than the second
                    pinList.add(Vec2i(aX, aY + 16))
                    pinList.add(Vec2i(aX, aY - 16))
                } else {
                    // facing right, first pin is smaller than the second
                    pinList.add(Vec2i(aX, aY - 16))
                    pinList.add(Vec2i(aX, aY + 16))
                }
            } else {
                println("Error! The positioning of the logic gate makes it impossible to be connected.")
                return null
            }
            pinList.add(bPin)
            return pinList
        }
    }
}
