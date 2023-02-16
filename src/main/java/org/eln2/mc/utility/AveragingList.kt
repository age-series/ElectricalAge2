package org.eln2.mc.utility

class AveragingList(private val sampleCount: Int) {
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
