package org.eln2.mc.common.parts.foundation

/**
 * A connection mode represents the way two cells may be connected.
 * */
enum class ConnectionMode {
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
    Wrapped
}
