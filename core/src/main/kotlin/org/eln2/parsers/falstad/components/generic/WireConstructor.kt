package org.eln2.parsers.falstad.components.generic

import org.eln2.parsers.falstad.CCData
import org.eln2.parsers.falstad.IComponentConstructor

/**
 * Wire Constructor
 *
 * Falstad's wires have 0 ohms of resistance, so we should try to compress them into the same node in the MNA
 */
class WireConstructor : IComponentConstructor {
    override fun construct(ccd: CCData) {
        ccd.falstad.getPin(ccd.pos)
            .unite(ccd.falstad.getPin(ccd.neg))
    }
}
