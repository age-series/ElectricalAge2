@file:Suppress("unused") // Because block variables here would be suggested for deletion.

package org.eln2.mc.common.blocks

import net.minecraft.world.item.*
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.eln2.mc.Eln2
import org.eln2.mc.common.blocks.block.DcDcConverterBlock
import org.eln2.mc.common.blocks.cell.*

object BlockRegistry {
    @Suppress("MemberVisibilityCanBePrivate") // Used for block registration and fetching
    val BLOCK_REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, Eln2.MODID)!! // Yeah, if this fails blow up the game
    @Suppress("MemberVisibilityCanBePrivate") // Used for block registration and fetching
    val BLOCK_ITEM_REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Eln2.MODID)!! // Yeah, if this fails blow up the game
    @Suppress("MemberVisibilityCanBePrivate") // Used for block registration and fetching
    val BLOCK_ENTITY_REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, Eln2.MODID)!! // Yeah, if this fails blow up the game

    fun setup(bus : IEventBus) {
        BLOCK_REGISTRY.register(bus)
        BLOCK_ITEM_REGISTRY.register(bus)
        BLOCK_ENTITY_REGISTRY.register(bus)
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

    data class BlockRegistryItem(
        val name: String,
        val block: RegistryObject<Block>,
        val item: RegistryObject<BlockItem>
    )

    private fun registerCellBlock(name : String, tab: CreativeModeTab? = null, supplier : () -> CellBlockBase) : CellBlockRegistryItem{
        val block = BLOCK_REGISTRY.register(name) { supplier() }
        val item = BLOCK_ITEM_REGISTRY.register(name) { BlockItem(block.get(), Item.Properties().also {if (tab != null) it.tab(tab)}) }
        return CellBlockRegistryItem(name, block, item)
    }

    private fun registerBasicBlock(name: String, tab: CreativeModeTab? = null, supplier: () -> Block): BlockRegistryItem {
        val block = BLOCK_REGISTRY.register(name) {supplier()}
        val item = BLOCK_ITEM_REGISTRY.register(name) {BlockItem(block.get(), Item.Properties().also {if(tab != null) it.tab(tab)})}
        return BlockRegistryItem(name, block, item)
    }

    private val eln2Tab: CreativeModeTab = object: CreativeModeTab("Electrical_Age") {
        override fun makeIcon(): ItemStack {
            return ItemStack(VOLTAGE_SOURCE_CELL.item.get().asItem())
        }
    }

    val RESISTOR_CELL = registerCellBlock("resistor", eln2Tab) { ResistorCellBlock() }
    val WIRE_CELL = registerCellBlock("wire", eln2Tab) { WireCellBlock() }
    val VOLTAGE_SOURCE_CELL = registerCellBlock("voltage_source", eln2Tab) { VoltageSourceCellBlock() }
    val GROUND_CELL = registerCellBlock("ground", eln2Tab) { GroundCellBlock() }
    val CAPACITOR_CELL = registerCellBlock("capacitor", eln2Tab) { CapacitorCellBlock() }
    val INDUCTOR_CELL = registerCellBlock("inductor", eln2Tab) { InductorCellBlock() }
    val DIODE_CELL = registerCellBlock("diode", eln2Tab) { DiodeCellBlock() }
    val DC_DC_CONVERTER_BLOCK = registerBasicBlock("dcdc_converter") { DcDcConverterBlock() }
    val `12V_BATTERY_CELL` = registerCellBlock("12v_battery", eln2Tab) { `12vBatteryCellBlock`()}
    val LIGHT_CELL = registerCellBlock("light") {LightCellBlock()}
    val SOLAR_LIGHT_CELL = registerCellBlock("solar_light") {SolarLightCellBlock()}
    val SOLAR_PANEL_CELL = registerCellBlock("solar_panel", eln2Tab) {SolarPanelCellBlock()}
}
