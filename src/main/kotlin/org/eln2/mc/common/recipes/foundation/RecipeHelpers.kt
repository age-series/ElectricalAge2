@file:Suppress("ClassName")

package org.eln2.mc.common.recipes.foundation

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.util.GsonHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import kotlin.math.min

/**
 * Represents one valid ingredient for a recipe.
 * The [value] is used to set how many of this specific item are required to substitute into the recipe.
 * */
data class Eln2WeightedItemIngredient(val ingredient: Ingredient, val value: Int = 1) {
    fun toNetwork(buf: FriendlyByteBuf) {
        ingredient.toNetwork(buf)
        buf.writeInt(value)
    }

    companion object {
        fun fromJson(json: JsonObject) : Eln2WeightedItemIngredient {
            val ingredient = Ingredient.fromJson(json.getAsJsonObject("ingredient"))
            val value = GsonHelper.getAsInt(json, "value")

            return Eln2WeightedItemIngredient(ingredient, value)
        }

        fun fromNetwork(buf: FriendlyByteBuf) : Eln2WeightedItemIngredient {
            val ingredient = Ingredient.fromNetwork(buf)
            val value = buf.readInt()

            return Eln2WeightedItemIngredient(ingredient, value)
        }
    }
}

/**
 * Represents all possible variants of an ingredient in a recipe. Example: allows you to substitute 1 coal for e.g. 8 coal fragments, or 1 coal coke dust, and so on.
 * I'm not sure that's how it should be done. If not, please correct (join the discord or open a pull request).
 *
 * @param options All possible allowed ingredients.
 * @param requiredValue The total value required. Basically, the value of an item stack is the number of items multiplied by [Eln2WeightedItemIngredient.value].
 * */
data class Eln2WeightedItemRecipeRequirement(val options: List<Eln2WeightedItemIngredient>, val requiredValue: Int) {
    fun toNetwork(buf: FriendlyByteBuf) {
        buf.writeCollection(options) { _, ingredient ->
            ingredient.toNetwork(buf)
        }

        buf.writeInt(requiredValue)
    }

    companion object {
        fun fromJson(json: JsonObject) : Eln2WeightedItemRecipeRequirement {
            val options = json.getAsJsonArray("options")
                .map { it as JsonObject }
                .map(Eln2WeightedItemIngredient::fromJson)

            val requiredValue = GsonHelper.getAsInt(json, "requiredValue")

            return Eln2WeightedItemRecipeRequirement(options, requiredValue)
        }

        fun fromNetwork(buf: FriendlyByteBuf) : Eln2WeightedItemRecipeRequirement {
            val options = buf.readList {
                Eln2WeightedItemIngredient.fromNetwork(buf)
            }

            val requiredValue = buf.readInt()

            return Eln2WeightedItemRecipeRequirement(options, requiredValue)
        }
    }
}

/**
 * Represents a recipe's multiple possible substituted inputs.
 * */
data class Eln2WeightedItemRecipeRequirements(val requirements: List<Eln2WeightedItemRecipeRequirement>) {
    fun toNetwork(buf: FriendlyByteBuf) {
        buf.writeCollection(requirements) { _, requirement ->
            requirement.toNetwork(buf)
        }
    }

    companion object {
        fun fromJson(json: JsonElement) : Eln2WeightedItemRecipeRequirements {
            val requirements = json.asJsonArray
                .map { it as JsonObject }
                .map { Eln2WeightedItemRecipeRequirement.fromJson(it) }

            return Eln2WeightedItemRecipeRequirements(requirements)
        }

        fun fromNetwork(buf: FriendlyByteBuf) : Eln2WeightedItemRecipeRequirements {
            val requirements = buf.readList {
                Eln2WeightedItemRecipeRequirement.fromNetwork(buf)
            }

            return Eln2WeightedItemRecipeRequirements(requirements)
        }
    }
}

/**
 * Checks if the [recipe] is matched by item stacks, and also mutates the item stacks along the way.
 * @param checkForUnmatchedItems If true, then the recipe won't be applicable if there exist items in the list that don't match any options in the recipe.
 * @return True if the container satisfies the recipe. Otherwise, false (but the container is still mutated!).
 * */
fun List<ItemStack>.applyRecipeWeighted(recipe: Eln2WeightedItemRecipeRequirements, checkForUnmatchedItems: Boolean) : Boolean {
    if(checkForUnmatchedItems) {
        /**
         * Checks for any illegal items:
         * */
        for (currentStack in this) {
            if(currentStack.isEmpty) {
                continue
            }

            if(!recipe.requirements.any { requirement -> requirement.options.any { it.ingredient.test(currentStack) } }) {
                return false
            }
        }
    }

    /**
     * The algorithm is very inefficient, but we assume the inventories are very small, and it's also not called in a hot path.
     * */
    for (requirement in recipe.requirements) {
        /**
         * The remaining value to satisfy:
         * */
        var valueRequirementRemaining = requirement.requiredValue

        /**
         * Tries to match each option to the inventory:
         * */
        for (option in requirement.options) {
            for (currentStack in this) {

                if(currentStack.isEmpty) {
                    continue
                }

                if(!option.ingredient.test(currentStack)) {
                    continue
                }

                /**
                 * Calculates how many items of this type are needed to satisfy the remaining value.
                 * */
                val itemsNeeded = (valueRequirementRemaining + option.value - 1) / option.value
                val itemsToTake = min(itemsNeeded, currentStack.count)

                val valueContributed = itemsToTake * option.value
                valueRequirementRemaining -= valueContributed

                /**
                 * Removes the items from the inventory:
                 * */
                currentStack.shrink(itemsToTake)

                if(valueRequirementRemaining <= 0) {
                    break
                }
            }

            if(valueRequirementRemaining <= 0) {
                break
            }
        }

        /**
         * Didn't match this specific requirement:
         * */
        if (valueRequirementRemaining > 0) {
            return false
        }
    }

    return true
}

/**
 * Function reference to [net.minecraftforge.items.IItemHandler.insertItem]
 * */
fun interface IItemHandler_insertItem {
    /**
     * <p>
     * Inserts an ItemStack into the given slot and return the remainder.
     * The ItemStack <em>should not</em> be modified in this function!
     * </p>
     * Note: This behaviour is subtly different from {@link IFluidHandler#fill(FluidStack, IFluidHandler.FluidAction)}
     *
     * @param slot     Slot to insert into.
     * @param stack    ItemStack to insert. This must not be modified by the item handler.
     * @param simulate If true, the insertion is only simulated
     * @return The remaining ItemStack that was not inserted (if the entire stack is accepted, then return an empty ItemStack).
     *         May be the same as the input ItemStack if unchanged, otherwise a new ItemStack.
     *         The returned ItemStack can be safely modified after.
     **/
    fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean) : ItemStack
}

/**
 * Tries to insert the [items] into this handler, in the slots in [slotRange]. **Mutates the handler!**
 * @return True if all items were inserted. Otherwise, false (still mutates the handler!).
 * */
fun IItemHandler_insertItem.insertRange(items: List<ItemStack>, slotRange: IntRange) : Boolean {
    for (sourceStack in items) {
        var remainingStack = sourceStack.copy()

        for (slot in slotRange) {
            remainingStack = this.insertItem(slot, remainingStack, false)

            if(remainingStack.isEmpty) {
                break
            }
        }

        if(!remainingStack.isEmpty) {
            return false
        }
    }

    return true
}
