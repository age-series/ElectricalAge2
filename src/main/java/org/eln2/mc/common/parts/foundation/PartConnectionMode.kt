package org.eln2.mc.common.parts.foundation

import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.space.*
import org.eln2.mc.extensions.directionTo
import org.eln2.mc.extensions.minus
import org.eln2.mc.extensions.plus

/**
 * A connection mode represents the way two cells may be connected.
 * */
enum class PartConnectionMode {
    /**
     * Planar connections are connections between units placed on the same plane, in adjacent containers.
     * */
    Planar,
    /**
     * Inner connections are connections between units placed on perpendicular faces in the same container.
     * */
    Inner,
    /**
     * Wrapped connections are connections between units placed on perpendicular faces of the same block.
     * Akin to a connection wrapping around the corner of the substrate block.
     * */
    Wrapped,
    /**
     * The connection mode could not be identified.
     * */
    Unknown
}

data class PartConnectionInfo(
    val mode: PartConnectionMode,
    val actualDirActualPlr: RelativeDirection
)

fun solvePartConnection(actualCell: CellBase, remoteCell: CellBase): PartConnectionInfo {
    val actualPosWorld = actualCell.posDescr.requireLocator<R3, BlockPosLocator>().pos
    val remotePosWorld = remoteCell.posDescr.requireLocator<R3, BlockPosLocator>().pos
    val actualFaceWorld = actualCell.posDescr.requireLocator<SO3, BlockFaceLocator>().faceWorld
    val remoteFaceWorld = remoteCell.posDescr.requireLocator<SO3, BlockFaceLocator>().faceWorld

    val mode: PartConnectionMode

    val dirGlobal = if(actualPosWorld == remotePosWorld) {
        if(actualFaceWorld == remoteFaceWorld) {
            error("Invalid configuration") // Cannot have multiple parts in same face, something is super wrong up the chain
        }

        // The only mode that uses this is the Inner mode.
        // But, if we find that the two directions are not perpendicular, this is not Inner, and as such, it is Unknown:
        if(actualFaceWorld == remoteFaceWorld.opposite) {
            // This is unknown. Inner connections happen between parts on perpendicular faces:
            mode = PartConnectionMode.Unknown
            actualFaceWorld
        }
        else {
            // This is Inner:
            mode = PartConnectionMode.Inner
            remoteFaceWorld.opposite
        }
    }
    else {
        // They are planar if the normals match up.
        // Wrapped parts have perpendicular normals.
        if(actualFaceWorld == remoteFaceWorld) {
            val txActualTarget = actualPosWorld.directionTo(remotePosWorld)

            if(txActualTarget == null) {
                // They are not in-plane, which means Unknown
                mode = PartConnectionMode.Unknown
                actualFaceWorld
            }
            else {
                // This is planar:
                mode = PartConnectionMode.Planar
                txActualTarget
            }
        }
        else {
            // Scan for Wrapped connections. If those do not match, then Unknown.
            val solution = DirectionMask
                .perpendicular(actualFaceWorld)
                .directionList
                .firstOrNull { (actualPosWorld + it - actualFaceWorld) == remotePosWorld }

            if(solution != null) {
                // Solution was found, this is wrapped:
                mode = PartConnectionMode.Wrapped
                solution
            }
            else {
                mode = PartConnectionMode.Unknown
                actualFaceWorld
            }
        }
    }

    return PartConnectionInfo(
        mode,
        RelativeDirection.fromForwardUp(
            actualCell.posDescr.requireLocator<SO3, IdentityDirectionLocator>().forwardWorld,
            actualFaceWorld,
            dirGlobal
        )
    )
}
