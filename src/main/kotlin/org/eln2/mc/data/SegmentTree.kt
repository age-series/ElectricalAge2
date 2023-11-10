package org.eln2.mc.data

/**
 * Represents the numeric range of a segment.
 * @param min The lower boundary of this segment.
 * @param max The upper boundary of this segment.
 * [min] must be smaller than [max].
 * */
data class ClosedInterval(val min: Double, val max: Double) {
    val range get() = min..max

    init {
        if (min >= max) {
            error("Invalid interval $min -> $max")
        }
    }

    fun contains(point: Double) = point in range
}

data class SegmentTreeNode<T>(
    val range: ClosedInterval,
    val data: T?,
    private val l: SegmentTreeNode<T>?,
    private val r: SegmentTreeNode<T>?,
) {
    constructor(range: ClosedInterval, segment: T?) : this(range, segment, null, null)

    /**
     * @return True if this segment's [range] contains the [point]. Otherwise, false.
     * */
    fun contains(point: Double): Boolean {
        return range.contains(point)
    }

    /**
     * Recursively queries until a leaf node containing [point] is found.
     * @param point A point contained within a segment. If this segment does not contain the point, an error will be produced.
     * @return The data value associated with the leaf node that contains [point]. An error will be produced if segment continuity is broken (parent contains point but the child nodes do not).
     * */
    fun query(point: Double): T? {
        if (!contains(point)) {
            return null
        }

        val left = l
        val right = r

        if (left != null && left.contains(point)) {
            return left.query(point)
        }

        if (right != null && right.contains(point)) {
            return right.query(point)
        }

        return data
    }
}

// Use when binary search is boring

class SegmentTree<T>(private val root: SegmentTreeNode<T>) {
    fun queryOrNull(point: Double): T? {
        if (!root.contains(point)) {
            return null
        }

        return root.query(point)
    }

    /**
     * Finds the segment with the specified range. Time complexity is O(log n)
     * */
    fun query(point: Double): T {
        return root.query(point) ?: error("$point not found")
    }
}

class SegmentTreeBuilder<TSegment> {
    private data class PendingSegment<T>(val segment: T, val range: ClosedInterval)

    private val pending = ArrayList<PendingSegment<TSegment>>()

    /**
     * Inserts a segment into the pending set. If its range is not sorted with the previously inserted segments,
     * [sort] must be called before attempting to [build].
     * */
    fun insert(segment: TSegment, range: ClosedInterval) {
        pending.add(PendingSegment(segment, range))
    }

    /**
     * Sorts the segments by range.
     * */
    fun sort() {
        pending.sortBy { it.range.min }
    }

    /**
     * Builds a [SegmentTree] from the pending set.
     * If segment continuity is broken, an error will be produced.
     * */
    fun build(): SegmentTree<TSegment> {
        if (pending.isEmpty()) {
            error("Tried to build empty segment tree")
        }

        /* if (pending.size > 1) {
             for (i in 1 until pending.size) {
                 val previous = pending[i - 1]
                 val current = pending[i]

                 if (previous.range.max != current.range.min) {
                     error("Segment tree continuity error")
                 }
             }
         }*/

        return SegmentTree(buildSegment(0, pending.size - 1))
    }

    private fun buildSegment(leftIndex: Int, rightIndex: Int): SegmentTreeNode<TSegment> {
        if (leftIndex == rightIndex) {
            val data = pending[leftIndex]

            return SegmentTreeNode(data.range, data.segment)
        }

        val mid = leftIndex + (rightIndex - leftIndex) / 2

        return SegmentTreeNode(
            ClosedInterval(pending[leftIndex].range.min, pending[rightIndex].range.max),
            data = null,
            l = buildSegment(leftIndex, mid),
            r = buildSegment(mid + 1, rightIndex)
        )
    }
}
