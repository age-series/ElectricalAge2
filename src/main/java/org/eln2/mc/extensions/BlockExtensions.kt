package org.eln2.mc.extensions

import net.minecraft.core.Direction
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState

fun BlockState.facing(): Direction = this.getValue(HorizontalDirectionalBlock.FACING)
