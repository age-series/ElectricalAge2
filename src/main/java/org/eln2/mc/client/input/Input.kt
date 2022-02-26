package org.eln2.mc.client.input
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.ClientRegistry
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.settings.KeyConflictContext
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.mc.client.gui.library.*
import org.eln2.mc.client.gui.library.nodeGraph.NodeGraphWindow
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.network.clientToServer.CircuitExplorerOpenPacket
import org.lwjgl.glfw.GLFW

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object Input {
    private val OPEN_CIRCUIT_PLOTTER = KeyMapping(
        "Open circuit plotter",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_P,
        "Electrical Age 2")

    init {
        ClientRegistry.registerKeyBinding(OPEN_CIRCUIT_PLOTTER)
    }

    @SubscribeEvent
    fun inputEvent(event: InputEvent.KeyInputEvent){
        if(OPEN_CIRCUIT_PLOTTER.isDown && event.action == GLFW.GLFW_PRESS){
            //handleOpenCircuitPlotter()

            val mc = Minecraft.getInstance()

            if(mc.screen is Viewport){
                return
            }

            val viewport = Viewport()
            viewport.addWindow(NodeGraphWindow(Rect4I(10, 10, 100, 100), viewport))
            viewport.addWindow(NodeGraphWindow(Rect4I(10, 10, 100, 100), viewport))
            viewport.addWindow(NodeGraphWindow(Rect4I(10, 10, 100, 100), viewport))
            viewport.addWindow(NodeGraphWindow(Rect4I(10, 10, 100, 100), viewport))
            viewport.addWindow(NodeGraphWindow(Rect4I(10, 10, 100, 100), viewport))

            mc.setScreen(viewport)
        }
    }

    private fun handleOpenCircuitPlotter(){
        val player = Minecraft.getInstance().cameraEntity as LivingEntity
        val pickResult = player.pick(100.0, 0f, false)

        if(pickResult.type != HitResult.Type.BLOCK)
            return

        val blockHit = pickResult as BlockHitResult
        if(player.level.getBlockState(blockHit.blockPos).block !is CellBlockBase)
            return

        Networking.sendToServer(CircuitExplorerOpenPacket())
    }
}
