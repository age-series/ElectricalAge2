package org.eln2.mc.common.content

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.shaders.AbstractUniform
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraftforge.client.event.RegisterShadersEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.network.NetworkHooks
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.SECOND
import org.ageseries.libage.data.classify
import org.ageseries.libage.mathematics.CubicHermiteSplineSegment1d
import org.ageseries.libage.mathematics.FramerateIndependentSmoother2d
import org.ageseries.libage.mathematics.ListSplineSegmentMap
import org.ageseries.libage.mathematics.Spline1d
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.mathematics.geometry.Rotation3d
import org.ageseries.libage.mathematics.geometry.Vector3d
import org.ageseries.libage.mathematics.geometry.Vector4d
import org.ageseries.libage.mathematics.map
import org.ageseries.libage.mathematics.nz
import org.ageseries.libage.mathematics.rounded
import org.ageseries.libage.mathematics.snzi
import org.ageseries.libage.sim.electrical.ElectricalConnectivityMap
import org.ageseries.libage.sim.electrical.ElectricalPin
import org.ageseries.libage.sim.electrical.Resistor
import org.ageseries.libage.utils.Stopwatch
import org.eln2.mc.ClientOnly
import org.eln2.mc.LOG
import org.eln2.mc.MODID
import org.eln2.mc.OnClientThread
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.client.render.foundation.partOffsetTable
import org.eln2.mc.common.blocks.foundation.AdditionalRenderingPart
import org.eln2.mc.common.cells.foundation.Cell
import org.eln2.mc.common.cells.foundation.CellCreateInfo
import org.eln2.mc.common.cells.foundation.CellGraph
import org.eln2.mc.common.cells.foundation.CellProvider
import org.eln2.mc.common.cells.foundation.ElectricalObject
import org.eln2.mc.common.cells.foundation.ElectricalSize
import org.eln2.mc.common.cells.foundation.Node
import org.eln2.mc.common.cells.foundation.SidedElectricalMonoMapped
import org.eln2.mc.common.cells.foundation.SimObject
import org.eln2.mc.common.cells.foundation.SubscriberCollection
import org.eln2.mc.common.cells.foundation.SimulationPhase
import org.eln2.mc.common.cells.foundation.addPost
import org.eln2.mc.common.containers.MyAbstractContainerScreen
import org.eln2.mc.common.content.modules.Eln2Signal
import org.eln2.mc.common.grids.GridConnectionCell
import org.eln2.mc.common.grids.GridNode
import org.eln2.mc.common.grids.GridTerminal
import org.eln2.mc.common.network.serverToClient.ClientSidePacketHandlerBuilder
import org.eln2.mc.common.network.serverToClient.ServerSidePacketHandlerBuilder
import org.eln2.mc.common.parts.foundation.GridCellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.common.parts.foundation.PartUseInfo
import org.eln2.mc.common.parts.foundation.stillValid
import org.eln2.mc.common.parts.foundation.eln2WritePartGuiData
import org.eln2.mc.MonopoleMap
import org.eln2.mc.common.content.OscilloscopePart.GuiMessage
import org.eln2.mc.extensions.getListTag
import org.eln2.mc.extensions.mulPose
import org.eln2.mc.extensions.preserve
import org.eln2.mc.extensions.readFloatArray
import org.eln2.mc.extensions.rotationFast
import org.eln2.mc.extensions.writeDoubleArray
import org.eln2.mc.extensions.writeFloatArray
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.isDigit
import org.eln2.mc.isLetter
import org.eln2.mc.requireIsOnRenderThread
import org.eln2.mc.resource
import java.util.UUID
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

@ClientOnly
class OscilloscopeTexture(val resourceId: ResourceLocation, val columnCount: Int, val channelCount: Int) {
    // No FP format, we hack away...
    private val image = NativeImage(
        NativeImage.Format.RGBA,
        columnCount,
        channelCount,
        true
    )

    val glTex = DynamicTexture(image)

    var writeX = 0
        private set

    var count = 0
        private set

    var closed = false
        private set

    init {
        val manager = Minecraft.getInstance().textureManager

        manager.register(resourceId, glTex)

        for (x in 0 until columnCount) {
            for (y in 0 until channelCount) {
                image.setPixelRGBA(x, y, 0)
            }
        }

        glTex.bind()
        glTex.upload()
    }

    fun writeColumn(column: FloatArray) {
        require(!closed) {
            error("Tried to upload column after texture closed!")
        }

        require(column.size == channelCount) {
            "Column ${column.size} must be as large as the texture's column ($channelCount)"
        }

        val height = channelCount
        var y = 0

        while (y < height) {
            var sample = -column[y]

            if(sample.isNaN()) {
                sample = 0.0f // channel mask
            }

            val v = (sample + 1.0f) * 0.5f

            val s0 = v * 0.999f

            val encG = s0 * 255.0f
            val encB = s0 * 65025.0f
            val encA = s0 * 16581375.0f

            val r = s0 - floor(encG / 255.0f)
            val g = encG - floor(encB / 255.0f)
            val b = encB - floor(encA / 255.0f)
            val a = encA - floor(encA / 255.0f) * 255.0f

            val intR = (r * 255.0f).toInt() and 0xFF
            val intG = (g * 255.0f).toInt() and 0xFF
            val intB = (b * 255.0f).toInt() and 0xFF
            val intA = (a).toInt() and 0xFF

            val int = (intA shl 24) or (intB shl 16) or (intG shl 8) or intR

            image.setPixelRGBA(writeX, y, int)
            y++
        }

        writeX = (writeX + 1) % columnCount

        if(count < columnCount) {
            count++
        }
    }

    fun upload() {
        glTex.bind()
        glTex.upload() // TODO we can upload just the slice
    }

    fun close() {
        if(closed) {
            return
        }

        closed = true

        image.close()
        glTex.close()
    }
}

/**
 * Defines an oscilloscope's colors per channel.
 * @param colors The colors for each channel, in order.
 * */
data class OscilloscopePalette(val colors: List<Vector4d>) {
    val colorsInt = colors.map { MyColor(it.w.toFloat(), it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }

    /**
     * Raw buffer holding the data to upload to the shader, that needs to be masked by the channel mask.
     * */
    private val rawData = FloatArray(colors.size * 4)

    init {
        colors.indices.forEach { i ->
            val color = colors[i]
            val j = i * 4

            rawData[j + 0] = color.x.toFloat()
            rawData[j + 1] = color.y.toFloat()
            rawData[j + 2] = color.z.toFloat()
            rawData[j + 3] = color.w.toFloat()
        }
    }

    /**
     * Sets the color palette.
     * @param mask The latest samples. The channels which have NaN will be set to alpha = 0.
     * */
    fun setUniform(unform: AbstractUniform, mask: FloatArray) {
        val data = FloatArray(rawData.size)

        val valuesToCopy = min(rawData.size, mask.size * 4)
        for (i in 0 until valuesToCopy step 4) {
            val sample = mask[i / 4]

            if(!sample.isNaN()) {
                data[i + 0] = rawData[i + 0]
                data[i + 1] = rawData[i + 1]
                data[i + 2] = rawData[i + 2]
                data[i + 3] = rawData[i + 3]
            }
        }

        unform.set(data)
    }

    companion object {
        fun defineRGBA(vararg components: Float) : OscilloscopePalette {
            require(components.size % 4 == 0 && components.isNotEmpty()) {
                "Invalid palette component count ${components.size}"
            }

            val vectors = ArrayList<Vector4d>(components.size / 4)

            for (i in 0 until components.size step 4) {
                vectors.add(
                    Vector4d(
                        components[i + 0].toDouble(),
                        components[i + 1].toDouble(),
                        components[i + 2].toDouble(),
                        components[i + 3].toDouble()
                    )
                )
            }

            return OscilloscopePalette(vectors)
        }

        val DEFAULT = defineRGBA(
            0.0f, 0.0f, 1.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f
        )
    }
}

object OscilloscopeShader {
    private var shader: ShaderInstance? = null

    fun register(event: RegisterShadersEvent) {
        val src = ShaderInstance(
            Minecraft.getInstance().resourceManager,
            resource("oscilloscope"),
            DefaultVertexFormat.POSITION_TEX
        )

        event.registerShader(src) {
            this.shader = it
            LOG.info("Loaded oscilloscope shader.")
        }
    }

    /**
     * Sets up the shader for rendering.
     * @param alpha Factor for the output alpha.
     * @param texture The raw data buffer.
     * @param mask The latest samples. Channels with NaN will be hidden.
     * */
    fun bindAndSetup(
        alpha: Float,
        texture: OscilloscopeTexture,
        mask: FloatArray,
        specification: OscilloscopeSpecification,
        aspect: Float,
        isGui: Boolean,
    ) {
        RenderSystem.assertOnRenderThread()

        val shader = shader ?: error("Oscilloscope shader didn't load")

        val thicknessFactor = if(isGui) specification.thicknessFactorGui else 1.0f

        RenderSystem.setShader { shader }
        shader.safeGetUniform("Sampler0").set(texture.glTex.id)
        shader.safeGetUniform("u_writeX").set(texture.writeX.toFloat())
        shader.safeGetUniform("u_count").set(texture.count.toFloat())
        shader.safeGetUniform("u_thickness").set(specification.channelThickness * thicknessFactor)
        shader.safeGetUniform("u_alpha").set(alpha)
        specification.palette.setUniform(shader.safeGetUniform("u_channelColors"), mask)

        shader.safeGetUniform("u_axisInfo").set(
            FloatArray(7).also {
                val (x, y, z, w) = specification.axisColor

                it[0] = specification.horizontalCuts.toFloat()
                it[1] = aspect
                it[2] = specification.axisThickness * thicknessFactor
                it[3] = x.toFloat()
                it[4] = y.toFloat()
                it[5] = z.toFloat()
                it[6] = w.toFloat()
            }
        )
    }

    fun unbind() {
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
    }
}

/**
 * Oscilloscope settings that don't change at runtime.
 * @param channelCount The number of channels. For each channel, a resistor will be created, that is grounded at the negative terminal and connected to the external circuit at the positive terminal.
 * @param minWindow The minimum number of samples on screen.
 * @param maxWindow The maximum number of samples on screen. This defines the time horizon based on the sampling rate (in optimal conditions, 100 samples/s).
 * @param channelThickness The line thickness of the waveform.
 * @param axisThickness The line thickness of the vertical and horizontal axis.
 * @param axisColor The color of the divisions.
 * @param horizontalCuts The number of horizontal lines to draw. Vertical cuts are adjusted based on aspect ratio.
 * @param thicknessFactorGui Factor for the thickness when the GUI screen is rendered. The in-world thicknesses may look excessive in GUI.
 * */
data class OscilloscopeSpecification(
    val channelCount: Int,
    val minWindow: Int,
    val maxWindow: Int,
    val palette: OscilloscopePalette,
    val channelThickness: Float,
    val axisThickness: Float,
    val axisColor: Vector4d,
    val horizontalCuts: Int,
    val thicknessFactorGui: Float,
)

/**
 * The electrical part of the oscilloscope. Handles creating resistors with a very high resistance to ground.
 * */
class OscilloscopeObject(cell: OscilloscopeCell, val specification: OscilloscopeSpecification) : ElectricalObject<OscilloscopeCell>(cell) {
    val channelRange = 0 until specification.channelCount
    val resistors = Array<Resistor?>(specification.channelCount) { null }

    private fun getResistorForTerminal(terminal: Int) : ElectricalPin? {
        if(!channelRange.contains(terminal)) {
            return null
        }

        val storedResistor = resistors[terminal]
        if(storedResistor != null) {
            return storedResistor.positive
        }

        val resistor = Resistor()
        resistor.resistance = 1e8
        resistors[terminal] = resistor

        return resistor.positive
    }

    override fun offerPolar(remote: ElectricalObject<*>) =
        if(cell.electricalMap.evaluates(cell, remote.cell)) {
            getResistorForTerminal(0)
        }
        else {
            null
        }

    override fun offerTerminal(gc: GridConnectionCell, m0: GridConnectionCell.NodeInfo) =
        getResistorForTerminal(m0.terminal)

    override fun build(map: ElectricalConnectivityMap) {
        super.build(map)

        resistors.forEach {
            if(it != null) {
                map.ground(it.negative)
            }
        }
    }

    override fun clearComponents() {
        resistors.fill(null)
    }

    /**
     * Gets the read potential for [channel].
     * If the channel is not connected, [Double.NaN] is returned. This will make the channel invisible later down the line.
     * */
    fun getPotential(channel: Int) : Double {
        check(channelRange.contains(channel)) {
            "Cannot read channel $channel of an oscilloscope with $channelRange"
        }

        val resistor = resistors[channel]
            ?: return Double.NaN

        return resistor.potential
    }
}

fun interface OscilloscopeSampleConsumer {
    fun consume(samples: FloatArray, timestamp: Double)
}

class OscilloscopeCell(ci: CellCreateInfo, override val electricalMap: MonopoleMap, specification: OscilloscopeSpecification) : Cell(ci), SidedElectricalMonoMapped<OscilloscopeCell> {
    override val electricalSize: ElectricalSize
        get() = ElectricalSize.Signal

    @SimObject
    val oscilloscope = OscilloscopeObject(this, specification)

    @Node
    val grid = GridNode(this)

    private var simulationTime = 0.0

    private var listener: OscilloscopeSampleConsumer? = null

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        super.subscribe(subscribers)
        subscribers.addPost(this::sampleAndRaiseEvent)
    }

    private fun sampleAndRaiseEvent(dt: Double, phase: SimulationPhase) {
        val consumer = listener
            ?: return

        simulationTime += dt

        val buffer = FloatArray(oscilloscope.specification.channelCount)

        for (i in 0 until oscilloscope.specification.channelCount) {
            buffer[i] = oscilloscope.getPotential(i).toFloat()
        }

        consumer.consume(buffer, simulationTime)
    }

    fun bind(consumer: OscilloscopeSampleConsumer) {
        listener = consumer
    }

    fun unbind() {
        listener = null
    }
}

/**
 * Double-buffer for oscilloscope samples. It handles de-jitter and de-batching the samples coming over the network.
 * @param desiredRenderingSize The desired size of the render buffer, in samples.
 * @param maxBufferSizeAbsolute Hard cap on the size. If samples aren't consumed in time, the buffer will discard the oldest samples so this threshold isn't broken (should never be reached).
 * @param kE Control parameter.
 * @param maxDebufferQueueSize The max size of the network smoothing loop. Ideally, the queue is never filled above say ~10 packets.
 * @param samplingRateAlpha Smoothing parameter for calculating the sampling rate.
 * */
class OscilloscopeTransferBuffer(
    val desiredRenderingSize: Int,
    val maxBufferSizeAbsolute: Int,
    val kE: Double = 30.0,
    val maxDebufferQueueSize: Int = 20,
    val samplingRateAlpha: Double = 0.1,
) {
    private val obj = Any()

    /**
     * The server packets are moved here. We get batches of ~5 arrays because the bulk packets are flushed per game tick.
     * */
    val debufferingQueue = ArrayDeque<Pair<FloatArray, Double>>()

    /**
     * The actual queue for rendering is this. Samples get copied from [debufferingQueue] to [renderQueue] at ~the sampling rate.
     * The control loop then uses the size of this queue to calculate the error.
     * */
    val renderQueue = ArrayDeque<Pair<FloatArray, Double>>()

    private val debufferWatch = Stopwatch()
    private var debufferingTimeAccumulator = 0.0

    private val extractionWatch = Stopwatch()
    var extractionTimeAccumulator = 0.0
        private set

    private var lastServerTime = -1.0
    private var samplingRateEma = 1.0 / CellGraph.DT
    private var nextPlayTime = desiredRenderingSize * CellGraph.DT

    var playRate = 1.0 / CellGraph.DT
        private set

    /**
     * The latest samples removed by [consume].
     * */
    var latestRemovedSet : Pair<FloatArray, Double>? = null
        private set

    /**
     * Inserts samples into the buffer. This should ideally be called every game tick with the batch of ~5 samples from the simulation.
     * */
    fun insertMessage(samples: FloatArray, serverTimeStamp: Double) {
        synchronized(obj) {
            while (renderQueue.size >= maxBufferSizeAbsolute) {
                LOG.warn("Dropping unrendered oscilloscope samples from render queue $serverTimeStamp")
                renderQueue.removeFirst()
            }

            while(debufferingQueue.size >= maxBufferSizeAbsolute) {
                LOG.warn("Dropping unrendered oscilloscope samples from debuffer queue $serverTimeStamp")
                debufferingQueue.removeFirst()
            }

            debufferingQueue.addLast(Pair(samples, serverTimeStamp))
        }

        if(lastServerTime != -1.0) {
            val dt = (serverTimeStamp - lastServerTime)

            if(dt > CellGraph.DT * 0.8 && dt < 10.0) {
                samplingRateEma = samplingRateAlpha * (1.0 / dt) + (1.0 - samplingRateAlpha) * samplingRateEma
            }
        }

        lastServerTime = serverTimeStamp
    }

    /**
     * Copies a sample from the debuffering queue to the rendering queue.
     * */
    fun copySample() : Boolean {
        var result = false
        synchronized(obj) {
            if(debufferingQueue.isNotEmpty()) {
                renderQueue.add(debufferingQueue.removeFirst())
                result = true
            }
        }

        return result
    }

    /**
     * Copies samples from the debuffering queue to the rendering queue at approx. 1.0 / samplingRateEma.
     * Copies samples forcefully if the buffer is at max capacity.
     * */
    private fun debuffer() {
        debufferingTimeAccumulator += !debufferWatch.sample()

        val samplingInterval = 1.0 / samplingRateEma

        // Copy samples nominally:
        while (debufferingTimeAccumulator >= samplingInterval) {
            debufferingTimeAccumulator -= samplingInterval
            copySample()
        }

        var forcedSamples = 0
        // Safeguard against weird stuff:
        while(debufferingQueue.size > maxDebufferQueueSize) {
            copySample()
            ++forcedSamples
        }

        if(forcedSamples > 10) {
            LOG.debug("Debuffer queue desaturated $forcedSamples samples")
        }
    }

    /**
     * Time-invariant [playRate] control. It is very stable and doesn't need real tuning.
     * It works by setting the play rate to the sampling rate plus an offset proportional to the difference in desired delay and actual delay.
     * */
    private fun control() {
        val samplingRate = samplingRateEma
        val samplingPeriod = (1.0.nz() / samplingRate.nz())
        val desiredDepthSeconds = desiredRenderingSize * samplingPeriod
        val depthSeconds = renderQueue.size * samplingPeriod

        val maxRateDelta = 10.0

        playRate = (1.0 / samplingPeriod) -kE * (desiredDepthSeconds - depthSeconds)
        playRate = playRate.coerceIn(samplingRate - maxRateDelta, samplingRate + maxRateDelta).coerceAtLeast(0.1)
    }

    private fun removeSet() : Pair<FloatArray, Double>? {
        extractionTimeAccumulator += !extractionWatch.sample()

        if(extractionTimeAccumulator < nextPlayTime) {
            return null
        }

        extractionTimeAccumulator -= nextPlayTime

        var hasResult = false
        synchronized(obj) {
            if(renderQueue.isNotEmpty()) {
                latestRemovedSet = renderQueue.removeFirst()
                hasResult = true
            }
        }

        nextPlayTime = 1.0 / playRate

        return if(hasResult) latestRemovedSet else null
    }

    /**
     * Gets the next column to insert into the rendering data. Called per-frame. Ideally, the frame rate should be very high.
     * Returns null if it's not time for that yet, or samples are simply not available.
     *
     * **P.S. Call this in a loop, exiting only when it returns null!**
     * */
    fun consume() : Pair<FloatArray, Double>? {
        debuffer()
        val result = removeSet()
        control()
        return result
    }
}

/**
 * Client-side data for the oscilloscope. Must be constructed on the render thread.
 * Must be re-constructed when the column count changes. When other settings change, the [serverOptions] reference is simply replaced.
 * */
@ClientOnly
class OscilloscopeClientSide(var serverOptions: OscilloscopeParameters, channels: Int) {
    private var closed = false

    val texture = OscilloscopeTexture(
        resource("oscilloscope_${UUID.randomUUID()}"),
        serverOptions.timeWindow,
        channels
    )

    /**
     * Buffer for mouse picking the waveform in the GUI:
     * */
    val guiBuffer = ArrayDeque<Pair<FloatArray, Double>>()

    /**
     * Buffer for scheduling the data upload:
     * */
    val transferBuffer = OscilloscopeTransferBuffer(25, 200)

    /**
     * Copies the data from the previous buffer after resize.
     * */
    constructor(serverOptions: OscilloscopeParameters, channels: Int, previousState: OscilloscopeClientSide) : this(serverOptions, channels) {
        fun addToGuiBuffer(it: Pair<FloatArray, Double>) {
            while(guiBuffer.size >= texture.columnCount) {
                guiBuffer.removeFirst()
            }

            guiBuffer.add(it)
        }

        // Write the data that was on screen:
        previousState.guiBuffer.forEach {
            texture.writeColumn(it.first)
            addToGuiBuffer(it)
        }

        // Move all network data to the render queue:
        while (true) {
            if(!previousState.transferBuffer.copySample()) {
                break
            }
        }

        // Finally, write the data that was in queue for rendering:
        previousState.transferBuffer.renderQueue.forEach {
            texture.writeColumn(it.first)
            addToGuiBuffer(it)
        }

        texture.upload()
    }

    init {
        requireIsOnRenderThread {
            "OscilloscopeClientSide#init"
        }

        OscilloscopeCopyManager.add(this)
    }

    /**
     * Uploads the columns into GPU memory and updates the [guiBuffer]. Called by [OscilloscopeCopyManager].
     * */
    fun transferSamples() {
        requireIsOnRenderThread {
            "OscilloscopeClientSide#copyIntoGPUMemory"
        }

        if(closed) {
            return
        }

        while (true) {
            val pair = transferBuffer.consume()

            if(pair != null) {
                texture.writeColumn(pair.first)
                texture.upload()

                while(guiBuffer.size >= texture.columnCount) {
                    guiBuffer.removeFirst()
                }

                guiBuffer.addLast(pair)
            }
            else {
                break
            }
        }
    }

    /**
     * Inserts a sample into the transfer buffer.
     * */
    fun enqueueForTransfer(samples: FloatArray, timestamp: Double) {
        transferBuffer.insertMessage(samples, timestamp)
    }

    fun close() {
        requireIsOnRenderThread {
            "OscilloscopeClientSide#close"
        }

        if(closed) {
            return
        }

        closed = true
        OscilloscopeCopyManager.remove(this)
        texture.close()
    }
}

private fun sanitizeSignalRange(v: Float) : Float {
    var result = v.coerceIn((-MAX_SIGNAL).toFloat(), (+MAX_SIGNAL).toFloat())

    if(result.isNaN() || result.isInfinite()) {
        result = 0.0f
    }

    return result
}

private fun sanitizeOutputRange(v: Float) : Float {
    var result = v

    if(result.isNaN() || result.isInfinite()) {
        result = 0.0f
    }

    return result
}

private fun sanitizeUnit(unit: String) =
    unit.filter {
        it.isLetter ||
        it.isDigit ||
        it == '^' ||
        it == '/' ||
        it == '-' ||
        it == '×' ||
        it == '²'
    }.take(5)

/**
 * Persistent settings for the oscilloscope **game object**. They are saved in the part's NBT.
 * Clients receive them as well, and they also send new settings to the server from the GUI.
 * */
class OscilloscopeParameters(var timeWindow: Int, val channelParameters: Array<ChannelParameters>) {
    constructor(specification: OscilloscopeSpecification) : this(
        specification.maxWindow / 2,
        Array<ChannelParameters>(specification.channelCount) {
            ChannelParameters(
                -1.0f, +1.0f,
                -1.0f, +1.0f,
                ""
            )
        }
    )

    /**
     * Settings for individual channels.
     * A linear map is used like all signal devices.
     * @param unit The optional unit displayed in the GUIs. Empty if no unit is specified.
     * */
    data class ChannelParameters(
        var signalMin: Float,
        var signalMax: Float,
        var displayMin: Float,
        var displayMax: Float,
        var unit: String
    )

    fun saveToTag(tag: CompoundTag) {
        tag.putInt(WINDOW, timeWindow)

        val channelParametersList = ListTag()
        channelParameters.forEach { channel ->
            val channelTag = CompoundTag()
            channelTag.putFloat(SIGNAL_MIN, channel.signalMin)
            channelTag.putFloat(SIGNAL_MAX, channel.signalMax)
            channelTag.putFloat(DISPLAY_MIN, channel.displayMin)
            channelTag.putFloat(DISPLAY_MAX, channel.displayMax)
            channelTag.putString(UNIT, channel.unit)
            channelParametersList.add(channelTag)
        }

        tag.put(CHANNEL_PARAMETERS, channelParametersList)
    }

    fun loadFromTag(tag: CompoundTag) {
        timeWindow = tag.getInt(WINDOW)

        val channelParametersList = tag.getListTag(CHANNEL_PARAMETERS)
        if(channelParametersList.size == channelParameters.size) {
            // Accept only if the channel count didn't change
            channelParameters.indices.forEach { i ->
                val channel = channelParameters[i]
                val channelTag = channelParametersList[i] as CompoundTag
                channel.signalMin = channelTag.getFloat(SIGNAL_MIN)
                channel.signalMax = channelTag.getFloat(SIGNAL_MAX)
                channel.displayMin = channelTag.getFloat(DISPLAY_MIN)
                channel.displayMax = channelTag.getFloat(DISPLAY_MAX)
                channel.unit = channelTag.getString(UNIT)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OscilloscopeParameters

        if (timeWindow != other.timeWindow) return false
        if (!channelParameters.contentEquals(other.channelParameters)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timeWindow
        result = 31 * result + channelParameters.contentHashCode()
        return result
    }

    companion object {
        private const val WINDOW = "timeWindow"
        private const val CHANNEL_PARAMETERS = "channelParameters"
        private const val SIGNAL_MIN = "signalMin"
        private const val SIGNAL_MAX = "signalMax"
        private const val DISPLAY_MIN = "displayMin"
        private const val DISPLAY_MAX = "displayMax"
        private const val UNIT = "unit"

        fun serialize(packet: OscilloscopeParameters, buffer: FriendlyByteBuf) {
            buffer.writeInt(packet.timeWindow)
            buffer.writeVarInt(packet.channelParameters.size)
            packet.channelParameters.forEach { element ->
                buffer.writeFloat(element.signalMin)
                buffer.writeFloat(element.signalMax)
                buffer.writeFloat(element.displayMin)
                buffer.writeFloat(element.displayMax)
                buffer.writeUtf(element.unit)
            }
        }

        fun deserialize(buffer: FriendlyByteBuf) : OscilloscopeParameters {
            val timeWindow = buffer.readInt()
            val arraySize = buffer.readVarInt()
            val array = Array(arraySize) {
                val signalMin = buffer.readFloat()
                val signalMax = buffer.readFloat()
                val displayMin = buffer.readFloat()
                val displayMax = buffer.readFloat()
                val unit = buffer.readUtf()

                OscilloscopeParameters.ChannelParameters(signalMin, signalMax, displayMin, displayMax, unit)
            }

            return OscilloscopeParameters(timeWindow, array)
        }
    }
}

/**
 * Because the block entity renderer is frustum culled (the render chunks are frustum culled actually, unrelated to `shouldRenderOffscreen`), we can't rely on it to handle the de-queueing of samples.
 * So we subscribe to the render event instead.
 * */
object OscilloscopeCopyManager {
    private val tasks = HashSet<OscilloscopeClientSide>()

    @OnClientThread
    fun add(task: OscilloscopeClientSide) {
        requireIsOnRenderThread {
            "OscilloscopeCopyManager#add"
        }

        require(tasks.add(task)) {
            "Duplicate add oscilloscope task $task"
        }
    }

    @OnClientThread
    fun remove(task: OscilloscopeClientSide) {
        requireIsOnRenderThread {
            "OscilloscopeCopyManager#remove"
        }

        require(tasks.remove(task)) {
            "Invalid remove oscilloscope task $task"
        }
    }

    @OnClientThread
    fun execute(event: RenderLevelStageEvent) {
        if(event.stage != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return
        }

        tasks.forEach {
            it.transferSamples()
        }
    }
}

fun interface OscilloscopeGridTerminalFactory {
    fun create(part: OscilloscopePart) : Supplier<GridTerminal>
}

class OscilloscopeChannelGenerators(val factories: List<OscilloscopeGridTerminalFactory>) {
    companion object {
        class Builder {
            val factories = ArrayList<OscilloscopeGridTerminalFactory>()

            fun withGridTerminal(block: OscilloscopePart.() -> Supplier<GridTerminal>) {
                factories.add {
                    block.invoke(it)
                }
            }
        }

        fun create(action: Builder.() -> Unit) : OscilloscopeChannelGenerators {
            val builder = Builder()
            action(builder)
            return OscilloscopeChannelGenerators(builder.factories.toList())
        }
    }
}

class OscilloscopePart(ci: PartCreateInfo, val specification: OscilloscopeSpecification, generators: OscilloscopeChannelGenerators, cellProvider: CellProvider<OscilloscopeCell>) :
    GridCellPart<OscilloscopeCell>(ci, cellProvider),
    AdditionalRenderingPart,
    ComponentDisplay,
    MenuProvider
{
    val channels = generators.factories.map { factory ->
        factory.create(this)
    }

    @ServerOnly // Saved in disk NBT, sent over the bulk packet. Changes received from the GUI container and applied.
    private var serverParameters = if(!placement.level.isClientSide) OscilloscopeParameters(specification) else null

    @ClientOnly // Initialized on render thread. Will be re-created if the time window changes.
    private var clientSide : OscilloscopeClientSide? = null

    //#region Rendering State Management and In-World Rendering

    override fun onUnloaded() {
        super.onUnloaded()
        clientSide?.close()
    }

    override fun onBroken() {
        super.onBroken()
        clientSide?.close()
    }

    override fun levelRender(context: AdditionalRenderingPart.Context) {
        val renderState = this.clientSide
            ?: return

        val poseStack = context.poseStack

        poseStack.pushPose()

        val transVert = 0.075 / 16.0 + 0.001

        poseStack.translate(
            placement.face.stepX.toFloat() * transVert,
            placement.face.stepY.toFloat() * transVert,
            placement.face.stepZ.toFloat() * transVert
        )

        val sizeX = 0.6f
        val sizeZ = 0.4f

        val (dx, dy, dz) = partOffsetTable[placement.face.get3DDataValue()]
        poseStack.translate(dx, dy, dz)
        poseStack.mulPose(placement.face.rotationFast)
        poseStack.mulPose(Rotation3d.exp(Vector3d.unitY * placement.facing.angle))
        // x = left-right, z = up-down (neg = up)
        poseStack.translate(-0.063f, 0.0f, 0.01f)
        // z = height, x = width
        poseStack.scale(sizeX, 1.0f, sizeZ);
        poseStack.mulPose(Rotation3d.exp(Vector3d.unitX * PI / 2.0))

        val texLoc = renderState.texture.resourceId

        RenderSystem.setShaderTexture(0, texLoc)

        val latestSamples = renderState.transferBuffer.latestRemovedSet?.first ?: FloatArray(renderState.texture.channelCount)

        OscilloscopeShader.bindAndSetup(
            1.0f,
            renderState.texture,
            latestSamples,
            specification,
            sizeX / sizeZ,
            false
        )

        dispatchInWorldQuadImmediate(poseStack)
        OscilloscopeShader.unbind()

        submitInWorldText(poseStack, context.buffer, latestSamples, specification.palette)

        poseStack.popPose()
    }

    // One draw call per oscilloscope, it's fine
    private fun dispatchInWorldQuadImmediate(poseStack: PoseStack) {
        RenderSystem.enableBlend()

        RenderSystem.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )

        RenderSystem.enableDepthTest()

        val pose = poseStack.last().pose()
        val tesselator = Tesselator.getInstance()
        val builder = tesselator.builder

        val quadLeft = -0.5f
        val quadRight = 0.5f
        val quadTop = -0.5f
        val quadBottom = 0.5f

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        builder.vertex(pose, quadLeft, quadBottom, 0f).uv(0f, 1f).endVertex()
        builder.vertex(pose, quadRight, quadBottom, 0f).uv(1f, 1f).endVertex()
        builder.vertex(pose, quadRight, quadTop, 0f).uv(1f, 0f).endVertex()
        builder.vertex(pose, quadLeft, quadTop, 0f).uv(0f, 0f).endVertex()
        tesselator.end()
    }

    private fun submitInWorldText(poseStack: PoseStack, bufferSource: MultiBufferSource, samples: FloatArray, palette: OscilloscopePalette) {
        val font = Minecraft.getInstance().font

        val scale = 0.007f
        val verticalSpacing = 0.1f

        val params = clientSide!!.serverOptions.channelParameters

        samples.indices.forEach { i ->
            poseStack.pushPose()

            val sample = samples[i].toDouble()

            poseStack.translate(-0.5f, -0.5f + i * verticalSpacing, -0.01f) // Z is above the quad
            poseStack.scale(scale, scale, scale)

            val text = if(sample.isNaN()) {
                "N/A"
            } else {
                val channelParams = params[i]

                val mapped = map(
                    sample,
                    -1.0, +1.0,
                    channelParams.displayMin.toDouble(), channelParams.displayMax.toDouble()
                )

                var text = "${mapped.rounded(3)}"

                if(snzi(mapped) == 1) {
                    text = "+$text"
                }

                text + channelParams.unit
            }

            val channelColor = palette.colorsInt[i]

            val textColor = MyColor((channelColor.a * 0.8).toInt(), channelColor.r, channelColor.g, channelColor.b)

            font.drawInBatch(
                text,
                0.0f,
                0.0f,
                textColor.data,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.POLYGON_OFFSET,
                MyColor(50, 255, 255, 255).data,
                15728880
            )

            poseStack.popPose()
        }
    }

    //#endregion

    //#region GUI

    override fun onUsedBy(context: PartUseInfo): InteractionResult {
        if(context.hand != InteractionHand.MAIN_HAND || !context.player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty) {
            return InteractionResult.FAIL
        }

        if(placement.level.isClientSide) {
            return InteractionResult.PASS
        }

        NetworkHooks.openScreen(context.player as ServerPlayer, this) { buf ->
            this.eln2WritePartGuiData(buf)
        }

       return InteractionResult.SUCCESS
    }

    override fun getDisplayName(): Component = TITLE

    class OscilloscopeMenu(pContainerId: Int, val part: OscilloscopePart) : AbstractContainerMenu(Eln2Signal.FLAT_OSCILLOSCOPE_MENU.get(), pContainerId) {
        override fun quickMoveStack(pPlayer: Player, pIndex: Int): ItemStack = ItemStack.EMPTY
        override fun stillValid(pPlayer: Player) = part.stillValid(pPlayer)
    }

    class OscilloscopeScreen(menu: OscilloscopeMenu, playerInventory: Inventory, title: Component) : MyAbstractContainerScreen<OscilloscopeMenu>(menu, playerInventory, title) {
        private val mousePosSmoother = FramerateIndependentSmoother2d(0.025)

        private class Widgets(val perChannel: Array<PerChannelWidgets>) {
            class PerChannelWidgets(
                val titleWidget: StringWidget,
                val unit: EditBox,
                val signalMin: EditBox,
                val signalMax: EditBox,
                val displayMin: EditBox,
                val displayMax: EditBox,
                val background: PanelWidget
            )

            class PanelWidget(
                val x: Int,
                val y: Int,
                val width: Int,
                val height: Int,
                val color1: MyColor,
                val color2: MyColor
            ) : Renderable {
                override fun render(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
                    pGuiGraphics.fillGradient(
                        x,
                        y,
                        x + width,
                        y + height,
                        color1.data,
                        color2.data
                    )
                }
            }
        }

        private var widgets: Widgets? = null
        private var scroll = 0.0

        override fun init() {
            super.init()
            widgets = null
        }

        override fun mouseScrolled(pMouseX: Double, pMouseY: Double, pDelta: Double): Boolean {
            scroll += pDelta * 10.0f
            return true
        }

        override fun renderLabels(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int) {
            // No-op
        }

        override fun renderBg(pGuiGraphics: GuiGraphics, pPartialTick: Float, mouseX: Int, mouseY: Int) {
            val part = menu.part

            val renderState = menu.part.clientSide
                ?: return

            mousePosSmoother.update(mouseX.toDouble(), mouseY.toDouble())
            val pMouseX = mousePosSmoother.x.toFloat()
            val pMouseY = mousePosSmoother.y.toFloat()

            val poseStack = pGuiGraphics.pose()

            val corner = (min(pGuiGraphics.guiWidth(), pGuiGraphics.guiHeight()) * 0.05f).toInt()
            val sizeX = (pGuiGraphics.guiWidth() * 0.75f).toInt()
            val sizeY = (pGuiGraphics.guiHeight() - 2.0f * corner).toInt()

            // Renders oscilloscope screen background:
            pGuiGraphics.fillGradient(
                corner, corner,
                corner + sizeX, corner + sizeY,
                MyColor(200, 50, 50, 75).data,
                MyColor(150, 50, 50, 100).data
            )

            val padLeft = pGuiGraphics.guiWidth() * 0.01f
            val padTop = pGuiGraphics.guiHeight() * 0.01f
            val verticalSpacing = pGuiGraphics.guiHeight() * 0.04f
            val textScale = min(pGuiGraphics.guiWidth(), pGuiGraphics.guiHeight()) / 514.0f * 1.5f

            // Renders sidebar background:
            pGuiGraphics.fill(
                (padLeft / 2.0f + corner + sizeX).toInt(),
                corner,
                (pGuiGraphics.guiWidth() - padLeft).toInt(),
                corner + sizeY,
                MyColor(100, 0, 0,0).data
            )

            var sidebarY = padTop + corner

            fun sampleText(sampleSrc: Float, i: Int) : String {
                return "Ch ${(i + 1)}: " + if(sampleSrc.isNaN()) {
                    "N/A"
                } else {
                    val params = renderState.serverOptions.channelParameters[i]

                    val mapped = map(
                        sampleSrc,
                        -1.0f, 1.0f,
                        params.displayMin, params.displayMax
                    )

                     "${mapped.toDouble().rounded(3)}${params.unit}"
                }
            }

            // Renders the latest value for each channel on the sidebar:
            (renderState.transferBuffer.latestRemovedSet?.first ?: FloatArray(renderState.texture.channelCount) { Float.NaN }).also { samples ->
                samples.indices.forEach { i ->
                    val sample = samples[i]

                    if(!sample.isNaN()) {
                        poseStack.preserve {
                            poseStack.translate(
                                padLeft + corner + sizeX,
                                sidebarY,
                                0.0f
                            )

                            sidebarY += verticalSpacing

                            poseStack.scale(textScale, textScale, 1.0f)

                            val channelColor = part.specification.palette.colorsInt[i]

                            val textColor = MyColor(
                                (channelColor.a * 0.8).toInt(),
                                channelColor.r,
                                channelColor.g,
                                channelColor.b
                            )

                            pGuiGraphics.drawString(
                                font,
                                sampleText(sample, i),
                                0,
                                0,
                                textColor.data
                            )
                        }
                    }
                }
            }

            fun textRow() {
                poseStack.translate(
                    padLeft + corner + sizeX,
                    sidebarY,
                    0.0f
                )

                sidebarY += verticalSpacing

                poseStack.scale(textScale, textScale, 1.0f)
            }

            // Shows the time horizon in seconds on the sidebar:
            poseStack.preserve {
                textRow()

                pGuiGraphics.drawString(
                    font,
                    "Window: ${Quantity(renderState.texture.columnCount * CellGraph.DT, SECOND).classify()}",
                    0, 0,
                    MyColor(200, 200, 200, 200).data
                )
            }

            // Now, sidebarY may not be used to add more elements because of the code below.

            //#region Input

            if(widgets == null) {
                // (Re)creates the widgets:
                val offsetBetweenBoxes = 4

                val perChannelRangeBoxes = Array(renderState.texture.channelCount) { iCh ->
                    val data = renderState.serverOptions.channelParameters[iCh]

                    val boxLeft = ceil(padLeft + corner + sizeX).toInt()
                    val boxHeight = min(12, ceil(pGuiGraphics.guiHeight() * 0.035f).toInt())
                    val boxWidth = ceil(pGuiGraphics.guiWidth() * 0.05f).toInt()

                    val minY = sidebarY

                    val title = StringWidget(
                        boxLeft,
                        ceil(sidebarY).toInt(),
                        boxWidth,
                        boxHeight,
                        Component.literal("Ch${(iCh + 1)}:"),
                        font
                    )

                    val unit = EditBox(
                        font,
                        boxLeft + title.width + 1,
                        ceil(sidebarY).toInt(),
                        boxWidth,
                        boxHeight,
                        Component.literal("Unit")
                    )

                    sidebarY += boxHeight + offsetBetweenBoxes

                    val signalMin = EditBox(
                        font,
                        boxLeft, ceil(sidebarY).toInt(),
                        boxWidth, boxHeight,
                        Component.literal("Signal Min")
                    )

                    val signalMax = EditBox(
                        font,
                        boxLeft + boxWidth + offsetBetweenBoxes, ceil(sidebarY).toInt(),
                        boxWidth, boxHeight,
                        Component.literal("Signal Max")
                    )

                    sidebarY += boxHeight + offsetBetweenBoxes

                    val displayMin = EditBox(
                        font,
                        boxLeft, ceil(sidebarY).toInt(),
                        boxWidth, boxHeight,
                        Component.literal("Display Max")
                    )

                    val displayMax = EditBox(
                        font,
                        boxLeft + boxWidth + offsetBetweenBoxes, ceil(sidebarY).toInt(),
                        boxWidth, boxHeight,
                        Component.literal("Display Max")
                    )

                    sidebarY += boxHeight + offsetBetweenBoxes

                    unit.setFilter { s -> s.isEmpty() || s == sanitizeUnit(s) }

                    signalMin.setFilter { s ->
                        s.isEmpty() || s == "-" || (s.toFloatOrNull() != null && sanitizeSignalRange(s.toFloat()) == s.toFloat())
                    }

                    signalMax.setFilter { s ->
                        s.isEmpty() || s == "-" || (s.toFloatOrNull() != null && sanitizeSignalRange(s.toFloat()) == s.toFloat())
                    }

                    displayMin.setFilter { s ->
                        s.isEmpty() || s == "-" || (s.toFloatOrNull() != null && sanitizeOutputRange(s.toFloat()) == s.toFloat())
                    }

                    displayMax.setFilter { s ->
                        s.isEmpty() || s == "-" || (s.toFloatOrNull() != null && sanitizeOutputRange(s.toFloat()) == s.toFloat())
                    }

                    unit.value = data.unit
                    signalMin.value = data.signalMin.toString()
                    signalMax.value = data.signalMax.toString()
                    displayMin.value = data.displayMin.toString()
                    displayMax.value = data.displayMax.toString()

                    val channelColor = part.specification.palette.colorsInt[iCh]

                    val panel = Widgets.PanelWidget(
                        boxLeft - padLeft.toInt() / 2, floor(minY).toInt(),
                        2 * boxWidth + offsetBetweenBoxes + padLeft.toInt(),
                        ceil(sidebarY - minY).toInt() + padLeft.toInt(),
                        MyColor((0.5f * channelColor.a).toInt(), channelColor.r, channelColor.g, channelColor.b),
                        MyColor(50, 50, 50, 50)
                    )

                    // Separator
                    sidebarY += boxHeight + offsetBetweenBoxes

                    Widgets.PerChannelWidgets(
                        title,
                        unit,
                        signalMin, signalMax,
                        displayMin, displayMax,
                        panel
                    )
                }

                perChannelRangeBoxes.forEach {
                    addRenderableOnly(it.background)
                    addRenderableWidget(it.unit)
                    addRenderableWidget(it.titleWidget)
                    addRenderableWidget(it.signalMin)
                    addRenderableWidget(it.signalMax)
                    addRenderableWidget(it.displayMin)
                    addRenderableWidget(it.displayMax)
                }

                widgets = Widgets(perChannelRangeBoxes)
            }
            else {
                // Create the full desired state, and compare with the current state.
                // If it is different, send to server.
                // Don't update the render state's options immediately, let the server send the update to us once it's validated:
                val desiredParameters = OscilloscopeParameters(
                    if(pMouseX > corner && pMouseY > corner && pMouseX < corner + sizeX && pMouseY < corner + sizeY) {
                        (renderState.serverOptions.timeWindow + scroll)
                            .roundToInt()
                            .coerceIn(part.specification.minWindow, part.specification.maxWindow)
                    }
                    else {
                        renderState.serverOptions.timeWindow
                    },
                    Array(renderState.serverOptions.channelParameters.size) { i ->
                        val currentChannelParameters = renderState.serverOptions.channelParameters[i]
                        val w = widgets!!.perChannel[i]

                        OscilloscopeParameters.ChannelParameters(
                            sanitizeSignalRange(w.signalMin.value.toFloatOrNull() ?: currentChannelParameters.signalMin),
                            sanitizeSignalRange(w.signalMax.value.toFloatOrNull() ?: currentChannelParameters.signalMax),
                            sanitizeOutputRange(w.displayMin.value.toFloatOrNull() ?: currentChannelParameters.displayMin),
                            sanitizeOutputRange(w.displayMax.value.toFloatOrNull() ?: currentChannelParameters.displayMax),
                            sanitizeUnit(w.unit.value)
                        )
                    }
                )

                if(desiredParameters != renderState.serverOptions) {
                    part.sendPacketToServer(GuiMessage::serialize, GuiMessage(desiredParameters))
                }
            }

            //#endregion

            //#region Interactive Oscilloscope

            RenderSystem.setShaderTexture(0, renderState.texture.resourceId)

            val latestSamples = renderState.transferBuffer.latestRemovedSet?.first ?: FloatArray(renderState.texture.channelCount)

            OscilloscopeShader.bindAndSetup(
                1.0f,
                renderState.texture,
                latestSamples,
                part.specification,
                sizeX.toFloat() / sizeY.toFloat(),
                true
            )

            dispatchScope(
                poseStack,
                corner.toFloat(),
                corner.toFloat(),
                sizeX.toFloat(),
                sizeY.toFloat()
            )

            OscilloscopeShader.unbind()

            val guiBuffer = renderState.guiBuffer

            if(guiBuffer.isNotEmpty() && pMouseX > corner && pMouseY > corner && pMouseX < corner + sizeX && pMouseY < corner + sizeY) {
                // We need to map the mouse to the column *on-screen*. This includes the transfer buffer's delay.
                // This is trivial because we insert the data that was last uploaded into the guiBuffer.

                var hoverDistanceSqr = Float.MAX_VALUE
                var hoveredColumn = -1
                var hoveredChannel = -1

                // Gets coordinates of discrete sample:
                fun sampleXScreen(column: Int) = map(
                    column.toFloat(),
                    0.0f, renderState.texture.columnCount.toFloat() - 1.0f,
                    corner.toFloat(), corner + sizeX.toFloat()
                )

                fun sampleYScreen(channel: Int, samples: FloatArray) = map(
                    samples[channel],
                    -1.0f, 1.0f,
                    corner + sizeY.toFloat(), corner.toFloat()
                )

                // Finds the discrete data point which is closest to the mouse:
                guiBuffer.forEachIndexed { column, (samples, _) ->
                    var channel = 0

                    while (channel < renderState.texture.channelCount) {
                        // Only consider channels that are connected:
                        if(!samples[channel].isNaN()) {
                            val dx = pMouseX - sampleXScreen(column)
                            val dy = pMouseY - sampleYScreen(channel, samples)

                            val distance = dx * dx + dy * dy

                            if(distance < hoverDistanceSqr) {
                                hoverDistanceSqr = distance
                                hoveredColumn = column
                                hoveredChannel = channel
                            }
                        }

                        channel++
                    }
                }

                // It is -1 if all samples are NaN (no channel is connected).
                if(hoveredColumn != -1) {
                    var sample = 0.0f
                    var time = 0.0
                    var hoveredX = 0.0f
                    var hoveredY = 0.0f

                    // Called when there is not enough data to interpolate (including when the left and right are NaN):
                    fun discreteSampler() {
                        val pair = guiBuffer[hoveredColumn]
                        sample = pair.first[hoveredChannel]
                        time = pair.second
                        hoveredX = sampleXScreen(hoveredColumn)
                        hoveredY = sampleYScreen(hoveredChannel, pair.first)
                    }

                    if(hoveredColumn != 0 && hoveredColumn < guiBuffer.size - 1) {
                        // Interpolates the sample, time, and positions.
                        // If it is possible, we will construct a spline with the closest discrete point we found in the center, and a left and right point.
                        // Then, we will find the point on the spline closest to the mouse.
                        // P.S. We need to check if the left and right samples are NaN! If so, we go to the fallback sampler instead.

                        val (samplesLeft, t0) = guiBuffer[hoveredColumn - 1]
                        val (samplesMid, t1) = guiBuffer[hoveredColumn]
                        val (samplesRight, t2) = guiBuffer[hoveredColumn + 1]

                        if(samplesLeft[hoveredChannel].isNaN() || samplesRight[hoveredChannel].isNaN()) {
                            // The channels just became connected, so there is no data yet.
                            discreteSampler()
                        }
                        else {
                            val x0 = sampleXScreen(hoveredColumn - 1).toDouble()
                            val x1 = sampleXScreen(hoveredColumn).toDouble()
                            val x2 = sampleXScreen(hoveredColumn + 1).toDouble()

                            val y0 = sampleYScreen(hoveredChannel, samplesLeft).toDouble()
                            val y1 = sampleYScreen(hoveredChannel, samplesMid).toDouble()
                            val y2 = sampleYScreen(hoveredChannel, samplesRight).toDouble()

                            fun spline(t0: Double, t1: Double, t2: Double, f0: Double, f1: Double, f2: Double) : Spline1d {
                                val ml = (f1 - f0).nz() / (t1 - t0).nz()
                                val mr = (f2 - f1).nz() / (t2 - t1).nz()
                                val mm = 0.5 * (ml + mr)

                                return Spline1d(ListSplineSegmentMap(
                                    listOf(
                                        CubicHermiteSplineSegment1d(t0, t1, f0, f1, ml, mm),
                                        CubicHermiteSplineSegment1d(t1, t2, f1, f2, mm, mr)
                                    )
                                ))
                            }

                            val positionSpline = spline(
                                x0, x1, x2,
                                y0, y1, y2
                            )

                            val timeSpline = spline(
                                x0, x1, x2,
                                t0, t1, t2
                            )

                            val valueSpline = spline(
                                x0, x1, x2,
                                samplesLeft[hoveredChannel].toDouble(),
                                samplesMid[hoveredChannel].toDouble(),
                                samplesRight[hoveredChannel].toDouble()
                            )

                            val tests = 100
                            val dx = (x2 - x0) / tests

                            var bestDistance = Double.MAX_VALUE
                            var resultX = Double.NaN
                            var resultY = Double.NaN

                            (0..tests).forEach { i ->
                                val x = x0 + dx * i
                                val y = positionSpline.evaluate(x)

                                val dx = pMouseX - x
                                val dy = pMouseY - y

                                val distance = dx * dx + dy * dy
                                if(distance < bestDistance) {
                                    bestDistance = distance
                                    resultX = x
                                    resultY = y
                                }
                            }

                            sample = valueSpline.evaluate(resultX).toFloat()
                            time = timeSpline.evaluate(resultX)
                            hoveredX = resultX.toFloat()
                            hoveredY = resultY.toFloat()
                        }
                    }
                    else {
                        // Not enough to interpolate
                        discreteSampler()
                    }

                    // Draws horizontal line on wave:
                    poseStack.preserve {
                        poseStack.translate(
                            0.0f,
                            hoveredY,
                            0.0f)

                        pGuiGraphics.hLine(
                            corner, corner + sizeX,
                            0,
                            MyColor.WHITE.data
                        )
                    }

                    // Draws vertical line on wave:
                    poseStack.preserve {
                        poseStack.translate(
                            hoveredX,
                            0.0f,
                            0.0f
                        )

                        pGuiGraphics.vLine(
                            0,
                            corner, corner + sizeY,
                            MyColor.WHITE.data
                        )
                    }

                    // Draws tooltip on mouse:
                    poseStack.preserve {
                        poseStack.translate(pMouseX, pMouseY, 0f)

                        val tooltip = listOf(
                            Component.literal("T+${time.rounded(2)}"),
                            Component.literal(sampleText(sample, hoveredChannel))
                        )

                        pGuiGraphics.renderComponentTooltip(
                            font,
                            tooltip,
                            0,
                            0
                        )
                    }
                }
            }

            //#endregion

            //#region Timeline

            // Renders some time points on the bottom of the graph:
            val timePoints = 31
            val detailedLineInterval = 5
            repeat(timePoints) { timePoint ->
                val x = map(
                    timePoint.toFloat(),
                    0.0f, timePoints.toFloat() - 1.0f,
                    corner.toFloat(), corner.toFloat() + sizeX
                ).roundToInt()

                val isDetailedLine = timePoint % detailedLineInterval == 0

                val height = ceil(pGuiGraphics.guiHeight() * if(isDetailedLine) 0.02f else 0.01f).toInt()

                val offsetIntoGraph = 2

                pGuiGraphics.vLine(
                    x,
                    corner + sizeY - offsetIntoGraph,
                    corner + sizeY - offsetIntoGraph + height,
                    MyColor(255, 255, 0, 0).data
                )

                if(isDetailedLine) {
                    val labelScale = 0.5f
                    val offsetY = 0.5f

                    poseStack.preserve {
                        poseStack.translate(
                            x.toFloat(),
                            corner + sizeY - offsetIntoGraph + height.toFloat() + offsetY,
                            0.0f
                        )

                        poseStack.scale(textScale * labelScale, textScale * labelScale, 1.0f)

                        val window = renderState.texture.columnCount * CellGraph.DT

                        val offset = map(
                            x.toDouble(),
                            corner.toDouble(), corner.toDouble() + sizeX,
                            -window, 0.0
                        )

                        pGuiGraphics.drawCenteredString(
                            font,
                            offset.rounded(2).toString() + "s",
                            0, 0,
                            MyColor.WHITE.data
                        )
                    }
                }
            }

            //#endregion

            scroll = 0.0
        }

        private fun dispatchScope(poseStack: PoseStack, x: Float, y: Float, width: Float, height: Float) {
            RenderSystem.enableBlend()

            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)

            val pose = poseStack.last().pose()
            val tesselator = Tesselator.getInstance()
            val builder = tesselator.builder

            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            builder.vertex(pose, x, y + height, 0f).uv(0f, 1f).endVertex()
            builder.vertex(pose, x + width, y + height, 0f).uv(1f, 1f).endVertex()
            builder.vertex(pose, x + width, y, 0f).uv(1f, 0f).endVertex()
            builder.vertex(pose, x, y, 0f).uv(0f, 0f).endVertex()
            tesselator.end()
        }
    }

    override fun createMenu(pContainerId: Int, pPlayerInventory: Inventory, pPlayer: Player): AbstractContainerMenu {
        return OscilloscopeMenu(pContainerId, this)
    }

    //#endregion

    @ServerOnly
    override fun getServerSaveTag(): CompoundTag {
        val tag = super.getServerSaveTag()
        serverParameters!!.saveToTag(tag)
        return tag
    }

    @ServerOnly
    override fun loadServerSaveTag(tag: CompoundTag) {
        super.loadServerSaveTag(tag)
        serverParameters!!.loadFromTag(tag)
    }

    //#region Simulation Setup and Data Export

    @ServerOnly
    override fun onCellAcquired() {
        super.onCellAcquired()
        cell.bind(this::exportFromSimulation)
    }

    /**
     * Exports the samples from the simulation, to the clients.
     * @param samples The **raw** samples directly read from the resistors, not mapped.
     * @param timestamp A consistent and strictly increasing timestamp taken from the simulation thread (for calculating deltas).
     * The samples are mapped **and clipped** to [-1, +1] with the linear maps in [serverParameters] here (and also sanitized), before being sent to the clients.
     * */
    @ServerOnly
    private fun exportFromSimulation(samples: FloatArray, timestamp: Double) {
        val options = serverParameters!!

        val mappedSamples = FloatArray(samples.size)

        samples.indices.forEach { channel ->
            val rawSample = samples[channel]

            if(rawSample.isNaN()) {
                mappedSamples[channel] = rawSample
            }
            else {
                val params = options.channelParameters[channel]

                // Here, we map to -1, 1 using the [signalMin, signalMax].
                // This is for the raw GPU encoding. The [displayMin, displayMax] are for labels and the sidebar.

                var result = map(
                    rawSample,
                    params.signalMin, params.signalMax,
                    -1.0f, 1.0f
                )

                // Clipping:
                result = result.coerceIn(-1.0f, 1.0f)

                // Sanitize:
                if(result.isNaN() || result.isInfinite()) {
                    result = 0.0f
                }

                if(result.approxEq(0.0f, 1e-6f)){
                    result = 0.0f
                }

                mappedSamples[channel] = result
            }
        }

        sendBulkPacket(
            OscilloscopeSyncMessage::serialize,
            OscilloscopeSyncMessage(
                mappedSamples,
                timestamp,
                options
            )
        )
    }

    @ServerOnly
    override fun onCellReleased() {
        super.onCellReleased()
        cell.unbind()
    }

    //#endregion

    //#region Client Data Import

    @ClientOnly
    override fun setupPacketsOnClient(builder: ClientSidePacketHandlerBuilder) {
        super.setupPacketsOnClient(builder)
        builder.withHandler<OscilloscopeSyncMessage>(OscilloscopeSyncMessage::deserialize, this::importFromSimulation)
    }

    @OnClientThread
    private fun importFromSimulation(message: OscilloscopeSyncMessage) {
        requireIsOnRenderThread {
            "OscilloscopePart#handleMessage"
        }

        var currentState = clientSide

        if(currentState == null) {
            // Create for the first time:
            currentState = OscilloscopeClientSide(message.options, specification.channelCount)
            clientSide = currentState
        }
        else {
            if(currentState.serverOptions.timeWindow != message.options.timeWindow) {
                // Re-create the buffers with the new depth:
                currentState.close()
                val previousState = currentState
                // Copies the samples from the old buffer to the new buffer:
                currentState = OscilloscopeClientSide(
                    message.options,
                    specification.channelCount,
                    previousState
                )
                clientSide = currentState
            }
            else {
                // Just replace the other settings and keep the current buffer:
                currentState.serverOptions = message.options
            }
        }

        currentState.enqueueForTransfer(message.normalizedSamples, message.timestamp)
    }

    //#endregion

    //#region Server GUI Handling

    @ServerOnly
    override fun setupPacketsOnServer(builder: ServerSidePacketHandlerBuilder) {
        builder.withHandler(GuiMessage::deserialize, this::applyGUIChanges)
    }

    @ServerOnly
    private fun applyGUIChanges(packet: GuiMessage, sender: ServerPlayer) {
        if (!isAllowedToSendGUIChanges(sender)) {
            LOG.error("Client $sender tried to apply oscilloscope changes without being allowed")
            return
        }

        val currentSettings = serverParameters!!
        val targetSettings = packet.parameters

        if (currentSettings.channelParameters.size != targetSettings.channelParameters.size) {
            LOG.error("Client $sender tried to send ${targetSettings.channelParameters.size} channels to oscilloscope with ${currentSettings.channelParameters.size}")
            return
        }

        currentSettings.timeWindow = targetSettings.timeWindow.coerceIn(specification.minWindow, specification.maxWindow)

        currentSettings.channelParameters.indices.forEach { i ->
            val a = currentSettings.channelParameters[i]
            val b = targetSettings.channelParameters[i]

            a.signalMin = sanitizeSignalRange(b.signalMin)
            a.signalMax = sanitizeSignalRange(b.signalMax)
            a.displayMin = sanitizeOutputRange(b.displayMin)
            a.displayMax = sanitizeOutputRange(b.displayMax)
            a.unit = sanitizeUnit(b.unit)
        }

        setSaveDirty()
    }

    //#endregion

    /**
     * Uber update message. We don't mind a little excess data.
     * @param normalizedSamples Samples mapped and clipped to [-1, 1] using the linear map in [options].
     * @param timestamp The strictly-increasing simulation time.
     * @param options The latest parameters.
     * */
    private class OscilloscopeSyncMessage(val normalizedSamples: FloatArray, val timestamp: Double, val options: OscilloscopeParameters) {
        companion object {
            fun serialize(packet: OscilloscopeSyncMessage, buffer: FriendlyByteBuf) {
                buffer.writeFloatArray(packet.normalizedSamples)
                buffer.writeDouble(packet.timestamp)
                OscilloscopeParameters.serialize(packet.options, buffer)
            }

            fun deserialize(buffer: FriendlyByteBuf) = OscilloscopeSyncMessage(
                buffer.readFloatArray(),
                buffer.readDouble(),
                OscilloscopeParameters.deserialize(buffer)
            )
        }
    }

    /**
     * Message with settings set by a client.
     * */
    private class GuiMessage(val parameters: OscilloscopeParameters) {
        companion object {
            fun serialize(packet: GuiMessage, buffer: FriendlyByteBuf) {
                OscilloscopeParameters.serialize(packet.parameters, buffer)
            }

            fun deserialize(buffer: FriendlyByteBuf) = GuiMessage(
                OscilloscopeParameters.deserialize(buffer)
            )
        }
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        for(i in 0 until specification.channelCount) {
            builder.debugInIDE { "Ch$i: ${cell.oscilloscope.getPotential(i).rounded()}" }
        }
    }

    companion object {
        private val TITLE = Component.translatable("screen.$MODID.oscilloscope")
    }
}
