@file:Suppress("UNCHECKED_CAST")

package org.eln2.mc.data

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.ageseries.libage.data.ImmutableBiMapView
import org.ageseries.libage.data.biMapOf
import org.eln2.mc.*
import org.eln2.mc.mathematics.DirectionMask
import org.eln2.mc.mathematics.RelativeDir
import java.util.function.Supplier

interface Locator<Param>
interface Positional
interface Directional

data class BlockPosLocator(val pos: BlockPos) : Locator<Positional>
data class IdentityDirectionLocator(val forwardWorld: Direction) : Locator<Directional> {
    override fun hashCode() = forwardWorld.valueHashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdentityDirectionLocator

        if (forwardWorld != other.forwardWorld) return false

        return true
    }
}

data class BlockFaceLocator(val faceWorld: Direction) : Locator<Directional> {
    override fun hashCode() = faceWorld.valueHashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlockFaceLocator

        if (faceWorld != other.faceWorld) return false

        return true
    }
}

interface LocatorSerializer {
    fun toNbt(obj: Any): CompoundTag
    fun fromNbt(tag: CompoundTag): Any
}

fun classId(c: Class<*>): String = c.canonicalName
fun locatorId(param: Class<*>, loc: Class<*>): String = "${classId(param)}: ${classId(loc)}"

val paramClassNames: ImmutableBiMapView<String, Class<*>> = biMapOf(
    classId(Positional::class.java) to Positional::class.java,
    classId(Directional::class.java) to Directional::class.java
)

val locatorClassNames: ImmutableBiMapView<String, Class<*>> = biMapOf(
    locatorId(Positional::class.java, BlockPosLocator::class.java) to BlockPosLocator::class.java,
    locatorId(Directional::class.java, IdentityDirectionLocator::class.java) to IdentityDirectionLocator::class.java,
    locatorId(Directional::class.java, BlockFaceLocator::class.java) to BlockFaceLocator::class.java
)

val locatorSerializers: ImmutableBiMapView<Class<*>, LocatorSerializer> = biMapOf(
    BlockPosLocator::class.java to object : LocatorSerializer {
        override fun toNbt(obj: Any): CompoundTag =
            CompoundTag().apply { putBlockPos("Pos", (obj as BlockPosLocator).pos) }

        override fun fromNbt(tag: CompoundTag): BlockPosLocator =
            BlockPosLocator(tag.getBlockPos("Pos"))
    },

    IdentityDirectionLocator::class.java to object : LocatorSerializer {
        override fun toNbt(obj: Any): CompoundTag =
            CompoundTag().apply { putDirection("Forward", (obj as IdentityDirectionLocator).forwardWorld) }

        override fun fromNbt(tag: CompoundTag): Any =
            IdentityDirectionLocator(tag.getDirection("Forward"))
    },

    BlockFaceLocator::class.java to object : LocatorSerializer {
        override fun toNbt(obj: Any): CompoundTag =
            CompoundTag().apply { putDirection("Face", (obj as BlockFaceLocator).faceWorld) }

        override fun fromNbt(tag: CompoundTag): Any =
            BlockFaceLocator(tag.getDirection("Face"))
    }
)

val locatorSetFactories: ImmutableBiMapView<Class<*>, Supplier<LocatorSet<*>>> = biMapOf(
    Positional::class.java to Supplier { LocatorSet<Positional>() },
    Directional::class.java to Supplier { LocatorSet<Directional>() }
)

fun getLocatorSerializer(locatorClass: Class<*>): LocatorSerializer {
    return locatorSerializers.forward[locatorClass]
        ?: error("Failed to find serializer definition for $locatorClass")
}

class LocatorSet<Param> {
    private val locatorMap = HashMap<Class<*>, Locator<Param>>()

    fun copy(): LocatorSet<Param> {
        val result = LocatorSet<Param>()

        locatorMap.forEach { (k, v) ->
            result.add(k, v)
        }

        return result
    }

    val locators: Collection<Locator<Param>> get() = locatorMap.values
    fun bindLocators(): HashMap<Class<*>, Locator<Param>> = locatorMap.bind()

    fun <T : Locator<Param>> withLocator(c: Class<T>, l: T): LocatorSet<Param> {
        if (locatorMap.put(c, l) != null) {
            error("Duplicate locator $c $l")
        }

        return this
    }

    fun add(c: Class<*>, instance: Locator<*>) {
        if (locatorMap.put(c, instance as Locator<Param>) != null) {
            error("Duplicate locator $c")
        }
    }

    inline fun <reified T : Locator<Param>> withLocator(l: T): LocatorSet<Param> = withLocator(T::class.java, l)
    fun <T : Locator<Param>> get(c: Class<T>): T? = locatorMap[c] as? T
    inline fun <reified T : Locator<Param>> get(): T? = get(T::class.java)
    fun <T : Locator<Param>> has(c: Class<T>): Boolean = get(c) != null
    inline fun <reified T : Locator<Param>> has(): Boolean = has(T::class.java)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (javaClass != other?.javaClass) {
            return false
        }

        other as LocatorSet<*>

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
}

class LocationDescriptor {
    private val locatorSets = HashMap<Class<*>, LocatorSet<*>>()

    fun copy(): LocationDescriptor {
        val result = LocationDescriptor()

        locatorSets.forEach { (k, v) ->
            result.withLocatorSet(k, v.copy())
        }

        return result
    }

    fun withLocatorSet(c: Class<*>, l: LocatorSet<*>): LocationDescriptor {
        if (locatorSets.put(c, l) != null) {
            error("Duplicate parameter $c $l")
        }

        return this
    }

    inline fun <reified Param> withLocatorSet(set: LocatorSet<Param>): LocationDescriptor {
        return withLocatorSet(Param::class.java, set)
    }

    fun <Param> getLocatorSet(c: Class<Param>): LocatorSet<Param>? {
        return locatorSets[c] as? LocatorSet<Param>
    }

    inline fun <reified Param> getLocatorSet(): LocatorSet<Param>? {
        return getLocatorSet(Param::class.java)
    }

    fun <Param> hasLocatorSet(c: Class<Param>): Boolean {
        return getLocatorSet(c) != null
    }

    inline fun <reified Param> hasLocatorSet(): Boolean {
        return hasLocatorSet(Param::class.java)
    }

    fun <Param> withLocator(
        locatorClass: Class<Locator<Param>>,
        paramClass: Class<Param>,
        l: Locator<Param>,
    ): LocationDescriptor {
        val set = locatorSets.getOrPut(paramClass) { LocatorSet<Param>() } as LocatorSet<Param>
        set.withLocator(locatorClass, l)

        return this
    }

    inline fun <reified Param> withLocator(l: Locator<Param>): LocationDescriptor {
        return withLocator(l.javaClass, Param::class.java, l)
    }

    inline fun <reified Param, reified Loc : Locator<Param>> getLocator(): Loc? {
        val set = getLocatorSet<Param>() ?: return null
        return set.get()
    }

    inline fun <reified Param, reified Loc : Locator<Param>> hasLocator(): Boolean {
        val set = getLocatorSet<Param>() ?: return false
        return set.has<Loc>()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (javaClass != other?.javaClass) {
            return false
        }

        other as LocationDescriptor

        if (locatorSets != other.locatorSets) return false

        return true
    }

    override fun hashCode(): Int {
        var h = 0

        locatorSets.values.forEach { ls ->
            ls.locators.forEach { locator ->
                h += locator.hashCode()
            }
        }

        return h
    }

    fun toNbt(): CompoundTag {
        val result = CompoundTag()

        val setList = ListTag()

        locatorSets.forEach { (paramClass, locatorSet) ->
            val setCompound = CompoundTag()

            setCompound.putString(
                "ParamClass",
                paramClassNames.backward[paramClass]
                    ?: error("Failed to get param name for $paramClass")
            )

            val locatorList = ListTag()

            locatorSet.bindLocators().forEach { (locatorClass, locatorInst) ->
                val locatorCompound = CompoundTag()

                locatorCompound.putString(
                    "LocatorClass",
                    locatorClassNames.backward[locatorClass]
                        ?: error("Failed to get locator name for $locatorClass")
                )

                val serializer = getLocatorSerializer(locatorClass)
                locatorCompound.put("Locator", serializer.toNbt(locatorInst))
                locatorList.add(locatorCompound)
            }

            setCompound.put("Locators", locatorList)

            setList.add(setCompound)
        }

        result.put("Sets", setList)

        return result
    }

    companion object {
        fun fromNbt(compoundTag: CompoundTag): LocationDescriptor {
            val result = LocationDescriptor()

            (compoundTag.get("Sets") as ListTag).map { it as CompoundTag }.forEach { setCompound ->
                val paramClassName = setCompound.getString("ParamClass")
                val paramClass = paramClassNames.forward[paramClassName]
                    ?: error("Failed to solve param class $paramClassName")

                val set = result.locatorSets.getOrPut(paramClass) {
                    (locatorSetFactories.forward[paramClass] ?: error("Failed to get locator set factory $paramClass"))
                        .get()
                }

                (setCompound.get("Locators") as ListTag).map { it as CompoundTag }.forEach { locatorCompound ->
                    val locatorClassName = locatorCompound.getString("LocatorClass")
                    val locatorClass = locatorClassNames.forward[locatorClassName]
                        ?: error("Failed to solve locator class $locatorClassName")

                    val serializer = getLocatorSerializer(locatorClass)
                    set.add(locatorClass, serializer.fromNbt(locatorCompound.getCompound("Locator")) as Locator<*>)
                }
            }

            return result
        }
    }
}

inline fun <reified Param> LocationDescriptor.requireSp(noinline message: (() -> Any)? = null) {
    if (message != null) require(this.hasLocatorSet<Param>(), message)
    else require(this.hasLocatorSet<Param>()) { "Requirement of ${Param::class.java} is not fulfilled" }
}

inline fun <reified Param, reified Loc : Locator<Param>> LocationDescriptor.requireLocator(noinline message: (() -> Any)? = null): Loc {
    if (message != null) require(this.hasLocator<Param, Loc>(), message)
    else require(this.hasLocator<Param, Loc>()) { "Requirement of ${Param::class.java}, ${Loc::class.java} is not fulfilled" }

    return this.getLocator<Param, Loc>()!!
}

// Wrappers:

fun LocationDescriptor.requireBlockPosLoc(message: (() -> Any)? = null): BlockPos =
    this.requireLocator<Positional, BlockPosLocator>(message).pos


fun LocationDescriptor.requireBlockFaceLoc(message: (() -> Any)? = null): Direction =
    this.requireLocator<Directional, BlockFaceLocator>(message).faceWorld


fun LocationDescriptor.requireIdentityDirLoc(message: (() -> Any)? = null): Direction =
    this.requireLocator<Directional, IdentityDirectionLocator>(message).forwardWorld

fun interface ILocationRelationshipRule {
    fun acceptsRelationship(descriptor: LocationDescriptor, target: LocationDescriptor): Boolean
}

class LocatorRelationRuleSet {
    private val rules = ArrayList<ILocationRelationshipRule>()

    fun with(rule: ILocationRelationshipRule): LocatorRelationRuleSet {
        rules.add(rule)
        return this
    }

    fun accepts(descriptor: LocationDescriptor, target: LocationDescriptor): Boolean {
        return rules.all { r -> r.acceptsRelationship(descriptor, target) }
    }
}

fun LocatorRelationRuleSet.withDirectionActualRule(mask: DirectionMask): LocatorRelationRuleSet {
    return this.with { a, b ->
        mask.has(a.findDirActualOrNull(b) ?: return@with false)
    }
}

fun LocationDescriptor.findDirActualOrNull(other: LocationDescriptor): RelativeDir? {
    val actualPosWorld = this.getLocator<Positional, BlockPosLocator>() ?: return null
    val actualIdWorld = this.getLocator<Directional, IdentityDirectionLocator>() ?: return null
    val actualFaceWorld = this.getLocator<Directional, BlockFaceLocator>() ?: return null
    val targetPosWorld = other.getLocator<Positional, BlockPosLocator>() ?: return null

    return RelativeDir.fromForwardUp(
        actualIdWorld.forwardWorld,
        actualFaceWorld.faceWorld,
        actualPosWorld.pos.directionTo(targetPosWorld.pos)
            ?: return null
    )
}

fun LocationDescriptor.findDirActual(other: LocationDescriptor): RelativeDir {
    return this.findDirActualOrNull(other) ?: error("Failed to get relative rotation direction")
}
