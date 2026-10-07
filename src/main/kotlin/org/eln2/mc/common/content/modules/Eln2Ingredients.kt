@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraftforge.registries.RegistryObject
import org.ageseries.libage.utils.addUnique
import org.ageseries.libage.utils.putUnique
import org.eln2.mc.client.render.foundation.MyColor
import org.eln2.mc.common.ModEvents
import org.eln2.mc.common.content.modules.ContentManager.withItemTagDatagen
import org.eln2.mc.common.content.modules.world.Eln2Ores
import org.eln2.mc.common.items.ItemRegistry.item
import org.eln2.mc.common.items.ItemRegistry.itemDefault
import org.eln2.mc.common.content.fluid.BrushItem
import java.util.function.Supplier

object Eln2Ingredients : ContentModule() {
    //#region Registration Helpers

    /**
     * Item with a data-generated model (consisting of a base texture and a tint).
     * @param id The item registry path.
     * @param tint The color of the item.
     * @param item The item itself.
     * */
    data class IngredientInfo<Type>(
        val id: String,
        override val tint: MyColor,
        val item: RegistryObject<Item>
    ) : Supplier<Item> by item, ModEvents.ItemAndTint

    /**
     * Repository for intermediary items, that will get simple generated models.
     * */
    open class IngredientSet<Type> {
        /**
         * Read by [org.eln2.mc.Eln2ItemModelProviderDatagen] (specific implementation for the type of intermediary item).
         * This generates the simple model using a base texture and tint.
         * */
        val itemsForModelDatagen = LinkedHashSet<IngredientInfo<Type>>()

        /**
         * Registers an item with the ID [name], and adds it to the items for tint (picked up automatically by a generic routine) and to [itemsForModelDatagen] (needs a specific implementation in [org.eln2.mc.Eln2ItemModelProviderDatagen]).
         * */
        fun register(name: String, tint: MyColor) : IngredientInfo<Type> {
            ContentManager.requireInit()

            val ingredientItem = itemDefault(name)
            val obj = IngredientInfo<Type>(name, tint, ingredientItem)

            ContentManager.addItemForTint(obj)
            itemsForModelDatagen.addUnique(obj)

            return obj
        }
    }

    /**
     * Repository extending [IngredientSet] with additional information for generating recipes to obtain the ingredient.
     * */
    class IngredientSetWithRecipes<Type, Builder>(private val builderConstructor: (obj: IngredientInfo<Type>) -> Builder) : IngredientSet<Type>() {
        /**
         * Read by [org.eln2.mc.Eln2RecipeProviderDatagen] (specific implementation for the type of intermediary item).
         * This generates the recipes that convert some input items into this ingredient (e.g. if this repository stores plates, the builder would have data needed to make the recipes for hammering and rolling to get the plate).
         * */
        val itemsForRecipeDatagen = LinkedHashSet<Builder>()

        /**
         * Executes [register] to register the item, tint and model, and creates a builder to gather the recipes to obtain the ingredient and adds it to [itemsForRecipeDatagen].
         * */
        fun build(name: String, tint: MyColor, build: (Builder.() -> Unit)? = null) : IngredientInfo<Type> {
            ContentManager.requireInit()

            val result = register(name, tint)
            val builder = builderConstructor(result)
            build?.invoke(builder)

            itemsForRecipeDatagen.addUnique(builder)

            return result
        }
    }

    //#region Ingots

    interface Ingot
    val INGOTS = IngredientSet<Ingot>()

    //#endregion

    //#region Plates

    interface Plate
    val PLATES = IngredientSetWithRecipes(::PlateBuilder)

    class PlateBuilder(val info: IngredientInfo<Plate>) {
        /**
         * Registers a rolling recipe that turns [sourceItemForRolling] into [info] with duration [rollingDuration].
         * */
        var sourceItemForRolling: Supplier<Item>? = null
        var rollingDuration = 100.0

        fun allRecipes(sourceItem: Supplier<Item>) {
            sourceItemForRolling = sourceItem
        }
    }

    //#endregion

    //#region Hot Items

    interface HotIngot
    val HOT_INGOTS = IngredientSetWithRecipes<HotIngot, HotItemBuilder<HotIngot>>(::HotItemBuilder)

    interface HotPlate
    val HOT_PLATES = IngredientSetWithRecipes<HotPlate, HotItemBuilder<HotPlate>>(::HotItemBuilder)

    class HotItemBuilder<Type>(val info: IngredientInfo<Type>) {
        /**
         * Registers smelting and blasting recipes that turn [sourceItemForVanillaHeating] into [info].
         * */
        var sourceItemForVanillaHeating: Supplier<Item>? = null
    }

    //#endregion

    //#region Wires

    interface Wire
    val WIRES = IngredientSetWithRecipes(::WireBuilder)

    class WireBuilder(val info: IngredientInfo<Wire>) {
        /**
         * Registers extruding recipe that turns [sourceItemForExtruding] into [info].
         * */
        var sourceItemForExtruding: Supplier<Item>? = null
        var extrudingDuration = 10.0
    }

    //#endregion

    //#region Dusts

    interface Dust
    val DUSTS = IngredientSetWithRecipes(::DustBuilder)

    class DustBuilder(val info: IngredientInfo<Dust>) {
        data class CrushingInfo(
            val sourceItem: Supplier<Item>,
            val tier: Int,
            val duration: Double
        )

        var sourceItemsForCrushing = LinkedHashSet<CrushingInfo>()

        /**
         * Adds a recipe that transforms [sourceItem] into [info] via crushing.
         * */
        fun fromCrushing(sourceItem: Supplier<Item>, tier: Int = 0, duration: Double = 30.0) {
            sourceItemsForCrushing.add(CrushingInfo(sourceItem, tier, duration))
        }
    }

    //#endregion

    val CRUSHING_RECIPES_FOR_DATAGEN = LinkedHashMap<Supplier<Item>, Supplier<Item>>()

    fun<T : Supplier<Item>> T.withCrushingRecipeDatagen(result: Supplier<Item>) : T {
        CRUSHING_RECIPES_FOR_DATAGEN.putUnique(this, result)
        return this
    }

    //#endregion

    val COKE = itemDefault("coke")
        .withItemTagDatagen(Eln2ConventionTags.COAL_EQUIVALENT)

    val COKE_DUST = DUSTS.build("coke_dust", MyColor(127, 127, 127)) {
        fromCrushing(COKE)
    }.withItemTagDatagen(Eln2ConventionTags.COAL_EQUIVALENT)

    val CARBON_PUTTY = itemDefault("carbon_putty")
    val RAW_CARBON_BRUSH = itemDefault("raw_carbon_brush")
    val CARBON_BRUSH = itemDefault("carbon_brush")

    val UNFIRED_RESISTOR = itemDefault("unfired_resistor")
    val RESISTOR = itemDefault("resistor")

    val CAPACITOR_SEPARATOR = itemDefault("capacitor_separator")
    val CAPACITOR = itemDefault("capacitor")

    val RAW_CIRCUIT_BOARD = itemDefault("raw_circuit_board")
    val PRIMITIVE_CIRCUIT = itemDefault("primitive_circuit")

    //#region Crushed Ores

    val CRUSHED_IRON_ORE = itemDefault("crushed_iron_ore")
    val CRUSHED_COPPER_ORE = itemDefault("crushed_copper_ore")
    val CRUSHED_GOLD_ORE = itemDefault("crushed_gold_ore")

    //#endregion

    //#region Lead

    val LEAD_INGOT = INGOTS.register("lead_ingot", MyColor(150, 150, 160))
        .withItemTagDatagen(Eln2ConventionTags.INGOT_LEAD)

    val LEAD_PLATE = PLATES.build("lead_plate", MyColor(165, 165, 180)) {
        allRecipes(LEAD_INGOT)
    }.withItemTagDatagen(Eln2ConventionTags.PLATE_LEAD)

    val LEAD_WIRE = WIRES.build("lead_wire", MyColor(110, 110, 130)) {
        sourceItemForExtruding = LEAD_INGOT
    }

    val LEAD_DUST = DUSTS.build("lead_dust", MyColor(110, 110, 130)) {
        fromCrushing(LEAD_INGOT, duration = 15.0)
        fromCrushing(LEAD_PLATE, duration = 12.5)
        fromCrushing(LEAD_WIRE, duration = 8.0)
    }.withItemTagDatagen(Eln2ConventionTags.DUST_LEAD)

    //#endregion

    //#region Tin

    val TIN_INGOT = INGOTS.register("tin_ingot", MyColor(244, 235, 231))
        .withItemTagDatagen(Eln2ConventionTags.INGOT_TIN)

    val TIN_PLATE = PLATES.build("tin_plate", TIN_INGOT.tint) {
        allRecipes(TIN_INGOT)
    }.withItemTagDatagen(Eln2ConventionTags.PLATE_TIN)

    val TIN_WIRE = WIRES.build("tin_wire", TIN_INGOT.tint) {
        sourceItemForExtruding = TIN_INGOT
    }

    val TIN_DUST = DUSTS.build("tin_dust", MyColor(205, 195, 193)) {
        fromCrushing(TIN_INGOT, duration = 20.0)
        fromCrushing(TIN_PLATE, duration = 18.0)
        fromCrushing(TIN_WIRE, duration = 15.0)
    }.withItemTagDatagen(Eln2ConventionTags.DUST_TIN)

    //#endregion

    //#region Iron

    val HOT_IRON_INGOT = HOT_INGOTS.build("hot_iron_ingot", MyColor(255, 192, 0)) {
        sourceItemForVanillaHeating = Supplier { Items.IRON_INGOT }
    }

    val IRON_PLATE = PLATES.build("iron_plate", MyColor.WHITE) {
        allRecipes(HOT_IRON_INGOT)
    }.withItemTagDatagen(Eln2ConventionTags.PLATE_IRON)

    val IRON_DUST = DUSTS.build("iron_dust", MyColor(200, 200, 200)) {
        fromCrushing(Items::IRON_INGOT)
        fromCrushing(IRON_PLATE)
    }.withItemTagDatagen(Eln2ConventionTags.DUST_IRON)

    val IRON_WIRE = WIRES.build("iron_wire", MyColor(180, 180, 190)) {
        sourceItemForExtruding = HOT_IRON_INGOT
    }

    //#endregion

    //#region Copper

    val HOT_COPPER_INGOT = HOT_INGOTS.build("hot_copper_ingot", MyColor(255, 127, 80)) {
        sourceItemForVanillaHeating = Supplier { Items.COPPER_INGOT }
    }

    val COPPER_PLATE = PLATES.build("copper_plate", MyColor(184, 115, 51)) {
        allRecipes(HOT_COPPER_INGOT)
    }.withItemTagDatagen(Eln2ConventionTags.PLATE_COPPER)

    val HOT_COPPER_PLATE = HOT_PLATES.build("hot_copper_plate", MyColor(240, 110, 70)) {
        sourceItemForVanillaHeating = COPPER_PLATE
    }

    val COPPER_WIRE = WIRES.build("copper_wire", MyColor(284, 185, 135)) {
        sourceItemForExtruding = HOT_COPPER_INGOT
    }

    val ENAMELED_COPPER_WIRE = WIRES.register("enameled_copper_wire", MyColor(184, 115, 51))

    val COPPER_DUST = DUSTS.build("copper_dust", COPPER_WIRE.tint) {
        fromCrushing(Items::COPPER_INGOT)
        fromCrushing(COPPER_PLATE)
        fromCrushing(COPPER_WIRE)
        fromCrushing(ENAMELED_COPPER_WIRE)
    }.withItemTagDatagen(Eln2ConventionTags.DUST_COPPER)

    //#endregion

    //#region Bronze

    val BRONZE_INGOT = INGOTS.register("bronze_ingot", MyColor(206, 137, 70))
        .withItemTagDatagen(Eln2ConventionTags.INGOT_BRONZE)

    val HOT_BRONZE_INGOT = HOT_INGOTS.build("hot_bronze_ingot", BRONZE_INGOT.tint) {
        sourceItemForVanillaHeating = BRONZE_INGOT
    }

    val BRONZE_PLATE = PLATES.build("bronze_plate", BRONZE_INGOT.tint) {
        allRecipes(HOT_BRONZE_INGOT)
    }.withItemTagDatagen(Eln2ConventionTags.PLATE_BRONZE)

    //#endregion
    val SULFUR_DUST = DUSTS.build("sulfur_dust", MyColor(200, 180, 50)) {
        fromCrushing(Eln2Ores.SULFUR_ORE.rawOreItem, duration = 15.0)
    }.withItemTagDatagen(Eln2ConventionTags.DUST_SULFUR)

    val POTASSIUM_NITRATE_DUST = DUSTS.build("potassium_nitrate_dust", MyColor(255, 230, 200)) {
        fromCrushing(Eln2Ores.SALTPETER_ORE.rawOreItem, duration = 15.0)
    }

    val MAGNETITE_DUST = DUSTS.build("magnetite_dust", MyColor(50, 50, 55)) {
        fromCrushing(Eln2Ores.MAGNETITE_ORE.rawOreItem, duration = 15.0)
    }

    val SCHEELITE_DUST = DUSTS.build("scheelite_dust", MyColor(255, 240, 180)) {
        fromCrushing(Eln2Ores.SCHEELITE_ORE.rawOreItem, duration = 15.0)
    }

    val SODIUM_CARBONATE_DUST = DUSTS.build("sodium_carbonate_dust", MyColor(240, 235, 230)) {
        fromCrushing(Eln2Ores.TRONA_ORE.rawOreItem, duration = 15.0)
    }

    val TUNGSTEN_TRIOXIDE_DUST = DUSTS.build("tungsten_trioxide_dust", MyColor(220, 200, 50))

    val TUNGSTEN_TRIOXIDE_PASTE = itemDefault("tungsten_trioxide_paste")
    val FILAMENT_SHAPED_TUNGSTEN_TRIOXIDE_PASTE = itemDefault("filament_shaped_tungsten_trioxide_paste")
    val TUNGSTEN_FILAMENT = itemDefault("tungsten_filament")

    val GLASS_ENVELOPE = itemDefault("glass_envelope")
    val UNSEALED_VACUUM_TUBE = itemDefault("unsealed_vacuum_tube")
    val VACUUM_TUBE = itemDefault("vacuum_tube")
    val UNSEALED_240V_100W_LIGHT_BULB = itemDefault("unsealed_240v_100w_light_bulb")

    val SCHEELITE_FUSION_MIX = DUSTS.build("scheelite_fusion_mix", MyColor(245, 235, 195))
    val SODIUM_TUNGSTENATE_MELT = DUSTS.build("sodium_tungstenate_melt", MyColor(215, 210, 200))
    val TUNGSTIC_ACID = DUSTS.build("tungstic_acid", MyColor(235, 230, 215))

    val CRUDE_MAGNET = itemDefault("crude_magnet")
    val COPPER_COIL = itemDefault("copper_coil")
    val INDUCTOR = itemDefault("inductor")
    val ROTOR_COIL_ASSEMBLY = itemDefault("rotor_coil_assembly")
    val ROTOR_COMMUTATOR = itemDefault("rotor_commutator")
    val COPPER_ROTOR = itemDefault("copper_rotor")
    val CRUDE_MOTOR_STATOR = itemDefault("crude_motor_stator")
    val BRUSHED_DC_MOTOR = itemDefault("brushed_dc_motor")

    val IRON_SHAFT = itemDefault("iron_shaft")

    val COPPER_ROD = itemDefault("copper_rod")

    val IRON_AXLE_MOUNT = itemDefault("iron_axle_mount")

    val IRON_GEAR = itemDefault("iron_gear")

    val CRUSHER_DRUM = itemDefault("crusher_drum")

    val CRUSHER_ASSEMBLY = itemDefault("crusher_assembly")

    val MACHINE_FRAME = itemDefault("machine_frame")

    val EXTRUDING_PORT = itemDefault("extruding_port")

    val EXTRUDING_ASSEMBLY = itemDefault("extruding_assembly")

    val ROLLING_ASSEMBLY = itemDefault("rolling_assembly")

    val PIPE_HEAT_EXCHANGE_ASSEMBLY = itemDefault("pipe_heat_exchange_assembly")

    //#region Tree Extraction

    val RAW_LATEX = itemDefault("raw_latex")
    val RAW_RESIN = itemDefault("raw_resin")
        .withItemTagDatagen(Eln2ConventionTags.GLUE)

    //#endregion

    //#region Rubber

    val RUBBER_COMPOUND = item("rubber_compound") { Item(Item.Properties().stacksTo(8)) }
    val RUBBER = itemDefault("rubber")
    val BURNT_RUBBER = itemDefault("burnt_rubber")

    //#endregion

    //#region Asbestos

    val ASBESTOS_FIBER = itemDefault("asbestos_fiber")

    //#endregion

    val AUTOCLAVE_TRAY = itemDefault("autoclave_tray")
    val AUTOCLAVE_RADIATOR = itemDefault("autoclave_radiator")
    val AUTOCLAVE_SHELL = itemDefault("autoclave_shell")
    val AUTOCLAVE_DOOR = itemDefault("autoclave_door")
    val WIND_TURBINE_BLADE = itemDefault("wind_turbine_blade")
    val WIND_TURBINE_CORE = itemDefault("wind_turbine_core")

    //#region Lead-Acid Battery

    val LEAD_ACID_BATTERY_CELL = itemDefault("lead_acid_battery_cell")
    val LEAD_ACID_BATTERY_CHASSIS = itemDefault("lead_acid_battery_chassis")
    val LEAD_ACID_BATTERY_BASE_12V = itemDefault("lead_acid_battery_base_12v")

    //#endregion

    //#region Rubber-Wood Panel

    val RUBBER_WOOD_PANEL = itemDefault("rubber_wood_panel")

    //#endregion

    //#region Advanced Coal Burner

    val INSULATED_COMBUSTION_CHAMBER = itemDefault("insulated_combustion_chamber")
    val BURNER_CONTROLLER_UNIT = itemDefault("burner_controller_unit")
    val BURNER_FUEL_HOPPER = itemDefault("burner_fuel_hopper")

    //#endregion

    //#region Probes

    val PROBE_BODY = itemDefault("probe_body")

    //#endregion

    //#region Steam Turbine

    val TURBINE_ROTOR = itemDefault("turbine_rotor")
    val STEAM_INJECTOR = itemDefault("steam_injector")
    val CONDENSER_UNIT = itemDefault("condenser_unit")
    val BEARING_ASSEMBLY = itemDefault("bearing_assembly")
    val TURBINE_CASING = itemDefault("turbine_casing")
    val TURBINE_CONTROLLER = itemDefault("turbine_controller")

    //#endregion

    //#region Brushes

    val CREOSOTE_BRUSH = item("creosote_brush") { BrushItem(32) }
    val ENAMEL_BRUSH = item("enamel_brush") { BrushItem(32) }

    //#endregion
}
