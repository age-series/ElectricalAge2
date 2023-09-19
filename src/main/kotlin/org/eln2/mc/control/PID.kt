package org.eln2.mc.control

import net.minecraft.nbt.CompoundTag

// P.S. this is mutable, so we adjust the instance in order to affect the controller
// do we want immutability?
data class PIDCoefficients(
    var kP: Double = 0.0,
    var kI: Double = 0.0,
    var kD: Double = 0.0,
) {
    fun copy(): PIDCoefficients {
        return PIDCoefficients(kP, kI, kD)
    }

    fun serializeNbt(): CompoundTag {
        val tag = CompoundTag()

        tag.putDouble("kP", kP)
        tag.putDouble("kI", kI)
        tag.putDouble("kD", kD)

        return tag
    }

    fun deserializeNbt(tag: CompoundTag) {
        kP = tag.getDouble("kP")
        kI = tag.getDouble("kI")
        kD = tag.getDouble("kD")
    }
}

// TODO: PIDF (using a plant model)

class PIDController(private val coefficients: PIDCoefficients) {
    private var errorSum = 0.0
    private var lastError = 0.0

    var setPoint = 0.0

    fun computeError(value: Double): Double {
        return setPoint - value
    }

    var minControl = Double.MIN_VALUE
    var maxControl = Double.MAX_VALUE

    /**
     * Runs an iteration of the PID Controller.
     *
     * @param value The current observed value.
     * @return The control signal, that is to be applied to the plant.
     */
    fun update(value: Double, dt: Double): Double {
        val error = computeError(value)

        errorSum += (error + lastError) * 0.5 * dt

        val derivative = (error - lastError) / dt

        lastError = error

        var control = coefficients.kP * error +
            coefficients.kI * errorSum +
            coefficients.kD * derivative

        control = control.coerceIn(minControl, maxControl)

        return control
    }

    fun unwind() {
        errorSum = 0.0
        lastError = 0.0
    }
}

fun pid(kP: Double, kI: Double, kD: Double): PIDController {
    return PIDController(PIDCoefficients(kP, kI, kD))
}
