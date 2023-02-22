package org.eln2.mc.common.content

import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.material.Material

class LightBlock : AirBlock(Properties.of(Material.AIR).lightLevel { 10 })
