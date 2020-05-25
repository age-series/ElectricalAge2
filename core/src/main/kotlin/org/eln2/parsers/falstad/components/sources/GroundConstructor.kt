package org.eln2.parsers.falstad.components.sources

import org.eln2.parsers.falstad.CCData
import org.eln2.parsers.falstad.IComponentConstructor

/**
 * Ground Constructor
 *
 * Falstad's basic ground pin. Sets a node as being a ground connected node.
 */
class GroundConstructor : IComponentConstructor {
    override fun construct(ccd: CCData) {
        ccd.pins = 1
        ccd.falstad.addGround(
            ccd.falstad.getPin(ccd.pinPositions[0])
        )
        ccd.falstad.floating = false
    }
}
