package org.eln2.mc

annotation class ClientOnly
annotation class ServerOnly
annotation class OnServerThread
annotation class OnClientThread

/**
 * Indicates that the code element is accessed from multiple threads.
 * */
annotation class CrossThreadAccess
annotation class RaceCondition
