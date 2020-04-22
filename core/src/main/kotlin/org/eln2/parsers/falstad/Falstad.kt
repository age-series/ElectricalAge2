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
				FalstadLine(trimmed.split(SPACES).map { word -> word.trim() }.toTypedArray())
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

	val pinPositions get() = (0 until min(pins, 2)).map { i -> PinPos(Vec2i(line.getInt(1 + 2 * i), line.getInt(2 + 2 * i))) }

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
		ccd.pinPositions.withIndex().forEach {posidx ->
			val pp = (ccd.falstad.getPin(posidx.value).representative as PosSet)
			ccd.falstad.addPinRef(pp, PinRef(c, posidx.index))
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
		FalstadLine.intoLines(source).forEach { line ->
			IComponentConstructor.getForLine(line)
				.construct(CCData(this, line))
		}

		if(DEBUG) {
			val repmap: MutableMap<PosSet, MutableSet<PosSet>> = mutableMapOf()
			roots.values.forEach {set ->
				dprintln("F.<init>: r $set => ${set.representative}")
				repmap.getOrPut(set.representative as PosSet, { mutableSetOf() }).add(set)
			}

			repmap.entries.forEach { rep ->
				dprintln("F.<init>: R ${rep.key}:")
				rep.value.forEach {set ->
					dprintln(" - $set")
				}
			}
		}

		val mergedRefs: MutableMap<PosSet, MutableSet<PinRef>> = mutableMapOf()
		roots.values.forEach {posset ->
			val pinset = refs[posset]
			if(pinset != null) {
				mergedRefs.getOrPut(posset.representative as PosSet, { mutableSetOf() }).addAll(pinset)
			}
		}

		if(DEBUG) mergedRefs.entries.forEach {pair ->
			dprintln("F.<init>: mR ${pair.key} => ${pair.value}")
		}

		mergedRefs.values.forEach {pinset ->
			val ordered = pinset.toList()  // Just need some ordering, any will do
			(0 until ordered.size - 1).forEach {i ->
				ordered[i].component.connect(
					ordered[i].pinidx,
					ordered[i+1].component,
					ordered[i+1].pinidx
				)
			}
		}

		grounds.forEach {posset ->
			val pinset = refs[posset.representative]
			if(pinset != null && pinset.size > 0) {
				// Only need to connect 1; the disjoint PosSets are merged right now
				val pr = pinset.iterator().next()
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

				f.circuit.components.forEach {comp ->
					dprintln("$comp:")
					when (comp) {
						is Resistor -> println("r.i = ${comp.current}")
					}
				}

				dprintln("nodes:")
				f.circuit.nodes.forEach {noderef ->
					dprintln(" - ${noderef.get() ?: "[removed]"}: ${noderef.get()?.potential ?: 0.0}V")
				}

				dprintln("outputs:")
				f.outputNodes.withIndex().forEach {nodeidx ->
					println("${nodeidx.index}: ${nodeidx.value.potential}V @${nodeidx.value.index}")
				}
			}

			val on = f.outputNodes
			println("#T\t${on.indices.map { i -> "O${i+1}" }.joinToString("\t")}")
			(1..steps).forEach {step ->
				if(!f.circuit.step(f.nominalTimestep)) error("step error (singularity?)")
				println("${step*f.nominalTimestep}\t${on.map { node -> node.potential }.joinToString("\t")}")
			}

			// FileOutputStream("falstad.dot").bufferedWriter().also {
			// 	it.write(f.circuit.toDot())
			// }.close()
		}
	}
}
