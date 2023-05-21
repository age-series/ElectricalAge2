package org.eln2.mc.extensions

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalMass
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.parts.foundation.PartUpdateType
import org.eln2.mc.common.space.*
import org.eln2.mc.sim.MaterialMapping

private const val ELECTRICAL_RESISTIVITY = "electricalResistivity"
private const val THERMAL_CONDUCTIVITY = "thermalConductivity"
private const val SPECIFIC_HEAT = "specificHeat"
private const val DENSITY = "density"
private const val MATERIAL_NAME = "materialName"
private const val ENERGY = "energy"
private const val MASS = "mass"
private const val MATERIAL = "material"

fun CompoundTag.putBlockPos(key: String, pos: BlockPos) {
    val dataTag = CompoundTag()
    dataTag.putInt("X", pos.x)
    dataTag.putInt("Y", pos.y)
    dataTag.putInt("Z", pos.z)
    this.put(key, dataTag)
}

fun CompoundTag.getBlockPos(key: String): BlockPos {
    val dataTag = this.get(key) as CompoundTag
    val x = dataTag.getInt("X")
    val y = dataTag.getInt("Y")
    val z = dataTag.getInt("Z")

    return BlockPos(x, y, z)
}

fun CompoundTag.putLocationDescriptor(id: String, descriptor: LocationDescriptor) {
    this.put(id, descriptor.toNbt())
}

fun CompoundTag.getLocationDescriptor(id: String): LocationDescriptor {
    return LocationDescriptor.fromNbt(this.getCompound(id))
}

fun CompoundTag.putCellPos(key: String, pos: CellPos) {
    val dataTag = CompoundTag()
    dataTag.putLocationDescriptor("Descriptor", pos.descriptor)
    this.put(key, dataTag)
}

fun CompoundTag.getCellPos(key: String): CellPos {
    val dataTag = this.get(key) as CompoundTag
    val descriptor = dataTag.getLocationDescriptor("Descriptor")
    return CellPos(descriptor)
}

fun CompoundTag.getStringList(key: String): List<String> {
    val tag = this.getCompound(key)
    return tag.allKeys.map { tag.getString(it) }
}

fun CompoundTag.putStringList(key: String, list: List<String>) {
    val tag = CompoundTag()
    list.forEachIndexed { index, it ->
        tag.putString("$index", it)
    }
    this.put(key, tag)
}

fun CompoundTag.getStringMap(key: String): Map<String, String> {
    val tag = this.getCompound(key)
    val map = mutableMapOf<String, String>()
    tag.allKeys.forEach { tagKey ->
        val tagValue = tag.getString(tagKey)
        map[tagKey] = tagValue
    }
    return map
}

fun CompoundTag.putStringMap(key: String, map: Map<String, String>) {
    val tag = CompoundTag()
    map.forEach { (k, v) ->
        tag.putString(k, v)
    }
    this.put(key, tag)
}

fun CompoundTag.putResourceLocation(key: String, resourceLocation: ResourceLocation) {
    this.putString(key, resourceLocation.toString())
}

fun CompoundTag.tryGetResourceLocation(key: String): ResourceLocation? {
    val str = this.getString(key)

    return ResourceLocation.tryParse(str)
}

fun CompoundTag.getResourceLocation(key: String): ResourceLocation {
    return this.tryGetResourceLocation(key) ?: error("Invalid resource location with key $key")
}

fun CompoundTag.putDirection(key: String, direction: Direction) {
    this.putInt(key, direction.get3DDataValue())
}

fun CompoundTag.getDirection(key: String): Direction {
    val data3d = this.getInt(key)

    return Direction.from3DDataValue(data3d)
}

fun CompoundTag.putRelativeDirection(key: String, direction: RelativeRotationDirection) {
    val data = direction.id

    this.putInt(key, data)
}

fun CompoundTag.getRelativeDirection(key: String): RelativeRotationDirection {
    val data = this.getInt(key)

    return RelativeRotationDirection.fromId(data)
}

fun CompoundTag.putPartUpdateType(key: String, type: PartUpdateType) {
    val data = type.id

    this.putInt(key, data)
}

fun CompoundTag.getPartUpdateType(key: String): PartUpdateType {
    val data = this.getInt(key)

    return PartUpdateType.fromId(data)
}

/**
 * Creates a new compound tag, calls the consumer method with the new tag, and adds the created tag to this instance.
 * @return The Compound Tag that was created.
 * */
fun CompoundTag.putSubTag(key: String, consumer: ((CompoundTag) -> Unit)): CompoundTag {
    val tag = CompoundTag()

    consumer(tag)

    this.put(key, tag)

    return tag
}

fun CompoundTag.withSubTag(key: String, tag: CompoundTag): CompoundTag {
    this.put(key, tag)
    return this
}

fun CompoundTag.withSubTagOptional(key: String, tag: CompoundTag?): CompoundTag {
    if(tag != null) {
        this.put(key, tag)
    }

    return this
}

/**
 * Gets the compound tag from this instance, and calls the consumer method with the found tag.
 * @return The tag that was found.
 * */
fun CompoundTag.useSubTag(key: String, consumer: ((CompoundTag) -> Unit)): CompoundTag {
    val tag = this.get(key) as CompoundTag
    consumer(tag)

    return tag
}

fun CompoundTag.placeSubTag(key: String, consumer: ((CompoundTag) -> Unit)): CompoundTag {
    val tag = CompoundTag()
    consumer(tag)

    this.put(key, tag)

    return tag
}

/**
 * Gets the compound tag from this instance, and calls the consumer method with the found tag.
 * @return The tag that was found.
 * */
fun CompoundTag.useSubTagIfPreset(key: String, consumer: ((CompoundTag) -> Unit)): CompoundTag? {
    val tag = this.get(key) as? CompoundTag
        ?: return null

    consumer(tag)

    return tag
}

fun CompoundTag.putMaterial(id: String, material: Material){
    this.putSubTag(id) {
        it.putDouble(ELECTRICAL_RESISTIVITY, material.electricalResistivity)
        it.putDouble(THERMAL_CONDUCTIVITY, material.thermalConductivity)
        it.putDouble(SPECIFIC_HEAT, material.specificHeat)
        it.putDouble(DENSITY, material.density)
    }
}

fun CompoundTag.getMaterial(id: String): Material {
    val tag = this.getCompound(id)

    return Material(
        tag.getDouble(ELECTRICAL_RESISTIVITY),
        tag.getDouble(THERMAL_CONDUCTIVITY),
        tag.getDouble(SPECIFIC_HEAT),
        tag.getDouble(DENSITY)
    )
}

fun CompoundTag.putMaterialMapped(id: String, material: Material) {
    this.putSubTag(id) {
        it.putString(MATERIAL_NAME, MaterialMapping.getName(material))
    }
}

fun CompoundTag.getMaterialMapped(id: String): Material {
    val tag = this.getCompound(id)

    return MaterialMapping.getMaterial(tag.getString(MATERIAL_NAME))
}

fun CompoundTag.putThermalMass(id: String, thermalMass: ThermalMass, materialSerializer : ((String, Material, CompoundTag) -> Unit)) {
    this.putSubTag(id) {
        materialSerializer(MATERIAL, thermalMass.material, it)
        it.putDouble(ENERGY, thermalMass.energy)
        it.putDouble(MASS, thermalMass.mass)
    }
}

fun CompoundTag.getThermalMass(id: String, materialDeserializer: ((String, CompoundTag) -> Material)): ThermalMass {
    val tag = this.getCompound(id)

    return ThermalMass(
        materialDeserializer(MATERIAL, tag),
        tag.getDouble(ENERGY),
        tag.getDouble(MASS)
    )
}

fun CompoundTag.putThermalMass(id: String, thermalMass: ThermalMass) {
    this.putThermalMass(id, thermalMass) { key, material, tag ->
        tag.putMaterial(key, material)
    }
}

fun CompoundTag.putThermalMassMapped(id: String, thermalMass: ThermalMass) {
    this.putThermalMass(id, thermalMass) { key, material, tag ->
        tag.putMaterialMapped(key, material)
    }
}

fun CompoundTag.getThermalMass(id: String): ThermalMass {
    return this.getThermalMass(id) { key, tag ->
        tag.getMaterial(key)
    }
}

fun CompoundTag.getThermalMassMapped(id: String): ThermalMass {
    return this.getThermalMass(id) { key, tag ->
        tag.getMaterialMapped(key)
    }
}

fun CompoundTag.putTemperature(id: String, temperature: Temperature) {
    this.putDouble(id, temperature.kelvin)
}

fun CompoundTag.getTemperature(id: String): Temperature {
    return Temperature(this.getDouble(id))
}
