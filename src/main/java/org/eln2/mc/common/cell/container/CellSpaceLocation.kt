package org.eln2.mc.common.cell.container

import net.minecraft.core.Direction
import org.eln2.mc.common.cell.CellBase
import org.eln2.mc.common.cell.CellProvider

data class CellSpaceLocation(val cell : CellBase, val innerFace : Direction)
