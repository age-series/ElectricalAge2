package org.eln2.mc.common.content

import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.data.DataEntity
import org.eln2.mc.data.TooltipField
import org.eln2.mc.data.data
import org.eln2.mc.scientific.DiffusionFluidOptions
import org.eln2.mc.scientific.VoxelPatchDirection
import org.eln2.mc.scientific.VoxelPatchModule
import kotlin.math.min

private val testDef = DiffusionDef(
    "test",
    DiffusionFluidOptions(
        substeps = 1,
        pressureCoefficient = 100f,
        diffusionRate = 0.1f
    )
)

const val STANDARD_PATCH_LOG = 3

class DiffusionConduitObject(cell: Cell) : DiffusionObject(cell, STANDARD_PATCH_LOG, testDef), DataEntity {
    private var totalDensity = 0f

    override val dataNode = data {
        it.withField(TooltipField { b ->
            b.text("Density", totalDensity)
        })
    }

    fun updateMeasurement() {
        totalDensity = 0f

        simulation.patch.innerScanInner {
            totalDensity += simulation.readDensity(it)
        }
    }

    override fun createVolumetricDefinition(module: VoxelPatchModule) {
        val traversed = HashSet<Pair<VoxelPatchDirection, VoxelPatchDirection>>()

        val dirs = connectionDirections

        dirs.forEach { a ->
            dirs.forEach { b ->
                if (a != b) {
                    val sorted = listOf(a, b).sortedBy { it.ordinal }

                    if (traversed.add(Pair(sorted[0], sorted[1]))) {
                        module.openTube(
                            a, b
                        )
                    }
                }
            }
        }
    }
}

class DiffusionConduitCell(val ci: CellCreateInfo) : Cell(ci) {
    @SimObject
    val diffusionConduitObj = activate<DiffusionConduitObject>()

    override fun subscribe(subs: SubscriberCollection) {
        subs.addPre10(this::update)
    }

    fun update(dt: Double, phase: SubscriberPhase) {
        diffusionConduitObj.updateMeasurement()
    }
}

class DiffusionTestSrcObject(cell: Cell) : DiffusionObject(cell, STANDARD_PATCH_LOG, testDef) {
    override fun createVolumetricDefinition(module: VoxelPatchModule) {
        module.carveInterior()

        connectionDirections.forEach {
            module.carveFace(it)
        }
    }

    fun emit(dt: Double) {
        val sim = this.simulation

        var densityTotal = 0f
        var densityCount = 0

        sim.patch.innerScanInner {
            densityTotal += sim.readDensity(it)
            densityCount++
        }

        val dxDensityMax = DENSITY_MAX - densityTotal

        if (dxDensityMax > 0.0) {
            val dxDensityActual = min(dxDensityMax, EMISSION_RATE * dt.toFloat())
            val dxDensityCell = dxDensityActual / densityCount

            sim.patch.innerScanInner {
                sim.addDensityIncr(it, dxDensityCell)
            }
        }
    }

    companion object {
        const val EMISSION_RATE = 10f
        const val DENSITY_MAX = 100f
    }
}

class DRAGONSTestSrcCell(val ci: CellCreateInfo) : Cell(ci) {
    @SimObject
    val diffusionSourceObj = activate<DiffusionTestSrcObject>()

    override fun subscribe(subs: SubscriberCollection) {
        subs.addPre(this::simulationTick)
    }

    private fun simulationTick(dt: Double, phase: SubscriberPhase) {
        diffusionSourceObj.emit(dt)
    }
}
