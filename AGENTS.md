# Repository Guidelines

## Code Style

These rules are mandatory. Follow them in all code you write or modify.

1. Always use braces for if/when/while/for:

Bad:

```kotlin
if (value == 0) return
```

Good:

```kotlin
if (value == 0) {
    return
}
```

2. Never use shorthand names for variables, methods, or classes. Use full, descriptive names.

3. Space methods and if/when/while/for blocks by one line. Fields and local variables don't need spacing, except for properties implementing getter and setter bodies.

4. Group related classes/enums into a single file (e.g. `Battery.kt` holds the interfaces, data objects, `BatteryCell`, and `BatteryPart` together).

5. Use libage extension methods: `approxEq` when comparing doubles with a tolerance, `putUnique` when inserting into maps (and the key must not be present or overwritten), `addUnique` (for sets).

6. Never write comments or KDoc documentation unless asked. This includes inline comments, block comments, and KDoc blocks. If asked, follow these sub-rules:
- Keep comments simple. Use a `:` to indicate if the comment is attached to a specific line.
- Don't break sentences over multiple lines. Each sentence stays on one line, even if it exceeds the line length limit:

Bad:

```kotlin
/**
 * Round-trip efficiency factor in [0, 1],
 * applied on both charge and discharge.
 * */
```

Good:

```kotlin
/**
 * Round-trip efficiency factor in [0, 1], applied on both charge and discharge.
 * */
```

- Don't add `@param` entries when the parameter names are already self-explanatory.
- Only use usual symbols from a standard keyboard -- no emdashes, arrow symbols or emojis.
- Use multiline KDoc when commenting on methods and classes, and use references `[MyClass]` instead of writing class names verbatim.
- Describe only what the code does. Don't reference implementation plans, design rationale from the session, or compare to other classes unless the comparison is essential for correct usage.

```kotlin
/**
 * Replicates the temperature of [thermalWire] if [WireThermalProperties.replicatesInternalTemperature].
 * */
fun internalTemperatureReplicator(consumer: InternalMultiThermalBodyTemperatureConsumer)
```

7. `.editorconfig`: LF line endings, UTF-8, 4-space indent, max line length 120 for `.kt`/`.java` (80 for others).

8. `@file:Suppress("unused", "MemberVisibilityCanBePrivate", ...)` is common at file tops to suppress lint caused by reflection usage of `@SimObject`, `@Behavior`, `@Replicator` and fields used to hold registration objects (which are usually never accessed).

## Project Overview

Eln2 (Electrical Age 2) is a Minecraft Forge mod that simulates physical systems — electric circuits, thermal properties, and kinetic/mechanical power — inside the game. The mod is part of the "age-series" and targets **Minecraft 1.20.1 / Forge 47.x**, written primarily in **Kotlin** (with a small number of Java mixins). Code is MIT-licensed, content is CC0.

At its core, the mod vendors a pure-JVM physics library (`libage`) that implements an MNA (Modified Nodal Analysis) electrical circuit solver, a lumped-capacitance thermal simulator, and a rigid-body kinetic solver. The Minecraft layer bridges these solvers to in-game blocks, items, and a free-form cable system.

## Architecture & Data Flow

### Two-layer architecture

**libage** is a Minecraft-agnostic physics/math library. Key systems:

- **Electrical solver** (`sim/electrical/Simulation.kt`): sparse-MNA using EJML (`DMatrixSparseCSC` + sparse LU). Components use Norton companion models (Backward Euler for L/C). Nonlinear power devices solved via Gauss-Seidel SOR (consumers) and Broyden quasi-Newton (sources).
- **Thermal** (`sim/Thermal.kt`): lumped-capacitance, separate from MNA — coupled behaviorally (electrical power → heat) at the Cell / SimulationObject level (i.e. "user" code).
- **Kinetic** (`sim/kinetic`): Constraint-solver for rotating shafts, gears, clutches etc.
- **Data structures** (`data/`): `BiMap`, `MultiMap`, `DisjointSet` (union-find), `BVH`, `SegmentTree`, `Quantity` (dimensional units in SI base — zero-cost via Kotlin value class), `Locators` (efficient set of "locator shards", i.e. block, face, orientation, ...), `Events`.
- Other utilities (`Material.kt`, `PeriodicTable.kt` — never read or touch this file, it is raw data inlined in code, mathematics module)

**eln2/mc** bridges libage to Minecraft through the **Cell abstraction**:

1. A `Cell` (`common/cells/foundation/Cells.kt`) is a simulation participant placed at a "generalized" position (via `Locator`). It carries `@SimObject`-annotated fields (`ElectricalObject`, `ThermalObject`, `KineticObject`).
2. A `CellGraph` (`common/cells/foundation/Graph.kt`) is a per-dimension `SavedData` holding connected cells and their solvers.
3. `CellGraph.buildSolver()` groups connected `ElectricalObject`s, builds one `ElectricalCircuitForestBuilder` per group, and compiles `ElectricalSimulation` instances, as well as handles building the other simulation domains.
4. `SimulationExecutionSubgraph` runs the sims on a `ForkJoinPool`: **5 substeps per tick at DT=1/100s** (effective 100 Hz), parallel across disconnected sub-solvers (sub-circuits).

The `Cell` holds up to one `SimulationObject` for each specific domain (`ElectricalObject`, `ThermalObject`, `KineticObject`). It usually deals with coupling the different domains (e.g. converting resistor power to heat via tick loop) and interacting with the block entity, but it never defines its own electrical, thermal or kinetic components directly. The domain-specific `SimulationObject`s hold the raw components (e.g. `Resistor`s for `ElectricalObject`s) and deal with some connectivity aspects and can implement their own tick loops as well.

### Grid (cable) system

Player-placed hanging cables (`common/grids/`) are a free-form way to connect machines locally or transfer large power over large distances. A `GridConnectionCell` owns a `GridConnectionElectricalObject` (a `Resistor`) and a `GridConnectionThermalObject` (a `ThermalMass`). "Cables" heat from `resistor.power` and melt if they exceed the material's melting temperature. They also glow red-hot and smoke before that.

### Game objects

Game objects are what actually interacts with the Minecraft world. By itself, Minecraft deals with Blocks and Block Entities. ELN2 adds three more types of game objects:

1. `Part`s (`common/parts/foundation/Parts.kt`) are small game objects living in a `MultipartBlockEntity` (created/destroyed dynamically). Up to 6 parts can be placed inside a single block space. Their `Locator` consists of the block pos, mounting face (normal), and orientation. Wires, for example, are implemented as parts, allowing wires to "stick" to the surface they are placed on, and follow the interior corners of the block space. `CellPart`s own one cell.
2. `Spec`s (`common/specs/foundation/Specs.kt`) are entity-like objects living on a `SpecContainerPart` — multiple specs can be placed on a substrate face with non-axis-aligned position/orientation. `CellSpec`s own one cell.
3. "Big Blocks" are multiblock machines that are placed and broken with the same mechanics as normal Minecraft blocks. They use fancy 3D models. A representative block entity is used as the core, and "delegates" are placed as phantom blocks for collision and connectivity to external networks. Representatives and delegates can own one cell each.

### Tick orchestration

`ForgeEvents.onServerTick` (`common/Events.kt`) is the per-tick orchestrator: START phase dispatches simulation frames (`dispatchAllSimulations`), END phase awaits completion (`awaitAllSimulations`), flushes bulk network packets, and updates wind/fluids/other specialized behaviors (e.g. Lead Chamber).

## Code Conventions & Common Patterns

### Kotlin-first

Nearly all mod code is Kotlin (1.9.22 via KotlinForForge 4.10.0). Java is used only for mixins due to compatibility issues.

### Registry pattern

The "registries" themselves are light singletons that create the required Forge bridge and offer some helper methods:

- **Standard registries** (`BlockRegistry`, `ItemRegistry`, etc.): typed helper methods like `blockAndItem(name, supplier)`, `itemDefault(name)`.
- **Custom registries** (`PartRegistry`, `CellRegistry`, `SpecRegistry`): Hold provider types (`PartProvider`, `CellProvider`, `SpecProvider`) and come with many helper methods.

IMPORTANT: Actual registration must **always** happen in content modules, such as `Eln2Processing`. Don't call registration methods outside of them.
Parts, specs and cells come with *immediate* and *memoized* registration helpers. Immediate registration takes the factory that instances the `Part`, `Spec`, `Cell` directly, while memoized registration runs a lambda that returns a factory. The lambda instances helper objects, which then practically become singletons when the lambda returns a factory calling the constructor of the specific `Part`, `Spec` or `Cell`.

**Content modules** (`common/content/modules/Eln2*.kt`) are `object`s implementing `ContentModule`. Their constructors (triggered by `ContentManager.initialize()`) call registry helpers at field-init time: `val SOME_BLOCK = BlockRegistry.blockAndItem(...)`. `ContentRegistrationScope` enforces dependency ordering between setup phases.

### Cell / SimulationObject pattern

```kotlin
class MyCell(ci: CellCreateInfo) : Cell(ci) {
    @SimObject val electrical = MyElectricalObject(this)
    @SimObject val thermal = MyThermalObject(this)
}
```

`@SimObject` fields are auto-registered via reflection. `ElectricalObject` overrides `addComponents(builder)` (add components) and `build(map)` (join pins). `CellGraph.buildSolver()` compiles these into `ElectricalSimulation`s.

### Behavior pattern

There are two types of behaviors:

1. `CellBehavior` implementations can subscribe to the simulation scheduler and the game-thread scheduler to run common logic. The only truly-reused behaviors are the implementations of `ExplosionBehavior`, which blow up the machine once physical limits are violated (e.g. over-heating). Usage involves a simple field annotated with `@Behavior`:

```kotlin
@Behavior
val kineticBreakdown = KineticBreakdownBehavior.create(options.breakdownAngularVelocity, this) {
    kinetic.node.angularVelocity
}
```

2. `ReplicatorBehavior` implementations are created when a player enters view range of the `Cell`'s game object and destroyed dynamically. They are used exclusively to send simulation data for rendering (e.g. rotation speeds for shafts). Usage involves writing a method in the cell, taking the consumer for changes as a parameter (usually, an interface implemented by the game object, exposing some means to deliver data, e.g. the bulk packets API): 

```kotlin
@Replicator
fun internalTemperatureReplicator(consumer: InternalMultiThermalBodyTemperatureConsumer) =
    if (thermalProperties.replicatesInternalTemperature)
        InternalMultiThermalBodyTemperatureReplicatorBehavior(listOf(thermalWire.thermalBody), consumer)
    else null
```

Note that, when a `CellBehavior` field is null, or when the replicator method returns null, the behavior is ignored (it's a valid pattern).

### Subscriber pattern

Cells subscribe to `SimulationPhase.Pre`/`Post` (their dedicated simulation thread) and `ServerPhase` (the game thread) ticks via `SubscriberPool`. Subscribers run at chosen intervals (usually every tick for simulation calculations). See `SubscriberPoolTests.kt` for the contract.

### Norton companion model (libage)

Dynamic components (inductor, capacitor, power devices) are modeled as a fixed conductance + variable current source (`NortonSystem`). This keeps the MNA matrix structure constant — only RHS values change at runtime, avoiding expensive re-factorization.

### Thread model

- **Server/Game thread**: mutates the cell graph (place/remove blocks), dispatches ticks, runs BlockEntity logic interacting with the world (normal item/fluid pipes, recipes).
- **Simulation threads**: runs parallelizable portions of circuits/thermal networks/shaft networks on the thread pool.
- **Synchronization**: `suspend()` blocks the server thread until the sim pauses at a substep boundary. Readouts for WAILA use double-buffered repositories (swapped post-step) to prevent torn reads.

### Quantity / units

All physics configs and parameters use `Quantity<T>` (in `data/Quantity.kt`). It is a value class wrapping a `Double` which holds a quantity in SI base units and offers convenient conversion and display APIs. Dimensions are marker interfaces (`Mass`, `Potential`, `Resistance`, `Temperature`, ...). Client config allows unit overrides (e.g. `/eln2 units set Temperature to Rk`). When writing properties/fields/variables holding `Quantity` values or methods returning `Quantity` values, don't also suffix/prefix the name with the SI unit (e.g. `val energy: Quantity<Energy>` is enough; don't write `val energyJoules: Quantity<Energy>`).

### Networking

Single `SimpleChannel` (`eln2:main`, protocol `"1"`). Messages registered in `Networking.setup()`. Bulk state replication (temperature, rotation, ...) via `BulkData.kt` (flushed at END server tick). `send(msg, player)` for S→C, `sendToServer(msg)` for C→S.

## Development Commands

Never run commands without being asked to (exception: you can build to make sure code compiles by yourself). Always keep in mind ForgeGradle command output can be extremely large.

```bash
./gradlew build              # Full build (compile + jar + shadowJar)
./gradlew test               # JUnit 5 unit tests
./gradlew runClient          # Launch Minecraft client with the mod
./gradlew runServer          # Launch dedicated server (writes ./run/eula.txt first)
./gradlew runGameTestServer  # Run GameTest server (crashes if no gametests registered)
./gradlew runData            # Generate data → src/generated/resources/
```

**Data generation**: `runData` generates recipes, loot tables, block/item tags, block states, and item models. Output goes to `src/generated/resources/` (included as a resource sourceSet). Data sources are populated at registration time via `ContentManager` repositories (`withSelfDrop`, `withItemTagDatagen`, etc.).

## Testing

- **Framework**: JUnit 5. Run via `./gradlew test`.
- **Test location**: `src/test/kotlin/`. Pure-JVM tests (no Minecraft runtime) test libage directly.
- For simulation tests, use the `ElectricalCircuitForestBuilder` → `testBuild()` pattern from `ElectricalTests.kt`.

## Runtime Notes

- Use `./gradlew` (wrapper), never a system Gradle.
- **libage is vendored**, not a Gradle dependency. `libage_version` in `gradle.properties` tracks the upstream git commit but is informational. Edit libage sources directly in `src/main/java/org/ageseries/libage/`.

## Agent Tools

### `agent-tools/mc-src-ts.py` 

Read and search Minecraft/Forge vanilla source. Accesses the ForgeGradle build cache that contains Parchment-remapped sources jar with the full decompiled Minecraft + Forge source.

**Commands:**

read <class-name> [--lines M-N]
   Dump source or a line range of a class.
   Class names: FQN, short name (auto-resolves), or Outer.Inner.

method <class-name> <method-name> [--lines M-N]
   Extract a method body with preceding javadoc.

grep <pattern> [--max N] [--context M] [--class C] [-F]
   Search source files. -F for literal; --class C is a substring match on the jar entry path (matches any file with C in its path, e.g. `--class BlockEntity` matches `ChestBlockEntity` too).

list [<package>]
   List classes under a package prefix.

find <name>
   Find classes by substring (case-insensitive).

glob <class-name> [flags...]
   List class members (methods, fields, constructors, inner types).
   
   Category filters (default: all):
      --methods       Methods only
      --fields        Fields only
      --ctors         Constructors only
      --inner-types   Inner types only
   
   Visibility filters (default: public + protected):
      --public        Include public
      --private       Include private
      --protected     Include protected
      --package       Include package-private
   
   Other filters:
      --static        Only static members
      --no-static     Only instance members
      --filter <str>  Substring match on member name

**Class name resolution:**

- FQN (`net.minecraft.Foo`) used directly
- Short name (`Foo`) resolved against top-level classes; ambiguous names print candidates
- Inner class (`Outer.Inner`) supported in all commands

**Examples:**

```bash
# Read a class (FQN, short name, or Outer.Inner)
python agent-tools/mc-src-ts.py read net.minecraft.world.level.block.entity.BlockEntity
python agent-tools/mc-src-ts.py read BlockEntity --lines 30-50

# Extract a method body with its javadoc
python agent-tools/mc-src-ts.py method BlockEntity getBlockPos
python agent-tools/mc-src-ts.py method LevelRenderer renderLevel --lines 1-30

# Simple pattern (no alternation)
python agent-tools/mc-src-ts.py grep "lightTexture" --class LevelRenderer

# Regex alternation — bare | is the alternation operator
python agent-tools/mc-src-ts.py grep "glBindTexture|glGenTextures" --class GlStateManager

# Regex alternation — \| also works (tool normalizes the GNU grep habit)
python agent-tools/mc-src-ts.py grep "glBindTexture\|glGenTextures" --class GlStateManager

# Literal fixed-string search: -F treats the whole pattern as exact text, not regex.
python agent-tools/mc-src-ts.py grep "getBlockPos(" --class BlockEntity -F

# Find classes by name substring
python agent-tools/mc-src-ts.py find BlockEntity

# List a package
python agent-tools/mc-src-ts.py list net.minecraft.world.level.block.entity

# Glob members of a class (methods, fields, ctors, inner types)
python agent-tools/mc-src-ts.py glob BlockEntity                            # default: public+protected
python agent-tools/mc-src-ts.py glob BlockEntity --methods --filter get       # methods matching "get"
python agent-tools/mc-src-ts.py glob BlockEntity --private --fields           # private fields
python agent-tools/mc-src-ts.py glob BlockEntity --public --static --filter CODEC
python agent-tools/mc-src-ts.py glob LevelRenderer.RenderChunkInfo           # inner class
python agent-tools/mc-src-ts.py glob BlockBehaviour --methods        # show source lines
```

**When to use:**

- Investigating how a vanilla MC or Forge class works
- Checking method signatures, parameter names, fields
- Discovering the API surface of a class without reading the whole file

**How to call from agent workflows:**

```bash
bash("python agent-tools/mc-src-ts.py glob BlockEntity --methods --filter get")
bash("python agent-tools/mc-src-ts.py method BlockEntity getBlockPos")
bash("python agent-tools/mc-src-ts.py grep pattern --class Foo -F")
```

Output is line-numbered source text.