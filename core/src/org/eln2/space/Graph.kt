package org.eln2.space

import org.eln2.debug.dprintln
import java.util.*

open class Object(val locator: Locator) {
    var concom: ConnectedComponent? = null
    // Only safe to use after this has been added to a space
    val space: Space get() = concom!!.space

    fun canConnectTo(other: Object, isA: Boolean): Boolean = true
    // Only safe to call after this has been added to a space
    fun connectTo(other: Object, isA: Boolean) {
        if(isA) {
            dprintln("O.cT: pre $concom onto ${other.concom}")
            concom!!.merge(other.concom!!)  // NB: Mutates either this.concom or other.concom
            dprintln("O.cT: post $concom onto ${other.concom}")
            // assert(concom == other.concom)
        }
    }
}

// TODO: Refactor this using the "Union-Find" Set--needs some care to keep track of the `objects` field, though.
class ConnectedComponent(val space: Space) {
    var objects: MutableSet<Object> = mutableSetOf()

    fun add(obj: Object) {
        obj.concom = this
        objects.add(obj)
    }
    
    fun add(vararg obj: Object) {
        obj.forEach { add(it) }
    }
    
    fun merge(other: ConnectedComponent) {
        val (bigger, smaller) = if(objects.size < other.objects.size) Pair(other, this) else Pair(this, other)
        smaller.objects.forEach { bigger.add(it) }
        smaller.objects.clear()
    }
}

open class Space {
    val components: WeakHashMap<ConnectedComponent, Unit> = WeakHashMap()
    val objectLocators: MutableMap<Locator, Object> = mutableMapOf()
    val locatorVectors: MutableMap<Vec, MutableSet<Locator>> = mutableMapOf()  // XXX A Multimap would be better

    fun add(obj: Object) {
        dprintln("add: $obj at ${obj.locator}")
        objectLocators[obj.locator] = obj
        locatorVectors.getOrPut(obj.locator.vec, { mutableSetOf() }).add(obj.locator)
        val concom = ConnectedComponent(this)
        concom.add(obj)
        components[concom] = Unit
        merge(obj)
    }
    
    fun add(vararg obj: Object) {
        obj.forEach { add(it) }
    }
    
    fun remove(obj: Object) {
        dprintln("remove: $obj at ${obj.locator}")
        val objects = obj.concom!!.objects.toSet()
        obj.concom!!.objects.clear()  // This set is about to be emptied anyway, no need to hold these references
        
        objects.forEach {
            // Drop any previous cc references
            it.concom = null
            // Also remove from the locators so merge() doesn't observe a null concom
            objectLocators.remove(it.locator)
            locatorVectors[it.locator.vec]!!.remove(it.locator)  // XXX leaks the possibly-empty set
        }

        // Rescan and merge new components
        objects.forEach {
            if(it != obj) add(it)
        }
    }
    
    protected fun merge(obj: Object) {
        obj.locator.neighbors().forEach {
            locatorVectors[it.vec]?.forEach {
                val objb = objectLocators[it]
                dprintln("merge: consider loc $it obj $objb")
                if (objb != null && obj.locator.canConnect(it) && it.canConnect(obj.locator) && obj.canConnectTo(objb, true) && objb.canConnectTo(obj, false)) {
                    dprintln("merge: merging")
                    obj.connectTo(objb, true)
                    objb.connectTo(obj, false)
                } else if (objb != null) {
                    if (!obj.locator.canConnect(it)) dprintln("merge: failed: this locator (${obj.locator}) can't connect to other locator ($it)")
                    if (!it.canConnect(obj.locator)) dprintln("merge: failed: other locator ($it) can't connect to this locator (${obj.locator})")
                    if (!obj.canConnectTo(objb, true)) dprintln("merge: failed: this object ($obj @ ${obj.locator}) can't connect to other object ($objb @ $it)")
                    if (!objb.canConnectTo(obj, false)) dprintln("merge: failed: other object ($objb @ $it) can't connect to this object ($obj @ ${obj.locator})")
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            main_1()
            println("===")
            main_2()
            println("===")
            main_3()
        }
        
        private fun describe(space: Space) {
            println("components:")
            for((loc, obj) in space.objectLocators) {
                println("- $obj @ $loc ( = ${obj.locator}) in cc ${obj.concom}")
            }
            println("ccs:")
            for((cc, _) in space.components) {
                println("- cc $cc:")
                for(obj in cc.objects) {
                    println("  - $obj @ ${obj.locator}")
                }
            }
        }
        
        fun main_1() {
            println("main_1: Basic BlockPos Tests")

            val space = Space()

            println("--- addition ---")

            val line = ArrayList<Object>()
            for (i in 0 until 10) {
                val o = Object(BlockPos(Vec(i, 0, 0)))
                line.add(o)
                space.add(o)
            }

            println("line:")
            for(o in line) {
                println("obj $o loc ${o.locator} cc ${o.concom}")
            }

            describe(space)

            println()
            println("--- removal ---")

            space.remove(line[5])

            println("line:")
            for(o in line) {
                println("obj $o loc ${o.locator} cc ${o.concom}")
            }

            describe(space)

            println()
            println("--- readdition ---")

            space.add(line[5])

            println("line:")
            for(o in line) {
                println("obj $o loc ${o.locator} cc ${o.concom}")
            }

            describe(space)
        }
        
        fun main_2() {
            println("main_2: SurfacePos tests")

            println("--- adding a line ---")

            val space = Space()
            val line = ArrayList<Object>()
            
            for(i in 0 until 5) {
                val o = Object(SurfacePos(Vec(i, 0, 0), PlanarFace.YN))
                line.add(o)
                space.add(o)
            }

            describe(space)

            println()
            println("--- adding a side ---")
            
            val side = Object(SurfacePos(Vec(4, 0, 0), PlanarFace.XP))
            space.add(side)

            describe(space)

            println()
            println("--- adding wrapping sides ---")

            val wrsidep = Object(SurfacePos(Vec(1, -1, 0), PlanarFace.XP))
            val wrsiden = Object(SurfacePos(Vec(-1, -1, 0), PlanarFace.XN))
            space.add(wrsidep, wrsidep)

            describe(space)

            println()
            println("--- adding inverse face ---")
            
            val inv = Object(SurfacePos(Vec(3, 0, 0), PlanarFace.YP))
            space.add(inv)

            describe(space)
        }
        
        fun main_3() {
            println("main_3: Interoperability tests")

            val space = Space()
            val so = Object(SurfacePos(Vec(5, 0, 0), PlanarFace.YN))
            space.add(so)

            println("--- adding a blockpos on the adjacent plane ---")
            
            val bpa = Object(BlockPos(Vec(5, 0, 1)))
            space.add(bpa)

            describe(space)

            println()
            println("--- adding a wrapped blockpos ---")

            val bpw = Object(BlockPos(Vec(6, 1, 0)))
            space.add(bpw)

            describe(space)
        }
    }
}