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
import org.apache.logging.log4j.LogManager
import org.eln2.mc.common.Networking
import org.eln2.mc.common.blocks.CellBlockBase
import org.eln2.mc.common.packets.clientToServer.CircuitExplorerOpenPacket
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
            LogManager.getLogger().info("handle!")
            handleOpenCircuitPlotter()
        }
        else
        {
            LogManager.getLogger().info("no, is down: ${OPEN_CIRCUIT_PLOTTER.isDown} action: ${event.action}")
        }
    }

    private fun handleOpenCircuitPlotter(){
        val player = Minecraft.getInstance().cameraEntity as LivingEntity

        val pickResult = player.pick(100.0, 0f, false)

        if(pickResult.type != HitResult.Type.BLOCK){
            LogManager.getLogger().info("did not hit")
            return
        }

        val blockHit = pickResult as BlockHitResult
        if(player.level.getBlockState(blockHit.blockPos).block !is CellBlockBase){
            LogManager.getLogger().info("it is not the block we're looking for")
            return
        }

        Networking.sendToServer(CircuitExplorerOpenPacket())

        //val cells = ArrayList(graph.cells.map { CellInfo(it.id.toString(), "n/a", it.pos) })

    }
}
