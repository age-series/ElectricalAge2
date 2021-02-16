package org.eln2.utils

import net.minecraft.block.Blocks
import net.minecraft.util.ResourceLocation
import net.minecraft.util.registry.Registry
import net.minecraft.util.registry.WorldGenRegistries
import net.minecraft.world.gen.GenerationStage
import net.minecraft.world.gen.feature.ConfiguredFeature
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.OreFeatureConfig
import net.minecraftforge.event.world.BiomeLoadingEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.ModBlocks

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
         ores = ModBlocks.values().map {
            Feature.ORE.withConfiguration(
                OreFeatureConfig(OreFeatureConfig.FillerBlockType.BASE_STONE_OVERWORLD,
                    it.block.defaultState,
                    4
                )
            ).range(64).square().func_242731_b(120)
        }
    }

    fun biomeModification(event: BiomeLoadingEvent) {
        ores.forEach{
            event.generation.withFeature(GenerationStage.Decoration.UNDERGROUND_ORES, it)
            print(event.generation.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES))
        }
    }
}
