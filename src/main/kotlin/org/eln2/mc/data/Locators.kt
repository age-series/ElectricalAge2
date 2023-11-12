@file:Suppress("UNCHECKED_CAST")

package org.eln2.mc.data

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.ageseries.libage.data.ImmutableBiMapView
import org.ageseries.libage.data.biMapOf
import org.eln2.mc.*
import org.eln2.mc.common.cells.foundation.NEGATIVE_PIN
import org.eln2.mc.common.cells.foundation.POSITIVE_PIN
import org.eln2.mc.common.parts.foundation.getPartConnectionOrNull
import org.eln2.mc.mathematics.Base6Direction3d
import org.eln2.mc.mathematics.Base6Direction3dMask

typealias BlockLocator = BlockPos
typealias FaceLocator = Direction

data class FacingLocator(val forwardWorld: Direction) {
    override fun hashCode() = forwardWorld.valueHashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FacingLocator

        if (forwardWorld != other.forwardWorld) return false

        return true
    }
}

interface LocatorSerializer {
    fun toNbt(obj: Any): CompoundTag
    fun fromNbt(tag: CompoundTag): Any
}

fun classId(c: Class<*>): String = c.canonicalName
fun locatorId(loc: Class<*>): String = "$${classId(loc)}"

val locatorClassNames: ImmutableBiMapView<String, Class<*>> = biMapOf(
    locatorId(BlockLocator::class.java) to BlockLocator::class.java,
    locatorId(FacingLocator::class.java) to FacingLocator::class.java,
    locatorId(FaceLocator::class.java) to FaceLocator::class.java
)

val locatorSerializers: ImmutableBiMapView<Class<*>, LocatorSerializer> = biMapOf(
    BlockLocator::class.java to object : LocatorSerializer {
        override fun toNbt(obj: Any): CompoundTag =
            CompoundTag().apply { putBlockPos("Position", (obj as BlockLocator)) }

        override fun fromNbt(tag: CompoundTag): BlockLocator =
            BlockLocator(tag.getBlockPos("Position"))
    },

    FacingLocator::class.java to object : LocatorSerializer {
        override fun toNbt(obj: Any): CompoundTag =
            CompoundTag().apply { putDirection("Forward", (obj as FacingLocator).forwardWorld) }

        override fun fromNbt(tag: CompoundTag): Any =
            FacingLocator(tag.getDirection("Forward"))
    },

    FaceLocator::class.java to object : LocatorSerializer {
        override fun toNbt(obj: Any): CompoundTag =
            CompoundTag().apply { putDirection("Face", (obj as FaceLocator)) }

        override fun fromNbt(tag: CompoundTag): Any =
            tag.getDirection("Face")
    }
)

fun getLocatorSerializer(locatorClass: Class<*>): LocatorSerializer {
    return locatorSerializers.forward[locatorClass] ?: error("Failed to find serializer definition for $locatorClass")
}

class LocatorSetBuilder {
    val locatorMap = HashMap<Class<*>, Any>()

    val locators: Collection<Any> get() = locatorMap.values

    fun bindLocators(): HashMap<Class<*>, Any> = locatorMap.bind()

    fun withLocator(locator: Any): LocatorSetBuilder {
        if (locatorMap.put(locator.javaClass, locator) != null) {
            error("Duplicate locator $locator")
        }

        return this
    }

    fun build() = Locator.createImmutable(locatorMap)
}

class Locator private constructor(val locatorMap: Map<Class<*>, Any>) {
    fun <T> get(c: Class<T>): T? = locatorMap[c] as? T

    inline fun <reified T> get(): T? = get(T::class.java)

    fun has(c: Class<*>): Boolean = get(c) != null

    inline fun <reified T> has(): Boolean = has(T::class.java)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (javaClass != other?.javaClass) {
            return false
        }

        other as Locator

        if (locatorMap.size != other.locatorMap.size) {
            return false
        }

        for (c in locatorMap.keys) {
            val otherValue = other.locatorMap[c]
                ?: return false

            if (otherValue != locatorMap[c]!!) {
                return false
            }
        }

        return true
    }

    override fun hashCode(): Int {
        return locatorMap.hashCode()
    }

    fun toNbt(): CompoundTag {
        val result = CompoundTag()
        val locatorList = ListTag()

        locatorMap.forEach { (locatorClass, locatorInst) ->
            val locatorCompound = CompoundTag()

            locatorCompound.putString(
                "Class",
                locatorClassNames.backward[locatorClass] ?: error("Failed to get locator name for $locatorClass")
            )

            val serializer = getLocatorSerializer(locatorClass)

            locatorCompound.put("Locator", serializer.toNbt(locatorInst))
            locatorList.add(locatorCompound)
        }

        result.put("Locators", locatorList)

        return result
    }

    override fun toString() = buildString {
        locatorMap.forEach { (locatorClass, locator) ->
            appendLine("${locatorClass.name}: $locator")
        }
    }

    companion object {
        fun fromNbt(compoundTag: CompoundTag): Locator {
            val builder = LocatorSetBuilder()
            val list = compoundTag.get("Locators") as ListTag

            list.forEach { tag ->
                val locatorCompound = tag as CompoundTag

                val locatorClassName = locatorCompound.getString("Class")

                val locatorClass = locatorClassNames.forward[locatorClassName]
                    ?: error("Failed to solve locator class $locatorClassName")

                val serializer = getLocatorSerializer(locatorClass)

                builder.withLocator(serializer.fromNbt(locatorCompound.getCompound("Locator")))
            }

            return Locator(builder.locatorMap)
        }

        fun createImmutable(source: Map<Class<*>, Any>) = Locator(source.toMap())
    }
}

inline fun <reified T> Locator.requireLocator(noinline message: (() -> Any)? = null): T {
    val result = this.get<T>()

    if (message != null) require(result != null, message)
    else require(result != null) { "Requirement of ${T::class.java} is not fulfilled" }

    return result
}

fun interface LocationRelationshipRule {
    fun acceptsRelationship(descriptor: Locator, target: Locator): Boolean
}

class LocatorRelationRuleSet {
    private val rules = ArrayList<LocationRelationshipRule>()

    fun with(rule: LocationRelationshipRule): LocatorRelationRuleSet {
        rules.add(rule)
        return this
    }

    fun accepts(descriptor: Locator, target: Locator): Boolean {
        return rules.all { r -> r.acceptsRelationship(descriptor, target) }
    }
}

fun LocatorRelationRuleSet.withDirectionRulePlanar(mask: Base6Direction3dMask): LocatorRelationRuleSet {
    return this.with { a, b ->
        mask.has(a.findDirActualPlanarOrNull(b) ?: return@with false)
    }
}

fun LocatorRelationRuleSet.withDirectionRulePart(mask: Base6Direction3dMask): LocatorRelationRuleSet {
    return this.with { a, b ->
        mask.has(a.findDirActualPartOrNull(b) ?: return@with false)
    }
}


fun Locator.findDirActualPlanarOrNull(other: Locator): Base6Direction3d? {
    val actualPosWorld = this.get<BlockLocator>() ?: return null
    val actualIdWorld = this.get<FacingLocator>() ?: return null
    val actualFaceWorld = this.get<FaceLocator>() ?: return null
    val targetPosWorld = other.get<BlockLocator>() ?: return null
    val dir = actualPosWorld.directionTo(targetPosWorld) ?: return null

    return Base6Direction3d.fromForwardUp(actualIdWorld.forwardWorld, actualFaceWorld, dir)
}

fun Locator.findDirActualPartOrNull(other: Locator): Base6Direction3d? {
    return getPartConnectionOrNull(this, other)?.directionPart
}

fun Locator.findDirActualPlanar(other: Locator): Base6Direction3d {
    return this.findDirActualPlanarOrNull(other) ?: error("Failed to get relative rotation direction")
}

fun Locator.findDirActualPart(other: Locator): Base6Direction3d {
    return this.findDirActualPartOrNull(other) ?: error("Failed to get relative rotation direction (part)")
}

enum class Pole(val conventionalPin: Int) {
    Plus(POSITIVE_PIN),
    Minus(NEGATIVE_PIN)
}

fun interface PoleMap {
    fun evaluateOrNull(sourceLocator: Locator, targetLocator: Locator): Pole?
}

/**
 * Maps the target location to a pole, when looking from the actual location.
 * @param sourceLocator The observer's location.
 * @param targetLocator The target's location.
 * @return The pole [targetLocator] corresponds to.
 * */
fun PoleMap.evaluate(sourceLocator: Locator, targetLocator: Locator): Pole =
    evaluateOrNull(sourceLocator, targetLocator).requireNotNull {
        "Unhandled pole map direction $sourceLocator $targetLocator $this"
    }

/**
 * Creates a [PoleMap] that maps [plusDir] to plus and [minusDir] to minus.
 * These directions are in the observer's frame. Fused positions are not allowed (like with multiparts)
 * This means that, from the object's perspective, [Pole.Plus] is returned when the other object is towards [plusDir], and [Pole.Minus] is returned when the target is towards [minusDir].
 * */
fun directionPoleMapPlanar(plusDir: Base6Direction3d = Base6Direction3d.Front, minusDir: Base6Direction3d = Base6Direction3d.Back) =
    PoleMap { actualDescr, targetDescr ->
        when (actualDescr.findDirActualPlanarOrNull(targetDescr)) {
            plusDir -> Pole.Plus
            minusDir -> Pole.Minus
            else -> null
        }
    }

/**
 * Creates a [PoleMap] that maps [plusDir] to plus and [minusDir] to minus.
 * These directions are in the observer's frame.
 * This means that, from the object's perspective, [Pole.Plus] is returned when the other object is towards [plusDir], and [Pole.Minus] is returned when the target is towards [minusDir].
 * */
fun directionPoleMapPart(plusDir: Base6Direction3d = Base6Direction3d.Front, minusDir: Base6Direction3d = Base6Direction3d.Back) =
    PoleMap { actualDescr, targetDescr ->
        when (actualDescr.findDirActualPartOrNull(targetDescr)) {
            plusDir -> Pole.Plus
            minusDir -> Pole.Minus
            else -> null
        }
    }
