package org.eln2.mc.common.content

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.LiquidBlockContainer
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.LazyOptional
import org.eln2.mc.common.capabilities.COMPOUND_CONTAINER_CAPABILITY
import org.eln2.mc.common.capabilities.CompoundContainerHandler
import org.eln2.mc.common.capabilities.convertMillibuckets
import org.eln2.mc.data.*
import org.eln2.mc.formatted
import org.eln2.mc.scientific.chemistry.*
import org.eln2.mc.scientific.chemistry.data.*
import org.eln2.mc.ticker

class FermentationBarrelBlock : Block(Properties.of(Material.WOOD)), EntityBlock, LiquidBlockContainer {
    override fun newBlockEntity(pPos: BlockPos, pState: BlockState) = FermentationBarrelBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(
        pLevel: Level,
        pState: BlockState,
        pBlockEntityType: BlockEntityType<T>,
    ): BlockEntityTicker<T>? {
        if (!pLevel.isClientSide) {
            if (pBlockEntityType == Content.FERMENTATION_BARREL_BLOCK_ENTITY.get()) {
                return ticker(FermentationBarrelBlockEntity::serverTick)
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
            val e = pLevel.getBlockEntity(pPos) as? FermentationBarrelBlockEntity ?: return InteractionResult.FAIL

            return e.use(pPlayer, pHand)
        }

        return InteractionResult.PASS
    }

    override fun canPlaceLiquid(pLevel: BlockGetter, pPos: BlockPos, pState: BlockState, pFluid: Fluid): Boolean {
        return pLevel.getBlockEntity(pPos) is FermentationBarrelBlockEntity && pFluid == Fluids.WATER
    }

    override fun placeLiquid(
        pLevel: LevelAccessor,
        pPos: BlockPos,
        pState: BlockState,
        pFluidState: FluidState,
    ): Boolean {
        val target = pLevel.getBlockEntity(pPos) as? FermentationBarrelBlockEntity ?: return false
        return target.insertMillibuckets(1000.0 * (pFluidState.amount.toDouble() / 8.0))
    }
}

val glucoseFermentationReaction = substituteEquation(
    "C₆H₁₂O₆ -> 2 C₂H₅OH + 2 CO₂",
    mapOf(
        "C₆H₁₂O₆" to glucosePowder,
        "C₂H₅OH" to liquidEthanol,
        "CO₂" to co2Gas
    )
)

val ethanolFermentationReactionS = substituteEquation(
    "C₂H₅OH -> CH₃COOH + H₂O",
    mapOf(
        "C₂H₅OH" to liquidEthanol,
        "CH₃COOH" to liquidAceticAcid,
        "H₂O" to liquidWater
    )
)

// We'll make a mass container capability, once we figure out how we want to handle things other than unstructured compositions
// That will likely have us making a registry and referring to the compounds based on IDs

class FermentationBarrelBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(Content.FERMENTATION_BARREL_BLOCK_ENTITY.get(), pos, state) {

    private val containerLazy = LazyOptional.of {
        object : CompoundContainerHandler(USER_VOLUME) {
            override fun extractInto(c: CompoundContainer, maxVolume: Quantity<Volume>): Boolean {
                return super.extractInto(c, maxVolume).also {
                    if(it) setChanged()
                }
            }

            override fun insertFrom(c: CompoundContainer, maxVolume: Quantity<Volume>): Boolean {
                return super.insertFrom(c, maxVolume).also {
                    if(it) setChanged()
                }
            }
        }
    }

    val massContainer get() = containerLazy.orElseThrow {
        Exception("Failed to get compound container")
    }.container

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        return COMPOUND_CONTAINER_CAPABILITY.orEmpty(cap, containerLazy.cast())
    }

    fun insertMillibuckets(mb: Double): Boolean {
        val result = CompoundContainer().apply {
            setVolumeSTP(liquidWater, convertMillibuckets(mb))
        }

        return (result.volumeSTP + massContainer.volumeSTP <= USER_VOLUME).also {
            if(it) {
                massContainer += result
                setChanged()
            }
        }
    }

    fun use(pPlayer: Player, pHand: InteractionHand): InteractionResult {
        val stack = pPlayer.getItemInHand(pHand)

        if (stack.count == 0) {
            return InteractionResult.FAIL
        }

        if (stack.item == Items.SUGAR) {
            val targetQuantity = Quantity(1.0, KILOGRAMS)

            val insertedVolume = massContainerOfMass(
                glucosePowder to targetQuantity
            ).volumeSTP

            if (massContainer.volumeSTP + insertedVolume > USER_VOLUME) {
                return InteractionResult.FAIL
            }

            val resultContainer = massContainer.bind().apply {
                this.addMass(glucosePowder, targetQuantity)
            }

            // Ignores other compounds in the container. If we want to not do that, we need more datasets...
            if(resultContainer.massConcentrationSTP(glucosePowder) > Quantity(909.0, G_PER_L)) {
                return InteractionResult.FAIL
            }

            massContainer.addMass(glucosePowder, targetQuantity)

            setChanged()

            return InteractionResult.CONSUME
        }

        return InteractionResult.PASS
    }

    private fun serverTick() {
        if(massContainer.massFractionSTP(liquidEthanol) < MAX_ETHANOL) {
            massContainer.applyReaction(
                glucoseFermentationReaction,
                ETHANOL_SPEED
            )
        }

        if(massContainer.massFractionSTP(liquidAceticAcid) < MAX_ACETIC) {
            massContainer.applyReaction(
                ethanolFermentationReactionS,
                ACETIC_SPEED
            )
        }

        massContainer[co2Gas] = Quantity(0.0)
        massContainer.trimAll()

        println("v: ${(massContainer.volumeSTP .. LITERS).formatted()} liters")

        massContainer.content.keys.forEach { cmp ->
            val mass = massContainer.getMass(cmp)

            if(mass > 0.001) {
                println("    ${cmp.label} -> ${(mass .. GRAMS).formatted(4)} grams")
            }
        }
    }

    companion object {
        // For testing! take them down a couple orders of magnitude in production
        private const val ETHANOL_SPEED = 1e-3
        private const val ACETIC_SPEED = 1e-3
        private const val MAX_ETHANOL = 0.2
        private const val MAX_ACETIC = 0.3

        private val USER_VOLUME = Quantity(100.0, LITERS)

        fun serverTick(pLevel: Level, pPos: BlockPos, pState: BlockState, pBlockEntity: FermentationBarrelBlockEntity) {
            pBlockEntity.serverTick()
        }
    }
}
