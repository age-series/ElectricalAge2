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
import kotlin.math.min

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

	val pinPositions get() = (0 until min(pins, 2)).map { PinPos(Vec2i(line.getInt(1 + 2 * it), line.getInt(2 + 2 * it))) }

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
			"o" to Ignore(),
			"38" to Ignore(),
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
				dprintln("F.<init>: r ${it} => ${it.representative}")
				repmap.getOrPut(it.representative as PosSet, { mutableSetOf() }).add(it)
			}

			repmap.entries.forEach {
				dprintln("F.<init>: R ${it.key}:")
				it.value.forEach {
					dprintln(" - $it")
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
				ordered[it].component.connect(
					ordered[it].pinidx,
					ordered[it+1].component,
					ordered[it+1].pinidx
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
		fun main(vararg args: String) {
			if(args.size < 1) error("Expected number of steps")
			val steps = args[0].toInt()

			val f = Falstad(System.`in`.bufferedReader().readText())

			if(DEBUG) {
				dprintln("$f:\n${f.source}\n${f.roots}\n${f.refs}\n${f.grounds}\n\n${f.circuit}")

				dprintln("### STEP: ${f.circuit.step(f.nominalTimestep)}")

				f.circuit.components.forEach {
					dprintln("$it:")
					when (it) {
						is Resistor -> println("r.i = ${it.current}")
					}
				}

				dprintln("nodes:")
				f.circuit.nodes.forEach {
					dprintln(" - ${it.get() ?: "[removed]"}: ${it.get()?.potential ?: 0.0}V")
				}

				dprintln("outputs:")
				f.outputNodes.withIndex().forEach {
					println("${it.index}: ${it.value.potential}V @${it.value.index}")
				}
			}

			val on = f.outputNodes
			println("#T\t${on.indices.map { "O${it+1}" }.joinToString("\t")}")
			(1..steps).forEach {
				if(!f.circuit.step(f.nominalTimestep)) error("step error (singularity?)")
				println("${it*f.nominalTimestep}\t${on.map { it.potential }.joinToString("\t")}")
			}

			// FileOutputStream("falstad.dot").bufferedWriter().also {
			// 	it.write(f.circuit.toDot())
			// }.close()
		}
	}
}
