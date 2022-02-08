package org.eln2.mc.common.blocks

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eln2.mc.Eln2

object BlockRegistry {
    private val LOGGER : Logger = LogManager.getLogger()

    private val BLOCK_REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, Eln2.MODID)
    private val BLOCK_ITEM_REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Eln2.MODID)
    private val BLOCK_ENTITY_REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, Eln2.MODID)

    fun setup(bus : IEventBus) {
        BLOCK_REGISTRY.register(bus)
        BLOCK_ITEM_REGISTRY.register(bus)
        BLOCK_ENTITY_REGISTRY.register(bus)
        LOGGER.info("Prepared block, block item and block entity registry.")
    }

    const val TEST_CELL_NAME = "test"
    val TEST_CELL_BLOCK = BLOCK_REGISTRY.register(TEST_CELL_NAME) { TestCellBlock() }
    val TEST_CELL_ITEM = BLOCK_ITEM_REGISTRY.register(TEST_CELL_NAME) { BlockItem(TEST_CELL_BLOCK.get(), Item.Properties()) }
    val TEST_CELL_ENTITY = BLOCK_ENTITY_REGISTRY.register(TEST_CELL_NAME){
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") // Thanks, Minecraft for the high quality code.
        BlockEntityType.Builder.of(::CellTileEntity, TEST_CELL_BLOCK.get()).build(null)
    }
}
