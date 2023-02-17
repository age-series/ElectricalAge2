package org.eln2.mc.integration.waila

import mcp.mobius.waila.api.*
import net.minecraft.nbt.CompoundTag
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

        registrar.addBlockData(object : IServerDataProvider<BlockEntity> {
            override fun appendServerData(
                data: CompoundTag?,
                accessor: IServerAccessor<BlockEntity>?,
                config: IPluginConfig?
            ) {
                if (data == null || accessor == null) {
                    return
                }

                val blockEntity = accessor.target

                if (blockEntity !is IWailaProvider) {
                    return
                }

                val builder = TooltipList.builder()

                try {
                    blockEntity.appendBody(builder, config)
                } catch (_: Exception) {
                    // Handle errors caused by simulator
                    // Make sure you add a breakpoint here if you aren't getting your toolip properly
                }

                builder.build().toNbt(data)
            }
        }, BlockEntity::class.java)
    }
}
