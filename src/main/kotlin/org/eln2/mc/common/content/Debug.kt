package org.eln2.mc.common.content

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.Vec3
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.common.parts.foundation.PartUseInfo
import org.eln2.mc.data.*
import org.eln2.mc.mathematics.Base6Direction3d
import kotlin.math.absoluteValue
import kotlin.math.sin

class ElectricalEnergyMeterCell(ci: CellCreateInfo) : Cell(ci) {
    companion object {
        private val A = Base6Direction3d.Front
        private val B = Base6Direction3d.Back
    }

    init {
        ruleSet.withDirectionRulePlanar(A + B)

        data.withField(TooltipField { b ->
            b.text("Metered energy", valueText(converter.energy, UnitType.JOULE))
        })
    }

    @SimObject @Inspect
    val resistor = ResistorObject(this, directionPoleMapPlanar(A, B))

    @Behavior
    val converter = ElectricalPowerConverterBehavior { resistor.power }
}

class ElectricalEnergyMeterPart(ci: PartCreateInfo) : CellPart<ElectricalEnergyMeterCell, BasicPartRenderer>(ci, Content.ELECTRICAL_ENERGY_METER_CELL.get()) {
    override fun createRenderer() = BasicPartRenderer(this, PartialModels.ELECTRICAL_WIRE_HUB)

    override fun onUsedBy(context: PartUseInfo): InteractionResult {
        if(!placement.level.isClientSide) {
            val resistance = cell.resistor.resistanceExact + if(context.player.isSecondaryUseActive) {
                -1.0
            }
            else {
                1.0
            }

            cell.resistor.resistanceExact = resistance.coerceIn(1.0, 1e6)
        }

        return super.onUsedBy(context)
    }
}

class OscillatorCell(ci: CellCreateInfo) : Cell(ci) {
    @SimObject
    val source = VoltageSourceObject(this)

    var t = 0.0

    override fun subscribe(subs: SubscriberCollection) {
        subs.addPre(this::simulationTick)
    }

    private fun simulationTick(dt: Double, phase: SubscriberPhase) {
        source.potential = sin(t / 2.0).absoluteValue * 12.0

        t += dt
    }
}

class OscillatorPart(ci: PartCreateInfo) :
    CellPart<OscillatorCell, BasicPartRenderer>(ci, Content.OSCILLATOR_CELL.get()) {

    override fun createRenderer() = BasicPartRenderer(this, PartialModels.BATTERY)
}
