package org.eln2.mc.data

import org.eln2.mc.mathematics.BoundingBox
import org.eln2.mc.mathematics.ContainmentMode
import java.util.*

interface BVHView<D, B : BoundingBox<B>> {
    val box: B
    val data: D?
    val parent: BVHView<D, B>?
    val left: BVHView<D, B>?
    val right: BVHView<D, B>?
    val isLeaf: Boolean
}

fun interface BVHTest<D, B : BoundingBox<B>> {
    fun passes(data: D?, box: B, isLeaf: Boolean): Boolean
}

interface BVH<D, B : BoundingBox<B>> {
    fun contains(obj: D): Boolean
    fun query(test: BVHTest<D, B>, user: (D) -> Boolean)
}

interface MutableBVH<D, B : BoundingBox<B>> : BVH<D, B> {
    fun insert(obj: D, box: B)
    fun remove(obj: D)
}

interface DynamicBVH<D, B : BoundingBox<B>> : MutableBVH<D, B> {
    fun update(obj: D, actualBox: B): Boolean
}

interface ReinsertDynamicBVH<D, B : BoundingBox<B>> : DynamicBVH<D, B> {
    val totalObjectUpdates: Int
    val totalObjectUpdatesDistinct: Int
    val totalTreeUpdates: Int

    val treeUpdateProbability get() = totalTreeUpdates.toDouble() / totalObjectUpdates.toDouble().coerceAtLeast(1.0)
    val adjustedTreeUpdateProbability get() = totalTreeUpdates.toDouble() / totalObjectUpdatesDistinct.toDouble().coerceAtLeast(1.0)
}

fun <D, B : BoundingBox<B>> BVH<D, B>.queryAll(test: BVHTest<D, B>, user: (D) -> Unit) = query(test) {
    user(it)
    true
}

fun <D, B : BoundingBox<B>> BVH<D, B>.queryAll(test: BVHTest<D, B>) = ArrayList<D>().apply {
    queryAll(test, this::add)
}

fun <B : BoundingBox<B>> boxTest(testBox: B) = BVHTest<Any, B> { _, box, _ ->
    box intersectsWith testBox
}

class SAHIncrBVH<D, B : BoundingBox<B>> : MutableBVH<D, B> {
    private var root: Node<D, B>? = null
    private val nodes = HashMap<D, Node<D, B>>()

    override fun contains(obj: D) = nodes.containsKey(obj)

    val nodesView: Map<D, BVHView<D, B>> get() = nodes
    val size get() = nodes.size

    private fun treeError() {
        error("Invalid tree")
    }

    private fun replaceChild(parent: Node<D, B>, oldChild: Node<D, B>?, newChild: Node<D, B>?) {
        if (parent.left == oldChild) parent.left = newChild
        else if (parent.right == oldChild) parent.right = newChild
        else treeError()

        if (newChild != null) {
            newChild.parent = parent
        }
    }

    override fun insert(obj: D, box: B) {
        require(!nodes.containsKey(obj)) {
            "Duplicate add $obj"
        }

        val root = this.root

        if (root == null) {
            this.root = Node<D, B>(box).also {
                it.data = obj
            }

            nodes[obj] = this.root!!

            return
        } else {
            val node = siblingScan(box)

            val newInternal = Node<D, B>(node.box u box).also { internal ->
                internal.parent = node.parent

                internal.left = node
                internal.right = Node<D, B>(box).also { leaf ->
                    leaf.data = obj
                    leaf.parent = internal
                    nodes[obj] = leaf
                }
            }

            if (node.parent != null) replaceChild(node.parent!!, node, newInternal)
            else this.root = newInternal

            node.parent = newInternal

            refit(newInternal)
        }
    }

    private fun balance(node: Node<D, B>) {
        fun isProxy(n: Node<D, B>?) = n?.left != null && n.right != null

        if (!isProxy(node)) {
            return
        }

        fun rotl(n: Node<D, B>) {
            val y = n.right!!
            val t2 = y.left!!
            val x = y.right!!

            y.left = n
            y.right = x
            n.right = t2
            t2.parent = n
            x.parent = y

            val p = n.parent
            n.parent = y

            if (p == null) {
                require(n == this.root)
                this.root = y
                y.parent = null
            } else replaceChild(p, n, y)
        }

        fun rotr(n: Node<D, B>) {
            val y = n.left!!
            val t3 = y.right!!
            val x = y.left!!

            y.right = n
            y.left = x
            n.left = t3
            t3.parent = n
            x.parent = y

            val p = n.parent
            n.parent = y

            if (p == null) {
                require(n == this.root)
                this.root = y
                y.parent = null
            } else {
                replaceChild(p, n, y)
            }
        }

        fun isLeaf(n: Node<D, B>?) = n != null && n.left == null && n.right == null

        if (isProxy(node.left!!) && isLeaf(node.right!!)) {
            if (isProxy(node.left!!.left) && isLeaf(node.left!!.right)) {
                rotr(node)
            } else if (isLeaf(node.left!!.left) && isProxy(node.left!!.right)) {
                rotl(node.left!!)
                rotr(node)
            }
        } else if (isLeaf(node.left!!) && isProxy(node.right!!)) {
            if (isProxy(node.right!!.left) && isLeaf(node.right!!.right)) {
                rotr(node.right!!)
                rotl(node)
            } else if (isLeaf(node.right!!.left) && isProxy(node.right!!.right)) {
                rotl(node)
            }
        }
    }

    private fun refit(start: Node<D, B>) {
        var current: Node<D, B>? = start

        while (current != null) {
            current.box = current.left!!.box u current.right!!.box
            balance(current)
            current = current.parent
        }

        return
    }

    private data class NodeCandidate<D, B : BoundingBox<B>>(val node: Node<D, B>, val costIncr: Double)

    private val insertQueue = PriorityQueue<NodeCandidate<D, B>>(1024) { (_, a), (_, b) -> a.compareTo(b) }
    private fun enqueueCandidate(n: Node<D, B>, costIncr: Double) = insertQueue.offer(NodeCandidate(n, costIncr))

    private fun siblingScan(box: B): Node<D, B> {
        fun iCostIncr(b: B) = (b u box).surface - b.surface

        val boxSurface = box.surface
        var bestCost = Double.MAX_VALUE
        var bestNode = root!!

        enqueueCandidate(root!!, iCostIncr(root!!.box))

        while (insertQueue.isNotEmpty()) {
            val (front, rxCostIncr) = insertQueue.remove()

            val actualFrontCost = (front.box u box).surface

            if (actualFrontCost < bestCost) {
                bestCost = actualFrontCost
                bestNode = front
            }

            val costIncr = rxCostIncr + iCostIncr(front.box)

            if (boxSurface + costIncr < bestCost) {
                front.left?.also { enqueueCandidate(it, costIncr) }
                front.right?.also { enqueueCandidate(it, costIncr) }
            }
        }

        return bestNode
    }

    override fun remove(obj: D) {
        val node = this.nodes.remove(obj) ?: error("$obj not found")

        require(node.left == null && node.right == null && node.data == obj)

        val parent = node.parent

        if (parent != null) {
            replaceChild(parent, node, null)

            // We'll collapse the parent and move the child to the upper parent:
            if (parent.parent != null) {
                if (parent.left != null) replaceChild(parent.parent!!, parent, parent.left)
                else if (parent.right != null) replaceChild(parent.parent!!, parent, parent.right)
                else treeError()
            } else if (parent.left == null && parent.right == null) {
                // Parent is actually the root, we may as well delete it:
                require(parent == this.root)
                this.root = null
            }
        } else {
            require(node == root)
            this.root = null
        }
    }

    private fun query(node: Node<D, B>, test: BVHTest<D, B>, user: (D) -> Boolean): Boolean {
        if (!test.passes(node.data, node.box, node.isLeaf)) {
            return true
        }

        if (node.data != null) {
            require(node.left == null && node.right == null)

            return user(node.data!!)
        } else {
            require(node.left != null || node.right != null)

            if (node.left != null && !query(node.left!!, test, user)) {
                return false
            }

            if (node.right != null && !query(node.right!!, test, user)) {
                return false
            }

            return true
        }
    }

    override fun query(test: BVHTest<D, B>, user: (D) -> Boolean) {
        if (root != null) {
            query(root!!, test, user)
        }
    }

    fun heightScan(): Int {
        fun explore(node: Node<*, *>, currentHeight: Int): Int {
            if (node.isLeaf) {
                return currentHeight
            }

            return listOfNotNull(node.left, node.right).maxOf {
                explore(it, currentHeight + 1)
            }
        }

        if (root == null) {
            return 0
        }

        return explore(root!!, 0)
    }

    fun viewNode(obj: D): BVHView<D, B>? = nodes[obj]

    fun integrityCheck() {
        val root = this.root ?: return

        require(root.parent == null)

        val pendingObjects = nodes.keys.toHashSet()

        fun explore(sender: Node<D, B>?, n: Node<D, B>) {
            if (sender != null) {
                require(n.parent == sender)
            } else require(n == root)

            require((n.left == null) == (n.right == null))
            if (n.isLeaf) require(n.data != null)
            else require(n.data == null)
            if (n.data != null) require(n.isLeaf)
            else require(!n.isLeaf)

            if (!n.isLeaf) {
                explore(n, n.left!!)
                explore(n, n.right!!)
            } else {
                require(pendingObjects.remove(n.data!!))
                require(nodes[n.data!!] == n)
            }
        }

        explore(null, root)

        require(pendingObjects.isEmpty())
    }

    private class Node<Data, Box : BoundingBox<Box>>(override var box: Box) : BVHView<Data, Box> {
        override var data: Data? = null
        override var parent: Node<Data, Box>? = null
        override var left: Node<Data, Box>? = null
        override var right: Node<Data, Box>? = null
        override val isLeaf get() = left == null && right == null

        override fun toString() = data.toString()
    }
}

class ReinsertSAHIncrBVH<D, B : BoundingBox<B>>(val adjustBox: (B) -> B) : ReinsertDynamicBVH<D, B> {
    private data class Obj<D, B : BoundingBox<B>>(
        val instance: D,
        val originalBox: B,
    )

    private val bvh = SAHIncrBVH<Obj<D, B>, B>()
    private val objects = HashMap<D, Obj<D, B>>()

    override fun contains(obj: D) = objects.containsKey(obj)

    override var totalObjectUpdates = 0
        private set
    override var totalObjectUpdatesDistinct = 0
        private set
    override var totalTreeUpdates = 0
        private set

    override fun insert(obj: D, box: B) {
        val adjustedBox = adjustBox(box)

        require(adjustedBox.evaluateContainment(box) == ContainmentMode.ContainedFully || adjustedBox == box) {
            "Supplied box was invalid"
        }

        val o = Obj(obj, box)

        require(objects.put(obj, o) == null) {
            "Duplicate add $obj"
        }

        bvh.insert(o, adjustedBox)
    }

    override fun update(obj: D, actualBox: B): Boolean {
        totalObjectUpdates++

        val o = objects[obj] ?: error("Object $obj not found")
        val node = bvh.viewNode(o) ?: error("Object $obj not found in storage")

        if (o.originalBox != actualBox) {
            totalObjectUpdatesDistinct++
        }

        if (node.box.evaluateContainment(actualBox) != ContainmentMode.ContainedFully) {
            remove(obj)
            insert(obj, actualBox)
            totalTreeUpdates++

            return true
        }

        return false
    }

    override fun query(test: BVHTest<D, B>, user: (D) -> Boolean) =
        bvh.query({ data: Obj<D, B>?, box: B, isLeaf: Boolean ->
            if (isLeaf) test.passes(data!!.instance, data.originalBox, true)
            else test.passes(null, box, false)
        }, { (instance, _) -> user(instance) })

    override fun remove(obj: D) {
        val o = objects[obj] ?: error("Obj $obj not found")
        bvh.remove(o)
        require(objects.remove(obj)?.instance == obj)
    }
}
