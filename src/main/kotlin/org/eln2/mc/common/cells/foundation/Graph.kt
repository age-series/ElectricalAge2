package org.eln2.mc.common.cells.foundation

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import net.minecraftforge.server.ServerLifecycleHooks
import org.ageseries.libage.data.Locator
import org.ageseries.libage.data.MILLI
import org.ageseries.libage.data.MultiMap
import org.ageseries.libage.data.MutableMapPairBiMap
import org.ageseries.libage.data.MutableSetMapMultiMap
import org.ageseries.libage.data.NANO
import org.ageseries.libage.data.Quantity
import org.ageseries.libage.data.SECOND
import org.ageseries.libage.data.Time
import org.ageseries.libage.sim.Simulator
import org.ageseries.libage.sim.SubSolverSet
import org.ageseries.libage.sim.electrical.ElectricalCircuitForestBuilder
import org.ageseries.libage.sim.electrical.ElectricalSimulation
import org.ageseries.libage.sim.kinetic.KineticSimulation
import org.ageseries.libage.sim.kinetic.KineticSimulationForestBuilder
import org.ageseries.libage.utils.Stopwatch
import org.ageseries.libage.utils.addUnique
import org.ageseries.libage.utils.measureDuration
import org.ageseries.libage.utils.putUnique
import org.eln2.mc.DEBUGGER_BREAK
import org.eln2.mc.ELN2_DEBUG
import org.eln2.mc.Eln2Config
import org.eln2.mc.LOG
import org.eln2.mc.OnServerThread
import org.eln2.mc.OnSimulationThread
import org.eln2.mc.common.cells.CellRegistry
import org.eln2.mc.LocklessAtomicObjectPool
import org.eln2.mc.PooledObjectPolicy
import org.eln2.mc.extensions.getLocator
import org.eln2.mc.extensions.putLocator
import org.eln2.mc.requireIsOnServerThread
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.ForkJoinWorkerThread
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.forEach
import kotlin.concurrent.Volatile
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Bijective map between [Cell] and the [org.ageseries.libage.data.Locator].
 * */
class CellMap : Iterable<Cell> {
    private val cells = MutableMapPairBiMap<Cell, Locator>()

    val size get() = cells.size

    fun add(cell: Cell) = cells.add(cell, cell.locator)

    fun remove(cell: Cell) = check(cells.removeForward(cell)) { "Cell $cell ${cell.locator} was not present"}

    fun contains(cell: Cell) = cells.forward.contains(cell)

    fun contains(locator: Locator) = cells.backward.contains(locator)

    fun getByLocator(locator: Locator) = cells.backward[locator]

    fun addAll(source: CellMap) = source.forEach { this.add(it) }

    override fun iterator(): Iterator<Cell> = cells.forward.keys.iterator()
}

/**
 * Manages the parallel execution of the simulation for the single [graph].
 * This includes:
 * - Performing changes while the background tasks are running
 * - Batching the various sub-solvers into work groups
 * - Executing the simulation flow
 * */
class SimulationExecutionSubgraph(val graph: CellGraph) {
    /**
     * Synchronization primitive with a globally unique ID, used for structured concurrency.
     * */
    abstract class SynchronizationPrimitive private constructor() {
        companion object {
            val GLOBAL_ID_GENERATOR = AtomicInteger()
        }

        /**
         * Globally unique ID, used to sort the locking order.
         * */
        val globalId = GLOBAL_ID_GENERATOR.getAndIncrement()

        /**
         * Called before the subsolver steps and when a transaction starts.
         * */
        abstract fun acquire()

        /**
         * Called after the subsolver finished the step and when a transaction ends.
         * */
        abstract fun release()

        class Reentrant(val sync: ReentrantLock = ReentrantLock()) : SynchronizationPrimitive() {
            override fun acquire() {
                sync.lock()
            }

            override fun release() {
                sync.unlock()
            }
        }
    }

    /**
     * Implemented by cells that will do locking with the subsolvers.
     * */
    interface SynchronizationPointCell<Self> where Self : Cell, Self : SynchronizationPointCell<Self> {
        // P.S. we can implement points for subscriber steps

        /**
         * Gets a synchronization primitive for each needed subsolver of [obj].
         * This is called each time the graph is built.
         * @return A map of the [subSolvers] and the synchronization primitive the subsolvers should lock on execute.
         * */
        fun createSynchronizationMapForSubSolvers(obj: SimulationObject<*>, subSolvers: List<Any>) : Map<Any, SynchronizationPrimitive>?
    }

    /**
     * Holds the individual subsolver and the synchronization points associated with it.
     * @param solver The underlying simulation.
     * @param method The update method for the specific simulation.
     * @param primitives The synchronization primitives, in no particular order.
     * */
    private class SubSolver(val solver: Any, val method: Runnable, primitives: Iterable<SynchronizationPrimitive>) {
        val sortedPrimitives: Array<SynchronizationPrimitive> = primitives
            .distinct()
            .sortedBy { it.globalId }
            .toTypedArray()

        var lastExecutionTime = Quantity<Time>(0.0)

        fun execute() {
            val synchronizationPoints = sortedPrimitives

            for (i in synchronizationPoints.indices){
                synchronizationPoints[i].acquire()
            }

            try {
                lastExecutionTime = measureDuration {
                    method.run()
                }
            }
            finally {
                /**
                 * Since this is used for locks, this makes sure we don't cause a deadlock even if the simulation step fails.
                 * This is to make the failure pattern consistent with the rest of the code.
                 * */
                for (i in synchronizationPoints.indices){
                    synchronizationPoints[i].release()
                }
            }
        }
    }

    private val subSolvers = ArrayList<SubSolver>()

    private val priorityQueue = PriorityQueue<SubSolver> { a, b ->
        a.lastExecutionTime.value.compareTo(b.lastExecutionTime.value)
    }

    /**
     * Re-builds the [subSolvers] list.
     * */
    fun rebuild() {
        subSolvers.clear()

        requireIsOnServerThread {
            DEBUGGER_BREAK("Cannot create SimulationExecutionSubgraph on current thread")
        }

        /**
         * Finds all synchronization points for cells and objects:
         * */
        val syncPrimitivesBySubSolver = MutableSetMapMultiMap<Any, SynchronizationPrimitive>()
        graph.forEach { cell ->
            /**
             * Registers the [SynchronizationPointCell]s:
             * */
            if(cell is SynchronizationPointCell<*>) {
                cell.objects.forEachObject { obj ->
                    val subSolvers = obj.getSubSolvers().toList()
                    val map = cell.createSynchronizationMapForSubSolvers(obj, subSolvers)

                    if(map != null && map.isNotEmpty()) {
                        check(map.keys.all { subSolver -> subSolvers.contains(subSolver) }) {
                            DEBUGGER_BREAK("Invalid synchronization map $cell")
                        }

                        for (subSolver in subSolvers) {
                            val primitive = map[subSolver]
                                ?: continue

                            syncPrimitivesBySubSolver[subSolver].add(primitive)
                        }
                    }
                }
            }
        }

        graph.electricalSubSolverSets.forEach { subSolverSet ->
            subSolverSet.solvers.forEach { electricalSimulation ->
                subSolvers.add(
                    SubSolver(
                        electricalSimulation,
                        electricalSimulation::step,
                        syncPrimitivesBySubSolver[electricalSimulation]
                    )
                )
            }
        }

        graph.thermalSims.forEach {
            subSolvers.add(
                SubSolver(
                    it,
                    { it.step(CellGraph.DT) },
                    syncPrimitivesBySubSolver[it]
                ),
            )
        }

        graph.kineticSubSolverSets.forEach { subSolverSet ->
            subSolverSet.solvers.forEach { kineticSimulation ->
                subSolvers.add(
                    SubSolver(
                        kineticSimulation,
                        kineticSimulation::step,
                        syncPrimitivesBySubSolver[kineticSimulation]
                    )
                )
            }
        }
    }

    //#region Concurrency State

    private val lock = ReentrantLock()
    private val resumeCondition = lock.newCondition()
    private val pausedSignal = lock.newCondition()

    @Volatile
    private var pauseRequested = false

    @Volatile
    private var isPaused = false

    private var isRunning = false

    // Not sure
    val isNotRunning get() = isPaused || !isRunning

    //#endregion

    //#region Game Thread API

    /**
     * Blocks the caller until the graph reaches a safe point, and pauses the simulation of this graph.
     * Will be a no-op if the simulation is already paused.
     * */
    @OnServerThread
    fun suspend() {
        requireIsOnServerThread {
            "Cannot use SimulationExecutionSubgraph#suspend on current thread"
        }

        lock.lock()

        try {
            if(pauseRequested) {
                check(isPaused)
                return
            }

            if(!isRunning) {
                return
            }

            /**
             * Request the simulation to pause:
             * */
            pauseRequested = true

            /**
             * Wait for the simulation to confirm it paused:
             * */
            while(!isPaused && isRunning) {
                pausedSignal.await()
            }
        }
        catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        finally {
            lock.unlock()
            LOG.info("Stopped simulation for $graph")
        }
    }

    /**
     * Resumes the simulation.
     * Will be a no-op if the simulation is already running.
     * */
    @OnServerThread
    fun resume() {
        requireIsOnServerThread {
            "Cannot use SimulationExecutionSubgraph#resume on current thread"
        }

        lock.lock()

        try {
            if(!isPaused) {
                check(!pauseRequested)
                return
            }

            pauseRequested = false
            isPaused = false
            resumeCondition.signal()
        }
        finally {
            lock.unlock()
            LOG.info("Resumed simulation for $graph")
        }
    }

    //#endregion

    //#region Logging

    private var updates = 0L
    private var updatesCheckpoint = 0L

    /**
     * Gets the number of updates that have occurred since the last call to this method.
     * */
    @OnServerThread
    fun sampleElapsedUpdates(): Long {
        val elapsed = updates - updatesCheckpoint
        updatesCheckpoint += elapsed
        return elapsed
    }

    var lastTickTime = Quantity(0.0, SECOND)
        private set

    //#endregion

    /**
     * The Synchronization Barrier.
     * If main thread requested a pause, we stop here, signal we are paused, and wait for resume.
     */
    private fun pauseBarrier() {
        /**
         * Check-lock-check:
         * */
        if(pauseRequested) {
            lock.lock()

            try {
                if(pauseRequested) {
                    isPaused = true

                    /**
                     * Signal to wake up game thread:
                     * */
                    pausedSignal.signalAll()

                    /**
                     * Wait for resume:
                     * */
                    while (pauseRequested) {
                        resumeCondition.await()
                    }
                }
            }
            finally {
                lock.unlock()
            }
        }
    }

    /**
     * A batch of sub-solvers to execute.
     * */
    private class ExecutionSubgroup private constructor() : Callable<Void> {
        /**
         * The individual tick methods, with additional time tracking.
         * */
        val subSolvers = ArrayList<SubSolver>()

        /**
         * Executes all sub-solvers.
         * */
        override fun call(): Void? {
            var i = 0
            val count = subSolvers.size
            while (i < count) {
                subSolvers[i].execute()
                i++
            }

            return null
        }

        companion object {
            fun allocate() = pool.get()
            fun release(instance: ExecutionSubgroup) = pool.release(instance)

            private val pool =
                LocklessAtomicObjectPool<ExecutionSubgroup>(object : PooledObjectPolicy<ExecutionSubgroup> {
                    override fun create(): ExecutionSubgroup {
                        return ExecutionSubgroup()
                    }

                    override fun release(obj: ExecutionSubgroup): Boolean {
                        obj.subSolvers.clear()
                        return true
                    }
                }, 1024 * 16)
        }
    }

    /**
     * Utility class for generating all the execution subgroups and the tasks to run.
     * */
    private class ExecutionSubgroupCompiler {
        val subGroups = ArrayList<ExecutionSubgroup>()

        /**
         * If true, the next [insert] call will generate a new group.
         * */
        private var splitPoint = false

        /**
         * After this is called, the next [insert] call will insert the sub-solver into a new group.
         * */
        fun markSplit() {
            splitPoint = true
        }

        /**
         * Inserts a new sub-solver.
         * */
        fun insert(solver: SubSolver) {
            if(splitPoint || subGroups.isEmpty()) {
                subGroups.add(ExecutionSubgroup.allocate())
            }

            subGroups[subGroups.size - 1].subSolvers.add(solver)
            splitPoint = false
        }

        /**
         * Frees all the allocated subgroups.
         * */
        fun finish() {
            subGroups.forEach {
                ExecutionSubgroup.release(it)
            }

            subGroups.clear()
            splitPoint = false
        }
    }

    private val workCompiler = ExecutionSubgroupCompiler()

    private fun executeSimulation() {
        val startTime = System.nanoTime()

        try {
            /**
             * Sorts the sub-solvers:
             * */
            subSolvers.forEach { subSolver ->
                priorityQueue.add(subSolver)
            }

            /**
             * Compiles all the subgroups:
             * */
            var totalTime = 0.0
            while (priorityQueue.isNotEmpty()) {
                val subSolver = priorityQueue.remove()
                workCompiler.insert(subSolver)

                totalTime += !subSolver.lastExecutionTime

                if(totalTime > !TIME_THRESHOLD) {
                    /**
                     * Create a new work group:
                     * */
                    workCompiler.markSplit()
                    totalTime = 0.0
                }
            }

            for (i in 0 until CellGraph.SUBSTEPS) {
                /**
                 * Checks if a pause is requested and, if so, pauses the simulation.
                 * */
                pauseBarrier()

                /**
                 * Dispatches the pre-update sequentially:
                 * */
                graph.simulationThreadSubscribers.update(CellGraph.DT, SimulationPhase.Pre)

                /**
                 * Dispatches the sub-solvers in parallel and awaits completion:
                 * */
                pool.invokeAll(workCompiler.subGroups)

                /**
                 * Dispatches the post-update sequentially:
                 * */
                graph.simulationThreadSubscribers.update(CellGraph.DT, SimulationPhase.Post)

                ++updates
            }
        }
        finally {
            workCompiler.finish()

            lock.lock()

            try {
                isRunning = false
                isPaused = false
                pauseRequested = false
                pausedSignal.signalAll()
            }
            finally {
                lock.unlock()
                lastTickTime = Quantity((System.nanoTime() - startTime).toDouble(), NANO * SECOND)
            }
        }
    }

    /**
     * The currently orchestrated frame.
     * */
    var frame: ForkJoinTask<*>? = null
        private set

    /**
     * Orchestrates the simulation frame. Called at the start of the game loop.
     * */
    fun orchestrateFrame() {
        requireIsOnServerThread {
            "Cannot orchestrate a frame from the current thread"
        }

        if(frame != null) {
            check(frame!!.isDone) {
                "Tried to orchestrate frame, but previous frame was not done!"
            }
        }

        lock.lock()
        try {
            check(!isRunning)

            isRunning = true
            isPaused = false
            pauseRequested = false

            frame = pool.submit {
                executeSimulation()
            }
        }
        finally {
            lock.unlock()
        }
    }

    /**
     * Waits for the simulation to complete. Called at the end of the game loop.
     * */
    fun awaitCompletion() {
        requireIsOnServerThread {
            "Cannot await completion on current thread"
        }

        if(frame == null) {
            return
        }

        frame!!.get()
        frame = null
    }

    /**
     * Structured lock over multiple synchronization primitives.
     * Allows completely freezing the execution of the subsolvers involved, for mutating their state from e.g. the game thread.
     * */
    class StructuredLock private constructor(val sortedPrimitives: List<SynchronizationPrimitive>) {
        init {
            if(ELN2_DEBUG) {
                for(i in 1 until sortedPrimitives.size) {
                    val a = sortedPrimitives[i - 1]
                    val b = sortedPrimitives[i]

                    if(a.globalId >= b.globalId) {
                        DEBUGGER_BREAK()
                    }
                }
            }
        }

        fun beginTransaction() {
            for (i in sortedPrimitives.indices) {
                val primitive = sortedPrimitives[i]
                primitive.acquire()
            }
        }

        fun endTransaction() {
            for (i in sortedPrimitives.indices) {
                val primitive = sortedPrimitives[i]
                primitive.release()
            }
        }

        inline fun executeTransaction(body: () -> Unit) {
            beginTransaction()

            try {
                body()
            }
            finally {
                endTransaction()
            }
        }

        companion object {
            fun create(source: Iterable<SynchronizationPrimitive>) = StructuredLock(source.sortedBy { it.globalId })
        }
    }

    companion object {
        /**
         * The approximate maximum tick time an execution group is given.
         * */
        val TIME_THRESHOLD = Quantity(0.25, MILLI * SECOND)

        private fun getThreadCount() : Int {
            val threadCount = Eln2Config.serverConfig.simulationThreadCount.get()

            // We do get an exception from thread pool creation, but explicit handling is better here.
            if (threadCount <= 0) {
                error("Simulation threads is $threadCount")
            }

            LOG.info("Using $threadCount ELN2 simulation threads")

            return threadCount
        }

        private val threadNumber = AtomicInteger()

        private fun createThread(pool: ForkJoinPool) : ForkJoinWorkerThread {
            val thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool)
            thread.name = "eln-pool-${threadNumber.getAndIncrement()}"
            return thread
        }

        private fun exceptionHandler(t: Thread, e: Throwable) {
            LOG.error("ELN2 SIMULATION ERROR ($t): $e")
        }

        private val pool = ForkJoinPool(
            getThreadCount(),
            ::createThread,
            ::exceptionHandler,
            true
        )

        fun makePool() {
            requireIsOnServerThread()
            pool
        }
    }
}

/**
 * The cell graph represents a physical network of cells.
 * It may have multiple simulation subsets, formed between objects in the cells of this graph.
 * The cell graph manages the solver and simulation.
 * It also has serialization/deserialization logic for saving to the disk using NBT.
 * */
class CellGraph(val id: UUID, val manager: CellGraphManager, val level: ServerLevel) : Iterable<Cell> {
    private val cells = CellMap()

    val electricalSubSolverSets = ArrayList<SubSolverSet<ElectricalSimulation>>()
    val thermalSims = ArrayList<Simulator>()
    val kineticSubSolverSets = ArrayList<SubSolverSet<KineticSimulation>>()

    /**
     * Persistent set of flags. The set is persistent over a single step. Flags can be added in the server pre-step, and they can be read in the server post-step, after which they are cleared.
     * */
    @OnServerThread
    interface FlagSets<TSolver> {
        /**
         * Sets the flag. Only makes sense to call in the server-side pre-step.
         * */
        fun setFlag(solver: TSolver, flag: Any) : Boolean

        /**
         * Checks if a flag is set. Only makes sense to call in the server-side post-step.
         * */
        fun isSet(solver: TSolver, flag: Any) : Boolean
    }

    @OnServerThread
    private class FlagSetsImpl<TSolver> : FlagSets<TSolver> {
        private val sets = HashMap<TSolver, HashSet<Any>>()

        private fun validateUsage() {
            if(ELN2_DEBUG) {
                requireIsOnServerThread()
            }
        }

        override fun setFlag(solver: TSolver, flag: Any) : Boolean {
            validateUsage()

            val set = sets.computeIfAbsent(solver) {
                HashSet<Any>()
            }

            return set.add(flag)
        }

        override fun isSet(solver: TSolver, flag: Any) : Boolean {
            validateUsage()

            val set = sets[solver]
                ?: return false

            return set.contains(flag)
        }

        fun clear() {
            validateUsage()

            sets.clear()
        }

        fun advanceFrame() {
            validateUsage()

            sets.values.forEach {
                it.clear()
            }
        }
    }

    private val kineticFlagsImplSimulation = FlagSetsImpl<KineticSimulation>()
    val kineticFlagsSimulation: FlagSets<KineticSimulation> get() = kineticFlagsImplSimulation

    /**
     * The execution graph for this cell graph, updated when [buildSolver] is called.
     * */
    val executionGraph = SimulationExecutionSubgraph(this)

    /**
     * Subscribers for the simulation steps.
     * */
    @OnSimulationThread
    val simulationThreadSubscribers = SubscriberPool<SimulationPhase>()

    /**
     * Subscribers for the server thread tick events.
     * */
    @OnServerThread
    val serverThreadSubscribers = SubscriberPool<ServerPhase>()

    /**
     * Flag set when the graph is being deserialized. Its scope is only data serialization; it will be set to false just after that is done, but before the graph is built.
     * */
    var isLoadingData = false
        private set

    fun captureAllInScope() {
        cells.forEach {
            CellAndContainerHandle.captureInScope(it)
        }
    }

    /**
     * Gets an iterator over the cells in this graph.
     * */
    override fun iterator(): Iterator<Cell> = cells.iterator()

    /**
     * Gets the number of cells in the graph.
     * */
    val size get() = cells.size

    fun isEmpty() = size == 0
    fun isNotEmpty() = !isEmpty()

    /**
     * Adds a cell to the internal sets, assigns its graph, and invalidates the saved data.
     * **This does not update the solver!
     * It is assumed that multiple operations of this type will be performed, then, the solver update will occur explicitly.**
     * The simulation must be stopped before calling this.
     * */
    fun addCell(cell: Cell) {
        validateMutationAccess()
        cells.add(cell)
        cell.graph = this
        manager.setDirty()
    }

    /**
     * Removes a cell from the internal sets, and invalidates the saved data.
     * **This does not update the solver!
     * It is assumed that multiple operations of this type will be performed, then,
     * the solver update will occur explicitly.**
     * The simulation must be stopped before calling this.
     * */
    fun removeCell(cell: Cell) {
        validateMutationAccess()
        cells.remove(cell)
        manager.setDirty()
    }

    /**
     * Copies the cells of this graph to the other graph, and invalidates the saved data.
     * The simulation must be stopped before calling this.
     * */
    fun copyTo(graph: CellGraph) {
        this.validateMutationAccess()
        graph.validateMutationAccess()
        graph.cells.addAll(this.cells)
        graph.manager.setDirty()
    }

    /**
     * Gets the cell with the specified [locator].
     * @return The cell, if found, or throws an exception, if the cell does not exist.
     * */
    fun getCellByLocator(locator: Locator): Cell {
        val result = cells.getByLocator(locator)

        if (result == null) {
            LOG.error(DEBUGGER_BREAK("Could not get cell at $locator")) // exception may be swallowed
            error("Could not get cell at $locator")
        }

        return result
    }

    fun setChanged() {
        if(!isLoadingData) {
            manager.setDirty()
        }
    }

    /**
     * Checks if the simulation is running. Presumably, this is used by logic that wants to mutate the graph.
     * It also checks if the caller is the server thread.
     * */
    private fun validateMutationAccess() {
        if (!executionGraph.isNotRunning) {
            error("Tried to mutate the simulation while it was running")
        }

        if (Thread.currentThread() != ServerLifecycleHooks.getCurrentServer().runningThread) {
            error("Illegal cross-thread access into the cell graph")
        }
    }

    fun advanceServerFrame() {
        kineticFlagsImplSimulation.advanceFrame()
    }

    private fun clearElectricalSimulation() {
        electricalSubSolverSets.forEach {
            it.solvers.forEach { solver ->
                solver.destroy()
            }
        }

        electricalSubSolverSets.clear()
    }

    private fun clearRotationSimulation() {
        // Reset angles and other state (so the fresh node's phase coincides with the rest of the network):
        kineticSubSolverSets.forEach {
            it.solvers.forEach { solver ->
                solver.nodes.forEach { node ->
                    node.resetForRestart()
                }
            }
        }

        // Destroy old references:
        kineticSubSolverSets.forEach {
            it.solvers.forEach { solver ->
                solver.destroy()
            }
        }

        kineticSubSolverSets.clear()
        kineticFlagsImplSimulation.clear()
    }

    /**
     * This realizes the object subsets and creates the underlying simulations.
     * The simulation must be suspended before calling this method.
     * @see stopSimulation
     * */
    fun buildSolver() {
        validateMutationAccess()

        clearElectricalSimulation()
        clearRotationSimulation()

        cells.forEach { it.clearObjectConnections() }
        cells.forEach { it.onBuildStarted() }
        cells.forEach { it.recordObjectConnections() }

        /**
         * Realization: Finds all connected graphs of objects of the same type (objects that are connected to each other).
         * An object might contain nodes that will not be connected to each other (e.g. a potential probe).
         * This means that those graphs will further be processed into sub-solvers. Sub-solvers are the actual final simulations.
         * They are created with the underlying components that are joined/connected with each other.
         * This is done by retaining the connection data before creating the underlying sub-solvers, and then determining the connected nodes with those connections.
         * This is done by domain-specific wrappers for [org.ageseries.libage.sim.SubSolverSystemBuilder].
         * */

        val electrical = realizeElectrical()
        realizeThermal()
        val kinetic = realizeKinetic()

        /**
         * Executes build on all objects.
         * This creates the connections in retained mode (except for thermals, until we add sub-solvers for them too).
         * It doesn't involve any underlying simulation yet. It's just recording the connection data.
         * */
        cells.forEach { cell ->
            cell.objects.forEachObject { obj ->
                when(obj.type) {
                    SimulationObjectType.Electrical -> {
                        (obj as ElectricalObject<*>).build(electrical.builderByObject[obj]!!)
                    }
                    SimulationObjectType.Thermal -> {
                        (obj as ThermalObject<*>).build()
                    }
                    SimulationObjectType.Kinetic -> {
                        (obj as KineticObject<*>).build(kinetic.builderByObject[obj]!!)
                    }
                }
            }
        }

        /**
         * Finally, the underlying simulations are created.
         * Each one's nodes/components/bodies **are connected** to at least one other body in that simulation.
         * (Except thermal, for now)
         * */

        electrical.objectsByBuilder.keys.forEach { builder ->
            val subSolvers = builder.build(DT, true, ElectricalSimulation.ConstructionOptions(
                1e12
            ))

            electrical.objectsByBuilder[builder].forEach { obj ->
                obj.setSubSolvers(subSolvers)
            }

            electricalSubSolverSets.add(subSolvers)
        }

        kinetic.objectsByBuilder.keys.forEach { builder ->
            val subSolvers = builder.build(DT, true)

            kinetic.objectsByBuilder[builder].forEach { obj ->
                obj.setSubSolvers(subSolvers)
            }

            kineticSubSolverSets.add(subSolvers)
        }

        cells.forEach { it.onBuildFinished() }

        executionGraph.rebuild()
    }

    /**
     * Result of realizing the electrical object graphs based (**only**) on object connections.
     * Each builder here will create one or more sub-solvers.
     * @param builderByObject The builder assigned to each object.
     * @param objectsByBuilder The builders, mapped to all the electrical objects that have connections between each other.
     * */
    private class ElectricalRealizationData(
        val builderByObject: Map<ElectricalObject<*>, ElectricalCircuitForestBuilder>,
        val objectsByBuilder: MultiMap<ElectricalCircuitForestBuilder, ElectricalObject<*>>
    )

    /**
     * Realizes the sub-system builders for all electrical objects.
     * */
    private fun realizeElectrical() : ElectricalRealizationData {
        val builderByObject = HashMap<ElectricalObject<*>, ElectricalCircuitForestBuilder>()
        val objectsByBuilder = MutableSetMapMultiMap<ElectricalCircuitForestBuilder, ElectricalObject<*>>()

        realizeComponents(SimulationObjectType.Electrical, factory = { set ->
            val builder = ElectricalCircuitForestBuilder()

            set.forEach {
                val obj = it.objects.electricalObject
                obj.addComponents(builder)
                builderByObject.putUnique(obj, builder)
                objectsByBuilder[builder].addUnique(obj)
            }
        })

        return ElectricalRealizationData(builderByObject, objectsByBuilder)
    }

    private fun realizeThermal() {
        thermalSims.clear()

        realizeComponents(SimulationObjectType.Thermal, factory = { set ->
            val simulation = Simulator()
            set.forEach { it.objects.thermalObject.setNewSimulation(simulation) }
            thermalSims.add(simulation)
        })
    }

    /**
     * Result of realizing the kinetic object graphs based (**only**) on object connections.
     * Each builder here will create one or more sub-solvers.
     * @param builderByObject The builder assigned to each object.
     * @param objectsByBuilder The builders, mapped to all the kinetic objects that have connections between each other.
     * */
    private class KineticRealizationData(
        val builderByObject: Map<KineticObject<*>, KineticSimulationForestBuilder>,
        val objectsByBuilder: MultiMap<KineticSimulationForestBuilder, KineticObject<*>>
    )

    /**
     * Realizes the sub-system builders for all kinetic objects.
     * */
    private fun realizeKinetic() : KineticRealizationData {
        val builderByObject = HashMap<KineticObject<*>, KineticSimulationForestBuilder>()
        val objectsByBuilder = MutableSetMapMultiMap<KineticSimulationForestBuilder, KineticObject<*>>()

        realizeComponents(SimulationObjectType.Kinetic, factory = { set ->
            val builder = KineticSimulationForestBuilder()

            set.forEach {
                val obj = it.objects.kineticObject
                obj.addNodes(builder)
                builderByObject.putUnique(obj, builder)
                objectsByBuilder[builder].addUnique(obj)
            }
        })

        return KineticRealizationData(builderByObject, objectsByBuilder)
    }

    /**
     * Realizes a subset of simulation objects that share the same simulation type.
     * This is a group of objects that:
     *  1. Are in cells that are physically connected
     *  2. Participate in the same simulation type (Electrical, Thermal, Mechanical)
     *
     * A separate solver/simulator may be created using this subset.
     *
     * This algorithm first creates a set with all cells that have the specified simulation type.
     * Then, it does a search through the cells, only taking into account connected nodes that have that simulation type.
     * When a cell is discovered, it is removed from the pending set.
     * At the end of the search, a connected component is realized.
     * The search is repeated until the pending set is exhausted.
     *
     * @param type The simulation type to search for.
     * @param factory A factory method to generate the subset from the discovered cells.
     * */
    private fun <TComponent> realizeComponents(type: SimulationObjectType, factory: ((Set<Cell>) -> TComponent), predicate: ((SimulationObject<*>, SimulationObject<*>) -> Boolean)? = null, ) {
        val pending = cells.asSequence().filter { it.hasObject(type) }.toHashSet()

        val queue = ArrayDeque<Cell>()
        val visited = HashSet<Cell>(pending.size)

        val results = ArrayList<TComponent>()

        while (pending.isNotEmpty()) {
            check(queue.isEmpty())

            visited.clear()

            queue.add(pending.first())

            while (queue.isNotEmpty()) {
                val cell = queue.removeFirst()

                if (!visited.add(cell)) {
                    continue
                }

                pending.remove(cell)

                for (connectedCell in cell.connections) {
                    if (connectedCell.hasObject(type)) {
                        if (predicate != null && !predicate(cell.objects[type], connectedCell.objects[type])) {
                            continue
                        }

                        queue.add(connectedCell)
                    }
                }
            }

            results.add(factory(visited))
        }
    }

    /**
     * Removes the graph from tracking and invalidates the saved data.
     * The simulation must be stopped before calling this.
     * */
    fun destroy() {
        validateMutationAccess()
        clearElectricalSimulation()
        clearRotationSimulation()
        manager.removeGraph(this)
        manager.setDirty()
    }

    @OptIn(ExperimentalContracts::class)
    inline fun<T> runSuspended(action: (() -> T)) : T {
        contract {
            callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        }

        executionGraph.suspend()

        try {
            return action()
        }
        finally {
            executionGraph.resume()
        }
    }

    // TODO revamp the schema

    fun toNbt(): CompoundTag {
        val circuitCompound = CompoundTag()

        require(executionGraph.isNotRunning)

        circuitCompound.putUUID(NBT_ID, id)

        val cellListTag = ListTag()

        cells.forEach { cell ->
            val cellTag = CompoundTag()
            val connectionsTag = ListTag()

            cell.connections.forEach { conn ->
                val connectionCompound = CompoundTag()
                connectionCompound.putLocator(NBT_POSITION, conn.locator)
                connectionsTag.add(connectionCompound)
            }

            cellTag.putLocator(NBT_POSITION, cell.locator)
            cellTag.putString(NBT_ID, cell.id.toString())
            cellTag.put(NBT_CONNECTIONS, connectionsTag)

            try {
                cellTag.put(NBT_CELL_DATA, cell.createTag())
            } catch (t: Throwable) {
                LOG.fatal("CELL SAVE ERROR: $t")
            }

            cellListTag.add(cellTag)
        }

        circuitCompound.put(NBT_CELLS, cellListTag)

        return circuitCompound
    }

    fun serverStop() {
        executionGraph.suspend()
    }

    companion object {
        const val SUBSTEPS = 5
        const val DT = 1.0 / 100.0

        private const val NBT_CELL_DATA = "data"
        private const val NBT_ID = "id"
        private const val NBT_CELLS = "cells"
        private const val NBT_POSITION = "pos"
        private const val NBT_CONNECTIONS = "connections"

        fun fromNbt(graphCompound: CompoundTag, manager: CellGraphManager, level: ServerLevel): CellGraph {
            val graphId = graphCompound.getUUID(NBT_ID)
            val result = CellGraph(graphId, manager, level)

            result.isLoadingData = true

            val cellListTag = graphCompound.get(NBT_CELLS) as ListTag?
                ?: // No cells are available
                return result

            // Used to assign the connections after all cells have been loaded:
            val cellConnections = HashMap<Cell, ArrayList<Locator>>()

            // Used to load cell custom data:
            val cellData = HashMap<Cell, CompoundTag>()

            cellListTag.forEach { cellNbt ->
                val cellCompound = cellNbt as CompoundTag
                val pos = cellCompound.getLocator(NBT_POSITION)
                val cellId = ResourceLocation.tryParse(cellCompound.getString(NBT_ID))!!

                val connectionPositions = ArrayList<Locator>()
                val connectionsTag = cellCompound.get(NBT_CONNECTIONS) as ListTag

                connectionsTag.forEach {
                    val connectionCompound = it as CompoundTag
                    val connectionPos = connectionCompound.getLocator(NBT_POSITION)
                    connectionPositions.add(connectionPos)
                }

                val cell = CellRegistry
                    .getCellProvider(cellId)
                    .create(pos, CellEnvironment.evaluate(level, pos))

                cellConnections[cell] = connectionPositions

                result.addCell(cell)

                cellData[cell] = cellCompound.getCompound(NBT_CELL_DATA)
            }

            // Now assign all connections and the graph to the cells:
            cellConnections.forEach { (cell, connectionPositions) ->
                val connections = ArrayList<Cell>(connectionPositions.size)

                connectionPositions.forEach { connections.add(result.getCellByLocator(it)) }

                // Now set graph and connection
                cell.graph = result
                cell.connections = connections
                cell.onUpdate(connectionsChanged = true, graphChanged = true)

                try {
                    cell.loadTag(cellData[cell]!!)
                } catch (t: Throwable) {
                    LOG.error(DEBUGGER_BREAK("Cell loading exception: $t"))
                }
            }

            result.cells.forEach { it.onLoadedFromDisk() }
            result.cells.forEach { it.onCreated() }

            result.isLoadingData = false

            return result
        }
    }
}

/**
 * The Cell Graph Manager tracks the cell graphs for a single dimension.
 * This is a **server-only** construct. Simulations never have to occur on the client.
 * */
class CellGraphManager(val level: ServerLevel) : SavedData() {
    private val graphs = HashMap<UUID, CellGraph>()

    @OnServerThread
    fun forEachGraph(consumer: (CellGraph) -> Unit) {
        requireIsOnServerThread {
            "CellGraphManager#forEachGraph"
        }

        graphs.values.forEach {
            consumer(it)
        }
    }

    private val statisticsWatch = Stopwatch()

    fun sampleTickRate(): Double {
        val elapsedSeconds = !statisticsWatch.sample()

        return graphs.values.sumOf { it.executionGraph.sampleElapsedUpdates() } / elapsedSeconds
    }

    val totalSpentTime get() = graphs.values.sumOf { !it.executionGraph.lastTickTime }

    /**
     * Checks whether this manager is tracking the specified graph.
     * @return True, if the graph is being tracked by this manager. Otherwise, false.
     * */
    fun contains(id: UUID): Boolean {
        return graphs.containsKey(id)
    }

    /**
     * Begins tracking a graph, and invalidates the saved data.
     * */
    fun addGraph(graph: CellGraph) {
        graphs[graph.id] = graph
        setDirty()
    }

    /**
     * Creates a fresh graph, starts tracking it, and invalidates the saved data.
     * */
    fun createGraph(): CellGraph {
        val graph = CellGraph(UUID.randomUUID(), this, level)
        addGraph(graph)
        setDirty()
        return graph
    }

    /**
     * Removes a graph, and invalidates the saved data.
     * **This does not call any _destroy_ methods on the graph!**
     * */
    fun removeGraph(graph: CellGraph) {
        graphs.remove(graph.id)
        LOG.info("Removed graph ${graph.id}!")
        setDirty()
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val graphListTag = ListTag()

        graphs.values.forEach { graph ->
            graph.runSuspended {
                graphListTag.add(graph.toNbt())
            }
        }

        tag.put("Graphs", graphListTag)
        LOG.info("Saved ${graphs.size} graphs to disk.")
        return tag
    }

    /**
     * Gets the graph with the specified ID, or throws an exception.
     * */
    fun getGraph(id: UUID) = graphs[id]
        ?: error(DEBUGGER_BREAK("Graph with id $id not found"))

    fun serverStop() {
        graphs.values.forEach { it.serverStop() }
    }

    companion object {
        private fun load(tag: CompoundTag, level: ServerLevel): CellGraphManager {
            val manager = CellGraphManager(level)

            val graphListTag = tag.get("Graphs") as ListTag?

            if (graphListTag == null) {
                LOG.info("No nodes to be loaded!")
                return manager
            }

            graphListTag.forEach { circuitNbt ->
                val graphCompound = circuitNbt as CompoundTag
                val graph = CellGraph.fromNbt(graphCompound, manager, level)

                if (graph.isEmpty()) {
                    LOG.error("Loaded circuit with no cells!")
                    return@forEach
                }

                manager.addGraph(graph)

                LOG.info("Loaded ${graph.size} cells for ${graph.id}!")
            }

            manager.graphs.values.forEach {
                it.forEach { cell ->
                    cell.onWorldLoadedPreSolver()
                }
            }

            manager.graphs.values.forEach { it.buildSolver() }

            manager.graphs.values.forEach {
                it.forEach { cell ->
                    cell.onWorldLoadedPostSolver()
                }
            }

            manager.graphs.values.forEach {
                it.forEach { cell ->
                    cell.onWorldLoadedPreSim()
                }
            }

            manager.graphs.values.forEach {
                it.executionGraph.resume()
            }

            manager.graphs.values.forEach {
                it.forEach { cell ->
                    cell.onWorldLoadedPostSim()
                }
            }

            return manager
        }

        /**
         * Gets or creates a graph manager for the specified level.
         * */
        fun getFor(level: ServerLevel): CellGraphManager = level.dataStorage.computeIfAbsent(
            { load(it, level) },
            { CellGraphManager(level) },
            "CellManager"
        )
    }
}
