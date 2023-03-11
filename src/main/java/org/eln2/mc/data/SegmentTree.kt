package org.eln2.mc.data

data class SegmentRange(val start: Double, val end: Double) {
    init {
        if(start >= end) {
            error("Invalid range $start -> $end")
        }
    }

    fun contains(point: Double): Boolean = point in start..end
}

data class SegmentTreeNode<T>(val range: SegmentRange, val data: T?, private val left: SegmentTreeNode<T>?, private val right: SegmentTreeNode<T>?) {
    constructor(range: SegmentRange, segment: T?): this(range, segment, null, null)

    fun contains(point: Double): Boolean {
        return range.contains(point)
    }

    fun query(point: Double): T {
        if(!contains(point)) {
            error("Segment $range doesn't have $point")
        }

        val left = left
        val right = right

        if(left != null && left.contains(point)) {
            return left.query(point)
        }

        if(right != null && right.contains(point)) {
            return right.query(point)
        }

        return data ?: error("Cannot get stored data of non-leaf node")
    }
}

class SegmentTree<T>(private val root: SegmentTreeNode<T>) {
    fun queryOrNull(point: Double): T? {
        if(!root.contains(point)) {
            return null
        }

        return root.query(point)
    }

    /**
     * Finds the segment with the specified range. Time complexity is O(log n)
     * */
    fun query(point: Double): T {
        return root.query(point)
    }
}

class SegmentTreeBuilder<TSegment> {
    private data class PendingSegment<T>(val segment: T, val range: SegmentRange)

    private val pending = ArrayList<PendingSegment<TSegment>>()

    fun insert(segment: TSegment, range: SegmentRange) {
        pending.add(PendingSegment(segment, range))
    }

    fun sort() {
        pending.sortBy { it.range.start }
    }

    fun build(): SegmentTree<TSegment> {
        if(pending.isEmpty()) {
            error("Tried to build empty segment tree")
        }

        if(pending.size > 1) {
            for (i in 1 until pending.size) {
                val previous = pending[i - 1]
                val current = pending[i]

                if(previous.range.end != current.range.start) {
                    error("Segment tree continuity error")
                }
            }
        }

        return SegmentTree(buildSegment(0, pending.size - 1))
    }

    private fun buildSegment(leftIndex: Int, rightIndex: Int): SegmentTreeNode<TSegment> {
        if(leftIndex == rightIndex) {
            val data = pending[leftIndex]

            return SegmentTreeNode(data.range, data.segment)
        }

        val mid = leftIndex + (rightIndex - leftIndex) / 2

        return SegmentTreeNode(
            SegmentRange(pending[leftIndex].range.start, pending[rightIndex].range.end),
            data = null,
            left = buildSegment(leftIndex, mid),
            right = buildSegment(mid + 1, rightIndex))
    }
}
