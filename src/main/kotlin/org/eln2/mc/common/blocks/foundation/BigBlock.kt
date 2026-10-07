@file:Suppress("MemberVisibilityCanBePrivate")

package org.eln2.mc.common.blocks.foundation

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraftforge.client.extensions.common.IClientBlockExtensions
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.LOG
import org.eln2.mc.OnServerThread
import org.eln2.mc.ServerOnly
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.cells.foundation.Cell
import org.eln2.mc.extensions.*
import org.eln2.mc.extensions.plus
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import org.eln2.mc.mathematics.Base6Direction3dMask
import org.eln2.mc.mathematics.toHorizontalFacing
import org.eln2.mc.requireIsOnServerThread
import org.joml.Quaternionf
import java.util.function.Consumer
import kotlin.math.PI
import kotlin.math.ceil

object MultiblockTransformations {
    // PS this is an old convention, should be removed and converted to our current convention
    /*fun rot(dir: Direction) = when (dir) {
        Direction.NORTH -> Rotation.COUNTERCLOCKWISE_90
        Direction.SOUTH -> Rotation.CLOCKWISE_90
        Direction.WEST -> Rotation.CLOCKWISE_180
        Direction.EAST -> Rotation.NONE
        else -> error("Invalid horizontal facing $dir")
    }*/

    fun rot(dir: Direction) = dir.toHorizontalFacing().minecraftRotation

    fun transformMultiblockWorld(facing: Direction, origin: BlockPos, posMb: BlockPos): BlockPos {
        val posActual = rot(facing) * posMb
        return origin + posActual
    }

    fun transformWorldMultiblock(facing: Direction, origin: BlockPos, posWorld: BlockPos): BlockPos {
        val posActual = posWorld - origin
        val txActualId = rot(facing).inverse()
        return txActualId * posActual
    }
}

data class MultiblockDelegateMap(val delegates: Map<BlockPos, BlockState>) {
    inline fun <reified T : Block> getBlocksOfType(): List<T> = delegates.values.mapNotNull { it.block as? T }

    val volume = run {
        var box = AABB(BlockPos.ZERO)

        delegates.keys.forEach {
            box = box.minmax(AABB(it))
        }

        box
    }

    val radius = ceil(listOf(volume.xsize, volume.ysize, volume.zsize).max() / 2).toInt()

    fun destroyInWorld(level: Level, facing: Direction, origin: BlockPos) {
        if(level.isClientSide) {
            return
        }

        delegates.forEach { (delegatePosId, state) ->
            val delegatePosWorld = MultiblockTransformations.transformMultiblockWorld(facing, origin, delegatePosId)
            val actualState = level.getBlockState(delegatePosWorld)

            if(actualState.block == state.block) {
                level.destroyBlock(delegatePosWorld, false)
            }
        }
    }

    /**
     * Iterates over the world position of each delegate.
     * @param facing The facing of the representative.
     * @param origin The position of the representative.
     * @param consumer Consumer for the position of each delegate, in the world frame.
     * */
    fun forEachDelegateInWorld(facing: Direction, origin: BlockPos, consumer: (BlockPos) -> Unit) {
        delegates.forEach { (delegatePosId, state) ->
            val delegatePosWorld = MultiblockTransformations.transformMultiblockWorld(facing, origin, delegatePosId)

            consumer(delegatePosWorld)
        }
    }

    /**
     * Used for setting the section collector in visuals.
     * Gets all the sections intersected by the entire multiblock plus each block's Von Neumann neighborhood.
     * Useful for setting the sections needed for GPU lighting.
     * @param facing The facing of the representative.
     * @param origin The position of the representative.
     * @return All the sections spanned by the origin block, delegate blocks, and the immediate neighbors to all the previously mentioned blocks.
     * */
    fun getTotalSpannedSectionsFat(facing: Direction, origin: BlockPos) : LongOpenHashSet {
        val result = LongOpenHashSet()

        result.add(SectionPos.asLong(origin))

        Direction.entries.forEach { dir ->
            result.add(SectionPos.asLong(origin + dir))
        }

        forEachDelegateInWorld(facing, origin) {
            result.add(SectionPos.asLong(it))

            Direction.entries.forEach { dir ->
                result.add(SectionPos.asLong(it + dir))
            }
        }

        return result
    }
}

class MultiblockScan(
    val definition: MultiblockDelegateMap,
    val worldAccess: ((BlockPos) -> BlockState),
    val origin: BlockPos,
    val facing: Direction,
) {
    private fun transform(posMb: BlockPos) = MultiblockTransformations.transformMultiblockWorld(facing, origin, posMb)

    private fun get(posMb: BlockPos) = worldAccess(transform(posMb))

    /**
     * Scans the world to check if all delegate blocks are present.
     * @return True, if all delegates are present. Otherwise, false.
     * */
    fun isFormed() : Boolean {
        for ((requiredPosMb, requiredState) in definition.delegates) {
            if (get(requiredPosMb) != requiredState) {
                return false
            }
        }

        return true
    }
}

interface MultiblockRepresentative {
    fun onDelegateUse(
        delegate: BlockEntity,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ) = InteractionResult.PASS

    fun onDelegateDestroyed(pDelegate: BlockEntity, suppressDrops: Boolean = false) { }
}

interface BigBlockRepresentativeBlockEntity<Self> : MultiblockRepresentative where Self : BigBlockRepresentativeBlockEntity<Self>, Self : BlockEntity {
    val delegateMap: MultiblockDelegateMap

    private val self get() = this as BlockEntity

    val representativeFacing: Direction
        get() = self.blockState.getValue(HorizontalDirectionalBlock.FACING)

    val representativePos: BlockPos
        get() = self.blockPos

    val representativeLevel: Level?
        get() = self.level

    // Destroys on server only but allowed to call from client
    fun destroyDelegates() {
        val level = self.level

        if(level == null) {
            LOG.error("Cannot destroy delegates in null level $this")
            return
        }

        if(level.isClientSide) {
            return
        }

        delegateMap.destroyInWorld(
            level,
            representativeFacing,
            representativePos
        )
    }

    /*override fun onDelegateDestroyedByPlayer(
        delegate: MultiblockDelegateBlockEntity,
        pPlayer: Player,
        pWillHarvest: Boolean,
        pFluid: FluidState,
    ) {
        destroyDelegates()

        self.blockState.block.onDestroyedByPlayer(
            self.blockState,
            self.level,
            self.blockPos,
            pPlayer,
            pWillHarvest,
            pFluid
        )
    }

    override fun onDelegateExploded(delegate: MultiblockDelegateBlockEntity, pExplosion: Explosion?) {
        destroyDelegates()

        self.blockState.block.onBlockExploded(
            self.blockState,
            self.level,
            self.blockPos,
            pExplosion
        )
    }*/

    override fun onDelegateDestroyed(pDelegate: BlockEntity, suppressDrops: Boolean) {
        // Representative is destroyed first so that delegate onRemove calls find no representative and no-op, preventing reentrancy.
        self.level!!.destroyBlock(
            self.blockPos,
            !suppressDrops
        )

        destroyDelegates()
    }
}

object ReplaceVanillaParticlesBlockExtension : IClientBlockExtensions {
    override fun addDestroyEffects(
        state: BlockState?,
        @Suppress("LocalVariableName") Level: Level?,
        pos: BlockPos?,
        manager: ParticleEngine?
    ): Boolean {
        if(pos != null) {
            manager?.destroy(pos, Blocks.IRON_BLOCK.defaultBlockState())
        }

        return true
    }

    override fun addHitEffects(
        state: BlockState?,
        level: Level?,
        target: HitResult?,
        manager: ParticleEngine?
    ): Boolean {
        return true
    }
}

open class MultiblockDelegateBlock(properties: Properties? = null) : BaseEntityBlock(properties ?: eln2StandardBlockProperties().noOcclusion().destroyTime(0.2f)) {
    companion object {
        val SKIP_RENDERING: BooleanProperty = BooleanProperty.create("skip_rendering")

        fun getRepresentativeAndDelegate(pLevel: LevelAccessor, pPos: BlockPos) : Pair<MultiblockDelegateBlockEntity, MultiblockRepresentative>? {
            val blockEntity = pLevel.getBlockEntity(pPos) as? MultiblockDelegateBlockEntity
                ?: return null

            val representativePos = blockEntity.representativePos

            if(representativePos == null) {
                LOG.error("Did not have representative for interaction $pLevel $pPos")
                return null
            }

            val representative = pLevel.getBlockEntity(representativePos) as? MultiblockRepresentative
                ?: pLevel.getBlockState(representativePos).block as? MultiblockRepresentative

            return if(representative == null) {
                LOG.debug("Representative is missing $pLevel $pPos $representativePos")
                null
            } else {
                blockEntity to representative
            }
        }

        private inline fun<T> runWithRepresentativeAndDelegate(pLevel: Level, pPos: BlockPos, use: (delegate: MultiblockDelegateBlockEntity, representative: MultiblockRepresentative) -> T) : T? {
            val pair = getRepresentativeAndDelegate(pLevel, pPos)
                ?: return null

            return use(pair.first, pair.second)
        }
    }

    init {
        @Suppress("LeakingThis")
        registerDefaultState(getStateDefinition().any()
            .setValue(SKIP_RENDERING, true)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)
        )
    }

    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(pBuilder)
        pBuilder.add(SKIP_RENDERING)
        pBuilder.add(HorizontalDirectionalBlock.FACING)
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity {
        return MultiblockDelegateBlockEntity(pPos, pState, null)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction): Boolean {
        return pState.getValue(SKIP_RENDERING)
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
        return runWithRepresentativeAndDelegate(pLevel, pPos) { delegate, representative ->
            representative.onDelegateUse(delegate, pPlayer, pHand, pHit)
        } ?: InteractionResult.FAIL
    }

    /**
     * Replaced by [onRemove].
     * */
    /*
    override fun onDestroyedByPlayer(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        willHarvest: Boolean,
        fluid: FluidState,
    ): Boolean {
        runWithRepresentativeAndDelegate(level, pos) { delegate, representative ->
            representative.onDelegateDestroyedByPlayer(delegate, player, willHarvest, fluid)
        }

        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid)
    }

    override fun onBlockExploded(state: BlockState, level: Level, pos: BlockPos, explosion: Explosion) {
        runWithRepresentativeAndDelegate(level, pos) { delegate, representative ->
            representative.onDelegateExploded(delegate, explosion)
        }

        super.onBlockExploded(state, level, pos, explosion)
    }
    */

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRemove(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pNewState: BlockState,
        pMovedByPiston: Boolean
    ) {
        check(pState.block !== pNewState.block) {
            DEBUGGER_BREAK("Multiblock delegate changed its blockstate, which is illegal")
        }

        // For non-player destruction (explosions, etc.), dispatch from here with drops enabled.
        // For player-initiated creative breaks, playerWillDestroy already dispatched with suppressDrops=true and destroyed the representative, so getRepresentativeAndDelegate returns null here.
        runWithRepresentativeAndDelegate(pLevel, pPos) { delegate, representative ->
            representative.onDelegateDestroyed(delegate)
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun playerWillDestroy(
        pLevel: Level,
        pPos: BlockPos,
        pState: BlockState,
        pPlayer: Player
    ) {
        if (!pLevel.isClientSide && pPlayer.isCreative) {
            // Dispatch before setBlock so the representative still exists. suppressDrops prevents the item drop in creative.
            runWithRepresentativeAndDelegate(pLevel, pPos) { delegate, representative ->
                representative.onDelegateDestroyed(delegate, suppressDrops = true)
            }
        }

        super.playerWillDestroy(pLevel, pPos, pState, pPlayer)
    }
}

@Suppress("UNCHECKED_CAST")
fun getRepresentativeFromDelegateBlockEntity(delegate: BlockEntity, representativePos: BlockPos?) : BlockEntity {
    requireIsOnServerThread {
        "$delegate getRepresentativeFromDelegateBlockEntity"
    }

    val level = delegate.level
        ?: error("Cannot get representative for $delegate: level null!")

    return level.getBlockEntity(representativePos ?: error("Cannot get representative for $delegate: position null!"))
        ?: error("Cannot get representative for $delegate: block entity null!")
}

inline fun<reified T> getRepresentativeFromDelegateBlockEntity(delegate: BlockEntity, representativePos: BlockPos?) : T {
    val blockEntity = getRepresentativeFromDelegateBlockEntity(delegate, representativePos)

    return (blockEntity as? T) ?: error("Cannot get representative for $delegate: required type was ${T::class.java}, but got $blockEntity instead!")
}

abstract class MultiblockDelegateUprightHorizontalDirectionCellBlock<C : Cell>(p: Properties? = null) : UprightHorizontalDirectionCellBlock<C>(p) {
    companion object {
        fun getRepresentativeAndDelegate(pLevel: LevelAccessor, pPos: BlockPos) : Pair<MultiblockDelegateCellBlockEntity<*>, MultiblockRepresentative>? {
            val blockEntity = pLevel.getBlockEntity(pPos) as? MultiblockDelegateCellBlockEntity<*>
                ?: return null

            val representativePos = blockEntity.representativePos

            if(representativePos == null) {
                LOG.error("Did not have representative for cell interaction $pLevel $pPos")
                return null
            }

            val representative = pLevel.getBlockEntity(representativePos) as? MultiblockRepresentative
                ?: pLevel.getBlockState(representativePos).block as? MultiblockRepresentative

            return if(representative == null) {
                LOG.debug("Cell representative is missing $pLevel $pPos $representativePos")
                null
            } else {
                blockEntity to representative
            }
        }

        private inline fun<T> runWithRepresentativeAndDelegate(pLevel: Level, pPos: BlockPos, use: (delegate: MultiblockDelegateCellBlockEntity<*>, representative: MultiblockRepresentative) -> T) : T? {
            val pair = getRepresentativeAndDelegate(pLevel, pPos)
                ?: return null

            return use(pair.first, pair.second)
        }
    }

    override fun initializeClient(consumer: Consumer<IClientBlockExtensions?>) {
        consumer.accept(ReplaceVanillaParticlesBlockExtension)
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
        return runWithRepresentativeAndDelegate(pLevel, pPos) { delegate, representative ->
            representative.onDelegateUse(delegate, pPlayer, pHand, pHit)
        } ?: InteractionResult.FAIL
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRemove(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pNewState: BlockState,
        pMovedByPiston: Boolean
    ) {
        check(pState.block !== pNewState.block) {
            DEBUGGER_BREAK("Cell Multiblock delegate changed its blockstate, which is illegal")
        }

        // For non-player destruction (explosions, etc.), dispatch from here with drops enabled.
        // For player-initiated creative breaks, playerWillDestroy already dispatched with suppressDrops=true and destroyed the representative, so getRepresentativeAndDelegate returns null here.
        runWithRepresentativeAndDelegate(pLevel, pPos) { delegate, representative ->
            representative.onDelegateDestroyed(delegate)
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pMovedByPiston)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun playerWillDestroy(
        pLevel: Level,
        pPos: BlockPos,
        pState: BlockState,
        pPlayer: Player
    ) {
        if (!pLevel.isClientSide && pPlayer.isCreative) {
            // Dispatch before setBlock so the representative still exists. suppressDrops prevents the item drop in creative.
            runWithRepresentativeAndDelegate(pLevel, pPos) { delegate, representative ->
                representative.onDelegateDestroyed(delegate, suppressDrops = true)
            }
        }

        super.playerWillDestroy(pLevel, pPos, pState, pPlayer)
    }
}

open class MultiblockDelegateBlockWithCustomCollider(properties: Properties? = null, initialShapes: List<AABB>) : MultiblockDelegateBlock(properties) {
    val colliderVariants = Base6Direction3dMask.HORIZONTALS.directionList.associateWith { dir ->
        val transform = when(MultiblockTransformations.rot(dir)) {
            Rotation.NONE -> Quaternionf().identity()
            Rotation.CLOCKWISE_90 -> Quaternionf().rotateY((-PI / 2.0).toFloat())
            Rotation.CLOCKWISE_180 -> Quaternionf().rotateY((-PI).toFloat())
            Rotation.COUNTERCLOCKWISE_90 -> Quaternionf().rotateY((PI / 2.0).toFloat())
        }

        var shape = Shapes.empty()

        initialShapes.forEach { boundingBox ->
            shape = Shapes.joinUnoptimized(
                shape,
                Shapes.create(
                    boundingBox
                        .move(-0.5, -0.5, -0.5)
                        .transformed(transform)
                        .move(0.5, 0.5, 0.5)
                ),
                BooleanOp.OR
            )
        }

        shape.optimize()
        shape
    }

    private fun getDelegateCollider(pState: BlockState) : VoxelShape {
        val facing = pState.getValue(HorizontalDirectionalBlock.FACING)

        return checkNotNull(colliderVariants[facing]) {
            "Invalid facing $facing"
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getCollisionShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = getDelegateCollider(pState)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = getDelegateCollider(pState)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getVisualShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = getDelegateCollider(pState)

    override fun skipRendering(pState: BlockState, pAdjacentBlockState: BlockState, pDirection: Direction) = true
}

open class MultiblockDelegateBlockEntity(pPos: BlockPos, pBlockState: BlockState, pType: BlockEntityType<*>?) : BlockEntity(pType ?: BlockRegistry.MULTIBLOCK_DELEGATE_BLOCK_ENTITY.get(), pPos, pBlockState), ComponentDisplay {
    var representativePos: BlockPos? = null
        private set

    @ServerOnly @OnServerThread
    fun setRepresentative(representative: BlockPos) {
        requireIsOnServerThread {
            DEBUGGER_BREAK("Cannot set representative on non-server thread")
        }

        check(representativePos == null) {
            DEBUGGER_BREAK("Tried to set representative multiple times")
        }

        this.representativePos = representative

        this.setChanged()
        this.setSyncDirty()
    }

    @ServerOnly @OnServerThread
    fun removeRepresentative() {
        requireIsOnServerThread {
            "Cannot remove representative on non-server thread"
        }

        check(representativePos != null) {
            "Tried to remove non-exiting representative"
        }

        representativePos = null

        this.setChanged()
        this.setSyncDirty()
    }

    private fun putRepresentative(tag: CompoundTag) {
        val representativePos = this.representativePos

        if(representativePos != null) {
            tag.putBlockPos(REPRESENTATIVE_POS, representativePos)
        }
    }

    private fun loadRepresentative(tag: CompoundTag) {
        if(tag.contains(REPRESENTATIVE_POS)) {
            this.representativePos = tag.getBlockPos(REPRESENTATIVE_POS)
        }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        putRepresentative(pTag)
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        loadRepresentative(pTag)
    }

    override fun getUpdateTag(): CompoundTag {
        val tag = super.getUpdateTag()
        putRepresentative(tag)
        return tag
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        val tag = CompoundTag()
        putRepresentative(tag)
        return ClientboundBlockEntityDataPacket.create(this) { tag }
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        val representativePos = this.representativePos

        if(level != null && representativePos != null) {
            builder.debugInIDE { "Representative: ${level!!.getBlockEntity(representativePos)}" }
            (level?.getBlockEntity(representativePos) as? ComponentDisplay)?.submitDisplay(builder)
        }
    }

    companion object {
        private const val REPRESENTATIVE_POS = "representativePos"
    }
}

abstract class MultiblockDelegateCellBlockEntity<C : Cell>(pPos: BlockPos, pBlockState: BlockState, pType: BlockEntityType<*>) : CellBlockEntity<C>(pPos, pBlockState, pType), ComponentDisplay {
    var representativePos: BlockPos? = null
        private set

    @ServerOnly @OnServerThread
    fun setRepresentative(representative: BlockPos) {
        requireIsOnServerThread {
            "Cannot set representative on non-server thread"
        }

        check(representativePos == null) {
            "Tried to set representative multiple times"
        }

        this.representativePos = representative

        this.setChanged()
        this.setSyncDirty()
    }

    @ServerOnly @OnServerThread
    fun removeRepresentative() {
        requireIsOnServerThread {
            "Cannot remove representative on non-server thread"
        }

        check(representativePos != null) {
            "Tried to remove non-exiting representative"
        }

        representativePos = null

        this.setChanged()
        this.setSyncDirty()
    }

    private fun putRepresentative(tag: CompoundTag) {
        val representativePos = this.representativePos

        if(representativePos != null) {
            tag.putBlockPos(REPRESENTATIVE_POS, representativePos)
        }
    }

    private fun loadRepresentative(tag: CompoundTag) {
        if(tag.contains(REPRESENTATIVE_POS)) {
            this.representativePos = tag.getBlockPos(REPRESENTATIVE_POS)
        }
    }

    override fun saveAdditional(pTag: CompoundTag) {
        super.saveAdditional(pTag)
        putRepresentative(pTag)
    }

    override fun load(pTag: CompoundTag) {
        super.load(pTag)
        loadRepresentative(pTag)
    }

    override fun getUpdateTag(): CompoundTag {
        val tag = super.getUpdateTag()
        putRepresentative(tag)
        return tag
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        val superPacket = super.getUpdatePacket()

        if(superPacket != null) {
            check(superPacket is ClientboundBlockEntityDataPacket) {
                "Unexpected super packet $superPacket"
            }
        }

        @Suppress("USELESS_CAST") // shut the hell up
        val tag = (superPacket as? ClientboundBlockEntityDataPacket)?.tag ?: CompoundTag()
        putRepresentative(tag)
        return ClientboundBlockEntityDataPacket.create(this) { tag }
    }

    override fun submitDisplay(builder: ComponentDisplayList) {
        val representativePos = this.representativePos

        if(level != null && representativePos != null) {
            builder.debugInIDE { "Representative: ${level!!.getBlockEntity(representativePos)}" }
            (level?.getBlockEntity(representativePos) as? ComponentDisplay)?.submitDisplay(builder)
        }
    }

    companion object {
        private const val REPRESENTATIVE_POS = "multiblockDelegateCellBlockEntityRepresentativePos"
    }
}

interface ValidateFormationBlock<Self> where Self : Block, Self : ValidateFormationBlock<Self> {
    fun validateMultiblockFormation(pos: BlockPos)
}

interface ValidateFormationBlockEntity<Self> where Self : BlockEntity, Self : ValidateFormationBlockEntity<Self> {
    fun validateMultiblockFormation()
}

class BigBlockItem(val definition: MultiblockDelegateMap, representative: Block) : BlockItem(representative, Properties()) {
    override fun place(pContext: BlockPlaceContext): InteractionResult {
        val facing = checkNotNull(block.getStateForPlacement(pContext)) {
            "Expected default state $block"
        }.getValue(HorizontalDirectionalBlock.FACING)

        fun transform(blockPosId: BlockPos) = MultiblockTransformations.transformMultiblockWorld(
            facing,
            pContext.clickedPos,
            blockPosId
        )

        definition.delegates.keys.forEach { posMb ->
            if(!pContext.level.getBlockState(transform(posMb)).isAir) {
                return InteractionResult.FAIL
            }
        }

        val result = super.place(pContext)

        if(pContext.level.isClientSide) {
            return result
        }

        val validations = ArrayList<() -> Unit>()

        fun enlistForValidation(block: Block, pos: BlockPos) {
            if(block is ValidateFormationBlock<*>) {
                validations.add {
                    block.validateMultiblockFormation(pos)
                }
            }
        }

        fun enlistForValidation(blockEntity: BlockEntity) {
            if(blockEntity is ValidateFormationBlockEntity<*>) {
                validations.add {
                    blockEntity.validateMultiblockFormation()
                }
            }
        }

        enlistForValidation(block, pContext.clickedPos)

        val repBlockEntity = pContext.level.getBlockEntity(pContext.clickedPos)

        if(repBlockEntity != null) {
            enlistForValidation(repBlockEntity)
        }

        if(result == InteractionResult.CONSUME) {
            definition.delegates.forEach { (delegatePosMb, state) ->
                val delegatePosWorld = transform(delegatePosMb)

                var delegateState = state
                val b = state.block
                enlistForValidation(block, delegatePosWorld)

                if(b is MultiblockDelegateBlock || b is MultiblockDelegateUprightHorizontalDirectionCellBlock<*>) {
                    delegateState = delegateState.setValue(HorizontalDirectionalBlock.FACING, facing)
                }

                pContext.level.setBlockAndUpdate(delegatePosWorld, delegateState)

                val blockEntity = pContext.level.getBlockEntity(delegatePosWorld)

                if(blockEntity != null) {
                    enlistForValidation(blockEntity)
                }

                when(blockEntity) {
                    is MultiblockDelegateBlockEntity -> blockEntity.setRepresentative(pContext.clickedPos)
                    is MultiblockDelegateCellBlockEntity<*> -> blockEntity.setRepresentative(pContext.clickedPos)
                }

                // New code: needed for the cells.
                // We do this here because we need the representative pos.
                b.setPlacedBy(pContext.level, delegatePosWorld, delegateState, pContext.player, pContext.itemInHand)
            }
        }

        validations.forEach {
            it.invoke()
        }

        return result
    }
}
