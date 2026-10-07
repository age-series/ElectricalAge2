@file:Suppress("unused", "UNCHECKED_CAST")

package org.eln2.mc.integration

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.registration.IRecipeCatalystRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.level.ItemLike
import net.minecraftforge.fluids.FluidType
import org.ageseries.libage.data.Temperature
import org.ageseries.libage.data.classify
import org.ageseries.libage.data.classifyAuxiliary
import org.eln2.mc.Eln2Config
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.content.processing.*
import org.eln2.mc.common.recipes.foundation.CatalyzedSimpleProcessingRecipe
import org.eln2.mc.common.recipes.foundation.DirectSimpleProcessingRecipe
import org.eln2.mc.resource
import net.minecraft.world.level.material.Fluid
import org.eln2.mc.common.content.modules.Eln2ForgeFluids
import org.eln2.mc.common.fluids.foundation.FluidTransformation
import org.eln2.mc.common.fluids.foundation.FluidTransformationManager
import org.eln2.mc.common.fluids.foundation.PhysicalFluid
import org.eln2.mc.common.fluids.foundation.PhysicalFluidManager
import java.util.function.Supplier
import kotlin.math.ceil

/**
 * Each processing machine recipe type gets one category
 * Categories are built in [registerCategories], then recipes are fetched from the [RecipeManager] and assigned in [registerRecipes].
 * */
@JeiPlugin
class Eln2Jei : IModPlugin {
    private lateinit var crushingCategory: CrushingCategory
    private lateinit var rollingCategory: RollingCategory
    private lateinit var extrudingCategory: ExtrudingCategory
    private lateinit var cokingCategory: CokingCategory
    private lateinit var alloyingCategory: AlloyingCategory
    private lateinit var vulcanizingCategory: VulcanizingCategory
    private lateinit var burningCategory: BurningCategory
    private lateinit var hydrogenReductionCategory: HydrogenReductionCategory
    private lateinit var electrolysisCategory: ElectrolysisCategory
    private lateinit var vacuumSealingCategory: VacuumSealingCategory
    private lateinit var phaseChangeCategory: PhaseChangeCategory
    private lateinit var treeExtractionCategory: TreeExtractionCategory
    private lateinit var leadChamberCategory: LeadChamberCategory

    override fun getPluginUid(): ResourceLocation = resource("jei_plugin")

    /**
     * Builds all six categories. The `guiHelper` provides JEI drawable factories and is passed to each category constructor for slot/background creation.
     * */
    override fun registerCategories(registration: IRecipeCategoryRegistration) {
        crushingCategory = CrushingCategory(registration.jeiHelpers.guiHelper)
        rollingCategory = RollingCategory(registration.jeiHelpers.guiHelper)
        extrudingCategory = ExtrudingCategory(registration.jeiHelpers.guiHelper)
        cokingCategory = CokingCategory(registration.jeiHelpers.guiHelper)
        alloyingCategory = AlloyingCategory(registration.jeiHelpers.guiHelper)
        vulcanizingCategory = VulcanizingCategory(registration.jeiHelpers.guiHelper)
        burningCategory = BurningCategory(registration.jeiHelpers.guiHelper)
        hydrogenReductionCategory = HydrogenReductionCategory(registration.jeiHelpers.guiHelper)
        vacuumSealingCategory = VacuumSealingCategory(registration.jeiHelpers.guiHelper)
        electrolysisCategory = ElectrolysisCategory(registration.jeiHelpers.guiHelper)
        phaseChangeCategory = PhaseChangeCategory()
        treeExtractionCategory = TreeExtractionCategory(registration.jeiHelpers.guiHelper)
        leadChamberCategory = LeadChamberCategory(registration.jeiHelpers.guiHelper)
        registration.addRecipeCategories(
            crushingCategory,
            rollingCategory,
            extrudingCategory,
            cokingCategory,
            alloyingCategory,
            vulcanizingCategory,
            burningCategory,
            hydrogenReductionCategory,
            electrolysisCategory,
            vacuumSealingCategory,
            phaseChangeCategory,
            treeExtractionCategory,
            leadChamberCategory
        )
    }

    /**
     * Fetches recipes from the client-level [RecipeManager] and assigns them to each category.
     * */
    override fun registerRecipes(registration: IRecipeRegistration) {
        val level = Minecraft.getInstance().level
            ?: return

        val recipeManager = level.recipeManager

        registerCategory(registration, crushingCategory, recipeManager, Eln2Processing.CRUSHING_RECIPE)
        registerCategory(registration, rollingCategory, recipeManager, Eln2Processing.ROLLING_RECIPE)
        registerCategory(registration, extrudingCategory, recipeManager, Eln2Processing.EXTRUDING_RECIPE)
        registerCategory(registration, cokingCategory, recipeManager, Eln2Processing.COKING_RECIPE)
        registerCategory(registration, alloyingCategory, recipeManager, Eln2Processing.ALLOYING_RECIPE)
        registerCategory(registration, vulcanizingCategory, recipeManager, Eln2Processing.VULCANIZING_RECIPE)
        registerCategory(registration, burningCategory, recipeManager, Eln2Processing.BURNING_RECIPE)
        registerCategory(registration, electrolysisCategory, recipeManager, Eln2Processing.ELECTROLYSIS_RECIPE)
        registerCategory(registration, vacuumSealingCategory, recipeManager, Eln2Processing.VACUUM_SEALING_RECIPE)
        registerCategory(registration, hydrogenReductionCategory, recipeManager, Eln2Processing.HYDROGEN_REDUCTION_RECIPE)

        // Phase change recipes are data-driven, not from the recipe manager:
        val phaseChangeRecipes = FluidTransformationManager.transformationsByFluid.mapNotNull { (fluid, transformation) ->
            val physicalFluid = PhysicalFluidManager.getProperties(fluid) ?: return@mapNotNull null
            PhaseChangeDisplay(fluid, physicalFluid, transformation)
        }
        registration.addRecipes(phaseChangeCategory.jeiRecipeType, phaseChangeRecipes)

        // Tree extraction recipes are data-driven, not from the recipe manager:
        val treeExtractionRecipes = TreeExtractionManager.getEntries().map { TreeExtractionDisplay(it) }
        registration.addRecipes(treeExtractionCategory.jeiRecipeType, treeExtractionRecipes)
        // Lead chamber process is data-driven, not from the recipe manager:
        val leadChamberRecipe = LeadChamberDisplay(
            Eln2ForgeFluids.SULFUR_DIOXIDE.get(),
            Eln2ForgeFluids.STEAM.get(),
            Eln2ForgeFluids.NITROGEN_DIOXIDE.get(),
            Eln2ForgeFluids.DILUTE_SULFURIC_ACID.get()
        )
        registration.addRecipes(leadChamberCategory.jeiRecipeType, listOf(leadChamberRecipe))
    }

    /**
     * Registers machine blocks as JEI catalysts (**P.S. the name "catalyst" is unrelated to ELN2 recipe catalysts!**).
     * Clicking these in JEI shows recipes for that category.
     * Only machine blocks are registered here (not recipe ingredients, like dies).
     * */
    override fun registerRecipeCatalysts(registration: IRecipeCatalystRegistration) {
        listOf(
            crushingCategory,
            rollingCategory,
            extrudingCategory,
            cokingCategory,
            alloyingCategory,
            vulcanizingCategory,
            burningCategory,
            hydrogenReductionCategory,
            electrolysisCategory,
            vacuumSealingCategory
        ).forEach { category ->
            category.collectCatalysts().forEach { catalyst ->
                registration.addRecipeCatalyst(catalyst.get(), category.jeiRecipeType)
            }
        }

        registration.addRecipeCatalyst(ItemStack(Eln2Processing.INSULATED_DISTILLATION_MODULE_BLOCK.item.get()), phaseChangeCategory.jeiRecipeType)
        registration.addRecipeCatalyst(ItemStack(Eln2Processing.CONDENSER_DISTILLATION_MODULE_BLOCK.item.get()), phaseChangeCategory.jeiRecipeType)
        registration.addRecipeCatalyst(ItemStack(Eln2ForgeFluids.IRON_TANK.blockAndItem.item.get()), phaseChangeCategory.jeiRecipeType)

        registration.addRecipeCatalyst(ItemStack(Eln2Processing.TREE_TAP_PART.item.get()), treeExtractionCategory.jeiRecipeType)
        registration.addRecipeCatalyst(ItemStack(Eln2Processing.LEAD_CHAMBER_BLOCK.item.get()), leadChamberCategory.jeiRecipeType)
    }

    private inline fun <reified T : Recipe<SimpleContainer>> registerCategory(
        registration: IRecipeRegistration,
        category: Eln2RecipeCategory<T>,
        recipeManager: RecipeManager,
        recipeType: net.minecraft.world.item.crafting.RecipeType<T>
    ) {
        val recipes = recipeManager.getAllRecipesFor(recipeType)
        category.setRecipes(recipes)
        registration.addRecipes(category.jeiRecipeType, recipes)
    }
}

/**
 * Base class for all ELN2 JEI recipe categories.
 * Wraps [IRecipeCategory] with shared state (recipes, catalysts), a tick timer for the animated progress arrow, and the furnace texture arrow drawing helper.
 * */
abstract class Eln2RecipeCategory<T : Recipe<*>>(
    val jeiRecipeType: RecipeType<T>,
    val categoryTitle: Component,
    private val categoryWidth: Int,
    private val categoryHeight: Int,
    val categoryIcon: IDrawable,
    private val categoryCatalysts: List<Supplier<ItemStack>>,
    guiHelper: IGuiHelper
) : IRecipeCategory<T> {
    private val arrowTimer = guiHelper.createTickTimer(40, 24, false)

    private var recipes: List<T> = emptyList()

    fun setRecipes(recipes: List<T>) {
        this.recipes = recipes
    }

    fun collectRecipes(): List<T> = recipes

    fun collectCatalysts(): List<Supplier<ItemStack>> = categoryCatalysts

    override fun getRecipeType(): RecipeType<T> = jeiRecipeType

    override fun getTitle(): Component = categoryTitle

    override fun getWidth(): Int = categoryWidth

    override fun getHeight(): Int = categoryHeight

    override fun getIcon(): IDrawable = categoryIcon

    protected fun noteArrow(graphics: GuiGraphics, x: Int, y: Int) {
        val texture = resource("textures/gui/container/progress_arrows.png")
        graphics.blit(
            texture,
            x, y,
            0.0f, 0.0f,
            22, 15,
            32, 32
        )

        val progress = arrowTimer.value * 22.0f / 24.0f

        if (progress > 0) {
            graphics.blit(
                texture,
                x, y,
                0.0f, 16.0f,
                progress.toInt(), 16,
                32, 32
            )
        }
    }
}

/**
 * Drawable that renders an [ItemStack] as a category icon.
 * */
class ItemIcon(private val stacks: List<ItemStack>) : IDrawable {
    constructor(item: ItemLike, count: Int = 1) : this(listOf(ItemStack(item, count)))
    constructor(stack: ItemStack) : this(listOf(stack))

    override fun getWidth(): Int = 16

    override fun getHeight(): Int = 16

    override fun draw(graphics: GuiGraphics, xOffset: Int, yOffset: Int) {
        if (stacks.isNotEmpty()) {
            graphics.renderFakeItem(stacks[0], xOffset, yOffset)
        }
    }
}

class SlotBackground(private val type: Type = Type.STANDARD) : IDrawable {
    enum class Type(val u: Int, val v: Int, val width: Int, val height: Int) {
        STANDARD(0, 0, 18, 18),
        BIG(32, 0, 32, 32),
        SMALL(40, 33, 16, 16)
    }

    override fun getWidth(): Int = type.width

    override fun getHeight(): Int = type.height

    override fun draw(graphics: GuiGraphics, xOffset: Int, yOffset: Int) {
        graphics.blit(
            resource("textures/gui/container/inventory_slots.png"),
            xOffset, yOffset,
            type.u.toFloat(), type.v.toFloat(),
            type.width, type.height,
            64, 64
        )
    }
}

/**
 * JEI category for the Alloying Smelter.
 * Uses [AlloyingRecipe] with weighted multi-item inputs from the ingredient grid.
 * Shows the first option from each weighted requirement group as input, and a single output slot.
 * */
class AlloyingCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<AlloyingRecipe>(
    jeiRecipeType = RecipeType(resource("alloying"), AlloyingRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.alloying"),
    categoryWidth = 177,
    categoryHeight = 85,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.ALLOYING_SMELTER_BLOCK.item.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.ALLOYING_SMELTER_BLOCK.item.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    private val inputPositions = listOf(
        21 to 24,
        48 to 24,
        75 to 24,
        102 to 24
    )

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: AlloyingRecipe, focuses: IFocusGroup) {
        for ((index, requirement) in recipe.inputItems.requirements.withIndex()) {
            if (index >= inputPositions.size) {
                break
            }

            val (x, y) = inputPositions[index]
            val firstOption = requirement.options.firstOrNull()
                ?: continue

            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                .setBackground(slot, -1, -1)
                .addIngredients(firstOption.ingredient)
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 129, 35)
            .setBackground(slot, -1, -1)
            .addItemStack(recipe.output)
    }

    override fun draw(
        recipe: AlloyingRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        noteArrow(graphics, 62, 38)
    }
}

/**
 * JEI category for the Coke Oven.
 * Uses [CokingRecipe] with weighted multi-item inputs, up to 4 output items, and an optional fluid output.
 * The first ingredient option from each weighted requirement group is shown in the input grid.
 * */
class CokingCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<CokingRecipe>(
    jeiRecipeType = RecipeType(resource("coking"), CokingRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.coking"),
    categoryWidth = 177,
    categoryHeight = 110,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.COKE_OVEN_BLOCK_ITEM.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.COKE_OVEN_BLOCK_ITEM.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    private val inputPositions = listOf(
        21 to 24,
        48 to 24,
        75 to 24,
        102 to 24
    )

    private val outputPositions = listOf(
        25 to 49,
        51 to 49,
        77 to 49,
        103 to 49
    )

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: CokingRecipe, focuses: IFocusGroup) {
        for ((index, requirement) in recipe.inputItems.requirements.withIndex()) {
            if (index >= inputPositions.size) {
                break
            }

            val (x, y) = inputPositions[index]
            val firstOption = requirement.options.firstOrNull()
                ?: continue

            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                .setBackground(slot, -1, -1)
                .addIngredients(firstOption.ingredient)
        }

        if (recipe.outputItems != null) {
            for ((index, stack) in recipe.outputItems.withIndex()) {
                if (index >= outputPositions.size) {
                    break
                }

                val (x, y) = outputPositions[index]
                builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .setBackground(slot, -1, -1)
                    .addItemStack(stack)
            }
        }

        if (recipe.outputFluid != null && !recipe.outputFluid.isEmpty) {
            val fluidStack = recipe.outputFluid
            builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 24)
                .setBackground(slot, -1, -1)
                .addFluidStack(fluidStack.fluid, fluidStack.amount.toLong())
                .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
        }
    }

    override fun draw(
        recipe: CokingRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        noteArrow(graphics, 62, 40)
    }
}

/**
 * JEI category for the Crusher. Single input ingredient, single output item.
 * */
class CrushingCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<DirectSimpleProcessingRecipe>(
    jeiRecipeType = RecipeType(resource("crushing"), DirectSimpleProcessingRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.crushing"),
    categoryWidth = 177,
    categoryHeight = 70,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.CRUSHER_PRIMITIVE_KINETIC.blockAndItem.item.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.CRUSHER_PRIMITIVE_KINETIC.blockAndItem.item.get()) },
        Supplier { ItemStack(Eln2Processing.CRUSHER_BRUSHED_DC_MOTOR.blockAndItem.item.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: DirectSimpleProcessingRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 29)
            .setBackground(slot, -1, -1)
            .addIngredients(recipe.input)

        builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 29)
            .setBackground(slot, -1, -1)
            .addItemStack(recipe.output)
    }

    override fun draw(
        recipe: DirectSimpleProcessingRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        noteArrow(graphics, 62, 32)
    }
}

class VacuumSealingCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<DirectSimpleProcessingRecipe>(
    jeiRecipeType = RecipeType(resource("vacuum_sealing"), DirectSimpleProcessingRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.vacuum_sealing"),
    categoryWidth = 177,
    categoryHeight = 70,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.VACUUM_SEALING_BRUSHED_DC_MOTOR.blockAndItem.item.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.VACUUM_SEALING_BRUSHED_DC_MOTOR.blockAndItem.item.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: DirectSimpleProcessingRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 29)
            .setBackground(slot, -1, -1)
            .addIngredients(recipe.input)

        builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 29)
            .setBackground(slot, -1, -1)
            .addItemStack(recipe.output)
    }

    override fun draw(
        recipe: DirectSimpleProcessingRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        noteArrow(graphics, 62, 32)
    }
}

/**
 * JEI category for the Extruder.
 * Uses [CatalyzedSimpleProcessingRecipe]: shows an input slot, a catalyst slot (the die), and an output slot.
 * */
class ExtrudingCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<CatalyzedSimpleProcessingRecipe>(
    jeiRecipeType = RecipeType(resource("extruding"), CatalyzedSimpleProcessingRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.extruding"),
    categoryWidth = 177,
    categoryHeight = 80,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.EXTRUDER_PRIMITIVE_KINETIC.blockAndItem.item.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.EXTRUDER_PRIMITIVE_KINETIC.blockAndItem.item.get()) },
        Supplier { ItemStack(Eln2Processing.EXTRUDER_BRUSHED_DC_MOTOR.blockAndItem.item.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: CatalyzedSimpleProcessingRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 24)
            .setBackground(slot, -1, -1)
            .addIngredients(recipe.input)

        builder.addSlot(RecipeIngredientRole.CATALYST, 27, 49)
            .setBackground(slot, -1, -1)
            .addIngredients(recipe.catalyst)

        builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 35)
            .setBackground(slot, -1, -1)
            .addItemStack(recipe.output)
    }

    override fun draw(
        recipe: CatalyzedSimpleProcessingRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        noteArrow(graphics, 62, 35)
    }
}

/**
 * JEI category for the Rolling Machine. Same recipe structure as crushing ([DirectSimpleProcessingRecipe]) but maps to rolling recipes and the rolling machine blocks.
 * */
class RollingCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<DirectSimpleProcessingRecipe>(
    jeiRecipeType = RecipeType(resource("rolling"), DirectSimpleProcessingRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.rolling"),
    categoryWidth = 177,
    categoryHeight = 70,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.ROLLING_MACHINE_PRIMITIVE_KINETIC.blockAndItem.item.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.ROLLING_MACHINE_PRIMITIVE_KINETIC.blockAndItem.item.get()) },
        Supplier { ItemStack(Eln2Processing.ROLLING_MACHINE_BRUSHED_DC_MOTOR.blockAndItem.item.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: DirectSimpleProcessingRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 29)
            .setBackground(slot, -1, -1)
            .addIngredients(recipe.input)

        builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 29)
            .setBackground(slot, -1, -1)
            .addItemStack(recipe.output)
    }

    override fun draw(
        recipe: DirectSimpleProcessingRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        noteArrow(graphics, 62, 32)
    }
}

/**
 * JEI category for the Burner Reactor.
 * Uses [BurningRecipe] with up to 4 weighted item inputs, an optional output item, and an optional fluid output.
 * Follows the same layout pattern as [CokingCategory].
 * */
class BurningCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<BurningRecipe>(
    jeiRecipeType = RecipeType(resource("burning"), BurningRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.burning"),
    categoryWidth = 177,
    categoryHeight = 110,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.BURNER_BLOCK.item.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.BURNER_BLOCK.item.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    private val inputPositions = listOf(
        21 to 24,
        48 to 24,
        75 to 24,
        102 to 24
    )

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: BurningRecipe, focuses: IFocusGroup) {
        for ((index, requirement) in recipe.inputItems.requirements.withIndex()) {
            if (index >= inputPositions.size) {
                break
            }

            val (x, y) = inputPositions[index]
            val firstOption = requirement.options.firstOrNull()
                ?: continue

            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                .setBackground(slot, -1, -1)
                .addIngredients(firstOption.ingredient)
        }

        if (recipe.outputItem != null && !recipe.outputItem.isEmpty) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 25, 49)
                .setBackground(slot, -1, -1)
                .addItemStack(recipe.outputItem)
        }

        if (recipe.outputFluid != null && !recipe.outputFluid.isEmpty) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 24)
                .setBackground(slot, -1, -1)
                .addFluidStack(recipe.outputFluid.fluid, recipe.outputFluid.amount.toLong())
                .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
        }
    }

    override fun draw(
        recipe: BurningRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        noteArrow(graphics, 62, 40)
    }
}

/**
 * JEI category for the Vulcanizing Autoclave. Shows the input item, the successful output, and the burnt output (when temperature exceeds the recipe's max temperature).
 * Tooltips display the temperature range and which result is the burn failure.
 * */
class VulcanizingCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<VulcanizingRecipe>(
    jeiRecipeType = RecipeType(resource("vulcanizing"), VulcanizingRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.vulcanizing"),
    categoryWidth = 177,
    categoryHeight = 85,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.VULCANIZING_AUTOCLAVE_BLOCK_ITEM.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.VULCANIZING_AUTOCLAVE_BLOCK_ITEM.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: VulcanizingRecipe, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 35)
            .setBackground(slot, -1, -1)
            .addIngredients(recipe.input)

        builder.addSlot(RecipeIngredientRole.OUTPUT, 115, 24)
            .setBackground(slot, -1, -1)
            .addItemStack(recipe.output.copy())
            .addRichTooltipCallback { view, tooltip ->
                tooltip.add(Component.translatable("jei.eln2.vulcanizing.temperature_range",
                    String.format("%.0f", recipe.minTemperature),
                    String.format("%.0f", recipe.maxTemperature)))
            }

        if (!recipe.burnOutput.isEmpty) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 115, 49)
                .setBackground(slot, -1, -1)
                .addItemStack(recipe.burnOutput.copy())
                .addRichTooltipCallback { view, tooltip ->
                    tooltip.add(Component.translatable("jei.eln2.vulcanizing.burnt"))
                }
        }
    }

    override fun draw(
        recipe: VulcanizingRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        noteArrow(graphics, 60, 38)
    }
}

/**
 * JEI category for the Hydrogen Reduction Furnace.
 * Shows the 4 input items, the progress arrow with a temperature label above,
 * the hydrogen amount below the arrow, and the output item.
 * */
class HydrogenReductionCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<HydrogenReductionRecipe>(
    jeiRecipeType = RecipeType(resource("hydrogen_reduction"), HydrogenReductionRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.hydrogen_reduction"),
    categoryWidth = 177,
    categoryHeight = 85,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.HYDROGEN_REDUCTION_FURNACE_BLOCK.item.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.HYDROGEN_REDUCTION_FURNACE_BLOCK.item.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    private val inputPositions = listOf(
        21 to 24,
        48 to 24,
        75 to 24,
        102 to 24,
    )

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: HydrogenReductionRecipe, focuses: IFocusGroup) {
        for ((index, requirement) in recipe.inputItems.requirements.withIndex()) {
            if (index >= inputPositions.size) break

            val (x, y) = inputPositions[index]
            val firstOption = requirement.options.firstOrNull() ?: continue

            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                .setBackground(slot, -1, -1)
                .addIngredients(firstOption.ingredient)
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 129, 35)
            .setBackground(slot, -1, -1)
            .addItemStack(recipe.output)
    }

    override fun draw(
        recipe: HydrogenReductionRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        noteArrow(graphics, 62, 38)

        val scale = Eln2Config.clientConfig.getScaleOverride(Temperature::class.java)

        // Temperature label above the arrow:
        val tempText = if(scale != null) {
            "${classifyAuxiliary(scale, !recipe.minimumTemperature)} - ${classifyAuxiliary(scale, !recipe.optimalTemperature)}"
        }
        else {
            recipe.minimumTemperature.classify()
        }
        graphics.drawString(
            Minecraft.getInstance().font,
            tempText,
            62 + 11 - Minecraft.getInstance().font.width(tempText) / 2,
            24,
            0xFF5555,
            false
        )

        // Hydrogen amount below the arrow:
        val h2Text = "${recipe.hydrogenAmount} mB H₂"
        graphics.drawString(
            Minecraft.getInstance().font,
            h2Text,
            62 + 11 - Minecraft.getInstance().font.width(h2Text) / 2,
            58,
            0x5555FF,
            false
        )
    }
}

class ElectrolysisCategory(guiHelper: IGuiHelper) : Eln2RecipeCategory<AqueousElectrolysisRecipe>(
    jeiRecipeType = RecipeType(resource("electrolysis"), AqueousElectrolysisRecipe::class.java),
    categoryTitle = Component.translatable("recipe.eln2.electrolysis"),
    categoryWidth = 177,
    categoryHeight = 95,
    categoryIcon = ItemIcon(ItemStack(Eln2Processing.ELECTROLYSIS_BLOCK_ITEM.get())),
    categoryCatalysts = listOf(
        Supplier { ItemStack(Eln2Processing.ELECTROLYSIS_BLOCK_ITEM.get()) }
    ),
    guiHelper
) {
    private val slot = SlotBackground()

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: AqueousElectrolysisRecipe, focuses: IFocusGroup) {
        if (!recipe.isSeparated) {
            recipe as NonSeparatedAqueousElectrolysisRecipe

            builder.addSlot(RecipeIngredientRole.INPUT, 21, 28)
                .setBackground(slot, -1, -1)
                .addFluidStack(recipe.inputFluid.fluid, recipe.inputFluid.amount.toLong())
                .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)

            if (recipe.outputGas != null && !recipe.outputGas.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 115, 5)
                    .setBackground(slot, -1, -1)
                    .addFluidStack(recipe.outputGas.fluid, recipe.outputGas.amount.toLong())
                    .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
            }

            if (recipe.outputFluid != null && !recipe.outputFluid.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 115, 28)
                    .setBackground(slot, -1, -1)
                    .addFluidStack(recipe.outputFluid.fluid, recipe.outputFluid.amount.toLong())
                    .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
            }

            if (recipe.anodeOutputItem != null && !recipe.anodeOutputItem.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 115, 51)
                    .setBackground(slot, -1, -1)
                    .addItemStack(recipe.anodeOutputItem)
            }

            // Electrode info slots:
            builder.addSlot(RecipeIngredientRole.CATALYST, 40, 75)
                .setBackground(slot, -1, -1)
                .addIngredients(recipe.anodeElectrode)

            builder.addSlot(RecipeIngredientRole.CATALYST, 80, 75)
                .setBackground(slot, -1, -1)
                .addIngredients(recipe.cathodeElectrode)

            if (recipe.cathodeOutputItem != null && !recipe.cathodeOutputItem.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 140, 51)
                    .setBackground(slot, -1, -1)
                    .addItemStack(recipe.cathodeOutputItem)
            }
        }
        else {
            recipe as SeparatedAqueousElectrolysisRecipe

            builder.addSlot(RecipeIngredientRole.INPUT, 15, 10)
                .setBackground(slot, -1, -1)
                .addFluidStack(recipe.anodeInputFluid.fluid, recipe.anodeInputFluid.amount.toLong())
                .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)

            builder.addSlot(RecipeIngredientRole.INPUT, 57, 10)
                .setBackground(slot, -1, -1)
                .addFluidStack(recipe.cathodeInputFluid.fluid, recipe.cathodeInputFluid.amount.toLong())
                .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)

            // Separator:
            builder.addSlot(RecipeIngredientRole.CATALYST, 36, 36)
                .setBackground(slot, -1, -1)
                .addIngredients(recipe.separator)

            // Anode outputs:
            if (recipe.anodeOutputGas != null && !recipe.anodeOutputGas.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 105, 5)
                    .setBackground(slot, -1, -1)
                    .addFluidStack(recipe.anodeOutputGas.fluid, recipe.anodeOutputGas.amount.toLong())
                    .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
            }

            if (recipe.anodeOutputFluid != null && !recipe.anodeOutputFluid.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 105, 28)
                    .setBackground(slot, -1, -1)
                    .addFluidStack(recipe.anodeOutputFluid.fluid, recipe.anodeOutputFluid.amount.toLong())
                    .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
            }

            if (recipe.anodeOutputItem != null && !recipe.anodeOutputItem.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 105, 51)
                    .setBackground(slot, -1, -1)
                    .addItemStack(recipe.anodeOutputItem)
            }

            // Cathode outputs:
            if (recipe.cathodeOutputGas != null && !recipe.cathodeOutputGas.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 137, 5)
                    .setBackground(slot, -1, -1)
                    .addFluidStack(recipe.cathodeOutputGas.fluid, recipe.cathodeOutputGas.amount.toLong())
                    .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
            }

            if (recipe.cathodeOutputFluid != null && !recipe.cathodeOutputFluid.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 137, 28)
                    .setBackground(slot, -1, -1)
                    .addFluidStack(recipe.cathodeOutputFluid.fluid, recipe.cathodeOutputFluid.amount.toLong())
                    .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
            }

            if (recipe.cathodeOutputItem != null && !recipe.cathodeOutputItem.isEmpty) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 137, 51)
                    .setBackground(slot, -1, -1)
                    .addItemStack(recipe.cathodeOutputItem)
            }

            // Electrode info slots:
            builder.addSlot(RecipeIngredientRole.CATALYST, 21, 75)
                .setBackground(slot, -1, -1)
                .addIngredients(recipe.anodeElectrode)

            builder.addSlot(RecipeIngredientRole.CATALYST, 80, 75)
                .setBackground(slot, -1, -1)
                .addIngredients(recipe.cathodeElectrode)
        }
    }

    override fun draw(
        recipe: AqueousElectrolysisRecipe,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        if (!recipe.isSeparated) {
            noteArrow(graphics, 62, 32)
        }
        else {
            noteArrow(graphics, 62, 15)
        }
    }
}

/**
 * Synthetic JEI display data for a fluid's phase change information.
 * Each entry wraps a single [Fluid] that has both [PhysicalFluid] properties and a [FluidTransformation] (boiling or condensation with optional residues).
 * */
class PhaseChangeDisplay(
    val fluid: Fluid,
    val physicalFluid: PhysicalFluid,
    val transformation: FluidTransformation
)

/**
 * JEI category for fluid phase changes.
 * Shows the input fluid with properties on the right, the boiling result (gas + optional liquid residue) above, and the condensation result (liquid + optional gas residue) below.
 * Unlike other [Eln2RecipeCategory]s, this category works with data-driven [FluidTransformation]s rather than Minecraft recipes.
 * */
class PhaseChangeCategory : IRecipeCategory<PhaseChangeDisplay> {
    val jeiRecipeType = RecipeType(resource("phase_change"), PhaseChangeDisplay::class.java)

    private val slot = SlotBackground()

    override fun getRecipeType(): RecipeType<PhaseChangeDisplay> = jeiRecipeType

    override fun getTitle(): Component = Component.translatable("recipe.eln2.phase_change")

    override fun getWidth(): Int = 177

    override fun getHeight(): Int = 85

    override fun getIcon(): IDrawable = ItemIcon(ItemStack(Eln2Processing.INSULATED_DISTILLATION_MODULE_BLOCK.item.get()))

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: PhaseChangeDisplay, focuses: IFocusGroup) {
        // Input fluid slot, middle-left
        builder.addSlot(RecipeIngredientRole.INPUT, 12, 38)
            .setBackground(slot, -1, -1)
            .addFluidStack(recipe.fluid, FluidType.BUCKET_VOLUME.toLong())
            .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)

        // Boiling result above the input
        recipe.transformation.boiling?.let { boiling ->
            builder.addSlot(RecipeIngredientRole.OUTPUT, 12, 12)
                .setBackground(slot, -1, -1)
                .addFluidStack(boiling.resultGas, FluidType.BUCKET_VOLUME.toLong())
                .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)

            boiling.resultLiquidResidue?.let { residue ->
                builder.addSlot(RecipeIngredientRole.OUTPUT, 34, 12)
                    .setBackground(slot, -1, -1)
                    .addFluidStack(residue, FluidType.BUCKET_VOLUME.toLong())
                    .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
            }
        }

        // Condensation result below the input
        recipe.transformation.condensation?.let { condensation ->
            builder.addSlot(RecipeIngredientRole.OUTPUT, 12, 64)
                .setBackground(slot, -1, -1)
                .addFluidStack(condensation.resultLiquid, FluidType.BUCKET_VOLUME.toLong())
                .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)

            condensation.resultGasResidue?.let { residue ->
                builder.addSlot(RecipeIngredientRole.OUTPUT, 34, 64)
                    .setBackground(slot, -1, -1)
                    .addFluidStack(residue, FluidType.BUCKET_VOLUME.toLong())
                    .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
            }
        }
    }

    override fun draw(
        recipe: PhaseChangeDisplay,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        val font = Minecraft.getInstance().font
        val phys = recipe.physicalFluid
        val trans = recipe.transformation

        val pose = graphics.pose()
        pose.pushPose()
        pose.scale(0.5f, 0.5f, 1.0f)

        val xLabel = 120
        val labelColor = 0x808080
        val valueColor = 0x000000
        val lineHeight = 14
        var y = 20

        // State: Gaseous / Liquid
        val stateLabel = Component.translatable("tooltip.eln2.fluid.state")
        val stateValue = Component.translatable(if (phys.isGaseous) "tooltip.eln2.fluid.gaseous" else "tooltip.eln2.fluid.liquid")
        graphics.drawString(font, stateLabel, xLabel, y, labelColor, false)
        graphics.drawString(font, stateValue, xLabel + font.width(stateLabel) + 4, y, valueColor, false)
        y += lineHeight

        // Density
        val densityLabel = Component.translatable("tooltip.eln2.fluid.density")
        val densityValue = Component.literal(phys.density.classify())
        graphics.drawString(font, densityLabel, xLabel, y, labelColor, false)
        graphics.drawString(font, densityValue, xLabel + font.width(densityLabel) + 4, y, valueColor, false)
        y += lineHeight

        // Specific Heat Capacity
        val shcLabel = Component.translatable("tooltip.eln2.fluid.specific_heat")
        val shcValue = Component.literal(phys.specificHeatCapacity.classify())
        graphics.drawString(font, shcLabel, xLabel, y, labelColor, false)
        graphics.drawString(font, shcValue, xLabel + font.width(shcLabel) + 4, y, valueColor, false)
        y += lineHeight

        // Boiling info
        trans.boiling?.let { boiling ->
            val tempLabel = Component.translatable("tooltip.eln2.fluid.boiling_point")
            val tempValue = Component.literal(Eln2Config.clientConfig.classifyWithOverride(boiling.temperature))
            graphics.drawString(font, tempLabel, xLabel, y, labelColor, false)
            graphics.drawString(font, tempValue, xLabel + font.width(tempLabel) + 4, y, 0xFF5555, false)
            y += lineHeight

            val enthalpyLabel = Component.translatable("tooltip.eln2.fluid.vaporization_enthalpy")
            val enthalpyValue = Component.literal(boiling.enthalpy.classify())
            graphics.drawString(font, enthalpyLabel, xLabel, y, labelColor, false)
            graphics.drawString(font, enthalpyValue, xLabel + font.width(enthalpyLabel) + 4, y, valueColor, false)

            // Boiling temperature between input and boiling result
            val tempText = Eln2Config.clientConfig.classifyWithOverride(boiling.temperature)
            graphics.drawString(font, tempText, 20 * 2 - font.width(tempText) / 2, 30 * 2, 0xFF5555, false)
        }

        // Condensation info
        trans.condensation?.let { condensation ->
            val tempLabel = Component.translatable("tooltip.eln2.fluid.condensation_point")
            val tempValue = Component.literal(Eln2Config.clientConfig.classifyWithOverride(condensation.temperature))
            graphics.drawString(font, tempLabel, xLabel, y, labelColor, false)
            graphics.drawString(font, tempValue, xLabel + font.width(tempLabel) + 4, y, 0x5555FF, false)
            y += lineHeight

            val enthalpyLabel = Component.translatable("tooltip.eln2.fluid.condensation_enthalpy")
            val enthalpyValue = Component.literal(condensation.enthalpy.classify())
            graphics.drawString(font, enthalpyLabel, xLabel, y, labelColor, false)
            graphics.drawString(font, enthalpyValue, xLabel + font.width(enthalpyLabel) + 4, y, valueColor, false)

            // Condensation temperature between input and condensation result
            val tempText = Eln2Config.clientConfig.classifyWithOverride(condensation.temperature)
            graphics.drawString(font, tempText, 20 * 2 - font.width(tempText) / 2, 55 * 2, 0x5555FF, false)
        }

        pose.popPose()
    }
}

/**
 * Synthetic JEI display data for tree extraction recipes (rubber tap).
 * Each entry maps a log block to the harvestable item.
 * */
class TreeExtractionDisplay(
    val entry: TreeExtractionManager.Entry
) {
    val logItem: ItemStack get() = ItemStack(entry.log)
    val resultItem: ItemStack get() = ItemStack(entry.resultItem)
}

/**
 * JEI category for the tree tap (rubber tapping).
 * Shows the log block, a progress arrow, and the output item with a time range above the arrow.
 * */
class TreeExtractionCategory(guiHelper: IGuiHelper) : IRecipeCategory<TreeExtractionDisplay> {
    val jeiRecipeType = RecipeType(resource("tree_extraction"), TreeExtractionDisplay::class.java)

    private val slot = SlotBackground()
    private val arrowTimer = guiHelper.createTickTimer(40, 24, false)

    override fun getRecipeType(): RecipeType<TreeExtractionDisplay> = jeiRecipeType

    override fun getTitle(): Component = Component.translatable("recipe.eln2.tree_extraction")

    override fun getWidth(): Int = 177

    override fun getHeight(): Int = 70

    override fun getIcon(): IDrawable = ItemIcon(ItemStack(Eln2Processing.TREE_TAP_PART.item.get()))

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: TreeExtractionDisplay, focuses: IFocusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 29)
            .setBackground(slot, -1, -1)
            .addItemStack(recipe.logItem)

        builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 29)
            .setBackground(slot, -1, -1)
            .addItemStack(recipe.resultItem)
    }

    override fun draw(
        recipe: TreeExtractionDisplay,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        // Progress arrow
        val texture = resource("textures/gui/container/progress_arrows.png")
        graphics.blit(
            texture,
            62, 32,
            0.0f, 0.0f,
            22, 15,
            32, 32
        )

        val progress = arrowTimer.value * 22.0f / 24.0f

        if (progress > 0) {
            graphics.blit(
                texture,
                62, 32,
                0.0f, 16.0f,
                progress.toInt(), 16,
                32, 32
            )
        }

        // Time range above the arrow
        val font = Minecraft.getInstance().font
        val minScans = ceil(1.0 / recipe.entry.maxBaseProgress).toInt().coerceAtLeast(1)
        val maxScans = ceil(1.0 / recipe.entry.minBaseProgress).toInt().coerceAtLeast(1)
        val minSeconds = minScans * 3
        val maxSeconds = maxScans * 5
        val timeText = "${minSeconds}s - ${maxSeconds}s"

        graphics.drawString(
            font,
            timeText,
            62 + 11 - font.width(timeText) / 2,
            20,
            0x808080,
            false
        )
    }
}


/**
 * Synthetic JEI display data for the lead chamber process.
 * Wraps the four fluids involved in the chamber reaction; stoichiometric ratios and catalyst saturation are read from [LeadChamberBlockEntity.LeadChamberSimulation] so the display cannot drift from the simulation.
 * */
class LeadChamberDisplay(
    val sulfurDioxide: Fluid,
    val steam: Fluid,
    val nitrogenDioxide: Fluid,
    val diluteSulfuricAcid: Fluid
)

class LeadChamberCategory(guiHelper: IGuiHelper) : IRecipeCategory<LeadChamberDisplay> {
    val jeiRecipeType = RecipeType(resource("lead_chamber"), LeadChamberDisplay::class.java)

    private val slot = SlotBackground()
    private val arrowTimer = guiHelper.createTickTimer(40, 24, false)

    override fun getRecipeType(): RecipeType<LeadChamberDisplay> = jeiRecipeType

    override fun getTitle(): Component = Component.translatable("recipe.eln2.lead_chamber")

    override fun getWidth(): Int = 177

    override fun getHeight(): Int = 85

    override fun getIcon(): IDrawable = ItemIcon(ItemStack(Eln2Processing.LEAD_CHAMBER_BLOCK.item.get()))

    override fun setRecipe(builder: IRecipeLayoutBuilder, recipe: LeadChamberDisplay, focuses: IFocusGroup) {
        val sulfurDioxideAmount = LeadChamberBlockEntity.LeadChamberSimulation.SULFUR_DIOXIDE_PER_ACID.toLong()
        val steamAmount = LeadChamberBlockEntity.LeadChamberSimulation.STEAM_PER_ACID.toLong()
        val acidAmount = 1L

        builder.addSlot(RecipeIngredientRole.INPUT, 21, 10)
            .setBackground(slot, -1, -1)
            .addFluidStack(recipe.sulfurDioxide, sulfurDioxideAmount)
            .setFluidRenderer(sulfurDioxideAmount, false, 16, 16)

        builder.addSlot(RecipeIngredientRole.INPUT, 48, 10)
            .setBackground(slot, -1, -1)
            .addFluidStack(recipe.steam, steamAmount)
            .setFluidRenderer(steamAmount, false, 16, 16)

        builder.addSlot(RecipeIngredientRole.OUTPUT, 110, 10)
            .setBackground(slot, -1, -1)
            .addFluidStack(recipe.diluteSulfuricAcid, acidAmount)
            .setFluidRenderer(acidAmount, false, 16, 16)

        builder.addSlot(RecipeIngredientRole.CATALYST, 21, 50)
            .setBackground(slot, -1, -1)
            .addFluidStack(recipe.nitrogenDioxide, FluidType.BUCKET_VOLUME.toLong())
            .setFluidRenderer(FluidType.BUCKET_VOLUME.toLong(), false, 16, 16)
            .addRichTooltipCallback { view, tooltip ->
                tooltip.add(Component.translatable("jei.eln2.lead_chamber.catalyst_not_consumed"))
            }
    }

    override fun draw(
        recipe: LeadChamberDisplay,
        recipeSlotsView: IRecipeSlotsView,
        graphics: GuiGraphics,
        mouseX: Double,
        mouseY: Double
    ) {
        val texture = resource("textures/gui/container/progress_arrows.png")

        graphics.blit(
            texture,
            75, 12,
            0.0f, 0.0f,
            22, 15,
            32, 32
        )

        val progress = arrowTimer.value * 22.0f / 24.0f

        if (progress > 0) {
            graphics.blit(
                texture,
                75, 12,
                0.0f, 16.0f,
                progress.toInt(), 16,
                32, 32
            )
        }

        val font = Minecraft.getInstance().font
        val maxCatalystPercent = (LeadChamberBlockEntity.LeadChamberSimulation.MAX_CATALYST_FRACTION * 100).toInt()
        val catalystNote = Component.translatable("jei.eln2.lead_chamber.catalyst_not_consumed")
        graphics.drawString(font, catalystNote, 42, 54, 0x5555FF, false)

        val maxRateNote = Component.translatable("jei.eln2.lead_chamber.max_rate", maxCatalystPercent)
        graphics.drawString(font, maxRateNote, 42, 66, 0x808080, false)
    }
}
