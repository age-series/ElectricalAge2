@file:Suppress("unused")

package org.eln2.mc.common.content.modules.world

import net.minecraft.tags.BlockTags
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.utils.addUnique
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.content.modules.ContentManager.withBlockTint
import org.eln2.mc.common.content.modules.ContentManager.withItemTint
import org.eln2.mc.common.content.modules.ContentManager.withItemTagDatagen
import org.eln2.mc.common.content.modules.ContentManager.withTagDatagen
import org.eln2.mc.common.content.modules.ContentModule
import org.eln2.mc.common.content.modules.Eln2ConventionTags
import org.eln2.mc.common.content.modules.Eln2Ingredients
import org.eln2.mc.common.items.ItemRegistry
import java.util.function.Supplier

object Eln2Ores : ContentModule() {
    //#region Registration Helpers

    interface OreRegistryItem : Supplier<Block> {
        val rawOreItem: RegistryObject<Item>
        val oreBlock: RegistryObject<Block>
        val oreBlockItem: RegistryObject<BlockItem>

        override fun get() = oreBlock.get()
    }

    /**
     * Read by [org.eln2.mc.Eln2BlockOreDropLootDatagen].
     * */
    val ORE_DROP_BLOCKS_FOR_DATAGEN = LinkedHashSet<OreRegistryItem>()

    private fun<T : OreRegistryItem> T.withLootDatagen() : T {
        ORE_DROP_BLOCKS_FOR_DATAGEN.addUnique(this) {
            "Duplicate default ore loot $this"
        }

        return this
    }

    /**
     * Read by [org.eln2.mc.Eln2RecipeProviderDatagen].
     * */
    val ORE_SMELTING_FOR_DATAGEN = LinkedHashSet<Pair<OreRegistryItem, Supplier<Item>>>()

    private fun<T : OreRegistryItem> T.withVanillaSmeltingDatagen(result: Supplier<Item>) : T {
        ORE_SMELTING_FOR_DATAGEN.addUnique(Pair(this, result)) {
            "Duplicate vanilla smelting $this"
        }

        return this
    }

    /**
     * Read by [org.eln2.mc.Eln2BlockStateProviderDatagen] and [org.eln2.mc.Eln2ItemModelProviderDatagen].
     * */
    val ORE_FOR_MODEL_DATAGEN = LinkedHashSet<OreRegistryItem>()

    /**
     * Registers multiple things:
     * - [OreRegistryItem.rawOreItem] tint [color] on layer `0` (used by the generated model)
     * - [OreRegistryItem.oreBlockItem] tint [color] on layer `1` (used by the generated model)
     * - [OreRegistryItem.oreBlock] tint [color] on layer `1` (used by the generated model)
     * - Model generator in [ORE_FOR_MODEL_DATAGEN], which generates the block model, block item model, raw ore model.
     * */
    private fun<T : OreRegistryItem> T.withModelDatagen(color: MyColor) : T {
        this.rawOreItem.withItemTint(0, color)
        this.oreBlockItem.withItemTint(1, color)
        this.oreBlock.withBlockTint(1, color)

        ORE_FOR_MODEL_DATAGEN.addUnique(this) {
            DEBUGGER_BREAK("Duplicate default ore model $this")
        }

        return this
    }

    /**
     * Registers an item tag datagen for the raw item of this ore.
     * */
    private fun<T : OreRegistryItem> T.withRawItemTagDatagen(tag: net.minecraft.tags.TagKey<Item>) : T {
        this.rawOreItem.withItemTagDatagen(tag)
        return this
    }

    data class BasicOreRegistryItem(
        override val rawOreItem: RegistryObject<Item>,
        override val oreBlock: RegistryObject<Block>,
        override val oreBlockItem: RegistryObject<BlockItem>
    ) : OreRegistryItem

    /**
     * Registers an ore consisting of a raw item form (`raw_`[oreName]), block and item ([oreName]`_ore`).
     * Drops and worldgen need to be added in the datapacks.
     * */
    fun basicOre(oreName: String) : BasicOreRegistryItem {
        val rawOreItem = ItemRegistry.item("raw_$oreName") {
            Item(Item.Properties())
        }

        val oreBlock = BlockRegistry.blockOnly("${oreName}_ore") {
            Block(BlockBehaviour.Properties.copy(Blocks.IRON_ORE).requiresCorrectToolForDrops())
        }

        val oreBlockItem = BlockRegistry.blockItemOnly("${oreName}_ore") {
            BlockItem(oreBlock.get(), Item.Properties())
        }

        return BasicOreRegistryItem(rawOreItem, oreBlock, oreBlockItem)
    }

    //#endregion

    val MAGNETITE_ORE = basicOre("magnetite")
        .withTagDatagen(BlockTags.MINEABLE_WITH_PICKAXE)
        .withTagDatagen(BlockTags.NEEDS_IRON_TOOL)
        .withLootDatagen()
        .withModelDatagen(MyColor(0xFF333333))
        .withVanillaSmeltingDatagen { Items.IRON_INGOT }

    val LEAD_ORE = basicOre("lead")
        .withTagDatagen(BlockTags.MINEABLE_WITH_PICKAXE)
        .withTagDatagen(BlockTags.NEEDS_STONE_TOOL)
        .withLootDatagen()
        .withModelDatagen(MyColor(90, 90, 120))
        .withVanillaSmeltingDatagen(Eln2Ingredients.LEAD_INGOT)

    val TIN_ORE = basicOre("tin")
        .withTagDatagen(BlockTags.MINEABLE_WITH_PICKAXE)
        .withTagDatagen(BlockTags.NEEDS_IRON_TOOL)
        .withLootDatagen()
        .withModelDatagen(MyColor(144, 135, 131))
        .withVanillaSmeltingDatagen(Eln2Ingredients.TIN_INGOT)

    val SULFUR_ORE = basicOre("sulfur")
        .withTagDatagen(BlockTags.MINEABLE_WITH_PICKAXE)
        .withTagDatagen(BlockTags.NEEDS_STONE_TOOL)
        .withLootDatagen()
        .withModelDatagen(MyColor(200, 180, 50))
        .withRawItemTagDatagen(Eln2ConventionTags.RAW_MATERIAL_SULFUR)

    val ASBESTOS_ORE = basicOre("asbestos")
        .withTagDatagen(BlockTags.MINEABLE_WITH_PICKAXE)
        .withTagDatagen(BlockTags.NEEDS_STONE_TOOL)
        .withLootDatagen()
        .withModelDatagen(MyColor(180, 180, 190))

    val SALTPETER_ORE = basicOre("saltpeter")
        .withTagDatagen(BlockTags.MINEABLE_WITH_PICKAXE)
        .withTagDatagen(BlockTags.NEEDS_STONE_TOOL)
        .withLootDatagen()
        .withModelDatagen(MyColor(255, 230, 200))

    val SCHEELITE_ORE = basicOre("scheelite")
        .withTagDatagen(BlockTags.MINEABLE_WITH_PICKAXE)
        .withTagDatagen(BlockTags.NEEDS_IRON_TOOL)
        .withLootDatagen()
        .withModelDatagen(MyColor(255, 245, 200))

    val TRONA_ORE = basicOre("trona")
        .withTagDatagen(BlockTags.MINEABLE_WITH_PICKAXE)
        .withTagDatagen(BlockTags.NEEDS_STONE_TOOL)
        .withLootDatagen()
        .withModelDatagen(MyColor(200, 195, 185))
}
