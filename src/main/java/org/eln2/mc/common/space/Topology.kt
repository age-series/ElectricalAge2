package org.eln2.mc.common.space

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.eln2.mc.extensions.directionTo

interface Locator<Param>
interface R3
interface SO3
data class BlockPosLocator(val pos: BlockPos) : Locator<R3>
data class IdentityDirectionLocator(val forward: Direction): Locator<SO3>
data class BlockFaceLocator(val innerFace: Direction) : Locator<SO3>

class LocatorSet<Param> {
    private val locators = HashMap<Class<*>, Locator<Param>>()

    fun<T : Locator<Param>> withLocator(c: Class<T>, l : T): LocatorSet<Param> {
        if(locators.put(c, l) != null) {
            error("Duplicate locator $c $l")
        }

        return this
    }

    inline fun<reified T : Locator<Param>> withLocator(l: T): LocatorSet<Param> = withLocator(T::class.java, l)
    fun <T : Locator<Param>> get(c: Class<T>): T? = locators[c] as? T
    inline fun<reified T : Locator<Param>> get(): T? = get(T::class.java)
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

        if(locators.size != other.locators.size) {
            return false
        }

        for(c in locators.keys) {
            val otherValue = other.locators[c]
                ?: return false

            if(otherValue != locators[c]!!){
                return false
            }
        }

        return true
    }

    override fun hashCode(): Int {
        return locators.hashCode()
    }
}

class LocationDescriptor {
    private val locatorSets = HashMap<Class<*>, LocatorSet<*>>()

    fun withLocatorSet(c: Class<*>, l: LocatorSet<*>): LocationDescriptor {
        if(locatorSets.put(c, l) != null){
            error("Duplicate parameter $c $l")
        }

        return this
    }

    inline fun<reified Param> withLocatorSet(set: LocatorSet<Param>): LocationDescriptor {
        return withLocatorSet(Param::class.java, set)
    }

    fun<Param> getLocatorSet(c: Class<Param>): LocatorSet<Param>? {
        return locatorSets[c] as? LocatorSet<Param>
    }

    inline fun<reified Param> getLocatorSet(): LocatorSet<Param>? {
        return getLocatorSet(Param::class.java)
    }

    fun <Param> hasLocatorSet(c: Class<Param>): Boolean {
        return getLocatorSet(c) != null
    }

    inline fun<reified Param> hasLocatorSet(): Boolean {
        return hasLocatorSet(Param::class.java)
    }

    fun<Param> withLocator(locatorClass: Class<Locator<Param>>, paramClass: Class<Param>, l: Locator<Param>): LocationDescriptor {
        val set = locatorSets.getOrPut(paramClass) { LocatorSet<Param>() } as LocatorSet<Param>
        set.withLocator(locatorClass, l)

        return this
    }

    inline fun<reified Param> withLocator(l: Locator<Param>): LocationDescriptor {
        return withLocator(l.javaClass, Param::class.java, l)
    }

    inline fun<reified Param, reified Loc: Locator<Param>> getLocator(): Loc? {
        val set = getLocatorSet<Param>() ?: return null
        return set.get()
    }

    inline fun<reified Param, reified Loc: Locator<Param>> hasLocator(): Boolean {
        val set = getLocatorSet<Param>() ?: return false
        return set.has<Loc>()
    }
}

inline fun <reified Param> LocationDescriptor.requireSp(noinline message: (() -> Any)? = null) {
    if(message != null) require(this.hasLocatorSet<Param>(), message)
    else require(this.hasLocatorSet<Param>()) { "Requirement of ${Param::class.java} is not fulfilled"}
}

inline fun<reified Param, reified Loc: Locator<Param>> LocationDescriptor.requireLocator(noinline message: (() -> Any)? = null) {
    if(message != null) require(this.hasLocator<Param, Loc>(), message)
    else require(this.hasLocator<Param, Loc>()) { "Requirement of ${Param::class.java}, ${Loc::class.java} is not fulfilled"}
}

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
        mask.has(a.findDirectionActual(b) ?: return@with false)
    }
}

annotation class Sp<Param>
annotation class Location<Locator>

@Sp<R3> @Sp<SO3>
@Location<BlockPosLocator> @Location<IdentityDirectionLocator> @Location<BlockFaceLocator>
fun LocationDescriptor.findDirectionActual(other: LocationDescriptor) : RelativeRotationDirection? {
    val actualPosWorld = this.getLocator<R3, BlockPosLocator>() ?: return null
    val actualIdWorld = this.getLocator<SO3, IdentityDirectionLocator>() ?: return null
    val actualFaceWorld = this.getLocator<SO3, BlockFaceLocator>() ?: return null
    val targetPosWorld = other.getLocator<R3, BlockPosLocator>() ?: return null

    return RelativeRotationDirection.fromForwardUp(
        actualIdWorld.forward,
        actualFaceWorld.innerFace.opposite,
        targetPosWorld.pos.directionTo(actualPosWorld.pos)
            ?: error("Failed to get transform from $actualPosWorld to $targetPosWorld")
    )
}
