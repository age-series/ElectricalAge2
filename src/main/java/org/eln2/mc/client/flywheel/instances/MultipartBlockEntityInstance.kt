package org.eln2.mc.client.flywheel.instances

import com.jozufozu.flywheel.api.InstancerManager
import com.jozufozu.flywheel.api.instance.DynamicInstance
import com.jozufozu.flywheel.backend.instancing.blockentity.BlockEntityInstance
import com.jozufozu.flywheel.core.structs.FlatLit
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.MultipartBlockEntity
import org.eln2.mc.common.parts.Part

class MultipartBlockEntityInstance(
    val instancerManager: InstancerManager,
    val blockEntity: MultipartBlockEntity) :
    BlockEntityInstance<MultipartBlockEntity>(instancerManager, blockEntity),
    DynamicInstance {

    private val parts = ArrayList<Part>()

    override fun beginFrame() {
        setupNewParts()
        cleanupRemovedParts()

        parts.forEach { part ->
            val renderer = part.renderer

            renderer.beginFrame()
        }
    }

    override fun updateLight() {
        parts.forEach { part ->
            relightPart(part)
        }
    }

    private fun setupNewParts(){
        while (true){
            val part = blockEntity.clientNewPartQueue.poll() ?: break

            parts.add(part)
            part.renderer.setupRendering(this)
            relightPart(part)
        }
    }

    private fun cleanupRemovedParts(){
        while (true){
            val part = blockEntity.clientRemovedQueue.poll() ?: break

            parts.remove(part)
            part.renderer.remove()
        }
    }

    override fun remove() {
        parts.forEach { part ->
            part.renderer.remove()
        }
    }

    private fun relightPart(part : Part){
        val models = part.renderer.relightModels()

        if(models != null){
            relight(pos, models.stream())
        }
    }
}
