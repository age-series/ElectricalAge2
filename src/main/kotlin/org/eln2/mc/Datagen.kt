package org.eln2.mc

import net.minecraft.client.renderer.block.model.BlockModel.GuiLight
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.data.PackOutput
import net.minecraft.data.loot.BlockLootSubProvider
import net.minecraft.data.recipes.*
import net.minecraft.data.tags.TagsProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.ItemTags
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.*
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.material.Fluids
import net.minecraftforge.client.model.generators.BlockStateProvider
import net.minecraftforge.client.model.generators.ItemModelBuilder
import net.minecraftforge.client.model.generators.ItemModelProvider
import net.minecraftforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder
import net.minecraftforge.client.model.generators.loaders.ItemLayerModelBuilder
import net.minecraftforge.common.data.BlockTagsProvider
import net.minecraftforge.common.data.ExistingFileHelper
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.registries.ForgeRegistries
import org.ageseries.libage.data.CELSIUS
import org.ageseries.libage.data.Quantity
import org.eln2.mc.common.content.modules.*
import org.eln2.mc.common.content.modules.Eln2ForgeFluids.requireBottle
import org.eln2.mc.common.content.modules.world.Eln2Ores
import org.eln2.mc.common.content.processing.AlloyingRecipe
import org.eln2.mc.common.content.processing.BurningRecipe
import org.eln2.mc.common.content.processing.HydrogenReductionRecipe
import org.eln2.mc.common.content.processing.NonSeparatedAqueousElectrolysisRecipe
import org.eln2.mc.common.content.processing.SeparatedAqueousElectrolysisRecipe
import org.eln2.mc.common.fluids.ForgeFluidRegistry
import org.eln2.mc.common.recipes.foundation.CatalyzedSimpleProcessingRecipeBuilder
import org.eln2.mc.common.recipes.foundation.DirectSimpleProcessingRecipeBuilder
import org.eln2.mc.common.recipes.foundation.Eln2WeightedItemIngredient
import org.eln2.mc.extensions.blockID
import org.eln2.mc.extensions.itemID
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Registers blocks dropping their block item when broken, from [ContentManager.SELF_DROP_BLOCKS_FOR_DATAGEN].
 * */
class Eln2BlockSelfDropLootDatagen : BlockLootSubProvider(emptySet(), FeatureFlags.REGISTRY.allFlags()) {
    override fun generate() {
        ContentManager.SELF_DROP_BLOCKS_FOR_DATAGEN.forEach { blockSupplier ->
            val block = blockSupplier.get()

            this.dropSelf(block)

            LOG.info("Registered self drop for {} ({})", block, block.blockID)
        }
    }

    override fun getKnownBlocks() = ContentManager.SELF_DROP_BLOCKS_FOR_DATAGEN.map {
        it.get()
    }
}

/**
 * Registers ore blocks dropping raw ore with default enchant configs, from [Eln2Ores.ORE_DROP_BLOCKS_FOR_DATAGEN].
 * */
class Eln2BlockOreDropLootDatagen : BlockLootSubProvider(emptySet(), FeatureFlags.REGISTRY.allFlags()) {
    override fun generate() {
        Eln2Ores.ORE_DROP_BLOCKS_FOR_DATAGEN.forEach { obj ->
            val raw = obj.rawOreItem.get()
            val block = obj.oreBlock.get()

            this.add(block) {
                createOreDrop(block, raw)
            }

            LOG.info("Registered ore drop for {} ({})", block, block.blockID)
        }
    }

    override fun getKnownBlocks() = Eln2Ores.ORE_DROP_BLOCKS_FOR_DATAGEN.map {
        it.oreBlock.get()
    }
}

/**
 * Registers tags for blocks, from [ContentManager.BLOCK_TAGS_FOR_DATAGEN].
 * */
class Eln2BlockTagsDatagen(output: PackOutput, lookupProvider: CompletableFuture<HolderLookup.Provider>, existingFileHelper: ExistingFileHelper) : BlockTagsProvider(output, lookupProvider, MODID, existingFileHelper) {
    override fun addTags(provider: HolderLookup.Provider) {
        ContentManager.BLOCK_TAGS_FOR_DATAGEN.forEach { (blockSupplier, tagKey) ->
            val block = blockSupplier.get()

            tag(tagKey).add(block)

            LOG.info(
                "Registered block tag {} for {}",
                tagKey,
                block
            )
        }
    }
}

/**
 * Generates item tag JSON files from [ContentManager.ITEM_TAGS_FOR_DATAGEN].
 * Tags our items with forge convention tags (e.g. `forge:ingots/tin`) so other mods' recipes that accept those tags can consume our items, and our recipes can accept their items.
 * */
class Eln2ItemTagsDatagen(output: PackOutput, lookupProvider: CompletableFuture<HolderLookup.Provider>, existingFileHelper: ExistingFileHelper) : TagsProvider<Item>(output, Registries.ITEM, lookupProvider, MODID, existingFileHelper) {
    override fun addTags(provider: HolderLookup.Provider) {
        ContentManager.ITEM_TAGS_FOR_DATAGEN.forEach { (itemSupplier, tagKey) ->
            val item = itemSupplier.get()
            val itemKey = ForgeRegistries.ITEMS.getResourceKey(item).orElseThrow()

            tag(tagKey).add(itemKey)

            LOG.info("Registered item tag {} for {} ({})", tagKey, item, item.itemID)
        }
    }
}

/**
 * Generates the block model from [Eln2Ores.ORE_FOR_MODEL_DATAGEN], which is built by [Eln2Ores.withModelDatagen] which registers tints on indices we use in the models.
 * */
class Eln2BlockStateProviderDatagen(output: PackOutput, existingFileHelper: ExistingFileHelper) : BlockStateProvider(output, MODID, existingFileHelper) {
    override fun registerStatesAndModels() {
        Eln2Ores.ORE_FOR_MODEL_DATAGEN.forEach { obj ->
            val oreBlock = obj.oreBlock.get()

            /**
             * Creates a model using two overlay textures:
             * - Stone base
             * - Overlay texture we prepared (see the markdown file), with tint index `1` (which we register into)
             * */
            val model = models().getBuilder(oreBlock.blockID.path)
                .renderType("minecraft:cutout")
                .parent(models().getExistingFile(mcLoc("block/block")))
                .texture("particle", mcLoc("block/stone"))
                .element()
                    .from(0f, 0f, 0f)
                    .to(16f, 16f, 16f)
                    .allFaces { face, builder ->
                        builder
                            .texture("#base")
                            .cullface(face)
                    }
                    .end()
                .element()
                    .from(0f, 0f, 0f)
                    .to(16f, 16f, 16f)
                    .allFaces { face, builder ->
                        builder
                            .texture("#overlay")
                            .cullface(face)
                            .tintindex(1)
                    }
                    .end()
                .texture("base", mcLoc("block/stone"))
                .texture("overlay", modLoc("block/ore_overlay"))

            simpleBlock(oreBlock, model)
        }
    }
}

/**
 * - Registers raw ore smelting, ore item smelting, raw ore blasting, from [Eln2Ores.ORE_SMELTING_FOR_DATAGEN].
 * - Registers assembly and disassembly recipes, from [Eln2Processing.PROCESSING_MACHINES_FOR_VISUAL_REGISTRATION_AND_DATAGEN].
 * - Registers recipes from all repositories in [Eln2Ingredients].
 * - Builds manual recipes.
 * */
class Eln2RecipeProviderDatagen(output: PackOutput) : RecipeProvider(output) {
    /**
     * Returns an [Ingredient] that prefers the forge convention tag for [item] if one exists, falling back to a concrete item ingredient. This lets our recipes accept items from other mods that are registered to the same forge convention tag.
     */
    private fun taggedIngredient(item: Item): Ingredient {
        val tag = ContentManager.tagForItem(item)

        return if (tag != null) {
            Ingredient.of(tag)
        }
        else {
            Ingredient.of(item)
        }
    }

    private fun buildManualRecipes(pWriter: Consumer<FinishedRecipe?>) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2ForgeFluids.INSULATING_VARNISH.requireBottle().bottleItem.get())
            .requires(Eln2ForgeFluids.NAPHTHA.requireBottle().bottleItem.get())
            .requires(Eln2Ingredients.RAW_RESIN.get())
            .requires(Items::GLASS_BOTTLE)
            .unlockedBy("has_raw_resin", has(Eln2Ingredients.RAW_RESIN.get()))
            .save(pWriter, resource("crafting/insulating_varnish_bottle_from_resin"))
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Ingredients.CREOSOTE_BRUSH.get())
            .requires(Eln2ForgeFluids.CREOSOTE.requireBottle().bottleItem.get())
            .requires(Items.BRUSH)
            .unlockedBy("has_creosote_bottle", has(Eln2ForgeFluids.CREOSOTE.requireBottle().bottleItem.get()))
            .save(pWriter, resource("crafting/creosote_brush"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Ingredients.ENAMEL_BRUSH.get())
            .requires(Eln2ForgeFluids.INSULATING_VARNISH.requireBottle().bottleItem.get())
            .requires(Items.BRUSH)
            .unlockedBy("has_insulating_varnish_bottle", has(Eln2ForgeFluids.INSULATING_VARNISH.requireBottle().bottleItem.get()))
            .save(pWriter, resource("crafting/enamel_brush"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.ENAMELED_COPPER_WIRE.get(), 8)
            .pattern("WWW")
            .pattern("WBW")
            .pattern("WWW")
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('B', Eln2Ingredients.ENAMEL_BRUSH.get())
            .unlockedBy("has_enamel_brush", has(Eln2Ingredients.ENAMEL_BRUSH.get()))
            .save(pWriter, resource("crafting/enameled_copper_wire"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.CARBON_PUTTY.get(), 8)
            .pattern("CCC")
            .pattern("CBC")
            .pattern("CCC")
            .define('C', Eln2Ingredients.COKE_DUST.get())
            .define('B', Eln2ForgeFluids.PITCH.requireBottle().bottleItem.get())
            .unlockedBy("has_coke_dust", has(Eln2Ingredients.COKE_DUST.get()))
            .save(pWriter, resource("crafting/carbon_putty"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.TUNGSTEN_TRIOXIDE_PASTE.get(), 8)
            .pattern("TTT")
            .pattern("TBT")
            .pattern("TTT")
            .define('T', Eln2Ingredients.TUNGSTEN_TRIOXIDE_DUST.get())
            .define('B', Eln2ForgeFluids.PITCH.requireBottle().bottleItem.get())
            .unlockedBy("has_tungsten_trioxide_dust", has(Eln2Ingredients.TUNGSTEN_TRIOXIDE_DUST.get()))
            .save(pWriter, resource("crafting/tungsten_trioxide_paste"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.RAW_CARBON_BRUSH.get())
            .pattern("CCW")
            .define('C', Eln2Ingredients.CARBON_PUTTY.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_putty", has(Eln2Ingredients.CARBON_PUTTY.get()))
            .save(pWriter, resource("crafting/raw_carbon_brush"))

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(Eln2Ingredients.RAW_CARBON_BRUSH.get()), RecipeCategory.MISC, Eln2Ingredients.CARBON_BRUSH.get(), 0.5f, 200)
            .unlockedBy("has_raw_carbon_brush", has(Eln2Ingredients.RAW_CARBON_BRUSH.get()))
            .save(pWriter, resource("smelting/raw_carbon_brush_to_carbon_brush_smelting"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.IRON_AXLE_MOUNT.get(), 4)
            .pattern(" I ")
            .pattern("P P")
            .pattern(" I ")
            .define('I', Items::IRON_INGOT)
            .define('P', Eln2Ingredients.IRON_PLATE.get())
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/iron_axle_mount"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.IRON_GEAR.get(), 4)
            .pattern(" I ")
            .pattern("IPI")
            .pattern(" I ")
            .define('I', Items::IRON_INGOT)
            .define('P', Eln2Ingredients.IRON_PLATE.get())
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/iron_gear"))

        CatalyzedSimpleProcessingRecipeBuilder(Eln2Processing.EXTRUDING_RECIPE)
            .withInput(Eln2Ingredients.HOT_IRON_INGOT.get())
            .withCatalyst(Eln2Processing.EXTRUDER_SHAFT_DIE.get())
            .withOutput(Eln2Ingredients.IRON_SHAFT.get())
            .withDuration(20.0)
            .unlockedBy("has_shaft_die", has(Eln2Processing.EXTRUDER_SHAFT_DIE.get()))
            .save(pWriter, resource("extruding/iron_shaft"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Kinetic.PRIMITIVE_STANDARD_IRON_STRAIGHT_JOINT.partInfo.item.get(), 4)
            .pattern(" L ")
            .pattern("BSB")
            .pattern(" P ")
            .define('L', Eln2Ingredients.CREOSOTE_BRUSH.get())
            .define('B', Eln2Ingredients.IRON_AXLE_MOUNT.get())
            .define('S', Eln2Ingredients.IRON_SHAFT.get())
            .define('P', Eln2Ingredients.IRON_PLATE.get())
            .unlockedBy("has_mount", has(Eln2Ingredients.IRON_AXLE_MOUNT.get()))
            .save(pWriter, resource("crafting/primitive_standard_iron_straight_joint"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Kinetic.PRIMITIVE_STANDARD_IRON_HUB_JOINT.partInfo.item.get(), 4)
            .pattern("LM ")
            .pattern("RGR")
            .pattern(" M ")
            .define('L', Eln2Ingredients.CREOSOTE_BRUSH.get())
            .define('M', Eln2Ingredients.IRON_AXLE_MOUNT.get())
            .define('R', Eln2Ingredients.IRON_SHAFT.get())
            .define('G', Eln2Ingredients.IRON_GEAR.get())
            .unlockedBy("has_gear", has(Eln2Ingredients.IRON_GEAR.get()))
            .save(pWriter, resource("crafting/primitive_standard_iron_hub_joint"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.COKE_OVEN_BLOCK_ITEM.get())
            .pattern("PBP")
            .pattern("B B")
            .pattern("BFB")
            .define('P', Eln2Ingredients.IRON_PLATE.get())
            .define('B', Items.BRICKS)
            .define('F', Items.FURNACE)
            .unlockedBy("has_bricks", has(Items.BRICKS))
            .save(pWriter, resource("crafting/coke_oven"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2ForgeFluids.FLUID_PIPE_BLOCK.item.get(), 8)
            .pattern("PPP")
            .define('P', Eln2Ingredients.IRON_PLATE.get())
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/fluid_pipe"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2ForgeFluids.GAS_RELEASE_BLOCK.item.get())
            .pattern("PG")
            .define('P', Eln2ForgeFluids.FLUID_PIPE_BLOCK.item.get())
            .define('G', Items.IRON_TRAPDOOR)
            .unlockedBy("has_fluid_pipe", has(Eln2ForgeFluids.FLUID_PIPE_BLOCK.item.get()))
            .save(pWriter, resource("crafting/gas_release"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2ForgeFluids.FLUID_PIPE_INSERTION_VALVE.get())
            .pattern("IGI")
            .define('I', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('G', Items.GLASS_PANE)
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/fluid_pipe_insertion_valve"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2ForgeFluids.FLUID_PIPE_EXTRACTION_VALVE.get())
            .pattern(" R ")
            .pattern("PGP")
            .pattern(" S ")
            .define('R', Items.REDSTONE)
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('G', Eln2Ingredients.IRON_GEAR.get())
            .define('S', Items.PISTON)
            .unlockedBy("has_iron_gear", has(Eln2Ingredients.IRON_GEAR.get()))
            .save(pWriter, resource("crafting/fluid_pipe_extraction_valve"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.PIPE_HEAT_EXCHANGE_ASSEMBLY.get())
            .pattern("CPC")
            .pattern("CPC")
            .pattern("CPC")
            .define('C', taggedIngredient(Eln2Ingredients.COPPER_PLATE.get()))
            .define('P', Eln2ForgeFluids.FLUID_PIPE_BLOCK.item.get())
            .unlockedBy("has_copper_plate", has(Eln2Ingredients.COPPER_PLATE.get()))
            .save(pWriter, resource("crafting/pipe_heat_exchange_assembly"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.CONDENSER_DISTILLATION_MODULE_BLOCK.get())
            .pattern(" P ")
            .pattern("PAP")
            .pattern(" P ")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('A', Eln2Ingredients.PIPE_HEAT_EXCHANGE_ASSEMBLY.get())
            .unlockedBy("has_pipe_heat_exchange_assembly", has(Eln2Ingredients.PIPE_HEAT_EXCHANGE_ASSEMBLY.get()))
            .save(pWriter, resource("crafting/condenser_distillation_module"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.INSULATED_DISTILLATION_MODULE_BLOCK.get())
            .pattern(" F ")
            .pattern("FAF")
            .pattern(" F ")
            .define('F', Eln2Ingredients.ASBESTOS_FIBER.get())
            .define('A', Eln2Ingredients.PIPE_HEAT_EXCHANGE_ASSEMBLY.get())
            .unlockedBy("has_asbestos_fiber", has(Eln2Ingredients.ASBESTOS_FIBER.get()))
            .save(pWriter, resource("crafting/insulated_distillation_module"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.DISTILLATION_COLUMN_BLOCK.item.get())
            .pattern("AIA")
            .pattern("I I")
            .pattern("AIA")
            .define('A', Eln2Ingredients.ASBESTOS_FIBER.get())
            .define('I', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_asbestos_fiber", has(Eln2Ingredients.ASBESTOS_FIBER.get()))
            .save(pWriter, resource("crafting/distillation_column"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2ForgeFluids.IRON_TANK.blockAndItem.item.get())
            .pattern("PPP")
            .pattern("P P")
            .pattern("PPP")
            .define('P', Eln2Ingredients.IRON_PLATE.get())
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/iron_tank"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Wires.STANDARD_INSULATED_COPPER_ELECTRICAL_WIRE.part.item.get())
            .requires(Eln2Ingredients.COPPER_WIRE.get())
            .requires(Eln2Ingredients.RUBBER.get())
            .unlockedBy("has_rubber", has(Eln2Ingredients.RUBBER.get()))
            .save(pWriter, resource("crafting/standard_insulated_copper_wire"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Wires.SIGNAL_WIRE.part.item.get(), 8)
            .requires(Eln2Ingredients.COPPER_WIRE.get())
            .requires(Eln2Ingredients.RUBBER.get())
            .requires(Items.REDSTONE)
            .unlockedBy("has_rubber", has(Eln2Ingredients.RUBBER.get()))
            .save(pWriter, resource("crafting/signal_wire"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Wires.STANDARD_UNINSULATED_COPPER_THERMAL_WIRE.part.item.get(), 4)
            .pattern("P")
            .pattern("P")
            .pattern("P")
            .define('P', Eln2Ingredients.COPPER_PLATE.get())
            .unlockedBy("has_copper_plate", has(Eln2Ingredients.COPPER_PLATE.get()))
            .save(pWriter, resource("crafting/standard_uninsulated_copper_thermal_wire"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Wires.STANDARD_INSULATED_COPPER_THERMAL_WIRE.part.item.get(), 4)
            .pattern("APA")
            .pattern("APA")
            .pattern("APA")
            .define('A', Eln2Ingredients.ASBESTOS_FIBER.get())
            .define('P', Eln2Ingredients.COPPER_PLATE.get())
            .unlockedBy("has_asbestos_fiber", has(Eln2Ingredients.ASBESTOS_FIBER.get()))
            .save(pWriter, resource("crafting/standard_insulated_copper_thermal_wire"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Wires.THERMAL_RADIATOR_PART.item.get())
            .pattern("PPP")
            .pattern("WWW")
            .define('P', Eln2Ingredients.COPPER_PLATE.get())
            .define('W', Eln2Wires.STANDARD_UNINSULATED_COPPER_THERMAL_WIRE.part.item.get())
            .unlockedBy("has_uninsulated_thermal_wire", has(Eln2Wires.STANDARD_UNINSULATED_COPPER_THERMAL_WIRE.part.item.get()))
            .save(pWriter, resource("crafting/thermal_radiator"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.AUTOCLAVE_TRAY.get())
            .pattern("IGI")
            .define('I', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('G', Items.GLASS_PANE)
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/autoclave_tray"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.AUTOCLAVE_RADIATOR.get())
            .pattern("   ")
            .pattern(" W ")
            .pattern("WWW")
            .define('W', Eln2Wires.STANDARD_UNINSULATED_COPPER_THERMAL_WIRE.part.item.get())
            .unlockedBy("has_uninsulated_thermal_wire", has(Eln2Wires.STANDARD_UNINSULATED_COPPER_THERMAL_WIRE.part.item.get()))
            .save(pWriter, resource("crafting/autoclave_radiator"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.AUTOCLAVE_SHELL.get())
            .pattern(" I ")
            .pattern("IWI")
            .pattern(" I ")
            .define('I', Items.IRON_BLOCK)
            .define('W', Eln2Wires.STANDARD_UNINSULATED_COPPER_THERMAL_WIRE.part.item.get())
            .unlockedBy("has_uninsulated_thermal_wire", has(Eln2Wires.STANDARD_UNINSULATED_COPPER_THERMAL_WIRE.part.item.get()))
            .save(pWriter, resource("crafting/autoclave_shell"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.AUTOCLAVE_DOOR.get())
            .pattern("   ")
            .pattern(" IP")
            .pattern("   ")
            .define('I', Items.IRON_BLOCK)
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_iron_block", has(Items.IRON_BLOCK))
            .save(pWriter, resource("crafting/autoclave_door"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Processing.VULCANIZING_AUTOCLAVE_BLOCK_ITEM.get())
            .requires(Eln2Ingredients.AUTOCLAVE_SHELL.get())
            .requires(Eln2Ingredients.AUTOCLAVE_RADIATOR.get())
            .requires(Eln2Ingredients.AUTOCLAVE_TRAY.get())
            .requires(Eln2Ingredients.AUTOCLAVE_DOOR.get())
            .unlockedBy("has_autoclave_shell", has(Eln2Ingredients.AUTOCLAVE_SHELL.get()))
            .save(pWriter, resource("crafting/vulcanizing_autoclave"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.WIND_TURBINE_BLADE.get(), 4)
            .pattern("WS")
            .pattern("W ")
            .pattern("WS")
            .define('W', Ingredient.of(Eln2ConventionTags.WOODEN_SLABS))
            .define('S', Items.STICK)
            .unlockedBy("has_wooden_slabs", has(Eln2ConventionTags.WOODEN_SLABS))
            .save(pWriter, resource("crafting/wind_turbine_blade"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.WIND_TURBINE_CORE.get())
            .pattern(" W ")
            .pattern(" W ")
            .pattern("PWP")
            .define('W', Ingredient.of(ItemTags.PLANKS))
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/wind_turbine_core"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Kinetic.BASIC_WIND_TURBINE_BLOCK_ITEM.get())
            .pattern(" B ")
            .pattern("BCB")
            .pattern(" B ")
            .define('B', Eln2Ingredients.WIND_TURBINE_BLADE.get())
            .define('C', Eln2Ingredients.WIND_TURBINE_CORE.get())
            .unlockedBy("has_wind_turbine_core", has(Eln2Ingredients.WIND_TURBINE_CORE.get()))
            .save(pWriter, resource("crafting/basic_wind_turbine"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Ingredients.CRUDE_MAGNET.get())
            .requires(Eln2Ingredients.MAGNETITE_DUST.get())
            .requires(Ingredient.of(Eln2ConventionTags.GLUE))
            .unlockedBy("has_magnetite_dust", has(Eln2Ingredients.MAGNETITE_DUST.get()))
            .save(pWriter, resource("crafting/crude_magnet"))

        //#region Motor

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.COPPER_COIL.get(), 4)
            .pattern("WWW")
            .pattern("W W")
            .pattern("WWW")
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_copper_wire", has(Eln2Ingredients.COPPER_WIRE.get()))
            .save(pWriter, resource("crafting/copper_coil"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.CRUDE_MOTOR_STATOR.get())
            .pattern("PMP")
            .pattern("M M")
            .pattern("PMP")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('M', Eln2Ingredients.CRUDE_MAGNET.get())
            .unlockedBy("has_crude_magnet", has(Eln2Ingredients.CRUDE_MAGNET.get()))
            .save(pWriter, resource("crafting/crude_motor_stator"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.ROTOR_COIL_ASSEMBLY.get())
            .pattern("PCP")
            .pattern("CSC")
            .pattern("PCP")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('C', Eln2Ingredients.COPPER_COIL.get())
            .define('S', Eln2Ingredients.IRON_SHAFT.get())
            .unlockedBy("has_copper_coil", has(Eln2Ingredients.COPPER_COIL.get()))
            .save(pWriter, resource("crafting/rotor_coil_assembly"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.ROTOR_COMMUTATOR.get())
            .pattern("VC ")
            .pattern(" S ")
            .pattern("WWW")
            .define('V', Eln2ForgeFluids.INSULATING_VARNISH.requireBottle().bottleItem.get())
            .define('C', taggedIngredient(Eln2Ingredients.COPPER_PLATE.get()))
            .define('S', Eln2Ingredients.IRON_SHAFT.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_iron_shaft", has(Eln2Ingredients.IRON_SHAFT.get()))
            .save(pWriter, resource("crafting/rotor_commutator"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Ingredients.COPPER_ROTOR.get())
            .requires(Eln2Ingredients.ROTOR_COIL_ASSEMBLY.get())
            .requires(Eln2Ingredients.ROTOR_COMMUTATOR.get())
            .requires(Eln2Ingredients.IRON_GEAR.get())
            .unlockedBy("has_rotor_coil_assembly", has(Eln2Ingredients.ROTOR_COIL_ASSEMBLY.get()))
            .save(pWriter, resource("crafting/copper_rotor"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.BRUSHED_DC_MOTOR.get())
            .pattern("BCB")
            .pattern(" S ")
            .pattern(" G ")
            .define('B', Eln2Ingredients.CARBON_BRUSH.get())
            .define('C', Eln2Ingredients.COPPER_ROTOR.get())
            .define('S', Eln2Ingredients.CRUDE_MOTOR_STATOR.get())
            .define('G', Eln2Ingredients.IRON_GEAR.get())
            .unlockedBy("has_copper_rotor", has(Eln2Ingredients.COPPER_ROTOR.get()))
            .save(pWriter, resource("crafting/brushed_dc_motor"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.BRUSHED_DC_MOTOR_WORK_BOX.item.get())
            .pattern("P P")
            .pattern("M  ")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('M', Eln2Ingredients.BRUSHED_DC_MOTOR.get())
            .unlockedBy("has_brushed_dc_motor", has(Eln2Ingredients.BRUSHED_DC_MOTOR.get()))
            .save(pWriter, resource("crafting/brushed_dc_motor_work_box"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Kinetic.BASIC_DC_MOTOR_PART.item.get())
            .pattern("MRW")
            .pattern("G  ")
            .define('M', Eln2Ingredients.MACHINE_FRAME.get())
            .define('R', Eln2Ingredients.BRUSHED_DC_MOTOR.get())
            .define('W', Eln2Wires.STANDARD_INSULATED_COPPER_ELECTRICAL_WIRE.part.item.get())
            .define('G', Ingredient.of(Eln2ConventionTags.GLUE))
            .unlockedBy("has_brushed_dc_motor", has(Eln2Ingredients.BRUSHED_DC_MOTOR.get()))
            .save(pWriter, resource("crafting/basic_dc_motor_part"))

        //#endregion

        //#region Tools

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Tools.WRENCH.get())
            .pattern("I I")
            .pattern(" I ")
            .pattern(" I ")
            .define('I', Items.IRON_INGOT)
            .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
            .save(pWriter, resource("crafting/wrench"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Tools.SCREWDRIVER.get())
            .pattern(" I ")
            .pattern("III")
            .define('I', Items.IRON_INGOT)
            .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
            .save(pWriter, resource("crafting/screwdriver"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Grid.GRID_CABLE_PLIERS.get())
            .pattern("I I")
            .pattern("I I")
            .pattern(" I ")
            .define('I', Items.IRON_INGOT)
            .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
            .save(pWriter, resource("crafting/grid_cable_pliers"))

        //#endregion

        //#region Grid Cables

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Grid.POWER_GRID_CONNECT_COPPER.item.get(), 4)
            .pattern("W W")
            .pattern(" W ")
            .define('W', Eln2Ingredients.ENAMELED_COPPER_WIRE.get())
            .unlockedBy("has_enameled_copper_wire", has(Eln2Ingredients.ENAMELED_COPPER_WIRE.get()))
            .save(pWriter, resource("crafting/power_grid_copper_cable"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Grid.MICRO_GRID_CONNECT_COPPER.item.get(), 4)
            .pattern(" R ")
            .pattern(" R ")
            .define('R', Eln2Ingredients.COPPER_ROD.get())
            .unlockedBy("has_copper_rod", has(Eln2Ingredients.COPPER_ROD.get()))
            .save(pWriter, resource("crafting/micro_grid_copper_cable"))

        //#endregion

        //#region Grid Interfaces

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Grid.POWER_GRID_INTERFACE_PART.item.get(), 4)
            .pattern(" I ")
            .pattern("CCC")
            .define('I', Items.COPPER_INGOT)
            .define('C', Eln2Grid.POWER_GRID_CONNECT_COPPER.item.get())
            .unlockedBy("has_power_grid_cable", has(Eln2Grid.POWER_GRID_CONNECT_COPPER.item.get()))
            .save(pWriter, resource("crafting/power_grid_interface"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Grid.MICRO_GRID_INTERFACE_PART.item.get(), 4)
            .pattern(" R ")
            .pattern(" C ")
            .define('R', Eln2Ingredients.COPPER_ROD.get())
            .define('C', Eln2Grid.MICRO_GRID_CONNECT_COPPER.item.get())
            .unlockedBy("has_micro_grid_cable", has(Eln2Grid.MICRO_GRID_CONNECT_COPPER.item.get()))
            .save(pWriter, resource("crafting/micro_grid_interface"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Grid.MICRO_GRID_ANCHOR_SPEC.item.get(), 4)
            .pattern(" R ")
            .pattern(" P ")
            .define('R', Eln2Ingredients.COPPER_ROD.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_copper_rod", has(Eln2Ingredients.COPPER_ROD.get()))
            .save(pWriter, resource("crafting/micro_grid_anchor"))

        //#endregion

        //#region Simple Plate Recipes

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.LEAD_PLATE.get(), 2)
            .pattern("I  ")
            .pattern("I  ")
            .pattern("   ")
            .define('I', taggedIngredient(Eln2Ingredients.LEAD_INGOT.get()))
            .unlockedBy("has_lead_ingot", has(Eln2ConventionTags.INGOT_LEAD))
            .save(pWriter, resource("crafting/lead_plate_from_ingots"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.TIN_PLATE.get(), 2)
            .pattern("I  ")
            .pattern("I  ")
            .pattern("   ")
            .define('I', taggedIngredient(Eln2Ingredients.TIN_INGOT.get()))
            .unlockedBy("has_tin_ingot", has(Eln2ConventionTags.INGOT_TIN))
            .save(pWriter, resource("crafting/tin_plate_from_ingots"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.IRON_PLATE.get(), 2)
            .pattern("I  ")
            .pattern("I  ")
            .pattern("   ")
            .define('I', taggedIngredient(Items.IRON_INGOT))
            .unlockedBy("has_iron_ingot", has(Eln2ConventionTags.INGOT_IRON))
            .save(pWriter, resource("crafting/iron_plate_from_ingots"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.COPPER_PLATE.get(), 2)
            .pattern("I  ")
            .pattern("I  ")
            .pattern("   ")
            .define('I', taggedIngredient(Items.COPPER_INGOT))
            .unlockedBy("has_copper_ingot", has(Eln2ConventionTags.INGOT_COPPER))
            .save(pWriter, resource("crafting/copper_plate_from_ingots"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.BRONZE_PLATE.get(), 2)
            .pattern("I  ")
            .pattern("I  ")
            .pattern("   ")
            .define('I', taggedIngredient(Eln2Ingredients.BRONZE_INGOT.get()))
            .unlockedBy("has_bronze_ingot", has(Eln2ConventionTags.INGOT_BRONZE))
            .save(pWriter, resource("crafting/bronze_plate_from_ingots"))

        //#endregion

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.IRON_SHAFT.get())
            .pattern("   ")
            .pattern("III")
            .pattern("   ")
            .define('I', taggedIngredient(Items.IRON_INGOT))
            .unlockedBy("has_iron_ingot", has(Eln2ConventionTags.INGOT_IRON))
            .save(pWriter, resource("crafting/iron_shaft_from_ingots"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.COPPER_ROD.get())
            .pattern("   ")
            .pattern("II ")
            .pattern("   ")
            .define('I', taggedIngredient(Items.COPPER_INGOT))
            .unlockedBy("has_copper_ingot", has(Eln2ConventionTags.INGOT_COPPER))
            .save(pWriter, resource("crafting/copper_rod_from_ingots"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.IRON_GEAR.get())
            .pattern(" N ")
            .pattern("NIN")
            .pattern(" N ")
            .define('I', taggedIngredient(Items.IRON_INGOT))
            .define('N', Items.IRON_NUGGET)
            .unlockedBy("has_iron_ingot", has(Eln2ConventionTags.INGOT_IRON))
            .save(pWriter, resource("crafting/iron_gear_simple"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.PRIMITIVE_KINETIC_WORK_BOX.item.get())
            .pattern("P P")
            .pattern("S G")
            .pattern("   ")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('S', Eln2Ingredients.IRON_SHAFT.get())
            .define('G', Eln2Ingredients.IRON_GEAR.get())
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/primitive_kinetic_work_box"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.MACHINE_FRAME.get(), 4)
            .pattern(" P ")
            .pattern("P P")
            .pattern(" P ")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/machine_frame"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.CRUSHER_DRUM.get(), 2)
            .pattern("POP")
            .pattern("OSO")
            .pattern("POP")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('O', Items.OBSIDIAN)
            .define('S', Eln2Ingredients.IRON_SHAFT.get())
            .unlockedBy("has_obsidian", has(Items.OBSIDIAN))
            .save(pWriter, resource("crafting/crusher_drum"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.CRUSHER_ASSEMBLY.get())
            .pattern("GPG")
            .pattern("D D")
            .pattern("GPG")
            .define('G', Eln2Ingredients.IRON_GEAR.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('D', Eln2Ingredients.CRUSHER_DRUM.get())
            .unlockedBy("has_crusher_drum", has(Eln2Ingredients.CRUSHER_DRUM.get()))
            .save(pWriter, resource("crafting/crusher_assembly"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.CRUSHER_HULL.hullItem.get())
            .pattern("MAG")
            .pattern("B  ")
            .pattern("   ")
            .define('M', Eln2Ingredients.MACHINE_FRAME.get())
            .define('A', Eln2Ingredients.CRUSHER_ASSEMBLY.get())
            .define('G', Eln2Ingredients.IRON_GEAR.get())
            .define('B', Ingredient.of(Eln2ConventionTags.GLUE))
            .unlockedBy("has_machine_frame", has(Eln2Ingredients.MACHINE_FRAME.get()))
            .save(pWriter, resource("crafting/crusher_hull"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.EXTRUDING_PORT.get(), 2)
            .pattern("GSG")
            .pattern("GSG")
            .pattern("   ")
            .define('G', Eln2Ingredients.IRON_GEAR.get())
            .define('S', Eln2Ingredients.IRON_SHAFT.get())
            .unlockedBy("has_iron_gear", has(Eln2Ingredients.IRON_GEAR.get()))
            .save(pWriter, resource("crafting/extruding_port"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.EXTRUDING_ASSEMBLY.get())
            .pattern("EPE")
            .define('E', Eln2Ingredients.EXTRUDING_PORT.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_extruding_port", has(Eln2Ingredients.EXTRUDING_PORT.get()))
            .save(pWriter, resource("crafting/extruding_assembly"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.EXTRUDER_HULL.hullItem.get())
            .pattern("MAB")
            .pattern("   ")
            .pattern("   ")
            .define('M', Eln2Ingredients.MACHINE_FRAME.get())
            .define('A', Eln2Ingredients.EXTRUDING_ASSEMBLY.get())
            .define('B', Ingredient.of(Eln2ConventionTags.GLUE))
            .unlockedBy("has_machine_frame", has(Eln2Ingredients.MACHINE_FRAME.get()))
            .save(pWriter, resource("crafting/extruder_hull"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.ROLLING_ASSEMBLY.get())
            .pattern("E E")
            .define('E', Eln2Ingredients.EXTRUDING_PORT.get())
            .unlockedBy("has_extruding_port", has(Eln2Ingredients.EXTRUDING_PORT.get()))
            .save(pWriter, resource("crafting/rolling_assembly"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.ROLLING_MACHINE_HULL.hullItem.get())
            .pattern("MAB")
            .pattern("   ")
            .pattern("   ")
            .define('M', Eln2Ingredients.MACHINE_FRAME.get())
            .define('A', Eln2Ingredients.ROLLING_ASSEMBLY.get())
            .define('B', Ingredient.of(Eln2ConventionTags.GLUE))
            .unlockedBy("has_machine_frame", has(Eln2Ingredients.MACHINE_FRAME.get()))
            .save(pWriter, resource("crafting/rolling_machine_hull"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2HeatGenerators.PRIMITIVE_BURNER_BLOCK.get())
            .pattern("SCS")
            .pattern("S S")
            .pattern("SCS")
            .define('S', Items.STONE)
            .define('C', Eln2Ingredients.COPPER_PLATE.get())
            .unlockedBy("has_copper_plate", has(Eln2Ingredients.COPPER_PLATE.get()))
            .save(pWriter, resource("crafting/primitive_burner"))

        //#region Advanced Coal Burner

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.INSULATED_COMBUSTION_CHAMBER.get())
            .pattern("AIA")
            .pattern("I I")
            .pattern("AIA")
            .define('A', Eln2Ingredients.ASBESTOS_FIBER.get())
            .define('I', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_asbestos_fiber", has(Eln2Ingredients.ASBESTOS_FIBER.get()))
            .save(pWriter, resource("crafting/insulated_combustion_chamber"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.BURNER_CONTROLLER_UNIT.get())
            .pattern(" C ")
            .pattern("RWR")
            .pattern(" W ")
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('R', Eln2Ingredients.RESISTOR.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_primitive_circuit", has(Eln2Ingredients.PRIMITIVE_CIRCUIT.get()))
            .save(pWriter, resource("crafting/burner_controller_unit"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.BURNER_FUEL_HOPPER.get())
            .pattern("P P")
            .pattern("PGP")
            .pattern(" P ")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('G', Eln2Ingredients.IRON_GEAR.get())
            .unlockedBy("has_iron_gear", has(Eln2Ingredients.IRON_GEAR.get()))
            .save(pWriter, resource("crafting/burner_fuel_hopper"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2HeatGenerators.ADVANCED_COAL_BURNER_BLOCK_ITEM.get())
            .pattern("WCW")
            .pattern("HMU")
            .pattern("WCW")
            .define('C', Eln2Ingredients.INSULATED_COMBUSTION_CHAMBER.get())
            .define('H', Eln2Ingredients.BURNER_FUEL_HOPPER.get())
            .define('M', Eln2Ingredients.MACHINE_FRAME.get())
            .define('U', Eln2Ingredients.BURNER_CONTROLLER_UNIT.get())
            .define('W', Eln2Wires.STANDARD_UNINSULATED_COPPER_THERMAL_WIRE.part.item.get())
            .unlockedBy("has_insulated_combustion_chamber", has(Eln2Ingredients.INSULATED_COMBUSTION_CHAMBER.get()))
            .save(pWriter, resource("crafting/advanced_coal_burner"))

        //#endregion

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Processing.EXTRUDER_WIRE_DIE.get())
            .requires(Items.STONE_SLAB)
            .requires(Items.IRON_NUGGET)
            .unlockedBy("has_stone_slab", has(Items.STONE_SLAB))
            .save(pWriter, resource("crafting/extruder_wire_die"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Processing.EXTRUDER_ROD_DIE.get())
            .requires(Eln2Processing.EXTRUDER_WIRE_DIE.get())
            .requires(Items.IRON_NUGGET)
            .unlockedBy("has_extruder_wire_die", has(Eln2Processing.EXTRUDER_WIRE_DIE.get()))
            .save(pWriter, resource("crafting/extruder_rod_die"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Processing.EXTRUDER_SHAFT_DIE.get())
            .requires(Eln2Processing.EXTRUDER_ROD_DIE.get())
            .requires(Items.IRON_NUGGET)
            .unlockedBy("has_extruder_rod_die", has(Eln2Processing.EXTRUDER_ROD_DIE.get()))
            .save(pWriter, resource("crafting/extruder_shaft_die"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.TREE_TAP_PART.item.get())
            .pattern("S S")
            .pattern("SPS")
            .pattern("SSS")
            .define('S', Ingredient.of(Eln2ConventionTags.WOODEN_SLABS))
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_iron_plate", has(Eln2Ingredients.IRON_PLATE.get()))
            .save(pWriter, resource("crafting/tree_tap"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Ingredients.RUBBER_COMPOUND.get(), 4)
            .requires(Eln2Ingredients.RAW_LATEX.get())
            .requires(Eln2Ingredients.SULFUR_DUST.get())
            .unlockedBy("has_sulfur_dust", has(Eln2Ingredients.SULFUR_DUST.get()))
            .save(pWriter, resource("crafting/rubber_compound"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Ingredients.ASBESTOS_FIBER.get(), 4)
            .requires(Eln2Ores.ASBESTOS_ORE.rawOreItem.get())
            .unlockedBy("has_raw_asbestos", has(Eln2Ores.ASBESTOS_ORE.rawOreItem.get()))
            .save(pWriter, resource("crafting/asbestos_fiber_from_raw"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.LEAD_ACID_BATTERY_CELL.get())
            .pattern("W W")
            .pattern("PAP")
            .define('W', Eln2Ingredients.ENAMELED_COPPER_WIRE.get())
            .define('P', taggedIngredient(Eln2Ingredients.LEAD_PLATE.get()))
            .define('A', Eln2Ingredients.ASBESTOS_FIBER.get())
            .unlockedBy("has_lead_plate", has(Eln2ConventionTags.PLATE_LEAD))
            .save(pWriter, resource("crafting/lead_acid_battery_cell"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.RUBBER_WOOD_PANEL.get())
            .pattern(" R ")
            .pattern(" LG")
            .pattern(" R ")
            .define('R', Eln2Ingredients.RUBBER.get())
            .define('L', Ingredient.of(ItemTags.LOGS))
            .define('G', Ingredient.of(Eln2ConventionTags.GLUE))
            .unlockedBy("has_rubber", has(Eln2Ingredients.RUBBER.get()))
            .save(pWriter, resource("crafting/rubber_wood_panel"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.LEAD_ACID_BATTERY_CHASSIS.get())
            .pattern("P P")
            .pattern("P P")
            .pattern("PPP")
            .define('P', Eln2Ingredients.RUBBER_WOOD_PANEL.get())
            .unlockedBy("has_rubber_wood_panel", has(Eln2Ingredients.RUBBER_WOOD_PANEL.get()))
            .save(pWriter, resource("crafting/lead_acid_battery_chassis"))

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Ingredients.LEAD_ACID_BATTERY_BASE_12V.get())
            .requires(Eln2Ingredients.LEAD_ACID_BATTERY_CHASSIS.get())
            .requires(Eln2Ingredients.LEAD_ACID_BATTERY_CELL.get(), 6)
            .unlockedBy("has_chassis", has(Eln2Ingredients.LEAD_ACID_BATTERY_CHASSIS.get()))
            .save(pWriter, resource("crafting/lead_acid_battery_base_12v"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Batteries.BATTERY_PART_12V.item.get())
            .pattern("   ")
            .pattern("GRA")
            .pattern("PBP")
            .define('G', Ingredient.of(Eln2ConventionTags.GLUE))
            .define('R', Ingredient.of(Eln2Ingredients.RUBBER_WOOD_PANEL.get()))
            .define('A', Eln2ForgeFluids.DILUTE_SULFURIC_ACID.bucket.get())
            .define('P', taggedIngredient(Eln2Ingredients.COPPER_PLATE.get()))
            .define('B', Eln2Ingredients.LEAD_ACID_BATTERY_BASE_12V.get())
            .unlockedBy("has_battery_base", has(Eln2Ingredients.LEAD_ACID_BATTERY_BASE_12V.get()))
            .save(pWriter, resource("crafting/lead_acid_battery_12v"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2InventoryPower.LEAD_ACID_POWER_CELL.get())
            .pattern("PBP")
            .pattern("BAB")
            .pattern("PBP")
            .define('P', taggedIngredient(Eln2Ingredients.LEAD_PLATE.get()))
            .define('B', Eln2Ingredients.LEAD_ACID_BATTERY_CELL.get())
            .define('A', Eln2ForgeFluids.DILUTE_SULFURIC_ACID.bucket.get())
            .unlockedBy("has_lead_acid_battery_cell", has(Eln2Ingredients.LEAD_ACID_BATTERY_CELL.get()))
            .save(pWriter, resource("crafting/lead_acid_power_cell"))

        //#region Tungsten Filament

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2Ingredients.SCHEELITE_FUSION_MIX.get())
            .requires(Eln2Ingredients.SCHEELITE_DUST.get())
            .requires(Eln2Ingredients.SODIUM_CARBONATE_DUST.get())
            .unlockedBy("has_scheelite_dust", has(Eln2Ingredients.SCHEELITE_DUST.get()))
            .save(pWriter, resource("crafting/scheelite_fusion_mix"))

        SimpleCookingRecipeBuilder.smelting(
            Ingredient.of(Eln2Ingredients.SCHEELITE_FUSION_MIX.get()),
            RecipeCategory.MISC,
            Eln2Ingredients.SODIUM_TUNGSTENATE_MELT.get(),
            0.7f,
            200
        ).apply {
            unlockedBy("has_scheelite_fusion_mix", has(Eln2Ingredients.SCHEELITE_FUSION_MIX.get()))
            save(pWriter, resource("smelting/scheelite_fusion_mix_to_sodium_tungstenate_melt"))
        }

        SimpleCookingRecipeBuilder.blasting(
            Ingredient.of(Eln2Ingredients.SCHEELITE_FUSION_MIX.get()),
            RecipeCategory.MISC,
            Eln2Ingredients.SODIUM_TUNGSTENATE_MELT.get(),
            0.7f,
            100
        ).apply {
            unlockedBy("has_scheelite_fusion_mix", has(Eln2Ingredients.SCHEELITE_FUSION_MIX.get()))
            save(pWriter, resource("blasting/scheelite_fusion_mix_to_sodium_tungstenate_melt"))
        }

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Eln2ForgeFluids.SODIUM_TUNGSTATE_SOLUTION.bucket.get())
            .requires(Eln2Ingredients.SODIUM_TUNGSTENATE_MELT.get())
            .requires(Items.WATER_BUCKET)
            .unlockedBy("has_sodium_tungstenate_melt", has(Eln2Ingredients.SODIUM_TUNGSTENATE_MELT.get()))
            .save(pWriter, resource("crafting/sodium_tungstate_solution_bucket"))

        SimpleCookingRecipeBuilder.smelting(
            Ingredient.of(Eln2Ingredients.TUNGSTIC_ACID.get()),
            RecipeCategory.MISC,
            Eln2Ingredients.TUNGSTEN_TRIOXIDE_DUST.get(),
            0.7f,
            200
        ).apply {
            unlockedBy("has_tungstic_acid", has(Eln2Ingredients.TUNGSTIC_ACID.get()))
            save(pWriter, resource("smelting/tungstic_acid_to_tungsten_trioxide_dust"))
        }

        SimpleCookingRecipeBuilder.blasting(
            Ingredient.of(Eln2Ingredients.TUNGSTIC_ACID.get()),
            RecipeCategory.MISC,
            Eln2Ingredients.TUNGSTEN_TRIOXIDE_DUST.get(),
            0.7f,
            100
        ).apply {
            unlockedBy("has_tungstic_acid", has(Eln2Ingredients.TUNGSTIC_ACID.get()))
            save(pWriter, resource("blasting/tungstic_acid_to_tungsten_trioxide_dust"))
        }

        //#endregion

        //#region Vacuum Tubes

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.GLASS_ENVELOPE.get(), 1)
            .pattern("G G")
            .pattern(" G ")
            .define('G', Items::GLASS_PANE)
            .unlockedBy("has_glass_pane", has(Items.GLASS_PANE))
            .save(pWriter, resource("crafting/glass_envelope"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.UNSEALED_VACUUM_TUBE.get(), 1)
            .pattern("FWW")
            .pattern("PG ")
            .define('F', Eln2Ingredients.TUNGSTEN_FILAMENT.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('P', Eln2Ingredients.IRON_PLATE.get())
            .define('G', Eln2Ingredients.GLASS_ENVELOPE.get())
            .unlockedBy("has_tungsten_filament", has(Eln2Ingredients.TUNGSTEN_FILAMENT.get()))
            .save(pWriter, resource("crafting/unsealed_vacuum_tube"))

        DirectSimpleProcessingRecipeBuilder(Eln2Processing.VACUUM_SEALING_RECIPE)
            .withInput(Eln2Ingredients.UNSEALED_VACUUM_TUBE.get())
            .withOutput(Eln2Ingredients.VACUUM_TUBE.get())
            .withDuration(60.0)
            .unlockedBy("has_unsealed_tube", has(Eln2Ingredients.UNSEALED_VACUUM_TUBE.get()))
            .save(pWriter, resource("vacuum_sealing/vacuum_tube"))

        //#endregion

        //#region Light Bulb

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.UNSEALED_240V_100W_LIGHT_BULB.get(), 1)
            .pattern(" E ")
            .pattern(" F ")
            .pattern("NIN")
            .define('E', Eln2Ingredients.GLASS_ENVELOPE.get())
            .define('F', Eln2Ingredients.TUNGSTEN_FILAMENT.get())
            .define('N', Items.IRON_NUGGET)
            .define('I', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_tungsten_filament", has(Eln2Ingredients.TUNGSTEN_FILAMENT.get()))
            .save(pWriter, resource("crafting/unsealed_240v_100w_light_bulb"))

        DirectSimpleProcessingRecipeBuilder(Eln2Processing.VACUUM_SEALING_RECIPE)
            .withInput(Eln2Ingredients.UNSEALED_240V_100W_LIGHT_BULB.get())
            .withOutput(Eln2Lights.LIGHT_BULB_240V_100W.get())
            .withDuration(60.0)
            .unlockedBy("has_unsealed_bulb", has(Eln2Ingredients.UNSEALED_240V_100W_LIGHT_BULB.get()))
            .save(pWriter, resource("vacuum_sealing/light_bulb_240v_100w"))

        //#endregion

        //#region Light Fixtures

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Lights.LIGHT_PART.item.get(), 4)
            .pattern(" G ")
            .pattern("W W")
            .pattern(" P ")
            .define('G', Items.GLASS_PANE)
            .define('W', Eln2Wires.STANDARD_INSULATED_COPPER_ELECTRICAL_WIRE.part.item.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_insulated_wire", has(Eln2Wires.STANDARD_INSULATED_COPPER_ELECTRICAL_WIRE.part.item.get()))
            .save(pWriter, resource("crafting/small_wall_lamp"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Lights.LIGHT_PART_MICRO_GRID.item.get(), 4)
            .pattern(" G ")
            .pattern("W W")
            .pattern(" P ")
            .define('G', Items.GLASS_PANE)
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_copper_wire", has(Eln2Ingredients.COPPER_WIRE.get()))
            .save(pWriter, resource("crafting/small_wall_lamp_micro_grid"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Lights.LAMP_POLE_BLOCK_ITEM.get())
            .pattern(" G ")
            .pattern(" W ")
            .pattern("CWC")
            .define('G', Items.GLASS)
            .define('W', Ingredient.of(ItemTags.PLANKS))
            .define('C', Eln2Grid.POWER_GRID_CONNECT_COPPER.item.get())
            .unlockedBy("has_grid_cable", has(Eln2Grid.POWER_GRID_CONNECT_COPPER.item.get()))
            .save(pWriter, resource("crafting/lamp_pole"))

        //#endregion

        //#region Inductor

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.INDUCTOR.get(), 4)
            .pattern(" W ")
            .pattern(" I ")
            .pattern(" W ")
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('I', Items::IRON_NUGGET)
            .unlockedBy("has_copper_wire", has(Eln2Ingredients.COPPER_WIRE.get()))
            .save(pWriter, resource("crafting/inductor"))

        //#endregion

        //#region Resistor

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.UNFIRED_RESISTOR.get(), 4)
            .pattern(" C ")
            .pattern("WPW")
            .pattern(" C ")
            .define('C', Items::CLAY_BALL)
            .define('P', Eln2Ingredients.CARBON_PUTTY.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_carbon_putty", has(Eln2Ingredients.CARBON_PUTTY.get()))
            .save(pWriter, resource("crafting/unfired_resistor"))

        SimpleCookingRecipeBuilder.smelting(
            Ingredient.of(Eln2Ingredients.UNFIRED_RESISTOR.get()),
            RecipeCategory.MISC,
            Eln2Ingredients.RESISTOR.get(),
            0.3f,
            200
        ).apply {
            unlockedBy("has_unfired_resistor", has(Eln2Ingredients.UNFIRED_RESISTOR.get()))
            save(pWriter, resource("smelting/unfired_resistor_to_resistor"))
        }

        //#endregion

        //#region Capacitor

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.CAPACITOR_SEPARATOR.get(), 8)
            .pattern("PPP")
            .pattern("PBP")
            .pattern("PPP")
            .define('P', Items::PAPER)
            .define('B', Eln2ForgeFluids.PITCH.requireBottle().bottleItem.get())
            .unlockedBy("has_pitch_bottle", has(Eln2ForgeFluids.PITCH.requireBottle().bottleItem.get()))
            .save(pWriter, resource("crafting/capacitor_separator"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.CAPACITOR.get(), 4)
            .pattern("PP")
            .pattern("WS")
            .define('P', Eln2Ingredients.COPPER_PLATE.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('S', Eln2Ingredients.CAPACITOR_SEPARATOR.get())
            .unlockedBy("has_capacitor_separator", has(Eln2Ingredients.CAPACITOR_SEPARATOR.get()))
            .save(pWriter, resource("crafting/capacitor"))

        //#endregion

        //#region Circuit Board

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.RAW_CIRCUIT_BOARD.get(), 4)
            .pattern("R")
            .pattern("G")
            .pattern("P")
            .define('R', Eln2Ingredients.RUBBER_WOOD_PANEL.get())
            .define('G', Ingredient.of(Eln2ConventionTags.GLUE))
            .define('P', Eln2Ingredients.COPPER_PLATE.get())
            .unlockedBy("has_rubber_wood_panel", has(Eln2Ingredients.RUBBER_WOOD_PANEL.get()))
            .save(pWriter, resource("crafting/raw_circuit_board"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.PRIMITIVE_CIRCUIT.get(), 1)
            .pattern("TRC")
            .pattern("WIB")
            .define('T', Eln2Ingredients.VACUUM_TUBE.get())
            .define('R', Eln2Ingredients.RESISTOR.get())
            .define('C', Eln2Ingredients.CAPACITOR.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('I', Eln2Ingredients.INDUCTOR.get())
            .define('B', Eln2Ingredients.RAW_CIRCUIT_BOARD.get())
            .unlockedBy("has_vacuum_tube", has(Eln2Ingredients.VACUUM_TUBE.get()))
            .save(pWriter, resource("crafting/primitive_circuit"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2PowerDevices.PRIMITIVE_DC_TO_DC_CONVERTER_BLOCK.get())
            .pattern("ICI")
            .pattern("PMP")
            .pattern("ICI")
            .define('I', Eln2Ingredients.INDUCTOR.get())
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('M', Eln2Ingredients.MACHINE_FRAME.get())
            .unlockedBy("has_primitive_circuit", has(Eln2Ingredients.PRIMITIVE_CIRCUIT.get()))
            .save(pWriter, resource("crafting/primitive_dc_to_dc_converter"))
        //#endregion

        //#region Switch

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.SWITCH_PART.item.get(), 4)
            .pattern(" R ")
            .pattern(" P ")
            .pattern("WIW")
            .define('R', Eln2Ingredients.RUBBER.get())
            .define('P', Ingredient.of(ItemTags.PLANKS))
            .define('W', Eln2Wires.STANDARD_INSULATED_COPPER_ELECTRICAL_WIRE.part.item.get())
            .define('I', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_insulated_wire", has(Eln2Wires.STANDARD_INSULATED_COPPER_ELECTRICAL_WIRE.part.item.get()))
            .save(pWriter, resource("crafting/switch"))

        //#endregion

        //#region Asbestos Separator

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.ASBESTOS_SEPARATOR.get())
            .pattern("RAR")
            .pattern("AAA")
            .pattern("RAR")
            .define('A', Eln2Ingredients.ASBESTOS_FIBER.get())
            .define('R', Eln2Ingredients.RUBBER.get())
            .unlockedBy("has_asbestos_fiber", has(Eln2Ingredients.ASBESTOS_FIBER.get()))
            .save(pWriter, resource("crafting/asbestos_separator"))

        //#endregion

        //#region Fuses

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.TIN_FUSE_1A.get(), 4)
            .pattern(" C ")
            .pattern(" T ")
            .pattern(" C ")
            .define('C', Eln2Ingredients.COPPER_WIRE.get())
            .define('T', Eln2Ingredients.TIN_WIRE.get())
            .unlockedBy("has_tin_wire", has(Eln2Ingredients.TIN_WIRE.get()))
            .save(pWriter, resource("crafting/tin_fuse_1a"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.TIN_FUSE_5A.get(), 2)
            .pattern("CC ")
            .pattern("TT ")
            .pattern("CC ")
            .define('C', Eln2Ingredients.COPPER_WIRE.get())
            .define('T', Eln2Ingredients.TIN_WIRE.get())
            .unlockedBy("has_tin_wire", has(Eln2Ingredients.TIN_WIRE.get()))
            .save(pWriter, resource("crafting/tin_fuse_5a"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.LEAD_FUSE_10A.get(), 2)
            .pattern(" C ")
            .pattern(" L ")
            .pattern(" C ")
            .define('C', Eln2Ingredients.COPPER_WIRE.get())
            .define('L', Eln2Ingredients.LEAD_WIRE.get())
            .unlockedBy("has_lead_wire", has(Eln2Ingredients.LEAD_WIRE.get()))
            .save(pWriter, resource("crafting/lead_fuse_10a"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.LEAD_FUSE_25A.get())
            .pattern("CC ")
            .pattern("LL ")
            .pattern("CC ")
            .define('C', Eln2Ingredients.COPPER_WIRE.get())
            .define('L', Eln2Ingredients.LEAD_WIRE.get())
            .unlockedBy("has_lead_wire", has(Eln2Ingredients.LEAD_WIRE.get()))
            .save(pWriter, resource("crafting/lead_fuse_25a"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.BRONZE_FUSE_50A.get())
            .pattern(" C ")
            .pattern(" B ")
            .pattern(" C ")
            .define('C', Eln2Ingredients.COPPER_WIRE.get())
            .define('B', Eln2Ingredients.BRONZE_PLATE.get())
            .unlockedBy("has_bronze_plate", has(Eln2Ingredients.BRONZE_PLATE.get()))
            .save(pWriter, resource("crafting/bronze_fuse_50a"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.COPPER_FUSE_100A.get())
            .pattern(" C ")
            .pattern(" W ")
            .pattern(" C ")
            .define('C', Eln2Ingredients.COPPER_WIRE.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_copper_wire", has(Eln2Ingredients.COPPER_WIRE.get()))
            .save(pWriter, resource("crafting/copper_fuse_100a"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.COPPER_FUSE_150A.get())
            .pattern("CC ")
            .pattern("WW ")
            .pattern("CC ")
            .define('C', Eln2Ingredients.COPPER_WIRE.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_copper_wire", has(Eln2Ingredients.COPPER_WIRE.get()))
            .save(pWriter, resource("crafting/copper_fuse_150a"))

        //#endregion

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.FUSE_PANEL_PART.item.get())
            .pattern(" C ")
            .pattern(" P ")
            .pattern(" C ")
            .define('C', Eln2Ingredients.COPPER_WIRE.get())
            .define('P', Eln2Ingredients.RUBBER_WOOD_PANEL.get())
            .unlockedBy("has_rubber_wood_panel", has(Eln2Ingredients.RUBBER_WOOD_PANEL.get()))
            .save(pWriter, resource("crafting/fuse_panel"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.BURNER_BLOCK.item.get())
            .pattern("PIP")
            .pattern("BVB")
            .pattern("PPP")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('I', Items.FURNACE)
            .define('B', Items.BRICKS)
            .define('V', Eln2ForgeFluids.FLUID_PIPE_EXTRACTION_VALVE.get())
            .unlockedBy("has_fluid_extraction_valve", has(Eln2ForgeFluids.FLUID_PIPE_EXTRACTION_VALVE.get()))
            .save(pWriter, resource("crafting/burner_reactor"))

        //#region Probes

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.PROBE_BODY.get(), 4)
            .pattern("WR")
            .pattern("CP")
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('R', Items.REDSTONE)
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_primitive_circuit", has(Eln2Ingredients.PRIMITIVE_CIRCUIT.get()))
            .save(pWriter, resource("crafting/probe_body"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Signal.POTENTIAL_PROBE_PART.item.get())
            .pattern(" B ")
            .pattern(" R ")
            .pattern(" W ")
            .define('B', Eln2Ingredients.PROBE_BODY.get())
            .define('R', Eln2Ingredients.RESISTOR.get())
            .define('W', Eln2Wires.STANDARD_INSULATED_COPPER_ELECTRICAL_WIRE.part.item.get())
            .unlockedBy("has_probe_body", has(Eln2Ingredients.PROBE_BODY.get()))
            .save(pWriter, resource("crafting/potential_probe"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Signal.CURRENT_PROBE_PART.item.get())
            .pattern(" B ")
            .pattern(" C ")
            .pattern(" W ")
            .define('B', Eln2Ingredients.PROBE_BODY.get())
            .define('C', Eln2Ingredients.COPPER_WIRE.get())
            .define('W', Eln2Wires.STANDARD_INSULATED_COPPER_ELECTRICAL_WIRE.part.item.get())
            .unlockedBy("has_probe_body", has(Eln2Ingredients.PROBE_BODY.get()))
            .save(pWriter, resource("crafting/current_probe"))

        //#endregion

        //#region Signal Grid

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Grid.SIGNAL_GRID_CONNECT.item.get(), 8)
            .pattern(" W ")
            .pattern(" R ")
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('R', Items.REDSTONE)
            .unlockedBy("has_redstone", has(Items.REDSTONE))
            .save(pWriter, resource("crafting/signal_grid_cable"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Grid.SIGNAL_GRID_ANCHOR_SPEC.item.get(), 4)
            .pattern(" R ")
            .pattern(" R ")
            .define('R', Items.REDSTONE)
            .unlockedBy("has_redstone", has(Items.REDSTONE))
            .save(pWriter, resource("crafting/signal_grid_anchor"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Grid.SIGNAL_GRID_INTERFACE_PART.item.get(), 4)
            .pattern(" R ")
            .pattern(" C ")
            .define('R', Items.REDSTONE)
            .define('C', Eln2Grid.SIGNAL_GRID_CONNECT.item.get())
            .unlockedBy("has_signal_grid_cable", has(Eln2Grid.SIGNAL_GRID_CONNECT.item.get()))
            .save(pWriter, resource("crafting/signal_grid_interface"))

        //#endregion

        //#region Signal Components

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Signal.SIGNAL_REFERENCE_PART.item.get())
            .pattern(" W ")
            .pattern(" R ")
            .pattern(" C ")
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('R', Items.REDSTONE)
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .unlockedBy("has_primitive_circuit", has(Eln2Ingredients.PRIMITIVE_CIRCUIT.get()))
            .save(pWriter, resource("crafting/signal_reference"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Signal.SIGNAL_CLAMPER_PART.item.get())
            .pattern("RC")
            .pattern("RW")
            .define('R', Items.REDSTONE)
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_primitive_circuit", has(Eln2Ingredients.PRIMITIVE_CIRCUIT.get()))
            .save(pWriter, resource("crafting/signal_clamper"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Signal.SIGNAL_OPAMP_PART.item.get())
            .pattern("RC")
            .pattern("WC")
            .define('R', Items.REDSTONE)
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_primitive_circuit", has(Eln2Ingredients.PRIMITIVE_CIRCUIT.get()))
            .save(pWriter, resource("crafting/signal_opamp"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Signal.SIGNAL_PID_PART.item.get())
            .pattern("CC")
            .pattern("CW")
            .pattern("RR")
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('R', Items.REDSTONE)
            .unlockedBy("has_primitive_circuit", has(Eln2Ingredients.PRIMITIVE_CIRCUIT.get()))
            .save(pWriter, resource("crafting/signal_pid"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Signal.RELAY_PART.item.get())
            .pattern("WGW")
            .pattern("RCP")
            .define('W', Eln2Ingredients.COPPER_COIL.get())
            .define('G', Items.GOLD_NUGGET)
            .define('R', Items.REDSTONE)
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_primitive_circuit", has(Eln2Ingredients.PRIMITIVE_CIRCUIT.get()))
            .save(pWriter, resource("crafting/relay"))

        //#endregion

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.GROUND_PART.item.get())
            .pattern(" I ")
            .pattern(" W ")
            .pattern(" P ")
            .define('I', Items.IRON_INGOT)
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('P', Ingredient.of(ItemTags.PLANKS))
            .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
            .save(pWriter, resource("crafting/ground"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2BasicComponents.GROUND_SPEC.item.get())
            .pattern(" R ")
            .pattern(" N ")
            .pattern(" P ")
            .define('R', Eln2Ingredients.COPPER_ROD.get())
            .define('N', Items.IRON_NUGGET)
            .define('P', Ingredient.of(ItemTags.PLANKS))
            .unlockedBy("has_copper_rod", has(Eln2Ingredients.COPPER_ROD.get()))
            .save(pWriter, resource("crafting/ground_micro_grid"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2HeatingElements.LOW_VOLTAGE_IRON_HEATING_ELEMENT.get())
            .pattern("WW")
            .pattern("WW")
            .define('W', Eln2Ingredients.IRON_WIRE.get())
            .unlockedBy("has_iron_wire", has(Eln2Ingredients.IRON_WIRE.get()))
            .save(pWriter, resource("crafting/low_voltage_iron_heating_element"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.HYDROGEN_REDUCTION_FURNACE_BLOCK.item.get())
            .pattern("AAA")
            .pattern("WBW")
            .pattern("PPP")
            .define('A', Eln2Ingredients.ASBESTOS_FIBER.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('B', Items.BRICKS)
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_asbestos_fiber", has(Eln2Ingredients.ASBESTOS_FIBER.get()))
            .save(pWriter, resource("crafting/hydrogen_reduction_furnace"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.RAW_GRAPHITE_ELECTRODE.get())
            .pattern("CCC")
            .pattern(" W ")
            .define('C', Eln2Ingredients.CARBON_PUTTY.get())
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_carbon_putty", has(Eln2Ingredients.CARBON_PUTTY.get()))
            .save(pWriter, resource("crafting/raw_graphite_electrode"))

        SimpleCookingRecipeBuilder.smelting(
            Ingredient.of(Eln2Processing.RAW_GRAPHITE_ELECTRODE.get()),
            RecipeCategory.MISC,
            Eln2Processing.GRAPHITE_ELECTRODE.get(),
            0.5f,
            200
        ).apply {
            unlockedBy("has_raw_graphite_electrode", has(Eln2Processing.RAW_GRAPHITE_ELECTRODE.get()))
            save(pWriter, resource("smelting/raw_graphite_electrode_to_graphite_electrode"))
        }

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.ELECTROLYSIS_BLOCK_ITEM.get())
            .pattern("WPW")
            .pattern("FRF")
            .pattern("PPP")
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('F', Eln2Ingredients.MACHINE_FRAME.get())
            .define('R', Eln2Ingredients.RUBBER.get())
            .unlockedBy("has_machine_frame", has(Eln2Ingredients.MACHINE_FRAME.get()))
            .save(pWriter, resource("crafting/electrolysis"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.LEAD_CHAMBER_BLOCK.item.get(), 2)
            .pattern("P P")
            .pattern("   ")
            .pattern("P P")
            .define('P', taggedIngredient(Eln2Ingredients.LEAD_PLATE.get()))
            .unlockedBy("has_lead_plate", has(Eln2ConventionTags.PLATE_LEAD))
            .save(pWriter, resource("crafting/lead_chamber"))

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, Eln2Tools.DRILL.get())
            .pattern("DII")
            .pattern("MCP")
            .define('D', Items.DIAMOND)
            .define('I', Items.IRON_INGOT)
            .define('M', Eln2Ingredients.BRUSHED_DC_MOTOR.get())
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('P', Eln2Grid.POWER_GRID_CONNECT_COPPER.item.get())
            .unlockedBy("has_brushed_dc_motor", has(Eln2Ingredients.BRUSHED_DC_MOTOR.get()))
            .save(pWriter, resource("crafting/drill"))

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, Eln2Tools.FLASHLIGHT.get())
            .pattern("GLG")
            .pattern("ICP")
            .pattern(" R ")
            .define('G', Items.GLASS_PANE)
            .define('L', Eln2Lights.LIGHT_BULB_240V_100W.get())
            .define('I', Items.IRON_INGOT)
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('P', Eln2Grid.POWER_GRID_CONNECT_COPPER.item.get())
            .define('R', Eln2Ingredients.RUBBER.get())
            .unlockedBy("has_light_bulb", has(Eln2Lights.LIGHT_BULB_240V_100W.get()))
            .save(pWriter, resource("crafting/flashlight"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2InventoryPower.POWER_CELL_CHARGER_BLOCK.item.get())
            .pattern("WPW")
            .pattern("MHC")
            .pattern("PPP")
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('M', Eln2Ingredients.MACHINE_FRAME.get())
            .define('H', Items.CHEST)
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .unlockedBy("has_machine_frame", has(Eln2Ingredients.MACHINE_FRAME.get()))
            .save(pWriter, resource("crafting/power_cell_charger"))

        //#region Steam Turbine

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.TURBINE_ROTOR.get())
            .pattern(" B ")
            .pattern("BSB")
            .pattern(" B ")
            .define('B', taggedIngredient(Eln2Ingredients.BRONZE_PLATE.get()))
            .define('S', Eln2Ingredients.IRON_SHAFT.get())
            .unlockedBy("has_bronze_plate", has(Eln2Ingredients.BRONZE_PLATE.get()))
            .save(pWriter, resource("crafting/turbine_rotor"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.STEAM_INJECTOR.get())
            .pattern(" P ")
            .pattern("THT")
            .pattern(" P ")
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('T', Eln2ForgeFluids.IRON_TANK.blockAndItem.item.get())
            .define('H', Eln2Ingredients.PIPE_HEAT_EXCHANGE_ASSEMBLY.get())
            .unlockedBy("has_pipe_heat_exchange_assembly", has(Eln2Ingredients.PIPE_HEAT_EXCHANGE_ASSEMBLY.get()))
            .save(pWriter, resource("crafting/steam_injector"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.CONDENSER_UNIT.get())
            .pattern("PPP")
            .pattern("CDC")
            .pattern("PPP")
            .define('P', Eln2ForgeFluids.FLUID_PIPE_BLOCK.item.get())
            .define('D', Eln2Processing.CONDENSER_DISTILLATION_MODULE_BLOCK.item.get())
            .define('C', taggedIngredient(Eln2Ingredients.COPPER_PLATE.get()))
            .unlockedBy("has_condenser_distillation_module", has(Eln2Processing.CONDENSER_DISTILLATION_MODULE_BLOCK.item.get()))
            .save(pWriter, resource("crafting/condenser_unit"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.BEARING_ASSEMBLY.get())
            .pattern("I I")
            .pattern("S S")
            .pattern("A A")
            .define('I', Items.IRON_BLOCK)
            .define('S', Eln2Ingredients.IRON_SHAFT.get())
            .define('A', Eln2Ingredients.IRON_AXLE_MOUNT.get())
            .unlockedBy("has_iron_axle_mount", has(Eln2Ingredients.IRON_AXLE_MOUNT.get()))
            .save(pWriter, resource("crafting/bearing_assembly"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.TURBINE_CASING.get())
            .pattern("III")
            .pattern("IMI")
            .pattern("IAI")
            .define('I', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .define('M', Eln2Ingredients.MACHINE_FRAME.get())
            .define('A', Eln2Ingredients.ASBESTOS_FIBER.get())
            .unlockedBy("has_machine_frame", has(Eln2Ingredients.MACHINE_FRAME.get()))
            .save(pWriter, resource("crafting/turbine_casing"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Ingredients.TURBINE_CONTROLLER.get())
            .pattern("CC ")
            .pattern("RG ")
            .pattern("WW ")
            .define('C', Eln2Ingredients.PRIMITIVE_CIRCUIT.get())
            .define('R', Items.REDSTONE)
            .define('G', Items.GOLD_NUGGET)
            .define('W', Eln2Ingredients.COPPER_WIRE.get())
            .unlockedBy("has_primitive_circuit", has(Eln2Ingredients.PRIMITIVE_CIRCUIT.get()))
            .save(pWriter, resource("crafting/turbine_controller"))

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2SteamTurbine.STEAM_TURBINE_BLOCK_ITEM.get())
            .pattern("BSB")
            .pattern("CRC")
            .pattern("UXU")
            .define('B', Eln2Ingredients.BEARING_ASSEMBLY.get())
            .define('S', Eln2Ingredients.STEAM_INJECTOR.get())
            .define('R', Eln2Ingredients.TURBINE_ROTOR.get())
            .define('C', Eln2Ingredients.TURBINE_CASING.get())
            .define('U', Eln2Ingredients.CONDENSER_UNIT.get())
            .define('X', Eln2Ingredients.TURBINE_CONTROLLER.get())
            .unlockedBy("has_turbine_rotor", has(Eln2Ingredients.TURBINE_ROTOR.get()))
            .save(pWriter, resource("crafting/steam_turbine"))

        //#endregion

        //#region Alloying Smelter

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Eln2Processing.ALLOYING_SMELTER_BLOCK.item.get())
            .pattern("BAB")
            .pattern("BFB")
            .pattern("BPB")
            .define('B', Items.BRICKS)
            .define('A', Eln2Ingredients.ASBESTOS_FIBER.get())
            .define('F', Items.FURNACE)
            .define('P', taggedIngredient(Eln2Ingredients.IRON_PLATE.get()))
            .unlockedBy("has_bricks", has(Items.BRICKS))
            .save(pWriter, resource("crafting/alloying_smelter"))

        //#endregion

        LOG.info("Generated manual recipes.")
    }

    override fun buildRecipes(pWriter: Consumer<FinishedRecipe?>) {
        Eln2Ores.ORE_SMELTING_FOR_DATAGEN.forEach { (obj, resultSupplier) ->
            val rawOreItem = obj.rawOreItem.get()
            val blockItem = obj.oreBlockItem.get()
            val result = resultSupplier.get()

            /**
             * Smelts the raw ore item.
             * */
            SimpleCookingRecipeBuilder.smelting(
                Ingredient.of(rawOreItem),
                RecipeCategory.MISC,
                result,
                0.7f,
                200
            ).apply {
                unlockedBy("has_${result.itemID.path}", has(rawOreItem))
                save(pWriter, resource("smelting/${rawOreItem.itemID.path}_to_${result.itemID.path}_smelting"))
            }

            /**
             * Smelts the block item.
             * */
            SimpleCookingRecipeBuilder.smelting(
                Ingredient.of(blockItem),
                RecipeCategory.MISC,
                result,
                0.7f,
                200
            ).apply {
                unlockedBy("has_${blockItem.itemID.path}", has(blockItem))
                save(pWriter, resource("smelting/${blockItem.itemID.path}_to_${result.itemID.path}_smelting"))
            }

            /**
             * Blasts the raw ore item.
             * */
            SimpleCookingRecipeBuilder.blasting(
                Ingredient.of(rawOreItem),
                RecipeCategory.MISC,
                result,
                0.7f,
                100
            ).apply {
                unlockedBy("has_${result.itemID.path}", has(rawOreItem))
                save(pWriter, resource("blasting/${rawOreItem.itemID.path}_to_${result.itemID.path}_blasting"))
            }

            LOG.info(
                "Registered smelting recipes for {}, {} -> {}",
                rawOreItem.itemID,
                blockItem.itemID,
                result.itemID
            )
        }

        Eln2Processing.PROCESSING_MACHINES_FOR_VISUAL_REGISTRATION_AND_DATAGEN.forEach { obj ->
            val blockItem = obj.blockAndItem.item.get()
            val hull = obj.hullItem.get()
            val boxItem = obj.box.item.get()

            /**
             * Assembly recipe:
             * */
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, blockItem)
                .requires(hull)
                .requires(boxItem)
                .unlockedBy("has_hull", has(hull))
                .unlockedBy("has_work_box", has(boxItem))
                .save(pWriter, resource("crafting/processing_machine/${blockItem.itemID.path}_assembly"))

            /**
             * Disassembly recipe. The hull is left behind thanks to the registered craft remainder:
             * */
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, hull)
                .requires(blockItem)
                .unlockedBy("has_machine", has(blockItem))
                .save(pWriter, resource("crafting/processing_machine/${blockItem.itemID.path}_disassembly"))

            LOG.info("Registered assembly and disassembly recipes for: {}", blockItem.itemID)
        }

        sequentialForEach(Eln2Ingredients.HOT_INGOTS.itemsForRecipeDatagen, Eln2Ingredients.HOT_PLATES.itemsForRecipeDatagen) { obj ->
            val sourceItem = obj.sourceItemForVanillaHeating?.get() ?: return@sequentialForEach
            val hotItem = obj.info.get()

            /**
             * Smelts into hot ingots:
             * */
            SimpleCookingRecipeBuilder.smelting(
                taggedIngredient(sourceItem),
                RecipeCategory.MISC,
                hotItem,
                0.5f,
                200
            ).apply {
                unlockedBy("has_item", has(sourceItem))
                save(pWriter, resource("smelting/${sourceItem.itemID.path}_to_${hotItem.itemID.path}_smelting"))
            }

            /**
             * Blasts into hot ingots:
             * */
            SimpleCookingRecipeBuilder.blasting(
                taggedIngredient(sourceItem),
                RecipeCategory.MISC,
                hotItem,
                0.5f,
                100
            ).apply {
                unlockedBy("has_item", has(sourceItem))
                save(pWriter, resource("blasting/${sourceItem.itemID.path}_to_${hotItem.itemID.path}_blasting"))
            }

            LOG.info("Added smelting and blasting to heat {} into {}", sourceItem.itemID, hotItem.itemID)
        }

        Eln2Ingredients.PLATES.itemsForRecipeDatagen.forEach { obj ->
            val plateItem = obj.info.get()
            val rollingItem = obj.sourceItemForRolling?.get()

            if(rollingItem != null) {
                DirectSimpleProcessingRecipeBuilder(Eln2Processing.ROLLING_RECIPE)
                    .withInput(taggedIngredient(rollingItem))
                    .withOutput(plateItem, 3)
                    .withDuration(obj.rollingDuration)
                    .unlockedBy("has_item_to_roll", has(rollingItem))
                    .save(pWriter, resource("rolling/${rollingItem.itemID.path}_to_${plateItem.itemID.path}"))

                LOG.info("Added plate rolling recipe from {} to {}", rollingItem.itemID, plateItem.itemID)
            }
        }

        Eln2Ingredients.WIRES.itemsForRecipeDatagen.forEach { obj ->
            val wireItem = obj.info.get()
            val itemForExtruding = obj.sourceItemForExtruding?.get()

            if(itemForExtruding != null) {
                CatalyzedSimpleProcessingRecipeBuilder(Eln2Processing.EXTRUDING_RECIPE)
                    .withInput(taggedIngredient(itemForExtruding))
                    .withCatalyst(Eln2Processing.EXTRUDER_WIRE_DIE.get())
                    .withOutput(wireItem)
                    .withDuration(obj.extrudingDuration)
                    .unlockedBy("has_item_for_wire_extrusion", has(itemForExtruding))
                    .save(pWriter, resource("extruding/${itemForExtruding.itemID.path}_to_${wireItem.itemID.path}"))

                LOG.info("Added wire extruding recipe from {} to {}", itemForExtruding.itemID, wireItem.itemID)
            }
        }

        Eln2Ingredients.DUSTS.itemsForRecipeDatagen.forEach { obj ->
            val dustItem = obj.info.get()

            obj.sourceItemsForCrushing.forEach { crushingInfo ->
                val crushingSource = crushingInfo.sourceItem.get()

                DirectSimpleProcessingRecipeBuilder(Eln2Processing.CRUSHING_RECIPE)
                    .withInput(taggedIngredient(crushingSource))
                    .withOutput(dustItem)
                    .withDuration(crushingInfo.duration)
                    .withTier(crushingInfo.tier)
                    .unlockedBy("has_item_to_crush", has(crushingSource))
                    .save(pWriter, resource("crushing/${crushingInfo.sourceItem.get().itemID.path}_to_${dustItem.itemID.path}"))

                LOG.info("Added dust crushing recipe from {} to {}", crushingInfo.sourceItem.get().itemID, dustItem.itemID)
            }
        }

        AlloyingRecipe.Builder(Eln2Processing.ALLOYING_RECIPE)
            .withInput(Eln2WeightedItemIngredient(Ingredient.of(Eln2ConventionTags.INGOT_COPPER), 3))
            .withInput(Eln2WeightedItemIngredient(Ingredient.of(Eln2ConventionTags.INGOT_TIN), 1))
            .withOutput(ItemStack(Eln2Ingredients.BRONZE_INGOT.get(), 4))
            .withDuration(600)
            .unlockedBy("has_copper", has(Eln2ConventionTags.INGOT_COPPER))
            .unlockedBy("has_tin", has(Eln2ConventionTags.INGOT_TIN))
            .save(pWriter, resource("alloying/copper_tin_to_bronze"))

        BurningRecipe.Builder(Eln2Processing.BURNING_RECIPE)
            .withInput(Eln2WeightedItemIngredient(Ingredient.of(Eln2ConventionTags.DUST_SULFUR), 1))
            .withOutputFluid(FluidStack(Eln2ForgeFluids.SULFUR_DIOXIDE.get(), 250))
            .withTemperatureRange(648.0, 698.0)
            .withDuration(1800)
            .unlockedBy("has_sulfur_dust", has(Eln2Ingredients.SULFUR_DUST.get()))
            .save(pWriter, resource("burning/sulfur_dust_to_sulfur_dioxide"))

        BurningRecipe.Builder(Eln2Processing.BURNING_RECIPE)
            .withInput(Eln2WeightedItemIngredient(Ingredient.of(Eln2ConventionTags.COAL_EQUIVALENT)))
            .withInput(Eln2WeightedItemIngredient(Ingredient.of(Eln2Ingredients.POTASSIUM_NITRATE_DUST.get())))
            .withInput(Eln2WeightedItemIngredient(taggedIngredient(Items.SAND)))
            .withOutputFluid(FluidStack(Eln2ForgeFluids.NITROGEN_DIOXIDE.get(), 125))
            .withTemperatureRange(710.01, 723.51)
            .withDuration(1800)
            .unlockedBy("has_potassium_nitrate_dust", has(Eln2Ingredients.POTASSIUM_NITRATE_DUST.get()))
            .save(pWriter, resource("burning/potassium_nitrate_to_nitrogen_dioxide"))

        //#region Tungsten

        CatalyzedSimpleProcessingRecipeBuilder(Eln2Processing.EXTRUDING_RECIPE)
            .withInput(Eln2Ingredients.TUNGSTEN_TRIOXIDE_PASTE.get())
            .withCatalyst(Eln2Processing.EXTRUDER_WIRE_DIE.get())
            .withOutput(Eln2Ingredients.FILAMENT_SHAPED_TUNGSTEN_TRIOXIDE_PASTE.get())
            .withDuration(15.0)
            .unlockedBy("has_tungsten_trioxide_paste", has(Eln2Ingredients.TUNGSTEN_TRIOXIDE_PASTE.get()))
            .save(pWriter, resource("extruding/tungsten_trioxide_paste_to_filament"))

        HydrogenReductionRecipe.Builder(Eln2Processing.HYDROGEN_REDUCTION_RECIPE)
            .withInput(Eln2WeightedItemIngredient(Ingredient.of(Eln2Ingredients.FILAMENT_SHAPED_TUNGSTEN_TRIOXIDE_PASTE.get())))
            .withOutput(ItemStack(Eln2Ingredients.TUNGSTEN_FILAMENT.get()))
            .withHydrogenAmount(5000)
            .withMinimumTemperature(Quantity(750.0, CELSIUS))
            .withOptimalTemperature(Quantity(900.0, CELSIUS))
            .withEnergyCost(10.0)
            .unlockedBy("has_filament_shaped_paste", has(Eln2Ingredients.FILAMENT_SHAPED_TUNGSTEN_TRIOXIDE_PASTE.get()))
            .save(pWriter, resource("hydrogen_reduction/filament_paste_to_tungsten_filament"))

        //#endregion

        //#region Electrolysis

        NonSeparatedAqueousElectrolysisRecipe.Builder(Eln2Processing.ELECTROLYSIS_RECIPE).apply {
            withAnodeElectrode(Eln2Processing.GRAPHITE_ELECTRODE.get())
            withCathodeElectrode(Eln2Processing.GRAPHITE_ELECTRODE.get())
            withInputFluid(FluidStack(Fluids.WATER, 1))
            withOutputGas(FluidStack(Eln2ForgeFluids.HHO_GAS.get(), 100))
            withEnergyCost(10000.0)
            withResistance(0.3)
            unlockedBy("has_graphite_electrode", has(Eln2Processing.GRAPHITE_ELECTRODE.get()))
            save(pWriter, resource("electrolysis/hho_from_water"))
        }

        SeparatedAqueousElectrolysisRecipe.Builder(Eln2Processing.ELECTROLYSIS_RECIPE).apply {
            withAnodeElectrode(Eln2Processing.GRAPHITE_ELECTRODE.get())
            withCathodeElectrode(Eln2Processing.GRAPHITE_ELECTRODE.get())
            withAnodeInputFluid(FluidStack(Fluids.WATER, 1))
            withCathodeInputFluid(FluidStack(Fluids.WATER, 1))
            withAnodeOutputGas(FluidStack(Eln2ForgeFluids.OXYGEN.get(), 100))
            withCathodeOutputGas(FluidStack(Eln2ForgeFluids.HYDROGEN.get(), 200))
            withSeparator(Eln2Processing.ASBESTOS_SEPARATOR.get())
            withEnergyCost(10000.0)
            withResistance(0.3)
            unlockedBy("has_graphite_electrode", has(Eln2Processing.GRAPHITE_ELECTRODE.get()))
            unlockedBy("has_asbestos_separator", has(Eln2Processing.ASBESTOS_SEPARATOR.get()))
            save(pWriter, resource("electrolysis/separated_water"))
        }

        SeparatedAqueousElectrolysisRecipe.Builder(Eln2Processing.ELECTROLYSIS_RECIPE).apply {
            withAnodeElectrode(Eln2Processing.GRAPHITE_ELECTRODE.get())
            withCathodeElectrode(Eln2Processing.GRAPHITE_ELECTRODE.get())
            withAnodeInputFluid(FluidStack(Eln2ForgeFluids.SODIUM_TUNGSTATE_SOLUTION.get(), 1))
            withCathodeInputFluid(FluidStack(Fluids.WATER, 1))
            withAnodeOutputItem(Eln2Ingredients.TUNGSTIC_ACID.get())
            withCathodeOutputFluid(FluidStack(Eln2ForgeFluids.SODIUM_HYDROXIDE_SOLUTION.get(), 1))
            withCathodeOutputGas(FluidStack(Eln2ForgeFluids.HYDROGEN.get(), 200))
            withSeparator(Eln2Processing.ASBESTOS_SEPARATOR.get())
            withEnergyCost(12000.0)
            withResistance(0.4)
            unlockedBy("has_sodium_tungstate_solution", has(Eln2ForgeFluids.SODIUM_TUNGSTATE_SOLUTION.bucket.get()))
            unlockedBy("has_graphite_electrode", has(Eln2Processing.GRAPHITE_ELECTRODE.get()))
            unlockedBy("has_asbestos_separator", has(Eln2Processing.ASBESTOS_SEPARATOR.get()))
            save(pWriter, resource("electrolysis/sodium_tungstate_to_tungstic_acid"))
        }

        //#endregion

        buildManualRecipes(pWriter)
    }
}

/**
 * - Generates the raw ore item model and the block item model from [Eln2Ores.ORE_FOR_MODEL_DATAGEN], which is built by [Eln2Ores.withModelDatagen] which registers tints on indices we use in the models.
 * - Generates bucket models, from [ForgeFluidRegistry.FORGE_FLUID_BUCKETS].
 * - Generates chemical bottle models, from [Eln2ForgeFluids.CHEMICAL_BOTTLES_FOR_RESOLVE_AND_DATAGEN].
 * - Generates models for the machine hulls and final machines, from [Eln2Processing.PROCESSING_MACHINES_FOR_VISUAL_REGISTRATION_AND_DATAGEN].
 * - Generates models for all the repositories in [Eln2Ingredients].
 * */
class Eln2ItemModelProviderDatagen(output: PackOutput, existingFileHelper: ExistingFileHelper) : ItemModelProvider(output, MODID, existingFileHelper) {
    override fun registerModels() {
        Eln2Ores.ORE_FOR_MODEL_DATAGEN.forEach { obj ->
            val rawOreItem = obj.rawOreItem.get()
            val oreBlock = obj.oreBlock.get()

            /**
             * For the block item, we parent the block's model:
             * */
            withExistingParent(oreBlock.blockID.toString(), modLoc("block/${oreBlock.blockID.path}"))

            /**
             * For the raw ore, we create a model with 2 layers, like the block model:
             * */
            singleTexture(
                rawOreItem.itemID.path,
                mcLoc("item/generated"),
                "layer0",
                modLoc("item/raw_ore_overlay")
            )

            LOG.info("Registered ore item models for {}", oreBlock.blockID)
        }

        ForgeFluidRegistry.FORGE_FLUID_BUCKETS.entries.forEach { bucketEntry ->
            val item = bucketEntry.get() as BucketItem
            val id = bucketEntry.id

            withExistingParent(id.path, ResourceLocation.parse("forge:item/bucket"))
                .customLoader { parent, helper ->
                    DynamicFluidContainerModelBuilder.begin(parent, helper)
                }
                .fluid(item.fluid)
                .coverIsMask(false)
                .end()

            LOG.info("Registered bucket model for {}", id)
        }

        Eln2ForgeFluids.CHEMICAL_BOTTLES_FOR_RESOLVE_AND_DATAGEN.forEach { (eln2Fluid, registeredBottle) ->
            val bottle = registeredBottle.bottleItem.get()

            getBuilder(bottle.itemID.path)
                .parent(getExistingFile(mcLoc("item/generated")))
                .texture("layer0", mcLoc("item/glass_bottle"))
                .texture("layer1", modLoc("item/bottle_fluid_overlay"))

            LOG.info("Registered bottle item {} for ELN2 fluid {}", bottle.itemID, eln2Fluid.id)
        }

        Eln2Processing.PROCESSING_MACHINES_FOR_VISUAL_REGISTRATION_AND_DATAGEN.forEach { obj ->
            val hullItem = obj.hullItem.get()
            val machineBlockItem = obj.blockAndItem.item.get()

            val model = obj.modelSupplier.get()
            val bodyLocation = model.body.modelLocation()

            withExistingParent(hullItem.itemID.path, bodyLocation)

            /**
             * Registers the finished machine's model as simply the hull model.
             * We might be able to refine this in the future.
             * It might be possible to take the box's item model (which would be a generated model, from a sprite) and layer it on top of the model used here.
             * */
            withExistingParent(machineBlockItem.itemID.path, bodyLocation)

            LOG.info("Registered processing machine models for {} and {}", hullItem.itemID, machineBlockItem.itemID)
        }

        /**
         * Does some tricks so the item model glows, ignoring light, for hot items.
         * We need to write this data in the model file; parenting doesn't work.
         * That's the first thing I tried, and it has some undefined behavior which makes all children use the texture of the (I think) first child that is loaded with that parent.
         * */
        fun ItemModelBuilder.loadGlowingTemplate(texture: ResourceLocation): ItemModelBuilder {
            this.texture("layer0", texture)
            this.guiLight(GuiLight.FRONT)

            this.customLoader { a, b -> ItemLayerModelBuilder.begin(a, b) }
                .emissive(15, 15, 0)
                .end()

            /**
             * Source: `minecraft:item/generated`
             * */
            this.transforms()
                .transform(ItemDisplayContext.GROUND)
                    .rotation(0f, 0f, 0f)
                    .translation(0f, 2f, 0f)
                    .scale(0.5f, 0.5f, 0.5f)
                    .end()
                .transform(ItemDisplayContext.HEAD)
                    .rotation(0f, 180f, 0f)
                    .translation(0f, 13f, 7f)
                    .scale(1f, 1f, 1f)
                    .end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(0f, 0f, 0f)
                    .translation(0f, 3f, 1f)
                    .scale(0.55f, 0.55f, 0.55f)
                    .end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0f, -90f, 25f)
                    .translation(1.13f, 3.2f, 1.13f)
                    .scale(0.68f, 0.68f, 0.68f)
                    .end()
                .transform(ItemDisplayContext.FIXED)
                    .rotation(0f, 180f, 0f)
                    .scale(1f, 1f, 1f)
                    .end()
                .end()

            return this
        }

        /**
         * Generates item models that simply tint a base texture.
         * */
        fun fromBase(source: Iterable<Supplier<Item>>, baseTexture: String, name: String, glowing: Boolean = false) {
            source.forEach { obj ->
                val item = obj.get()

                val baseTextureResource = modLoc("item/$baseTexture")

                if(glowing) {
                    getBuilder(item.itemID.path)
                        .loadGlowingTemplate(baseTextureResource)
                }
                else {
                    getBuilder(item.itemID.path)
                        .parent(getExistingFile(mcLoc("item/generated")))
                        .texture("layer0", baseTextureResource)
                }

                LOG.info("Registered {} model for {}", name, item.itemID)
            }
        }

        fromBase(Eln2Ingredients.INGOTS.itemsForModelDatagen, "ingot_base", "ingot")
        fromBase(Eln2Ingredients.HOT_INGOTS.itemsForModelDatagen, "hot_ingot_base", "hot ingot", true)
        fromBase(Eln2Ingredients.PLATES.itemsForModelDatagen, "plate_base", "plate")
        fromBase(Eln2Ingredients.HOT_PLATES.itemsForModelDatagen, "hot_plate_base", "hot plate", true)
        fromBase(Eln2Ingredients.WIRES.itemsForModelDatagen, "wire_base", "wire")
        fromBase(Eln2Ingredients.DUSTS.itemsForModelDatagen, "dust_base", "dust")
    }
}
