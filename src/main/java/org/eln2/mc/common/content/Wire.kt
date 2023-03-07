@file:Suppress("NonAsciiCharacters")

package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.Eln2
import org.eln2.mc.mathematics.Mathematics.bbVec
import org.eln2.mc.client.render.MultipartBlockEntityInstance
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.objects.ElectricalComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.common.cells.foundation.objects.ResistorBundle
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.cells.foundation.Conventions
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.ConnectionMode
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.ModelDataExtensions.blockCenter
import org.eln2.mc.extensions.ModelDataExtensions.zeroCenter
import org.eln2.mc.extensions.NbtExtensions.getRelativeDirection
import org.eln2.mc.extensions.NbtExtensions.putRelativeDirection
import org.eln2.mc.extensions.QuaternionExtensions.times
import org.eln2.mc.extensions.Vec3Extensions.times
import org.eln2.mc.extensions.Vec3Extensions.toVec3
import org.eln2.mc.integration.waila.IWailaProvider
import org.eln2.mc.integration.waila.TooltipBuilder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

// This organisation approach is inspired by a suggestion from jrddunbr.
// Having the content in one file does help navigate more easily.

/**
 * The Wire Object has a single resistor bundle. The Internal Pins of the bundle are connected to each other, and
 * the External Pins are exported to other Electrical Objects.
 * */
class WireObject : ElectricalObject(), IWailaProvider {
    private val resistors = ResistorBundle(0.05)

    /**
     * Gets or sets the resistance of the bundle.
     * Only applied when the circuit is re-built.
     * */
    var resistance : Double = resistors.resistance
        set(value) {
            field = value
            resistors.resistance = value
        }

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
        return resistors.getOfferedResistor(directionOf(neighbour))
    }

    override fun clearComponents() {
        resistors.clear()
    }

    override fun addComponents(circuit: Circuit) {
        resistors.register(connections, circuit)
    }

    override fun build() {
        resistors.connect(connections, this)

        // Is there a better way to do this?

        // The Wire uses a bundle of 4 resistors. Every resistor's "Internal Pin" is connected to every
        // other resistor's internal pin. "External Pins" are offered to connection candidates.

        resistors.process { a ->
            resistors.process { b ->
                if (a != b) {
                    a.connect(Conventions.INTERNAL_PIN, b, Conventions.INTERNAL_PIN)
                }
            }
        }
    }

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        if (connections.size == 2) {
            // Straight through wire. Just give absolute value I guess since directionality is ~ meaningless for wires.

            val sampleResistor = resistors.getOfferedResistor(connections[0].direction).component as Resistor

            builder.current(abs(sampleResistor.current))
        } else {
            // Branch currents. Print them all.

            connections
                .map { (resistors.getOfferedResistor(it.direction).component as Resistor).current }
                .forEach { builder.current(it) }
        }
    }
}

data class WireModel(val resistanceMeter: Double)

object WireModels {
    private fun getResistance(ρ: Double, L: Double, A: Double): Double {
        return ρ * L / A
    }

    fun copper(thickness: Double): WireModel {
        return WireModel(getResistance(1.77 * 10e-8, 1.0, thickness))
    }
}

class WireCell(pos: CellPos, id: ResourceLocation, val model: WireModel) : CellBase(pos, id) {
    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(WireObject().also {
            it.resistance = model.resistanceMeter / 2.0 // Divide by two because bundle creates 2 resistors
        })
    }
}

class WirePart(id: ResourceLocation, context: PartPlacementContext, cellProvider: CellProvider) :
    CellPart(id, context, cellProvider) {

    override val baseSize = bbVec(8.0, 2.0, 8.0)

    private val connectedDirections = HashSet<RelativeRotationDirection>()
    private var wireRenderer: WirePartRenderer? = null

    override fun createRenderer(): IPartRenderer {
        wireRenderer = WirePartRenderer(this)

        applyRendererState()

        return wireRenderer!!
    }

    override fun destroyRenderer() {
        super.destroyRenderer()
        wireRenderer = null
    }

    override fun onPlaced() {
        super.onPlaced()

        if (!placementContext.level.isClientSide) {
            syncChanges()
        }
    }

    override fun loadFromTag(tag: CompoundTag) {
        super.loadFromTag(tag)

        val wireData = tag.get("WireData") as CompoundTag

        loadTag(wireData)
    }

    override fun getSaveTag(): CompoundTag? {
        val tag = super.getSaveTag() ?: return null

        val wireData = createTag()

        tag.put("WireData", wireData)

        return tag
    }

    override fun getSyncTag(): CompoundTag {
        return createTag()
    }

    override fun handleSyncTag(tag: CompoundTag) {
        loadTag(tag)
    }

    private fun createTag(): CompoundTag {
        val tag = CompoundTag()

        val directionList = ListTag()

        connectedDirections.forEach { direction ->
            val directionTag = CompoundTag()

            directionTag.putRelativeDirection("Direction", direction)

            directionList.add(directionTag)
        }

        tag.put("Directions", directionList)

        return tag
    }

    private fun loadTag(tag: CompoundTag) {
        connectedDirections.clear()

        val directionList = tag.get("Directions") as ListTag

        directionList.forEach { directionTag ->
            val direction = (directionTag as CompoundTag).getRelativeDirection("Direction")

            connectedDirections.add(direction)
        }

        if (placementContext.level.isClientSide) {
            applyRendererState()
        }
    }

    private fun applyRendererState() {
        wireRenderer?.applyDirections(connectedDirections.toList())
    }

    override fun recordConnection(direction: RelativeRotationDirection, mode: ConnectionMode) {
        connectedDirections.add(direction)
        syncAndSave()
    }

    override fun recordDeletedConnection(direction: RelativeRotationDirection) {
        connectedDirections.remove(direction)
        syncAndSave()
    }
}

class WirePartRenderer(val part: WirePart) : IPartRenderer {
    companion object {
        // P.S. these are also written in respect to the model rotation.

        private val caseMap = mapOf(
            (DirectionMask.EMPTY) to PartialModels.WIRE_CROSSING_EMPTY,
            (DirectionMask.FRONT) to PartialModels.WIRE_CROSSING_SINGLE_WIRE,
            (DirectionMask.FRONT + DirectionMask.BACK) to PartialModels.WIRE_STRAIGHT,
            (DirectionMask.FRONT + DirectionMask.LEFT) to PartialModels.WIRE_CORNER,
            (DirectionMask.LEFT + DirectionMask.FRONT + DirectionMask.RIGHT) to PartialModels.WIRE_CROSSING,
            (DirectionMask.HORIZONTALS) to PartialModels.WIRE_CROSSING_FULL
        )
    }

    private lateinit var multipartInstance: MultipartBlockEntityInstance
    private var modelInstance: ModelData? = null

    // Reset on every frame
    private var latestDirections = AtomicReference<List<RelativeRotationDirection>>()

    fun applyDirections(directions: List<RelativeRotationDirection>) {
        latestDirections.set(directions)
    }

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        multipartInstance = multipart
    }

    override fun beginFrame() {
        selectModel()
    }

    private fun selectModel() {
        val directions =
            latestDirections.getAndSet(null)
                ?: return

        val mask = DirectionMask.ofRelatives(directions)

        var found = false

        // Here, we search for the correct configuration by just rotating all the cases we know.
        caseMap.forEach { (mappingMask, model) ->
            val match = mappingMask.matchCounterClockWise(mask)

            if (match != -1) {
                found = true

                updateInstance(model, Vector3f.YP.rotationDegrees(90f * match))

                return@forEach
            }
        }

        if (!found) {
            Eln2.LOGGER.error("Wire did not handle cases: $directions")
        }
    }

    private fun updateInstance(model: PartialModel, rotation: Quaternion) {
        modelInstance?.delete()

        // Conversion equation:
        // let S - world size, B - block bench size
        // S = B / 16
        val size = 1.5 / 16

        // todo: it still looks a little bit off the ground, why?

        modelInstance = multipartInstance.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .translate(part.placementContext.face.opposite.normal.toVec3() * Vec3(size, size, size))
            .blockCenter()
            .translate(part.worldBoundingBox.center)
            .multiply(part.placementContext.face.rotation * part.facingRotation * rotation)
            .zeroCenter()

        multipartInstance.relightPart(part)
    }

    override fun relightModels(): List<FlatLit<*>> {
        if (modelInstance != null) {
            return listOf(modelInstance!!)
        }

        return listOf()
    }

    override fun remove() {
        modelInstance?.delete()
    }
}
