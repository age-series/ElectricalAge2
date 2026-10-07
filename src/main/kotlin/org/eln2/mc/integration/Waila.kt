package org.eln2.mc.integration

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.LiteralContents
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec2
import org.ageseries.libage.data.*
import org.ageseries.libage.utils.sourceName
import org.eln2.mc.*
import org.eln2.mc.common.blocks.foundation.*
import org.eln2.mc.common.content.processing.*
import org.eln2.mc.common.fluids.foundation.FractionalFluidStack
import org.eln2.mc.common.fluids.foundation.MultipleFractionalFluidTank
import org.eln2.mc.common.fluids.foundation.PhysicalFluidManager
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.specs.foundation.CellSpec
import org.eln2.mc.common.specs.foundation.GridSpec
import org.eln2.mc.common.specs.foundation.SpecContainerPart
import org.eln2.mc.extensions.forEachCompound
import org.eln2.mc.extensions.formattedPercentNormalized
import org.eln2.mc.extensions.getListTag
import snownee.jade.api.*
import snownee.jade.api.config.IPluginConfig
import snownee.jade.api.fluid.JadeFluidObject
import java.util.function.Supplier
import kotlin.math.absoluteValue

@WailaPlugin
class Eln2WailaPlugin : IWailaPlugin {
    override fun register(registration: IWailaCommonRegistration) {
        registration.registerBlockDataProvider(DistillationFluidProvider, PhaseChangeModuleBlockEntity::class.java)
        registration.registerBlockDataProvider(ElectrolysisProxyProvider, ElectrolysisProxyBlockEntity::class.java)
        registration.registerBlockDataProvider(ComponentDisplayProvider, BlockEntity::class.java)
    }

    override fun registerClient(registration: IWailaClientRegistration) {
        registration.registerBlockComponent(DistillationFluidProvider, PhaseChangeModuleBlock::class.java)
        registration.registerBlockComponent(ElectrolysisProxyProvider, ElectrolysisProxyBlock::class.java)
        registration.registerBlockComponent(ComponentDisplayProvider, Block::class.java)

        registration.addRayTraceCallback { _, accessor, _ ->
            if (accessor is BlockAccessor) {
                val representativePos = when {
                    accessor.block is ElectrolysisProxyBlock -> null
                    else -> when {
                        accessor.block is MultiblockDelegateBlock -> {
                            val delegateBlockEntity = accessor.blockEntity
                                as? MultiblockDelegateBlockEntity

                            delegateBlockEntity?.representativePos
                        }
                        accessor.block is MultiblockDelegateUprightHorizontalDirectionCellBlock<*> -> {
                            val delegateBlockEntity = accessor.blockEntity
                                as? MultiblockDelegateCellBlockEntity<*>

                            delegateBlockEntity?.representativePos
                        }
                        else -> {
                            null
                        }
                    }
                }

                if(representativePos != null) {
                    return@addRayTraceCallback registration
                        .blockAccessor()
                        .from(accessor)
                        .blockState(accessor.level.getBlockState(representativePos))
                        .blockEntity(accessor.level.getBlockEntity(representativePos))
                        .build()
                }
            }

            return@addRayTraceCallback accessor
        }
    }

    private object ComponentDisplayProvider : IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        private const val COMPONENT_DISPLAY = "component_display"

        override fun getUid() = resource(COMPONENT_DISPLAY)

        private inline fun<reified T> castGameObject(p1: BlockAccessor) : T? {
            val blockEntity = p1.blockEntity
                ?: return null

            if(blockEntity is MultipartBlockEntity) {
                val part = blockEntity.pickPart(p1.player)

                if(part is SpecContainerPart) {
                    val spec = part.pickSpec(p1.player)?.second

                    if(spec is GridSpec) {
                        val terminal = spec.pickTerminal(p1.player)

                        if(terminal is T) {
                            return terminal
                        }
                    }

                    return spec as? T
                }

                return part as? T
            }

            return blockEntity as? T
        }

        override fun appendServerData(p0: CompoundTag, p1: BlockAccessor) {
            val display = castGameObject<ComponentDisplay>(p1)

            val components = mutableListOf<Component>()
            val builder = ComponentDisplayList(components)

            if(display != null) {
                try {
                    run {
                        if(display is CellBlockEntity<*>) {
                            if(!display.hasCell) {
                                return@run
                            }
                        }

                        if(display is CellPart<*>) {
                            if(!display.hasCell) {
                                return@run
                            }
                        }

                        if(display is CellSpec<*>) {
                            if(!display.hasCell) {
                                return@run
                            }
                        }

                        display.submitDisplay(builder)
                    }
                } catch (e : Throwable) {
                    LOG.error(DEBUGGER_BREAK("Display error $display: $e"))
                }
            }

            if(components.isNotEmpty()) {
                p0.put(COMPONENT_DISPLAY, packComponentList(components))
            }
        }

        override fun appendTooltip(p0: ITooltip, p1: BlockAccessor, p2: IPluginConfig) {
            val tag = p1.serverData.get(COMPONENT_DISPLAY) as? CompoundTag
                ?: return

            val components = unpackComponentList(tag)

            components.forEach {
                p0.add(it)
            }
        }
    }

    /**
     * Shows liquids and gases.
     * */
    private object DistillationFluidProvider : IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        override fun getUid() = resource("distillation_fluids")

        override fun appendServerData(data: CompoundTag, accessor: BlockAccessor) {
            val module = accessor.blockEntity as? PhaseChangeModuleBlockEntity
                ?: return

            val fluids = ListTag()

            fun addTank(tank: MultipleFractionalFluidTank) {
                tank.fluids.forEach { stack ->
                    if (!stack.isEmpty) {
                        fluids.add(stack.toNbt())
                    }
                }
            }

            addTank(module.liquidTank)
            addTank(module.gasTank)

            data.put("fluids", fluids)
        }

        override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
            if (!accessor.serverData.contains("fluids")) {
                return
            }

            val list = accessor.serverData.getListTag("fluids")
            val helper = tooltip.elementHelper

            val liquids = ArrayList<FractionalFluidStack>()
            val gases = ArrayList<FractionalFluidStack>()

            list.forEachCompound { tag ->
                val stack = FractionalFluidStack.fromNbt(tag)

                if (!stack.isEmpty) {
                    val thermalFluid = PhysicalFluidManager.getProperties(stack.fluid)

                    if(thermalFluid != null) {
                        if(thermalFluid.isGaseous) {
                            gases.add(stack)
                        }
                        else {
                            liquids.add(stack)
                        }
                    }
                    else {
                        if (stack.fluid.fluidType.density < 0 || stack.fluid.fluidType.isLighterThanAir) {
                            gases.add(stack)
                        }
                        else {
                            liquids.add(stack)
                        }
                    }
                }
            }

            val scale = Eln2Config.clientConfig.getScaleOverride(Volume::class.java)

            fun renderRow(stack: FractionalFluidStack) {
                tooltip.add(
                    helper
                        .fluid(JadeFluidObject.of(stack.fluid, stack.unit().amount.toLong()))
                        .size(Vec2(12.0f, 12.0f)))

                tooltip.append(
                    helper.text(stack.unit().displayName).apply {
                        translate(Vec2(2.0f, 3.0f))
                    }
                )

                val quantity = if(scale == null) {
                    Quantity(stack.amount, LITER).classify()
                }
                else {
                    classifyAuxiliary(scale, !Quantity(stack.amount, LITER))
                }

                tooltip.append(
                    helper.text(Component.literal(quantity)).apply {
                        translate(Vec2(4.0f, 3.0f))
                    }
                )
            }

            fun renderCollection(fluids: List<FractionalFluidStack>) {
                if(fluids.isEmpty()) {
                    return
                }

                fluids.forEach {
                    renderRow(it)
                    tooltip.add(helper.spacer(0, 2))
                }

                tooltip.add(helper.spacer(0, 5))
            }

            renderCollection(liquids)
            renderCollection(gases)
        }
    }

    /**
     * Shows input liquids, output liquids and output gases.
     * */
    private object ElectrolysisProxyProvider : IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        override fun getUid() = resource("electrolysis")

        override fun appendServerData(data: CompoundTag, accessor: BlockAccessor) {
            val module = accessor.blockEntity as? ElectrolysisProxyBlockEntity
                ?: return

            if(!accessor.level.isLoaded(module.representativePos)) {
                return
            }

            val representative = accessor.level.getBlockEntity(module.representativePos) as? ElectrolysisMainBlockEntity
                ?: return

            fun addTank(tag: ListTag, tank: MultipleFractionalFluidTank) {
                tank.fluids.forEach { stack ->
                    if (!stack.isEmpty) {
                        tag.add(stack.toNbt())
                    }
                }
            }

            val fluidSide = if(!representative.inventoryHandler.hasSeparator() || module.isLeftDelegate()) {
                representative.fluidHandler.anode
            } else {
                representative.fluidHandler.cathode
            }

            val inputLiquids = ListTag()
            val outputLiquids = ListTag()
            val outputGases = ListTag()

            addTank(inputLiquids, fluidSide.inputLiquidTank)
            addTank(outputLiquids, fluidSide.outputLiquidTank)
            addTank(outputGases, fluidSide.outputGasTank)

            val fluids = CompoundTag()
            fluids.put("inputLiquids", inputLiquids)
            fluids.put("outputLiquids", outputLiquids)
            fluids.put("outputGases", outputGases)

            data.put("electrolysis_fluids", fluids)
        }

        override fun appendTooltip(tooltip: ITooltip, accessor: BlockAccessor, config: IPluginConfig) {
            if (!accessor.serverData.contains("electrolysis_fluids")) {
                return
            }

            val fluids = accessor.serverData.getCompound("electrolysis_fluids")
            val inputLiquids = fluids.getListTag("inputLiquids").map { FractionalFluidStack.fromNbt(it as CompoundTag) }
            val outputLiquids = fluids.getListTag("outputLiquids").map { FractionalFluidStack.fromNbt(it as CompoundTag) }
            val outputGases = fluids.getListTag("outputGases").map { FractionalFluidStack.fromNbt(it as CompoundTag) }

            val helper = tooltip.elementHelper
            val scale = Eln2Config.clientConfig.getScaleOverride(Volume::class.java)

            fun renderRow(stack: FractionalFluidStack) {
                tooltip.add(
                    helper
                        .fluid(JadeFluidObject.of(stack.fluid, stack.unit().amount.toLong()))
                        .size(Vec2(12.0f, 12.0f)))

                tooltip.append(
                    helper.text(stack.unit().displayName).apply {
                        translate(Vec2(2.0f, 3.0f))
                    }
                )

                val quantity = if(scale == null) {
                    Quantity(stack.amount, LITER).classify()
                }
                else {
                    classifyAuxiliary(scale, !Quantity(stack.amount, LITER))
                }

                tooltip.append(
                    helper.text(Component.literal(quantity)).apply {
                        translate(Vec2(4.0f, 3.0f))
                    }
                )
            }

            fun renderCollection(fluids: List<FractionalFluidStack>) {
                if(fluids.isEmpty()) {
                    return
                }

                fluids.forEach {
                    renderRow(it)
                    tooltip.add(helper.spacer(0, 2))
                }

                tooltip.add(helper.spacer(0, 5))
            }

            renderCollection(inputLiquids)
            renderCollection(outputLiquids)
            renderCollection(outputGases)
        }
    }
}

/**
 * Implemented by classes that want to export simple text data to JADE quickly.
 * Only [Component]s are supported.
 * */
interface ComponentDisplay {
    fun submitDisplay(builder: ComponentDisplayList)
}

private const val ENTRIES = "entries"
private const val JSON = "json"

private fun unpackComponentList(tag: CompoundTag): List<Component> {
    val listTag = tag.get(ENTRIES) as? ListTag

    if (listTag == null || listTag.isEmpty()) {
        return emptyList()
    }

    val results = ArrayList<Component>(listTag.size)

    listTag.forEachCompound {
        val text = it.getString(JSON)
            ?: return@forEachCompound

        val component = Component.Serializer.fromJson(text)
            ?: return@forEachCompound

        for (i in component.siblings.indices) {
            val sibling = component.siblings[i]
            val contents = sibling.contents

            if(contents is LiteralContents) {
                if(contents.text.startsWith(ComponentDisplayList.QUANTITY_PREFIX)) {
                    val dimensionName = contents.text
                        .removePrefix(ComponentDisplayList.QUANTITY_PREFIX)
                        .substringBefore(ComponentDisplayList.QUANTITY_SUFFIX)

                    val number = contents.text
                        .substringAfter(ComponentDisplayList.QUANTITY_SUFFIX)
                        .toDouble()

                    val dimensionClass = checkNotNull(DIMENSION_TYPES.backward[dimensionName])
                    val auxiliaryScale = Eln2Config.clientConfig.getScaleOverride(dimensionClass)

                    component.siblings[i] = Component.literal(
                        if(auxiliaryScale == null) {
                            classify(dimensionClass, number)
                        }
                        else {
                            classifyAuxiliary(auxiliaryScale, number)
                        }
                    )
                }
            }
        }

        results.add(component)
    }

    return results
}

private fun packComponentList(components: List<Component>) : CompoundTag {
    val tag = CompoundTag()
    val listTag = ListTag()

    components.forEach {
        val text = Component.Serializer.toJson(it)
        val compound = CompoundTag()
        compound.putString(JSON, text)
        listTag.add(compound)
    }

    tag.put(ENTRIES, listTag)

    return tag
}

class ComponentDisplayList(private val entries: MutableList<Component>) {
    companion object {
        private const val QUANTITY_IDENTIFIER = "Eln2Quantity"
        const val QUANTITY_PREFIX = "$QUANTITY_IDENTIFIER["
        const val QUANTITY_SUFFIX = "]"

        const val EPS = 1e-4
    }

    fun add(component: Component) {
        entries.add(component)
    }

    private fun translationKey(identifier: String): String {
        return "waila.$MODID.$identifier"
    }

    @Deprecated("Use debugInIDE")
    fun debug(text: String) {
        add(Component.literal("*$text"))
    }

    /**
     * Displays if the mod is running in IDE.
     * */
    fun debugInIDE(supplier: Supplier<String>) {
        if(ELN2_DEBUG) {
            @Suppress("DEPRECATION")
            debug("*" + supplier.get()) // another star to indicate [debugInIDE] was called and not just [debug]
        }
    }

    fun translatePercent(key: String, percentage: Double) {
        add(
            Component.translatable(translationKey(key)).apply {
                append(": ")
                append(percentage.formattedPercentNormalized())
            }
        )
    }

    fun translateRow(key: String, text: String) {
        add(
            Component.translatable(translationKey(key)).apply {
                append(": ")
                append(text)
            }
        )
    }

    fun translateBoolean(key: String, value: Boolean) {
        add(
            Component.translatable(translationKey(key)).apply {
                append(": ")
                append(
                    Component.translatable(
                        if(value) {
                            translationKey("boolean_true")
                        }
                        else {
                            translationKey("boolean_false")
                        }
                    )
                )
            }
        )
    }

    // Not sure how to do it with Component in a cleaner way -- Component hard-codes
    // serialization for contents, so we can't just make QuantityContents or something like that without some involved
    // mixin (there's no easy place to attach to) so I rather just do it like this

    inline fun<reified T> translateQuantityRow(key: String, quantity: Quantity<T>, eps: Double = 1e-6) {
        val name = DIMENSION_TYPES.forward[T::class.java]

        checkNotNull(name) {
            "Invalid dimension ${T::class.java}"
        }

        translateRow(
            key,
            "${QUANTITY_PREFIX}${name}${QUANTITY_SUFFIX}${if(quantity.value.absoluteValue < eps) 0.0 else quantity.value}"
        )
    }

    data class Domain(val identifier: String) {
        companion object {
            val None = Domain("implicit")
            val Electrical = Domain("electrical")
            val Thermal = Domain("thermal")
        }
    }

    inline fun<reified T> quantity(quantity: Quantity<T>, domain: Domain = Domain.None, eps: Double = EPS) {
        translateQuantityRow(T::class.java.sourceName() + "_${domain.identifier}", quantity, eps)
    }

    inline fun<reified T> quantityInput(quantity: Quantity<T>, domain: Domain = Domain.None,eps: Double = EPS) {
        translateQuantityRow(T::class.java.sourceName() + "_input_${domain.identifier}", quantity, eps)
    }

    inline fun<reified T> quantityOutput(quantity: Quantity<T>, domain: Domain = Domain.None,eps: Double = EPS) {
        translateQuantityRow(T::class.java.sourceName() + "_output_${domain.identifier}", quantity, eps)
    }

    inline fun<reified T> quantitySetpoint(quantity: Quantity<T>, domain: Domain = Domain.None,eps: Double = EPS) {
        translateQuantityRow(T::class.java.sourceName() + "_setpoint_${domain.identifier}", quantity, eps)
    }

    inline fun<reified T> quantityMax(quantity: Quantity<T>, domain: Domain = Domain.None,eps: Double = EPS) {
        translateQuantityRow(T::class.java.sourceName() + "_max_${domain.identifier}", quantity, eps)
    }

    inline fun<reified T> quantityInputMax(quantity: Quantity<T>, domain: Domain = Domain.None,eps: Double = EPS) {
        translateQuantityRow(T::class.java.sourceName() + "_input_max_${domain.identifier}", quantity, eps)
    }

    inline fun<reified T> quantityOutputMax(quantity: Quantity<T>, domain: Domain = Domain.None,eps: Double = EPS) {
        translateQuantityRow(T::class.java.sourceName() + "_output_max_${domain.identifier}", quantity, eps)
    }

    inline fun<reified T> quantityDissipated(quantity: Quantity<T>, domain: Domain = Domain.None,eps: Double = EPS) {
        translateQuantityRow(T::class.java.sourceName() + "_dissipated_${domain.identifier}", quantity, eps)
    }

    fun coldTemperature(temperature: Quantity<Temperature>, eps: Double = EPS) {
        translateQuantityRow("cold_temperature", temperature, eps)
    }

    fun hotTemperature(temperature: Quantity<Temperature>, eps: Double = EPS) {
        translateQuantityRow("hot_temperature", temperature, eps)
    }

    fun signalInput(value: Double, eps: Double = EPS) {
        translateQuantityRow("signal_input", Quantity(value, VOLT), eps)
    }

    fun signalOutput(value: Double, eps: Double = EPS) {
        translateQuantityRow("signal_output", Quantity(value, VOLT), eps)
    }

    fun charge(value: Double) = translatePercent("charge", value)
    fun integrity(value: Double) = translatePercent("integrity", value)
    fun progress(value: Double) = translatePercent("progress", value)
    fun efficiency(value: Double) = translatePercent("eta", value)
}
