package org.eln2.mc.common.blocks.cell

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.network.NetworkHooks
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.common.cell.CellRegistry
import org.eln2.mc.common.containers.VoltageSourceCellContainer

class VoltageSourceCellBlock : CellBlockBase() {
    override fun getCellProvider(): ResourceLocation {
        return CellRegistry.VOLTAGE_SOURCE_CELL.id
    }

    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult
    ): InteractionResult {
        if (!pLevel.isClientSide) {
            val te = pLevel.getBlockEntity(pPos)
            if (te is CellTileEntity) {
                val containerProvider = object : MenuProvider {
                    override fun getDisplayName(): Component {
                        return TranslatableComponent("block.eln2.voltage_source")
                    }

                    override fun createMenu(
                        pContainerId: Int,
                        pInventory: Inventory,
                        pPlayer: Player
                    ): AbstractContainerMenu {
                        return VoltageSourceCellContainer(pContainerId, pInventory, pPlayer, te)
                    }
                }

                NetworkHooks.openGui(pPlayer as ServerPlayer, containerProvider, te.pos)
                return InteractionResult.SUCCESS
            }
        }

        return InteractionResult.SUCCESS
    }
}
