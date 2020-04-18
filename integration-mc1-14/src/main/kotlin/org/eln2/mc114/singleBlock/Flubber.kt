package org.eln2.mc114.singleBlock

import cam72cam.mod.block.BlockSettings
import cam72cam.mod.block.BlockType
import cam72cam.mod.entity.Player
import cam72cam.mod.item.ItemBase
import cam72cam.mod.item.ItemStack
import cam72cam.mod.math.Vec3d
import cam72cam.mod.math.Vec3i
import cam72cam.mod.util.Facing
import cam72cam.mod.util.Hand
import cam72cam.mod.world.World
import org.eln2.mc114.Eln2CreativeTabs
import org.eln2.mc114.Eln2Items

class FlubberBlock: BlockType(BlockSettings("eln2", "flubber")) {

	override fun onPick(world: World?, pos: Vec3i?): ItemStack {
		return ItemStack(Eln2Items.flubberItem, 1)
	}

	override fun onBreak(world: World?, pos: Vec3i?) {
		println("I've been broken!")
	}

	override fun onClick(world: World?, pos: Vec3i?, player: Player?, hand: Hand?, facing: Facing?, hit: Vec3d?): Boolean {
		return false
	}

	override fun tryBreak(world: World?, pos: Vec3i?, player: Player?): Boolean {
		return true
	}

	override fun onNeighborChange(world: World?, pos: Vec3i?, neighbor: Vec3i?) {
		println("Block Update!")
	}
}

class FlubberItem : ItemBase("eln2", "flubber", 1, Eln2CreativeTabs.MAIN_TAB)
