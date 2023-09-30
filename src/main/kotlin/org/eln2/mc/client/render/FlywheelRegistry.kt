package org.eln2.mc.client.render

import com.jozufozu.flywheel.backend.instancing.InstancedRenderRegistry
import org.eln2.mc.client.render.foundation.MultipartBlockEntityInstance
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.content.Content
//import org.eln2.mc.common.content.GridConnectionInstanceFlw

object FlywheelRegistry {
    fun initialize() {
        InstancedRenderRegistry.configure(BlockRegistry.MULTIPART_BLOCK_ENTITY.get())
            .alwaysSkipRender()
            .factory { manager, entity -> MultipartBlockEntityInstance(manager, entity) }
            .apply()

      /*  InstancedRenderRegistry.configure(Content.GRID_CONNECTION_ENTITY.get())
            .alwaysSkipRender()
            .factory { manager, entity -> GridConnectionInstanceFlw(manager, entity) }
            .apply()*/
    }
}
