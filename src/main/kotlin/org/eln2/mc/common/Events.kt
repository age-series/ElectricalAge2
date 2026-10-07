@file:Suppress("unused")

package org.eln2.mc.common

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.data.loot.LootTableProvider
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.client.event.RegisterColorHandlersEvent
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
import net.minecraftforge.data.event.GatherDataEvent
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.event.level.ChunkWatchEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.SECOND
import org.ageseries.libage.data.classify
import org.ageseries.libage.utils.Stopwatch
import org.ageseries.libage.utils.putUnique
import org.eln2.mc.*
import org.eln2.mc.client.render.DebugVisualizer
import org.eln2.mc.client.dynamicLight.DynamicLightManager
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.ModEvents.registerItemColors
import org.eln2.mc.common.blocks.BlockRegistry
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntityLevelRendererProvider
import org.eln2.mc.common.cells.foundation.CellGraphManager
import org.eln2.mc.common.cells.foundation.ServerPhase
import org.eln2.mc.common.cells.foundation.SimulationExecutionSubgraph
import org.eln2.mc.common.content.*
import org.eln2.mc.common.content.fluid.BucketFluidInteraction
import org.eln2.mc.common.content.fluid.FluidPipeNetworkManager
import org.eln2.mc.common.content.modules.ContentManager
import org.eln2.mc.common.content.modules.Eln2Ingredients
import org.eln2.mc.common.content.modules.Eln2ForgeFluids
import org.eln2.mc.common.content.processing.LeadChamberExecutionManager
import org.eln2.mc.common.content.processing.TreeExtractionManager
import org.eln2.mc.common.events.Scheduler
import org.eln2.mc.common.events.schedulePost
import org.eln2.mc.common.fluids.foundation.FluidTransformationManager
import org.eln2.mc.common.fluids.ForgeFluidRegistry
import org.eln2.mc.common.fluids.foundation.PhysicalFluidManager
import org.eln2.mc.common.fluids.foundation.appendThermalFluidTooltip
import org.eln2.mc.common.grids.GridCollisions
import org.eln2.mc.common.grids.GridConnectionManagerClient
import org.eln2.mc.common.grids.GridConnectionManagerServer
import org.eln2.mc.common.network.serverToClient.BulkMessages
import org.eln2.mc.common.network.Networking
import org.eln2.mc.common.parts.PartRegistry
import org.eln2.mc.common.specs.foundation.SpecPlacementOverlayServer
import org.eln2.mc.extensions.formatted
import java.util.function.Supplier

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object ModEvents {
    var isFullyLoaded = false
        private set

    @SubscribeEvent @JvmStatic
    fun registerBlockEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(
            BlockRegistry.MULTIPART_BLOCK_ENTITY.get(),
            MultipartBlockEntityLevelRendererProvider()
        )

        ContentManager.registerBlockEntityRenderers(event)
    }

    private fun createTintLookupTable(layers: Set<Pair<Int, MyColor>>) : Int2IntMap {
        val lookupTable = Int2IntOpenHashMap().apply {
            defaultReturnValue(MyColor.WHITE.data)
        }

        layers.forEach { (tintIdx, color) ->
            lookupTable.putUnique(tintIdx, color.data) {
                DEBUGGER_BREAK("Duplicate color layer $tintIdx")
            }
        }

        lookupTable.trim()

        return lookupTable
    }

    /**
     * Used by [registerItemColors].
     * */
    interface ItemAndTint : Supplier<Item> {
        val tint: MyColor
    }

    @SubscribeEvent @JvmStatic
    fun registerItemColors(event: RegisterColorHandlersEvent.Item) {
        ContentManager.ITEM_TINT_FOR_REGISTRATION.map.forEach { (itemSupplier, layers) ->
            val item = itemSupplier.get()
            val lookupTable = createTintLookupTable(layers)

            event.register({ _, pTintIndex ->
                lookupTable.get(pTintIndex)
            }, item)
        }

        ForgeFluidRegistry.FORGE_FLUID_BUCKETS.entries.forEach { bucketEntry ->
            val item = bucketEntry.get() as BucketItem

            /**
             * Tint Index:
             * - `0` is the bucket
             * - `1` is the fluid
             * */
            event.register({ _, tintIndex ->
                if (tintIndex == 1) {
                    return@register IClientFluidTypeExtensions.of(item.fluid).tintColor
                }

                return@register MyColor.WHITE.data
            }, item)
        }

        Eln2ForgeFluids.CHEMICAL_BOTTLES_FOR_RESOLVE_AND_DATAGEN.forEach { (_, itemRegistryItem) ->
            val item = itemRegistryItem.bottleItem.get()

            event.register({ _, tintIndex ->
                if(tintIndex == 1) {
                    itemRegistryItem.tintColor.data
                }
                else {
                    MyColor.WHITE.data
                }
            }, item)
        }

        ContentManager.ITEMS_FOR_TINT_ON_LAYER0.forEach { obj ->
            val tint = obj.tint
            val item = obj.get()

            event.register({ _, tintIndex ->
                if(tintIndex == 0) {
                    tint.data
                }
                else {
                    MyColor.WHITE.data
                }
            }, item)
        }
    }

    @SubscribeEvent @JvmStatic
    fun registerBlockColors(event: RegisterColorHandlersEvent.Block) {
        ContentManager.BLOCK_TINT_FOR_REGISTRATION.map.forEach { (blockSupplier, layers) ->
            val block = blockSupplier.get()
            val lookupTable = createTintLookupTable(layers)

            event.register({ _, _, _, pTintIndex ->
                lookupTable.get(pTintIndex)
            }, block)
        }
    }

    @SubscribeEvent @JvmStatic
    fun loadCompletedEvent(event: FMLLoadCompleteEvent) {
        event.enqueueWork {
            isFullyLoaded = true
            BlockRegistry.finalize()
            PartRegistry.finalize()

            if(ELN2_DEBUG) {
                LOG.warn("ELN2 is running in debug mode, simulation performance will be affected!")
            }
        }
    }

    @SubscribeEvent @JvmStatic
    fun onGatherData(event: GatherDataEvent) {
        val generator = event.generator
        val output = generator.packOutput
        val existingFileHelper = event.existingFileHelper
        val lookupProvider = event.lookupProvider

        generator.addProvider(
            event.includeServer(),
            LootTableProvider(
                output,
                emptySet(), // What?
                listOf(
                    LootTableProvider.SubProviderEntry(
                        ::Eln2BlockSelfDropLootDatagen,
                        LootContextParamSets.BLOCK
                    ),
                    LootTableProvider.SubProviderEntry(
                        ::Eln2BlockOreDropLootDatagen,
                        LootContextParamSets.BLOCK
                    )
                )
            )
        )

        generator.addProvider(
            event.includeServer(),
            Eln2BlockTagsDatagen(output, lookupProvider, existingFileHelper)
        )

        generator.addProvider(
            event.includeServer(),
            Eln2ItemTagsDatagen(output, lookupProvider, existingFileHelper)
        )

        generator.addProvider(
            event.includeClient(),
            Eln2BlockStateProviderDatagen(output, existingFileHelper)
        )

        generator.addProvider(
            event.includeServer(),
            Eln2RecipeProviderDatagen(output)
        )

        generator.addProvider(
            event.includeClient(),
            Eln2ItemModelProviderDatagen(output, existingFileHelper)
        )
    }
}

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
object ForgeEvents {
    // To remove these and add better inst
    private val upsAveragingList = AveragingList(100)
    private val tickTimeAveragingList = AveragingList(100)
    private var lastLog = 0

    // total reset every tick
    private val lastTickStopwatch = Stopwatch()

    val timeSinceLastTick get() = lastTickStopwatch.total

    private fun forEachGraphManager(user: (CellGraphManager) -> Unit) {
        ServerLifecycleHooks.getCurrentServer().allLevels.forEach {
            val graphManager = CellGraphManager.getFor(it)
            user(graphManager)
        }
    }

    @SubscribeEvent @JvmStatic
    fun onAddReloadListeners(event: AddReloadListenerEvent) {
        event.addListener(PhysicalFluidManager)
        event.addListener(FluidTransformationManager)
        event.addListener(TreeExtractionManager)
    }

    @SubscribeEvent @JvmStatic
    fun onFurnaceFuelBurnTime(event: FurnaceFuelBurnTimeEvent) {
        when (event.itemStack.item) {
            Eln2Ingredients.COKE.get() -> event.burnTime = 3200
            Eln2Ingredients.COKE_DUST.get() -> event.burnTime = 3200
        }
    }

    @SubscribeEvent @JvmStatic
    fun onItemTooltip(event: ItemTooltipEvent) {
        val item = event.itemStack.item

        if (item is BucketItem) {
            appendThermalFluidTooltip(item.fluid, event.toolTip)

            if (ForgeRegistries.ITEMS.getKey(item)?.namespace == MODID) {
                event.toolTip.add(Component.translatable("tooltip.eln2.fluid_container.deposit").withStyle(ChatFormatting.GRAY))
        }
    }

        if (item == Items.BUCKET || item == Items.GLASS_BOTTLE) {
            event.toolTip.add(Component.translatable("tooltip.eln2.fluid_container.load").withStyle(ChatFormatting.GRAY))
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @JvmStatic
    fun onBucketRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        BucketFluidInteraction.onRightClickBlock(event)
    }

    @SubscribeEvent @JvmStatic
    fun onServerStarting(event: ServerStartingEvent) {
        LOG.info("Making ELN2 thread pool")
        SimulationExecutionSubgraph.makePool()
    }

    private fun simulationStats() {
        var tickRate = 0.0
        var tickTime = 0.0

        forEachGraphManager {
            tickRate += it.sampleTickRate()
            tickTime += it.totalSpentTime
        }

        upsAveragingList.addSample(tickRate)
        tickTimeAveragingList.addSample(tickTime)

        if(ELN2_LOG_STATS) {
            if (++lastLog == 100) {
                lastLog = 0

                LOG.debug("Simulation rate: ${upsAveragingList.calculate().formatted()} U/S")
                LOG.debug("Simulation time: ${Quantity(tickTimeAveragingList.calculate(), SECOND).classify()}")
            }
        }

        lastTickStopwatch.resetTotal()
    }

    private fun dispatchServerSubscribers(phase: ServerPhase) {
        forEachGraphManager { manager ->
            manager.forEachGraph { graph ->
                graph.serverThreadSubscribers.update(1.0 / 20.0, phase)
            }
        }
    }

    private fun dispatchAllSimulations() {
        forEachGraphManager { manager ->
            manager.forEachGraph { graph ->
                graph.executionGraph.orchestrateFrame()
            }
        }
    }

    private fun awaitAllSimulations() {
        forEachGraphManager { manager ->
            manager.forEachGraph { graph ->
                graph.executionGraph.awaitCompletion()
            }
        }
    }

    private fun advanceServerFrames() {
        forEachGraphManager { manager ->
            manager.forEachGraph { graph ->
                graph.advanceServerFrame()
            }
        }
    }

    /**
     * Orchestrates all per-tick work.
     * The order of the calls is important here.
     * */
    @SubscribeEvent @JvmStatic
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if(event.phase == TickEvent.Phase.START) {
            Scheduler.onServerTick(event)

            LeadChamberExecutionManager.dispatch()

            // Schedule the simulations after those subscribers ran, so they don't get a torn frame:
            dispatchServerSubscribers(ServerPhase.Start)
            dispatchServerSubscribers(ServerPhase.AfterStart1)
            dispatchServerSubscribers(ServerPhase.AfterStart2)
            dispatchAllSimulations()
        }
        else {
            // Dispatch with high priority:
            Scheduler.onServerTick(event)

            simulationStats()

            // The order of these 3 doesn't matter:
            GhostLightServer.applyChanges()
            ScrewdriverItem.Scroll.tickCooldowns()
            WindSystem.update()

            // Await simulation completion, dispatch those subscribers, clear simulation flags, then flush messages:
            awaitAllSimulations()
            dispatchServerSubscribers(ServerPhase.End)
            dispatchServerSubscribers(ServerPhase.AfterEnd)
            advanceServerFrames()
            BulkMessages.flush()

            FluidPipeNetworkManager.update()
        }
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerWatch(event: ChunkWatchEvent.Watch) {
        GhostLightServer.playerWatch(event.level, event.player, event.pos)
        GridConnectionManagerServer.playerWatch(event.level, event.player, event.pos)
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerUnwatch(event: ChunkWatchEvent.UnWatch) {
        GhostLightServer.playerUnwatch(event.level, event.player, event.pos)
        GridConnectionManagerServer.playerUnwatch(event.level, event.player, event.pos)
    }

    @SubscribeEvent @JvmStatic
    fun onServerStopping(event: ServerStoppingEvent) {
        event.server.allLevels.forEach {
            LOG.info("Stopping simulations for $it")
            CellGraphManager.getFor(it).serverStop()
        }

        GhostLightServer.clear()
        WindSystem.clear()

        GridConnectionManagerServer.clear()
        SpecPlacementOverlayServer.clear()

        FluidPipeNetworkManager.clear()
        DrillItem.clearAll()
        FlashlightItem.clearAll()
    }

    private fun scheduleWorldTrackingEventServer(event: BlockEvent, handler: (ServerLevel, BlockPos) -> Unit) {
        if(event.level.isClientSide || event.isCanceled) {
            return
        }

        schedulePost(0) {
            if(!event.isCanceled) {
                handler(event.level as ServerLevel, event.pos)
            }
        }
    }

    @SubscribeEvent @JvmStatic
    fun onBlockBreakEvent(event: BlockEvent.BreakEvent) {
        scheduleWorldTrackingEventServer(event, GhostLightServer::handleBlockEvent)
        scheduleWorldTrackingEventServer(event, WindSystem::handleBlockEvent)
    }

    @SubscribeEvent @JvmStatic
    fun onEntityPlaceEvent(event: BlockEvent.EntityPlaceEvent) { // not called client side? Done for the clients in MixinBlockPlaceContext
        if(event.level is Level && GridCollisions.intersectsPlacementBlock(event.level as Level, event.pos, event.state)) {
            event.isCanceled = true
        }

        scheduleWorldTrackingEventServer(event, GhostLightServer::handleBlockEvent)
        scheduleWorldTrackingEventServer(event, WindSystem::handleBlockEvent)
    }

    @SubscribeEvent @JvmStatic
    fun onClientLevelClosed(event: LevelEvent.Unload) {
        if(event.level.isClientSide) {
            GhostLightHackClient.clear()
            GridConnectionManagerClient.clear()
            DebugVisualizer.clear()
            DrillPowerMessage.reset()
            FlashlightPowerMessage.reset()
            FlashlightItem.clearClient()
            DynamicLightManager.clear()
        }
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        if (!event.player.level().isClientSide) {
            val player = event.player as? ServerPlayer ?: return

            PlayerPowerManager.tick(player)

            DrillItem.onPlayerTickEnd(player)
            FlashlightItem.onPlayerTickEnd(player)
        }
    }

    @SubscribeEvent @JvmStatic
    fun onBreakSpeed(event: PlayerEvent.BreakSpeed) {
        DrillItem.onBreakSpeed(event)
    }

    @SubscribeEvent @JvmStatic
    fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        if (!event.level.isClientSide) {
            DrillItem.onLeftClickBlock(event)
        }
    }

    @SubscribeEvent @JvmStatic
    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        if (!event.level.isClientSide) {
            DrillItem.onBlockBreak(event)
        }
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        if (!event.entity.level().isClientSide) {
            val player = event.entity as? ServerPlayer ?: return
            PlayerPowerManager.clear(player)
            DrillItem.clearPlayer(player.uuid)
            FlashlightItem.clearPlayer(player.uuid)
        }
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        if (!event.entity.level().isClientSide) {
            val player = event.entity as? ServerPlayer ?: return
            PlayerPowerManager.clear(player)
            DrillItem.clearPlayer(player.uuid)
            FlashlightItem.clearPlayer(player.uuid)
        }
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        if (!event.entity.level().isClientSide) {
            val player = event.entity as? ServerPlayer ?: return
            PlayerPowerManager.clear(player)
            DrillItem.clearPlayer(player.uuid)
            FlashlightItem.clearPlayer(player.uuid)
            Networking.send(DrillPowerMessage(0.0f), player)
        }
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerClone(event: PlayerEvent.Clone) {
        if (!event.entity.level().isClientSide) {
            val player = event.entity as? ServerPlayer ?: return
            PlayerPowerManager.clear(player)
            DrillItem.clearPlayer(player.uuid)
            FlashlightItem.clearPlayer(player.uuid)
            Networking.send(DrillPowerMessage(0.0f), player)
        }
    }

    @SubscribeEvent @JvmStatic
    fun onPlayerChangedDimension(event: PlayerEvent.PlayerChangedDimensionEvent) {
        if (!event.entity.level().isClientSide) {
            val player = event.entity as? ServerPlayer ?: return
            PlayerPowerManager.clear(player)
            DrillItem.clearPlayer(player.uuid)
            FlashlightItem.clearPlayer(player.uuid)
            Networking.send(DrillPowerMessage(0.0f), player)
        }
    }
}
