package org.eln2.mc.common.content

import com.mojang.blaze3d.vertex.PoseStack
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler
import org.ageseries.libage.data.CELSIUS
import org.ageseries.libage.data.Quantity
import org.eln2.mc.*
import org.eln2.mc.client.render.foundation.renderTextured
import org.eln2.mc.common.blocks.foundation.CellBlock
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.cells.foundation.*
import org.eln2.mc.common.events.AtomicUpdate
import org.eln2.mc.control.PIDController
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaEntity
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.*
import kotlin.math.*

class HeatGeneratorFuelMass(var fuelAmount: Double, val energyDensity: Double) {
    companion object {
        private const val AMOUNT = "amount"
        private const val ENERGY_CAPACITY = "energyCapacity"

        fun fromNbt(tag: CompoundTag): HeatGeneratorFuelMass {
            return HeatGeneratorFuelMass(
                tag.getDouble(AMOUNT),
                tag.getDouble(ENERGY_CAPACITY)
            )
        }
    }

    val availableEnergy get() = fuelAmount * energyDensity

    fun removeEnergy(energy: Double) {
        fuelAmount -= energy / energyDensity
    }

    fun toNbt(): CompoundTag {
        return CompoundTag().also {
            it.putDouble(AMOUNT, fuelAmount)
            it.putDouble(ENERGY_CAPACITY, energyDensity)
        }
    }
}

object FuelEnergyDensity {
    const val COAL = 24.0 * 1e6
}

class FuelBurnerBehavior(val cell: Cell, val body: ThermalBody) : CellBehavior, WailaEntity {
    companion object {
        private const val FUEL = "fuel"
        private const val PID = "pid"
        private val DESIRED_TEMPERATURE = Quantity(700.0, CELSIUS)
        private val MAX_POWER = Quantity(10.0, kW)

    }

    private var fuel: HeatGeneratorFuelMass? = null
    private val updates = AtomicUpdate<HeatGeneratorFuelMass>()

    private val pid = PIDController(1.0, 0.0, 0.0).also {
        it.setPoint = 1.0
        it.minControl = 0.0
        it.maxControl = 1.0
    }

    fun updateFuel(mass: HeatGeneratorFuelMass) = updates.setLatest(mass)

    val availableEnergy get() = fuel?.availableEnergy ?: 0.0

    private var signal = 0.0
    private var thermalPower = 0.0

    override fun subscribe(subscribers: SubscriberCollection) = subscribers.addPre(this::simulationTick)

    private fun simulationTick(dt: Double, phase: SubscriberPhase) {
        updates.consume {
            fuel = it
            pid.reset()
            cell.setChanged()
        }

        val fuel = this.fuel ?: return

        val t = (!DESIRED_TEMPERATURE - body.temperatureKelvin) / !DESIRED_TEMPERATURE
        signal = pid.update(t, dt)

        val heat = min(fuel.availableEnergy, signal * !MAX_POWER * dt)

        thermalPower = heat / dt
        // todo fixme frakme
        body.temperatureKelvin = 1200.0
        if(!heat.approxEq(0.0)) {
            fuel.removeEnergy(heat)
            body.energy += heat
            cell.setChanged()
        }
    }

    override fun appendWaila(builder: WailaTooltipBuilder, config: IPluginConfig?) {
        builder.text("Control Signal x1000", (signal * 1000).formatted(2))
        builder.text("Thermal Power", valueText(thermalPower, UnitType.WATT))
        builder.text("Fuel", valueText((fuel?.fuelAmount ?: 0.0) * 1000.0, UnitType.GRAM))
    }

    fun saveNbt() = CompoundTag()
        .withSubTagOptional(FUEL, fuel?.toNbt())
        .withSubTag(PID, pid.saveToNbt())

    fun loadNbt(tag: CompoundTag) = tag
        .useSubTagIfPreset(FUEL) { fuel = HeatGeneratorFuelMass.fromNbt(it) }
        .useSubTagIfPreset(PID) { pid.loadFromNbt(it) }
}

class HeatGeneratorCell(ci: CellCreateInfo, thermalDef: ThermalBodyDef) : Cell(ci) {
    companion object {
        private const val BURNER_BEHAVIOR = "burner"
    }

    @SimObject
    val thermalWire = ThermalWireObject(this, thermalDef)

    @Behavior
    val burner = FuelBurnerBehavior(this, thermalWire.thermalBody)

    init {
        ruleSet.withDirectionRulePlanar(Base6Direction3dMask.HORIZONTALS)
    }

    val needsFuel get() = burner.availableEnergy approxEq 0.0

    fun replaceFuel(mass: HeatGeneratorFuelMass) = burner.updateFuel(mass)

    override fun loadCellData(tag: CompoundTag) {
        tag.useSubTagIfPreset(BURNER_BEHAVIOR, burner::loadNbt)
    }

    override fun saveCellData(): CompoundTag {
        return CompoundTag().withSubTag(BURNER_BEHAVIOR, burner.saveNbt())
    }
}

class HeatGeneratorBlockEntity(pos: BlockPos, state: BlockState) : CellBlockEntity<HeatGeneratorCell>(pos, state, Content.HEAT_GENERATOR_BLOCK_ENTITY.get()) {
    companion object {
        const val FUEL_SLOT = 0

        private const val INVENTORY = "inventory"

        fun tick(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pLevel == null || pBlockEntity == null) {
                return
            }

            if (pBlockEntity !is HeatGeneratorBlockEntity) {
                LOG.error("Got $pBlockEntity instead of heat generator")
                return
            }

            if (!pLevel.isClientSide) {
                pBlockEntity.serverTick()
            }
        }
    }

    class InventoryHandler(val entity: HeatGeneratorBlockEntity) : ItemStackHandler(1) {
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if (stack.item != Items.COAL) {
                return ItemStack.EMPTY
            }

            return super.insertItem(slot, stack, simulate).also {
                entity.inputChanged()
            }
        }
    }

    val inventoryHandler = InventoryHandler(this)
    private val inventoryHandlerLazy = LazyOptional.of { inventoryHandler }

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryHandlerLazy.cast()
        }

        return super.getCapability(cap, side)
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        pTag.put(INVENTORY, inventoryHandler.serializeNBT())
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        pTag.useSubTagIfPreset(INVENTORY, inventoryHandler::deserializeNBT)
    }

    fun serverTick() {
        val cell = cell ?: return

        if (!cell.needsFuel) {
            return
        }

        val stack = inventoryHandler.extractItem(FUEL_SLOT, 1, false)

        if (stack.isEmpty) {
            return
        }

        cell.replaceFuel(HeatGeneratorFuelMass(1.0, FuelEnergyDensity.COAL))

        // Inventory changed:
        setChanged()
    }

    fun inputChanged() {
        setChanged()
    }
}

// FRAK YOU MINECRAFT!!

class HeatGeneratorMenu(pContainerId: Int, playerInventory: Inventory, handler: ItemStackHandler) :
    AbstractContainerMenu(Content.HEAT_GENERATOR_MENU.get(), pContainerId) {

    companion object {
        fun create(id: Int, inventory: Inventory, player: Player, entity: HeatGeneratorBlockEntity): HeatGeneratorMenu {
            return HeatGeneratorMenu(
                id,
                inventory,
                entity.inventoryHandler
            )
        }
    }

    constructor(pContainerId: Int, playerInventory: Inventory) : this(
        pContainerId,
        playerInventory,
        ItemStackHandler(1),
    )

    private val playerGridStart: Int
    private val playerGridEnd: Int

    init {
        addSlot(SlotItemHandler(handler, HeatGeneratorBlockEntity.FUEL_SLOT, 56, 35))

        playerGridStart = 1
        playerGridEnd = playerGridStart + this.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun quickMoveStack(pPlayer: Player, pIndex: Int): ItemStack {
        val slot = slots[pIndex]

        if (!slot.hasItem()) {
            return ItemStack.EMPTY
        }

        val stack = slot.item

        if (pIndex == HeatGeneratorBlockEntity.FUEL_SLOT) {
            // Quick move from input to player

            if (!moveItemStackTo(stack, playerGridStart, playerGridEnd, true)) {
                return ItemStack.EMPTY
            }
        } else {
            // Only move into input slot

            if (!moveItemStackTo(
                    stack,
                    HeatGeneratorBlockEntity.FUEL_SLOT,
                    HeatGeneratorBlockEntity.FUEL_SLOT + 1,
                    true
                )
            ) {
                return ItemStack.EMPTY
            }
        }

        slot.setChanged()

        return stack
    }

    override fun stillValid(pPlayer: Player): Boolean {
        return true
    }
}

class HeatGeneratorScreen(menu: HeatGeneratorMenu, playerInventory: Inventory, title: Component) :
    AbstractContainerScreen<HeatGeneratorMenu>(menu, playerInventory, title) {
    companion object {
        private val TEXTURE = resource("textures/gui/container/furnace_test.png")
        private val TEX_SIZE = Vector2I(256, 256)
        private val BACKGROUND_UV_SIZE = Vector2I(176, 166)
    }

    override fun renderBg(pPoseStack: PoseStack, pPartialTick: Float, pMouseX: Int, pMouseY: Int) {
        renderTextured(
            texture = TEXTURE,
            poseStack = pPoseStack,
            blitOffset = 0,
            color = vec4fOne(),
            position = Vector2I(leftPos, topPos),
            uvSize = BACKGROUND_UV_SIZE,
            uvPosition = Vector2F.zero(),
            textureSize = TEX_SIZE
        )
    }
}

class HeatGeneratorBlock : CellBlock<HeatGeneratorCell>() {
    override fun getCellProvider() = Content.HEAT_GENERATOR_CELL.get()

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return HeatGeneratorBlockEntity(pPos, pState)
    }

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T> {
        return BlockEntityTicker(HeatGeneratorBlockEntity::tick)
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        return pLevel.constructMenu(pPos, pPlayer, { Component.literal("Test") }, HeatGeneratorMenu::create)
    }
}

// Best option is to estimate irradiance but maybe in the future

interface IlluminatedBodyView {
    val sunAngle: Double
    val isObstructed: Boolean
    val normal: Direction
}

abstract class SolarIlluminationBehavior(private val cell: Cell) : CellBehavior, IlluminatedBodyView {
    // Is it fine to access these from our simulation threads?
    override val sunAngle: Double
        get() = cell.graph.level.getSunAngle(0f).toDouble()

    override val isObstructed: Boolean
        get() = !cell.graph.level.canSeeSky(cell.locator.requireLocator<BlockLocator> { "Solar Behaviors require block pos locator" })
}


fun interface PhotovoltaicVoltageFunction {
    fun compute(view: IlluminatedBodyView): Double
}

data class PhotovoltaicModel(
    val voltageFunction: PhotovoltaicVoltageFunction,
    val panelResistance: Double,
)

fun celestialPass(sunAngle: Double) : Rotation2d? {
    val nx = when (sunAngle) {
        in (3.0 / 2.0) * PI..2.0 * PI -> {
            map(sunAngle, (3.0 / 2.0) * PI, 2.0 * PI, 0.0, 0.5)
        }

        in 0.0..PI / 2.0 -> {
            map(sunAngle, 0.0, PI / 2.0, 0.5, 1.0)
        }

        else -> {
            // Under horizon
            return null
        }
    }

    return Rotation2d.exp(PI * nx)
}

fun celestialPass3d(sunAngle: Double) : Vector3d? {
    val pass = celestialPass(sunAngle) ?: return null

    return Vector3d(pass.re, pass.im, 0.0)
}

object PhotovoltaicModels {
    // We map angle difference to a voltage coefficient. 0 - directly overhead, 1 - under horizon
    private val TEST_SPLINE = InterpolatorBuilder()
        .with(0.0, 1.0)
        .with(0.95, 0.8)
        .with(1.0, 0.0)
        .buildCubic()

    private fun voltageTest(maximumVoltage: Double): PhotovoltaicVoltageFunction {
        return PhotovoltaicVoltageFunction { view ->
            if (view.isObstructed) {
                return@PhotovoltaicVoltageFunction 0.0
            }

            val pass = celestialPass3d(view.sunAngle) ?: return@PhotovoltaicVoltageFunction 0.0

            val angle = pass angle view.normal.toVector3d()

            val value = TEST_SPLINE.evaluate(
                map(
                    angle.absoluteValue.coerceIn(0.0, PI / 2.0),
                    0.0,
                    PI / 2.0,
                    0.0,
                    1.0
                )
            )

            return@PhotovoltaicVoltageFunction value * maximumVoltage
        }
    }

    fun test24Volts(): PhotovoltaicModel {
        //https://www.todoensolar.com/285w-24-volt-AmeriSolar-Solar-Panel
        return PhotovoltaicModel(voltageTest(32.0), 3.5)
    }
}

/**
 * Photovoltaic generator behavior. Works by modulating the voltage of [generator], based on the [model].
 * */
class PhotovoltaicBehavior(val cell: Cell, val generator: VRGeneratorObject<*>, val model: PhotovoltaicModel) : SolarIlluminationBehavior(cell) {
    override fun subscribe(subscribers: SubscriberCollection) {
        subscribers.addSubscriber(SubscriberOptions(100, SubscriberPhase.Pre), this::update)
    }

    private fun update(dt: Double, phase: SubscriberPhase) {
        generator.updatePotential(model.voltageFunction.compute(this))
    }

    override val normal = cell.locator.requireLocator<FaceLocator> {
        "Photovoltaic behavior requires a face locator"
    }
}

class PhotovoltaicGeneratorCell(ci: CellCreateInfo, model: PhotovoltaicModel) : Cell(ci) {
    @SimObject @Inspect
    val generator = VRGeneratorObject<Cell>(
        this,
        directionPoleMapPlanar()
    ).also { it.potentialExact = 0.0 }

    @Behavior
    val photovoltaic = PhotovoltaicBehavior(this, generator, model)

    init {
        ruleSet.withDirectionRulePlanar(Base6Direction3d.Front + Base6Direction3d.Back)
    }
}
