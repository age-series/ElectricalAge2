package org.eln2.mc.common.content

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUtils
import net.minecraft.world.item.Items
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.LiquidBlockContainer
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Material
import net.minecraft.world.level.material.WaterFluid
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.LazyOptional
import org.eln2.mc.*
import org.eln2.mc.client.render.PartialModels
import org.eln2.mc.client.render.foundation.BasicPartRenderer
import org.eln2.mc.common.capabilities.COMPOUND_CONTAINER_CAPABILITY
import org.eln2.mc.common.capabilities.CompoundContainerHandler
import org.eln2.mc.common.events.ScheduledWork
import org.eln2.mc.common.events.periodicPre
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.common.parts.foundation.PartPlacementInfo
import org.eln2.mc.common.parts.foundation.PartProvider
import org.eln2.mc.common.parts.foundation.PartUseInfo
import org.eln2.mc.data.*
import org.eln2.mc.mathematics.SNZE_EPSILON
import org.eln2.mc.mathematics.mappedTo
import org.eln2.mc.mathematics.vec3
import org.eln2.mc.scientific.chemistry.CompoundContainer
import org.eln2.mc.scientific.chemistry.data.*

class TreeTapPartProvider : PartProvider() {
    override val placementCollisionSize = TreeTapPart.SIZE
    override fun create(context: PartPlacementInfo) = TreeTapPart(id, context)
    override fun canPlace(context: PartPlacementInfo) = context.face.isHorizontal()
}

class TreeTapPart(id: ResourceLocation, placement: PartPlacementInfo) : Part<BasicPartRenderer>(id, placement) {
    override val sizeActual = SIZE

    var quantity = 0.0

    init {
        require(placement.face.isHorizontal()) {
            "Placed tree tap on invalid face ${placement.face}"
        }
    }

    private var schedule: ScheduledWork? = null

    override fun createRenderer() = BasicPartRenderer(this, PartialModels.BATTERY)

    override fun onAdded() {
        if (!placement.level.isClientSide) {
            require(schedule == null)
            schedule = periodicPre(TREE_TAP_INTERVAL, ::update)
        }
    }

    @ServerOnly
    private fun update(): Boolean {
        if(quantity >= 1.0) {
            return true
        }

        val substrate = placement.level.getBlockState(placement.pos - placement.face)

        BLOCKS[substrate.block]?.also {
            quantity = (quantity + it + SNZE_EPSILON).coerceIn(0.0, 1.0)
            invalidateSave()
        }

        return true
    }

    override fun getSaveTag() = CompoundTag().apply { putDouble(NBT_QUANTITY, quantity) }
    override fun loadFromTag(tag: CompoundTag) {
        quantity = tag.getDouble(NBT_QUANTITY)
    }

    override fun onUsedBy(context: PartUseInfo): InteractionResult {
        if (placement.level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        if (quantity >= 1.0) {
            val stack = context.player.getItemInHand(context.hand)

            if(stack.count == 0) {
                return InteractionResult.FAIL
            }

            if(stack.item != Items.BUCKET) {
                return InteractionResult.FAIL
            }

            context.player.setItemInHand(context.hand, ItemStack(Content.LATEX_SAP_BUCKET.item.get(), 1))
            quantity -= 1.0
            invalidateSave()

            return InteractionResult.CONSUME
        }

        return InteractionResult.FAIL
    }

    override fun onRemoved() {
        schedule?.cancel()
    }

    override val dataNode = data {
        it.withField(TooltipField { b -> b.text("Quantity", quantity.formattedPercentN()) })
    }

    companion object {
        val SIZE = vec3(1.0)
        private const val NBT_QUANTITY = "quantity"
        private const val TREE_TAP_INTERVAL = 10

        private val BLOCKS = mapOf(
            Blocks.OAK_LOG to 0.5
        )
    }
}

class LatexCoagulationBarrelBlock : Block(Properties.of(Material.WOOD)), EntityBlock, LiquidBlockContainer {
    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = LatexCoagulationBarrelBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        if (!pLevel.isClientSide) {
            if (pBlockEntityType == Content.LATEX_COAGULATION_BARREL_BLOCK_ENTITY.get()) {
                return ticker(LatexCoagulationBarrelBlockEntity::serverTick)
            }
        }

        return null
    }

    @Deprecated("Deprecated in Java", ReplaceWith("?"))
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        if (!pLevel.isClientSide) {
            val e = pLevel.getBlockEntity(pPos) as? LatexCoagulationBarrelBlockEntity ?: return InteractionResult.FAIL

            return e.use(pPlayer, pHand)
        }

        return InteractionResult.FAIL
    }

    override fun canPlaceLiquid(pLevel: BlockGetter, pPos: BlockPos, pState: BlockState, pFluid: Fluid): Boolean {
        return pLevel.getBlockEntity(pPos) is LatexCoagulationBarrelBlockEntity && pFluid == Content.LATEX_SAP_SOURCE_FLUID.get()
    }

    override fun placeLiquid(
        pLevel: LevelAccessor,
        pPos: BlockPos,
        pState: BlockState,
        pFluidState: FluidState,
    ): Boolean {
        if(pFluidState.amount != 8) {
            return false
        }

        val target = pLevel.getBlockEntity(pPos) as? LatexCoagulationBarrelBlockEntity ?: return false
        return target.insertSap()
    }
}

// We'll make a mass container capability, once we figure out how we want to handle things other than unstructured compositions
// That will likely have us making a registry and referring to the compounds based on IDs

class RawNaturalRubberItem : Item(Properties().stacksTo(64))

class LatexCoagulationBarrelBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(Content.LATEX_COAGULATION_BARREL_BLOCK_ENTITY.get(), pos, state) {
    private var hasSap = false
    private var progress = 0.0

    private val containerLazy = LazyOptional.of {
        object : CompoundContainerHandler(USER_VOLUME) {
            override fun extractInto(c: CompoundContainer, maxVolume: Quantity<Volume>): Boolean {
                return false
            }

            override fun insertFrom(c: CompoundContainer, maxVolume: Quantity<Volume>): Boolean {
                if(c.content.keys.any { !WHITELIST.contains(it) }) {
                    return false
                }

                return super.insertFrom(c, maxVolume).also {
                    if(it) setChanged()
                }
            }
        }
    }

    private val massContainer get() = containerLazy.orElseThrow {
        Exception("Failed to get compound container")
    }.container

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        return COMPOUND_CONTAINER_CAPABILITY.orEmpty(cap, containerLazy.cast())
    }

    fun insertSap(): Boolean {
        if(hasSap) {
            return false
        }

        hasSap = true
        setChanged()

        return true
    }

    fun use(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        val stack = pPlayer.getItemInHand(pHand)

        if (stack.count == 0 && hasSap && progress >= 1.0) {
            if(pPlayer.inventory.add(ItemStack(Content.RAW_NATURAL_RUBBER.item.get(), 1))) {
                hasSap = false
                progress = 0.0
                setChanged()

                return InteractionResult.SUCCESS
            }
        }

        return InteractionResult.FAIL
    }

    private fun serverTick() {
        if(!hasSap) {
            return
        }

        if(progress >= 1.0) {
            return
        }

        var rate = NATURAL_RATE

        rate *= massContainer.molarFraction(liquidAceticAcid).mappedTo(
            0.0,
            1.0,
            1.0,
            kACETIC
        )

        progress = (progress + rate).coerceIn(0.0, 1.0)
        setChanged()

        println("p: ${progress.formattedPercentN()}, r: ${rate.formatted(5)}")
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        pTag.putBoolean(NBT_HAS_SAP, hasSap)
        pTag.putDouble(NBT_PROGRESS, progress)
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        hasSap = pTag.getBoolean(NBT_HAS_SAP)
        progress = pTag.getDouble(NBT_PROGRESS)
    }

    companion object {
        private const val NBT_HAS_SAP = "hasSap"
        private const val NBT_PROGRESS = "sapProgress"

        private val USER_VOLUME = Quantity(1.0, LITERS)
        // For testing, take it down a couple orders of magnitude in production
        private const val NATURAL_RATE = 1e-3 // over ticks
        private const val kACETIC = 25.0

        private val WHITELIST = listOf(
            liquidWater,
            liquidEthanol,
            liquidAceticAcid,
            glucosePowder
        )

        fun serverTick(pLevel: Level, pPos: BlockPos, pState: BlockState, pBlockEntity: LatexCoagulationBarrelBlockEntity) {
            pBlockEntity.serverTick()
        }
    }
}
