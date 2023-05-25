package org.eln2.mc.common.blocks.foundation

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.gameevent.BlockPositionSource
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.gameevent.GameEventListener
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.AABB
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.events.EventScheduler
import org.eln2.mc.extensions.*
import org.eln2.mc.utility.ResourceReader
import kotlin.math.ceil

data class MultiblockDefinition(val requiredBlocksId: Map<BlockPos, ResourceLocation>) {
    val volume = volumeScan()
    val radius = ceil(listOf(volume.xsize, volume.ysize, volume.zsize).maxOrNull()!! / 2).toInt() // Weird, no max function?

    private fun volumeScan(): AABB {
        var box = AABB(BlockPos.ZERO)

        requiredBlocksId.keys.forEach {
            box = box.minmax(AABB(it))
        }

        return box
    }

    companion object {
        fun load(name: String): MultiblockDefinition {
            val json = Gson().fromJson(ResourceReader.getResourceString(Eln2.resource("multiblocks/$name.json")), JsonMultiblock::class.java)

            return MultiblockDefinition(
                json.blocks.associate {
                    it.p to (ResourceLocation.tryParse(it.id)
                        ?: error("Failed to parse resource location \"${it.id}\""))
                }
            )
        }
    }
}

object MultiblockTransformations {
    fun txIdWorld(facingWorld: Direction, actualOriginWorld: BlockPos, posId: BlockPos): BlockPos {
        val posActual = rot(facingWorld) * posId

        return actualOriginWorld + posActual
    }

    fun txWorldId(facingWorld: Direction, actualOriginWorld: BlockPos, posWorld: BlockPos): BlockPos {
        val posActual = posWorld - actualOriginWorld
        val txActualId = rot(facingWorld).inverse()

        return txActualId * posActual
    }
}

class MultiblockScan(val definition: MultiblockDefinition, val worldAccess: ((BlockPos) -> ResourceLocation), val posWorld: BlockPos, val facingWorld: Direction) {
    fun scanMissing(): List<BlockPos> {
        val results = ArrayList<BlockPos>()

        definition.requiredBlocksId.forEach { (requiredPosId, requiredBlock) ->
            val requiredPosWorld = MultiblockTransformations.txIdWorld(facingWorld, posWorld, requiredPosId)
            val actualBlock = worldAccess(requiredPosWorld)

            if(actualBlock != requiredBlock) {
                results.add(requiredPosWorld)
            }
        }

        return results
    }
}

interface MultiblockUser {
    fun onMultiblockDestroyed(variant: MultiblockDefinition) { }
    fun onMultiblockFormed(variant: MultiblockDefinition) { }
}

class MultiblockManager(val ctrlPosWorld: BlockPos, val ctrlFacingWorld: Direction, val defs: List<MultiblockDefinition>) : GameEventListener {
    constructor(ctrlPosWorld: BlockPos, ctrlFacingWorld: Direction, def: MultiblockDefinition) : this(ctrlPosWorld, ctrlFacingWorld, listOf(def))
    constructor(ctrlPosWorld: BlockPos, ctrlFacingWorld: Direction, defs: Set<MultiblockDefinition>) : this(ctrlPosWorld, ctrlFacingWorld, defs.toList())

    override fun getListenerSource() = BlockPositionSource(ctrlPosWorld)
    override fun getListenerRadius() = defs.maxOf { it.radius }

    fun txWorldId(posWorld: BlockPos) = MultiblockTransformations.txWorldId(ctrlFacingWorld, ctrlPosWorld, posWorld)
    fun txIdWorld(posId: BlockPos) = MultiblockTransformations.txIdWorld(ctrlFacingWorld, ctrlPosWorld, posId)

    fun getVariant(level: Level): MultiblockDefinition? = defs.firstOrNull {
        MultiblockScan(
            it,
            { p -> level.getBlockState(p).block.registryName!! },
            ctrlPosWorld,
            ctrlFacingWorld
        ).scanMissing().isEmpty()
    }

    fun enqueueUserScan(level: Level) {
        if(level.isClientSide) {
            return
        }

        EventScheduler.scheduleWorkPost(0) {
            val user = level.getBlockEntity(ctrlPosWorld) as? MultiblockUser
                ?: return@scheduleWorkPost

            getVariant(level)?.also { user.onMultiblockFormed(it) }
        }
    }

    override fun handleGameEvent(pLevel: Level, pEvent: GameEvent, pEntity: Entity?, targetPosWorld: BlockPos): Boolean {
        if(pEvent != GameEvent.BLOCK_PLACE && pEvent != GameEvent.BLOCK_DESTROY) {
            // Don't do unnecessary scans.
            return false
        }

        if(targetPosWorld == ctrlPosWorld) {
            // Event gets dispatched when the controller itself gets placed, we'll ignore it.
            return false
        }

        val user = pLevel.getBlockEntity(ctrlPosWorld) as? MultiblockUser
            ?: return false

        val targetPosId = txWorldId(targetPosWorld)

        defs.forEach { def ->
            val scan = MultiblockScan(
                def,
                { p ->
                    if(pEvent == GameEvent.BLOCK_DESTROY && p == targetPosWorld) {
                        // Needed because the world returns the state of the block being broken
                        Blocks.AIR.registryName!!
                    }
                    else {
                        pLevel.getBlockState(p).block.registryName!!
                    }
                },
                ctrlPosWorld,
                ctrlFacingWorld
            )

            if(scan.definition.requiredBlocksId.containsKey(targetPosId)) {
                if(pEvent == GameEvent.BLOCK_PLACE) {
                    if(scan.scanMissing().isEmpty()) {
                        user.onMultiblockFormed(scan.definition)
                        return true
                    }
                }
                else if(pEvent == GameEvent.BLOCK_DESTROY) {
                    val destroyedVariant = getVariant(pLevel)
                    if(destroyedVariant != null) {
                        // We exploit the fact that the event is sent before the BlockState is
                        // removed to check if the multiblock was formed before the block was broken:
                        user.onMultiblockDestroyed(destroyedVariant)
                        return true
                    }
                }
            }
            else {
                return false
            }
        }

        return false
    }
}

data class JsonMultiblockEntry(val p: BlockPos, val id: String)
data class JsonMultiblock(val blocks: List<JsonMultiblockEntry>)

class MultiblockScanTool : Item(Properties().stacksTo(1)) {
    override fun useOn(pContext: UseOnContext): InteractionResult {
        if(pContext.level.isClientSide) {
            return InteractionResult.PASS
        }

        val originWorld = pContext.clickedPos
        val resultsWorld = HashSet<BlockPos>()

        val queue = arrayListOf(originWorld)
        while (queue.isNotEmpty()) {
            val posWorld = queue.removeFirst()

            if(!pContext.level.getBlockState(posWorld).isAir) {
                if(resultsWorld.add(posWorld)) {
                    queue.addAll(Direction.values().map { posWorld + it })
                }
            }

            if(resultsWorld.size > 1000) {
                Eln2.LOGGER.error("Too many results!")
                return InteractionResult.PASS
            }
        }

        resultsWorld.remove(originWorld)


        println()

        println(
            GsonBuilder().setPrettyPrinting().create().toJson(
                JsonMultiblock(
                    resultsWorld.map {
                        JsonMultiblockEntry(
                            rot(pContext.horizontalDirection).inverse() * (it - originWorld),
                            pContext.level.getBlockState(it).block.registryName!!.toString()
                        )
                    },
                )
            )
        )

        println()

        return InteractionResult.SUCCESS
    }
}

interface MultiblockControllerEntity : MultiblockUser {
    val manager: MultiblockManager

    fun onDestroyed()
}

abstract class MBControllerBlock<BE>(properties: Properties? = null) : HorizontalDirectionalBlock(properties ?: Properties.of(Material.STONE)), EntityBlock
    where BE : BlockEntity, BE : MultiblockControllerEntity
{
    init { registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.EAST)) }

    final override fun getStateDefinition(): StateDefinition<Block, BlockState> {
        return super.getStateDefinition()
    }

    override fun getStateForPlacement(pContext: BlockPlaceContext): BlockState =
        super.defaultBlockState().setValue(FACING, pContext.horizontalDirection)

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)

        pBuilder.add(FACING)
    }

    abstract fun createBlockEntity(pPos: BlockPos, pState: BlockState): BE

    final override fun newBlockEntity(pPos: BlockPos, pState: BlockState) =
        createBlockEntity(pPos, pState)

    final override fun <T : BlockEntity?> getListener(pLevel: Level, pBlockEntity: T): GameEventListener? {
        if(pLevel.isClientSide) {
            return null
        }

        if(pBlockEntity is MultiblockControllerEntity) {
            return pBlockEntity.manager
        }

        return null
    }

    private fun notifyDestroyed(level: Level?, pos: BlockPos?) {
        if(level != null && pos != null) {
            if(!level.isClientSide) {
                (level.getBlockEntity(pos) as? MultiblockControllerEntity)?.onDestroyed()
            }
        }
    }

    override fun onDestroyedByPlayer(
        state: BlockState?,
        level: Level?,
        pos: BlockPos?,
        player: Player?,
        willHarvest: Boolean,
        fluid: FluidState?
    ): Boolean {
        notifyDestroyed(level, pos) // Do this before calling the super method (which removes the block entity)
        // It would be a bit weird if it returned false, we'll go ahead and log:
        val result = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)

        if(!result) {
            LOGGER.error("Strange MB break result: false")
        }

        return result
    }

    override fun onBlockExploded(state: BlockState?, level: Level?, pos: BlockPos?, explosion: Explosion?) {
        notifyDestroyed(level, pos)
        super.onBlockExploded(state, level, pos, explosion)
    }
}

class BasicMBControllerBlock<BE>(val factory: (pPos: BlockPos, pState: BlockState) -> BE) : MBControllerBlock<BE>() where BE : BlockEntity, BE : MultiblockControllerEntity {
    override fun createBlockEntity(pPos: BlockPos, pState: BlockState): BE = factory(pPos, pState)
}
