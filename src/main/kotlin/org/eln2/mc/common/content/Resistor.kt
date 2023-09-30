package org.eln2.mc.common.content

import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.eln2.mc.LIBAGE_SET_EPS
import org.eln2.mc.NoInj
import org.eln2.mc.add
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.PartialModels.bbOffset
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.approxEq
import org.eln2.mc.mathematics.bbVec

@NoInj
class ResistorObject(
    cell: Cell,
    val poleMap: PoleMap
) : ElectricalObject(cell), DataEntity, WailaEntity {

    private val resistor = ComponentHolder {
        Resistor().also { it.resistance = resistanceExact }
    }

    var resistanceExact: Double = 1.0
        set(value) {
            field = value
            resistor.ifPresent { it.resistance = value }
        }

    /**
     * Updates the resistance if the deviation between the current resistance and [value] is larger than [eps].
     * @return True if the resistance was updated. Otherwise, false.
     * */
    fun updateResistance(value: Double, eps: Double = LIBAGE_SET_EPS): Boolean {
        if(resistanceExact.approxEq(value, eps)) {
            return false
        }

        resistanceExact = value

        return true
    }

    val hasResistor get() = resistor.isPresent

    val current get() = if(resistor.isPresent) resistor.instance.current else 0.0
    val power get() = if(resistor.isPresent) resistor.instance.power else 0.0
    val potential get() = if(resistor.isPresent) resistor.instance.potential else 0.0

    override val maxConnections = 2

    override fun offerComponent(neighbour: ElectricalObject) = ElectricalComponentInfo(
        resistor.instance,
        poleMap.evaluate(cell.locator, neighbour.cell.locator).conventionalPin
    )

    override fun clearComponents() = resistor.clear()

    override fun addComponents(circuit: Circuit) {
        circuit.add(resistor)
    }

    override val dataNode = data {
        it.withField(ResistanceField {
            resistanceExact
        })

        it.withField(CurrentField {
            current
        })

        it.withField(PowerField {
            power
        })
    }
}

class ResistorCell(ci: CellCreateInfo) : Cell(ci) {
    companion object {
        private val A = Base6Direction3d.Front
        private val B = Base6Direction3d.Back
    }

    init {
        ruleSet.withDirectionRule(A + B)
    }

    @SimObject @Inspect
    val resistor = ResistorObject(this, directionPoleMap(A, B))

    @SimObject @Inspect
    val thermalWire = ThermalWireObject(this)

    @Behavior
    val heating = PowerHeatingBehavior({ resistor.power }, thermalWire.thermalBody)
}

class ResistorPart(id: ResourceLocation, placementContext: PartPlacementInfo) :
    CellPart<ResistorCell, BasicPartRenderer>(id, placementContext, Content.RESISTOR_CELL.get()) {

    override val partSize = bbVec(3.5, 2.25, 5.0)

    override fun createRenderer() = BasicPartRenderer(this, PartialModels.RESISTOR).also {
        it.downOffset = bbOffset(2.5)
    }
}
