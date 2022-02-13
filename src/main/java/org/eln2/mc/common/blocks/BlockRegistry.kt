@file:Suppress("unused") // Because block variables here would be suggested for deletion.

package org.eln2.mc.common.blocks

import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.cell.GroundCellBlock
import org.eln2.mc.common.blocks.cell.ResistorCellBlock
import org.eln2.mc.common.blocks.cell.VoltageSourceCellBlock
import org.eln2.mc.common.blocks.cell.WireCellBlock

object BlockRegistry {
    private val BLOCK_REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, Eln2.MODID)
    private val BLOCK_ITEM_REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Eln2.MODID)
    private val BLOCK_ENTITY_REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, Eln2.MODID)

    fun setup(bus : IEventBus) {
        BLOCK_REGISTRY.register(bus)
        BLOCK_ITEM_REGISTRY.register(bus)
        BLOCK_ENTITY_REGISTRY.register(bus)
        Eln2.LOGGER.info("Prepared block, block item and block entity registry.")
    }

    val CELL_BLOCK_ENTITY: RegistryObject<BlockEntityType<CellTileEntity>> = BLOCK_ENTITY_REGISTRY.register("cell"){
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") // Thanks, Minecraft for the high quality code.
        BlockEntityType.Builder.of(::CellTileEntity).build(null)
    }

    class CellBlockRegistryItem(
        val name : String,
        val block : RegistryObject<CellBlockBase>,
        val item : RegistryObject<BlockItem>
    )

    private fun registerCellBlock(name : String, supplier : () -> CellBlockBase) : CellBlockRegistryItem{
        val block = BLOCK_REGISTRY.register(name) { supplier() }
        val item = BLOCK_ITEM_REGISTRY.register(name) { BlockItem(block.get(), Item.Properties()) }

        return CellBlockRegistryItem(name, block, item)
    }

    val RESISTOR_CELL = registerCellBlock("resistor") { ResistorCellBlock() }
    val WIRE_CELL = registerCellBlock("wire") { WireCellBlock() }
    val VOLTAGE_SOURCE_CELL = registerCellBlock("voltage_source") { VoltageSourceCellBlock() }
    val GROUND_CELL = registerCellBlock("ground") { GroundCellBlock() }
}
