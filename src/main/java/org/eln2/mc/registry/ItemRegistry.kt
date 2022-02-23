@file:Suppress("unused") // Because block variables here would be suggested for deletion.

package org.eln2.mc.registry

import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import org.eln2.mc.Eln2
import org.eln2.mc.Eln2.LOGGER
import org.eln2.mc.common.eln2Tab
import org.eln2.mc.registry.data.ItemRegistryItem
import org.eln2.mc.content.item.VoltmeterItem

object ItemRegistry {
    @Suppress("MemberVisibilityCanBePrivate") // Used for item registration and fetching
    val REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Eln2.MODID)!! // Yeah, if this fails blow up the game

    fun setup(bus : IEventBus){
        REGISTRY.register(bus)
        LOGGER.info("Prepared item registry.")
    }

    /**
     * Used to register basic items. Supply a name (eg, "ballpoint_pen", and an Item supplier). Result can be stored.
     *
     * ItemRegistry.registerBasicItem("voltmeter") { VoltmeterItem(Eln2Tab) }
     *
     * @param name Minecraft item name (all lowercase letters and underscores only)
     * @param supplier An Item supplier
     * @return ItemRegistryItem - the string name and the deferred registry entry
     */
    private fun registerBasicItem(name: String, supplier: () -> Item): ItemRegistryItem {
        val item = REGISTRY.register(name) {supplier()}
        return ItemRegistryItem(name, item)
    }

    /*
     * Item Registry Begins Here
     */
    val VOLTMETER_ITEM = registerBasicItem("voltmeter") { VoltmeterItem(eln2Tab) }
}
