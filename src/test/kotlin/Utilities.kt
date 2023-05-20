fun rangeScan(start: Double = 0.0, end: Double = 10.0, steps: Int = 10000, action: ((Double) -> Unit)) {
    require(start < end)

    val stepSize = (end - start) / steps
    var x = start

    while (x < end) {
        action(x)

        x += stepSize
    }
}

fun rangeScanRec(action: ((DoubleArray) -> Unit), layers: Int, start: Double = 0.0, end: Double = 10.0, steps: Int = 10) {
    fun helper(depth: Int, vec: DoubleArray) {
        rangeScan(start = start, end = end, steps = steps) { v ->
            vec[depth] = v

            if (depth > 0) {
                helper(depth - 1, vec);
            }
            else {
                action(vec);
            }
        }

        val results = DoubleArray(layers)

        helper(layers - 1, results)
    }
}
