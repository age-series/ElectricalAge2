package org.eln2.mc.common.content

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.Conventions
import org.eln2.mc.common.cells.foundation.objects.ElectricalComponentInfo
import org.eln2.mc.common.cells.foundation.objects.ElectricalObject
import org.eln2.mc.common.cells.foundation.objects.ResistorBundle
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.ConnectionMode
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.common.space.RelativeRotationDirection

/**
 * The ground object is simply a bundle of resistors, with one grounded pin.
 * The ungrounded pin is exported to other Electrical Objects.
 * */
class GroundObject : ElectricalObject() {
    private val resistors = ResistorBundle(0.01)

    /**
     * Gets or sets the resistance of the bundle.
     * Only applied when the circuit is re-built.
     * */
    var resistance: Double
        get() = resistors.resistance
        set(value) { resistors.resistance = value }

    override fun offerComponent(neighbour: ElectricalObject): ElectricalComponentInfo {
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
        resistors.process { it.ground(Conventions.INTERNAL_PIN) }
    }
}

class GroundCell(pos: CellPos, id: ResourceLocation) : CellBase(pos, id) {
    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(GroundObject())
    }
}

class GroundPart(id: ResourceLocation, placementContext: PartPlacementContext) :
    CellPart(id, placementContext, CellRegistry.GROUND_CELL.get()) {

    override val baseSize = Vec3(1.0, 1.0, 1.0)

    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.WIRE_CROSSING_EMPTY)
    }

    override fun recordConnection(direction: RelativeRotationDirection, mode: ConnectionMode) {}

    override fun recordDeletedConnection(direction: RelativeRotationDirection) {}
}
