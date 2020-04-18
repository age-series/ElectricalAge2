package org.eln2.debug

/**
 * Debug_Flags: List of acceptable flags that will enable the DEBUG flag from the system environment.
 */
val DEBUG_FLAGS = listOf("mods.eln.debug", "eln2.core.debug", "eln2.all.debug")

/**
 * Debug: If this is true, debug prints will print out
 */
var DEBUG =
	(
		System.getenv().filterKeys {
			key -> DEBUG_FLAGS.any {
				it.toLowerCase().replace('.', '_') == key.toLowerCase()
			}
		}
		+
		System.getProperties().filterKeys {
			key -> DEBUG_FLAGS.any { it == key }
		}
	).isNotEmpty()

/**
 * dprintln: Print's empty newline if DEBUG = True
 */
fun dprintln() = if (DEBUG) println() else Unit
/**
 * dprintln: Print's object (toString) followed by newline if DEBUG = True
 * @param debug_string The Object you want to print.
 */
fun dprintln(a: Any?) = if (DEBUG) println(a) else Unit
/**
 * dprint: Print's object (toString) (with no newline)if DEBUG = True
 * @param debug_string The object you want to print
 */
fun dprint(a: Any?) = if (DEBUG) print(a) else Unit
