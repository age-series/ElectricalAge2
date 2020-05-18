package org.eln2.utils

import net.minecraft.block.Block
import net.minecraft.world.biome.Biome
import net.minecraft.world.gen.GenerationStage
import net.minecraft.world.gen.feature.ConfiguredFeature
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.OreFeatureConfig
import net.minecraft.world.gen.placement.CountRangeConfig
import net.minecraft.world.gen.placement.Placement
import net.minecraftforge.registries.ForgeRegistries
import org.eln2.ModBlocks

/**
 * Handles ore generation for Eln2
 */
class OreGen {
	companion object {
		/**
		 * Configures ore generation in the world. Registers against Forge.
		 */
		// TODO: Allow user to configure each ore (rate/on/off) in the Eln2 configuration file under the `ores` section
		fun setupOreGeneration() {
			ForgeRegistries.BIOMES.filter {
				it.category != Biome.Category.NETHER && it.category != Biome.Category.THEEND
			}.forEach {
				/*
				Flubber generation

				it.addFeature(
					GenerationStage.Decoration.UNDERGROUND_ORES,
					getOreFeature(ModBlocks.FLUBBER.block,
						OreGenSpec(3, 4000, 0, 128)
					))
				 */
				it.addFeature(
					GenerationStage.Decoration.UNDERGROUND_ORES,
					getOreFeature(ModBlocks.ORE_NATIVE_COPPER.block,
						OreGenSpec(8, 128, 0, 128)
					))
			}
		}

		/**
		 * getOreFeature
		 *
		 * Gets an configuration from an ore block and an ore gen spec.
		 * @param block The block to generate in the world
		 * @param ogs The ore generation specification which tells the world generator how to generate the ore.
		 */
		private fun getOreFeature(block: Block, ogs: OreGenSpec): ConfiguredFeature<*, *> {
			return Feature.ORE.withConfiguration(
				OreFeatureConfig(
					OreFeatureConfig.FillerBlockType.NATURAL_STONE,
					block.defaultState,
					5)
			).withPlacement(
				Placement.COUNT_RANGE.configure(CountRangeConfig(ogs.count, ogs.yMin, 0, ogs.yMax)))
		}
	}
}

/**
 * Ore Gen Spec
 *
 * A basic data class to store data about ore generation (size, where it spawns, how much per chunk, etc.
 * @param size The size of the generation
 * @param count The number of ore per chunk to try and generate
 * @param yMin The Y minimum for ore generation
 * @param yMax The Y maximum for ore generation
 */
data class OreGenSpec(val size: Int, val count: Int, val yMin: Int, val yMax: Int)
