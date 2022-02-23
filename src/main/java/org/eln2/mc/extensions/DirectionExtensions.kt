package org.eln2.mc.extensions

import net.minecraft.core.Direction

// Some helper functions for rotations in Direction
fun Direction.getFront(dir: Direction) = dir
fun Direction.getBack(dir: Direction) = dir.opposite
fun Direction.getLeft(dir: Direction) = dir.counterClockWise
fun Direction.getRight(dir: Direction) = dir.clockWise
