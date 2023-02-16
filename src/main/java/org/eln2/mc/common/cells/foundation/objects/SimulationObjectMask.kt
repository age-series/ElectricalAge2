package org.eln2.mc.common.cells.foundation.objects

@JvmInline
value class SimulationObjectMask(private val mask: Int) {
    companion object{
        private fun getBit(type: SimulationObjectType): Int {
            return when(type){
                SimulationObjectType.Electrical -> 1 shl 0
            }
        }

        val EMPTY = SimulationObjectMask(0)

        fun of(type : SimulationObjectType): SimulationObjectMask{
            return SimulationObjectMask(getBit(type))
        }

        fun of(types: List<SimulationObjectType>): SimulationObjectMask{
            return SimulationObjectMask(types.map { getBit(it) }.reduce{acc, bit -> acc or bit})
        }

        fun of(vararg types: SimulationObjectType): SimulationObjectMask{
            return of(types.asList())
        }
    }

    val isEmpty get() = mask == 0

    fun hasFlag(type: SimulationObjectType): Boolean{
        return mask and getBit(type) > 0
    }

    operator fun plus(other : SimulationObjectMask) : SimulationObjectMask{
        return SimulationObjectMask(mask or other.mask)
    }

    operator fun plus(other : SimulationObjectType) : SimulationObjectMask{
        return SimulationObjectMask(mask or getBit(other))
    }
}
