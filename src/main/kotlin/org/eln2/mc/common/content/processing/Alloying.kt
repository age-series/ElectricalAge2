@file:Suppress("unused")

package org.eln2.mc.common.content.processing

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.CriterionTriggerInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.data.recipes.FinishedRecipe
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraftforge.common.ForgeHooks
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler
import net.minecraftforge.registries.ForgeRegistries
import org.eln2.mc.LOG
import org.eln2.mc.MODID
import org.eln2.mc.ServerOnly
import org.eln2.mc.client.screens.ProgressSupplierMenu
import org.eln2.mc.common.containers.ContainerHelper
import org.eln2.mc.common.containers.SlotItemHandlerWithPlacePredicate
import org.eln2.mc.common.content.modules.Eln2Processing
import org.eln2.mc.common.recipes.RecipeRegistry
import org.eln2.mc.common.recipes.foundation.*
import org.eln2.mc.extensions.bindToList
import org.eln2.mc.extensions.constructMenuHelper2
import org.eln2.mc.extensions.eln2Unlock
import org.eln2.mc.extensions.getInt
import org.eln2.mc.integration.ComponentDisplay
import org.eln2.mc.integration.ComponentDisplayList
import java.util.function.Consumer

private const val ALLOYING_INPUT_SLOT_COUNT = 4

class AlloyingRecipe(
    override val recipeSerializer: Serializer,
    override val recipeId: ResourceLocation,
    val inputItems: Eln2WeightedItemRecipeRequirements,
    val output: ItemStack,
    val duration: Int,
    override val tier: Int
) : Eln2CustomRecipe<AlloyingRecipe>, Eln2NonStandardRecipe, Eln2TieredRecipe {
    override fun matches(pContainer: SimpleContainer, pLevel: Level): Boolean {
        val items = pContainer.bindToList()
        return items.applyRecipeWeighted(inputItems, true)
    }

    class Builder(val recipe: RecipeType<AlloyingRecipe>) {
        var inputItems: Eln2WeightedItemRecipeRequirements = Eln2WeightedItemRecipeRequirements(emptyList())
        var output: ItemStack = ItemStack.EMPTY
        var duration: Int = 200
        var tier: Int = 0
        val advancement: Advancement.Builder = Advancement.Builder.advancement()

        fun withInput(ingredient: Eln2WeightedItemIngredient): Builder {
            inputItems = Eln2WeightedItemRecipeRequirements(
                inputItems.requirements + Eln2WeightedItemRecipeRequirement(
                    listOf(ingredient), ingredient.value
                )
            )
            return this
        }

        fun withInput(ingredient: Eln2WeightedItemIngredient, requiredValue: Int): Builder {
            inputItems = Eln2WeightedItemRecipeRequirements(
                inputItems.requirements + Eln2WeightedItemRecipeRequirement(
                    listOf(ingredient), requiredValue
                )
            )
            return this
        }

        fun withOutput(output: ItemStack): Builder {
            this.output = output
            return this
        }

        fun withDuration(duration: Int): Builder {
            this.duration = duration
            return this
        }

        fun withTier(tier: Int): Builder {
            this.tier = tier
            return this
        }

        fun unlockedBy(pCriterionName: String, pCriterionTrigger: CriterionTriggerInstance): Builder {
            advancement.addCriterion(pCriterionName, pCriterionTrigger)
            return this
        }

        fun save(consumer: Consumer<FinishedRecipe?>, id: ResourceLocation) {
            check(inputItems.requirements.isNotEmpty()) { "Input items for alloying recipe cannot be empty" }
            check(!output.isEmpty) { "Output for alloying recipe cannot be empty" }
            if (advancement.criteria.isEmpty()) {
                LOG.error("No criterion for alloying recipe $id")
            } else {
                advancement.eln2Unlock(id)
            }
            consumer.accept(Result(this, id))
        }

        class Result(val parent: Builder, val recipeId: ResourceLocation) : Eln2FinishedRecipe {
            override fun serializeRecipeData(json: JsonObject) {
                json.add("inputItems", JsonArray().also { arr ->
                    parent.inputItems.requirements.forEach { req ->
                        arr.add(JsonObject().also { reqObj ->
                            reqObj.addProperty("requiredValue", req.requiredValue)
                            reqObj.add("options", JsonArray().also { opts ->
                                req.options.forEach { opt ->
                                    opts.add(JsonObject().also { optObj ->
                                        optObj.add("ingredient", opt.ingredient.toJson())
                                        optObj.addProperty("value", opt.value)
                                    })
                                }
                            })
                        })
                    }
                })

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

    class Serializer(override val recipeType: RecipeType<AlloyingRecipe>) : Eln2RecipeSerializer<AlloyingRecipe> {
        override fun fromJson(pRecipeId: ResourceLocation, pSerializedRecipe: JsonObject): AlloyingRecipe {
            val inputItems = Eln2WeightedItemRecipeRequirements.fromJson(pSerializedRecipe.get("inputItems"))
            val output = ShapedRecipe.itemStackFromJson(pSerializedRecipe.getAsJsonObject("result"))
            val duration = pSerializedRecipe.get("duration").asInt
            val tier = pSerializedRecipe.getInt("tier", 0)
            return AlloyingRecipe(this, pRecipeId, inputItems, output, duration, tier)
        }

        override fun fromNetwork(pRecipeId: ResourceLocation, pBuffer: FriendlyByteBuf): AlloyingRecipe {
            val inputItems = Eln2WeightedItemRecipeRequirements.fromNetwork(pBuffer)
            val output = pBuffer.readItem()
            val duration = pBuffer.readInt()
            val tier = pBuffer.readInt()
            return AlloyingRecipe(this, pRecipeId, inputItems, output, duration, tier)
        }

        override fun toNetwork(pBuffer: FriendlyByteBuf, pRecipe: AlloyingRecipe) {
            pRecipe.inputItems.toNetwork(pBuffer)
            pBuffer.writeItem(pRecipe.output)
            pBuffer.writeInt(pRecipe.duration)
            pBuffer.writeInt(pRecipe.tier)
        }
    }
}

class AlloyingSmelterBlock : HorizontalDirectionalBlock(Properties.of().strength(3.5f).requiresCorrectToolForDrops()), EntityBlock {
    companion object {
        val LIT: BooleanProperty = BooleanProperty.create("lit")
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false))
    }

    override fun createBlockStateDefinition(pBuilder: StateDefinition.Builder<Block, BlockState>) {
        pBuilder.add(FACING, LIT)
    }

    override fun newBlockEntity(pPos: BlockPos, pState: BlockState): BlockEntity = AlloyingSmelterBlockEntity(pPos, pState)

    override fun <T : BlockEntity?> getTicker(pLevel: Level, pState: BlockState, pBlockEntityType: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (pLevel.isClientSide) return null
        return BlockEntityTicker(AlloyingSmelterBlockEntity::tickServer)
    }

    @Deprecated("Deprecated in Java")
    override fun use(pState: BlockState, pLevel: Level, pPos: BlockPos, pPlayer: Player, pHand: InteractionHand, pHit: BlockHitResult): InteractionResult {
        return pLevel.constructMenuHelper2<AlloyingSmelterBlockEntity>(pPos, pPlayer, Component.translatable("menu.$MODID.alloying_smelter"), ::AlloyingSmelterMenu)
    }

    override fun animateTick(pState: BlockState, pLevel: Level, pPos: BlockPos, pRandom: RandomSource) {
        if (pState.getValue(LIT)) {
            val d0 = pPos.x.toDouble() + 0.5
            val d1 = pPos.y.toDouble()
            val d2 = pPos.z.toDouble() + 0.5
            if (pRandom.nextDouble() < 0.5) {
                pLevel.playLocalSound(d0, d1, d2, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0f, 1.0f, false)
            }
            val direction = pState.getValue(FACING).counterClockWise
            repeat(4) {
                val d4 = pRandom.nextDouble() * 0.6 - 0.3
                val d5 = if (direction.axis === Direction.Axis.X) direction.stepX.toDouble() * 0.52 else d4
                val d6 = pRandom.nextDouble() * 6.0 / 16.0
                val d7 = if (direction.axis === Direction.Axis.Z) direction.stepZ.toDouble() * 0.52 else d4
                pLevel.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + d6, d2 + d7, 0.0, 0.0, 0.0)
                pLevel.addParticle(ParticleTypes.FLAME, d0 + d5, d1 + d6, d2 + d7, 0.0, 0.0, 0.0)
            }
        }
    }
}

class AlloyingSmelterBlockEntity(pPos: BlockPos, pState: BlockState) :
    BlockEntity(Eln2Processing.ALLOYING_SMELTER_BLOCK_ENTITY.get(), pPos, pState), ComponentDisplay {

    companion object {
        const val INVENTORY = "inventory"
        const val BURN_TIME = "burnTime"
        const val BURN_DURATION = "burnDuration"
        const val PROGRESS_TAG = "progress"
        const val DATA_BURN_TIME = 0
        const val DATA_BURN_DURATION = 1
        const val DATA_PROGRESS = 2
        const val DATA_DURATION = 3
        private const val FUEL_SLOT = ALLOYING_INPUT_SLOT_COUNT
        private const val OUTPUT_SLOT = ALLOYING_INPUT_SLOT_COUNT + 1
        private const val SLOT_COUNT = ALLOYING_INPUT_SLOT_COUNT + 2

        @ServerOnly
        fun tickServer(pLevel: Level?, pPos: BlockPos?, pState: BlockState?, pBlockEntity: BlockEntity?) {
            if (pBlockEntity !is AlloyingSmelterBlockEntity) return
            pBlockEntity.serverTick()
        }
    }

    class InventoryHandler(val blockEntity: AlloyingSmelterBlockEntity) : ItemStackHandler(SLOT_COUNT) {
        private var inputChanged = false
        val inputSlots = 0 until ALLOYING_INPUT_SLOT_COUNT

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack {
            if (slot == OUTPUT_SLOT) return stack
            return super.insertItem(slot, stack, simulate)
        }

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            if (slot == FUEL_SLOT) return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0
            return true
        }

        override fun onContentsChanged(slot: Int) {
            if (slot in inputSlots || slot == FUEL_SLOT) {
                inputChanged = true
            }

            blockEntity.setChanged()
        }

        fun wasInputChanged() : Boolean {
            if(inputChanged) {
                inputChanged = false
                return true
            }

            return false
        }

        fun markInputChanged() {
            inputChanged = true
        }

        fun buildInputContainer(): SimpleContainer {
            val container = SimpleContainer(ALLOYING_INPUT_SLOT_COUNT)
            for (i in 0 until ALLOYING_INPUT_SLOT_COUNT) {
                container.setItem(i, getStackInSlot(i))
            }
            return container
        }

        fun hasSpaceForOutput(stack: ItemStack): Boolean {
            return super.insertItem(OUTPUT_SLOT, stack, true).isEmpty
        }

        fun consumeInputs(recipe: AlloyingRecipe) {
            check(stacks.take(ALLOYING_INPUT_SLOT_COUNT).applyRecipeWeighted(recipe.inputItems, true))
        }

        fun placeOutput(stack: ItemStack) {
            super.insertItem(OUTPUT_SLOT, stack, false)
        }
    }

    class Operation(val recipe: AlloyingRecipe, var progress: Int)

    val inventoryHandler = InventoryHandler(this)
    private var burnTime = 0
    private var burnDuration = 0
    private var operation: Operation? = null
    private var savedOperationData: OperationLoadingData? = null

    val data = SimpleContainerData(4)

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return LazyOptional.of { inventoryHandler }.cast()
        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() { super.invalidateCaps() }

    override fun setLevel(pLevel: Level) {
        super.setLevel(pLevel)

        if (!pLevel.isClientSide && savedOperationData != null) {
            val optional = pLevel.recipeManager.byKey(savedOperationData!!.recipeId)

            if (optional.isPresent && optional.get() is AlloyingRecipe) {
                operation = Operation(optional.get() as AlloyingRecipe, savedOperationData!!.progress)
            }

            savedOperationData = null
        }
    }

    private fun findRecipe(): AlloyingRecipe? {
        val level = level ?: return null
        val container = inventoryHandler.buildInputContainer()
        return level.recipeManager.getRecipeFor(Eln2Processing.ALLOYING_RECIPE, container, level).orElse(null)
    }

    @ServerOnly
    fun serverTick() {
        val level = level
            ?: return

        val wasLit = blockState.getValue(AlloyingSmelterBlock.LIT)

        if (burnTime > 0) {
            burnTime--
            setChanged()
        }

        if (inventoryHandler.wasInputChanged()) {
            val actualRecipe = findRecipe()

            if(actualRecipe == null){
                operation = null
            }
            else {
                if(actualRecipe.recipeId != operation?.recipe?.recipeId) {
                    if (inventoryHandler.hasSpaceForOutput(actualRecipe.output)) {
                        operation = Operation(actualRecipe, 0)
                    }
                }
            }
        }

        if (operation != null) {
            if (burnTime <= 0) {
                val fuelStack = inventoryHandler.getStackInSlot(FUEL_SLOT)
                if (!fuelStack.isEmpty) {
                    val fuelValue = ForgeHooks.getBurnTime(fuelStack, RecipeType.SMELTING)
                    if (fuelValue > 0) {
                        burnDuration = fuelValue
                        burnTime = fuelValue
                        fuelStack.shrink(1)
                        setChanged()
                    }
                }

                if (burnTime <= 0) {
                    this@AlloyingSmelterBlockEntity.operation = null
                    setChanged()
                }
            }
        }

        if (operation != null && burnTime > 0) {
            val operation = operation!!
            operation.progress++
            setChanged()

            if (operation.progress >= operation.recipe.duration) {
                val outputStack = operation.recipe.output.copy()
                inventoryHandler.consumeInputs(operation.recipe)
                inventoryHandler.placeOutput(outputStack)
                inventoryHandler.markInputChanged()
                this@AlloyingSmelterBlockEntity.operation = null
                setChanged()
            }
        }

        if (operation == null) {
            data.set(DATA_PROGRESS, 0)
            data.set(DATA_DURATION, 0)
        }
        else {
            data.set(DATA_PROGRESS, operation!!.progress)
            data.set(DATA_DURATION, operation!!.recipe.duration)
        }

        data.set(DATA_BURN_TIME, burnTime)
        data.set(DATA_BURN_DURATION, burnDuration)

        val isLit = burnTime > 0

        if (wasLit != isLit) {
            level.setBlock(blockPos, blockState.setValue(AlloyingSmelterBlock.LIT, isLit), Block.UPDATE_ALL)
        }
    }

    override fun saveAdditional(pTag: net.minecraft.nbt.CompoundTag) {
        super.saveAdditional(pTag)
        pTag.put(INVENTORY, inventoryHandler.serializeNBT())
        pTag.putInt(BURN_TIME, burnTime)
        pTag.putInt(BURN_DURATION, burnDuration)

        if (operation != null) {
            pTag.putInt(PROGRESS_TAG, operation!!.progress)
            pTag.putString("recipe", operation!!.recipe.recipeId.toString())
        }
    }

    override fun load(pTag: net.minecraft.nbt.CompoundTag) {
        super.load(pTag)
        inventoryHandler.deserializeNBT(pTag.getCompound(INVENTORY))
        burnTime = pTag.getInt(BURN_TIME)
        burnDuration = pTag.getInt(BURN_DURATION)

        if (pTag.contains(PROGRESS_TAG)) {
            savedOperationData = OperationLoadingData(
                ResourceLocation.parse(pTag.getString("recipe")),
                pTag.getInt(PROGRESS_TAG)
            )
        }
    }

    private data class OperationLoadingData(val recipeId: ResourceLocation, val progress: Int)

    override fun submitDisplay(builder: ComponentDisplayList) {
        val op = operation
        if (op != null) builder.progress(op.progress / op.recipe.duration.toDouble())
    }
}

class AlloyingSmelterMenu(
    pContainerId: Int,
    playerInventory: Inventory,
    handler: ItemStackHandler,
    val containerData: SimpleContainerData,
    val access: ContainerLevelAccess,
    val level: Level
) : AbstractContainerMenu(Eln2Processing.ALLOYING_SMELTER_MENU.get(), pContainerId), ProgressSupplierMenu {
    @ServerOnly
    constructor(entity: AlloyingSmelterBlockEntity, id: Int, inventory: Inventory) : this(
        id, inventory, entity.inventoryHandler, entity.data,
        ContainerLevelAccess.create(entity.level!!, entity.blockPos), entity.level!!
    )

    constructor(pContainerId: Int, playerInventory: Inventory) : this(
        pContainerId, playerInventory,
        ItemStackHandler(ALLOYING_INPUT_SLOT_COUNT + 2),
        SimpleContainerData(4), ContainerLevelAccess.NULL, playerInventory.player.level()
    )

    init {
        fun inputSlot(idx: Int, x: Int, y: Int) {
            addSlot(SlotItemHandlerWithPlacePredicate(handler, idx, x, y) { stack ->
                ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) <= 0
            })
        }

        inputSlot(0, 34, 26)
        inputSlot(1, 53, 26)
        inputSlot(2, 34, 45)
        inputSlot(3, 53, 45)

        addSlot(SlotItemHandler(handler, ALLOYING_INPUT_SLOT_COUNT, 86, 57))
        addSlot(SlotItemHandlerWithPlacePredicate(handler, ALLOYING_INPUT_SLOT_COUNT + 1, 122, 35) { false })
        addDataSlots(containerData)
        ContainerHelper.addPlayerGrid(playerInventory, this::addSlot)
    }

    override fun stillValid(pPlayer: Player) = stillValid(access, pPlayer, Eln2Processing.ALLOYING_SMELTER_BLOCK.block.get())

    override fun quickMoveStack(pPlayer: Player, pIndex: Int) = ContainerHelper.quickMove(slots, pPlayer, pIndex)

    override fun getProgressForRender(): Float {
        val duration = containerData.get(AlloyingSmelterBlockEntity.DATA_DURATION)
        if (duration <= 0) return 0.0f
        return containerData.get(AlloyingSmelterBlockEntity.DATA_PROGRESS).toFloat() / duration.toFloat()
    }
}
