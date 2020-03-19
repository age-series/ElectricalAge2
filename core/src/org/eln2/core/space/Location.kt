package org.eln2.core.space

import kotlin.math.abs

data class Vec(val x: Int, val y: Int, val z: Int) {
    operator fun unaryMinus() = Vec(-x, -y, -z)
    operator fun plus(other: Vec) = Vec(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec) = this + (-other)
    operator fun times(other: Int) = Vec(x * other, y * other, z * other)

    val isZero: Boolean get() = (x == 0) && (y == 0) && (z == 0)
    
    fun l1norm(v: Vec): Int = abs(v.x - x) + abs(v.y - y) + abs(v.z - z)

    companion object {
        val ZERO = Vec(0, 0, 0)
        val XU = Vec(1, 0, 0)
        val YU = Vec(0, 1, 0)
        val ZU = Vec(0, 0, 1)
    }
}

enum class PlanarDir(val int: Int) {
    Up(0), Right(1), Down(2), Left(3);

    companion object {
        fun fromInt(i: Int) = when(i) {
            0 -> Up
            1 -> Right
            2 -> Down
            3 -> Left
            else -> error("Not a PlanarDir: $i")
        }
    }

    val rotated: PlanarDir get() = fromInt((int + 1) % 4)
    val inverted: PlanarDir get() = fromInt((int + 2) % 4)
    val rotated_left: PlanarDir get() = fromInt((int + 3) % 4)
}

enum class Axis(val int: Int) {
    X(0), Y(1), Z(2);

    companion object {
        fun fromInt(i: Int) = when(i) {
            0 -> X
            1 -> Y
            2 -> Z
            else -> null
        }

        // This is allowed to return nonsensical results along perfect diagonals.
        fun fromVecMajor(v: Vec): Axis {
            var (x, y, z) = v
            x = abs(x); y = abs(y); z = abs(z)
            val max = arrayOf(x, y, z).max()
            return when(max){
                x -> X
                y -> Y
                else -> Z
            }
        }

        /* Microoptimization: avoid the overhead from constructing these repeatedly */
        val X_VEC = Vec.XU
        val Y_VEC = Vec.YU
        val Z_VEC = Vec.ZU
    }
    
    val vec: Vec get() = when(this) {
        X -> X_VEC
        Y -> Y_VEC
        Z -> Z_VEC
    }

    fun cross(other: Axis): Axis? = when(this) {
        X -> when(other) {
            X -> null
            Y -> Z
            Z -> Y
        }
        Y -> when(other) {
            X -> Z
            Y -> null
            Z -> X
        }
        Z -> when(other) {
            X -> Y
            Y -> X
            Z -> null
        }
    }
}

enum class PlanarFace(val int: Int) {
    XP(0), YP(1), ZP(2), XN(3), YN(4), ZN(5);

    companion object {
        fun fromInt(i: Int) = when(i) {
            0 -> XP
            1 -> YP
            2 -> ZP
            3 -> XN
            4 -> YN
            5 -> ZN
            else -> error("Invalid PlanarFace: $i")
        }

        fun fromAxis(a: Axis, n: Boolean): PlanarFace = fromInt(a.int + if(n) 3 else 0)
        
        fun fromVec(v: Vec): PlanarFace {
            val axis = Axis.fromVecMajor(v)
            return fromAxis(axis, when(axis) {
                Axis.X -> v.x < 0
                Axis.Y -> v.y < 0
                Axis.Z -> v.z < 0
            })
        }
        
        fun fromNormal(v: Vec): PlanarFace = fromVec(v).inverse

        /* See the microopt in Axis above */
        val XP_VEC = Axis.X_VEC
        val YP_VEC = Axis.Y_VEC
        val ZP_VEC = Axis.Z_VEC
        val XN_VEC = -XP_VEC
        val YN_VEC = -YP_VEC
        val ZN_VEC = -ZP_VEC

        val ADJACENCIES = arrayOf(
                arrayOf(YP, ZP, YN, ZN),
                arrayOf(XP, ZP, XN, ZN),
                arrayOf(XP, YP, XN, YN)
        )
    }
    
    val neg: Boolean get() = int > 2
    val axis: Axis get() = when(this) {
        XP, XN -> Axis.X
        YP, YN -> Axis.Y
        ZP, ZN -> Axis.Z
    }
    val inverse: PlanarFace get() = when(this) {
        XP -> XN
        XN -> XP
        YP -> YN
        YN -> YP
        ZP -> ZN
        ZN -> ZP
    }

    val vec: Vec = if(neg) when(axis) {
        Axis.X -> PlanarFace.XN_VEC
        Axis.Y -> PlanarFace.YN_VEC
        Axis.Z -> PlanarFace.ZN_VEC
    } else axis.vec
    
    val normal: Vec = if(neg) axis.vec else when(axis) {
        Axis.X -> PlanarFace.XN_VEC
        Axis.Y -> PlanarFace.YN_VEC
        Axis.Z -> PlanarFace.ZN_VEC
    }
    
    val adjacencies: Array<PlanarFace> = PlanarFace.ADJACENCIES[int % 3]
}

interface Locator {
    val vec: Vec
    fun neighbors(): List<Locator>
    /* When overriding this, put more specific tests in more specific locators, as a rule */
    fun canConnect(other: Locator): Boolean = true
}

/* Support for "simple nodes" that take an entire block. */
data class BlockPos(override val vec: Vec): Locator {
    companion object {
        val CONNECTIVITY_DELTAS = arrayListOf(
                // Cardinal directions on the horizontal plane (Y-normal)
                Vec(1, 0, 0), Vec(-1, 0, 0), Vec(0, 0, 1), Vec(0, 0, -1),
                // Up a block
                Vec(1, 1, 0), Vec(-1, 1, 0), Vec(0, 1, 1), Vec(0, 1, -1),
                // Down a block
                Vec(1, -1, 0), Vec(-1, -1, 0), Vec(0, -1, 1), Vec(0, -1, -1)
        )
    }

    override fun neighbors(): List<Locator> = CONNECTIVITY_DELTAS.map { translate(it) }

    fun translate(v: Vec) = BlockPos(vec + v)
}

/* Support for surface-mounted nodes, up to six per block. */
data class SurfacePos(override val vec: Vec, val face: PlanarFace): Locator {
   companion object {
       val CONNECTIVITY_DELTAS = arrayListOf(
               Vec(0, 0, 0),  // SurfacePos Locators can connect within the block
               // On the same plane:
               Vec(1, 0, 0), Vec(-1, 0, 0), Vec(0, 0, 1), Vec(0, 0, -1),
               // One unit down (anti-normal) in cardinal directions ("wrapping around")
               Vec(1, -1, 0), Vec(-1, -1, 0), Vec(0, -1, 1), Vec(0, -1, -1)
       )
   } 

    // Preserve chirality: invert _two_ components, or none.
    // There's very little thought in the permutation otherwise, however; if those need to be changed, they can be.
    fun toReference(v: Vec): Vec = when(face) {
        PlanarFace.XN -> Vec(v.y, v.x, v.z)
        PlanarFace.XP -> Vec(-v.y, -v.x, v.z)
        PlanarFace.YP -> Vec(-v.x, -v.y, v.z)
        PlanarFace.YN -> v
        PlanarFace.ZN -> Vec(v.x, v.z, v.y)
        PlanarFace.ZP -> Vec(-v.x, v.z, -v.y)
    }
    
    fun translate(v: Vec) = SurfacePos(vec + toReference(v), face)

    override fun neighbors(): List<Locator> = CONNECTIVITY_DELTAS.map { translate(it) }

    override fun canConnect(other: Locator): Boolean = when(other) {
        is SurfacePos -> when(other.vec.l1norm(vec)) {
            0 -> other.face != face && other.face != face.inverse
            1 -> face == other.face
            2 -> {
                val delta = other.vec - vec
                val other_norm = delta - face.normal
                other.face == PlanarFace.fromNormal(other_norm)
            }
            else -> error("Illegal norm")
        }
        is BlockPos -> true
        else -> true
    }
}