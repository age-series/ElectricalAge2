package org.eln2.space

import java.util.ArrayList
import java.util.WeakHashMap
import org.eln2.data.MutableMultiMap
import org.eln2.data.mutableMultiMapOf
import org.eln2.debug.dprintln

open class Object(val locator: Locator) {
    var concom: ConnectedComponent? = null

    // Only safe to use after this has been added to a space
    val space: Space get() = concom!!.space

    fun canConnectTo(other: Object, isA: Boolean): Boolean = true

    // Only safe to call after this has been added to a space
    fun connectTo(other: Object, isA: Boolean) {
        if (isA) {
            dprintln("O.cT: pre $concom onto ${other.concom}")
            concom!!.merge(other.concom!!) // NB: Mutates either this.concom or other.concom
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
        val (bigger, smaller) = if (objects.size < other.objects.size) Pair(other, this) else Pair(this, other)
        smaller.objects.forEach { bigger.add(it) }
        smaller.objects.clear()
    }
}

open class Space {
    val components: WeakHashMap<ConnectedComponent, Unit> = WeakHashMap()
    val objectLocators: MutableMap<Locator, Object> = mutableMapOf()
    val locatorVectors: MutableMultiMap<Vec3i, Locator> = mutableMultiMapOf()

    fun add(obj: Object) {
        dprintln("add: $obj at ${obj.locator}")
        objectLocators[obj.locator] = obj
        locatorVectors[obj.locator.vec3i] = obj.locator
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
        obj.concom!!.objects.clear() // This set is about to be emptied anyway, no need to hold these references

        objects.forEach {
            // Drop any previous cc references
            it.concom = null
            // Also remove from the locators so merge() doesn't observe a null concom
            objectLocators.remove(it.locator)
            locatorVectors.remove(it.locator.vec3i, it.locator)
        }

        // Rescan and merge new components
        objects.forEach {
            if (it != obj) add(it)
        }
    }

    private fun canConnect(obj: Object, objb: Object, locator: Locator) =
        obj.locator.canConnect(locator) && locator.canConnect(obj.locator) && obj.canConnectTo(
            objb,
            true
        ) && objb.canConnectTo(obj, false)

    protected fun merge(obj: Object) {
        obj.locator.neighbors().forEach {
            locatorVectors[it.vec3i].forEach { loc ->
                val objb = objectLocators[loc]
                dprintln("merge: consider loc $loc obj $objb")
                if (objb != null && canConnect(obj, objb, loc)) {
                    dprintln("merge: merging")
                    obj.connectTo(objb, true)
                    objb.connectTo(obj, false)
                } else if (objb != null) {
                    if (!obj.locator.canConnect(loc)) dprintln("merge: failed: this locator (${obj.locator}) can't connect to other locator ($loc)")
                    if (!loc.canConnect(obj.locator)) dprintln("merge: failed: other locator ($loc) can't connect to this locator (${obj.locator})")
                    if (!obj.canConnectTo(
                            objb,
                            true
                        )
                    ) dprintln("merge: failed: this object ($obj @ ${obj.locator}) can't connect to other object ($objb @ $loc)")
                    if (!objb.canConnectTo(
                            obj,
                            false
                        )
                    ) dprintln("merge: failed: other object ($objb @ $loc) can't connect to this object ($obj @ ${obj.locator})")
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
            for ((loc, obj) in space.objectLocators) {
                println("- $obj @ $loc ( = ${obj.locator}) in cc ${obj.concom}")
            }
            println("ccs:")
            for ((cc, _) in space.components) {
                println("- cc $cc:")
                for (obj in cc.objects) {
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
                val o = Object(BlockPos(Vec3i(i, 0, 0)))
                line.add(o)
                space.add(o)
            }

            println("line:")
            for (o in line) {
                println("obj $o loc ${o.locator} cc ${o.concom}")
            }

            describe(space)

            println()
            println("--- removal ---")

            space.remove(line[5])

            println("line:")
            for (o in line) {
                println("obj $o loc ${o.locator} cc ${o.concom}")
            }

            describe(space)

            println()
            println("--- readdition ---")

            space.add(line[5])

            println("line:")
            for (o in line) {
                println("obj $o loc ${o.locator} cc ${o.concom}")
            }

            describe(space)
        }

        fun main_2() {
            println("main_2: SurfacePos tests")

            println("--- adding a line ---")

            val space = Space()
            val line = ArrayList<Object>()

            for (i in 0 until 5) {
                val o = Object(SurfacePos(Vec3i(i, 0, 0), PlanarFace.NegY))
                line.add(o)
                space.add(o)
            }

            describe(space)

            println()
            println("--- adding a side ---")

            val side = Object(SurfacePos(Vec3i(4, 0, 0), PlanarFace.PosX))
            space.add(side)

            describe(space)

            println()
            println("--- adding wrapping sides ---")

            val wrsidep = Object(SurfacePos(Vec3i(1, -1, 0), PlanarFace.PosX))
            val wrsiden = Object(SurfacePos(Vec3i(-1, -1, 0), PlanarFace.NegX))
            space.add(wrsidep, wrsidep)

            describe(space)

            println()
            println("--- adding inverse face ---")

            val inv = Object(SurfacePos(Vec3i(3, 0, 0), PlanarFace.PosY))
            space.add(inv)

            describe(space)
        }

        fun main_3() {
            println("main_3: Interoperability tests")

            val space = Space()
            val so = Object(SurfacePos(Vec3i(5, 0, 0), PlanarFace.NegY))
            space.add(so)

            println("--- adding a blockpos on the adjacent plane ---")

            val bpa = Object(BlockPos(Vec3i(5, 0, 1)))
            space.add(bpa)

            describe(space)

            println()
            println("--- adding a wrapped blockpos ---")

            val bpw = Object(BlockPos(Vec3i(6, 1, 0)))
            space.add(bpw)

            describe(space)
        }
    }
}
