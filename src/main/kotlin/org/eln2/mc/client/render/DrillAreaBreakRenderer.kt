@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.eln2.mc.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator
import net.minecraft.client.resources.model.ModelBakery
import net.minecraft.core.BlockPos
import net.minecraft.server.level.BlockDestructionProgress
import net.minecraft.world.entity.player.Player
import org.eln2.mc.ClientOnly
import org.eln2.mc.common.content.DrillItem
import org.eln2.mc.common.content.DrillMode
import java.util.SortedSet

/**
 * Renders the vanilla crumbling/breaking overlay on the area-pattern neighbors of a [DrillItem] in 3x3 or 5x5 mode.
 *
 * The player's center-block progress is already in [net.minecraft.client.renderer.LevelRenderer]'s `destructionProgress`
 * map (vanilla puts it there). This renderer reads that map during the render pass and, for each drill-wielding player
 * with an active area-mode break, emits the same `renderBreakingTexture` calls on the neighbor blocks using the
 * center's destroy stage.
 *
 * No persistent state is mutated: [net.minecraft.client.renderer.LevelRenderer]'s `destroyingBlocks` and
 * `destructionProgress` are read-only here. The mining face is derived client-side from the player's eye position
 * relative to the center block via [DrillItem.pickMiningFace], so no extra networking is required. Works for the local
 * player and for other players (whose held-item NBT and entity position are already synced to this client).
 * */
@ClientOnly
object DrillAreaBreakRenderer {
    /**
     * Called from [org.eln2.mc.mixin.client.MixinLevelRenderer] after vanilla's `destroyProgress` loop, before the crumbling buffer is flushed.
     *
     * @param destructionProgress The `Long2ObjectMap<SortedSet<BlockDestructionProgress>>` shadowed from [net.minecraft.client.renderer.LevelRenderer] by the mixin. Read-only.
     * @param poseStack The active [PoseStack], positioned at the camera origin (same state as vanilla's loop).
     * @param camX Camera X offset (vanilla `d0`).
     * @param camY Camera Y offset (vanilla `d1`).
     * @param camZ Camera Z offset (vanilla `d2`).
     * */
    fun render(
        destructionProgress: Long2ObjectMap<SortedSet<BlockDestructionProgress>>,
        poseStack: PoseStack,
        camX: Double,
        camY: Double,
        camZ: Double,
    ) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
            ?: return

        val blockRenderer = minecraft.blockRenderer
        val crumblingBufferSource = minecraft.renderBuffers().crumblingBufferSource()
        @Suppress("UnstableApiUsage") val modelDataManager = level.modelDataManager

        for (entry in destructionProgress.long2ObjectEntrySet()) {
            val center = BlockPos.of(entry.longKey)
            val sortedSet = entry.value ?: continue
            if (sortedSet.isEmpty()) {
                continue
            }

            val progress = sortedSet.last()
            val stage = progress.progress
            if (stage < 0) {
                continue
            }

            val player = level.getEntity(progress.id) as? Player ?: continue
            val stack = player.mainHandItem
            val drill = stack.item as? DrillItem ?: continue
            val mode = drill.getMode(stack)
            if (mode == DrillMode.Single) {
                continue
            }

            val face = DrillItem.pickMiningFace(center, player)
            val positions = DrillItem.computeAreaPositions(center, face, mode.radius)

            val destroyType = ModelBakery.DESTROY_TYPES[stage]

            for (pos in positions) {
                if (pos == center) {
                    continue
                }

                if (destructionProgress.containsKey(pos.asLong())) {
                    continue
                }

                val state = level.getBlockState(pos)
                if (state.isAir || state.getDestroySpeed(level, pos) < 0.0f) {
                    continue
                }

                poseStack.pushPose()
                poseStack.translate(pos.x.toDouble() - camX, pos.y.toDouble() - camY, pos.z.toDouble() - camZ)
                val pose = poseStack.last()
                val vertexConsumer: VertexConsumer = SheetedDecalTextureGenerator(
                    crumblingBufferSource.getBuffer(destroyType),
                    pose.pose(),
                    pose.normal(),
                    1.0f
                )
                @Suppress("UnstableApiUsage") val modelData = modelDataManager?.getAt(pos)
                blockRenderer.renderBreakingTexture(
                    state,
                    pos,
                    level,
                    poseStack,
                    vertexConsumer,
                    modelData ?: net.minecraftforge.client.model.data.ModelData.EMPTY
                )
                poseStack.popPose()
            }
        }
    }
}
