package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.client.render.MultipartBlockEntityInstance
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.client.render.foundation.RadiantBodyColor
import org.eln2.mc.client.render.foundation.createPartInstance
import org.eln2.mc.client.render.foundation.defaultRadiantBodyColor
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.behaviors.withStandardExplosionBehavior
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.sim.ThermalBody

data class RadiatorModel(
    val destructionTemperature: Double,
    val surfaceArea: Double,
    val material: Material,
    val mass: Double)

class ThermalRadiatorCell(pos: CellPos, id: ResourceLocation, val model: RadiatorModel): CellBase(pos, id) {
    init {
        behaviors.withStandardExplosionBehavior(this, model.destructionTemperature) {
            thermalWire.body.temperatureK
        }
    }

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(ThermalWireObject(this).also {
            it.body = ThermalBody(ThermalMass(model.material, it.body.thermalEnergy, model.mass), model.surfaceArea)
        })
    }

    private val thermalWire get() = thermalObject as ThermalWireObject

    val temperature get() = thermalWire.body.temperatureK
}

class RadiatorPart(id: ResourceLocation, placementContext: PartPlacementContext)
    : CellPart(id, placementContext, Content.THERMAL_WIRE_CELL_COPPER.get()) {
    override val baseSize: Vec3
        get() = Vec3(1.0, 3.0 / 16.0, 1.0)

    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.RADIATOR).also {
            it.downOffset = bbOffset(3.0)
        }
    }
}

class RadiantBipoleRenderer(
    val part: Part,
    val body: PartialModel,
    val left: PartialModel,
    val right: PartialModel,
    val bodyDownOffset: Double,
    val bodyRotation: Float,
    val leftDownOffset: Double,
    val leftRotation: Float,
    val rightDownOffset: Double,
    val rightRotation: Float,
    val leftColor: RadiantBodyColor,
    val rightColor: RadiantBodyColor) : IPartRenderer {
    constructor(part: Part, body: PartialModel, left: PartialModel, right: PartialModel, downOffset: Double, rotation: Float, leftColor: RadiantBodyColor, rightColor: RadiantBodyColor) :
        this(part, body, left, right, downOffset, rotation, downOffset, rotation, downOffset, rotation, leftColor, rightColor)

    constructor(part: Part, body: PartialModel, left: PartialModel, right: PartialModel, downOffset: Double, rotation: Float) :
        this(part, body, left, right, downOffset, rotation, defaultRadiantBodyColor(), defaultRadiantBodyColor())

    override fun isSetupWith(multipartBlockEntityInstance: MultipartBlockEntityInstance): Boolean {
        return this::multipart.isInitialized && this.multipart == multipartBlockEntityInstance
    }

    private var bodyInstance: ModelData? = null
    private var leftInstance: ModelData? = null
    private var rightInstance: ModelData? = null

    private lateinit var multipart: MultipartBlockEntityInstance

    private val leftSideUpdate = AtomicUpdate<Temperature>()
    private val rightSideUpdate = AtomicUpdate<Temperature>()

    fun updateLeftSideTemperature(value: Temperature) {
        leftSideUpdate.setLatest(value)
    }

    fun updateRightSideTemperature(value: Temperature) {
        rightSideUpdate.setLatest(value)
    }

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        this.multipart = multipart

        buildInstance()
    }

    fun buildInstance() {
        if (!this::multipart.isInitialized) {
            error("Multipart not initialized!")
        }

        bodyInstance?.delete()
        leftInstance?.delete()
        rightInstance?.delete()

        bodyInstance = createPartInstance(multipart, body, part, bodyDownOffset, bodyRotation)
        leftInstance = createPartInstance(multipart, left, part, leftDownOffset, leftRotation)
        rightInstance = createPartInstance(multipart, right, part, rightDownOffset, rightRotation)

        multipart.relightPart(part)
    }

    override fun beginFrame() {
        leftSideUpdate.consume { leftInstance?.setColor(leftColor.evaluate(it)) }
        rightSideUpdate.consume { rightInstance?.setColor(rightColor.evaluate(it)) }
    }

    override fun relightModels(): List<FlatLit<*>> {
        return ArrayList<FlatLit<*>>().also {
            bodyInstance?.apply { it.add(this) }
            leftInstance?.apply { it.add(this) }
            rightInstance?.apply { it.add(this) }
        }
    }

    override fun remove() {
        bodyInstance?.delete()
        leftInstance?.delete()
        rightInstance?.delete()
    }
}
