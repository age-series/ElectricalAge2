package org.eln2.mc.common.parts

import com.jozufozu.flywheel.core.materials.FlatLit
import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance
import org.eln2.mc.utility.CrossThreadAccess

/**
 * This is the per-part renderer. One is created for every instance of a part.
 * The methods may be called from separate threads.
 * Thread safety must be guaranteed by the implementation.
 * */
@CrossThreadAccess
interface IPartRenderer {
    /**
     * Called when the part is picked up by the renderer.
     * @param multipart The renderer instance.
     * */
    fun setupRendering(multipart: MultipartBlockEntityInstance)

    /**
     * Called each frame. This method may be used to animate parts or to
     * apply general per-frame updates.
     * */
    fun beginFrame()

    /**
     * Called when a re-light is required.
     * This happens when the light sources are changed.
     * @return A list of models that need relighting, or null if none do so.
     * */
    fun relightModels() : List<FlatLit<*>>?

    /**
     * Called when the renderer is no longer required.
     * All resources must be released here.
     * */
    fun remove()
}
