@file:Suppress("unused")

package org.eln2.mc.common.recipes.foundation

import com.google.gson.JsonObject
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.CriterionTriggerInstance
import net.minecraft.core.RegistryAccess
import net.minecraft.data.recipes.FinishedRecipe
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.GsonHelper
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.registries.ForgeRegistries
import org.eln2.mc.*
import org.eln2.mc.common.recipes.RecipeRegistry
import org.eln2.mc.extensions.bindToSimpleContainer
import org.eln2.mc.extensions.eln2Unlock
import org.eln2.mc.extensions.getInt
import org.eln2.mc.extensions.recipeExists
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Recipe for the [ProcessingRecipeInventoryHandler] and [ProcessingRecipeLoop].
 * */
interface Eln2ProcessingLoopRecipe {
    /**
     * The ID of the recipe, including the path of the data file.
     * */
    val recipeId: ResourceLocation

    /**
     * The base duration, in seconds. Granularity of this value is in game ticks.
     * The processing takes [duration] (in equivalent ticks) (excluding I/O) if the device's speed is 1.
     * */
    val duration: Double
}

interface Eln2SimpleOutputProcessingLoopRecipe : Eln2ProcessingLoopRecipe, Recipe<SimpleContainer> {
    val output: ItemStack
}

/**
 * Implemented by recipes which have a certain tier, which blocks crude machines from being able to apply it.
 * */
interface Eln2TieredRecipe {
    val tier: Int
}

/**
 * Processor (probably a [org.eln2.mc.common.cells.foundation.Cell]) that allows starting/stopping its operation and provides the processing speed.
 * */
interface ProcessingDevice {
    /**
     * Set by the game object when processing is needed.
     * */
    @CrossThreadAccess
    @OnServerThread
    var isActive: Boolean

    /**
     * Read by the game object and used to advance the recipe.
     * */
    @CrossThreadAccess
    @OnServerThread
    val processingSpeed: Double
}

/**
 * Implemented by a [ProcessingDevice] that has a tier, meant to be used with [Eln2TieredRecipe].
 * */
interface TieredProcessingDevice : ProcessingDevice {
    val tier: Int
}

/**
 * Recipe for converting an item into another item. Can be used for e.g. a crusher.
 * @param input The input ingredient. Must be a single item stack with 1 count.
 * @param output The output item. Must be a single item with 1 or more count.
 * @param duration The base duration, in seconds.
 * @param tier The recipe's tier. By default, `0`.
 * */
class DirectSimpleProcessingRecipe(
    val recipeSerializer: Serializer,
    override val recipeId: ResourceLocation,
    val input: Ingredient,
    override val output: ItemStack,
    override val duration: Double,
    override val tier: Int
) : Eln2SimpleOutputProcessingLoopRecipe, Eln2TieredRecipe {
    // WARNING: Do NOT access `input.items` (Ingredient.getItems()) in an init block or during deserialization.
    // Doing so forces tag resolution before the TagManager has finished loading, caching an empty result
    // permanently. This causes tag-based ingredients to silently fail until /reload.

    override fun matches(pContainer: SimpleContainer, pLevel: Level) = input.test(pContainer.getItem(INPUT_SLOT))
    override fun assemble(pContainer: SimpleContainer, pRegistryAccess: RegistryAccess): ItemStack = output.copy()
    override fun canCraftInDimensions(pWidth: Int, pHeight: Int) = true
    override fun getResultItem(pRegistryAccess: RegistryAccess): ItemStack = output.copy()

    override fun getId() = recipeId
    override fun getSerializer() = recipeSerializer
    override fun getType() = recipeSerializer.recipeType

    class Serializer(val recipeType: RecipeType<DirectSimpleProcessingRecipe>) :
        RecipeSerializer<DirectSimpleProcessingRecipe> {
        override fun fromJson(pRecipeId: ResourceLocation, pSerializedRecipe: JsonObject): DirectSimpleProcessingRecipe {
            val input = Ingredient.fromJson(pSerializedRecipe.get("ingredient"))
            val output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "result"))
            val duration = pSerializedRecipe.getAsJsonPrimitive("duration").asDouble
            val tier = pSerializedRecipe.getInt("tier", 0)

            return DirectSimpleProcessingRecipe(
                this,
                pRecipeId,
                input,
                output,
                duration,
                tier
            )
        }

        override fun fromNetwork(pRecipeId: ResourceLocation, pBuffer: FriendlyByteBuf): DirectSimpleProcessingRecipe {
            val input = Ingredient.fromNetwork(pBuffer)
            val output = pBuffer.readItem()
            val duration = pBuffer.readDouble()
            val tier = pBuffer.readInt()

            return DirectSimpleProcessingRecipe(
                this,
                pRecipeId,
                input,
                output,
                duration,
                tier
            )
        }

        override fun toNetwork(pBuffer: FriendlyByteBuf, pRecipe: DirectSimpleProcessingRecipe) {
            pRecipe.input.toNetwork(pBuffer)
            pBuffer.writeItem(pRecipe.output)
            pBuffer.writeDouble(pRecipe.duration)
            pBuffer.writeInt(pRecipe.tier)
        }
    }
}

class DirectSimpleProcessingRecipeBuilder(val recipe: RecipeType<DirectSimpleProcessingRecipe>) {
    private var input: Ingredient = Ingredient.EMPTY
    private var output: ItemStack = ItemStack.EMPTY
    private var duration: Double = 200.0
    private var tier: Int = 0
    val advancement: Advancement.Builder = Advancement.Builder.advancement()

    fun withInput(input: ItemLike): DirectSimpleProcessingRecipeBuilder {
        this.input = Ingredient.of(input)
        return this
    }

    fun withInput(input: Ingredient): DirectSimpleProcessingRecipeBuilder {
        this.input = input
        return this
    }

    fun withOutput(output: ItemLike, count: Int = 1): DirectSimpleProcessingRecipeBuilder {
        this.output = ItemStack(output, count)
        return this
    }

    fun withDuration(duration: Double): DirectSimpleProcessingRecipeBuilder {
        this.duration = duration
        return this
    }

    fun withTier(tier: Int): DirectSimpleProcessingRecipeBuilder {
        this.tier = tier
        return this
    }

    fun unlockedBy(pCriterionName: String, pCriterionTrigger: CriterionTriggerInstance): DirectSimpleProcessingRecipeBuilder {
        advancement.addCriterion(pCriterionName, pCriterionTrigger)
        return this
    }

    fun save(consumer: Consumer<FinishedRecipe?>, id: ResourceLocation) {
        check(!input.isEmpty) {
            DEBUGGER_BREAK("Input for direct simple processing recipe cannot be empty")
        }

        check(!output.isEmpty) {
            DEBUGGER_BREAK("Output for direct simple processing recipe cannot be empty")
        }

        if (advancement.criteria.isEmpty()) {
            LOG.error("No criterion for direct simple processing recipe $id")
        }
        else {
            advancement.eln2Unlock(id)
        }

        consumer.accept(Serializer(this, id))
    }

    class Serializer(val parent: DirectSimpleProcessingRecipeBuilder, val recipeId: ResourceLocation) : Eln2FinishedRecipe {
        override fun serializeRecipeData(json: JsonObject) {
            json.add("ingredient", parent.input.toJson())

            json.add("result", JsonObject().also { resultJson ->
                resultJson.addProperty("item", ForgeRegistries.ITEMS.getKey(parent.output.item)!!.toString())
                resultJson.addProperty("count", parent.output.count)
            })

            json.addProperty("duration", parent.duration)

            if (parent.tier != 0) {
                json.addProperty("tier", parent.tier)
            }
        }

        override fun getId(): ResourceLocation = recipeId
        override fun getType(): RecipeSerializer<*> = RecipeRegistry.getRecipeSerializer(parent.recipe)!!.get()
        override fun serializeAdvancement(): JsonObject = parent.advancement.serializeToJson()
    }
}

/**
 * Recipe for converting an item into another item, using a catalyst item that isn't consumed. Can be used for e.g. a press.
 * @param input The input ingredient. Must be a single item stack with 1 count.
 * @param catalyst The catalyst. Must abe a single item stack with 1 count.
 * @param output The output item. Must be a single item with 1 or more count.
 * @param duration The base duration, in seconds.
 * @param tier The recipe's tier. By default, `0`.
 * */
class CatalyzedSimpleProcessingRecipe(
    val recipeSerializer: Serializer,
    override val recipeId: ResourceLocation,
    val input: Ingredient,
    val catalyst: Ingredient,
    override val output: ItemStack,
    override val duration: Double,
    override val tier: Int
) : Eln2SimpleOutputProcessingLoopRecipe, Eln2TieredRecipe {
    // WARNING: Do NOT access `input.items` or `catalyst.items` (Ingredient.getItems()) in an init block or
    // during deserialization. Doing so forces tag resolution before the TagManager has finished loading,
    // caching an empty result permanently. This causes tag-based ingredients to silently fail until /reload.

    override fun matches(pContainer: SimpleContainer, pLevel: Level) =
        input.test(pContainer.getItem(INPUT_SLOT)) &&
        catalyst.test(pContainer.getItem(CATALYST_SLOT))

    override fun assemble(pContainer: SimpleContainer, pRegistryAccess: RegistryAccess): ItemStack = output.copy()
    override fun canCraftInDimensions(pWidth: Int, pHeight: Int) = true
    override fun getResultItem(pRegistryAccess: RegistryAccess): ItemStack = output.copy()

    override fun getId() = recipeId
    override fun getSerializer() = recipeSerializer
    override fun getType() = recipeSerializer.recipeType

    class Serializer(val recipeType: RecipeType<CatalyzedSimpleProcessingRecipe>) :
        RecipeSerializer<CatalyzedSimpleProcessingRecipe> {
        override fun fromJson(pRecipeId: ResourceLocation, pSerializedRecipe: JsonObject): CatalyzedSimpleProcessingRecipe {
            val input = Ingredient.fromJson(pSerializedRecipe.get("ingredient"))
            val catalyst = Ingredient.fromJson(pSerializedRecipe.get("catalyst"))
            val output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(pSerializedRecipe, "result"))
            val duration = pSerializedRecipe.getAsJsonPrimitive("duration").asDouble
            val tier = pSerializedRecipe.getInt("tier", 0)

            return CatalyzedSimpleProcessingRecipe(
                this,
                pRecipeId,
                input,
                catalyst,
                output,
                duration,
                tier
            )
        }

        override fun fromNetwork(pRecipeId: ResourceLocation, pBuffer: FriendlyByteBuf): CatalyzedSimpleProcessingRecipe {
            val input = Ingredient.fromNetwork(pBuffer)
            val catalyst = Ingredient.fromNetwork(pBuffer)
            val output = pBuffer.readItem()
            val duration = pBuffer.readDouble()
            val tier = pBuffer.readInt()

            return CatalyzedSimpleProcessingRecipe(
                this,
                pRecipeId,
                input,
                catalyst,
                output,
                duration,
                tier
            )
        }

        override fun toNetwork(pBuffer: FriendlyByteBuf, pRecipe: CatalyzedSimpleProcessingRecipe) {
            pRecipe.input.toNetwork(pBuffer)
            pRecipe.catalyst.toNetwork(pBuffer)
            pBuffer.writeItem(pRecipe.output)
            pBuffer.writeDouble(pRecipe.duration)
            pBuffer.writeInt(pRecipe.tier)
        }
    }
}

class CatalyzedSimpleProcessingRecipeBuilder(val recipe: RecipeType<CatalyzedSimpleProcessingRecipe>) {
    var input: Ingredient = Ingredient.EMPTY
    var catalyst: Ingredient = Ingredient.EMPTY
    var output: ItemStack = ItemStack.EMPTY
    var duration: Double = 10.0
    var tier: Int = 0
    val advancement: Advancement.Builder = Advancement.Builder.advancement()

    fun withInput(input: ItemLike): CatalyzedSimpleProcessingRecipeBuilder {
        this.input = Ingredient.of(input)
        return this
    }

    fun withInput(input: Ingredient): CatalyzedSimpleProcessingRecipeBuilder {
        this.input = input
        return this
    }

    fun withCatalyst(catalyst: ItemLike): CatalyzedSimpleProcessingRecipeBuilder {
        this.catalyst = Ingredient.of(catalyst)
        return this
    }

    fun withCatalyst(catalyst: Ingredient): CatalyzedSimpleProcessingRecipeBuilder {
        this.catalyst = catalyst
        return this
    }

    fun withOutput(output: ItemLike, count: Int = 1): CatalyzedSimpleProcessingRecipeBuilder {
        this.output = ItemStack(output, count)
        return this
    }

    fun withDuration(duration: Double): CatalyzedSimpleProcessingRecipeBuilder {
        this.duration = duration
        return this
    }

    fun withTier(tier: Int): CatalyzedSimpleProcessingRecipeBuilder {
        this.tier = tier
        return this
    }

    fun unlockedBy(pCriterionName: String, pCriterionTrigger: CriterionTriggerInstance): CatalyzedSimpleProcessingRecipeBuilder {
        advancement.addCriterion(pCriterionName, pCriterionTrigger)
        return this
    }

    fun save(consumer: Consumer<FinishedRecipe?>, id: ResourceLocation) {
        check(!input.isEmpty) {
            DEBUGGER_BREAK("Input for catalyzed simple processing recipe cannot be empty")
        }

        check(!catalyst.isEmpty) {
            DEBUGGER_BREAK("Catalyst for catalyzed simple processing recipe cannot be empty")
        }

        check(!output.isEmpty) {
            DEBUGGER_BREAK("Output for catalyzed simple processing recipe cannot be empty")
        }

        if (advancement.criteria.isEmpty()) {
            LOG.error("No criterion for catalyzed simple processing recipe $id")
        }
        else {
            advancement.eln2Unlock(id)
        }

        consumer.accept(Result(this, id))
    }

    class Result(val parent: CatalyzedSimpleProcessingRecipeBuilder, val recipeId: ResourceLocation) : Eln2FinishedRecipe {
        override fun serializeRecipeData(json: JsonObject) {
            json.add("ingredient", parent.input.toJson())

            json.add("catalyst", parent.catalyst.toJson())

            json.add("result", JsonObject().also { resultJson ->
                resultJson.addProperty("item", ForgeRegistries.ITEMS.getKey(parent.output.item)!!.toString())
                resultJson.addProperty("count", parent.output.count)
            })

            json.addProperty("duration", parent.duration)

            if (parent.tier != 0) {
                json.addProperty("tier", parent.tier)
            }
        }

        override fun getId(): ResourceLocation = recipeId
        override fun getType(): RecipeSerializer<*> = RecipeRegistry.getRecipeSerializer(parent.recipe)!!.get()
        override fun serializeAdvancement(): JsonObject = parent.advancement.serializeToJson()
    }
}

interface ProcessingRecipeInventoryHandler<R : Eln2ProcessingLoopRecipe> {
    /**
     * Checks if the input was recently changed, and resets the flag.
     * */
    fun wasInputChanged() : Boolean

    /**
     * Searches for the recipe in the current input slot. Must be fast (think: called per-tick).
     * Guaranteed to be present if the input slot is not empty because the input is filtered.
     * (**Implementations of the interface must guarantee this filtering logic!**)
     * */
    fun searchForRecipe() : Optional<R>

    /**
     * If the input was recently changed, checks if the previous recipe is the same as the current one.
     * */
    fun wasRecipeChanged(previousRecipe: R) : Boolean {
        if(!wasInputChanged()) {
            return false
        }

        val currentRecipe = searchForRecipe()

        if(currentRecipe.isEmpty) {
            return true
        }

        return currentRecipe.get().recipeId != previousRecipe.recipeId
    }

    /**
     * Checks if the output slot is compatible with the output item and has enough space.
     * */
    fun hasSpaceForExport(recipe: R) : Boolean

    /**
     * Consumes the input and exports the output.
     * **Only allowed if the input is present and if [hasSpaceForExport] returns true for the recipe returned by [searchForRecipe].**
     * */
    fun execute()
}

class SimpleProcessingRecipeInventoryHandler<R>(
    val onChanged: Runnable,
    val levelSupplier: Supplier<Level>,
    val recipeType: RecipeType<R>,
    size: Int,
    val inputSlots: IntArray
) : ItemStackHandler(size), ProcessingRecipeInventoryHandler<R> where R : Eln2SimpleOutputProcessingLoopRecipe, R : Recipe<SimpleContainer> {
    companion object {
        fun<R> create(
            blockEntity: BlockEntity,
            recipeType: RecipeType<R>,
            size: Int,
            inputSlots: IntArray = intArrayOf(INPUT_SLOT)
        ) where R : Eln2SimpleOutputProcessingLoopRecipe, R : Recipe<SimpleContainer> =
            SimpleProcessingRecipeInventoryHandler<R>(
                blockEntity::setChanged,
                { blockEntity.level ?: error(DEBUGGER_BREAK("Level null in block entity simple processing inventory handler")) },
                recipeType, size, inputSlots
            )
    }

    private var inputChanged = false

    override fun wasInputChanged() : Boolean {
        val result = inputChanged
        inputChanged = false
        return result
    }

    /**
     * Prevents inserting items into the output slot.
     * Filters input items by [isItemValid].
     * */
    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
        if(slot == OUTPUT_SLOT) {
            return stack
        }

        return super.insertItem(slot, stack, simulate)
    }

    override fun hasSpaceForExport(recipe: R) = super.insertItem(OUTPUT_SLOT, recipe.output, true).isEmpty

    override fun execute() {
        val recipeOp = searchForRecipe()

        if(recipeOp.isEmpty) {
            error(DEBUGGER_BREAK("Cannot export processing result: recipe not valid for input"))
        }

        val recipe = recipeOp.get()

        check(super.extractItem(INPUT_SLOT, 1, false).count == 1) {
            DEBUGGER_BREAK("Did not extract exactly one input item")
        }

        check(super.insertItem(OUTPUT_SLOT, recipe.output.copy(), false).isEmpty) {
            DEBUGGER_BREAK("Could not insert all output items")
        }
    }

    override fun searchForRecipe(): Optional<R> {
        val stack = getStackInSlot(INPUT_SLOT)

        if(stack.isEmpty) {
            return Optional.empty<R>()
        }

        val level = levelSupplier.get()

        return level.recipeManager.getRecipeFor(
            recipeType,
            this.bindToSimpleContainer(),
            level
        )
    }

    override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
        return if(slot == INPUT_SLOT) {
            val copy = this.bindToSimpleContainer()
            copy.setItem(slot, stack)
            return levelSupplier.get().recipeExists(recipeType, copy)
        }
        else {
            true
        }
    }

    override fun onContentsChanged(slot: Int) {
        if(inputSlots.contains(slot)) {
            inputChanged = true
        }

        onChanged.run()
    }

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
        if(slot == INPUT_SLOT) {
            return ItemStack.EMPTY // prevents automation from extracting input
        }

        return super.extractItem(slot, amount, simulate)
    }
}

/**
 * Server tick for a machine that uses a [ProcessingDevice] and applies a [DirectSimpleProcessingRecipe] or [CatalyzedSimpleProcessingRecipe].
 * */
@ServerOnly
class ProcessingRecipeLoop<R : Eln2ProcessingLoopRecipe>(val onChanged: Runnable) {
    companion object {
        private const val IS_WORKING = "hasRecipe"
        private const val TIME_PROGRESS = "timeProgress"

        fun<R : Eln2ProcessingLoopRecipe> create(blockEntity: BlockEntity) = ProcessingRecipeLoop<R> {
            blockEntity.setChanged()
        }
    }

    class Operation<R : Eln2ProcessingLoopRecipe>(val recipe: R) {
        var timeProgress = 0.0
    }

    var operation: Operation<R>? = null
    var savedProgress: Double? = null // Level not available in [load] for block entities, we do the trick the cell block entity does.

    enum class InsufficientTierBehavior {
        /**
         * Keeps [ProcessingDevice.isActive] set to true, even if the tier is insufficient.
         * */
        KeepDeviceActive,
        /**
         * Sets [ProcessingDevice.isActive] to false.
         * */
        Shutdown
    }

    var insufficientTierBehavior = InsufficientTierBehavior.KeepDeviceActive

    enum class TickResult {
        /**
         * Returned when there is nothing to do.
         * */
        NoRecipe,
        /**
         * Returned when the tier of the machine is insufficient.
         * */
        InsufficientTier,
        /**
         * Returned when the recipe was advanced successfully, or was found, and it is compatible by tier.
         * */
        Advance,
        /**
         * Returned when the operation has finished.
         * */
        Finished,
        /**
         * Returned when the operation could progress, but there isn't enough space to export.
         * */
        Halted,
        /**
         * Returned when the input items have changed, and the current [operation] is invalid.
         * */
        ResetRecipe
    }

    /**
     * The last progress made by [tick].
     * **The value is only meaningful if [tick] returned [TickResult.Advance].
     * */
    var lastProgress: Double = 0.0

    /**
     * If [device] is [TieredProcessingDevice] and [recipe] is [Eln2TieredRecipe], check whether the device's tier is larger than or equal to the recipe tier.
     * Otherwise, defaults to true.
     * */
    fun isRecipeCompatibleByTier(device: ProcessingDevice, recipe: R) : Boolean {
        if(device is TieredProcessingDevice && recipe is Eln2TieredRecipe) {
            return device.tier >= recipe.tier
        }

        return true
    }

    /**
     * Advances the processing, if the inventory is eligible for operation.
     * Calls [BlockEntity.setChanged] if the NBT needs to be serialized.
     * */
    fun tick(device: ProcessingDevice, inventoryHandler: ProcessingRecipeInventoryHandler<R>) : TickResult {
        val processingSpeed = device.processingSpeed

        if(operation == null) {
            lastProgress = 0.0

            val recipe = inventoryHandler.searchForRecipe() // Should be fast

            if(recipe.isPresent) {
                val isCompatible = isRecipeCompatibleByTier(device, recipe.get())

                device.isActive = isCompatible || insufficientTierBehavior == InsufficientTierBehavior.KeepDeviceActive

                operation = Operation(recipe.get())

                if(savedProgress != null) {
                    operation!!.timeProgress = savedProgress!!
                    savedProgress = null
                }

                onChanged.run()

                return if(isCompatible) TickResult.Advance else TickResult.InsufficientTier
            }
            else {
                device.isActive = false
                return TickResult.NoRecipe
            }
        }
        else {
            val op = operation!!

            // Check if input changed, and reset operation if the recipe is different:
            if(inventoryHandler.wasRecipeChanged(op.recipe)) {
                operation = null
                onChanged.run()
                lastProgress = 0.0
                return TickResult.ResetRecipe
            }
            else {
                if(isRecipeCompatibleByTier(device, op.recipe)) {
                    // Progress if we have space for the output.
                    // If we don't, we just wait with the current recipe.
                    lastProgress = (op.timeProgress / op.recipe.duration).coerceIn(0.0, 1.0)

                    if(inventoryHandler.hasSpaceForExport(op.recipe)) {
                        device.isActive = true
                        op.timeProgress += processingSpeed * (1.0 / 20.0)
                        op.timeProgress = op.timeProgress.coerceIn(0.0, op.recipe.duration)

                        var finished = false
                        if(op.timeProgress == op.recipe.duration) {
                            // Finish processing:
                            inventoryHandler.execute()
                            operation = null
                            finished = true
                        }

                        onChanged.run()

                        return if(finished) TickResult.Finished else TickResult.Advance
                    }
                    else {
                        device.isActive = false
                        return TickResult.Halted
                    }
                }
                else {
                    device.isActive = insufficientTierBehavior == InsufficientTierBehavior.KeepDeviceActive
                    return TickResult.InsufficientTier
                }
            }
        }
    }

    fun saveAdditional(pTag: CompoundTag) {
        pTag.putBoolean(IS_WORKING, operation != null)

        if(operation != null) {
            pTag.putDouble(TIME_PROGRESS, operation!!.timeProgress)
        }
    }

    fun load(pTag: CompoundTag) {
        val isWorking = pTag.getBoolean(IS_WORKING)

        if(isWorking) {
            savedProgress = pTag.getDouble(TIME_PROGRESS)
        }
    }
}
