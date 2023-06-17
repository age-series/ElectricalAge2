package org.eln2.mc

import com.jozufozu.flywheel.core.materials.model.ModelData
import com.mojang.math.*
import mcp.mobius.waila.api.IPluginConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.network.NetworkHooks
import org.ageseries.libage.data.BiMap
import org.ageseries.libage.data.MutableSetMapMultiMap
import org.ageseries.libage.data.mutableBiMapOf
import org.ageseries.libage.sim.Material
import org.ageseries.libage.sim.Scale
import org.ageseries.libage.sim.electrical.mna.Circuit
import org.ageseries.libage.sim.electrical.mna.component.Resistor
import org.ageseries.libage.sim.electrical.mna.component.VoltageSource
import org.ageseries.libage.sim.thermal.ConnectionParameters
import org.ageseries.libage.sim.thermal.Simulator
import org.ageseries.libage.sim.thermal.Temperature
import org.ageseries.libage.sim.thermal.ThermalMass
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import org.eln2.mc.common.blocks.foundation.CellBlockEntity
import org.eln2.mc.common.blocks.foundation.MultiblockManager
import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity
import org.eln2.mc.common.cells.foundation.Cell
import org.eln2.mc.common.cells.foundation.CellPos
import org.eln2.mc.common.cells.foundation.ComponentHolder
import org.eln2.mc.common.cells.foundation.ElectricalComponentInfo
import org.eln2.mc.common.parts.foundation.Part
import org.eln2.mc.common.parts.foundation.PartUpdateType
import org.eln2.mc.data.LocationDescriptor
import org.eln2.mc.mathematics.RelativeDir
import org.eln2.mc.data.*
import org.eln2.mc.integration.WailaTooltipBuilder
import org.eln2.mc.mathematics.*
import org.eln2.mc.mathematics.Vector3d
import org.eln2.mc.sim.EnvironmentInformation
import org.eln2.mc.sim.MaterialMapping
import org.eln2.mc.sim.ThermalBody
import org.eln2.mc.utility.Vectors
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.abs

fun AABB.viewClip(entity: LivingEntity): Optional<Vec3> {
    val viewDirection = entity.lookAngle

    val start = Vec3(entity.x, entity.eyeY, entity.z)

    val distance = 5.0

    val end = start + viewDirection * distance

    return this.clip(start, end)
}

fun AABB.minVec3(): Vec3 {
    return Vec3(this.minX, this.minY, this.minZ)
}

fun AABB.maxVec3(): Vec3 {
    return Vec3(this.maxX, this.maxY, this.maxZ)
}

fun AABB.size(): Vec3 {
    return this.maxVec3() - this.minVec3()
}

fun AABB.corners(list: MutableList<Vec3>) {
    val min = this.minVec3()
    val max = this.maxVec3()

    list.add(min)
    list.add(Vec3(min.x, min.y, max.z))
    list.add(Vec3(min.x, max.y, min.z))
    list.add(Vec3(max.x, min.y, min.z))
    list.add(Vec3(min.x, max.y, max.z))
    list.add(Vec3(max.x, min.y, max.z))
    list.add(Vec3(max.x, max.y, min.z))
    list.add(max)
}

fun AABB.corners(): ArrayList<Vec3> {
    val list = ArrayList<Vec3>()

    this.corners(list)

    return list
}

/**
 * Transforms the Axis Aligned Bounding Box by the given rotation.
 * This operation does not change the volume for axis aligned transformations.
 * */
fun AABB.transformed(quaternion: Quaternion): AABB {
    var min = Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    var max = Vector3f(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

    this.corners().forEach {
        val corner = it.toVector3f()

        corner.transform(quaternion)

        min = Vectors.componentMin(min, corner)
        max = Vectors.componentMax(max, corner)
    }

    return AABB(min.toVec3(), max.toVec3())
}

fun BlockState.facing(): Direction = this.getValue(HorizontalDirectionalBlock.FACING)
operator fun BlockPos.plus(displacement: Vec3i): BlockPos {
    return this.offset(displacement)
}

operator fun BlockPos.plus(direction: Direction): BlockPos {
    return this + direction.normal
}

operator fun BlockPos.minus(displacement: Vec3i): BlockPos {
    return this.subtract(displacement)
}

operator fun BlockPos.minus(other: BlockPos): BlockPos {
    return BlockPos(this.x - other.x, this.y - other.y, this.z - other.z)
}

operator fun BlockPos.minus(direction: Direction): BlockPos {
    return this - direction.normal
}

fun BlockPos.toVec3(): Vec3 {
    return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
}

fun BlockPos.directionTo(other: BlockPos): Direction? {
    return Direction.fromNormal(other - this)
}

fun Direction.isVertical(): Boolean {
    return this == Direction.UP || this == Direction.DOWN
}

fun Direction.isHorizontal(): Boolean {
    return !isVertical()
}

val Direction.alias: RelativeDir
    get() = when (this) {
    Direction.DOWN -> RelativeDir.Down
    Direction.UP -> RelativeDir.Up
    Direction.NORTH -> RelativeDir.Front
    Direction.SOUTH -> RelativeDir.Back
    Direction.WEST -> RelativeDir.Left
    Direction.EAST -> RelativeDir.Right
}

val RelativeDir.alias: Direction get() = when (this) {
    RelativeDir.Front -> Direction.NORTH
    RelativeDir.Back -> Direction.SOUTH
    RelativeDir.Left -> Direction.WEST
    RelativeDir.Right -> Direction.EAST
    RelativeDir.Up -> Direction.UP
    RelativeDir.Down -> Direction.DOWN
}

fun Direction.index(): Int {
    return this.get3DDataValue()
}

fun Direction.toVector3D(): Vector3D {
    return Vector3D(this.stepX.toDouble(), this.stepY.toDouble(), this.stepZ.toDouble())
}

fun AbstractContainerMenu.addPlayerGrid(playerInventory: Inventory, addSlot: ((Slot) -> Unit)): Int {
    var slots = 0

    for (i in 0..2) {
        for (j in 0..8) {
            addSlot(Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18))
            slots++
        }
    }
    for (k in 0..8) {
        addSlot(Slot(playerInventory, k, 8 + k * 18, 142))
        slots++
    }

    return slots
}

fun interface ContainerFactory<T: BlockEntity> {
    fun create(id: Int, inventory: Inventory, player: Player, entity: T): AbstractContainerMenu
}

fun Level.playLocalSound(
    pos: Vec3,
    pSound: SoundEvent,
    pCategory: SoundSource,
    pVolume: Float,
    pPitch: Float,
    pDistanceDelay: Boolean
) {
    this.playLocalSound(pos.x, pos.y, pos.z, pSound, pCategory, pVolume, pPitch, pDistanceDelay)
}

fun Level.addParticle(
    pParticleData: ParticleOptions,
    pos: Vec3,
    pXSpeed: Double,
    pYSpeed: Double,
    pZSpeed: Double
) {
    this.addParticle(pParticleData, pos.x, pos.y, pos.z, pXSpeed, pYSpeed, pZSpeed)
}

fun Level.addParticle(
    pParticleData: ParticleOptions,
    pos: Vec3,
    speed: Vec3
) {
    this.addParticle(pParticleData, pos.x, pos.y, pos.z, speed.x, speed.y, speed.z)
}

@ServerOnly
fun ServerLevel.destroyPart(part: Part<*>) {
    val pos = part.placement.pos

    val multipart = this.getBlockEntity(pos)
        as? MultipartBlockEntity

    if (multipart == null) {
        Eln2.LOGGER.error("Multipart null at $pos")

        return
    }

    val saveTag = CompoundTag()

    multipart.breakPart(part, saveTag)

    val itemEntity = ItemEntity(
        this,
        pos.x.toDouble(),
        pos.y.toDouble(),
        pos.z.toDouble(),
        Part.createPartDropStack(part.id, saveTag)
    )

    this.addFreshEntity(itemEntity)

    if (multipart.isEmpty) {
        this.destroyBlock(pos, false)
    }
}

inline fun<reified TEntity: BlockEntity> Level.constructMenu(
    pos: BlockPos,
    player: Player,
    crossinline title: (() -> Component),
    factory: ContainerFactory<TEntity>
): InteractionResult {

    if(!this.isClientSide) {
        val entity = this.getBlockEntity(pos) as? TEntity
            ?: return InteractionResult.FAIL

        val containerProvider = object : MenuProvider {
            override fun getDisplayName(): Component {
                return title()
            }

            override fun createMenu(
                pContainerId: Int,
                pInventory: Inventory,
                pPlayer: Player
            ): AbstractContainerMenu {
                return factory.create(
                    pContainerId,
                    pInventory,
                    pPlayer,
                    entity
                )
            }
        }

        NetworkHooks.openGui(player as ServerPlayer, containerProvider, entity.blockPos)
        return InteractionResult.SUCCESS
    }

    return InteractionResult.SUCCESS
}

inline fun<reified TEntity: BlockEntity> Level.constructMenu(
    pos: BlockPos,
    player: Player,
    crossinline title: (() -> Component),
    crossinline factory: ((Int, Inventory, ItemStackHandler) -> AbstractContainerMenu),
    crossinline accessor: ((TEntity) -> ItemStackHandler)): InteractionResult {

    return this.constructMenu<TEntity>(pos, player, title) {
            id, inventory, _, entity -> factory(id, inventory, accessor(entity))
    }
}

fun Level.getDataAccess(pos: BlockPos): DataNode? {
    return ((this.getBlockEntity(pos) ?: return null)
            as? DataEntity ?: return null)
        .dataNode
}

inline fun<reified T : Cell> Level.getCellOrNull(mb: MultiblockManager, cellPosId: BlockPos): T? {
    val entity = this.getBlockEntity(mb.txIdWorld(cellPosId)) as? CellBlockEntity
        ?: return null

    return entity.cell as? T
}

inline fun<reified T : Cell> Level.getCell(mb: MultiblockManager, cellPosId: BlockPos): T =
    getCellOrNull(mb, cellPosId) ?: error("Cell was not present")

private const val LIBAGE_SET_EPS = 0.001
fun org.ageseries.libage.sim.electrical.mna.component.Component.connect(pin: Int, info: ElectricalComponentInfo){
    this.connect(pin, info.component, info.index)
}

fun Circuit.add(holder: ComponentHolder<*>){
    this.add(holder.instance)
}

fun Resistor.setResistanceEpsilon(resistance: Double, epsilon: Double = LIBAGE_SET_EPS): Boolean {
    if(abs(this.resistance - resistance) < epsilon){
        return false
    }

    this.resistance = resistance

    return true
}

fun VoltageSource.setPotentialEpsilon(potential: Double, epsilon: Double = LIBAGE_SET_EPS): Boolean {
    if(abs(this.potential - potential) < epsilon){
        return false
    }

    this.potential = potential

    return true
}

fun Simulator.add(body: ThermalBody) {
    this.add(body.thermal)
}

fun Simulator.remove(body: ThermalBody) {
    this.remove(body.thermal)
}

fun Simulator.connect(a: ThermalBody, b: ThermalBody, parameters: ConnectionParameters){
    this.connect(a.thermal, b.thermal, parameters)
}

fun Simulator.connect(a: ThermalMass, environmentInformation: EnvironmentInformation) {
    val connectionInfo = ConnectionParameters(
        conductance = environmentInformation.airThermalConductivity
    )

    this.connect(a, environmentInformation.temperature, connectionInfo)
}

fun Simulator.connect(a: ThermalBody, environmentInformation: EnvironmentInformation) {
    this.connect(a.thermal, environmentInformation)
}

operator fun Matrix4f.times(other: Matrix4f): Matrix4f {
    val copy = this.copy()

    copy.multiply(other)

    return copy
}

operator fun Matrix4f.timesAssign(other: Matrix4f) {
    this.multiply(other)
}

/**
 * This removes the translation I observed in BlockBench models.
 * Useful for applying transformations like rotation and scale.
 * */
fun ModelData.zeroCenter(): ModelData {
    return this.translate(Vec3(-0.5, 0.0, -0.5))
}

fun ModelData.blockCenter(): ModelData {
    return this.translate(Vec3(0.5, 0.0, 0.5))
}

private const val NBT_ELECTRICAL_RESISTIVITY = "electricalResistivity"
private const val NBT_THERMAL_CONDUCTIVITY = "thermalConductivity"
private const val NBT_SPECIFIC_HEAT = "specificHeat"
private const val NBT_DENSITY = "density"
private const val NBT_MATERIAL_NAME = "materialName"
private const val NBT_ENERGY = "energy"
private const val NBT_MASS = "mass"
private const val NBT_MATERIAL = "material"
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

fun CompoundTag.putRelativeDirection(key: String, direction: RelativeDir) {
    val data = direction.id

    this.putInt(key, data)
}

fun CompoundTag.getDirectionActual(key: String): RelativeDir {
    val data = this.getInt(key)

    return RelativeDir.fromId(data)
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
        it.putDouble(NBT_ELECTRICAL_RESISTIVITY, material.electricalResistivity)
        it.putDouble(NBT_THERMAL_CONDUCTIVITY, material.thermalConductivity)
        it.putDouble(NBT_SPECIFIC_HEAT, material.specificHeat)
        it.putDouble(NBT_DENSITY, material.density)
    }
}

fun CompoundTag.getMaterial(id: String): Material {
    val tag = this.getCompound(id)

    return Material(
        tag.getDouble(NBT_ELECTRICAL_RESISTIVITY),
        tag.getDouble(NBT_THERMAL_CONDUCTIVITY),
        tag.getDouble(NBT_SPECIFIC_HEAT),
        tag.getDouble(NBT_DENSITY)
    )
}

fun CompoundTag.putMaterialMapped(id: String, material: Material) {
    this.putSubTag(id) {
        it.putString(NBT_MATERIAL_NAME, MaterialMapping.getName(material))
    }
}

fun CompoundTag.getMaterialMapped(id: String): Material {
    val tag = this.getCompound(id)

    return MaterialMapping.getMaterial(tag.getString(NBT_MATERIAL_NAME))
}

fun CompoundTag.putThermalMass(id: String, thermalMass: ThermalMass, materialSerializer : ((String, Material, CompoundTag) -> Unit)) {
    this.putSubTag(id) {
        materialSerializer(NBT_MATERIAL, thermalMass.material, it)
        it.putDouble(NBT_ENERGY, thermalMass.energy)
        it.putDouble(NBT_MASS, thermalMass.mass)
    }
}

fun CompoundTag.getThermalMass(id: String, materialDeserializer: ((String, CompoundTag) -> Material)): ThermalMass {
    val tag = this.getCompound(id)

    return ThermalMass(
        materialDeserializer(NBT_MATERIAL, tag),
        tag.getDouble(NBT_ENERGY),
        tag.getDouble(NBT_MASS)
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

fun CompoundTag.putThermalBody(id: String, body: ThermalBody) {
    this.putSubTag(id) {
        it.putThermalMass("Mass", body.thermal)
        it.putDouble("Area", body.area)
    }
}

fun CompoundTag.getThermalBody(id: String): ThermalBody {
    val tag = this.getCompound(id)

    return ThermalBody(
        tag.getThermalMass("Mass"),
        tag.getDouble("Area")
    )
}

fun CompoundTag.putTemperature(id: String, temperature: Temperature) {
    this.putDouble(id, temperature.kelvin)
}

fun CompoundTag.getTemperature(id: String): Temperature {
    return Temperature(this.getDouble(id))
}

fun Double.formatted(decimals: Int = 2): String {
    return "%.${decimals}f".format(this)
}

fun Double.formattedPercentN(decimals: Int = 2): String {
    return "${(this * 100.0).formatted(decimals)}%"
}

operator fun Quaternion.times(other: Quaternion): Quaternion {
    val copy = this.copy()

    copy.mul(other)

    return copy
}

operator fun Quaternion.timesAssign(other: Quaternion) {
    this.mul(other)
}

fun Rotation.inverse() = when(this) {
    Rotation.NONE -> Rotation.NONE
    Rotation.CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90
    Rotation.CLOCKWISE_180 -> Rotation.CLOCKWISE_180
    Rotation.COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90
}

operator fun Rotation.times(p: BlockPos) = p.rotate(this)
fun rot(dir: Direction) = when(dir) {
    Direction.NORTH -> Rotation.COUNTERCLOCKWISE_90
    Direction.SOUTH -> Rotation.CLOCKWISE_90
    Direction.WEST -> Rotation.CLOCKWISE_180
    Direction.EAST -> Rotation.NONE
    else -> error("Invalid horizontal facing $dir")
}

fun ThermalMass.appendBody(builder: WailaTooltipBuilder, config: IPluginConfig?) {
    builder.energy(this.energy)
    builder.mass(this.mass)
    builder.temperature(this.temperature.kelvin)
}

fun Simulator.subStep(dt: Double, steps: Int, consumer: ((Int, Double) -> Unit)? = null){
    val stepSize = dt / steps

    repeat(steps) {
        this.step(stepSize)
        consumer?.invoke(it, stepSize)
    }
}

fun WailaTooltipBuilder.resistor(resistor: Resistor): WailaTooltipBuilder {
    this.resistance(resistor.resistance)
    this.pinVoltages(resistor.pins)
    this.current(resistor.current)
    this.power(resistor.power)

    return this
}

fun WailaTooltipBuilder.voltageSource(source: VoltageSource): WailaTooltipBuilder {
    this.voltage(source.potential)
    this.current(source.current)

    return this
}

operator fun Vec3.plus(b: Vec3): Vec3 {
    return Vec3(this.x + b.x, this.y + b.y, this.z + b.z)
}

operator fun Vec3.plus(delta: Double): Vec3 {
    return Vec3(this.x + delta, this.y + delta, this.z + delta)
}

operator fun Vec3.minus(b: Vec3): Vec3 {
    return Vec3(this.x - b.x, this.y - b.y, this.z - b.z)
}

operator fun Vec3.minus(delta: Double): Vec3 {
    return Vec3(this.x - delta, this.y - delta, this.z - delta)
}

operator fun Vec3.times(b: Vec3): Vec3 {
    return Vec3(this.x * b.x, this.y * b.y, this.z * b.z)
}

operator fun Vec3.times(scalar: Double): Vec3 {
    return Vec3(this.x * scalar, this.y * scalar, this.z * scalar)
}

operator fun Vec3.div(b: Vec3): Vec3 {
    return Vec3(this.x / b.x, this.y / b.y, this.z / b.z)
}

operator fun Vec3.div(scalar: Double): Vec3 {
    return Vec3(this.x / scalar, this.y / scalar, this.z / scalar)
}

operator fun Vec3.unaryMinus(): Vec3 {
    return Vec3(-this.x, -this.y, -this.z)
}

operator fun Vec3.unaryPlus(): Vec3 {
    // For completeness

    return Vec3(this.x, this.y, this.z)
}

fun Vec3i.toVec3(): Vec3 {
    return Vec3(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
}

fun Vector3f.toVec3(): Vec3 {
    return Vec3(this.x().toDouble(), this.y().toDouble(), this.z().toDouble())
}

fun Vec3i.toVector3f(): Vector3f {
    return Vector3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
}

fun Vec3.toVector3f(): Vector3f {
    return Vector3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
}

fun Vec3.toVector4f(w: Float): Vector4f {
    return Vector4f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat(), w)
}

fun Vector3f.toVector3d() = Vector3d(this.x().toDouble(), this.y().toDouble(), this.z().toDouble())
fun BlockPos.toVector3d() = Vector3d(this.x.toDouble(), this.y.toDouble(), this.z.toDouble())
fun BlockPos.toVector3di() = Vector3di(this.x, this.y, this.z)

fun BlockEntity.sendClientUpdate() = this.level!!.sendBlockUpdated(this.blockPos, this.blockState, this.blockState, Block.UPDATE_CLIENTS)

fun<K, V> MutableSetMapMultiMap<K, V>.bind(): MutableSetMapMultiMap<K, V> {
    val result = MutableSetMapMultiMap<K, V>()

    this.keys.forEach { k ->
        result[k].addAll(this[k])
    }

    return result
}

fun<T> ArrayList<T>.bind() = ArrayList<T>(this.size).also { it.addAll(this) }
@Suppress("UNCHECKED_CAST")
fun<K, V> HashMap<K, V>.bind() = this.clone() as HashMap<K, V>

fun<T> MutableList<T>.swapi(i: Int, j: Int) {
    val tmp = this[i]
    this[i] = this[j]
    this[j] = tmp
}

fun Entity.moveTo(v: Vector3d) = this.moveTo(v.x, v.y, v.z)
fun Vector3d.toVec3() = Vec3(this.x, this.y, this.z)

fun Matrix4f.loadMatrix(m: Matrix4x4) {
    this.loadTransposed(
        FloatBuffer.allocate(16).apply {
            put(m.c0.x.toFloat())
            put(m.c0.y.toFloat())
            put(m.c0.z.toFloat())
            put(m.c0.w.toFloat())
            put(m.c1.x.toFloat())
            put(m.c1.y.toFloat())
            put(m.c1.z.toFloat())
            put(m.c1.w.toFloat())
            put(m.c2.x.toFloat())
            put(m.c2.y.toFloat())
            put(m.c2.z.toFloat())
            put(m.c2.w.toFloat())
            put(m.c3.x.toFloat())
            put(m.c3.y.toFloat())
            put(m.c3.z.toFloat())
            put(m.c3.w.toFloat())
        }
    )
}

fun Matrix3f.loadMatrix(m: Matrix3x3) {
    this.loadTransposed(
        FloatBuffer.allocate(9).apply {
            put(m.c0.x.toFloat())
            put(m.c0.y.toFloat())
            put(m.c0.z.toFloat())
            put(m.c1.x.toFloat())
            put(m.c1.y.toFloat())
            put(m.c1.z.toFloat())
            put(m.c2.x.toFloat())
            put(m.c2.y.toFloat())
            put(m.c2.z.toFloat())
        }
    )
}

fun ModelData.loadPose(p: Pose3d): ModelData {
    this.model.loadMatrix(p())
    this.normal.loadMatrix(p.rotation())

    return this
}

fun<U> CompoundTag.putQuantity(key: String, e: Quantity<U>) = this.putDouble(key, !e)
fun<U> CompoundTag.getQuantity(key: String) = Quantity<U>(this.getDouble(key))

inline fun <K, V> Iterable<K>.associateWithBi(valueSelector: (K) -> V): BiMap<K, V> {
    val result = mutableBiMapOf<K, V>()

    this.forEach { k ->
        result.add(k, valueSelector(k))
    }

    return result
}

fun ByteBuffer.putVector3di(v: Vector3di) {
    this.putInt(v.x)
    this.putInt(v.y)
    this.putInt(v.z)
}

fun ByteBuffer.getVector3di() = Vector3di(
    this.int,
    this.int,
    this.int
)

fun Direction.valueHashCode() = this.normal.hashCode()

fun Vector3di.toBlockPos() = BlockPos(this.x, this.y, this.z)
fun Vec3.toVector3d() = Vector3d(this.x, this.y, this.z)

fun Scale.map(u: Dual) = factor * u + base
fun Scale.unmap(u: Dual) = (u - base) / factor

fun ChunkPos.toVector2d() = Vector2d(this.x.toDouble(), this.z.toDouble())

fun<T> List<T>.averageOf(valueSelector: (T) -> Double): Double = this.sumOf(valueSelector) / this.size
fun<T> Collection<T>.sumOfDual(n: Int, dualSelector: (T) -> Dual): Dual {
    var result = Dual.const(0.0, n)
    this.forEach { result += dualSelector(it) }
    return result
}
