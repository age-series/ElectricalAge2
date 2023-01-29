package org.eln2.mc.client.flywheel.instances

import com.jozufozu.flywheel.api.InstancerManager
import com.jozufozu.flywheel.api.instance.DynamicInstance
import com.jozufozu.flywheel.backend.instancing.blockentity.BlockEntityInstance
import com.jozufozu.flywheel.core.structs.FlatLit
import org.eln2.mc.common.blocks.MultipartBlockEntity
import org.eln2.mc.common.parts.Part

class MultipartBlockEntityInstance(
    val instancerManager: InstancerManager,
    val blockEntity: MultipartBlockEntity) :
    BlockEntityInstance<MultipartBlockEntity>(instancerManager, blockEntity),
    DynamicInstance {

    private val parts = ArrayList<Part>()

    private val relightModels = ArrayList<FlatLit<*>>()

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
            part.renderer.relight()
        }

        processRelightQueue()
    }

    private fun processRelightQueue(){
        relight(pos, relightModels.stream())
        relightModels.clear()
    }

    /**
     * Use this method to enqueue light updates for your part models.
     * */
    fun enqueueRelight(vararg models: FlatLit<*>) {
        relightModels.addAll(models)
    }

    private fun setupNewParts(){
        var hasNewParts = false

        while (true){
            val part = blockEntity.clientNewPartQueue.poll() ?: break

            parts.add(part)
            part.renderer.setupRendering(this)
            part.renderer.relight()

            hasNewParts = true
        }

        if(hasNewParts){
            processRelightQueue()
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
}
