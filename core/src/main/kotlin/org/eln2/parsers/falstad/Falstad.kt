package org.eln2.parsers.falstad

import org.eln2.debug.DEBUG
import org.eln2.debug.dprintln
import org.eln2.parsers.falstad.components.sources.*
import org.eln2.parsers.falstad.components.generic.*
import org.eln2.parsers.falstad.components.passive.*
import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.electrical.mna.component.Resistor
import org.eln2.sim.electrical.mna.component.VoltageSource
import org.eln2.space.Set
import org.eln2.space.Vec2i
import java.io.FileOutputStream

val SPACES = Regex(" +")

data class PinRef(val component: Component, val pinidx: Int) {
	val node get() = component.node(pinidx)
}
data class PinPos(val pos: Vec2i)

class FalstadLine(val params: Array<String>) {
	companion object {
		fun fromLine(s: String): FalstadLine? {
			val trimmed = s.trim()
			return if(trimmed.isEmpty())
				null
			else
				FalstadLine(trimmed.split(SPACES).map { it.trim() }.toTypedArray())
		}
		fun intoLines(src: String) = src.lines().mapNotNull { fromLine(it) }
	}

	operator fun get(i: Int) = params[i]
	
	fun getInt(i: Int) = get(i).toInt()
	fun getDouble(i: Int) = get(i).toDouble()
}

data class CCData(val falstad: Falstad, val line: FalstadLine) {
	val circuit get() = falstad.circuit

	// Sets the conceptual number of nodes of the component--not the actual number of positions in the line!
	var pins: Int = 2

	val pinPositions get() = (0 until 2).map { PinPos(Vec2i(line.getInt(1 + 2 * it), line.getInt(2 + 2 * it))) }

	// Only safe to use these if pins >= 2
	val pos: PinPos get() = pinPositions[0]
	val neg: PinPos get() = pinPositions[1]
	
	val flags: Int get() = line.getInt(5)
	val type: String get() = line[0]

	val data get() = line.params.drop(6)
}

data class PosSet(val pos: PinPos): Set()

/**
 * Component Constructor
 *
 * Interface that allows Falstad objects to be loaded from strings
 */
interface IComponentConstructor {
	companion object {
		// TODO: Custom Falstad components are a thing, so perhaps we should make this mutable?
		//     Possibly, things could then register themselves into this? May make other things more complex
		val TYPE_CONSTRUCTORS: Map<String, IComponentConstructor> = mapOf(
			"$" to InterpretGlobals(),
			"O" to OutputProbe(),
			"w" to WireConstructor(),
			"g" to GroundConstructor(),
			"r" to ResistorConstructor(),
			"l" to InductorConstructor(),
			"c" to CapacitorConstructor(),
			"v" to VoltageSourceConstructor(),
			"172" to VoltageRailConstructor(),
			"i" to CurrentSourceConstructor()
		)
		
		fun getForLine(fl: FalstadLine) = TYPE_CONSTRUCTORS[fl[0]] ?: error("unrecognized type: ${fl[0]}")
	}
	fun construct(ccd: CCData)
}

abstract class PoleConstructor: IComponentConstructor {
	abstract fun component(ccd: CCData): Component
	abstract fun configure(ccd: CCData, cmp: Component)

	override fun construct(ccd: CCData) {
		val c = component(ccd)
		ccd.circuit.add(c)
		configure(ccd, c)
		ccd.pinPositions.withIndex().forEach {
			val pp = (ccd.falstad.getPin(it.value).representative as PosSet)
			ccd.falstad.addPinRef(pp, PinRef(c, it.index))
		}
	}
}

class Falstad(val source: String) {
	val roots: MutableMap<PinPos, PosSet> = mutableMapOf()
	fun getPin(p: PinPos) = roots.getOrPut(p, { PosSet(p) })

	val refs: MutableMap<PosSet, MutableSet<PinRef>> = mutableMapOf()
	fun getPinRefs(p: PosSet) = refs.getOrPut(p, { mutableSetOf() })
	fun addPinRef(p: PosSet, pr: PinRef) = getPinRefs(p).add(pr)
	
	val grounds: MutableSet<PosSet> = mutableSetOf()
	fun addGround(p: PosSet) = grounds.add(p)
	
	val outputs: MutableSet<PinRef> = mutableSetOf()
	fun addOutput(pr: PinRef) = outputs.add(pr)
	val outputNodes get() = outputs.map { it.node }

	var nominalTimestep = 0.05
	// Set to false directly by various constructors that introduce explicit grounds
	var floating = true

	val circuit = Circuit()

	init {
		FalstadLine.intoLines(source).forEach {
			IComponentConstructor.getForLine(it)
				.construct(CCData(this, it))
		}

		if(DEBUG) {
			val repmap: MutableMap<PosSet, MutableSet<PosSet>> = mutableMapOf()
			roots.values.forEach {
				dprintln("F.<init>: r $it => ${it.representative}")
				repmap.getOrPut(it.representative as PosSet, { mutableSetOf() }).add(it)
			}

			repmap.entries.forEach {
				dprintln("F.<init>: R ${it.key}:")
				it.value.forEach {
					it2 ->
					dprintln(" - $it2")
				}
			}
		}

		val mergedRefs: MutableMap<PosSet, MutableSet<PinRef>> = mutableMapOf()
		roots.values.forEach {
			val set = refs[it]
			if(set != null) {
				mergedRefs.getOrPut(it.representative as PosSet, { mutableSetOf() }).addAll(set)
			}
		}

		if(DEBUG) mergedRefs.entries.forEach {
			dprintln("F.<init>: mR ${it.key} => ${it.value}")
		}

		mergedRefs.values.forEach {
			val ordered = it.toList()  // Just need some ordering, any will do
			(0 until ordered.size - 1).forEach {
					it2 ->
					ordered[it2].component.connect(
					ordered[it2].pinidx,
					ordered[it2+1].component,
					ordered[it2+1].pinidx
				)
			}
		}

		grounds.forEach {
			val set = refs[it.representative]
			if(set != null && set.size > 0) {
				// Only need to connect 1; the disjoint PosSets are merged right now
				val pr = set.iterator().next()
				pr.component.connect(pr.pinidx, circuit.ground)
			}
		}

		if(floating) {
			// This isn't expected to be the hotpath, so spend some extra time
			// looking for a valid VoltageSource to fix an arbitrary ground
			dprintln("F.<init>: floating!")
			var found = false
			for(comp in circuit.components) {
				if(comp is VoltageSource) {
					dprintln("F.<init>: floating: ground $comp pin 1 (node ${comp.node(1)}")
					comp.connect(1, circuit.ground)
					found = true
					break
				}
			}
			if(!found) println("WARN: F.<init>: floating circuit and no VSource; the matrix is likely underconstrained.")
		}
	}

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

			val f = Falstad(falstad)
			println("$f:\n${f.source}\n${f.roots}\n${f.refs}\n${f.grounds}\n\n${f.circuit}")

			f.circuit.step(f.nominalTimestep)

			f.circuit.components.forEach {
				println("$it:")
				when(it) {
					is Resistor -> println("r.i = ${it.current}")
				}
			}

			println("nodes:")
			f.circuit.nodes.forEach {
				println(" - ${it.get() ?: "[removed]"}: ${it.get()?.potential ?: 0.0}V")
			}

			println("outputs:")
			f.outputNodes.withIndex().forEach {
				println("${it.index}: ${it.value.potential}V @${it.value.index}")
			}

			FileOutputStream("falstad.dot").bufferedWriter().also {
				it.write(f.circuit.toDot())
			}.close()
		}
	}
}
