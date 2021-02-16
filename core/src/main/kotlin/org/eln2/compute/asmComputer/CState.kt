package org.eln2.compute.asmComputer

/**
 * States of our computer
 */
enum class CState {
    // The computer is running
    Running,

    // The computer is stopped on an instruction (paused?)
    Stopped,

    // An invalid instruction was passed.
    Errored,

    // The computer is waiting for data
    Data,
}
