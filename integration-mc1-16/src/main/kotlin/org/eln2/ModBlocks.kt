package org.eln2

import net.minecraft.block.Block
import org.eln2.blocks.FlubberBlock
import org.eln2.blocks.OreBlock
import org.eln2.node.NodeBlock
import org.eln2.utils.OreData

/**
 * Blocks added here are automatically registered.
 */
enum class ModBlocks(val block: Block) {
    FLUBBER(FlubberBlock()),
    ORE_NATIVE_COPPER(OreBlock(OreData(1.0f, "ore_native_copper",1.0F, ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    NODE_BLOCK(NodeBlock()),
    ORE_ACANTHITE(OreBlock(OreData(1.0f,"ore_acanthite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_ANTHRACITE_COAL(OreBlock(OreData(1.0f,"ore_anthracite_coal",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_AZURITE(OreBlock(OreData(1.0f,"ore_azurite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_BAUXITE(OreBlock(OreData(1.0f,"ore_bauxite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_BISMUTHINITE(OreBlock(OreData(1.0f,"ore_bismuthinite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_BITUMINOUS_COAL(OreBlock(OreData(1.0f,"ore_bituminous_coal",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_BORAX(OreBlock(OreData(1.0f,"ore_borax",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CASSITERITE(OreBlock(OreData(1.0f,"ore_cassiterite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CATTIERITE(OreBlock(OreData(1.0f,"ore_cattierite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CERUSSITE(OreBlock(OreData(1.0f,"ore_cerussite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CHALCOCITE(OreBlock(OreData(1.0f,"ore_chalcocite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CHALCOPYRITE(OreBlock(OreData(1.0f,"ore_chalcopyrite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CHROMITE(OreBlock(OreData(1.0f,"ore_chromite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CASSITERITE_RARE(OreBlock(OreData(1.0f,"ore_cassiterite_rare",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CINNABAR(OreBlock(OreData(1.0f,"ore_cinnabar",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CORUNDUM(OreBlock(OreData(1.0f,"ore_corundum",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_CRYOLITE(OreBlock(OreData(1.0f,"ore_cryolite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_DIAMOND(OreBlock(OreData(1.0f,"ore_diamond",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_ESKOLAITE(OreBlock(OreData(1.0f,"ore_eskolaite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_FLUORITE(OreBlock(OreData(1.0f,"ore_fluorite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_GALENA(OreBlock(OreData(1.0f,"ore_galena",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_GRAPHITE(OreBlock(OreData(1.0f,"ore_graphite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_HALITE(OreBlock(OreData(1.0f,"ore_halite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_HEMATITE(OreBlock(OreData(1.0f,"ore_hematite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_ILMENITE(OreBlock(OreData(1.0f,"ore_ilmenite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_KERNITE(OreBlock(OreData(1.0f,"ore_kernite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_LEPIDOLITE(OreBlock(OreData(1.0f,"ore_lepidolite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_LIGNITE(OreBlock(OreData(1.0f,"ore_lignite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_LIMONITE(OreBlock(OreData(1.0f,"ore_limonite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_MAGNETITE(OreBlock(OreData(1.0f,"ore_magnetite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_MALACHITE(OreBlock(OreData(1.0f,"ore_malachite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_MILLERITE(OreBlock(OreData(1.0f,"ore_millerite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_NATIVE_GOLD(OreBlock(OreData(1.0f,"ore_native_gold",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_NATIVE_PLATNIUM(OreBlock(OreData(1.0f,"ore_native_platnium",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_NATIVE_SULFUR(OreBlock(OreData(1.0f,"ore_native_sulfur",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_PENTLANDITE(OreBlock(OreData(1.0f,"ore_pentlandite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_PETALITE(OreBlock(OreData(1.0f,"ore_petalite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_PSILOMELANE(OreBlock(OreData(1.0f,"ore_psilomelane",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_PYRITE(OreBlock(OreData(1.0f,"ore_pyrite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_PYROLUSITE(OreBlock(OreData(1.0f,"ore_pyrolusite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_QUARTZ(OreBlock(OreData(1.0f,"ore_quartz",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_RHODOCHROSITE(OreBlock(OreData(1.0f,"ore_rhodochrosite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_RUTILE(OreBlock(OreData(1.0f,"ore_rutile",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_SCHEELITE(OreBlock(OreData(1.0f,"ore_scheelite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_SIDERITE(OreBlock(OreData(1.0f,"ore_siderite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_SILVER(OreBlock(OreData(1.0f,"ore_silver",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_SMITHSONITE(OreBlock(OreData(1.0f,"ore_smithsonite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_SPHALERITE(OreBlock(OreData(1.0f,"ore_sphalerite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_SPHEROCOBALTITE(OreBlock(OreData(1.0f,"ore_spherocobaltite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_SPODUMENE(OreBlock(OreData(1.0f,"ore_spodumene",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_STANNITE(OreBlock(OreData(1.0f,"ore_stannite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_SYLVITE(OreBlock(OreData(1.0f,"ore_sylvite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_TANTALITE(OreBlock(OreData(1.0f,"ore_tantalite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_TITANITE(OreBlock(OreData(1.0f,"ore_titanite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance))),
    ORE_WOLFRAMITE(OreBlock(OreData(1.0f,"ore_wolframite",1.0F,ModItems.ORE_CHUNKS_COPPER.items.defaultInstance)))
}
