package org.eln2.node

import net.minecraft.nbt.CompoundNBT

interface INodeNBT {
    fun read(p0: CompoundNBT)
    fun write(p0: CompoundNBT): CompoundNBT
}
