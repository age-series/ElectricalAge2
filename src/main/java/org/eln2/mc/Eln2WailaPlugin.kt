package org.eln2.mc

import mcp.mobius.waila.api.*
import mcp.mobius.waila.api.component.PairComponent
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import org.eln2.mc.common.blocks.CellTileEntity
import org.eln2.mc.extensions.NbtExtensions.getStringMap
import org.eln2.mc.extensions.NbtExtensions.setStringMap


@WailaPlugin(id = "${Eln2.MODID}:waila_plugin")
class Eln2WailaPlugin: IWailaPlugin {
    override fun register(registrar: IRegistrar?) {
        if (registrar == null) return

        val clientProvider: IBlockComponentProvider = object: IBlockComponentProvider {
            override fun appendBody(tooltip: ITooltip?, accessor: IBlockAccessor?, config: IPluginConfig?) {
                super.appendBody(tooltip, accessor, config)
                if (tooltip == null || accessor == null || config == null) return
                val bodyList = accessor.serverData.getStringMap("body")
                bodyList.forEach { (k, v) ->
                    tooltip.addLine(PairComponent(TranslatableComponent(k), TextComponent(v)))
                }
            }
        }

        registrar.addComponent(clientProvider, TooltipPosition.BODY, CellTileEntity::class.java)

        val serverProvider: IServerDataProvider<CellTileEntity> = object: IServerDataProvider<CellTileEntity> {
            override fun appendServerData(
                sendData: CompoundTag?,
                player: ServerPlayer?,
                world: Level?,
                cell: CellTileEntity?
            ) {
                if (sendData == null || player == null || world == null || cell == null) return
                sendData.setStringMap("body", cell.getHudMap())
            }
        }

        registrar.addBlockData(serverProvider, CellTileEntity::class.java)

    }
}
