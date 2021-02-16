package org.eln2.utils

import net.minecraft.block.Blocks
import net.minecraft.world.gen.GenerationStage
import net.minecraft.world.gen.feature.ConfiguredFeature
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.OreFeatureConfig
import net.minecraft.world.gen.feature.template.BlockMatchRuleTest
import net.minecraftforge.event.world.BiomeLoadingEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.eln2.ModBlocks

/**
 * Handles ore generation for Eln2
 */
object OreGen {

    private var ores: List<ConfiguredFeature<*, *>> = mutableListOf()

    /**
     * Configures ore generation in the world. Registers against Forge.
     */
    fun setupOreGeneration() {
         ores = ModBlocks.values().map {
            Feature.ORE.withConfiguration(
                OreFeatureConfig(BlockMatchRuleTest(Blocks.STONE),
                    it.block.defaultState,
                    4
                )
            ).range(64).square().func_242731_b(60000)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun gen(event: BiomeLoadingEvent) {
        ores.forEach {
            event.generation.withFeature(GenerationStage.Decoration.UNDERGROUND_ORES, it)
            print(event.generation.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES))
        }
    }
}


