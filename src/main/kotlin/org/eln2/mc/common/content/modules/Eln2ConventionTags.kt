@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraftforge.registries.ForgeRegistries

/**
 * Forge convention tags for cross-mod item interchangeability.
 */
object Eln2ConventionTags {
    //#region Ingot Tags

    val INGOT_LEAD = forgeTag("ingots/lead")
    val INGOT_TIN = forgeTag("ingots/tin")
    val INGOT_BRONZE = forgeTag("ingots/bronze")

    val INGOT_IRON = forgeTag("ingots/iron")
    val INGOT_COPPER = forgeTag("ingots/copper")

    //#endregion

    //#region Dust Tags

    val DUST_LEAD = forgeTag("dusts/lead")
    val DUST_TIN = forgeTag("dusts/tin")
    val DUST_IRON = forgeTag("dusts/iron")
    val DUST_COPPER = forgeTag("dusts/copper")
    val DUST_SULFUR = forgeTag("dusts/sulfur")

    //#endregion

    //#region Plate Tags

    val PLATE_LEAD = forgeTag("plates/lead")
    val PLATE_TIN = forgeTag("plates/tin")
    val PLATE_IRON = forgeTag("plates/iron")
    val PLATE_COPPER = forgeTag("plates/copper")
    val PLATE_BRONZE = forgeTag("plates/bronze")

    //#endregion

    //#region Raw Material Tags

    val RAW_MATERIAL_LEAD = forgeTag("raw_materials/lead")
    val RAW_MATERIAL_TIN = forgeTag("raw_materials/tin")
    val RAW_MATERIAL_SULFUR = forgeTag("raw_materials/sulfur")

    //#endregion

    //#region Misc Tags

    val SAND = forgeTag("sand")
    val GLUE = forgeTag("glue")
    val WOODEN_SLABS: TagKey<Item> = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "wooden_slabs"))
    val COAL_EQUIVALENT: TagKey<Item> = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("eln2", "coal_equivalent"))

    //#endregion

    init {
        // Register vanilla items' convention tags in the lookup so recipe datagen uses tags instead of concrete items (cross-mod compat).
        ContentManager.registerVanillaItemTag(Items.IRON_INGOT, INGOT_IRON)
        ContentManager.registerVanillaItemTag(Items.COPPER_INGOT, INGOT_COPPER)
        ContentManager.registerVanillaItemTag(Items.COAL, COAL_EQUIVALENT)
        ContentManager.registerVanillaItemTag(Items.CHARCOAL, COAL_EQUIVALENT)
        ContentManager.registerVanillaItemTag(Items.SAND, SAND)
        ContentManager.registerVanillaItemTag(Items.RED_SAND, SAND)
    }

    private fun forgeTag(path: String): TagKey<Item> =
        TagKey.create(ForgeRegistries.ITEMS.registryKey, ResourceLocation.fromNamespaceAndPath("forge", path))
}
