package org.eln2.mc.utility

class AveragingList(private val sampleCount: Int) {
    init {
        if(sampleCount <= 0){
            error("Invalid sample count $sampleCount")
        }
    }

    private val samples: ArrayList<Double> = ArrayList()

    fun addSample(value: Double) {
        samples.add(value)

        while (samples.size > sampleCount) {
            samples.removeAt(0)
        }
    }

    fun calculate(): Double {
        return samples.sum() / samples.size
    }
}
