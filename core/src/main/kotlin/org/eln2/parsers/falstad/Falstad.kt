package org.eln2.parsers.falstad

import org.eln2.data.MutableMultiMap
import org.eln2.data.mutableMultiMapOf
import org.eln2.debug.DEBUG
import org.eln2.debug.dprintln
import org.eln2.parsers.falstad.components.generic.Ignore
import org.eln2.parsers.falstad.components.generic.InterpretGlobals
import org.eln2.parsers.falstad.components.generic.OutputProbe
import org.eln2.parsers.falstad.components.generic.WireConstructor
import org.eln2.parsers.falstad.components.passive.CapacitorConstructor
import org.eln2.parsers.falstad.components.passive.InductorConstructor
import org.eln2.parsers.falstad.components.passive.ResistorConstructor
import org.eln2.parsers.falstad.components.sources.CurrentSourceConstructor
import org.eln2.parsers.falstad.components.sources.GroundConstructor
import org.eln2.parsers.falstad.components.sources.VoltageRailConstructor
import org.eln2.parsers.falstad.components.sources.VoltageSourceConstructor
import org.eln2.sim.electrical.mna.Circuit
import org.eln2.sim.electrical.mna.Node
import org.eln2.sim.electrical.mna.component.Component
import org.eln2.sim.electrical.mna.component.Resistor
import org.eln2.sim.electrical.mna.component.VoltageSource
import org.eln2.data.DisjointSet
import org.eln2.space.Vec2i

/**
 * A splitting pattern for runs of one or more spaces (ASCII 32).
 *
 * Falstad's own exporter only generates one space between parameters, which cannot contain spaces.
 */
val SPACES = Regex(" +")

/**
 * Refers to a pin of a [Component].
 *
 * In general, the pair of a Component and its index of a pin (its "own [NodeRef]") uniquely identifies the NodeRef of that component. It is not accurate to say this identifies a [Node], as the Nodes of a [Circuit] are progressively merged by connections.
 */
data class PinRef(val component: Component, val pinidx: Int) {
    /**
     * Get the [Node] of this [PinRef].
     *
     * This is the same Node as owned by other [NodeRef]s for [Component]s which are connected to this same "pin".
     */
    val node get() = component.node(pinidx)
}

/**
 * A simple wrapper class for a position (as a [Vec2i]) referring to a position within the Falstad canvas.
 */
data class PinPos(val pos: Vec2i)

/**
 * Represents a line of text from a Falstad save.
 *
 * Conceptually, this is almost always a single [Component], though there are some exceptions for non-component lines (such as a "$" line, which represents global state and configuration, and some other "virtual" components like Output Probes, which we internally represent differently).
 */
class FalstadLine(val params: Array<String>) {
    companion object {
        /**
         * Construct a [FalstadLine] from a string representing a single line of input.
         *
         * The string may have a terminator or not; it is trimmed in either case.
         *
         * null is returned if the line was devoid of data (empty or consisting of only whitespace).
         */
        fun fromLine(s: String): FalstadLine? {
            val trimmed = s.trim()
            return if (trimmed.isEmpty())
                null
            else
                FalstadLine(trimmed.split(SPACES).map { word -> word.trim() }.toTypedArray())
        }

        /**
         * Construct an iterator over [FalstadLine]s from a string representing the source of a Falstad save.
         */
        fun intoLines(src: String) = src.lines().mapNotNull { fromLine(it) }
    }

    /**
     * Index into a Falstad save; returns the String at the given position (0 represents the start of the line).
     */
    operator fun get(i: Int) = params[i]

    /**
     * Get the parameter at position [i] (0 being the first element) in the line as an Int.
     */
    fun getInt(i: Int) = get(i).toInt()

    /**
     * Get the parameter at position [i] (0 being the first element) in the line as a Double.
     */
    fun getDouble(i: Int) = get(i).toDouble()
}

/**
 * Aggregator class used as the parameter to [IComponentConstructor].
 *
 * This class isn't intended to be used outside of the context of the construct function inside of that interface. Inside of that context, this is intended to pass any information an IComponentConstructor might need. Avoid adding arguments to the interface method itself; any additional data should be put into this class.
 */
data class CCData(val falstad: Falstad, val line: FalstadLine) {
    /**
     * Get the underlying [Circuit] which is being constructed.
     */
    val circuit get() = falstad.circuit

    /**
     * The number of pins on this component.
     *
     * This is NOT the same as the positions in the data line. As far as we can tell, the positions there are the bounding box of the component on the canvas, and only map to pin positions in the orthogonal case.
     *
     * This field can be set by an [IComponentConstructor], and specifically [PoleConstructor] will use this value to determine how many [PinRef]s should be constructed and added to the current [Falstad] deserializer. In general, this should be the same as [Component.nodeCount] for constructors which make only a single [Component].
     */
    var pins: Int = 2

    /**
     * Get the pin positions of this component as a List<[PinPos]>.
     *
     * As far as we know, this is the bounding box of the component. This just happens to coincide with the pin positions of orthogonal bipoles.
     *
     * The exact elements consumed are indices 1 through 4 inclusive.
     */
    val pinPositions get() = (0 until 2).map { i -> PinPos(Vec2i(line.getInt(1 + 2 * i), line.getInt(2 + 2 * i))) }

    /**
     * Get the first point of the bounding box in pinPositions.
     *
     * By convention, we consider this to be the negative terminal of a bipole, thus the name.
     */
    val neg: PinPos get() = pinPositions[0]

    /**
     * Get the second point of the bounding box in pinPositions.
     *
     * By convention, we consider this to be the positive terminal of a bipole, thus the name.
     */
    val pos: PinPos get() = pinPositions[1]

    /**
     * Get the integer flags argument from this constructor line.
     *
     * This is the Int value of the field at index 5.
     */
    val flags: Int get() = line.getInt(5)

    /**
     * Get the type code argument from this constructor line.
     *
     * This is the String value of the field at index 0 (the start of the line).
     */
    val type: String get() = line[0]

    /**
     * Get the data from this constructor line, as a List<String>.
     *
     * This is the sequence of Strings starting at index 6 of the line (past the flags argument).
     *
     * In Falstad's model, these are usually space-separated freeform data defined on a per-component basis.
     */
    val data get() = line.params.drop(6)
}

/**
 * A [DisjointSet] subclass which holds a [PinPos].
 *
 * The Set class is used to implement the Disjoint Sets algorithm; the representative of the merged sets is used to determine which PinPos' are connected by wires, and thus merged into [Node]s as present in the [Circuit].
 */
data class PosSet(val pos: PinPos) : DisjointSet()

/**
 * An interface for constructing [Component]s (usually)
 *
 * This is the core interface for turning a [FalstadLine] into a Component. It need not actually produce a Component; FalstadLines sometimes represent non-component data (such as global constants, or constructs with which we do not have a one-to-one mapping with our Components).
 */
interface IComponentConstructor {
    companion object {
        /**
         * Type constructor map, switched on the first field (the [CCData.type] code).
         *
         * This is mutable and can be registered into at runtime. There is no reentrant interface to do this at the moment.
         *
         * Generally, failing to find a typecode in here is a fatal error at construction time of a [Falstad] deserializer.
         */
        val TYPE_CONSTRUCTORS: MutableMap<String, IComponentConstructor> = mutableMapOf(
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

        /**
         * Returns the [IComponentConstructor] suitable for use to construct this [FalstadLine].
         *
         * @throws IllegalStateException when the type code is not found in [TYPE_CONSTRUCTORS].
         */
        fun getForLine(fl: FalstadLine) = TYPE_CONSTRUCTORS[fl[0]] ?: error("unrecognized type: ${fl[0]}")
    }

    /**
     * Code to generally construct a component.
     *
     * The constructor receives a [CCData] context [ccd], which is a class specifically made to be this parameter and encapsulate all necessary context. DO NOT add parameters to this method; instead, add contextual data as fields to CCData. The context includes the line itself (as a [FalstadLine]) and the [Falstad] deserializer instance (with its [Circuit]).
     *
     * The method is expected to mutate the context data suitable to the information found in its line; it does not return a value.
     */
    fun construct(ccd: CCData)
}

/**
 * A helper class for the common case of constructing (up to) bipoles.
 *
 * This abstract class implements [IComponentConstructor] and handles most of the routine parts of adding [PinRef]s to the [Falstad] context, as well as registering [Component]s with the [Circuit]. Use this class when there is a more-or-less one-to-one mapping between a [Falstad line][FalstadLine] and a [Component]; otherwise, [IComponentConstructor] is more general.
 */
abstract class PoleConstructor : IComponentConstructor {
    /**
     * Returns the [Component] which is to be added to the [Circuit].
     */
    abstract fun component(ccd: CCData): Component

    /**
     * "Configures" the component; this usually involves initializing Component-specific data (resistance for resistors, capacitance for capactors, etc.)
     */
    abstract fun configure(ccd: CCData, cmp: Component)

    /**
     * Constructs the bipole component, using component and configure methods.
     */
    override fun construct(ccd: CCData) {
        val c = component(ccd)
        ccd.circuit.add(c)
        configure(ccd, c)
        ccd.pinPositions.withIndex().forEach { posidx ->
            val pp = (ccd.falstad.getPin(posidx.value).representative as PosSet)
            ccd.falstad.addPinRef(pp, PinRef(c, posidx.index))
        }
    }
}

/**
 * The Falstad deserializer.
 *
 * Given a source string as a constructor parameter, this class makes a [Circuit] corresponding to the same Falstad simulation which was exported.
 *
 * In general, [Component]s should have the exact same values if they can be represented at all. The state of a Component (e.g., the hidden variables of capacitors and inductors) are also restored.
 *
 * The Circuit should NOT be used in game logic code without some sanitization; in particular, Falstad allows for the construction of arbitrary and untrustworthy values in many locations, which can (at the very least) unbalance the game.
 */
class Falstad(val source: String) {
    /**
     * "List of roots" (of the [Disjoint Set][DisjointSet] forest); a mapping from [PinPos] to [PosSet].
     *
     * The values of this map are members of the forest (not necessarily "roots", but the root can easily be found as the representative).
     */
    val roots: MutableMap<PinPos, PosSet> = mutableMapOf()

    /**
     * Get a [PosSet] from a [PinPos], registering it in [roots] if need be.
     *
     * The return value can be safely ignored if roots registration was the only goal.
     */
    fun getPin(p: PinPos) = roots.getOrPut(p, { PosSet(p) })

    /**
     * "List of refs"; a MultiMap from [PosSet] to [PinRef].
     */
    val refs: MutableMultiMap<PosSet, PinRef> = mutableMultiMapOf()

    /**
     * Get the set of [PinRef]s corresponding to this [PosSet] (creating an empty set it if it doesn't exist).
     */
    fun getPinRefs(p: PosSet) = refs[p]

    /**
     * Add a [PinRef] [pr] to the set of refs in [PosSet] [p]; in effect, declare that the PinRef ([Component] pin) [pr] is located at the position contained in the PosSet [p] (normally returned by [getPin]).
     */
    fun addPinRef(p: PosSet, pr: PinRef) = getPinRefs(p).add(pr)

    /**
     * "Set of grounds"; the locations of ground nodes to be unified on the canvas, encapsulated in [PosSet]s.
     */
    val grounds: MutableSet<PosSet> = mutableSetOf()

    /**
     * Add a ground to the ground set; in effect, declare that the position in the given [PosSet] [p] (usually via [getPin]) is grounded.
     */
    fun addGround(p: PosSet) = grounds.add(p)

    /**
     * Set of outputs; anything labelled as an "analog output" in the Falstad circuit.
     *
     * They have an arbitrary ordering, loosely based on creation order, within the save; however, their labels appear not to be saved, otherwise this would map them instead.
     */
    val outputs: MutableSet<PinRef> = mutableSetOf()

    /**
     * Declare [pr] as an output [PinRef].
     *
     * Note that this does not take a [PosSet]; the PinRef is assumed to be the component against which the output is determined. If this is to be connected in the Falstad canvas, this PinRef should also be correlated with a PosSet using [addPinRef].
     */
    fun addOutput(pr: PinRef) = outputs.add(pr)

    /**
     * The set of output [Node]s corresponding to the output [PinRef]s.
     *
     * This can be used, e.g., to determine output potentials.
     */
    val outputNodes get() = outputs.map { it.node }

    /**
     * The globally configured Falstad timestep, which is often different from our usual 0.05s timestep (Falstad's default is 5us).
     */
    var nominalTimestep = 0.05

    /**
     * Whether or not the [Circuit] is floating.
     *
     * A circuit is "floating" if it has no privileged ground reference. Circuits that do not have at least some connection to ground often underconstrain the MNA matrix and fail to iterate.
     *
     * An empty circuit is floating by definition; it stops floating when a component is constructed that provides a reference to ground. Currently, this list includes:
     * - Explicit grounds (element "g");
     * - Voltage rails (which act as a [VoltageSource] which is always grounded).
     * Others may arise in the future. The constructors for these components set this field to false directly.
     */
    var floating = true

    /**
     * The [Circuit] being constructed by this Falstad deserializer; this might be considered the output of this process.
     */
    val circuit = Circuit()

    init {
        FalstadLine.intoLines(source).forEach { line ->
            IComponentConstructor.getForLine(line)
                .construct(CCData(this, line))
        }

        if (DEBUG) {
            val repmap: MutableMultiMap<PosSet, PosSet> = mutableMultiMapOf()
            roots.values.forEach { set ->
                dprintln("F.<init>: r $set => ${set.representative}")
                repmap[set.representative as PosSet] = set
            }

            repmap.keyMapping.forEach { rep ->
                dprintln("F.<init>: R ${rep.key}:")
                rep.value.forEach { set ->
                    dprintln(" - $set")
                }
            }
        }

        val mergedRefs: MutableMultiMap<PosSet, PinRef> = mutableMultiMapOf()
        roots.values.forEach { posset ->
            mergedRefs[posset.representative as PosSet].addAll(refs[posset])
        }

        if (DEBUG) mergedRefs.entries.forEach { pair ->
            dprintln("F.<init>: mR ${pair.key} => ${pair.value}")
        }

        mergedRefs.valueSets.forEach { pinset ->
            val ordered = pinset.toList() // Just need some ordering, any will do
            (0 until ordered.size - 1).forEach { i ->
                ordered[i].component.connect(
                    ordered[i].pinidx,
                    ordered[i + 1].component,
                    ordered[i + 1].pinidx
                )
            }
        }

        grounds.forEach { posset ->
            val pinset = refs[posset.representative as PosSet]
            if (pinset.isNotEmpty()) {
                // Only need to connect 1; the disjoint PosSets are merged right now
                val pr = pinset.iterator().next()
                pr.component.ground(pr.pinidx)
            }
        }

        if (floating) {
            // This isn't expected to be the hotpath, so spend some extra time
            // looking for a valid VoltageSource to fix an arbitrary ground
            dprintln("F.<init>: floating!")
            var found = false
            for (comp in circuit.components) {
                if (comp is VoltageSource) {
                    dprintln("F.<init>: floating: ground $comp pin 1 (node ${comp.node(1)}")
                    comp.ground(1)
                    found = true
                    break
                }
            }
            if (!found) println("WARN: F.<init>: floating circuit and no VSource; the matrix is likely underconstrained.")
        }
    }

    companion object {
        /**
         * Falstad's main: run a circuit description for a given number of steps.
         *
         * This tool takes, as a command line argument, an integer number of steps, and reads a Falstad circuit from stdin. If it succeeeds, it simulates it for the given number of steps, writing a GNUplot-compatible data file to stdout which contains a timestamp (first column) followed by the node potentials (in Volts) of every output node ("analog outputs" by Falstad).
         *
         * The output file can be redirected and plotted using a line such as `plot 'file.dat' with lines`.
         *
         * The simulation rate is taken from the Falstad description.
         *
         * Be careful when running with [DEBUG] enabled; the debugging output will intersperse into the data file.
         */
        @JvmStatic
        fun main(vararg args: String) {
            if (args.size < 1) error("Expected number of steps")
            val steps = args[0].toInt()

            val f = Falstad(System.`in`.bufferedReader().readText())

            if (DEBUG) {
                dprintln("$f:\n${f.source}\n${f.roots}\n${f.refs}\n${f.grounds}\n\n${f.circuit}")

                dprintln("### STEP: ${f.circuit.step(f.nominalTimestep)}")

                f.circuit.components.forEach { comp ->
                    dprintln("$comp:")
                    when (comp) {
                        is Resistor -> println("r.i = ${comp.current}")
                    }
                }

                dprintln("nodes:")
                f.circuit.nodes.forEach { noderef ->
                    dprintln(" - ${noderef.get() ?: "[removed]"}: ${noderef.get()?.potential ?: 0.0}V")
                }

                dprintln("outputs:")
                f.outputNodes.withIndex().forEach { nodeidx ->
                    println("${nodeidx.index}: ${nodeidx.value?.potential}V @${nodeidx.value?.index}")
                }
            }

            val on = f.outputNodes
            println("#T\t${on.indices.joinToString("\t") { i -> "O${i + 1}" }}")
            (1..steps).forEach { step ->
                if (!f.circuit.step(f.nominalTimestep)) error("step error (singularity?)")
                println("${step * f.nominalTimestep}\t${on.joinToString("\t") { node -> node?.potential.toString() }}")
            }

            // FileOutputStream("falstad.dot").bufferedWriter().also {
            // 	it.write(f.circuit.toDot())
            // }.close()
        }
    }
}
