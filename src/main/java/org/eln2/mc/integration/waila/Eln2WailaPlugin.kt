package org.eln2.mc.integration.waila

import mcp.mobius.waila.api.*
import mcp.mobius.waila.api.component.PairComponent
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.extensions.NbtExtensions.getStringMap
import org.eln2.mc.extensions.NbtExtensions.putStringMap


@WailaPlugin(id = "${Eln2.MODID}:waila_plugin")
class Eln2WailaPlugin : IWailaPlugin {
    override fun register(registrar: IRegistrar?) {
        if (registrar == null) {
            return
        }

        val clientProvider: IBlockComponentProvider = object : IBlockComponentProvider {
            override fun appendBody(tooltip: ITooltip?, accessor: IBlockAccessor?, config: IPluginConfig?) {
                super.appendBody(tooltip, accessor, config)

                if (tooltip == null || accessor == null || config == null) {
                    return
                }

                val bodyList = accessor.serverData.getStringMap("body")

                bodyList.forEach { (k, v) ->
                    tooltip.addLine(PairComponent(TranslatableComponent(k), TextComponent(v)))
                }
            }
        }

        registrar.addComponent(clientProvider, TooltipPosition.BODY, CellBlockEntity::class.java)
        registrar.addComponent(clientProvider, TooltipPosition.BODY, MultipartBlockEntity::class.java)

        val cellBlockServerProvider: IServerDataProvider<CellBlockEntity> =
            object : IServerDataProvider<CellBlockEntity> {
                override fun appendServerData(
                    data: CompoundTag?,
                    accessor: IServerAccessor<CellBlockEntity>?,
                    config: IPluginConfig?
                ) {
                    if (data == null || accessor == null) {
                        return
                    }

                    val cellBlockEntity = accessor.target

                    data.putStringMap("body", cellBlockEntity.getHudMap().mapKeys { "waila.eln2.${it.key}" })
                }
            }

        registrar.addBlockData(cellBlockServerProvider, CellBlockEntity::class.java)

        val multipartServerProvider: IServerDataProvider<MultipartBlockEntity> =
            object : IServerDataProvider<MultipartBlockEntity> {
                override fun appendServerData(
                    data: CompoundTag?,
                    accessor: IServerAccessor<MultipartBlockEntity>?,
                    config: IPluginConfig?
                ) {
                    if (data == null || accessor == null) {
                        return
                    }

                    val multipartBlockEntity = accessor.target

                    data.putStringMap("body", multipartBlockEntity.getHudMap().mapKeys { "waila.eln2.${it.key}" })
                }
            }

        registrar.addBlockData(cellBlockServerProvider, CellBlockEntity::class.java)
        registrar.addBlockData(multipartServerProvider, MultipartBlockEntity::class.java)
    }
}
