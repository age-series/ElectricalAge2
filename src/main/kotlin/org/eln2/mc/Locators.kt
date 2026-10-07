@file:Suppress("UNCHECKED_CAST")

package org.eln2.mc

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.ageseries.libage.data.Locator
import org.ageseries.libage.data.LocatorDispatcher
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.sim.Pole
import org.eln2.mc.common.cells.foundation.Cell
import org.eln2.mc.common.cells.foundation.CellLayer
import org.eln2.mc.common.cells.foundation.CellLayer.Block
import org.eln2.mc.common.cells.foundation.CellLayer.Part
import org.eln2.mc.common.cells.foundation.CellLayer.Spec
import org.eln2.mc.common.network.serverToClient.getBlockPos
import org.eln2.mc.common.network.serverToClient.putBlockPos
import org.eln2.mc.common.parts.foundation.getPartConnectionOrNull
import org.eln2.mc.extensions.directionTo
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.FacingDirection
import java.nio.ByteBuffer

@Suppress("NOTHING_TO_INLINE")
object Locators : LocatorDispatcher<Locators>() {
    //#region Serializers

    private inline fun writeInt(int: Int, buffer: ByteBuffer) { buffer.putInt(int) }

    private inline fun readInt(buffer: ByteBuffer) = buffer.getInt()

    private inline fun readCellLayer(buffer: ByteBuffer) = CellLayer.fromId(buffer.get())

    private inline fun writeCellLayer(layer: CellLayer, buffer: ByteBuffer) = buffer.put(layer.id)

    private inline fun writeBlockPos(blockPos: BlockPos, buffer: ByteBuffer) {
        buffer.putInt(blockPos.x)
        buffer.putInt(blockPos.y)
        buffer.putInt(blockPos.z)
    }

    private inline fun readBlockPos(buffer: ByteBuffer) = BlockPos(
        buffer.getInt(),
        buffer.getInt(),
        buffer.getInt()
    )

    private inline fun writeDirection(direction: Direction, buffer: ByteBuffer) { buffer.put(direction.get3DDataValue().toByte()) }

    private inline fun readDirection(buffer: ByteBuffer) = Direction.from3DDataValue(buffer.get().toInt())

    private inline fun writeFacingDirection(direction: FacingDirection, buffer: ByteBuffer) { buffer.put(direction.index.toByte()) }

    private inline fun readFacingDirection(buffer: ByteBuffer) = FacingDirection.byIndex(buffer.get().toInt())

    private inline fun writeVector3d(vector3d: Vector3d, buffer: ByteBuffer) {
        buffer.putDouble(vector3d.x)
        buffer.putDouble(vector3d.y)
        buffer.putDouble(vector3d.z)
    }

    private inline fun writeBlockPair(pair: Pair<BlockPos, BlockPos>, buffer: ByteBuffer) {
        buffer.putBlockPos(pair.first)
        buffer.putBlockPos(pair.second)
    }

    private inline fun readBlockPair(buffer: ByteBuffer) = Pair(
        buffer.getBlockPos(),
        buffer.getBlockPos()
    )

    private inline fun readVector3d(buffer: ByteBuffer) = Vector3d(
        buffer.getDouble(),
        buffer.getDouble(),
        buffer.getDouble()
    )

    private inline fun writeDirectionMask(mask: Base6Direction3dMask, buffer: ByteBuffer) { buffer.put(mask.value.toByte()) }

    private inline fun readDirectionMask(buffer: ByteBuffer) = Base6Direction3dMask(buffer.get().toInt())

    //#endregion

    /**
     * Indicates the container of the cell. It is used for implicit connection filtering, during placement:
     * - [Block] can connect to [Block] and [Part]
     * - [Part] can connect to [Part] and [Block]
     * - [Spec] doesn't connect on placement
     * */
    val CELL_LAYER = register<CellLayer>(
        ::writeCellLayer,
        ::readCellLayer,
        1
    )

    /**
     * Standard for all physical machines: the block position where the block entity actually lives (it can either be a dedicated block entity for the machine, or the multipart block entity's position for the part or spec).
     * */
    val BLOCK = register<BlockPos>(
        ::writeBlockPos,
        ::readBlockPos,
        12
    )

    /**
     * Normal of the substrate plane where this device is mounted.
     * Only makes sense for parts and specs (this is a fundamental property).
     * */
    val SUBSTRATE_FACE = register<Direction>(
        ::writeDirection,
        ::readDirection,
        1
    )

    /**
     * Conventional facing direction inside a local frame of the mounting plane.
     * */
    val CONVENTIONAL_FACING = register<FacingDirection>(
        ::writeFacingDirection,
        ::readFacingDirection,
        1
    )

    /**
     * Directions in the world frame where the game object extends connections to other game objects.
     * */
    val PIPELIKE_MASK = register<Base6Direction3dMask>(
        ::writeDirectionMask,
        ::readDirectionMask,
        1
    )

    /**
     * The exact in-world position of a spec.
     * */
    val SPEC_MOUNTING_POINT = register<Vector3d>(
        ::writeVector3d,
        ::readVector3d,
        24
    )

    /**
     * The unique (in its spec container) ID of a spec.
     * */
    val SPEC_PLACEMENT_ID = register<Int>(
        ::writeInt,
        ::readInt,
        4
    )

    /**
     * The unique (in the entire level) sorted pair of grid terminals. Used by the grid connection cells which don't have a physical location.
     * */
    val GRID_ENDPOINT_PAIR = register<SortedUUIDPair>(
        SortedUUIDPair::write,
        SortedUUIDPair::read,
        32
    )

    /**
     * Special, not-yet-used but implemented locator that the cell environment calculation takes into consideration.
     * If the cell's machine spans more than one block, the cell environment evaluation will take averages (e.g. if the block is on the border between two biomes).
     * */
    val BLOCK_RANGE = register<Pair<BlockPos, BlockPos>>(
        ::writeBlockPair,
        ::readBlockPair,
        24
    )
}

fun Locator.hasLocalFrame() = this.has(Locators.SUBSTRATE_FACE) && this.has(Locators.CONVENTIONAL_FACING)

fun Locator.findDirActualPlanarOrNull(other: Locator): Base6Direction3d? {
    val a = this.get(Locators.BLOCK) ?: return null
    val b = this.get(Locators.CONVENTIONAL_FACING) ?: return null
    val c = this.get(Locators.SUBSTRATE_FACE) ?: return null
    val d = other.get(Locators.BLOCK) ?: return null
    val dir = a.directionTo(d) ?: return null

    return Base6Direction3d.fromForwardUp(b, c, dir)
}

fun Locator.findDirActualSpecificFrameOrNull(other: Locator): Base6Direction3d? {
    return getPartConnectionOrNull(this, other)?.directionSpecificFrame
}

fun Locator.findDirActualPlanar(other: Locator): Base6Direction3d {
    return this.findDirActualPlanarOrNull(other) ?: error("Failed to get relative rotation direction")
}

fun Locator.findDirActualPart(other: Locator): Base6Direction3d {
    return this.findDirActualSpecificFrameOrNull(other) ?: error("Failed to get relative rotation direction (part)")
}

fun interface PoleMap {
    fun evaluateOrNull(sourceCell: Cell, targetCell: Cell): Pole?
}

fun interface MonopoleMap {
    fun evaluates(sourceCell: Cell, targetCell: Cell): Boolean
}

// Inlined:

fun anyEvaluates(source: Cell, target: Cell, a: MonopoleMap, b: MonopoleMap, c: MonopoleMap) =
    if(ELN2_DEBUG) {
        val rA = a.evaluates(source, target)
        val rB = b.evaluates(source, target)
        val rC = c.evaluates(source, target)
        var i = 0

        if(rA) { i++ }
        if(rB) { i++ }
        if(rC) { i++ }

        check(i <= 1) {
            DEBUGGER_BREAK("Inconsistent triple monopolar map")
        }

        i > 0
    }
    else {
        a.evaluates(source, target) ||
        b.evaluates(source, target) ||
        c.evaluates(source, target)
    }

fun PoleMap.evaluate(sourceCell: Cell, targetCell: Cell): Pole =
    checkNotNull(evaluateOrNull(sourceCell, targetCell)) {
        "Unhandled pole map direction $sourceCell $targetCell $this"
    }

/**
 * Creates a [PoleMap] that maps [plusDir] to plus and [minusDir] to minus.
 * These directions are in the observer's frame. Fused positions are not allowed (like with multiparts)
 * This means that, from the object's perspective, [Pole.Positive] is returned when the other object is towards [plusDir], and [Pole.Negative] is returned when the target is towards [minusDir].
 * */
fun directionPoleMapPlanar(plusDir: Base6Direction3d = Base6Direction3d.Front, minusDir: Base6Direction3d = Base6Direction3d.Back) =
    PoleMap { c1, c2 ->
        when (c1.locator.findDirActualPlanarOrNull(c2.locator)) {
            plusDir -> Pole.Positive
            minusDir -> Pole.Negative
            else -> null
        }
    }

/**
 * Creates a [PoleMap] that maps [dir] to [pole]. Used for devices that have only one port (e.g. [pole] is the input for a radiator).
 * */
fun directionMonopolarMapPlanar(dir: Base6Direction3d, pole: Pole) = PoleMap { c1, c2 ->
    when (c1.locator.findDirActualPlanarOrNull(c2.locator)) {
        dir -> pole
        else -> null
    }
}

fun monopolarMapPlanar(dir: Base6Direction3d) = MonopoleMap { c1, c2 ->
    when(c1.locator.findDirActualPlanarOrNull(c2.locator)) {
        dir -> true
        else -> false
    }
}

fun monopolarMapPlanar(dirs: Base6Direction3dMask) = MonopoleMap { c1, c2 ->
    val dir = c1.locator.findDirActualPlanarOrNull(c2.locator)
        ?: return@MonopoleMap false

    return@MonopoleMap dirs.has(dir)
}

fun nullPolarMap() = PoleMap { a, b -> null }

fun nullMonopoleMap() = MonopoleMap { a, b -> false }

/**
 * Creates a [PoleMap] that maps [plusDir] to plus and [minusDir] to minus.
 * These directions are in the observer's frame.
 * This means that, from the object's perspective, [Pole.Positive] is returned when the other object is towards [plusDir], and [Pole.Negative] is returned when the target is towards [minusDir].
 * */
fun directionPoleMapPart(plusDir: Base6Direction3d = Base6Direction3d.Front, minusDir: Base6Direction3d = Base6Direction3d.Back) =
    PoleMap { c1, c2 ->
        when (c1.locator.findDirActualSpecificFrameOrNull(c2.locator)) {
            plusDir -> Pole.Positive
            minusDir -> Pole.Negative
            else -> null
        }
    }
