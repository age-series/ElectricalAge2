package org.eln2.mc.data

/**
 * Represents the numeric range of a segment.
 * @param start The lower boundary of this segment.
 * @param end The upper boundary of this segment.
 * [start] must be smaller than [end].
 * */
data class SegmentRange(val start: Double, val end: Double) {
    init {
        if (start >= end) {
            error("Invalid range $start -> $end")
        }
    }

    fun contains(point: Double): Boolean = point in start..end
}

/**
 * The [SegmentTreeNode] encompasses a [SegmentRange], may have an associated [data] value (if it is a leaf node), and may have two children nodes.
 * @param range The value range of this node.
 * @param data The data value associated with this node.
 * @param l The left child of this node. Its [SegmentTreeNode.range]'s lower boundary will be equal to this node's lower boundary.
 * @param r The right child of this node. Its [SegmentTreeNode.range]'s upper boundary will be equal to this node's upper boundary.
 * Continuity of the ranges is not verified here.
 * */
data class SegmentTreeNode<T>(val range: SegmentRange, val data: T?, private val l: SegmentTreeNode<T>?, private val r: SegmentTreeNode<T>?, ) {
    constructor(range: SegmentRange, segment: T?) : this(range, segment, null, null)

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
    fun query(point: Double): T {
        if (!contains(point)) {
            error("Segment $range doesn't have $point")
        }

        val left = l
        val right = r

        if (left != null && left.contains(point)) {
            return left.query(point)
        }

        if (right != null && right.contains(point)) {
            return right.query(point)
        }

        return data ?: error("Cannot get stored data of non-leaf node")
    }
}

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
        return root.query(point)
    }
}

/**
 * Builds a segment tree from a mapping of [SegmentRange]s and data values.
 * */
class SegmentTreeBuilder<TSegment> {
    private data class PendingSegment<T>(val segment: T, val range: SegmentRange)

    private val pending = ArrayList<PendingSegment<TSegment>>()

    /**
     * Inserts a segment into the pending set. If its range is not sorted with the previously inserted segments,
     * [sort] must be called before attempting to [build].
     * */
    fun insert(segment: TSegment, range: SegmentRange) {
        pending.add(PendingSegment(segment, range))
    }

    /**
     * Sorts the segments by range.
     * */
    fun sort() {
        pending.sortBy { it.range.start }
    }

    /**
     * Builds a [SegmentTree] from the pending set.
     * If segment continuity is broken, an error will be produced.
     * */
    fun build(): SegmentTree<TSegment> {
        if (pending.isEmpty()) {
            error("Tried to build empty segment tree")
        }

        if (pending.size > 1) {
            for (i in 1 until pending.size) {
                val previous = pending[i - 1]
                val current = pending[i]

                if (previous.range.end != current.range.start) {
                    error("Segment tree continuity error")
                }
            }
        }

        return SegmentTree(buildSegment(0, pending.size - 1))
    }

    private fun buildSegment(leftIndex: Int, rightIndex: Int): SegmentTreeNode<TSegment> {
        if (leftIndex == rightIndex) {
            val data = pending[leftIndex]

            return SegmentTreeNode(data.range, data.segment)
        }

        val mid = leftIndex + (rightIndex - leftIndex) / 2

        return SegmentTreeNode(
            SegmentRange(pending[leftIndex].range.start, pending[rightIndex].range.end),
            data = null,
            l = buildSegment(leftIndex, mid),
            r = buildSegment(mid + 1, rightIndex)
        )
    }
}
