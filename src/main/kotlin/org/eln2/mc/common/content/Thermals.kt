package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.sim.thermal.Temperature
import org.eln2.mc.NoInj
import org.eln2.mc.ThermalBody
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.MultipartBlockEntityInstance
import org.eln2.mc.client.render.foundation.RadiantBodyColor
import org.eln2.mc.client.render.foundation.createPartInstance
import org.eln2.mc.client.render.foundation.defaultRadiantBodyColor
import org.eln2.mc.common.cells.foundation.TemperatureReplicator
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.common.network.serverToClient.PacketHandlerBuilder
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.common.parts.foundation.PartRenderer

class RadiatorPart(
    id: ResourceLocation,
    placementContext: PartPlacementInfo,
    val radiantColor: RadiantBodyColor
) : CellPart<ThermalWireCell, RadiantBodyRenderer>(id, placementContext, Content.THERMAL_RADIATOR_CELL.get()), TemperatureReplicator {
    override val partSize = Vec3(1.0, 3.0 / 16.0, 1.0)

    override fun createRenderer() = RadiantBodyRenderer(this, PartialModels.RADIATOR, radiantColor, bbOffset(3.0), 0.0)

    override fun registerPackets(builder: PacketHandlerBuilder) {
        builder.withHandler<Sync> {
            renderer.update(Temperature(it.temperature))
        }
    }

    override fun streamTemperatureChanges(bodies: List<ThermalBody>, dirty: List<ThermalBody>) {
        sendBulkPacket(Sync(bodies.first().temperatureKelvin))
    }

    @Serializable
    private data class Sync(val temperature: Double)
}

class RadiantBodyRenderer(
    val part: Part<*>,
    val model: PartialModel,
    val color: RadiantBodyColor,
    val offset: Double,
    val yRotation: Double
) : PartRenderer() {
    private var bodyInstance: ModelData? = null
    private val updates = AtomicUpdate<Temperature>()

    fun update(temperature: Temperature) = updates.setLatest(temperature)

    override fun setupRendering() {
        bodyInstance = createPartInstance(multipart, model, part, offset, yRotation)
    }

    override fun beginFrame() {
        if(bodyInstance != null) {
            updates.consume { temperature ->
                bodyInstance?.setColor(color.evaluate(temperature))
            }
        }
    }

    override fun getModelsToRelight() = bodyInstance?.let { listOf(it) } ?: emptyList()

    override fun remove() {
        bodyInstance?.delete()
    }
}

@NoInj
class RadiantBipoleRenderer(
    val part: Part<*>,
    val body: PartialModel,
    val left: PartialModel,
    val right: PartialModel,
    val bodyDownOffset: Double,
    val bodyRotation: Double,
    val leftDownOffset: Double,
    val leftRotation: Double,
    val rightDownOffset: Double,
    val rightRotation: Double,
    val leftColor: RadiantBodyColor,
    val rightColor: RadiantBodyColor,
) : PartRenderer() {
    constructor(
        part: Part<*>,
        body: PartialModel,
        left: PartialModel,
        right: PartialModel,
        downOffset: Double,
        rotation: Double,
        leftColor: RadiantBodyColor,
        rightColor: RadiantBodyColor,
    ) :
        this(
            part,
            body,
            left,
            right,
            downOffset,
            rotation,
            downOffset,
            rotation,
            downOffset,
            rotation,
            leftColor,
            rightColor
        )

    constructor(
        part: Part<*>,
        body: PartialModel,
        left: PartialModel,
        right: PartialModel,
        downOffset: Double,
        rotation: Double,
    ) :
        this(part, body, left, right, downOffset, rotation, defaultRadiantBodyColor(), defaultRadiantBodyColor())

    private var bodyInstance: ModelData? = null
    private var leftInstance: ModelData? = null
    private var rightInstance: ModelData? = null

    private val leftSideUpdate = AtomicUpdate<Temperature>()
    private val rightSideUpdate = AtomicUpdate<Temperature>()

    fun updateLeftSideTemperature(value: Temperature) = leftSideUpdate.setLatest(value)

    fun updateRightSideTemperature(value: Temperature) = rightSideUpdate.setLatest(value)

    override fun setupRendering() {
        buildInstance()
    }

    fun buildInstance() {
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

    override fun getModelsToRelight(): List<FlatLit<*>> {
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

