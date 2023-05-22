package org.eln2.mc.extensions

import org.eln2.mc.common.cells.foundation.Cell
import org.eln2.mc.sim.BiomeEnvironments

fun Cell.getEnvironment() = BiomeEnvironments.get(this.graph.level, this.pos)
fun Cell.getEnvironmentTemp() = this.getEnvironment().temperature
