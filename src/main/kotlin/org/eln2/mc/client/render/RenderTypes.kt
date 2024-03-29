@file:Suppress("INACCESSIBLE_TYPE")

package org.eln2.mc.client.render

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import org.eln2.mc.LOG
import org.eln2.mc.noop
import org.eln2.mc.resource

object RenderTypes : RenderStateShard("Dragon", ::noop, ::noop) {
    val CABLE: RenderType = RenderType.create(
        "cable",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        2097152,
        true,
        false,
        RenderType.CompositeState
            .builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(RENDERTYPE_SOLID_SHADER)
            .setTextureState(
                TextureStateShard(
                    resource("textures/cable/cable.png"),
                    false,
                    true
                )
            )
            .createCompositeState(true)
    )

    fun initialize() {
        LOG.info("Initialized Render Types")
    }
}
