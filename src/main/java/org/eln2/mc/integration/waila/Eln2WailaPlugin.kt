package org.eln2.mc.integration.waila

import mcp.mobius.waila.api.*
import mcp.mobius.waila.api.component.PairComponent
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.world.level.block.entity.BlockEntity
import org.eln2.mc.Eln2

@WailaPlugin(id = "${Eln2.MODID}:waila_plugin")
class Eln2WailaPlugin : IWailaPlugin {
    override fun register(registrar: IRegistrar?) {
        if (registrar == null) {
            return
        }

        registrar.addComponent(object : IBlockComponentProvider {
            override fun appendBody(tooltip: ITooltip?, accessor: IBlockAccessor?, config: IPluginConfig?) {
                if (tooltip == null || accessor == null || config == null) {
                    return
                }

                val entries = TooltipList.fromNbt(accessor.serverData)

                entries.values.forEach { entry ->
                    entry.write(tooltip)
                }
            }
        }, TooltipPosition.BODY, BlockEntity::class.java)

        registrar.addBlockData(object: IServerDataProvider<BlockEntity>{
            override fun appendServerData(
                data: CompoundTag?,
                accessor: IServerAccessor<BlockEntity>?,
                config: IPluginConfig?
            ) {
                if (data == null || accessor == null) {
                    return
                }

                val blockEntity = accessor.target

                if(blockEntity !is IWailaProvider){
                    return
                }

                val builder = TooltipList.builder()

                blockEntity.appendBody(builder, config)

                builder.build().toNbt(data)
            }
        }, BlockEntity::class.java)
    }
}
