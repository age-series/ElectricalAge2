package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
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
import org.eln2.mc.mathematics.Mathematics.bbVec
import org.eln2.mc.annotations.ClientOnly
import org.eln2.mc.annotations.ServerOnly
import org.eln2.mc.client.render.MultipartBlockEntityInstance
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.animations.colors.ColorInterpolators
import org.eln2.mc.client.render.animations.colors.Utilities.colorF
import org.eln2.mc.client.render.foundation.PartRendererTransforms.applyBlockBenchTransform
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.SubscriberPhase
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.events.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.extensions.NbtExtensions.useSubTag
import org.eln2.mc.extensions.NbtExtensions.withSubTag
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

        val brightnessProperty: IntegerProperty = IntegerProperty.create("brightness", 0, 15)

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

    var rawBrightness: Double = 0.0
        private set

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
        graph.subscribers.addPreInstantaneous(this::simulationTick)
    }

    override fun onRemoving() {
        graph.subscribers.removeSubscriber(this::simulationTick)
    }

    private fun simulationTick(elapsed: Double, phase: SubscriberPhase){
        rawBrightness = model.brightnessFunction.calculateBrightness(resistorObject.power)

        val actualBrightness =
            (rawBrightness * 15.0)
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

class LightRenderer(
    private val part: LightPart,
    private val cage: PartialModel,
    private val emitter: PartialModel) : IPartRenderer {

    companion object {
        val interpolator = ColorInterpolators.rgbLinear()
        val COLD_TINT = colorF(1f, 1f, 1f, 1f)
        val WARM_TINT = Color(254, 196, 127, 255)
    }

    private val brightnessUpdate = AtomicUpdate<Double>()

    fun updateBrightness(newValue: Double){
        brightnessUpdate.setLatest(newValue)
    }

    var yRotation = 0f
    var downOffset = 0.0

    private var cageInstance: ModelData? = null
    private var emitterInstance: ModelData? = null

    private lateinit var multipart: MultipartBlockEntityInstance

    override fun setupRendering(multipart: MultipartBlockEntityInstance) {
        this.multipart = multipart

        buildInstance()
    }

    private fun create(model: PartialModel): ModelData{
        return multipart.materialManager
            .defaultSolid()
            .material(Materials.TRANSFORMED)
            .getModel(model)
            .createInstance()
            .loadIdentity()
            .applyBlockBenchTransform(part, downOffset, yRotation)
    }

    private fun buildInstance() {
        if (!this::multipart.isInitialized) {
            error("Multipart not initialized!")
        }

        cageInstance?.delete()
        emitterInstance?.delete()

        cageInstance = create(cage)
        emitterInstance = create(emitter)

        multipart.relightPart(part)
    }

    override fun relightModels(): List<FlatLit<*>> {
        val list = ArrayList<ModelData>(2)

        cageInstance?.also { list.add(it) }
        emitterInstance?.also { list.add(it) }

        return list
    }

    override fun remove() {
        cageInstance?.delete()
        emitterInstance?.delete()
    }

    override fun beginFrame() {
        val model = emitterInstance
            ?: return

        brightnessUpdate.consume {
            val brightness = it.coerceIn(0.0, 1.0).toFloat()

            model.setColor(interpolator.interpolate(COLD_TINT, WARM_TINT, brightness))
        }
    }
}

class LightPart(id: ResourceLocation, placementContext: PartPlacementContext, cellProvider: CellProvider):
    CellPart(id, placementContext, cellProvider),
    IEventListener {

    companion object {
        private const val RAW_BRIGHTNESS = "rawBrightness"
        private const val CLIENT_DATA = "clientData"
    }

    override val baseSize = bbVec(8.0, 1.0 + 2.302, 5.0)

    private var lights = ArrayList<IGhostLightHandle>()

    override fun createRenderer(): IPartRenderer {
        return LightRenderer(
            this,
            PartialModels.SMALL_WALL_LAMP_CAGE,
            PartialModels.SMALL_WALL_LAMP_EMITTER)
            .also { it.downOffset = baseSize.y / 2.0 }
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
       /* val normal = placementContext.face

        (perpendicular(normal) + normal).directionList
        .map { placementContext.pos + it }
        .map { GhostLightBlock.createHandle(placementContext.level, it) }
        .forEach { lights.add(it) }*/
    }

    private fun onLightUpdate(event: LightChangeEvent) {
        LOGGER.info("Light update event: ${event.brightness}")

        lights.forEach {
            it.update(event.brightness)
        }

        updateBrightness(event.brightness)

        syncChanges()
    }

    @ServerOnly
    override fun getSyncTag(): CompoundTag {
        return CompoundTag().withSubTag(CLIENT_DATA, packClientData())
    }

    @ClientOnly
    override fun handleSyncTag(tag: CompoundTag) {
        tag.useSubTag(CLIENT_DATA, this::unpackClientData)
    }

    override fun getSaveTag(): CompoundTag? {
        return super.getSaveTag()?.withSubTag(CLIENT_DATA, packClientData())
    }

    override fun loadFromTag(tag: CompoundTag) {
        super.loadFromTag(tag)

        if(placementContext.level.isClientSide){
            tag.useSubTag(CLIENT_DATA, this::unpackClientData)
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

    private fun unpackClientData(tag: CompoundTag){
        lightRenderer.updateBrightness(tag.getDouble(RAW_BRIGHTNESS))
    }

    private fun packClientData(): CompoundTag{
        return CompoundTag().also {
            it.putDouble(RAW_BRIGHTNESS, lightCell.rawBrightness)
        }
    }

    private val lightCell get() = cell as LightCell
    private val lightRenderer get() = renderer as LightRenderer
}
