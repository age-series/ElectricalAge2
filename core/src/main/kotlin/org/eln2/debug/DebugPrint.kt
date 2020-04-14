package org.eln2.debug

var DEBUG = System.getenv("MODS_ELN_DEBUG") != null || System.getProperty("mods.eln.debug") != null

fun dprintln() = if (DEBUG) println() else Unit
fun dprintln(a: Any?) = if (DEBUG) println(a) else Unit
fun dprint(a: Any?) = if (DEBUG) print(a) else Unit
