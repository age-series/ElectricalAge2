package org.eln2.mc.client.flywheel.instances

import com.jozufozu.flywheel.api.MaterialManager
import com.jozufozu.flywheel.api.instance.DynamicInstance
import com.jozufozu.flywheel.backend.instancing.blockentity.BlockEntityInstance
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.MultipartBlockEntity
import org.eln2.mc.common.parts.Part
import org.eln2.mc.common.parts.PartUpdateType
import org.eln2.mc.utility.ClientOnly

@ClientOnly
class MultipartBlockEntityInstance(val materialManager: MaterialManager, blockEntity: MultipartBlockEntity) :
    BlockEntityInstance<MultipartBlockEntity>(materialManager, blockEntity),
    DynamicInstance {

    private val parts = ArrayList<Part>()

    override fun init() {
        super.init()

        blockEntity.bindRenderer(this)
    }

    /**
     * Called by flywheel at the start of each frame.
     * This applies any part updates (new or removed parts), and notifies the part renderers about the new frame.
     * */
    override fun beginFrame() {
        handlePartUpdates()

        parts.forEach { part ->
            val renderer = part.renderer

            renderer.beginFrame()
        }
    }

    /**
     * Called by flywheel when a re-light is required.
     * This applies a re-light to all the part renderers.
     * */
    override fun updateLight() {
        parts.forEach { part ->
            relightPart(part)
        }
    }

    /**
     * This method is called at the start of each frame.
     * It dequeues all the part updates that were handled on the game thread.
     * These updates may indicate:
     *  - New parts added to the multipart.
     *  - Parts that were destroyed.
     * */
    private fun handlePartUpdates(){
        while (true){
            val update = blockEntity.renderQueue.poll() ?: break
            val part = update.part

            when(update.type){
                PartUpdateType.Add -> {
                    // Parts may already be added, because of the bind method that we called.

                    if(!parts.contains(part)){
                        parts.add(part)
                        part.renderer.setupRendering(this)
                        relightPart(part)
                    }
                }
                PartUpdateType.Remove -> {
                    parts.remove(part)
                    part.destroyRenderer()
                }
            }
        }
    }

    /**
     * Called by flywheel when this renderer is no longer needed.
     * This also calls a cleanup method on the part renderers.
     * */
    override fun remove() {
        Eln2.LOGGER.info("Removing multipart renderer")

        parts.forEach { part ->
            part.destroyRenderer()
        }

        blockEntity.unbindRenderer()
    }

    /**
     * This is called by parts when they need to force a re-light.
     * This may happen when a model is initially created.
     * */
    fun relightPart(part : Part){
        val models = part.renderer.relightModels()

        if(models != null){
            relight(pos, models.stream())
        }
    }
}
