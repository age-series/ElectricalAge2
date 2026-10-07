@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import net.minecraft.client.gui.screens.MenuScreens
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.WATT
import org.ageseries.libage.data.WATT_HOUR
import org.eln2.mc.client.screens.BasicProgressScreen
import org.eln2.mc.common.cells.CellRegistry.cellMemoize
import org.eln2.mc.common.cells.foundation.CellFactory
import org.eln2.mc.common.content.InventoryPowerCellModel
import org.eln2.mc.common.content.PowerCellChargerBlock
import org.eln2.mc.common.content.PowerCellChargerBlockEntity
import org.eln2.mc.common.content.PowerCellChargerCell
import org.eln2.mc.common.content.PowerCellChargerMenu
import org.eln2.mc.common.content.PowerCellItem
import org.eln2.mc.common.items.ItemRegistry.item
import org.eln2.mc.common.items.CreativeTabRegistry
import org.eln2.mc.common.blocks.BlockRegistry.blockAndItem
import org.eln2.mc.common.blocks.BlockRegistry.blockEntityOnly
import org.eln2.mc.common.content.modules.ContentManager.withSelfDrop
import org.eln2.mc.common.containers.ContainerRegistry.menu
import org.eln2.mc.directionPoleMapPlanar
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.resource

object Eln2InventoryPower : ContentModule() {
    val LEAD_ACID_POWER_CELL_MODEL = InventoryPowerCellModel(
        energyCapacity = Quantity(200.0, WATT_HOUR),
        maxPowerInput = Quantity(100.0, WATT),
        maxPowerOutput = Quantity(200.0, WATT),
        efficiency = 0.85
    )

    //#region Power Cell Charger

    val POWER_CELL_CHARGER_CELL = cellMemoize("power_cell_charger") {
        val map = directionPoleMapPlanar(Base6Direction3d.Left, Base6Direction3d.Right)

        CellFactory {
            PowerCellChargerCell(it, map)
        }
    }

    val POWER_CELL_CHARGER_BLOCK = blockAndItem("power_cell_charger") { PowerCellChargerBlock() }
        .withSelfDrop()

    val POWER_CELL_CHARGER_BLOCK_ENTITY = blockEntityOnly(
        "power_cell_charger",
        POWER_CELL_CHARGER_BLOCK.block,
        ::PowerCellChargerBlockEntity
    )

    val POWER_CELL_CHARGER_MENU = menu("power_cell_charger", ::PowerCellChargerMenu)

    //#endregion

    val LEAD_ACID_POWER_CELL = item("lead_acid_power_cell") {
        PowerCellItem(LEAD_ACID_POWER_CELL_MODEL)
    }

    init {
        CreativeTabRegistry.creativeTabVariant {
            LEAD_ACID_POWER_CELL.get().createStack(count = 1)
        }
    }

    override fun setupScreens() {
        MenuScreens.register(POWER_CELL_CHARGER_MENU.get()) { menu, inventory, title ->
            BasicProgressScreen(
                menu, inventory, title,
                resource("textures/gui/container/power_cell_charger/base.png"),
                resource("textures/gui/container/power_cell_charger/progress.png"),
                78.0f,
                97.0f
            )
        }
    }
}
