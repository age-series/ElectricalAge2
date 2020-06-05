@file:Suppress("unused")

package org.eln2.debug

/**
 * Debug_Flags: List of acceptable flags that will enable the DEBUG flag from the system environment.
 */
val DEBUG_FLAGS = listOf("mods.eln.debug", "eln2.core.debug", "eln2.all.debug")

/**
 * Debug: If this is true, debug prints will print out.
 *
 * Currently, this is set to true if any of the following Java properties are set (typically using -D on the java command line):
 *
 * - mods.eln.debug
 * - eln2.core.debug
 * - eln2.all.debug
 *
 * However, this list may be out of date; you should check the latest DEBUG_FLAGS for your version to be sure.
 *
 * In circumstances where it may be easier, an environment variable of the same name may also be set, with the periods changed to underscores (e.g. mods_eln_debug). The case doesn't matter; conventionally, IPC environment variables are in ALL_CAPS_SNAKE_CASE (e.g. MODS_ELN_DEBUG).
 *
 * Regardless of whether or not it is a Java property or an environment variable, presence is sufficient to enable debugging. The value does not matter, even if it is empty, but meaning may be imparted on values in the future; for backward-compatibility, consider "all", "1", and "" (the empty string) to enable every debug feature. There is presently no way to force debugging off if any setting would turn it on.
 */
var DEBUG =
    (
        System.getenv().filterKeys { key ->
            DEBUG_FLAGS.any {
                it.toLowerCase().replace('.', '_') == key.toLowerCase()
            }
        } +
            System.getProperties().filterKeys { key ->
                DEBUG_FLAGS.any { it == key }
            }
        ).isNotEmpty()

/**
 * dprintln: Prints empty newline if DEBUG = True
 */
fun dprintln() = if (DEBUG) println() else Unit

/**
 * dprintln: Prints an object (toString) with context to where it was called followed by newline if [DEBUG] = True
 * @param a The Object you want to print.
 */
fun dprintln(a: Any?) = if (DEBUG) {
    val stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
    val caller = stackWalker.walk { it.skip(1).findFirst().get() }
    println("${caller.declaringClass.simpleName}.${caller.methodName}: $a")
} else Unit

/**
 * dprint: Prints an object (toString) with context to where is was called without a newline if [DEBUG] = True
 * @param a The object you want to print
 */
fun dprint(a: Any?) = if (DEBUG) {
    val stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
    val caller = stackWalker.walk { it.skip(1).findFirst().get() }
    print("${caller.declaringClass.simpleName}.${caller.methodName}: $a")
} else Unit

