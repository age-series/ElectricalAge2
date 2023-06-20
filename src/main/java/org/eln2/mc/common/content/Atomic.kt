package org.eln2.mc.common.content

import net.minecraft.Util
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.events.schedulePre
import org.eln2.mc.common.getLevelDataStorage
import org.eln2.mc.data.*
import org.eln2.mc.formatted
import org.eln2.mc.mathematics.Vector3d
import org.eln2.mc.scientific.*
import org.eln2.mc.toVector3d

val testMaterial = RadioactiveMaterial(
    listOf(
        RadiationEmissionMode(
            gammaPhoton(),
            Quantity(1.0, CURIE),
            exponentialSphereIntensityFunction()
        )
    )
)

private data class StoredBlockKey(
    val block: Block,
    val pos: BlockPos
)

class RadioactiveBlockTest : Block(Properties.of(Material.STONE)) {
    override fun setPlacedBy(
        pLevel: Level,
        pPos: BlockPos,
        pState: BlockState,
        pPlacer: LivingEntity?,
        pStack: ItemStack
    ) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack)

        if(pLevel.isClientSide) {
            return
        }

        val pos = Vector3d(
            pPos.x + 0.5,
            pPos.y + 0.5,
            pPos.z + 0.5
        )

        LOGGER.info("Inserting test source at $pos")

        val source = (pLevel as ServerLevel).radiationSystem.createSource(
            pos,
            testMaterial
        )

        LOGGER.info("Result: $source")

        getLevelDataStorage(pLevel).storeNew(StoredBlockKey(this, pPos), source)
    }

    override fun destroy(pLevel: LevelAccessor, pPos: BlockPos, pState: BlockState) {
        if(!pLevel.isClientSide) {
            LOGGER.info("Destroying at $pPos")

            val source = getLevelDataStorage(pLevel).remove<StoredBlockKey, RadiationSource>(
                StoredBlockKey(this, pPos)
            )

            if(source == null) {
                LOGGER.error("Failed to fetch source")
            }
            else {
                source.destroy()
            }
        }

        super.destroy(pLevel, pPos, pState)
    }
}

class RadiationMeterItem : Item(Properties()) {
    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        if(!pLevel.isClientSide) {
            val system = (pLevel as ServerLevel).radiationSystem

            val readTask = system.createListener().apply {
                position = pPlayer.position().toVector3d()
            }.measureAsync()

            schedulePre(1) {
                if(readTask.isDone) {
                    val result = readTask.get()

                    val doseRate = result.received.sumOf {
                        !it.shardView.mode.quanta.evaluateAbsorbedDose(
                            receiverIntensity = it.intensity,
                            interval = Quantity(1.0),
                            absorber = RadiationAbsorberInfo(
                                mass = Quantity(60.0, KILOGRAMS),
                                material = HUMAN,
                                penetrationDepth = Quantity(50.0, CENTIMETERS)
                            )
                        )
                    }

                    pPlayer.sendSystemMessage(
                        Component.literal(
                            "Activity: ${
                                result.received.joinToString(" ") {
                                    "${(it.intensity .. MICROCURIES).formatted(4)} µCi ${it.shardView.mode.quanta.symbol}" 
                                }
                            }, Dose rate: (${valueText((doseRate * 3600.0), UnitType.GRAY)}/h)"
                        ))

                    false
                }
                else true
            }
        }

        return super.use(pLevel, pPlayer, pUsedHand)
    }
}
