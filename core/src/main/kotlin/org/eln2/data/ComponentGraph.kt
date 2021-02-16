package org.eln2.data

import org.eln2.debug.dprintln
import java.lang.StringBuilder
import java.util.Stack

/**
 * An edge in the [ComponentGraph], connecting two vertices bidirectionally.
 *
 * Normally, these are constructed by calling [ComponentGraph.MutationGuard.connect]. A subclass of ComponentGraph can override its internal [ComponentGraph.connect] method to, e.g., return a subclass of Edge.
 *
 * The current implementation has no concept of directed edges (as that would require discriminating between strongly and weakly connected components), but the ordering is preserved for the lifetime of this object.
 */
open class Edge(val a: Vertex, val b: Vertex): Iterable<Vertex> {
	override fun iterator() = arrayOf(a, b).iterator()
}

/**
 * A vertex in the [ComponentGraph], representing a discrete entity that can be connected (by [Edge]s) to other vertices.
 *
 * These are often constructed by calling [ComponentGraph.MutationGuard.newVertex]. A ComponentGraph subclass can override the internal [ComponentGraph.newVertex] method to, e.g., return a subclass.
 *
 * When constructed, a vertex is in a singleton [ConnectedComponent] (as created by [ComponentGraph.constructComponent]). This _does not change_ during ComponentGraph mutation until the last outstanding [ComponentGraph.MutationGuard] is dropped, at which point the [ConnectedComponent]s are properly realized. See [ComponentGraph.mutate] for details.
 */
open class Vertex(val graph: ComponentGraph) {
    /**
     * The [ConnectedComponent] to which this vertex belongs.
     *
     * This is not up-to-date until the last [ComponentGraph.MutationGuard] is dropped. See [ComponentGraph.mutate] for details.
     */
	var component = graph.constructComponent(this)

    /**
     * The "incidence map" of this vertex, mapping the distal vertex to an [Edge] which contains both vertices.
     */
    val incident: MutableMap<Vertex, Edge> = mutableMapOf()
}

/**
 * A (strongly) "connected component" in the [ComponentGraph], representing a set of vertices such that there exists a path from every vertex in the set to every other vertex.
 *
 * The user often doesn't construct these; rather, the ComponentGraph does (in [ComponentGraph.assignComponents]). The user can arrange to see them in their fully constructed state by adding a [ComponentListener] and observing its [ComponentListener.componentRealized] events.
 */
open class ConnectedComponent(initialVertex: Vertex) {
    /**
     * The (vertices)[Vertex] within this connected component.
     */
	val vertices: MutableSet<Vertex> = mutableSetOf(initialVertex)

    /**
     * Add a vertex to this component.
     *
     * This is usually only called from [ComponentGraph.assignComponents].
     */
	internal open fun add(v: Vertex) = vertices.add(v)

    /**
     * Remove a vertex from this component.
     *
     * This should only be called from [ComponentGraph.assignComponents], but the current non-incremental algorithm doesn't yet do this.
     */
	internal open fun remove(v: Vertex) = vertices.remove(v)

    /**
     * Remove all vertices from this component.
     *
     * This is normally done only from [ComponentGraph.assignComponents].
     */
	internal open fun clear() = vertices.clear()

    /**
     * Whether or not this component has been "realized"--whether listeners ("user code") have seen this component in its constructed state.
     *
     * Some connected components are transiently constructed, such as the default singleton belonging to every [Vertex] on construction (see [Vertex.component]'s documentation). Since this is not likely to be valid, and it is not efficient to compute these online, the [ComponentGraph] waits until all outstanding mutations are done to "realize" the [ConnectedComponent]s, setting this field in the process.
     *
     * Connected components which aren't realized have not been witnessed by user code, and can safely be dropped without being unrealized.
     *
     * Modification of a realized component requires two more calls into user code, before and after modification ([ComponentListener.realizedComponentModificationStarted] and [ComponentListener.realizedComponentModificationFinished]), to allow users to synchronize state.
     */
	internal var realized = false

    /**
     * An internal callback for when this connected component is [realized], to be overridden in subclasses.
     */
    internal open fun realize() {}

    /**
     * An internal callback for when this component is un[realized], to be overriden in subclasses.
     */
	internal open fun unrealize() {}
}

/**
 * An interface to be implemented by users interested in events about [ConnectedComponent]s.
 */
interface ComponentListener {
    /**
     * Called when a [ConnectedComponent] is realized--constructed and with a final set of vertices.
     *
     * See [ConnectedComponent.realized] for more details.
     */
	fun componentRealized(c: ConnectedComponent)

    /**
     * Called when a [ConnectedComponent] is unrealized--it will no longer be held by the [ComponentGraph].
     */
	fun componentUnrealized(c: ConnectedComponent)

    /**
     * Called when a realized [ConnectedComponent] is about to be modified. This is called before the set of vertices changes.
     */
    fun realizedComponentModificationStarted(c: ConnectedComponent)

    /**
     * Called when a realized [ConnectedComponent] has been modified. This is called once the set of vertices is up-to-date again.
     */
    fun realizedComponentModificationFinished(c: ConnectedComponent)
}

/**
 * An interface to be implemented by users interested in events about (vertices)[Vertex].
 */
interface VertexListener {
    /**
     * Called when a [Vertex] is added to the graph.
     *
     * Its [Vertex.component] is not yet valid--wait until components are realized (via [ComponentListener.componentRealized]) for that.
     */
	fun vertexAdded(v: Vertex)

    /**
     * Called when a [Vertex] is removed from the graph.
     *
     * Its [Vertex.component] may not be representative of the actual state of the graph (if connectivity changed before this removal), but it will definitely be the same [ConnectedComponent] it was in during the last realization.
     */
    fun vertexRemoved(v: Vertex)
}

/**
 * An interface to be implemented by users interested in events about [Edge]s.
 */
interface EdgeListener {
    /**
     * Called when an [Edge] is added to the graph.
     *
     * This likely changed connectivity, but this isn't yet reflected in the [ConnectedComponent]s--wait for [ComponentListener.realizedComponentModificationFinished] for that.
     */
	fun edgeAdded(e: Edge)

    /**
     * Called when an [Edge] is removed from the graph.
     *
     * This may have changed connectivity, but this isn't yet reflected in the [ConnectedComponent]s--wait for [ComponentListener.realizedComponentModificationFinished] for that.
     */
	fun edgeRemoved(e: Edge)
}

/**
 * The Component Graph, the core abstraction of an undirected graph that keeps track of connected components.
 *
 * The vertices and edges consist of [Vertex] and [Edge] instances. The output of the algorithm determining the connected components results in a set of [ConnectedComponent]s.
 *
 * Most of these data have references to (or containers of references to) each other, for ease of use, but this does mean that careless user code can witness invariants be broken. This is especially the case because the algorithm to determine the components is O((V+E) * L) (for V vertices and E edges), with L the lookup cost for the sets (presently amortized constant). To avoid both performance penalties and the observation of variants, methods for changing the graph are internal (and can only be accessed publicly from a [MutationGuard]), and the ComponentGraph "realizes" [ConnectedComponent]s only once a [MutationGuard] is dropped (one is normally constructed and scoped via [mutate]). See [mutate] for more details.
 *
 * The ComponentGraph can have any number of "listeners"--user objects implementing [ComponentListener], [VertexListener], and [EdgeListener]. Of the three, the [ComponentListener] is poised especially to be used for collecting the output of the connected component algorithm, but the other two are useful for middleware adapters.
 *
 * [ComponentGraph] can be used as-is, and with the default [Vertex], [Edge], and [ComponentGraph] classes, but it (and all of these) can also be subclassed. This class contains construction utility methods ([constructVertex], [constructEdge], and [constructComponent] respectively) that allows subclasses to simply override the construction process.
 */
open class ComponentGraph {
    /**
     * The set of (vertices)[Vertex] which are considered to be in this graph.
     */
	val vertices: MutableSet<Vertex> = mutableSetOf()

    /**
     * The set of [Edge]s considered to be in this graph.
     *
     * As an invariant, this is always the union of all "incident edges" from every vertex's [Vertex.incident] value sets.
     */
	val edges: MutableSet<Edge> = mutableSetOf()

    /**
     * The set of [ConnectedComponent]s in this graph.
     *
     * This is only guaranteed to be valid and up-to-date when the graph has no outstanding [MutationGuard]s (when [isImmutable] is true).
     */
	var components: MutableSet<ConnectedComponent> = mutableSetOf()

    /**
     * A set of listeners for this graph implementing [ComponentListener].
     */
	val componentListeners: MutableSet<ComponentListener> = mutableSetOf()

    /**
     * A set of listeners for this graph implementing [VertexListener].
     */
	val vertexListeners: MutableSet<VertexListener> = mutableSetOf()

    /**
     * A set of listeners for this graph implementing [EdgeListener].
     */
	val edgeListeners: MutableSet<EdgeListener> = mutableSetOf()

    /**
     * Add a listener to this graph.
     *
     * The listener _should_ implement one of the [ComponentListener], [VertexListener], or [EdgeListener] interfaces (this is a no-op if it doesn't). It will receive the proper events from now on, but do note that most event interfaces concern _changes_ in state, so any objects looking to _track_ state will have to inspect the _current_ state of the graph (which is only safe to do outside of mutation--when [isImmutable] is true).
     */
	fun addListener(listener: Any) {
		if(listener is ComponentListener) componentListeners.add(listener)
		if(listener is VertexListener) vertexListeners.add(listener)
		if(listener is EdgeListener) edgeListeners.add(listener)
	}

    /**
     * Remove a listener from this graph.
     *
     * If the listener was added via [addListener], it will be removed. If it was already removed, or never added, this is a no-op, but otherwise safe.
     */
	fun removeListener(listener: Any) {
		componentListeners.remove(listener)
		vertexListeners.remove(listener)
		edgeListeners.remove(listener)
	}

    /**
     * Construct a [ConnectedComponent] (or a subclass).
     *
     * This is intended to be overridden to produce a more specific subclass in a [ComponentGraph] subclass.
     */
	internal open fun constructComponent(v: Vertex) = ConnectedComponent(v)

    /**
     * Construct a [Vertex] (or a subclass).
     *
     * This is intended to be overridden to produce a more specific subclass in a [ComponentGraph] subclass.
     */
	internal open fun constructVertex() = Vertex(this)

    /**
     * Construct an [Edge] (or a subclass).
     *
     * This is intended to be overridden to produce a more specific subclass in a [ComponentGraph] subclass.
     *
     * The ordering of arguments is preserved to the [Edge] constructor, which might be of use for "pseudo-oriented" edges.
     */
    internal open fun constructEdge(a: Vertex, b: Vertex) = Edge(a, b)

    /**
     * Add a new vertex, internally. This is intended to be used by the [MutationGuard] via [mutate].
     */
	internal fun newVertex() = constructVertex().apply {
		vertices.add(this)
		components.add(component)
		vertexListeners.forEach { it.vertexAdded(this) }
	}

    /**
     * Remove a vertex, internally. This is intended to be used by the [MutationGuard] via [mutate].
     *
     * The [ComponentGraph] implementation is safely idempotent, but this invokes [VertexListener]s, which may or may not be safely idempotent.
     */
	internal fun removeVertex(v: Vertex) {
		vertexListeners.forEach { it.vertexRemoved(v) }
		disconnect(v)
		vertices.remove(v)
	}

    /**
     * Connect two (vertices)[Vertex], internally, returning an [Edge] (or a subclass). This is intended to be used by the [MutationGuard] via [mutate].
     *
     * The ordering of arguments is preserved to the [Edge] constructor, which might be of use for "pseudo-oriented" edges.
     */
	internal fun connect(a: Vertex, b: Vertex): Edge {
		assert(a.graph == this) { "CG.c: vertex a=$a not in graph $this" }
		assert(b.graph == this) { "CG.c: vertex b=$b not in graph $this" }
		
		val edge = constructEdge(a, b)
		edges.add(edge)
		a.incident[b] = edge
		b.incident[a] = edge

		edgeListeners.forEach { it.edgeAdded(edge) }
		
		return edge
	}

    /**
     * Disconnect two (vertices)[Vertex], internally, returning whether or not any change occurred. This is intended to be used by the [MutationGuard] via [mutate].
     */
	internal fun disconnect(a: Vertex, b: Vertex): Boolean {
		assert(a.graph == this) { "CG.d: vertex a=$a not in graph $this" }
		assert(b.graph == this) { "Cg.d: vertex b=$a not in graph $this" }
		
		val edge = a.incident[b]
		return if(edge != null) {
			edgeListeners.forEach { it.edgeRemoved(edge) }
			
			a.incident.remove(b)
			b.incident.remove(a)
			edges.remove(edge)

			true
		} else {
			false
		}
	}

    /**
     * Disconnect a [Vertex] from all incident edges. This is intended to be used by the [MutationGuard] via [mutate].
     */
	internal fun disconnect(v: Vertex) {
		v.incident.keys.forEach {
			disconnect(v, it)
		}
		assert(v.incident.isEmpty())
	}

    /**
     * A "visitation queue entry"--a member of the "visitation stack" used by [visitDepthFirst]. Not intended for public use.
     */
	protected class Visitation(val vertex: Vertex, val phase: Phase, val root: Boolean = false) {
		enum class Phase {
			Down, Up
		}
	}

    /**
     * Visit the graph using a depth-first search.
     *
     * Starting from the given [start] [Vertex], this is guaranteed to evaluate both [downPhase] and [upPhase] for _all_ [vertices] in the graph. Other than [start], these other "roots" are chosen arbitrarily.
     *
     * [downPhase] is called with the following arguments, in order:
     * 
     * - `visited` ([Vertex]) is the [Vertex] being visited;
     * - `depth` (Int) is the depth of this visitation--how far this visit is from the root (not guaranteed to be the shortest path, however);
     * - `isRoot` (Boolean) is whether or not this [Vertex] was visited spontaneously--not because it was connected to any other root (equivalently, when `depth` is 0);
     * - `neighbors` (MutableSet<[Vertex]>) is the _mutable_ set of incident of the current `visited` [Vertex] (user code can change this set to control which vertices are visited).
     *
     * [upPhase] is called with the following arguments, in order:
     *
     * - `visited` ([Vertex]) is the [Vertex] being visited;
     * - `depth` (Int) is the depth of this visitation, as above.
     * 
     * The most important user of this is [assignComponents].
     */
	fun visitDepthFirst(
		downPhase: (visited: Vertex, depth: Int, isRoot: Boolean, neighbors: MutableSet<Vertex>) -> Unit,
		upPhase: (visited: Vertex, depth: Int) -> Unit,
		start: Vertex? = null
	) {
		val unvisited = vertices.toMutableSet()  // XXX Relying on the implementation detail of a copy here...
		var depth = 0
		val stack = Stack<Visitation>()

		while(unvisited.isNotEmpty()) {
			if(stack.isEmpty()) {
				dprintln("adding a new root")
				stack.push(Visitation(start ?: unvisited.first(), Visitation.Phase.Down, true))
			}

			val visitation = stack.pop()
			val vertex = visitation.vertex
			dprintln("visiting $vertex phase ${visitation.phase} root ${visitation.root}")

			when(visitation.phase) {
				Visitation.Phase.Down -> {
					unvisited.remove(vertex)
					stack.push(Visitation(vertex, Visitation.Phase.Up))

					val neighbors = vertex.incident.keys
					dprintln("pre-down neighbors: $neighbors")
					downPhase(vertex, depth, visitation.root, neighbors)
					dprintln("post-down neighbors: $neighbors")
					neighbors.forEach { if (it in unvisited) stack.push(Visitation(it, Visitation.Phase.Down)) }
					depth++
				}
				Visitation.Phase.Up -> {
					depth--
					upPhase(vertex, depth)
				}
			}
		}
	}

    /**
     * Assigns the [ConnectedComponent]s of this graph. When this finished execution, the graph's [components] are up-to-date, and all listeners (especially [ComponentListener]) are informed.
     *
     * This is normally the last step called after [mutate] before [isImmutable] is true. See [mutate] for details.
     */
	// FIXME: There are several optimizations which can be applied here:
	// - Find bridges (non-bridge edges can be removed without issue)
	// - Minimize component changeover (don't just choose one and clear it)
	internal fun assignComponents(start: Vertex? = null) {
		var currentComponent: ConnectedComponent? = null
		val newComponents: MutableSet<ConnectedComponent> = mutableSetOf()
        val modifiedComponents: MutableSet<ConnectedComponent> = mutableSetOf()

		visitDepthFirst({
			vertex, _, isRoot, _ ->
			dprintln("visiting $vertex root=$isRoot")
			if(isRoot) {
                if(vertex.component in newComponents) {
                    dprintln("forcing new component onto $vertex")
                    // Don't allow a vertex to, by identity, share a component already registered if it is a root; this
                    // state means this particular vertex was formerly connected, but now is not.
                    vertex.component = constructComponent(vertex)
                }
				currentComponent = vertex.component
                dprintln("current component is $currentComponent realized=${currentComponent!!.realized}")
                if(currentComponent!!.realized) {
                    dprintln("component $currentComponent already realized")
                    componentListeners.forEach { listener -> listener.realizedComponentModificationStarted(currentComponent!!)}
                    modifiedComponents.add(currentComponent!!)
                }
				newComponents.add(currentComponent!!)
				currentComponent!!.clear()
				currentComponent!!.add(vertex)
			} else {
				vertex.component = currentComponent!!
				currentComponent!!.add(vertex)
			}
		}, {_, _ -> Unit}, start)

		dprintln("old components $components, new components $newComponents")

		// Process removed components which were realized
		(components - newComponents).forEach {
			if(it.realized) {
				componentListeners.forEach { listener -> listener.componentUnrealized(it) }
				it.unrealize()
                it.realized = false
			}
		}

		components = newComponents
		
		// Realize any added components
        (components - modifiedComponents).forEach {
			if(!it.realized) {
                it.realized = true
				it.realize()
				componentListeners.forEach { listener -> listener.componentRealized(it) }
			}
		}

        modifiedComponents.forEach {
            componentListeners.forEach { listener -> listener.realizedComponentModificationFinished(it) }
        }
	}

    /**
     * The number of outstanding [MutationGuard]s.
     * 
     * The graph is "immutable" when this is 0.
     */
	var outstandingGuards = 0
		protected set

    /**
     * Whether or not [outstandingGuards] is 0 (the graph has no outstanding [MutationGuard]s).
     */
    val isImmutable
        get() = outstandingGuards == 0

    /**
     * A guard that automatically updates the graph when the last one is dropped.
     * 
     * This interacts with [outstandingGuards] for correctness.
     *
     * This class' safety can _only_ be guaranteed if the following two invariants are upheld:
     *
     * 1. [MutationGuard.drop] is called, _in every execution path_, when the [MutationGuard] is no longer in use.
     * 2. This instance is _not_ used (no method is called) any more after [MutationGuard.drop] is called.
     *
     * It's not necessary to drop the reference after (2), but it's much easier to uphold this invariant if this is done.
     *
     * Usually, one of these is constructed and used as the "context object" in [mutate]. See that for details.
     */
	inner class MutationGuard internal constructor() {
        /**
         * The visit root first considered after (dropping)[drop] this guard.
         *
         * The default of `null` is safe, but this can be used to inform the algorithm about a [ConnectedComponent] that should be preserved (having already been realized).
         */
        var visitRoot: Vertex? = null
            protected set

        /**
         * Set the [visitRoot].
         *
         * This can only be done once per guard, and should only be done just before returning from a [mutate] closure.
         *
         * Remember that this is just a hint, and can't guarantee how visitation will occur.
         */
        fun setRoot(v: Vertex) {
            assert(visitRoot == null) { "tried to set visit root $v when already set to $visitRoot" }
            visitRoot = v
        }

		init {
			outstandingGuards++
			dprintln("beginning mutation, nest level $outstandingGuards")
		}

        /**
         * Connect two (vertices)[Vertex].
         *
         * The order of vertices to the [Edge] constructor is preserved. This may be useful for "pseudo-oriented" edges.
         */
        fun connect(a: Vertex, b: Vertex) = this@ComponentGraph.connect(a, b)

        /**
         * Disconnect two (vertices)[Vertex], returning whether or not the graph actually changed (if an edge was removed).
         */
		fun disconnect(a: Vertex, b: Vertex) = this@ComponentGraph.disconnect(a, b)

        /**
         * Disconnect every edge incident to [Vertex] [v].
         */
		fun disconnect(v: Vertex) = this@ComponentGraph.disconnect(v)

        /**
         * Add (and return) a new [Vertex] to the underlying [ComponentGraph].
         */
        fun newVertex() = this@ComponentGraph.newVertex()

        /**
         * Remove [Vertex] [v] from the graph.
         *
         * The base implementation is safely idempotent, but the listeners might not be.
         */
        fun removeVertex(v: Vertex) = this@ComponentGraph.removeVertex(v)

        /**
         * Drop this [MutationGuard]. Normally, this does not need to be done explicitly; [mutate] will do this automatically.
         *
         * Once this is done, it is _not safe_ to use any other method on this instance.
         */
        fun drop() {
			dprintln("ending mutation, nest level $outstandingGuards")
			if(--outstandingGuards == 0) {
				assignComponents(visitRoot)
			}
		}
	}

    /**
     * Safely mutate this graph.
     *
     * This provides the closure with access (in the context) to a [MutationGuard] object, which is (dropped)[MutationGuard.drop] when the closure returns.
     *
     * The reference _can_, but _shouldn't_, be stored anywhere, to maintain the second invariant of the [MutationGuard].
     *
     * Mutations can safely be nested (it is safe to call [mutate], directly or indirectly, from another [mutate] closure). Only when the last [MutationGuard] is dropped is the graph state brought up-to-date (via [assignComponents]). After this final drop (usually at the return of a user closure), [isImmutable] is true and all observable invariants (especially the state of [components] and each [Vertex.component]) is valid.
     */
	fun<T> mutate(body: MutationGuard.() -> T): T {
		val guard = MutationGuard()
		try {
			return with(guard, body)
		} finally {
			guard.drop()
		}
	}

    /**
     * Convert the graph to the source of a GraphViz file.
     */
	fun toDot(): String {
		val sb = StringBuilder()
		sb.append("graph {\n")

		val vertexNames = vertices.mapIndexed { i, v -> Pair(v, i) }.toMap()
		val componentNames = components.mapIndexed { i, c -> Pair(c, i) }.toMap()

		sb.append("\t// Vertices\n")
		sb.append("\tverror [label=\"error node\" color=\"magenta\"];\n")
		vertexNames.entries.forEach {(vertex, name) ->
			val cname = componentNames[vertex.component]
			val color = if(cname != null) {
				"/spectral11/${cname % 11 + 1}"
			} else {
				"magenta"
			}
			sb.append("\tv$name [label=\"v$name\\nc$cname\" color=\"$color\"];\n")
		}
		sb.append("\n")

		sb.append("\t// Edges\n")
		edges.forEach {
			val va = vertexNames[it.a]?.toString() ?: "error"
			val vb = vertexNames[it.b]?.toString() ?: "error"
			val cname = componentNames[it.a.component]
			val color = if(cname != null) {
				"/spectral11/${cname % 11 + 1}"
			} else {
				"magenta"
			}
			sb.append("\tv$va -- v$vb [color = \"$color\"];\n")
		}
		sb.append("}\n")
		return sb.toString()
	}
}
