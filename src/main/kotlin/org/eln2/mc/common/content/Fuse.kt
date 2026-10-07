package org.eln2.mc.common.content

import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraftforge.registries.ForgeRegistries
import org.ageseries.libage.data.*
import org.ageseries.libage.mathematics.approxEq
import org.ageseries.libage.sim.ConnectionParameters
import org.ageseries.libage.sim.Simulator
import org.ageseries.libage.sim.ThermalMass
import org.ageseries.libage.sim.ThermalMassDefinition
import org.ageseries.libage.sim.electrical.ElectricalSimulation
import org.eln2.mc.*
import org.eln2.mc.client.render.FlwMaterials
import org.eln2.mc.client.render.FlwModels
import org.eln2.mc.client.render.foundation.PartialModelHelper
import org.eln2.mc.client.render.foundation.partTransformation
import org.eln2.mc.common.blocks.foundation.MultipartVisualizationContext
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.content.modules.Eln2BasicComponents
import org.eln2.mc.common.events.runPre
import org.eln2.mc.common.parts.foundation.AbstractPartVisual
import org.eln2.mc.common.parts.foundation.CellPart
import org.eln2.mc.common.parts.foundation.PartCreateInfo
import org.eln2.mc.common.parts.foundation.PartUseInfo
import org.eln2.mc.extensions.*
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import kotlin.math.abs

data class FuseModel(
    val materialName: String,
    val ratedCurrent: Quantity<Current>,
    val resistance: Quantity<Resistance>,
    val massDef: ThermalMassDefinition,
    val meltingPoint: Quantity<Temperature>,
    val leakage: ConnectionParameters
)

class FuseItem(val model: FuseModel) : Item(Properties()) {

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)

        val gray = ChatFormatting.GRAY
        val yellow = ChatFormatting.YELLOW
        val red = ChatFormatting.RED
        val aqua = ChatFormatting.AQUA

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.fuse.material")
                .append(": ")
                .append(Component.translatable(getMaterialKey(model.materialName)).withStyle(gray))
                .withStyle(yellow)
        )

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.fuse.rated_current")
                .append(": ")
                .append(Component.literal(Eln2Config.clientConfig.classifyWithOverride(model.ratedCurrent)).withStyle(aqua))
                .withStyle(yellow)
        )

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.fuse.resistance")
                .append(": ")
                .append(Component.literal(Eln2Config.clientConfig.classifyWithOverride(model.resistance)).withStyle(gray))
                .withStyle(yellow)
        )

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.fuse.melting_point")
                .append(": ")
                .append(Component.literal(Eln2Config.clientConfig.classifyWithOverride(model.meltingPoint)).withStyle(gray))
                .withStyle(yellow)
        )

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.fuse.single_use")
                .withStyle(red)
        )
    }
}

class BurntFuseItem : Item(Properties()) {

    override fun appendHoverText(
        pStack: ItemStack,
        pLevel: Level?,
        pTooltipComponents: MutableList<Component>,
        pIsAdvanced: TooltipFlag,
    ) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced)

        pTooltipComponents.add(
            Component.translatable("tooltip.eln2.fuse.burnt")
                .withStyle(ChatFormatting.DARK_RED)
        )
    }
}

class FusePanelCell(ci: CellCreateInfo, override val electricalMap: PoleMap) : Cell(ci), SidedElectricalMapped<FusePanelCell> {
    companion object {
        private const val ITEM_ID = "item"
        private const val TEMPERATURE = "temperature"

        private fun createFunctionalState(environment: CellEnvironment, item: FuseItem) : FunctionalFuseState {
            val mass = item.model.massDef()
            environment.loadTemperature(mass)

            val simulator = Simulator()
            simulator.add(mass)
            simulator.connect(mass, environment.ambientTemperature, item.model.leakage)

            return FunctionalFuseState(item, mass, simulator)
        }
    }

    override val electricalSize: ElectricalSize
        get() = ElectricalSize.Standard

    interface FuseState

    object BurntFuseState : FuseState

    class FunctionalFuseState(
        val fuseItem: FuseItem,
        val thermalMass: ThermalMass,
        val environmentSimulator: Simulator
    ) : FuseState

    var fuseState: FuseState? = null
        private set

    var eventConsumer: FuseEventConsumer? = null
        private set

    fun bind(consumer: FuseEventConsumer) {
        require(eventConsumer == null)
        eventConsumer = consumer
    }

    fun unbind() {
        eventConsumer = null
    }

    @SimObject
    val electrical = PolarResistorObject<FusePanelCell>(this, electricalMap).also {
        it.component.resistance = ElectricalSimulation.MAX_RESISTANCE
    }

    @Behavior
    val dielectricBreakdown = DielectricBreakdownBehavior.create(this).also {
        it.addPort(
            electrical.component,
            !Quantity(1800.0, VOLT),
            !Quantity(1800.0, VOLT),
        )
    }

    /**
     * Inserts a new fuse item. There must be no fuse state already present.
     * Must be called under a sync barrier.
     * */
    @OnServerThread
    fun insertItem(newItem: FuseItem) {
        requireIsOnServerThread()
        check(fuseState == null) { "Cannot insert fuse with an existing state" }

        fuseState = createFunctionalState(environmentData, newItem)
        electrical.component.resistance = !newItem.model.resistance
        setChanged()
        eventConsumer?.onFuseStateChanged()
    }

    /**
     * Removes the current fuse item.
     * Must be called under a sync barrier.
     * @return The existing item. Can be either a [FuseItem] or a [BurntFuseItem] if present, or null.
     * */
    @OnServerThread
    fun removeItem() = when(val state = fuseState) {
        null -> null
        is BurntFuseState -> {
            fuseState = null
            setChanged()
            eventConsumer?.onFuseStateChanged()
            Eln2BasicComponents.BURNT_FUSE_ITEM.get()
        }
        is FunctionalFuseState -> {
            fuseState = null
            setChanged()
            electrical.component.resistance = ElectricalSimulation.MAX_RESISTANCE
            eventConsumer?.onFuseStateChanged()
            state.fuseItem
        }
        else -> error("Invalid fuse state $fuseState")
    }

    override fun subscribe(subscribers: SubscriberCollection<SimulationPhase>) {
        subscribers.addPost(this::tickPost)
    }

    fun tickPost(dt: Double, phase: SimulationPhase) {
        val state = fuseState as? FunctionalFuseState
            ?: return

        val t = state.thermalMass.temperature
        state.thermalMass.energy += Quantity(abs(electrical.component.power * dt), JOULE)
        state.environmentSimulator.step(dt)
        setChangedIf(!t.value.approxEq(!state.thermalMass.temperature, 0.1))

        if(state.thermalMass.temperature > state.fuseItem.model.meltingPoint) {
            fuseState = BurntFuseState
            electrical.component.resistance = ElectricalSimulation.MAX_RESISTANCE
            setChanged()
            eventConsumer?.onFuseStateChanged()
            eventConsumer?.playAv()
        }
    }

    enum class StateIndicator(val friendlyName: String) {
        None("none"),
        Burnt("burnt"),
        Functional("functional");

        fun put(tag: CompoundTag) {
            tag.putString(ID, friendlyName)
        }

        companion object {
            private const val ID = "fuseStateIndicator"

            fun get(tag: CompoundTag) = when(tag.getString(ID)) {
                None.friendlyName -> None
                Burnt.friendlyName -> Burnt
                Functional.friendlyName -> Functional
                else -> error("Invalid fuse tag $tag")
            }
        }
    }

    override fun saveCellData(): CompoundTag {
        val tag = CompoundTag()

        when(val state = fuseState) {
            null -> StateIndicator.None.put(tag)
            is BurntFuseState -> StateIndicator.Burnt.put(tag)
            is FunctionalFuseState -> {
                StateIndicator.Functional.put(tag)

                val id = ForgeRegistries.ITEMS.getKey(state.fuseItem) ?: error("Could not get ID of ${state.fuseItem}")
                tag.putResourceLocation(ITEM_ID, id)
                tag.putQuantity(TEMPERATURE, state.thermalMass.temperature)
            }
            else -> error("Invalid fuse state $fuseState")
        }

        return tag
    }

    override fun loadCellData(tag: CompoundTag) {
        when(StateIndicator.get(tag)) {
            StateIndicator.None -> {
                fuseState = null
                electrical.component.resistance = ElectricalSimulation.MAX_RESISTANCE
            }
            StateIndicator.Burnt -> {
                fuseState = BurntFuseState
                electrical.component.resistance = ElectricalSimulation.MAX_RESISTANCE
            }
            StateIndicator.Functional -> {
                val id = tag.getResourceLocation(ITEM_ID)
                val temperature = tag.getQuantity<Temperature>(TEMPERATURE)
                val item = (ForgeRegistries.ITEMS.getValue(id) as? FuseItem)
                    ?: error("Could not get fuse item $id")

                fuseState = createFunctionalState(environmentData, item).also {
                    it.thermalMass.temperature = temperature
                }

                electrical.component.resistance = !item.model.resistance
            }
        }
    }
}

interface FuseEventConsumer {
    @CrossThreadAccess
    fun onFuseStateChanged()

    @CrossThreadAccess
    fun playAv()
}

class FusePanelPart(ci: PartCreateInfo) :
    CellPart<FusePanelCell>(ci, Eln2BasicComponents.FUSE_CELL.get()),
    FuseEventConsumer,
    ComponentDisplay
{
    val renderState = if(placement.level.isClientSide) RenderState() else null

    class RenderState {
        var state: FusePanelCell.StateIndicator = FusePanelCell.StateIndicator.None
    }

    override fun onCellAcquired() {
        cell.bind(this)
    }

    override fun onCellReleased() {
        cell.unbind()
    }

    @ServerOnly
    override fun onUsedBy(context: PartUseInfo): InteractionResult {
        if (placement.level.isClientSide || context.hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS
        }

        cell.graph.runSuspended {
            val removedItem = cell.removeItem()

            if(removedItem != null) {
                (context.player as ServerPlayer).addItem(
                    placement.mountingPointWorld,
                    ItemStack(removedItem, 1)
                )

                return InteractionResult.SUCCESS
            }

            val mainHandStack = context.player.mainHandItem

            if(mainHandStack.item is FuseItem) {
                cell.insertItem(mainHandStack.item as FuseItem)
                mainHandStack.shrink(1)
                return InteractionResult.CONSUME
            }

            return InteractionResult.FAIL
        }
    }

    @CrossThreadAccess
    override fun onFuseStateChanged() {
        runPre {
            setSyncDirty()
        }
    }

    @CrossThreadAccess
    override fun playAv() {
        runPre {
            val blockPos = placement.position
            val serverLevel = placement.level as ServerLevel

            serverLevel.playSound(
                null,
                blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble(),
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS,
                randomFloat(0.9f, 1.1f),
                randomFloat(0.9f, 1.1f)
            )


            val (originX, originY, originZ) = placement.mountingPointWorld
            val outDirection = placement.facingWorld
            val dirX = outDirection.stepX.toDouble()
            val dirY = outDirection.stepY.toDouble()
            val dirZ = outDirection.stepZ.toDouble()

            serverLevel.sendParticles(
                ParticleTypes.FLAME,
                originX, originY, originZ,
                12,
                dirX * 0.15, dirY * 0.15, dirZ * 0.15,
                0.6
            )

            serverLevel.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                originX, originY, originZ,
                6,
                dirX * 0.05, dirY * 0.05, dirZ * 0.05,
                0.3
            )

            serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                originX, originY, originZ,
                4,
                dirX * 0.08, dirY * 0.08, dirZ * 0.08,
                0.4
            )
        }
    }

    override fun getSyncTag(): CompoundTag {
        val tag = CompoundTag()

        when(val state = cell.fuseState) {
            null -> FusePanelCell.StateIndicator.None.put(tag)
            is FusePanelCell.BurntFuseState -> FusePanelCell.StateIndicator.Burnt.put(tag)
            is FusePanelCell.FunctionalFuseState -> FusePanelCell.StateIndicator.Functional.put(tag)
            else -> error("Invalid fuse state $state")
        }

        return tag
    }

    override fun handleSyncTag(tag: CompoundTag) {
        renderState!!.state = FusePanelCell.StateIndicator.get(tag)
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        val state = cell.fuseState as? FusePanelCell.FunctionalFuseState
            ?: return

        builder.add(state.fuseItem.defaultInstance.displayName)
        builder.quantity(state.thermalMass.temperature)
        builder.quantity(cell.electrical.component.readouts.current)
    }
}

class FusePanelPartVisual(visualizationContext: MultipartVisualizationContext, part: FusePanelPart) : AbstractPartVisual<FusePanelPart>(visualizationContext, part), SimpleDynamicVisual {
    val base: TransformedInstance = visualizationContext.instancerProvider()
        .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(FlwModels.FUSE_BASE, FlwMaterials.SMOOTH_LIT))
        .createInstance()
        .also { it.partTransformation(visualizationContext.parent, part) }

    var fuse: TransformedInstance? = null
    var state = FusePanelCell.StateIndicator.None

    override fun updateLight(p0: Float) {
        visualizationContext.parent.relightInstances(base, fuse)
    }

    private fun ensureInstanceCreated() {
        if(fuse != null) {
            return
        }

        fuse = visualizationContext.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, PartialModelHelper.applyMaterial(FlwModels.FUSE_FUSE, FlwMaterials.SMOOTH_LIT))
            .createInstance()
            .also {
                it.partTransformation(visualizationContext.parent, part)
                visualizationContext.parent.relightInstances(it)
            }
    }

    override fun beginFrame(p0: DynamicVisual.Context?) {
        val rendererState = part.renderState!!.state

        if(rendererState == state) {
            return
        }

        state = rendererState

        when(rendererState) {
            FusePanelCell.StateIndicator.None -> {
                fuse?.delete()
                fuse = null
            }
            FusePanelCell.StateIndicator.Burnt -> {
                ensureInstanceCreated()
                fuse!!.color(0.25f, 0.25f, 0.25f)
                fuse!!.setChanged()
            }
            FusePanelCell.StateIndicator.Functional -> {
                ensureInstanceCreated()
                fuse!!.color(1.0f, 1.0f, 1.0f)
                fuse!!.setChanged()
            }
        }
    }

    override fun _delete() {
        base.delete()
        fuse?.delete()
    }
}
