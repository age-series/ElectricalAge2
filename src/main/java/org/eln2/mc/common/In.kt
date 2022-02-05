package org.eln2.mc.common

enum class Side {
    // can be a single player server or a physical server
    LogicalServer,

    // the game client, can be single-player or multiplayer
    PhysicalClient,

    //  a hosted server.
    PhysicalServer
}

// used to mark the side that is using a resource
annotation class In(val specifier : Side)
