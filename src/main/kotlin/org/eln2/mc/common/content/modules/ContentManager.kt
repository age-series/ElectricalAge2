package org.eln2.mc.common.content.modules

import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraftforge.client.event.EntityRenderersEvent
import org.ageseries.libage.data.MutableSetMapMultiMap
import org.ageseries.libage.utils.addUnique
import org.eln2.mc.ClientOnly
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.LOG
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.ModEvents
import org.eln2.mc.common.content.modules.world.Eln2Ores
import org.eln2.mc.requireIsOnRenderThread
import java.util.function.Supplier

private val obj = Any()

/**
 * Validation layer for a method that executes only once. Used to ensure the content setup methods are called from the right call site, in the right order.
 * - A setup method from [ContentManager] calls methods from [ContentModule] inside the block of [executeInScope].
 * - Methods from [ContentModule] ensure they are being called exactly once, from the right setup method in [ContentManager], and in the correct order relative to other setup methods, with [validate].
 * @param dependencies [ContentRegistrationScope]s [enter] depends on. All of these scopes must have already been executed, otherwise [enter] will result in an error.
 * */
class ContentRegistrationScope(val name: String, val dependencies: List<ContentRegistrationScope> = listOf()) {
    private var thread: Thread? = null
    private var executed = false

    private fun enter() {
        synchronized(obj) {
            dependencies.forEach { scope ->
                check(scope.executed) {
                    "Scope $name depends on ${scope.name}, which never executed!"
                }
            }

            check(thread == null && !executed)
            thread = Thread.currentThread()
        }
    }

    private fun leave() {
        synchronized(obj) {
            check(thread != null && Thread.currentThread() == thread)
            thread = null
            executed = true
        }
    }

    fun executeInScope(action: () -> Unit) {
        try {
            enter()
            action()
            leave()
        }
        catch (t: Throwable) {
            LOG.fatal("Error occurred during execution in $name!")
            throw t
        }
    }

    fun validate() {
        synchronized(obj) {
            check(thread != null && !executed && Thread.currentThread() == thread) {
                "Was not in $name!"
            }
        }
    }
}

/**
 * All `object`s that implement [ContentModule]. They are in the `modules` package.
 * This collection is built in [ContentManager.initialize], which calls [ContentModule.initialize] which causes the singleton to be instanced.
 * The constructor of [ContentModule] then validates the scope and adds itself to the set.
 * */
private val contentModules = HashSet<ContentModule>()

/**
 * Scope of the [ContentManager.initialize] method. The singletons implementing [ContentModule] are constructed in this scope.
 * */
private val initScope = ContentRegistrationScope("initialize")

/**
 * Client-only scope where screens are registered.
 * */
private val setupScreensScope = ContentRegistrationScope(
    "setupScreens",
    listOf(initScope)
)

/**
 * Client-only scope where block entity visualizers are registered in [ContentManager.registerBlockEntityVisualizers].
 * */
private val registerBlockEntityVisualizersScope = ContentRegistrationScope(
    "registerBlockEntityVisualizers",
    listOf(initScope)
)

/**
 * Client-only scope where part visualizers are registered in [ContentManager.registerPartVisualizers].
 * Depends on [registerBlockEntityVisualizersScope].
 * */
private val registerPartVisualizersScope = ContentRegistrationScope(
    "registerPartVisualizers",
    listOf(registerBlockEntityVisualizersScope)
)

/**
 * Client-only scope where spec visualizers are registered in [ContentManager.registerSpecVisualizers].
 * Depends on [registerPartVisualizersScope].
 * */
private val registerSpecVisualizersScope = ContentRegistrationScope(
    "registerSpecVisualizers",
    listOf(registerPartVisualizersScope)
)

/**
 * Client-only scope where BERs are registered.
 * */
private val registerBlockEntityRenderersScope = ContentRegistrationScope(
    "registerBlockEntityRenderers",
    listOf(initScope)
)

/**
 * Client-only scope where render layers (for fluids) are set.
 * */
private val setRenderLayersScope = ContentRegistrationScope(
    "setRenderLayers",
    listOf(initScope)
)

/**
 * Implemented by `object`s that hold fields for the registered blocks, items, block entities, cells, parts, specs, and other things.
 * Methods to register visualizers and block entity renderers are also present. They are called in [ContentManager], with some validation.
 * */
abstract class ContentModule {
    init {
        initScope.validate()

        synchronized(obj) {
            contentModules.addUnique(this) {
                "Duplicate init content module $this!"
            }
        }
    }

    fun initialize() {
        initScope.validate()
        LOG.info("Initialized content module ${this.javaClass}.")
    }

    fun setupScreensModule() {
        setupScreensScope.validate()
        setupScreens()
    }

    protected open fun setupScreens() { }

    fun registerBlockEntityVisualizersModule() {
        registerBlockEntityVisualizersScope.validate()
        registerBlockEntityVisualizers()
    }

    protected open fun registerBlockEntityVisualizers() { }

    fun registerPartVisualizersModule() {
        registerPartVisualizersScope.validate()
        registerPartVisualizers()
    }

    protected open fun registerPartVisualizers() { }

    fun registerSpecVisualizersModule() {
        registerSpecVisualizersScope.validate()
        registerSpecVisualizers()
    }

    protected open fun registerSpecVisualizers() { }

    fun registerBlockEntityRenderersModule(event: EntityRenderersEvent.RegisterRenderers) {
        registerBlockEntityRenderersScope.validate()
        registerBlockEntityRenderers(event)
    }

    protected open fun registerBlockEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) { }

    fun setRenderLayersModule() {
        setRenderLayersScope.validate()
        setRenderLayers()
    }

    protected open fun setRenderLayers() { }
}

/**
 * Event dispatcher for content registration and repository for things that will be included in datagen.
 * It primarily handles initializing [ContentModule]s.
 * Note on datagen: we create some repositories of common things to generate during datagen.
 * We don't hold _everything_ here, see the datagen file to see other sources. For example, [Eln2Ores] has its own repository of blocks to include in loot table generation.
 */
object ContentManager {
    /**
     * Called by external registration helpers.
     * */
    fun requireInit() = initScope.validate()

    /**
     * P.S. This is the only place you have to add new content modules to:
     * */
    fun initialize() = initScope.executeInScope {
        Eln2Tools.initialize()
        Eln2Ingredients.initialize()
        Eln2Wires.initialize()
        Eln2Kinetic.initialize()
        Eln2BasicComponents.initialize()
        Eln2Batteries.initialize()
        Eln2Solar.initialize()
        Eln2InventoryPower.initialize()
        Eln2Lights.initialize()
        Eln2HeatGenerators.initialize()
        Eln2Thermal.initialize()
        Eln2PowerDevices.initialize()
        Eln2Processing.initialize()
        Eln2SteamTurbine.initialize()
        Eln2Signal.initialize()
        Eln2Grid.initialize()
        Eln2ForgeFluids.initialize()
        Eln2Ores.initialize()
        Eln2HeatingElements.initialize()

        LOG.info("Content init completed.")
    }

    @ClientOnly
    fun setupScreens() = setupScreensScope.executeInScope {
        requireIsOnRenderThread()

        contentModules.forEach {
            it.setupScreensModule()
        }

        LOG.info("Client screens completed.")
    }

    @ClientOnly
    fun registerBlockEntityVisualizers() = registerBlockEntityVisualizersScope.executeInScope {
        contentModules.forEach {
            it.registerBlockEntityVisualizersModule()
        }

        LOG.info("Register block entity visualizers completed.")
    }

    @ClientOnly
    fun registerPartVisualizers() = registerPartVisualizersScope.executeInScope {
        contentModules.forEach {
            it.registerPartVisualizersModule()
        }

        LOG.info("Register part visualizers completed.")
    }

    @ClientOnly
    fun registerSpecVisualizers() = registerSpecVisualizersScope.executeInScope {
        contentModules.forEach {
            it.registerSpecVisualizersModule()
        }

        LOG.info("Register spec visualizers completed.")
    }

    @ClientOnly
    fun registerBlockEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) = registerBlockEntityRenderersScope.executeInScope {
        contentModules.forEach {
            it.registerBlockEntityRenderersModule(event)
        }

        LOG.info("Register block entity renderers completed.")
    }

    fun setRenderLayers() = setRenderLayersScope.executeInScope {
        contentModules.forEach {
            it.setRenderLayersModule()
        }

        LOG.info("Set render layers completed.")
    }

    /**
     * Registered by [org.eln2.mc.common.ModEvents.registerItemColors].
     * */
    val ITEM_TINT_FOR_REGISTRATION = MutableSetMapMultiMap<Supplier<Item>, Pair<Int, MyColor>>()

    fun<I : Item, T : Supplier<I>> T.withItemTint(tintIndex: Int, color: MyColor) : T {
        initScope.validate()

        @Suppress("UNCHECKED_CAST")
        ITEM_TINT_FOR_REGISTRATION[this as Supplier<Item>].addUnique(Pair(tintIndex, color)) {
            DEBUGGER_BREAK("Duplicate item tint registration for $this $tintIndex $color")
        }

        return this
    }

    /**
     * Registered by [org.eln2.mc.common.ModEvents.registerBlockColors].
     * */
    val BLOCK_TINT_FOR_REGISTRATION = MutableSetMapMultiMap<Supplier<Block>, Pair<Int, MyColor>>()

    fun<B : Block, T : Supplier<B>> T.withBlockTint(tintIndex: Int, color: MyColor) : T {
        initScope.validate()

        @Suppress("UNCHECKED_CAST")
        BLOCK_TINT_FOR_REGISTRATION[this as Supplier<Block>].addUnique(Pair(tintIndex, color)) {
            DEBUGGER_BREAK("Duplicate block tint registration for $this $tintIndex $color")
        }

        return this
    }

    /**
     * Used by [org.eln2.mc.Eln2BlockSelfDropLootDatagen].
     * */
    val SELF_DROP_BLOCKS_FOR_DATAGEN = LinkedHashSet<Supplier<Block>>()

    fun<B : Block, T : Supplier<B>> T.withSelfDrop() : T{
        initScope.validate()

        @Suppress("UNCHECKED_CAST")
        SELF_DROP_BLOCKS_FOR_DATAGEN.addUnique(this as Supplier<Block>) {
            DEBUGGER_BREAK("Duplicate self drop for $this")
        }

        return this
    }

    /**
     * Used by [org.eln2.mc.Eln2BlockTagsDatagen].
     * */
    val BLOCK_TAGS_FOR_DATAGEN = LinkedHashSet<Pair<Supplier<Block>, TagKey<Block>>>()

    fun<T : Supplier<Block>> T.withTagDatagen(tag: TagKey<Block>) : T {
        initScope.validate()

        BLOCK_TAGS_FOR_DATAGEN.addUnique(Pair(this, tag)) {
            DEBUGGER_BREAK("Duplicate block tag for $this")
        }

        return this
    }

    /**
     * Used by [org.eln2.mc.Eln2ItemTagsDatagen].
     * */
    val ITEM_TAGS_FOR_DATAGEN = LinkedHashSet<Pair<Supplier<Item>, TagKey<Item>>>()

    /**
     * Reverse lookup: supplier -> tag, populated by [withItemTagDatagen].
     * Used by [tagForItem] during recipe datagen to prefer convention tags over concrete items.
     * */
    val ITEM_TO_TAG_LOOKUP = HashMap<Supplier<Item>, TagKey<Item>>()

    /**
     * Returns the convention [TagKey] for [item], or `null` if none exists.
     * Searches the lookup populated by [withItemTagDatagen] at registration time.
     * */
    fun tagForItem(item: Item): TagKey<Item>? {
        return ITEM_TO_TAG_LOOKUP.entries.firstOrNull { (supplier, _) ->
            supplier.get() === item
        }?.value
    }

    fun<T : Supplier<Item>> T.withItemTagDatagen(tag: TagKey<Item>) : T {
        initScope.validate()

        ITEM_TAGS_FOR_DATAGEN.addUnique(Pair(this, tag)) {
            DEBUGGER_BREAK("Duplicate item tag for $this")
        }

        ITEM_TO_TAG_LOOKUP[this] = tag

        return this
    }

    /**
     * Registers a vanilla item's convention tag in [ITEM_TO_TAG_LOOKUP] without generating tag datagen output.
     * This lets recipe generation prefer the tag over the concrete item for vanilla items.
     * Must be called during init.
     * */
    fun registerVanillaItemTag(item: Item, tag: TagKey<Item>) {
        initScope.validate()
        val supplier = Supplier { item }
        ITEM_TO_TAG_LOOKUP[supplier] = tag
        ITEM_TAGS_FOR_DATAGEN.add(Pair(supplier, tag))
    }

    /**
     * Tint registered in [ModEvents.registerItemColors], on texture index 0.
     * */
    val ITEMS_FOR_TINT_ON_LAYER0 = LinkedHashSet<ModEvents.ItemAndTint>()

    fun addItemForTint(item: ModEvents.ItemAndTint) {
        initScope.validate()

        ITEMS_FOR_TINT_ON_LAYER0.addUnique(item) {
            DEBUGGER_BREAK("Duplicate item for tint $item")
        }
    }
}
