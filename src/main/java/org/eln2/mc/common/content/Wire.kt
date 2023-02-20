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
import org.eln2.mc.client.render.MultipartBlockEntityInstance
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.objects.ComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.common.cells.foundation.objects.ResistorBundle
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.cells.objects.Conventions
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.ConnectionMode
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeRotationDirection
import org.eln2.mc.extensions.DirectionMaskExtensions.matchCounterClockWise
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

class WireObject : ElectricalObject(), IWailaProvider {
    private val resistors = ResistorBundle(0.05)

    override fun offerComponent(neighbour: ElectricalObject): ComponentInfo {
        return resistors.getOfferedResistor(directionOf(neighbour))
    }

    override fun recreateComponents() {
        resistors.clear()
    }

    override fun registerComponents(circuit: Circuit) {
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

class WireCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(WireObject())
    }
}

class WirePart(id: ResourceLocation, context: PartPlacementContext) :
    CellPart(id, context, CellRegistry.WIRE_CELL.get()) {
    override val baseSize: Vec3 get() = Vec3(0.5, 0.25, 0.5)

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

    override val allowPlanarConnections = true
    override val allowInnerConnections = true
    override val allowWrappedConnections = true
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
