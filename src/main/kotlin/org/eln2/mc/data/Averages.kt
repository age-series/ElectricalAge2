package org.eln2.mc.data

import net.minecraft.core.Vec3i
import org.eln2.mc.mathematics.Vector3d

class AveragingList(private val sampleCount: Int) {
    init {
        if (sampleCount <= 0) {
            error("Invalid sample count $sampleCount")
        }
    }

    private val samples: ArrayList<Double> = ArrayList()

    fun addSample(value: Double) {
        samples.add(value)

        while (samples.size > sampleCount) {
            samples.removeAt(0)
        }
    }

    fun calculate(): Double {
        return samples.sum() / samples.size
    }
}

class Average {
    var count = 0
    var value = 0.0

    fun add(x: Double) {
        value += (x - value) / (count + 1)
        ++count
    }

    fun add(x: Int) {
        add(x.toDouble())
    }

    fun reset() {
        count = 0
        value = 0.0
    }

    companion object {
        fun combine(a: Average, b: Average): Average {
            val count = a.count + b.count
            val kA = a.count / count
            val kB = b.count / count

            val result = Average()

            result.count = count
            result.value = a.value * kA + b.value * kB

            return result
        }
    }
}

class Average3d {
    var count = 0
    var averageX = 0.0
    var averageY = 0.0
    var averageZ = 0.0

    var average: Vector3d
        get() = Vector3d(averageX, averageY, averageZ)
        set(value) {
            averageX = value.x
            averageY = value.y
            averageZ = value.z
        }

    fun add(x: Double, y: Double, z: Double) {
        averageX += (x - averageX) / (count + 1)
        averageY += (y - averageY) / (count + 1)
        averageZ += (z - averageZ) / (count + 1)
        ++count
    }

    fun add(x: Int, y: Int, z: Int) {
        add(x.toDouble(), y.toDouble(), z.toDouble())
    }

    fun add(value: Vector3d) {
        add(value.x, value.y, value.z)
    }

    fun add(value: Vec3i) {
        add(value.x, value.y, value.z)
    }

    fun reset() {
        count = 0
        averageX = 0.0
        averageY = 0.0
        averageZ = 0.0
    }

    companion object {
        fun combine(a: Average3d, b: Average3d): Average3d {
            val count = a.count + b.count
            val kA = a.count / count
            val kB = b.count / count

            val result = Average3d()

            result.count = count
            result.averageX = a.averageX * kA + b.averageX * kB
            result.averageY = a.averageY * kA + b.averageY * kB
            result.averageZ = a.averageZ * kA + b.averageZ * kB

            return result
        }
    }
}
