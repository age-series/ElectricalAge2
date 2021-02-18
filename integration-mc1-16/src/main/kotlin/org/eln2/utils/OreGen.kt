package org.eln2.utils

import net.minecraft.world.gen.GenerationStage
import net.minecraft.world.gen.IDecoratable
import net.minecraft.world.gen.feature.ConfiguredFeature
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.OreFeatureConfig
import net.minecraft.world.gen.placement.Placement
import net.minecraft.world.gen.placement.TopSolidRangeConfig
import net.minecraftforge.event.world.BiomeLoadingEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.OreBlocks

/**
 * Handles ore generation for Eln2
 */
@Mod.EventBusSubscriber
object OreGen {

    private var ores: List<ConfiguredFeature<*, *>> = mutableListOf()

    /**
     * Configures ore generation in the world. Registers against Forge.
     */

    fun setupOreGeneration() {

         ores = OreBlocks.values().map {
             Feature.ORE.withConfiguration(
                 OreFeatureConfig(OreFeatureConfig.FillerBlockType.BASE_STONE_OVERWORLD,
                     it.block.defaultState,
                     (it.block.getRarity()*2) //Vein size, calculated using the ore rarity times 2
                 )
             ).range(
                 (it.block.getRarity()*25) // Max height for the block to appear, calculated using rarity times 25 for a max of 125
             ).square().func_242731_b(it.block.getRarity())
        }
    }

    fun biomeModification(event: BiomeLoadingEvent) {
        ores.forEach{event.generation.withFeature(GenerationStage.Decoration.UNDERGROUND_ORES, it)}
    }
}
