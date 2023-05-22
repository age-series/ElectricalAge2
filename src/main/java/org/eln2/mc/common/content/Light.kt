package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.mathematics.bbVec
import org.eln2.mc.annotations.ClientOnly
import org.eln2.mc.annotations.ServerOnly
import org.eln2.mc.client.render.MultipartBlockEntityInstance
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.animations.colors.ColorInterpolators
import org.eln2.mc.client.render.animations.colors.Utilities.colorF
import org.eln2.mc.client.render.foundation.PartRendererTransforms.applyBlockBenchTransform
import org.eln2.mc.common.blocks.foundation.GhostLight
import org.eln2.mc.common.cells.foundation.CellBase
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.SubscriberPhase
import org.eln2.mc.common.cells.foundation.behaviors.withStandardBehavior
import org.eln2.mc.common.cells.foundation.objects.SimulationObjectSet
import org.eln2.mc.common.events.*
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.IPartRenderer
import org.eln2.mc.common.parts.foundation.PartPlacementContext
import org.eln2.mc.common.space.DirectionMask
import org.eln2.mc.common.space.RelativeDirection
import org.eln2.mc.common.space.withDirectionActualRule
import org.eln2.mc.extensions.useSubTag
import org.eln2.mc.extensions.withSubTag
import org.eln2.mc.integration.waila.TooltipBuilder

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

class LightCell(
    pos: CellPos,
    id: ResourceLocation,
    val model: LightModel,
    val dir1: RelativeDirection = RelativeDirection.Left,
    val dir2: RelativeDirection = RelativeDirection.Right
) : CellBase(pos, id) {
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

    init {
        behaviors.withStandardBehavior(this, { resistorObject.power }, { thermal.body })
        ruleSet.withDirectionActualRule(DirectionMask.ofRelatives(dir1, dir2))
    }

    override fun createObjectSet(): SimulationObjectSet {
        return SimulationObjectSet(
            ResistorObject(this, dir1, dir2).also { it.resistance = model.resistance },
            ThermalWireObject(this)
        )
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
    private val thermal get() = thermalObject as ThermalWireObject

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

    override fun isSetupWith(multipartBlockEntityInstance: MultipartBlockEntityInstance): Boolean {
        return this::multipart.isInitialized && this.multipart == multipartBlockEntityInstance
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

    private var lights = ArrayList<GhostLight>()

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
