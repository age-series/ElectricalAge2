package org.eln2.mc.client.gui.library

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextComponent
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.withPose
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.translate
import org.eln2.mc.client.gui.library.extensions.PoseStackExtensions.scale
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.client.gui.library.extensions.WindowExtensions.withButton
import org.eln2.mc.client.gui.library.extensions.WindowExtensions.withCheckBox
import org.eln2.mc.client.gui.library.extensions.WindowExtensions.withLabel
import org.eln2.mc.client.gui.library.extensions.WindowExtensions.withSkin
import java.util.*
import kotlin.math.max

open class Viewport(
    title : Component = TextComponent(""),
    var usePan : Boolean = true,
    var useZoom : Boolean = true
    ) : Screen(title) {

    var lastMouse : Point2I = Point2I.zero
        get
        private set

    class WindowInfo(val window : AbstractWindow, val zIndex : Int, val level : LinkedList<AbstractWindow>)

    val mcFont: Font get() = Minecraft.getInstance().font
    val size get() = Rect4I(0, 0, width, height)

    var translation = Point2F.zero
    var scale = 1.0

    private val windows = HashMap<Int, LinkedList<AbstractWindow>>()
    private var clickingWindow : WindowInfo? = null

    fun addWindow(window: AbstractWindow){
        val depth = if (windows.isNotEmpty()) windows.keys.last() + 1 else 0

        val newList = LinkedList<AbstractWindow>()
        newList.add(window)

        windows[depth] = newList
    }

    override fun init() {
        super.init()
    }

    override fun render(poseStack: PoseStack, pMouseX: Int, pMouseY: Int, partialTime: Float) {
        if(windows.isEmpty()){
            return
        }

        val mouse = Point2I(pMouseX, pMouseY)

        lastMouse = mouse

        val hoveredWindow = getHoveredWindow(mouse)
        val focusedWindow = getFocusedWindow()

        poseStack.withPose {
            poseStack.scale(scale.toFloat())
            poseStack.translate(translation)

            windows.values.forEachIndexed { index, level ->
                level.forEach { window ->
                    poseStack.withPose {
                        val location = window.globalPos.toF()
                        poseStack.translate(location)

                        val hovered = hoveredWindow?.window === window
                        val focused = focusedWindow.window === window

                        val mouseAtWindow = mouseRelativeTo(mouse, window)
                        window.render(poseStack, location, mouseAtWindow, hovered, focused, partialTime)
                    }
                }
            }
        }
    }

    override fun mouseReleased(pMouseX: Double, pMouseY: Double, pButton: Int): Boolean {
        processMouseClick(
            Point2I(pMouseX.toInt(), pMouseY.toInt()),
            MouseInfo(MouseButton(pButton), MouseAction.Release))
        return true
    }

    override fun mouseClicked(pMouseX: Double, pMouseY: Double, pButton: Int): Boolean {
        processMouseClick(
            Point2I(pMouseX.toInt(), pMouseY.toInt()),
            MouseInfo(MouseButton(pButton), MouseAction.Click))
        return true
    }

    override fun mouseScrolled(pMouseX: Double, pMouseY: Double, pDelta: Double): Boolean {
        if(hasShiftDown() && useZoom){
            if(pDelta < 0){
                scale = max(0.4, scale + pDelta.toFloat() / 20f)
            }
            else {
                scale += pDelta.toFloat() / 20f
            }

            windows.values.forEach{ it.forEach{it.scale = scale.toFloat()}}
        }

        return true
    }

    private fun getHoveredWindowAndFocus(mouse: Point2I) : WindowInfo?{
        if(windows.isEmpty()){
            return null
        }

        val hoveredWindow = getHoveredWindow(mouse) ?: return null

        val focusedWindow = getFocusedWindow()

        if(hoveredWindow.window !== focusedWindow.window){
            moveWindowToTop(hoveredWindow)
        }

        return hoveredWindow
    }

    private fun processMouseClick(mouse: Point2I, event : MouseInfo){
        if(event.action == MouseAction.Release){
            if(clickingWindow == null){
                return
            }

            clickingWindow!!.window.onMouseClicked(mouseRelativeTo(mouse, clickingWindow!!.window), event)
            clickingWindow = null

            return
        }

        val window = getHoveredWindowAndFocus(mouse) ?: return

        clickingWindow = window

        window.window.onMouseClicked(mouseRelativeTo(mouse, window.window), event)
    }

    override fun mouseDragged(pMouseX: Double, pMouseY: Double, pButton: Int, pDragX: Double, pDragY: Double): Boolean {
        if(hasShiftDown() && usePan){
            // pan

            translation += Point2F(pDragX.toFloat() / scale.toFloat(), pDragY.toFloat() / scale.toFloat())
            return true
        }

        if(clickingWindow == null){
            LOGGER.info("no window is being dragged")
            return true
        }

        val mouse = Point2I(pMouseX.toInt(), pMouseY.toInt())
        lastMouse = mouse
        val delta = Point2F(pDragX.toFloat() / scale.toFloat(), pDragY.toFloat() / scale.toFloat())
        val button = MouseButton(pButton)
        val mouseAtWindow = mouseRelativeTo(mouse, clickingWindow!!.window)
        clickingWindow!!.window.onMouseDragged(mouseAtWindow, button, delta)
        LOGGER.info("dragged!")

        return true
    }

    fun mouseRelativeTo(mouse : Point2I, window : AbstractWindow) : Point2I{
        return mouse - scaleSurfaceAndTranslate(window.initialSurface).pos.toI()
    }

    fun getHoveredWindow(mouse : Point2I) : WindowInfo? {
        for(levelIndex in windows.keys.reversed()) {
            val level = windows[levelIndex]!!
            level.forEach{window ->
                val surface = scaleSurfaceAndTranslate(window.initialSurface)
                if(surface.hasPoint(mouse.toF())){
                    return WindowInfo(window, levelIndex, level)
                }
            }
        }

        return null
    }

    fun scaleSurfaceAndTranslate(surface : Rect4I) : Rect4F{
        val scaled = surface.toF().scale(scale.toFloat())
        return Rect4F(scaled.pos + Point2F((translation.x * scale).toFloat(), (translation.y * scale).toFloat()), scaled.size)
    }

    private fun getFocusedWindow() : WindowInfo{
        return WindowInfo(windows.values.last()[0], windows.keys.last(), windows.values.last())
    }

    private fun moveWindowToTop(info : WindowInfo){
        info.level.remove(info.window)
        addWindow(info.window)
    }
}
