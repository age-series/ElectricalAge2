package org.eln2.mc.common.cells.foundation

import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import org.eln2.mc.common.parts.foundation.ConnectionMode
import org.eln2.mc.common.space.RelativeRotationDirection

interface ICellContainer {
    fun getCells(): ArrayList<CellBase>
    fun query(query: CellQuery): CellBase?
    fun queryNeighbors(actualCell: CellBase): ArrayList<CellNeighborInfo>

    fun recordConnection(actualCell: CellBase, remoteCell: CellBase)

    fun topologyChanged()

    val manager: CellGraphManager
}

interface Locator<Param>
interface R3
interface SO3

data class BlockPosLocator(val pos: BlockPos) : Locator<R3>
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

    inline fun<reified Param, reified Loc: Locator<Param>> getLocator(): Locator<Param>? {
        val set = getLocatorSet<Param>() ?: return null
        return set.get<Loc>()
    }

    inline fun<reified Param, reified Loc: Locator<Param>> hasLocator(): Boolean {
        val set = getLocatorSet<Param>() ?: return false
        return set.has<Loc>()
    }
}

/**
 * Represents a query into a Cell Container. Currently, queries are used to determine cell connection candidates.
 * @param connectionFace The face at the boundary between the two containers. It can be thought of as the common face. Implicitly, this is the contact face of the container that is being queried. It is not implied that a cell exists on this face, but rather that it may connect via this face.
 * @param surface This is the placement face of the cell. It is used to determine whether a connection is viable for certain connection modes. As an example, take the planar connection. It will only allow connections between cells that are mounted on the same face (plane) in the two containers.
 * */
data class CellQuery(val connectionFace: Direction, val surface: Direction)

/**
 * Encapsulates information about a neighbor cell.
 * */
data class CellNeighborInfo(
    val neighbor: CellBase,
    val neighborContainer: ICellContainer
)
