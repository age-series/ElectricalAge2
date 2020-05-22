package org.eln2.data

import org.eln2.debug.dprintln
import java.lang.StringBuilder
import java.util.*

open class Edge(val a: Vertex, val b: Vertex): Iterable<Vertex> {
	override fun iterator() = arrayOf(a, b).iterator()
}

open class Vertex(val graph: ComponentGraph) {
	var component = graph.constructComponent(this)
	val incident: MutableMap<Vertex, Edge> = mutableMapOf()
}

open class ConnectedComponent(initialVertex: Vertex) {
	val vertices: MutableSet<Vertex> = mutableSetOf(initialVertex)
	
	internal open fun add(v: Vertex) = vertices.add(v)
	internal open fun remove(v: Vertex) = vertices.remove(v)
	internal open fun clear() = vertices.clear()

	internal var realized = false
	internal open fun realize() {}
	internal open fun unrealize() {}
}

interface ComponentListener {
	fun componentRealized(c: ConnectedComponent)
	fun componentUnrealized(c: ConnectedComponent)
}

interface VertexListener {
	fun vertexAdded(v: Vertex)
	fun vertexRemoved(v: Vertex)
}

interface EdgeListener {
	fun edgeAdded(e: Edge)
	fun edgeRemoved(e: Edge)
}

open class ComponentGraph {
	val vertices: MutableSet<Vertex> = mutableSetOf()
	val edges: MutableSet<Edge> = mutableSetOf()
	var components: MutableSet<ConnectedComponent> = mutableSetOf()
	
	val componentListeners: MutableSet<ComponentListener> = mutableSetOf()
	val vertexListeners: MutableSet<VertexListener> = mutableSetOf()
	val edgeListeners: MutableSet<EdgeListener> = mutableSetOf()
	
	fun addListener(listener: Any) {
		if(listener is ComponentListener) componentListeners.add(listener)
		if(listener is VertexListener) vertexListeners.add(listener)
		if(listener is EdgeListener) edgeListeners.add(listener)
	}
	
	fun removeListener(listener: Any) {
		componentListeners.remove(listener)
		vertexListeners.remove(listener)
		edgeListeners.remove(listener)
	}

	internal open fun constructComponent(v: Vertex) = ConnectedComponent(v)
	internal open fun constructVertex() = Vertex(this)
	internal open fun constructEdge(a: Vertex, b: Vertex) = Edge(a, b)
	
	internal fun newVertex() = constructVertex().apply {
		vertices.add(this)
		components.add(component)
		vertexListeners.forEach { it.vertexAdded(this) }
	}
	
	internal fun removeVertex(v: Vertex) {
		vertexListeners.forEach { it.vertexRemoved(v) }
		disconnect(v)
		vertices.remove(v)
	}

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
	
	internal fun disconnect(v: Vertex) {
		v.incident.keys.forEach {
			disconnect(v, it)
		}
		assert(v.incident.isEmpty())
	}

	protected class Visitation(val vertex: Vertex, val phase: Phase, val root: Boolean = false) {
		enum class Phase {
			Down, Up
		}
	}

	fun visitDepthFirst(
		downPhase: (visited: Vertex, depth: Int, isRoot: Boolean, neighbors: MutableSet<Vertex>) -> Unit,
		upPhase: (visited: Vertex, depth: Int) -> Unit,
		start: Vertex? = null
	) {
		val unvisited = vertices.toMutableSet()  // XXX Relying on the implementation detail of a copy here...
		var depth = 0
		val stack = Stack<Visitation>()

		while(!unvisited.isEmpty()) {
			if(stack.isEmpty()) {
				dprintln("CG.vDF: adding a new root")
				stack.push(Visitation(start ?: unvisited.first(), Visitation.Phase.Down, true))
			}

			val visitation = stack.pop()
			val vertex = visitation.vertex
			dprintln("CG.vDF: visiting $vertex phase ${visitation.phase} root ${visitation.root}")

			when(visitation.phase) {
				Visitation.Phase.Down -> {
					unvisited.remove(vertex)
					stack.push(Visitation(vertex, Visitation.Phase.Up))

					val neighbors = vertex.incident.keys
					dprintln("CG.vDF: pre-down neighbors: $neighbors")
					downPhase(vertex, depth, visitation.root, neighbors)
					dprintln("CG.vDF: post-down neighbors: $neighbors")
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
	
	// FIXME: There are several optimizations which can be applied here:
	// - Find bridges (non-bridge edges can be removed without issue)
	// - Minimize component changeover (don't just choose one and clear it)
	internal fun assignComponents() {
		var currentComponent: ConnectedComponent? = null
		val newComponents: MutableSet<ConnectedComponent> = mutableSetOf()

		visitDepthFirst({
			vertex, _, isRoot, _ ->
			dprintln("CG.aC: visiting $vertex root=$isRoot")
			if(isRoot) {
				currentComponent = vertex.component
				newComponents.add(currentComponent!!)
				currentComponent!!.clear()
				currentComponent!!.add(vertex)
			} else {
				vertex.component = currentComponent!!
				currentComponent!!.add(vertex)
			}
		}, {_, _ -> Unit})

		dprintln("CG.aC: old components $components, new components $newComponents")

		// Process removed components which were realized
		(components - newComponents).forEach {
			if(it.realized) {
				componentListeners.forEach { listener -> listener.componentUnrealized(it) }
				it.unrealize()
			}
		}

		components = newComponents
		
		// Realize any added components
		components.forEach {
			if(!it.realized) {
				it.realize()
				componentListeners.forEach { listener -> listener.componentRealized(it) }
			}
		}
	}
	
	var outstandingGuards = 0
		protected set

	inner class MutationGuard internal constructor() {
		init {
			outstandingGuards++
			dprintln("CG.MG.<init>: beginning mutation, nest level $outstandingGuards")
		}
		
		fun connect(a: Vertex, b: Vertex) = this@ComponentGraph.connect(a, b)
		fun disconnect(a: Vertex, b: Vertex) = this@ComponentGraph.disconnect(a, b)
		fun disconnect(v: Vertex) = this@ComponentGraph.disconnect(v)
		fun newVertex() = this@ComponentGraph.newVertex()
		fun removeVertex(v: Vertex) = this@ComponentGraph.removeVertex(v)
		
		fun drop() {
			dprintln("CG.MG.drop: ending mutation, nest level $outstandingGuards")
			if(--outstandingGuards == 0) {
				assignComponents()
			}
		}
	}

	fun<T> mutate(body: MutationGuard.() -> T): T {
		val guard = MutationGuard()
		try {
			return with(guard, body)
		} finally {
			guard.drop()
		}
	}
	
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
