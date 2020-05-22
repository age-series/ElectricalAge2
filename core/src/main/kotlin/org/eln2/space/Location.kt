package org.eln2.space

import kotlin.math.abs

/**
 * A vector in two-dimensional space.
 */
data class Vec2i(val x: Int, val y: Int) {
	/**
	 * Gets the opposite vector.
	 */
	operator fun unaryMinus() = Vec2i(-x, -y)

	/**
	 * Gets the sum of this vector and another vector.
	 */
	operator fun plus(other: Vec2i) = Vec2i(x + other.x, y + other.y)

	/**
	 * Gets the sum of this vector and the opposite of another vector.
	 */
	operator fun minus(other: Vec2i) = this + (-other)

	/**
	 * Gets the scalar multiple of this vector.
	 */
	operator fun times(scalar: Int) = Vec2i(x * scalar, y * scalar)

	val isZero: Boolean get() = this == ZERO

	companion object {
		val ZERO = Vec2i(0, 0)
		val XU = Vec2i(1, 0)
		val YU = Vec2i(0, 1)
	}

}

/**
 * A vector in three-dimensional space.
 */
data class Vec3i(val x: Int, val y: Int, val z: Int) {
	/**
	 * Gets the opposite vector.
	 */
	operator fun unaryMinus() = Vec3i(-x, -y, -z)

	/**
	 * Gets the sum of this vector and another vector.
	 */
	operator fun plus(other: Vec3i) = Vec3i(x + other.x, y + other.y, z + other.z)

	/**
	 * Gets the sum of this vector and the opposite of another vector.
	 */
	operator fun minus(other: Vec3i) = this + (-other)

	/**
	 * Gets the scalar multiple of this vector.
	 */
	operator fun times(other: Int) = Vec3i(x * other, y * other, z * other)

	val isZero: Boolean get() = (x == 0) && (y == 0) && (z == 0)

	/**
	 * Calculates the L1 distance between two vectors.
	 * See https://en.wikipedia.org/wiki/Taxicab_geometry
	 */
	fun l1norm(v: Vec3i): Int = abs(v.x - x) + abs(v.y - y) + abs(v.z - z)

	companion object {
		val ZERO = Vec3i(0, 0, 0)
		val XU = Vec3i(1, 0, 0)
		val YU = Vec3i(0, 1, 0)
		val ZU = Vec3i(0, 0, 1)
	}
}

/**
 * A direction in space.
 */
enum class PlanarDir(val int: Int) {
	Up(0), Right(1), Down(2), Left(3);

	companion object {
		fun fromInt(i: Int) = when (i) {
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

/**
 * A set of unit vectors that represents each direction in three-dimensional space.
 */
enum class Axis(val int: Int) {
	X(0), Y(1), Z(2);

	companion object {
		fun fromInt(i: Int) = when (i) {
			0 -> X
			1 -> Y
			2 -> Z
			else -> null
		}

		// This is allowed to return nonsensical results along perfect diagonals.
		/**
		 * Returns the axis in which a given vector is closest to.
		 */
		fun fromVecMajor(v: Vec3i): Axis {
			var (x, y, z) = v
			x = abs(x); y = abs(y); z = abs(z)
			val max = arrayOf(x, y, z).max()
			return when (max) {
				x -> X
				y -> Y
				else -> Z
			}
		}

		/* Microoptimization: avoid the overhead from constructing these repeatedly */
		val X_VEC = Vec3i.XU
		val Y_VEC = Vec3i.YU
		val Z_VEC = Vec3i.ZU
	}

	val vec3i: Vec3i
		get() = when (this) {
			X -> X_VEC
			Y -> Y_VEC
			Z -> Z_VEC
		}

	/**
	 * Returns the cross product of this axis and another.
	 */
	fun cross(other: Axis): Axis? = when (this) {
		X -> when (other) {
			X -> null
			Y -> Z
			Z -> Y
		}
		Y -> when (other) {
			X -> Z
			Y -> null
			Z -> X
		}
		Z -> when (other) {
			X -> Y
			Y -> X
			Z -> null
		}
	}
}

/**
 * The six orthogonal vectors corresponding to the faces of a cube.
 */
enum class PlanarFace(val int: Int) {
	XP(0), YP(1), ZP(2), XN(3), YN(4), ZN(5);

	companion object {
		/**
		 * Gets the face corresponding to a number.
		 */
		fun fromInt(i: Int) = when (i) {
			0 -> XP
			1 -> YP
			2 -> ZP
			3 -> XN
			4 -> YN
			5 -> ZN
			else -> error("Invalid PlanarFace: $i")
		}

		/**
		 * Gets the plane that the axis is pointing to.
		 */
		fun fromAxis(a: Axis, n: Boolean): PlanarFace = fromInt(a.int + if (n) 3 else 0)

		/**
		 * Gets the plane that the vector is pointing to.
		 */
		fun fromVec(v: Vec3i): PlanarFace {
			val axis = Axis.fromVecMajor(v)
			return fromAxis(axis, when (axis) {
				Axis.X -> v.x < 0
				Axis.Y -> v.y < 0
				Axis.Z -> v.z < 0
			})
		}

		/**
		 * Gets the plane that the normal of the vector is pointing to.
		 */
		fun fromNormal(v: Vec3i): PlanarFace = fromVec(v).inverse

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
	val axis: Axis
		get() = when (this) {
			XP, XN -> Axis.X
			YP, YN -> Axis.Y
			ZP, ZN -> Axis.Z
		}
	val inverse: PlanarFace
		get() = when (this) {
			XP -> XN
			XN -> XP
			YP -> YN
			YN -> YP
			ZP -> ZN
			ZN -> ZP
		}

	/**
	 "Vector": the vec that points out of the block center toward this face.
	 */
	val vec3i: Vec3i
		get() = if (neg) when (axis) {
			Axis.X -> PlanarFace.XN_VEC
			Axis.Y -> PlanarFace.YN_VEC
			Axis.Z -> PlanarFace.ZN_VEC
		} else axis.vec3i

	/**
	 * "Normal": the vec that points, normal to this face, toward the block center.
	 */
	val normal: Vec3i
		get() = if (neg) axis.vec3i else when (axis) {
			Axis.X -> PlanarFace.XN_VEC
			Axis.Y -> PlanarFace.YN_VEC
			Axis.Z -> PlanarFace.ZN_VEC
		}

	val adjacencies: Array<PlanarFace> get() = PlanarFace.ADJACENCIES[int % 3]
}

/**
 * A generic that represents the location of something and its neighbors.
 */
interface Locator {
	val vec3i: Vec3i

	/**
	 * A list of neighbors in which connections are possible with.
	 */
	fun neighbors(): List<Locator>

	/* When overriding this, put more specific tests in more specific locators, as a rule */
	/**
	 * Returns true if it is possible for this node and another node to connect.
	 */
	fun canConnect(other: Locator): Boolean = true
}

/**
 *  Positionally support for "simple nodes" that take an entire block.
 */
data class BlockPos(override val vec3i: Vec3i) : Locator {
	companion object {
		val CONNECTIVITY_DELTAS = arrayListOf(
			// Cardinal directions on the horizontal plane (Y-normal)
			Vec3i(1, 0, 0), Vec3i(-1, 0, 0), Vec3i(0, 0, 1), Vec3i(0, 0, -1),
			// Up a block
			Vec3i(1, 1, 0), Vec3i(-1, 1, 0), Vec3i(0, 1, 1), Vec3i(0, 1, -1),
			// Down a block
			Vec3i(1, -1, 0), Vec3i(-1, -1, 0), Vec3i(0, -1, 1), Vec3i(0, -1, -1)
		)
	}

	override fun neighbors(): List<Locator> = CONNECTIVITY_DELTAS.map { translate(it) }

	/**
	 * Offsets a vector based on the position of this node.
	 */
	fun translate(v: Vec3i) = BlockPos(vec3i + v)
}

/**
 *  Positionally support for surface-mounted nodes, up to six per block.
 */
data class SurfacePos(override val vec3i: Vec3i, val face: PlanarFace) : Locator {
	companion object {
		// On the same plane:
		val PLANAR_DELTAS = arrayListOf(
			Vec3i(1, 0, 0), Vec3i(-1, 0, 0), Vec3i(0, 0, 1), Vec3i(0, 0, -1)
		)

		// On adjacent planes:
		val ADJACENT_DELTAS = arrayListOf(
			// SurfacePos locators can connect to adjacent planes on the same block:
			Vec3i(0, 0, 0),
			// One unit down (anti-normal) in cardinal directions ("wrapping around")
			Vec3i(1, -1, 0), Vec3i(-1, -1, 0), Vec3i(0, -1, 1), Vec3i(0, -1, -1)
		)
	}

	// Preserve chirality: invert _two_ components, or none.
	// There's very little thought in the permutation otherwise, however; if those need to be changed, they can be.
	/**
	 * Orients a vector based on which plane of a cube this node is on.
	 */
	fun toReference(v: Vec3i): Vec3i = when (face) {
		PlanarFace.XN -> Vec3i(v.y, v.x, v.z)
		PlanarFace.XP -> Vec3i(-v.y, -v.x, v.z)
		PlanarFace.YP -> Vec3i(-v.x, -v.y, v.z)
		PlanarFace.YN -> v
		PlanarFace.ZN -> Vec3i(v.x, v.z, v.y)
		PlanarFace.ZP -> Vec3i(-v.x, v.z, -v.y)
	}

	/**
	 * Offsets a vector based on the position of this node.
	 */
	fun translate(v: Vec3i) = SurfacePos(vec3i + toReference(v), face)

	override fun neighbors(): List<Locator> = (PLANAR_DELTAS
		.map { translate(it) }  // Other adjacent blocks on the same plane
		+
		face.adjacencies.map { SurfacePos(vec3i, it) }  // Connections within the same block
		+
		face.adjacencies.map { SurfacePos(vec3i + face.vec3i + it.normal, it) }  // "Wrapping" (L1=2) connections
		)

	override fun canConnect(other: Locator): Boolean = when (other) {
		is SurfacePos -> when (other.vec3i.l1norm(vec3i)) {
			0 -> other.face != face && other.face != face.inverse
			1 -> face == other.face
			2 -> {
				val delta = other.vec3i - vec3i
				val other_norm = delta + face.normal
				println("SP.cC: L1=2: delta $delta other_norm $other_norm face.normal ${face.normal} this.vec $vec3i other.vec ${other.vec3i} other.face ${other.face} PF.fN(on) ${PlanarFace.fromNormal(other_norm)}")
				other.face == PlanarFace.fromNormal(other_norm)
			}
			else -> error("Illegal norm")
		}
		is BlockPos -> true
		else -> true
	}
}
