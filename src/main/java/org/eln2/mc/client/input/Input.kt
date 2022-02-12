package org.eln2.mc.client.input
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraftforge.client.ClientRegistry
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.settings.KeyConflictContext
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.apache.logging.log4j.LogManager
import org.eln2.mc.client.gui.CellInfo
import org.eln2.mc.client.gui.PlotterScreen
import org.eln2.mc.client.overlay.plotter.PlotterOverlay
import org.lwjgl.glfw.GLFW

//String description,
// net.minecraftforge.client.settings.IKeyConflictContext keyConflictContext,
// final InputConstants.Type inputType,
// final int keyCode,
// String category
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
object Input {
    private val OPEN_CIRCUIT_PLOTTER = KeyMapping(
        "Open circuit plotter",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_P,
        "Electrical Age 2")

    fun setup(){
        ClientRegistry.registerKeyBinding(OPEN_CIRCUIT_PLOTTER)

    }

    @SubscribeEvent
    fun inputEvent(event: InputEvent.KeyInputEvent){
        if(OPEN_CIRCUIT_PLOTTER.isDown && event.action == GLFW.GLFW_PRESS){
            LogManager.getLogger().info("handle!")
            handleOpenCircuitPlotter()
        }
        else
        {
            LogManager.getLogger().info("no, is down: ${OPEN_CIRCUIT_PLOTTER.isDown} action: ${event.action}")
        }
    }

    private fun handleOpenCircuitPlotter(){
        if(PlotterOverlay.latestGraph == null){
            return
        }

        val cells = ArrayList(PlotterOverlay.latestGraph!!.cells.map { CellInfo(it.id.toString(), "n/a", it.pos) })

        Minecraft.getInstance().setScreen(PlotterScreen(cells))
    }
}
