package org.eln2.mc.common.parts

import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance

interface IPartRenderer {
    fun setupRendering(multipart: MultipartBlockEntityInstance)

    fun beginFrame()

    fun relight()

    fun remove()
}
