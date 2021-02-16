package org.eln2.data

import org.eln2.debug.DEBUG
import org.eln2.debug.dprintln
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DebugListener: ComponentListener, VertexListener, EdgeListener {
	override fun componentRealized(c: ConnectedComponent) {
		dprintln("component realized: $c")
	}

	override fun componentUnrealized(c: ConnectedComponent) {
		dprintln("component unrealized: $c")
	}

    override fun realizedComponentModificationStarted(c: ConnectedComponent) {
        dprintln("realized component modification started: $c")
    }

    override fun realizedComponentModificationFinished(c: ConnectedComponent) {
        dprintln("realized component modification finished: $c")
    }

	override fun vertexAdded(v: Vertex) {
		dprintln("vertex added: $v")
	}

	override fun vertexRemoved(v: Vertex) {
		dprintln("vertex removed: $v")
	}

	override fun edgeAdded(e: Edge) {
		dprintln("edge added: $e")
	}

	override fun edgeRemoved(e: Edge) {
		dprintln("edge removed: $e")
	}

	companion object {
		val instance = DebugListener()
	}
}

internal class ComponentGraphTest {
	fun makeInstance(): ComponentGraph = ComponentGraph().apply { addListener(DebugListener.instance) }
	
	fun debugDump(cg: ComponentGraph) {
		dprintln("GRAPH $cg:")
		dprintln("  vertices (${cg.vertices.size}):")
		cg.vertices.forEach {
			dprintln("    $it in ${it.component} with ${it.component.vertices}")
			dprintln("      incidence ${it.incident} degree ${it.incident.size}")
		}
		dprintln("  edges (${cg.edges.size}):")
		cg.edges.forEach {
			dprintln("    $it between (${it.a}, ${it.b})")
		}
		dprintln("  components (${cg.components.size}):")
		cg.components.forEach {
			dprintln("    $it has ${it.vertices.size} vertices:")
			it.vertices.forEach { v->
				dprintln("      $v (component ${v.component})")
			}
		}
	}
    
    fun fullConsistencyCheck(cg: ComponentGraph) {
        try {
            fullConsistencyCheckImpl(cg)
        } catch(e: Throwable) {
            DEBUG = true
            System.err.println("The following graph failed consistency checks:")
            debugDump(cg)
            throw e
        }
    }
	
	fun fullConsistencyCheckImpl(cg: ComponentGraph) {
		debugDump(cg)

		val seenEdges: MutableSet<Edge> = mutableSetOf()
		val seenComponents: MutableSet<ConnectedComponent> = mutableSetOf()
		val actualEdgeMap: MutableMultiMap<Vertex, Vertex> = mutableMultiMapOf()
		cg.vertices.forEach {
			assert(it.component in cg.components) {"component ${it.component} of vertex $it not in graph collection ${cg.components}"}
			assert(it in it.component.vertices) {"vertex $it not in its own component (${it.component})'s vertices ${it.component.vertices}"}
			it.incident.forEach { (other, edge) ->
				assert(it in edge) {"vertex $it not in its own edge $edge to $other"}
				assert(other in edge) {"other vertex $other not in $edge found on vertex $it"}
				assert(edge in cg.edges) {"edge $edge from vertex $it to vertex $other not in graph collection ${cg.edges}"}
				seenEdges.add(edge)
				actualEdgeMap[it] = other
			}
			seenComponents.add(it.component)
		}
		assert((cg.components - seenComponents).isEmpty()) {"component graph collection has some unregistered components\ngraph says ${cg.components}\ntest saw $seenComponents\ndelta ${cg.components - seenComponents}"}
		assert((cg.edges - seenEdges).isEmpty()) {"edge graph collection has some unregistered edges\ngraph says ${cg.edges}\ntest saw $seenEdges\ndelta ${cg.edges - seenEdges}"}
		actualEdgeMap.entries.forEach { (a, b) ->
			assert(a in b.incident) {"seen edge ($a, $b) has $a not in $b's incidence map ${b.incident}"}
			assert(b in a.incident) {"seen edge ($a, $b) has $b not in $a's incidence map ${a.incident}"}
			assert(a in b.incident[a]!!) {"seen edge ($a, $b) has $a not in $b's edge ${b.incident[a]}"}
			assert(b in a.incident[b]!!) {"seen edge ($a, $b) has $b not in $a's edge ${a.incident[b]}"}
			assertEquals(a.incident[b], b.incident[a]) {"seen edge ($a, $b) has mismatched instances\na has ${a.incident[b]}\nb has ${b.incident[a]}"}
		}

		val seenVertices: MutableSet<Vertex> = mutableSetOf()
		cg.components.forEach {
			assert(!it.vertices.isEmpty()) {"component $it is empty"}
			it.vertices.forEach { v ->
				assert(v in cg.vertices) {"component $it has vertex $v not in graph collection ${cg.vertices}"}
				assertEquals(v.component, it) {"component $it has vertex $v with different component ${v.component}"}
				seenVertices.add(v)
			}
		}
		assert((cg.vertices - seenVertices).isEmpty()) {"vertex graph collection has some unregistered vertices\ngraph says ${cg.vertices}\ntest saw $seenVertices\ndelta ${cg.vertices - seenVertices}"}

		cg.edges.forEach {
			it.forEach { v ->
				assert(v in it) {"edge $it has vertex $v not in edge"}
				assert(v in cg.vertices) {"edge $it has vertex $v not in graph collection ${cg.vertices}"}
				assert(it in v.incident.values) {"edge $it has vertex $v which does not own edge in incidence map ${v.incident}"}
			}
		}
	}

	@Test
	fun empty() {
		val cg = makeInstance()

		fullConsistencyCheck(cg)

		assert(cg.vertices.isEmpty())
		assert(cg.components.isEmpty())
		assert(cg.edges.isEmpty())
	}

	@Test
	fun singleVertex() {
		val cg = makeInstance()

		cg.mutate {
			newVertex()
		}

		fullConsistencyCheck(cg)

		assertEquals(cg.vertices.size, 1)
		assertEquals(cg.components.size, 1)
		assert(cg.edges.isEmpty())

		val v = cg.vertices.first()
		assertEquals(v.component, cg.components.first())
		assertEquals(v.component.vertices.size, 1)
		assertEquals(v.component.vertices.first(), v)
	}
	
	@Test
	fun completeGraphs() {
		(2 .. 10).forEach { cardinality ->
			dprintln("===== complete: $cardinality =====")

			val cg = makeInstance()

			val vertices: MutableSet<Vertex> = mutableSetOf()
			cg.mutate {
				(0 until cardinality).forEach {
					val vertex = newVertex()
					vertices.forEach { other -> connect(vertex, other) }
					vertices.add(vertex)
				}
			}

			fullConsistencyCheck(cg)

			assertEquals(vertices, cg.vertices)
			assertEquals(cg.vertices.size, cardinality)
			assertEquals(cg.components.size, 1)
			assertEquals(cg.edges.size, cardinality * (cardinality - 1) / 2)
		}
	}

	@Test
	fun lineGraphs() {
		(2 .. 10).forEach { cardinality ->
			dprintln("===== line: $cardinality =====")
			
			val cg = makeInstance()

			val vertices: MutableSet<Vertex> = mutableSetOf()
			cg.mutate {
				var lastVertex: Vertex? = null
				(0 until cardinality).forEach {
					val vertex = newVertex()
					if(lastVertex != null) connect(vertex, lastVertex!!)
					lastVertex = vertex
					vertices.add(vertex)
				}
			}

			fullConsistencyCheck(cg)

			assertEquals(vertices, cg.vertices)
			assertEquals(cg.vertices.size, cardinality)
			assertEquals(cg.components.size, 1)
			assertEquals(cg.edges.size, cardinality - 1)
		}
	}
	
	@Test
	fun cycles() {
		(3 .. 10).forEach { cardinality ->
			dprintln("===== cycle: $cardinality =====")
			
			val cg = makeInstance()
			
			val vertices: MutableSet<Vertex> = mutableSetOf()
			cg.mutate {
				var firstVertex: Vertex? = null
				var lastVertex: Vertex? = null
				(0 until cardinality).forEach {
					val vertex = newVertex()
					if(lastVertex != null) connect(vertex, lastVertex!!)
					if(firstVertex == null) firstVertex = vertex
					if(it == cardinality - 1) connect(vertex, firstVertex!!)
					lastVertex = vertex
					vertices.add(vertex)
				}
			}

			fullConsistencyCheck(cg)

			assertEquals(vertices, cg.vertices)
			assertEquals(cg.vertices.size, cardinality)
			assertEquals(cg.components.size, 1)
			assertEquals(cg.edges.size, cardinality)
		}
	}
    
    @Test
    fun incrementalBilinearGraph() {
        val cg = makeInstance()

        val (a, b) = cg.mutate {
            Pair(newVertex(), newVertex())
        }
        val (aSet, bSet) = Pair(mutableSetOf(a), mutableSetOf(b))

        fullConsistencyCheck(cg)
        assertEquals(cg.vertices, aSet + bSet)
        assertEquals(cg.vertices.size, 2)
        assertEquals(cg.components.size, 2)
        assertEquals(cg.edges.size, 0)
        
        var (nextA, nextB) = Pair(a, b)
        
        (1 .. 10).forEach {
            val (aNew, bNew) = cg.mutate {
                val (an, bn) = Pair(newVertex(), newVertex())
                connect(an, nextA)
                connect(bn, nextB)
                Pair(an, bn)
            }
            
            nextA = aNew
            nextB = bNew
            aSet.add(aNew)
            bSet.add(bNew)

            fullConsistencyCheck(cg)
            assertEquals(cg.vertices, aSet + bSet)
            assertEquals(cg.components.size, 2)
            assertEquals(cg.edges.size, 2 * it)
            assertEquals(a.component.vertices, aSet)
            assertEquals(b.component.vertices, bSet)
        }
        
        aSet.forEach { selectedA ->
            bSet.forEach { selectedB ->
                cg.mutate {
                    connect(selectedA, selectedB)
                }
                fullConsistencyCheck(cg)
                assertEquals(cg.components.size, 1)
                cg.mutate {
                    disconnect(selectedA, selectedB)
                }
                fullConsistencyCheck(cg)
                assertEquals(cg.components.size, 2)
            }
        }
    }
    
    @Test
    fun edgelessGraphs() {
        val cg = makeInstance()
        
        val vertices: MutableSet<Vertex> = mutableSetOf()

        (0 .. 10).forEach {
            cg.mutate {
                vertices.add(newVertex())
            }

            fullConsistencyCheck(cg)
            assertEquals(cg.vertices, vertices)
            assertEquals(cg.components.size, vertices.size)
        }
    }
    
    @Test
    fun incrementalComponentModification() {
        val cg = makeInstance()

        var updateCounter = 0

        cg.addListener(object: ComponentListener {
            override fun componentRealized(c: ConnectedComponent) {}
            override fun componentUnrealized(c: ConnectedComponent) {}
            override fun realizedComponentModificationStarted(c: ConnectedComponent) {
                updateCounter += 1
            }
            override fun realizedComponentModificationFinished(c: ConnectedComponent) {
                updateCounter += 1
            }
        })
        
        val vertex = cg.mutate { newVertex() }

        (1..10).forEach {
            updateCounter = 0
            cg.mutate { 
                val v = newVertex()
                connect(v, vertex)
                setRoot(vertex)
            }
            assertEquals(updateCounter, 2)
        }
    }
}
