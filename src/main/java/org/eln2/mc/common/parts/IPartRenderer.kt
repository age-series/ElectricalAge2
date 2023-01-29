package org.eln2.mc.common.parts

import com.jozufozu.flywheel.core.structs.FlatLit
import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance

interface IPartRenderer {
    fun setupRendering(multipart: MultipartBlockEntityInstance)

    fun beginFrame()

    fun relightModels() : List<FlatLit<*>>?

    fun remove()
}
