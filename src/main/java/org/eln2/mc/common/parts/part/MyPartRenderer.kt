package org.eln2.mc.common.parts.part

import com.jozufozu.flywheel.core.BasicModelSupplier
import com.jozufozu.flywheel.core.material.MaterialShaders
import com.jozufozu.flywheel.core.structs.StructTypes
import com.jozufozu.flywheel.core.structs.model.ModelData
import com.jozufozu.flywheel.core.structs.oriented.OrientedData
import com.jozufozu.flywheel.vanilla.ChestInstance
import net.minecraft.Util
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.resources.model.Material
import net.minecraft.world.level.block.state.properties.ChestType
import org.eln2.mc.Eln2
import org.eln2.mc.client.flywheel.instances.MultipartBlockEntityInstance
import org.eln2.mc.common.parts.IPartRenderer

class MyPartRenderer(val part : MyPart) : IPartRenderer {
    private lateinit var instance : MultipartBlockEntityInstance

    private lateinit var model : OrientedData

    override fun setupRendering(multipartBlockEntityInstance: MultipartBlockEntityInstance) {
        instance = multipartBlockEntityInstance
    }

    override fun beginFrame() {
        Eln2.LOGGER.info("Begin Frame")
    }

    override fun relight() {
        Eln2.LOGGER.info("Relight")
    }

    override fun remove() {
        model.delete()
    }

    private fun loadInstance(){

    }
}
