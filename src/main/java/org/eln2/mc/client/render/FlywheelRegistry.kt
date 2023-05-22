package org.eln2.mc.client.render

import com.jozufozu.flywheel.backend.instancing.InstancedRenderRegistry
import org.eln2.mc.client.render.foundation.MultipartBlockEntityInstance
import org.eln2.mc.common.blocks.BlockRegistry

object FlywheelRegistry {
    fun initialize() {
        InstancedRenderRegistry.configure(BlockRegistry.MULTIPART_BLOCK_ENTITY.get())
            .alwaysSkipRender()
            .factory { manager, entity -> MultipartBlockEntityInstance(manager, entity) }
            .apply()
    }
}
