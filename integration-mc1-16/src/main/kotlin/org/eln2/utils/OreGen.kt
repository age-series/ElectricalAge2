package org.eln2.utils

import net.minecraft.world.gen.GenerationStage
import net.minecraft.world.gen.feature.ConfiguredFeature
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.OreFeatureConfig
import net.minecraftforge.event.world.BiomeLoadingEvent
import net.minecraftforge.fml.common.Mod
import org.eln2.registry.OreBlocks

/**
 * Handles ore generation for Eln2
 */
@Mod.EventBusSubscriber
object OreGen {

    private var ores: List<ConfiguredFeature<*, *>> = mutableListOf()

    /**
     * Configures ore generation in the world
     */
    fun setupOreGeneration() {
         ores = OreBlocks.values().map {
             Feature.ORE.configured(
                 OreFeatureConfig(OreFeatureConfig.FillerBlockType.NATURAL_STONE,
                     it.block.defaultBlockState(),
                     (it.block.getRarity()*2) //Vein size, calculated using the ore rarity times 2
                 )
             ).range(
                 (it.block.getRarity()*25) // Max height for the block to appear, calculated using rarity times 25 for a max of 125
             ).squared().chance(it.block.getRarity())
        }
    }

    /**
     * Registers the ore generation with Forge
     *
     * @event The event from Forge as part of the annotation.
     */
    fun registerOreGeneration(event: BiomeLoadingEvent) {
        ores.forEach{
            event.generation.addFeature(GenerationStage.Decoration.UNDERGROUND_ORES, it)
        }
    }
}
