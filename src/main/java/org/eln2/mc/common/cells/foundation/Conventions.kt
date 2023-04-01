package org.eln2.mc.common.cells.foundation

import org.eln2.mc.common.space.RelativeRotationDirection

/**
 * These are some convention constants.
 * */
object Conventions {
    /**
     * Describes the pin exported to other Electrical Objects.
     * */
    const val EXTERNAL_PIN = 1
    const val POSITIVE_PIN = EXTERNAL_PIN

    /**
     * Describes the pin used internally by Electrical Objects.
     * */
    const val INTERNAL_PIN = 0
    const val NEGATIVE_PIN = INTERNAL_PIN
}
