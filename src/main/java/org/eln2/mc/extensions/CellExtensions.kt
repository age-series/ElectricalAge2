package org.eln2.mc.extensions

import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.sim.BiomeEnvironments

fun CellBase.getEnvironment() = BiomeEnvironments.get(this.graph.level, this.pos)
fun CellBase.getEnvironmentTemp() = this.getEnvironment().temperature
