@file:Suppress("LocalVariableName", "LocalVariableName", "NonAsciiCharacters")

package org.eln2.mc.scientific

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.ServerTickEvent
import net.minecraftforge.event.entity.EntityLeaveLevelEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.data.mutableBiMapOf
import org.ageseries.libage.data.mutableMultiMapOf
import org.eln2.mc.*
import org.eln2.mc.common.*
import org.eln2.mc.common.capabilities.RadiationCapabilityProvider
import org.eln2.mc.common.events.registerHandler
import org.eln2.mc.data.*
import org.eln2.mc.mathematics.*
import org.eln2.mc.scientific.chemistry.*
import java.math.BigDecimal
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random
import kotlin.system.measureNanoTime

fun interface ParametricLinearAttenuationFunction<Param> {
    fun evaluate(t: Quantity<Param>): Quantity<ReciprocalDistance>
}

interface ParametricIntensityFunction<Param> {
    fun evaluateIntensityFactor(t: Quantity<Param>): Double
    fun evaluateCutoff(maximalIntensity: Double): Quantity<Param>
}

data class RadiationShieldingMaterial(
    val density: Quantity<Density>,
    val linearAttenuation: ParametricLinearAttenuationFunction<Energy>,
)

fun linearAttenuationMaterial(
    density: Quantity<Density>,
    linearAttenuationSpline: Spline1d,
    paramScale: QuantityScale<Energy> = MeV,
    valueScale: QuantityScale<ReciprocalDistance> = RECIP_CENTIMETERS,
    valueProcess: ((Double) -> Double)? = null,
) = RadiationShieldingMaterial(density) { p ->
    Quantity(
        linearAttenuationSpline.evaluate(p..paramScale).let {
            if (valueProcess == null) it
            else valueProcess(it)
        },
        valueScale
    )
}

fun massAttenuationMaterial(
    density: Quantity<Density>,
    massAttenuationSpline: Spline1d,
    paramScale: QuantityScale<Energy> = MeV,
    valueScale: QuantityScale<ReciprocalArealDensity> = CM2_PER_G,
    valueProcess: ((Double) -> Double)? = null,
) = linearAttenuationMaterial(density, massAttenuationSpline, paramScale, RECIP_METER) { v: Double ->
    (!Quantity(v, valueScale) * !density).let {
        if (valueProcess == null) it
        else valueProcess(it)
    }
}

// Optimization: caching the result. Its computation is very involved. Apart from countless virtual calls, every atom samples 5 splines and allocates a bunch of intermediary storage.
// The stone solution has 10 atoms, so you can already see why caching is a super optimization.
// I didn't have this before. At 70K VPS, it was using up ~70% of the CPU time. With this, it uses 3.3% :Fish_Amaze:
// At 200K VPS, it uses around 5% (which means there is some overhead associated with setting up the traversal, because the increase only happened due to larger scans / measurement)
private val radiationCastCache =
    ConcurrentHashMap<Triple<Int, Quantity<Density>, Quantity<Energy>>, Quantity<ReciprocalDistance>>()

private fun MolecularMassMixture.radiationCast(ro: Quantity<Density>) = RadiationShieldingMaterial(ro) { e ->
    radiationCastCache.getOrPut(Triple(this.hash, ro, e)) {
        computeLinearAttenuation(this, e, ro)
    }
}

private fun MolecularComposition.radiationCast(ro: Quantity<Density>) = (!this).radiationCast(ro)

private fun MolecularComposition.radiationCast() = this.radiationCast(this.properties.density)

private fun ChemicalElement.radiationCast() = (!!this).radiationCast(this.density)

val AIR = airMix.radiationCast(Quantity(1.293, KG_PER_M3))
val WATER = H2O.radiationCast()
val ICE = H2O.radiationCast(Quantity(0.917, G_PER_CM3))
val CLAY = clayMix.radiationCast(Quantity(1.3, G_PER_CM3))
val STONE = stoneMix.radiationCast(Quantity(2.6, G_PER_CM3))
val STONE_THIN = stoneMix.radiationCast(Quantity(1.3, G_PER_CM3))
val STONE_SUPER_THIN = stoneMix.radiationCast(Quantity(0.613, G_PER_CM3))
val GRAVEL = stoneMix.radiationCast(Quantity(1.56, G_PER_CM3))
val COBBLESTONE_THIN = stoneMix.radiationCast(Quantity(1.2, G_PER_CM3))
val SOIL = ktSoilMix.radiationCast(Quantity(2.5, G_PER_CM3))

val IRON = ChemicalElement.Iron.radiationCast()
val COPPER = ChemicalElement.Copper.radiationCast()

val SAND = SiO2.radiationCast(Quantity(1.52, G_PER_CM3))
val SODA_LIME_GLASS = sodaLimeGlassMix.radiationCast(Quantity(2.51, G_PER_CM3))
val SODA_LIME_GLASS_SUPER_THIN = sodaLimeGlassMix.radiationCast(Quantity(0.5, G_PER_CM3))
val OBSIDIAN = obsidianSpecimenMix.radiationCast(Quantity(2.41, G_PER_CM3))

val BETULA_VERRUCOSA = betulaVerrucosaMix.radiationCast(Quantity(0.552, G_PER_CM3))
val BETULA_VERRUCOSA_HALF = betulaVerrucosaMix.radiationCast(Quantity(0.25, G_PER_CM3))
val BETULA_VERRUCOSA_THIN = betulaVerrucosaMix.radiationCast(Quantity(0.1, G_PER_CM3))
val BETULA_VERRUCOSA_SUPER_THIN = betulaVerrucosaMix.radiationCast(Quantity(0.05, G_PER_CM3))

val PICEA_GLAUCA = piceaGlaucaMix.radiationCast(Quantity(0.47, G_PER_CM3))
val PICEA_GLAUCA_HALF = piceaGlaucaMix.radiationCast(Quantity(0.23, G_PER_CM3))
val PICEA_GLAUCA_THIN = piceaGlaucaMix.radiationCast(Quantity(0.07, G_PER_CM3))
val PICEA_GLAUCA_SUPER_THIN = piceaGlaucaMix.radiationCast(Quantity(0.03, G_PER_CM3))

val QUERCUS_FAGACEAE = quercusFagaceaeMix.radiationCast(Quantity(0.75, G_PER_CM3))
val QUERCUS_FAGACEAE_HALF = quercusFagaceaeMix.radiationCast(Quantity(0.4, G_PER_CM3))
val QUERCUS_FAGACEAE_THIN = quercusFagaceaeMix.radiationCast(Quantity(0.02, G_PER_CM3))
val QUERCUS_FAGACEAE_SUPER_THIN = quercusFagaceaeMix.radiationCast(Quantity(0.1, G_PER_CM3))

val HUMAN = linearAttenuationMaterial(
    density = Quantity(1062.01261557, KG_PER_M3),
    loadPairInterpolator("rads/human/ds.kvp"),
    paramScale = keV,
    valueScale = RECIP_METER
)

data class RadiationAbsorberInfo(
    val mass: Quantity<Mass>,
    val material: RadiationShieldingMaterial,
    val penetrationDepth: Quantity<Distance>,
)

interface RadiationQuanta {
    val symbol: String
    val energy: Quantity<Energy>

    fun evaluateTransactionProbability(
        penetrationDepth: Quantity<Distance>,
        medium: RadiationShieldingMaterial,
    ): Double

    fun evaluateAbsorbedDose(
        receiverIntensity: Quantity<Radioactivity>,
        interval: Quantity<Time>,
        absorber: RadiationAbsorberInfo,
    ): Quantity<RadiationAbsorbedDose> {
        val disintegrations = !receiverIntensity * !interval
        val availableEnergy = energy * disintegrations

        val absorbProbability = 1.0 - evaluateTransactionProbability(
            absorber.penetrationDepth,
            absorber.material
        )

        return Quantity(!(availableEnergy * absorbProbability) / !absorber.mass)
    }

    fun evaluateDoseEquivalent(
        receiverIntensity: Quantity<Radioactivity>,
        interval: Quantity<Time>,
        absorber: RadiationAbsorberInfo,
    ): Quantity<RadiationDoseEquivalent>
}

data class RadiationEmissionMode(
    val quanta: RadiationQuanta,
    val emitterIntensity: Quantity<Radioactivity>,
    val intensityFunction: ParametricIntensityFunction<Distance>,
) {
    val upperBound = intensityFunction.evaluateCutoff(!emitterIntensity)
}

fun exponentialSphereIntensityFunction(x: Double = 2.0, threshold: Quantity<Radioactivity> = RADIATION_SYSTEM_CUTOFF) =
    object : ParametricIntensityFunction<Distance> {
        override fun evaluateIntensityFactor(t: Quantity<Distance>) =
            (1.0 / (4.0 * PI * (!t).pow(x))).coerceIn(0.0, 1.0)

        override fun evaluateCutoff(maximalIntensity: Double) = Quantity<Distance>(
            (4.0 * PI).pow(-1.0 / x) * ((!threshold / maximalIntensity).pow(-1.0 / x))
        )
    }

data class Photon(override val symbol: String, override val energy: Quantity<Energy>) : RadiationQuanta {
    override fun evaluateTransactionProbability(
        penetrationDepth: Quantity<Distance>,
        medium: RadiationShieldingMaterial,
    ) = exp(-!medium.linearAttenuation.evaluate(energy) * !penetrationDepth)

    override fun evaluateDoseEquivalent(
        receiverIntensity: Quantity<Radioactivity>,
        interval: Quantity<Time>,
        absorber: RadiationAbsorberInfo,
    ) = evaluateAbsorbedDose(receiverIntensity, interval, absorber).reparam<RadiationDoseEquivalent>()

    override fun toString() = "${energy.format(scale = MeV)} MeV $symbol"
}

fun gammaPhoton(energy: Quantity<Energy> = Quantity(1.3325, MeV) /* Co-60 upper bound*/) = Photon(
    symbol = gamma,
    energy = energy
)

data class RadiationStep(val mode: RadiationEmissionMode, val id: Int) {
    private var receiveProbability = 1.0

    fun crossBlockingMedium(crossedDistance: Quantity<Distance>, crossedMedium: RadiationShieldingMaterial) {
        receiveProbability *= mode.quanta.evaluateTransactionProbability(
            penetrationDepth = crossedDistance,
            medium = crossedMedium
        )
    }

    fun evaluateEffectiveIntensity(dxReceiverSource: Quantity<Distance>) =
        mode.emitterIntensity *
            mode.intensityFunction.evaluateIntensityFactor(dxReceiverSource) *
            receiveProbability
}

data class ReceivedRadiationInfo(
    val shardView: RadiationShardView,
    val intensity: Quantity<Radioactivity>,
)

data class RadioactiveMaterial(val emissionModes: List<RadiationEmissionMode>)

interface RadiationShardView {
    val mode: RadiationEmissionMode
    val system: VoxelDDAThreadedRadiationSystem
    val idSource: Int
    val sphereOfInfluence: BoundingSphere
    val boundingBox: BoundingBox3d
    val sourceView: RadiationSourceView
}

interface RadiationSourceShard : RadiationShardView {
    val source: RadiationSource

    override val sourceView: RadiationSourceView
        get() = source
}

interface RadiationSourceView {
    val material: RadioactiveMaterial
    val system: RadiationSystem
    val idSystem: Int
    val position: Vector3d
    val shardsView: List<RadiationShardView>
}

interface RadiationSource : RadiationSourceView {
    val shards: List<RadiationSourceShard>

    override val shardsView: List<RadiationShardView>
        get() = shards

    fun destroy()
}

data class RadiationFrame(
    val received: List<ReceivedRadiationInfo>,
    val version: Int,
)

// Haven't decided if it is worth pursuing charged particles.
// We'd have to add more things (e.g. tracking the mean quantum energy in radiation steps).
// The charged particles also interact with matter in a big way (so, normal emissions of common isotopes will be dampened out super quickly in a block world, unlike the photons we currently have)
// Neutrons would also be nice, but, it is a lot of work to put this together (and this is a dead project!)

fun electronDensity(ρ: Double, Z: Double, A: Double): BigDecimal {
    val ro = BigDecimal(ρ, CONST_CONTEXT)
    val z = BigDecimal(Z, CONST_CONTEXT)
    val a = BigDecimal(A, CONST_CONTEXT)
    return (NA_B * z * ro) / (a * MU_B)
}

fun rcqfBethe(
    v: BigDecimal,  // particle speed
    z: BigDecimal,  // particle charge relative to e
    n: BigDecimal,  // electron number density (target)
    I: BigDecimal,   // mean excitation energy (target)
): BigDecimal {
    val c = C_B
    val c2 = c * c
    val eps0 = EPS0_B
    val e = E_B
    val e2 = e * e
    val me = Me_B
    val b = v / c
    val b2 = b * b
    val four = BigDecimal(4.0, CONST_CONTEXT)
    val two = BigDecimal(2.0, CONST_CONTEXT)
    val one = BigDecimal(1.0, CONST_CONTEXT)

    return ((four * PI_B) / (me * c2)) *
        ((n * z * z) / b2) *
        ((e2 / (four * PI_B * eps0)).pow(2)) *
        (ln((two * me * c2 * b2) / (I * (one - b2)), CONST_CONTEXT) - b2)
}

interface RadiationListener {
    var position: Vector3d
    val system: VoxelDDAThreadedRadiationSystem
    val idSystem: Int

    fun measureAsync(): Future<RadiationFrame>
}

private const val PROPERTY_RANDOMIZE_RANGE = 0.1
private fun buildBlockList(vararg pairs: Pair<Block, RadiationShieldingMaterial>): Map<Block, RadiationShieldingMaterial> {
    val results = HashMap<Block, RadiationShieldingMaterial>()

    pairs.forEach { (block, material) ->
        val rng = Random(block.javaClass.kotlin.reflectId)

        results[block] = RadiationShieldingMaterial(
            material.density * rng.nextDouble(1.0 - PROPERTY_RANDOMIZE_RANGE, 1.0 + PROPERTY_RANDOMIZE_RANGE),
            material.linearAttenuation
        )
    }

    return results
}

private val radiationMaterialBlocks = buildBlockList(
    Blocks.AIR to AIR,
    Blocks.WATER to WATER,
    Blocks.DIRT to SOIL,
    Blocks.GRASS_BLOCK to SOIL,
    Blocks.CLAY to CLAY,
    Blocks.SAND to SAND,
    Blocks.RED_SAND to SAND,
    Blocks.SANDSTONE to SAND,
    Blocks.GRAVEL to GRAVEL,

    Blocks.BRICKS to CLAY, // just burnt

    Blocks.IRON_BLOCK to IRON,
    Blocks.COPPER_BLOCK to COPPER,

    Blocks.BIRCH_DOOR to BETULA_VERRUCOSA_THIN,
    Blocks.BIRCH_FENCE to BETULA_VERRUCOSA_THIN,
    Blocks.BIRCH_FENCE_GATE to BETULA_VERRUCOSA_THIN,
    Blocks.BIRCH_LEAVES to BETULA_VERRUCOSA_SUPER_THIN,
    Blocks.BIRCH_LOG to BETULA_VERRUCOSA,
    Blocks.BIRCH_PLANKS to BETULA_VERRUCOSA,
    Blocks.BIRCH_PRESSURE_PLATE to BETULA_VERRUCOSA_SUPER_THIN,
    Blocks.BIRCH_SAPLING to BETULA_VERRUCOSA_SUPER_THIN,
    Blocks.BIRCH_SIGN to BETULA_VERRUCOSA_SUPER_THIN,
    Blocks.BIRCH_SLAB to BETULA_VERRUCOSA_HALF,
    Blocks.BIRCH_STAIRS to BETULA_VERRUCOSA_HALF,
    Blocks.BIRCH_TRAPDOOR to BETULA_VERRUCOSA_THIN,
    Blocks.BIRCH_WALL_SIGN to BETULA_VERRUCOSA_THIN,
    Blocks.BIRCH_WOOD to BETULA_VERRUCOSA,

    Blocks.SPRUCE_DOOR to PICEA_GLAUCA_THIN,
    Blocks.SPRUCE_FENCE to PICEA_GLAUCA_THIN,
    Blocks.SPRUCE_FENCE_GATE to PICEA_GLAUCA_THIN,
    Blocks.SPRUCE_LEAVES to PICEA_GLAUCA_SUPER_THIN,
    Blocks.SPRUCE_LOG to PICEA_GLAUCA,
    Blocks.SPRUCE_PLANKS to PICEA_GLAUCA,
    Blocks.SPRUCE_PRESSURE_PLATE to PICEA_GLAUCA_SUPER_THIN,
    Blocks.SPRUCE_SAPLING to PICEA_GLAUCA_SUPER_THIN,
    Blocks.SPRUCE_SIGN to PICEA_GLAUCA_SUPER_THIN,
    Blocks.SPRUCE_SLAB to PICEA_GLAUCA_HALF,
    Blocks.SPRUCE_STAIRS to PICEA_GLAUCA_HALF,
    Blocks.SPRUCE_TRAPDOOR to PICEA_GLAUCA_THIN,
    Blocks.SPRUCE_WALL_SIGN to PICEA_GLAUCA_THIN,
    Blocks.SPRUCE_WOOD to PICEA_GLAUCA,

    Blocks.OAK_DOOR to QUERCUS_FAGACEAE_THIN,
    Blocks.OAK_FENCE to QUERCUS_FAGACEAE_THIN,
    Blocks.OAK_FENCE_GATE to QUERCUS_FAGACEAE_THIN,
    Blocks.OAK_LEAVES to QUERCUS_FAGACEAE_SUPER_THIN,
    Blocks.OAK_LOG to QUERCUS_FAGACEAE,
    Blocks.OAK_PLANKS to QUERCUS_FAGACEAE,
    Blocks.OAK_PRESSURE_PLATE to QUERCUS_FAGACEAE_SUPER_THIN,
    Blocks.OAK_SAPLING to QUERCUS_FAGACEAE_SUPER_THIN,
    Blocks.OAK_SIGN to QUERCUS_FAGACEAE_SUPER_THIN,
    Blocks.OAK_SLAB to QUERCUS_FAGACEAE_HALF,
    Blocks.OAK_STAIRS to QUERCUS_FAGACEAE_HALF,
    Blocks.OAK_TRAPDOOR to QUERCUS_FAGACEAE_THIN,
    Blocks.OAK_WALL_SIGN to QUERCUS_FAGACEAE_THIN,
    Blocks.OAK_WOOD to QUERCUS_FAGACEAE,

    Blocks.BEACON to SODA_LIME_GLASS,
    Blocks.BLACK_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.BLACK_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.BLUE_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.BLUE_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.BROWN_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.BROWN_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.CONDUIT to SODA_LIME_GLASS,
    Blocks.CYAN_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.CYAN_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.END_PORTAL_FRAME to SODA_LIME_GLASS,
    Blocks.GLASS to SODA_LIME_GLASS,
    Blocks.GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.GLOWSTONE to SODA_LIME_GLASS,
    Blocks.GRAY_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.GRAY_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.GREEN_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.GREEN_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.LIGHT_BLUE_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.LIGHT_BLUE_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.LIGHT_GRAY_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.LIGHT_GRAY_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.LIME_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.LIME_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.MAGENTA_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.MAGENTA_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.NETHER_PORTAL to SODA_LIME_GLASS,
    Blocks.ORANGE_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.ORANGE_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.PINK_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.PINK_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.PURPLE_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.PURPLE_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.REDSTONE_LAMP to SODA_LIME_GLASS,
    Blocks.RED_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.RED_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.SEA_LANTERN to SODA_LIME_GLASS,
    Blocks.TINTED_GLASS to SODA_LIME_GLASS,
    Blocks.WHITE_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.WHITE_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,
    Blocks.YELLOW_STAINED_GLASS to SODA_LIME_GLASS,
    Blocks.YELLOW_STAINED_GLASS_PANE to SODA_LIME_GLASS_SUPER_THIN,

    Blocks.OBSIDIAN to OBSIDIAN,
    Blocks.CRYING_OBSIDIAN to OBSIDIAN,

    Blocks.BLUE_ICE to ICE,                // Todo adjust densities for unnatural ice specimens?
    Blocks.FROSTED_ICE to ICE,
    Blocks.ICE to ICE,
    Blocks.PACKED_ICE to ICE,

    Blocks.BLACKSTONE to STONE,
    Blocks.BLACKSTONE_SLAB to STONE,
    Blocks.BLACKSTONE_STAIRS to STONE,
    Blocks.BLACKSTONE_WALL to STONE,

    Blocks.CHISELED_POLISHED_BLACKSTONE to STONE,
    Blocks.CHISELED_RED_SANDSTONE to STONE,
    Blocks.CHISELED_SANDSTONE to STONE,
    Blocks.CHISELED_STONE_BRICKS to STONE_THIN,
    Blocks.COBBLESTONE to STONE,
    Blocks.COBBLESTONE_SLAB to COBBLESTONE_THIN,
    Blocks.COBBLESTONE_STAIRS to COBBLESTONE_THIN,
    Blocks.COBBLESTONE_WALL to COBBLESTONE_THIN,
    Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS to STONE_THIN,
    Blocks.CRACKED_STONE_BRICKS to STONE_THIN,
    Blocks.CUT_RED_SANDSTONE to STONE,
    Blocks.CUT_RED_SANDSTONE_SLAB to STONE_THIN,
    Blocks.CUT_SANDSTONE to STONE,
    Blocks.CUT_SANDSTONE_SLAB to STONE_THIN,
    Blocks.DEEPSLATE_REDSTONE_ORE to STONE,
    Blocks.DRIPSTONE_BLOCK to STONE_SUPER_THIN,
    Blocks.END_STONE to STONE,
    Blocks.END_STONE_BRICKS to STONE_THIN,
    Blocks.END_STONE_BRICK_SLAB to STONE_THIN,
    Blocks.END_STONE_BRICK_STAIRS to STONE_THIN,
    Blocks.END_STONE_BRICK_WALL to STONE,
    Blocks.GILDED_BLACKSTONE to STONE,
    Blocks.GLOWSTONE to STONE,
    Blocks.GRINDSTONE to STONE,
    Blocks.INFESTED_CHISELED_STONE_BRICKS to STONE_THIN,
    Blocks.INFESTED_COBBLESTONE to STONE,
    Blocks.INFESTED_CRACKED_STONE_BRICKS to STONE,
    Blocks.INFESTED_MOSSY_STONE_BRICKS to STONE,
    Blocks.INFESTED_STONE to STONE,
    Blocks.INFESTED_STONE_BRICKS to STONE_THIN,
    Blocks.LODESTONE to STONE,
    Blocks.MOSSY_COBBLESTONE to STONE,
    Blocks.MOSSY_COBBLESTONE_SLAB to STONE_THIN,
    Blocks.MOSSY_COBBLESTONE_STAIRS to STONE_THIN,
    Blocks.MOSSY_COBBLESTONE_WALL to STONE_THIN,
    Blocks.MOSSY_STONE_BRICKS to STONE,
    Blocks.MOSSY_STONE_BRICK_SLAB to STONE_THIN,
    Blocks.MOSSY_STONE_BRICK_STAIRS to STONE_THIN,
    Blocks.MOSSY_STONE_BRICK_WALL to STONE_THIN,
    Blocks.POINTED_DRIPSTONE to STONE,
    Blocks.POLISHED_BLACKSTONE to STONE,
    Blocks.POLISHED_BLACKSTONE_BRICKS to STONE,
    Blocks.POLISHED_BLACKSTONE_BRICK_SLAB to STONE_THIN,
    Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS to STONE_THIN,
    Blocks.POLISHED_BLACKSTONE_BRICK_WALL to STONE_THIN,
    Blocks.POLISHED_BLACKSTONE_BUTTON to STONE_SUPER_THIN,
    Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE to STONE_SUPER_THIN,
    Blocks.POLISHED_BLACKSTONE_SLAB to STONE_THIN,
    Blocks.POLISHED_BLACKSTONE_STAIRS to STONE_THIN,
    Blocks.POLISHED_BLACKSTONE_WALL to STONE_THIN,
    Blocks.REDSTONE_BLOCK to STONE,
    Blocks.REDSTONE_LAMP to STONE,
    Blocks.REDSTONE_ORE to STONE,
    Blocks.REDSTONE_TORCH to STONE,
    Blocks.REDSTONE_WALL_TORCH to STONE,
    Blocks.REDSTONE_WIRE to STONE,
    Blocks.RED_SANDSTONE to STONE,
    Blocks.RED_SANDSTONE_SLAB to STONE_THIN,
    Blocks.RED_SANDSTONE_STAIRS to STONE_THIN,
    Blocks.RED_SANDSTONE_WALL to STONE_THIN,
    Blocks.SANDSTONE to STONE,
    Blocks.SANDSTONE_SLAB to STONE_THIN,
    Blocks.SANDSTONE_STAIRS to STONE_THIN,
    Blocks.SANDSTONE_WALL to STONE_THIN,
    Blocks.SMOOTH_RED_SANDSTONE to STONE,
    Blocks.SMOOTH_RED_SANDSTONE_SLAB to STONE_THIN,
    Blocks.SMOOTH_RED_SANDSTONE_STAIRS to STONE_THIN,
    Blocks.SMOOTH_SANDSTONE to STONE,
    Blocks.SMOOTH_SANDSTONE_SLAB to STONE_THIN,
    Blocks.SMOOTH_SANDSTONE_STAIRS to STONE_THIN,
    Blocks.SMOOTH_STONE to STONE_THIN,
    Blocks.SMOOTH_STONE_SLAB to STONE_THIN,
    Blocks.STONE to STONE,
    Blocks.STONECUTTER to STONE,
    Blocks.STONE_BRICKS to STONE,
    Blocks.STONE_BRICK_SLAB to STONE_THIN,
    Blocks.STONE_BRICK_STAIRS to STONE_THIN,
    Blocks.STONE_BRICK_WALL to STONE_THIN,
    Blocks.STONE_BUTTON to STONE_SUPER_THIN,
    Blocks.STONE_PRESSURE_PLATE to STONE_SUPER_THIN,
    Blocks.STONE_SLAB to STONE_THIN,
    Blocks.STONE_STAIRS to STONE_THIN
)

val RADIATION_SYSTEM_CUTOFF = Quantity(30.0, BECQUEREL)
val RADIATION_SYSTEM_CHUNK_TIMEOUT = Quantity(5.0, MINUTES)
const val RADIATION_SYSTEM_CLEANUP_RATE = 10L
const val RADIATION_SYSTEM_STATS_RATE = 1L

private val instances = ConcurrentHashMap<ServerLevel, VoxelDDAThreadedRadiationSystem>()

interface RadiationSystem {
    fun createSource(position: Vector3d, material: RadioactiveMaterial): RadiationSource
    fun createListener(): RadiationListener
}

private fun getRadiationSystem(level: ServerLevel) =
    instances.getOrPut(level) { VoxelDDAThreadedRadiationSystem(level) }

val ServerLevel.radiationSystem: RadiationSystem get() = getRadiationSystem(this)

private const val PLAYER_SAMPLES_AXIS = 3 // 27 readings total

val PLAYER_ABSORBER = RadiationAbsorberInfo(
    mass = Quantity(60.0, KILOGRAMS),
    material = HUMAN,
    penetrationDepth = Quantity(50.0, CENTIMETERS)
)

@Mod.EventBusSubscriber
object RadiationSystemEvents {
    private val readings = HashMap<ServerPlayer, ArrayList<Future<RadiationFrame>>>()

    @SubscribeEvent
    @JvmStatic
    fun onServerStopping(event: ServerStoppingEvent) {
        LOG.info("Destroying radiation systems")

        event.server.allLevels.forEach {
            getRadiationSystem(it).serverStop()
        }
    }

    var tick = 0

    @SubscribeEvent
    @JvmStatic
    fun onServerTick(event: ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            return
        }

        if (tick++ % 5 != 0) {
            return
        }

        val dt = 1.0 / 20.0 * 5

        ServerLifecycleHooks.getCurrentServer().playerList.players.forEach { player ->
            val system = (player.level as ServerLevel).radiationSystem

            player.getCapability(RadiationCapabilityProvider.CAPABILITY).ifPresent { radiationStorage ->
                val readingTasks = readings.getOrPut(player) { ArrayList() }

                fun dispatchReadings() {
                    val box = player.boundingBox

                    for (ix in 0 until PLAYER_SAMPLES_AXIS) {
                        val x = box.minX + (ix + 1.0) * ((box.maxX - box.minX) / PLAYER_SAMPLES_AXIS)

                        for (iy in 0 until PLAYER_SAMPLES_AXIS) {
                            val y = box.minY + (iy + 1.0) * ((box.maxY - box.minY) / PLAYER_SAMPLES_AXIS)

                            for (iz in 0 until PLAYER_SAMPLES_AXIS) {
                                val z = box.minZ + (iz + 1.0) * ((box.maxZ - box.minZ) / PLAYER_SAMPLES_AXIS)

                                readingTasks.add(
                                    system.createListener().apply {
                                        position = Vector3d(x, y, z)
                                    }.measureAsync()
                                )
                            }
                        }
                    }
                }

                if (readingTasks.isEmpty()) {
                    dispatchReadings()
                } else if (readingTasks.all { it.isDone }) {
                    val frames = readingTasks.map { it.get() }
                    readingTasks.clear()
                    dispatchReadings()

                    val incomingEmissions = HashMap<RadiationShardView, ArrayList<Quantity<Radioactivity>>>()

                    frames.forEach { (received, _) ->
                        received.forEach { (shard, intensity) ->
                            val measurements = incomingEmissions.getOrPut(shard) { ArrayList() }
                            measurements.add(intensity)
                        }
                    }

                    var totalRate = Quantity(0.0, BECQUEREL)
                    var totalAbsorptionRate = Quantity(0.0, GRAY)
                    var totalEquivalentRate = Quantity(0.0, SIEVERT)

                    incomingEmissions.forEach { (shard, intensityReadings) ->
                        totalRate += Quantity(
                            intensityReadings.averageOf { intensity -> !intensity },
                            BECQUEREL
                        )

                        totalAbsorptionRate += Quantity(
                            intensityReadings.averageOf { intensity ->
                                !shard.mode.quanta.evaluateAbsorbedDose(
                                    receiverIntensity = intensity,
                                    interval = Quantity(1.0, SECOND),
                                    absorber = PLAYER_ABSORBER
                                )
                            }, GRAY
                        )


                        totalEquivalentRate += Quantity(
                            intensityReadings.averageOf { intensity ->
                                !shard.mode.quanta.evaluateDoseEquivalent(
                                    receiverIntensity = intensity,
                                    interval = Quantity(1.0, SECOND),
                                    absorber = PLAYER_ABSORBER
                                )
                            }, SIEVERT
                        )
                    }

                    radiationStorage.doseRate = totalRate
                    radiationStorage.absorbedDose += totalAbsorptionRate * dt
                    radiationStorage.equivalentDose += totalEquivalentRate * dt

                    println(
                        "${
                            valueText(!totalEquivalentRate * !Quantity(1.0, HOURS), UnitType.SIEVERT)
                        }/h (${
                            valueText(!radiationStorage.equivalentDose, UnitType.SIEVERT)
                        } total)"
                    )
                }
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onEntityLeaveWorld(event: EntityLeaveLevelEvent) {
        if (event.entity is ServerPlayer) {
            readings.remove(event.entity)
        }
    }
}

class VoxelDDAThreadedRadiationSystem(val level: ServerLevel) : RadiationSystem {
    private val gridRW = ReadWrite(
        SparseGrid3d(WORLD_LOG) {
            TileGrid3d.create(it, WORLD_LOG, AIR)
        }
    )

    private val blockEventProvider = level.getEventSourceProvider()

    private val pendingDeleteChunksRW = ReadWrite(
        HashMap<ChunkPos, Stopwatch>()
    )

    private val executor = Executors.newSingleThreadScheduledExecutor().also {
        it.scheduleAtFixedRate(
            ::runCleanupPass,
            RADIATION_SYSTEM_CLEANUP_RATE,
            RADIATION_SYSTEM_CLEANUP_RATE,
            TimeUnit.SECONDS
        )

        it.scheduleAtFixedRate(
            ::updateStats,
            RADIATION_SYSTEM_STATS_RATE,
            RADIATION_SYSTEM_STATS_RATE,
            TimeUnit.SECONDS
        )
    }

    private val shardBVHRW = ReadWrite(
        SAHIncrBVH<Shard, BoundingBox3d>()
    )

    private data class SerializedChunkAccess(
        val eventSource: BlockEventSource,
        val subscribedSourcesRW: ReadWrite<HashSet<RadiationSource>>,
        val initialize: CountDownLatch,
    )

    private val blockEventSourcesRW = ReadWrite(
        HashMap<ChunkPos, SerializedChunkAccess>()
    )

    private var sourceId = AtomicInteger()
    private var listenerId = AtomicInteger()

    // Statistics:
    private val statisticsSw = Stopwatch()
    private val voxelsTraversedFrame = AtomicLong()
    private val testsFrame = AtomicLong()
    private val timeSpentFrame = AtomicLong()

    private fun loadBlockFrame(frame: BlockEventFrame) {
        gridRW.write {
            frame.set.forEach { (pos, state) ->
                it[pos.toVector3di()] = radiationMaterialBlocks[state.block] ?: AIR
            }
        }
    }

    private fun gtOnBlockEvent(e: GTBSUpdateEvent) {
        loadBlockFrame(e.actual())
    }

    private fun gtOnInitialSync(e: GTBSInitialSyncEvent) {
        loadBlockFrame(e.frame)

        blockEventSourcesRW.read {
            (it[e.source.chunkPos]
                ?: error("Could not get source set")
                ).initialize.countDown()
        }
    }

    private fun serializeChunkAccess(radiationSource: Source, target: ChunkPos) {
        fun syncExisting(): Boolean {
            var synced: Boolean

            blockEventSourcesRW.read { sources ->
                val actualSerializedAccess = sources[target]

                synced = if (actualSerializedAccess != null) {
                    actualSerializedAccess.initialize.await()

                    actualSerializedAccess.subscribedSourcesRW.writeOptionalIf({ !it.contains(radiationSource) }) { subscribedSources ->
                        subscribedSources.add(radiationSource)
                    }

                    radiationSource.actualSerializedChunks.write {
                        it.add(target)
                    }

                    true
                } else false
            }

            return synced
        }

        if (syncExisting()) {
            return
        }

        blockEventSourcesRW.write { sources ->
            if (sources.containsKey(target)) {
                return@write
            }

            require(
                sources.put(
                    target,
                    SerializedChunkAccess(
                        blockEventProvider.openEventSource(target) { eventSource ->
                            eventSource.gtEvents.registerHandler(this::gtOnInitialSync)
                            eventSource.gtEvents.registerHandler(this::gtOnBlockEvent)
                        },
                        ReadWrite(HashSet<RadiationSource>().apply {
                            add(radiationSource)
                        }),
                        CountDownLatch(1)
                    )
                ) == null
            )
        }

        require(syncExisting()) {
            "Failed to sync with existing after creating"
        }
    }

    override fun createSource(position: Vector3d, material: RadioactiveMaterial): RadiationSource {
        return Source(material, this, sourceId.getAndIncrement(), position).also { radSrc ->
            pendingDeleteChunksRW.write {
                val acquired = ArrayList<ChunkPos>()

                it.forEach { (pendingChunk, _) ->
                    if (radSrc.targetChunkRange.contains(pendingChunk.toVector2d())) {
                        acquired.add(pendingChunk)
                    }
                }

                acquired.forEach { chunk ->
                    it.remove(chunk)
                }
            }

            shardBVHRW.write {
                radSrc.shards.forEach { shard ->
                    it.insert(
                        obj = shard,
                        box = shard.boundingBox
                    )
                }
            }
        }
    }

    private fun removeSource(src: Source) {
        shardBVHRW.write {
            src.shards.forEach { shard ->
                it.remove(shard)
            }
        }

        val releasedChunks = ArrayList<ChunkPos>()

        blockEventSourcesRW.read { serializedChunks ->
            src.actualSerializedChunks.read { chunks ->
                chunks.forEach { chunk ->
                    val sourceSet = serializedChunks[chunk]
                        ?: return@forEach

                    sourceSet.subscribedSourcesRW.write {
                        it.remove(src)

                        if (it.isEmpty()) {
                            releasedChunks.add(chunk)
                        }
                    }
                }
            }
        }

        if (releasedChunks.isNotEmpty()) {
            pendingDeleteChunksRW.write {
                releasedChunks.forEach { chunk ->
                    it[chunk] = Stopwatch()
                    println("Queue $chunk")
                }
            }
        }
    }

    override fun createListener(): RadiationListener = Listener(
        this, listenerId.getAndIncrement()
    )

    private fun computeResultAsync(listener: Listener) = executor.submit<RadiationFrame> {
        val frame: RadiationFrame

        val time = measureNanoTime {
            val listenerPos = listener.position

            val shards: ArrayList<Shard>
            shardBVHRW.read {
                shards = it.queryAll { data, box, _ ->
                    if (!box.contains(listener.position)) {
                        return@queryAll false
                    }

                    if (data == null) {
                        return@queryAll true
                    }

                    data.sphereOfInfluence.contains(listener.position)
                }
            }

            if (shards.isEmpty()) {
                frame = RadiationFrame(listOf(), listener.getVersionAndIncr())
                return@measureNanoTime
            }

            testsFrame.incrementAndGet()

            val emissionMapBi = mutableBiMapOf<Shard, RadiationStep>()

            shards.forEachIndexed { index, shard ->
                emissionMapBi.add(
                    shard,
                    RadiationStep(shard.mode, index)
                )
            }

            val distinctPaths = mutableMultiMapOf<Source, Shard>()
            val pathScans = HashMap<Source, ArrayList<BlockPos>>()

            shards.forEach { distinctPaths[it.source].add(it) }

            fun getRay(source: Source) = Ray3d(
                origin = source.position,
                direction = source.position directionTo listenerPos
            )

            distinctPaths.keys.forEach { source ->
                val path = ArrayList<BlockPos>()
                val traceChunks = HashSet<ChunkPos>()

                dda(getRay(source), source.position..listenerPos) { x, y, z ->
                    val pos = BlockPos(x, y, z)
                    path.add(pos)

                    val chunk = ChunkPos(pos)
                    if (traceChunks.add(chunk)) {
                        serializeChunkAccess(
                            source,
                            chunk
                        )
                    }

                    return@dda true
                }

                pathScans[source] = path
            }

            var traversed = 0L

            gridRW.read { grid ->
                distinctPaths.keys.forEach { source ->
                    val activeEmissions = ArrayList(
                        distinctPaths[source].map {
                            emissionMapBi.forward[it]!!
                        }
                    )

                    val ray = getRay(source)
                    val ddaScan = pathScans[source]!!

                    for (voxel in ddaScan) {
                        val blockCenter = Vector3d(voxel.x + 0.5, voxel.y + 0.5, voxel.z + 0.5)

                        val intersection = (ray intersectionWith BoundingBox3d.centerSize(blockCenter, 1.0))
                            ?: continue // Should not happen (make sure you keep a breakpoint here at all times!)

                        val entryPos = ray.evaluate(intersection.entry)
                        val exitPos = ray.evaluate(intersection.exit)

                        val penetrationDepth = Quantity<Distance>(entryPos..exitPos)
                        val blockMaterial = grid[voxel.x, voxel.y, voxel.z]
                        val dxSourceBlock = Quantity<Distance>(source.position..blockCenter)

                        activeEmissions.removeAll { emission ->
                            emission.crossBlockingMedium(
                                crossedDistance = penetrationDepth,
                                crossedMedium = blockMaterial
                            )

                            val isDampened =
                                emission.evaluateEffectiveIntensity(dxSourceBlock) < RADIATION_SYSTEM_CUTOFF
                            if (isDampened) require(emissionMapBi.removeBackward(emission))

                            isDampened
                        }

                        traversed++

                        if (activeEmissions.isEmpty()) {
                            break
                        }
                    }
                }
            }

            voxelsTraversedFrame.getAndAdd(traversed)

            frame = RadiationFrame(
                emissionMapBi.forward.map { (shard, emission) ->
                    ReceivedRadiationInfo(
                        shard,
                        emission.evaluateEffectiveIntensity(
                            Quantity(listener.position..shard.source.position)
                        )
                    )
                },
                listener.getVersionAndIncr()
            )

            return@measureNanoTime
        }

        timeSpentFrame.addAndGet(time)

        return@submit frame
    }

    private fun runCleanupPass() {
        pendingDeleteChunksRW.writeOptionalIf({ it.isNotEmpty() }) { pendingDelete ->
            val cleanedUp = ArrayList<ChunkPos>()

            pendingDelete.forEach { (chunk, timer) ->
                if (timer.total < RADIATION_SYSTEM_CHUNK_TIMEOUT) {
                    return@forEach
                }

                blockEventSourcesRW.read { sources ->
                    val sourceSet = sources[chunk]
                        ?: error("Lingering timeout chunk")

                    sourceSet.subscribedSourcesRW.write { subscribed ->
                        if (subscribed.isNotEmpty()) {
                            println("unexpected non-empty subscribers")
                        } else {
                            sources.remove(chunk)

                            gridRW.write { grid ->
                                for (y in level.level.minBuildHeight..level.maxBuildHeight step 16) {
                                    if (grid.subGrids.remove(
                                            grid.createTileKey(
                                                chunk.middleBlockX,
                                                y,
                                                chunk.middleBlockZ
                                            )
                                        ) == null
                                    ) {
                                        println("unexpected failure to remove grid")
                                    }
                                }
                            }
                        }
                    }
                }

                cleanedUp.add(chunk)
                println("Chunk $chunk timed out")
            }

            cleanedUp.forEach {
                pendingDelete.remove(it)
            }
        }
    }

    private fun updateStats() {
        val dt = statisticsSw.sample()
        val traversed = voxelsTraversedFrame.getAndSet(0)
        val timeSpent = Quantity(timeSpentFrame.getAndSet(0).toDouble(), NANOSECONDS)
        val tests = testsFrame.getAndSet(0)

        println("Time spent: ${(timeSpent / dt).formattedPercentN()}")
        println("VPS: ${(traversed.toDouble() / !dt).formatted()}")
        println("TPS: ${(tests.toDouble() / !dt).formatted()}")
    }

    fun serverStop() {
        executor.shutdown()
        instances.remove(level)
    }

    private data class Source(
        override val material: RadioactiveMaterial,
        override val system: VoxelDDAThreadedRadiationSystem,
        override val idSystem: Int,
        override val position: Vector3d,
    ) : RadiationSource {
        override val shards = createShards()

        val actualSerializedChunks = ReadWrite(HashSet<ChunkPos>())

        val targetChunkRange = shards.map { it.chunksRange }.reduce { acc, shard -> acc u shard }

        private fun createShards() = material.emissionModes.mapIndexed { index, mode ->
            Shard(mode, system, this, index)
        }

        override fun destroy() {
            system.removeSource(this)
        }
    }

    private data class Shard(
        override val mode: RadiationEmissionMode,
        override val system: VoxelDDAThreadedRadiationSystem,
        override val source: Source,
        override val idSource: Int,
    ) : RadiationSourceShard {
        override val sphereOfInfluence get() = BoundingSphere(source.position, !mode.upperBound)
        override val boundingBox get() = BoundingBox3d(sphereOfInfluence)

        val chunksRange = BoundingBox2d(
            ChunkPos(boundingBox.min.floor.toBlockPos()).toVector2d(),
            ChunkPos(boundingBox.max.ceiling.toBlockPos()).toVector2d()
        )
    }

    private data class Listener(
        override val system: VoxelDDAThreadedRadiationSystem,
        override val idSystem: Int,
    ) : RadiationListener {
        override var position: Vector3d = Vector3d.zero
        private val version = AtomicInteger()
        fun getVersionAndIncr() = version.getAndIncrement()
        override fun measureAsync(): Future<RadiationFrame> = system.computeResultAsync(this)
    }

    companion object {
        private const val WORLD_LOG = 4
    }
}
