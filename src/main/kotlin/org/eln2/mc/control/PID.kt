package org.eln2.mc.control

import net.minecraft.nbt.CompoundTag

class PIDController(var kP: Double, var kI: Double, var kD: Double) {
    companion object {
        private const val KP = "kP"
        private const val KI = "kI"
        private const val KD = "kD"
        private const val ERROR_SUM = "errorSum"
        private const val LAST_ERROR = "lastError"
        private const val MIN_CONTROL = "minControl"
        private const val MAX_CONTROL = "maxControl"

        fun fromNbt(tag: CompoundTag) = PIDController(0.0, 0.0, 0.0).also { it.loadFromNbt(tag) }
    }

    private var errorSum = 0.0
    private var lastError = 0.0

    /**
     * Gets or sets the setpoint (desired value).
     * */
    var setPoint = 0.0

    /**
     * Gets or sets the minimum control signal returned by [update]
     * */
    var minControl = Double.MIN_VALUE

    /**
     * Gets or sets the maximum control signal returned by [update]
     * */
    var maxControl = Double.MAX_VALUE

    fun update(value: Double, dt: Double): Double {
        val error = setPoint - value

        errorSum += (error + lastError) * 0.5 * dt

        val derivative = (error - lastError) / dt

        lastError = error

        return (kP * error + kI * errorSum + kD * derivative).coerceIn(minControl, maxControl)
    }

    fun reset() {
        errorSum = 0.0
        lastError = 0.0
    }

    fun saveToNbt() = CompoundTag().also {
        it.putDouble(KP, kP)
        it.putDouble(KI, kI)
        it.putDouble(KD, kD)
        it.putDouble(ERROR_SUM, errorSum)
        it.putDouble(LAST_ERROR, lastError)
        it.putDouble(MIN_CONTROL, minControl)
        it.putDouble(MAX_CONTROL, maxControl)
    }

    fun loadFromNbt(tag: CompoundTag) {
        kP = tag.getDouble(KP)
        kI = tag.getDouble(KI)
        kD = tag.getDouble(KD)
        errorSum = tag.getDouble(ERROR_SUM)
        lastError = tag.getDouble(LAST_ERROR)
        minControl = tag.getDouble(MIN_CONTROL)
        maxControl = tag.getDouble(MAX_CONTROL)
    }
}
