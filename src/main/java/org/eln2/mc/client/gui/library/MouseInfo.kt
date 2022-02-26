package org.eln2.mc.client.gui.library

class MouseButton(val code : Int) {
    companion object{
        val left = MouseButton(0)
        val right = MouseButton(1)
    }

    override fun equals(other: Any?): Boolean {
        return other is MouseButton && other.code == code
    }

    override fun hashCode(): Int {
        return code
    }
}

enum class MouseAction{
    Click,
    Release
}

class MouseInfo(val button: MouseButton, val action: MouseAction)
