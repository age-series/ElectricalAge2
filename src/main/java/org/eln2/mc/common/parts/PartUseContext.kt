package org.eln2.mc.common.parts

import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player

data class PartUseContext(val player : Player, val hand : InteractionHand)