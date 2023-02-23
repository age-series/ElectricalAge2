package org.eln2.mc.common.content

import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.Material
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.Mathematics.vec3One
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.common.events.IEvent
import org.eln2.mc.common.events.IEventListener
import org.eln2.mc.common.events.IEventQueueAccess
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.common.space.DirectionMask.Companion.perpendicular
import org.eln2.mc.extensions.BlockPosExtensions.plus
import org.eln2.mc.integration.waila.TooltipBuilder

interface IGhostLightHandle {
    fun update(brightness: Int)
    fun destroy()
}

class GhostLightBlock : AirBlock(Properties.of(Material.AIR).lightLevel { it.getValue(brightnessProperty) }) {
    private class LightGrid(val level: Level) {
        private class Cell(val level: Level, val pos: BlockPos, val grid: LightGrid) {
            private fun handleBrightnessChanged(handle: Handle){
                refreshGhost()
            }

            private fun handleDestroyed(handle: Handle) {
                handles.remove(handle)

                if(handles.size == 0) {
                    clearFromLevel(level, pos)
                    grid.onCellCleared(pos)
                }
            }

            private val handles = ArrayList<Handle>()

            fun createHandle(): IGhostLightHandle {
                return Handle(this).also { handles.add(it) }
            }

            fun refreshGhost(){
                LOGGER.info("Refresh ghost")

                val maximalBrightness = handles.maxOf { it.trackedBrightness }

                setInLevel(level, pos, maximalBrightness)
            }

            private class Handle(val cell: Cell): IGhostLightHandle {
                var trackedBrightness: Int = 0

                var destroyed = false

                override fun update(brightness: Int) {
                    if(destroyed){
                        error("Cannot set brightness, handle destroyed!")
                    }

                    if(brightness == trackedBrightness){
                        return
                    }

                    trackedBrightness = brightness

                    cell.handleBrightnessChanged(this)
                }

                override fun destroy() {
                    if(!destroyed){
                        cell.handleDestroyed(this)
                    }
                }
            }
        }

        private val cells = HashMap<BlockPos, Cell>()

        fun onCellCleared(pos: BlockPos) {
            cells.remove(pos)
        }

        fun createHandle(pos: BlockPos): IGhostLightHandle {
            return cells.computeIfAbsent(pos) { Cell(level, pos, this) }.createHandle()
        }

        fun refreshGhost(pos: BlockPos){
            cells[pos]?.refreshGhost()
        }
    }

    companion object {
        private val block get() = Content.LIGHT_GHOST_BLOCK.block.get()

        private val brightnessProperty: IntegerProperty = IntegerProperty.create("brightness", 0, 15)

        private val grids = HashMap<Level, LightGrid>()

        private fun setInLevel(level: Level, pos: BlockPos, brightness: Int): Boolean {
            val previousBlockState = level.getBlockState(pos)

            if(previousBlockState.block != Blocks.AIR && previousBlockState.block != block){
                LOGGER.info("Could not place, existing block there: $previousBlockState")
                return false
            }

            if(previousBlockState.block != block || previousBlockState.getValue(brightnessProperty) != brightness){
                level.setBlockAndUpdate(pos, block.defaultBlockState().setValue(brightnessProperty, brightness))
                LOGGER.info("Placed")
                return true
            }

            return false
        }

        private fun clearFromLevel(level: Level, pos: BlockPos): Boolean {
            val state = level.getBlockState(pos)

            if(state.block != block) {
                LOGGER.error("Cannot remove: not ghost light")

                return false
            }

            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())

            return true
        }

        private fun getGrid(level: Level): LightGrid {
            return grids.computeIfAbsent(level){ LightGrid(level) }
        }

        fun createHandle(level: Level, pos: BlockPos): IGhostLightHandle {
            return getGrid(level).createHandle(pos)
        }

        fun refreshGhost(level: Level, pos: BlockPos){
            grids[level]?.refreshGhost(pos)
        }
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        pBuilder.add(brightnessProperty)
    }
}

fun interface ILightBrightnessFunction {
    fun calculateBrightness(power: Double): Double
}

data class LightModel(
    val brightnessFunction: ILightBrightnessFunction,
    val resistance: Double
)

object LightModels {
    fun test(): LightModel{
        return LightModel({it / 100.0}, 10.0)
    }
}

data class LightChangeEvent(val brightness: Int): IEvent

class LightCell(pos: CellPos, id: ResourceLocation, val model: LightModel) : CellBase(pos, id) {
    private var trackedBrightness: Int = 0

    private var receiver: IEventQueueAccess? = null

    fun subscribeEvents(access: IEventQueueAccess) {
        receiver = access
    }

    fun unsubscribeEvents() {
        receiver = null
    }

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(ResistorObject().also { it.resistance = model.resistance })
    }

    override fun onGraphChanged() {
        graph.addSubscriber(this::simulationTick)
    }

    override fun onRemoving() {
        graph.removeSubscriber(this::simulationTick)
    }

    private fun simulationTick(elapsed: Double){
        val actualBrightness = (model
            .brightnessFunction
            .calculateBrightness(resistorObject.power) * 15.0)
            .toInt()
            .coerceIn(0, 15)

        val receiver = this.receiver

        if(trackedBrightness == actualBrightness || receiver == null) {
            // Cannot track the value.

            return
        }

        trackedBrightness = actualBrightness

        receiver.enqueueEvent(LightChangeEvent(actualBrightness))
    }

    private val resistorObject get() = electricalObject as ResistorObject

    override fun appendBody(builder: TooltipBuilder, config: IPluginConfig?) {
        super.appendBody(builder, config)
        builder.text("Brightness", trackedBrightness)
    }
}

class LightPart(id: ResourceLocation, placementContext: PartPlacementContext, cellProvider: CellProvider):
    CellPart(id, placementContext, cellProvider),
    IEventListener {

    override val baseSize = vec3One()

    private var lights = ArrayList<IGhostLightHandle>()

    override fun createRenderer(): IPartRenderer {
        return BasicPartRenderer(this, PartialModels.WIRE_CROSSING_FULL)
    }

    private fun cleanup() {
        lights.forEach { it.destroy() }
        lights.clear()
    }

    override fun onCellAcquired() {
        EventScheduler.register(this)

        registerHandlers()
        createLights()

        lightCell.subscribeEvents(EventScheduler.getEventAccess(this))
    }

    private fun registerHandlers() {
        val manager = EventScheduler.getManager(this)

        manager.registerHandler(this::onLightUpdate)
    }

    private fun createLights(){
        val normal = placementContext.face

        (perpendicular(normal) + normal).directionList
        .map { placementContext.pos + it }
        .map { GhostLightBlock.createHandle(placementContext.level, it) }
        .forEach { lights.add(it) }
    }

    private fun onLightUpdate(event: LightChangeEvent) {
        LOGGER.info("Light update event: ${event.brightness}")

        lights.forEach {
            it.update(event.brightness)
        }
    }

    override fun onCellReleased() {
        lightCell.unsubscribeEvents()
        EventScheduler.remove(this)
    }

    override fun onRemoved() {
        super.onRemoved()

        cleanup()
    }

    private val lightCell get() = cell as LightCell
}
