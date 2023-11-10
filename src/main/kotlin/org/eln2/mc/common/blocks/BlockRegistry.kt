@file:Suppress("unused") // Because block variables here would be suggested for deletion.

package org.eln2.mc.common.blocks

import net.minecraft.world.item.*
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.MODID
import org.eln2.mc.common.blocks.foundation.*

object BlockRegistry {
    private val BLOCK_REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID)!!
    private val BLOCK_ITEM_REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, MODID)!!
    private val BLOCK_ENTITY_REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID)!!

    fun <T : BlockEntity> blockEntity(
        name: String,
        blockEntitySupplier: BlockEntityType.BlockEntitySupplier<T>,
        blockSupplier: (() -> Block),
    ): RegistryObject<BlockEntityType<T>> {

        return BLOCK_ENTITY_REGISTRY.register(name) {
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") // Thanks, Minecraft for the high quality code.
            BlockEntityType.Builder.of(
                blockEntitySupplier,
                blockSupplier()
            )
                .build(null)
        }
    }

    fun setup(bus: IEventBus) {
        BLOCK_REGISTRY.register(bus)
        BLOCK_ITEM_REGISTRY.register(bus)
        BLOCK_ENTITY_REGISTRY.register(bus)
    }

    /*val CELL_BLOCK_ENTITY: RegistryObject<BlockEntityType<CellBlockEntity>> = BLOCK_ENTITY_REGISTRY.register("cell") {
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        BlockEntityType.Builder.of(::CellBlockEntity).build(null)
    }*/

    val MULTIPART_BLOCK_ENTITY: RegistryObject<BlockEntityType<MultipartBlockEntity>> =
        BLOCK_ENTITY_REGISTRY.register("multipart") {
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") // Thanks, Minecraft for the high quality code.
            BlockEntityType.Builder.of(::MultipartBlockEntity, MULTIPART_BLOCK.block.get()).build(null)
        }

   /* data class CellBlockRegistryItem(
        val name: String,
        val block: RegistryObject<CellBlock>,
        val item: RegistryObject<BlockItem>,
    )
*/
    data class BlockRegistryItem(
        val name: String,
        val block: RegistryObject<Block>,
        val item: RegistryObject<BlockItem>,
    ) {
        val registryName get() = block.id ?: error("Invalid registry name")
    }

   /* private fun registerCellBlock(
        name: String,
        tab: CreativeModeTab? = null,
        supplier: () -> CellBlock,
    ): CellBlockRegistryItem {
        val block = BLOCK_REGISTRY.register(name) { supplier() }
        val item = BLOCK_ITEM_REGISTRY.register(name) {
            BlockItem(
                block.get(),
                Item.Properties().also { *//*TODO where did tabs go?*//* })
        }

        return CellBlockRegistryItem(name, block, item)
    }
*/
    fun block(
        name: String,
        tab: CreativeModeTab? = null,
        supplier: () -> Block,
    ): BlockRegistryItem {
        val block = BLOCK_REGISTRY.register(name) { supplier() }
        val item = BLOCK_ITEM_REGISTRY.register(name) {
            BlockItem(
                block.get(),
                Item.Properties().also { /* TODO where did tabs go? */ }
            )
        }

        return BlockRegistryItem(name, block, item)
    }

    val MULTIPART_BLOCK = block("multipart", tab = null) { MultipartBlock() }
}
