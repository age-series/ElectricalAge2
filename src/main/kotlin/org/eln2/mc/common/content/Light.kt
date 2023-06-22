package org.eln2.mc.common.content

import com.jozufozu.flywheel.core.Materials
import com.jozufozu.flywheel.core.PartialModel
import com.jozufozu.flywheel.core.materials.FlatLit
import com.jozufozu.flywheel.core.materials.model.ModelData
import com.jozufozu.flywheel.util.Color
import net.minecraft.resources.ResourceLocation
import org.eln2.mc.ClientOnly
import org.eln2.mc.ServerOnly
import org.eln2.mc.Stopwatch
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.MultipartBlockEntityInstance
import org.eln2.mc.client.render.foundation.applyBlockBenchTransform
import org.eln2.mc.client.render.foundation.colorF
import org.eln2.mc.client.render.foundation.colorLerp
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.*
import org.eln2.mc.common.network.serverToClient.with
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.common.parts.foundation.PartRenderer
import org.eln2.mc.data.TooltipField
import org.eln2.mc.data.withDirectionActualRule
import org.eln2.mc.mathematics.DirectionMask
import org.eln2.mc.mathematics.RelativeDir
import org.eln2.mc.mathematics.approxEq
import org.eln2.mc.mathematics.bbVec
import java.nio.ByteBuffer

data class LightModel(
    val brightnessFunction: (Double) -> Double,
    val resistance: Double,
)

data class LightChangeEvent(val brightness: Int) : Event

fun interface RenderBrightnessConsumer {
    fun consume(brightness: Double)
}

class LightCell(
    ci: CellCreateInfo,
    val model: LightModel,
    // Probably doesn't make sense to use maps here:
    dir1: RelativeDir = RelativeDir.Left,
    dir2: RelativeDir = RelativeDir.Right,
) :
    Cell(ci) {
    companion object {
        private const val RENDER_SYNC_INTERVAL = 0.05
        private const val RENDER_EPS = 10e-4
    }

    private var trackedBr: Int = 0

    private var trackedRenderBr: Double = 0.0
    private var renderBrSw = Stopwatch()

    private var serverThreadReceiver: EventQueue? = null
    private var renderBrConsumer: RenderBrightnessConsumer? = null

    @SimObject
    val resistorObj = ResistorObject(this, dir1, dir2).also { it.resistance = model.resistance }

    @SimObject
    val thermalWireObj = ThermalWireObject(this)

    @Behavior
    val behavior = standardBehavior(this, { resistorObj.power }, { thermalWireObj.body })

    var rawBr: Double = 0.0
        private set

    fun bind(serverThreadAccess: EventQueue, renderBrightnessConsumer: RenderBrightnessConsumer) {
        this.serverThreadReceiver = serverThreadAccess
        this.renderBrConsumer = renderBrightnessConsumer
    }

    fun unbind() {
        serverThreadReceiver = null
        renderBrConsumer = null
    }

    override fun subscribe(subs: SubscriberCollection) {
        subs.addPre10(this::simulationTick)
    }

    private fun simulationTick(elapsed: Double, phase: SubscriberPhase) {
        rawBr = model.brightnessFunction(resistorObj.power)

        if (!renderBrSw.total >= RENDER_SYNC_INTERVAL) {
            renderBrSw.resetTotal()

            if (!rawBr.approxEq(trackedRenderBr, RENDER_EPS)) {
                trackedRenderBr = rawBr
                renderBrConsumer?.consume(rawBr)
            }
        }

        val actualBrightness = (rawBr * 15.0).toInt().coerceIn(0, 15)

        val receiver = this.serverThreadReceiver

        if (trackedBr == actualBrightness || receiver == null) {
            return
        }

        trackedBr = actualBrightness

        receiver.enqueue(LightChangeEvent(actualBrightness))
    }

    override val dataNode = super.dataNode.withChild {
        it.data.withField(TooltipField { b ->
            b.text("Minecraft Brightness", trackedBr)
            b.text("Model Brightness", rawBr)
        })
    }

    init {
        ruleSet.withDirectionActualRule(DirectionMask.ofRelatives(dir1, dir2))
    }
}

class LightRenderer(private val part: LightPart, private val cage: PartialModel, private val emitter: PartialModel) :
    PartRenderer {
    companion object {
        val COLD_TINT = colorF(1f, 1f, 1f, 1f)
        val WARM_TINT = Color(254, 196, 127, 255)
    }

    override fun isSetupWith(multipartBlockEntityInstance: MultipartBlockEntityInstance): Boolean {
        return this::multipart.isInitialized && this.multipart == multipartBlockEntityInstance
    }

    private val brightnessUpdate = AtomicUpdate<Double>()

    fun updateBrightness(newValue: Double) {
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

    private fun create(model: PartialModel): ModelData {
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

            model.setColor(colorLerp(COLD_TINT, WARM_TINT, brightness))
        }
    }
}

class LightPart(id: ResourceLocation, placementContext: PartPlacementInfo, cellProvider: CellProvider) :
    CellPart<LightRenderer>(id, placementContext, cellProvider), EventListener {
    override val sizeActual = bbVec(8.0, 1.0 + 2.302, 5.0)

    override fun createRenderer() = LightRenderer(
        this,
        PartialModels.SMALL_WALL_LAMP_CAGE,
        PartialModels.SMALL_WALL_LAMP_EMITTER
    )
        .also { it.downOffset = sizeActual.y / 2.0 }

    @ServerOnly
    override fun onCellAcquired() {
        Scheduler.register(this).registerHandler(this::onLightUpdate)

        lightCell.bind(
            serverThreadAccess = Scheduler.getEventAccess(this),
            renderBrightnessConsumer = ::sendRenderBrBulk
        )
    }

    @ServerOnly
    private fun sendRenderBrBulk(value: Double) {
        val buffer = ByteBuffer.allocate(8) with value
        enqueueBulkMessage(buffer.array())
    }

    @ClientOnly
    override fun handleBulkMessage(msg: ByteArray) {
        val buffer = ByteBuffer.wrap(msg)
        lightRenderer.updateBrightness(buffer.double)
    }

    @ServerOnly
    private fun onLightUpdate(event: LightChangeEvent) {
        updateBrightness(event.brightness)
    }

    override fun onCellReleased() {
        lightCell.unbind()
        Scheduler.remove(this)
    }

    private val lightCell get() = cell as LightCell
    private val lightRenderer get() = renderer
}
