# Cells

### Abstract

Cells are discrete simulation units. They exist inside 
simulations. A cell may form connections with other cells. They are 
owned by a Cell Container.

### Cell Containers

Cell containers are a queryable set of cells, that exist 
within a one block space. They are implemented by block entities 
(machines, and multiparts). They are used by the connection logic to 
establish connections between different placement schemes.

### Connection Logic

A simple connection algorithm is used. No optimization requirements were observed.

#### Placement

Placement is the process of inserting a cell into a graph,
 and setting up the simulator. The following placement cases were 
considered:

1. The cell has no connection candidates. A new graph is created.

2. The cell has a single connection candidate. It may join the existing circuit.

3. The cell has multiple connections candidates, which are part of the same graph. It may join the existing circuit.

4. The cell has multiple connection candidates, which do not 
   share a common graph. A new circuit is created with all connected cells.

Cases 1, 2, 3 result in constant-time *connection* logic.

#### Removal

Removal is the process of removing a cell from the graph, 
and refitting the connected cells as required. The topology of the 
entire graph might change, if the removed cell is a cut vertex. The 
following cases were considered:

1. The graph only contains the removed cell. The entire graph can be destroyed.

2. The removed cell has one connection. The cell is simply removed from the graph.

3. The removed cell has multiple connections, and it is not a cut vertex. The cell is simply removed from the graph.

4. The removed cell has multiple connections, and it is a cut vertex. The graph is split into multiple graphs.

Cases 1, 2, 3 *theoretically* result in constant-time *connection* logic. The current implementation still does a search for case 3, 
because it was established that this part of the system does not cause 
huge performance issues.

The connection logic is not tied to the world, or block 
entities. Queries are handled by the implementors of the container 
interface, which may access the world.

### Simulations

Currently, only electrical simulations are implemented.

#### Issues

A massive performance penalty is incurred every time a 
cell (component) is added/removed to/from a large circuit. This is due 
to `libage` re-building the matrix on 
every change. This cost is a huge issue. It fractures gameplay and can 
open up servers to exploitation.

# Parts - multipart block entities

### Abstract

The *part* emulates a *block entity* with a 
very simplified and concise API. You never need to interact with game 
code. It also handles server-client synchronization using a simple API.

Up to 6 parts may exist in one block volume. They are 
placed per-face. This document addresses some design considerations and 
issues.

### Multiparts

Multiparts are container block entities, with no special 
logic. They act as a container for parts. They may have up to 6 parts 
(one part per inner face).

Block placement logic is emulated with multiparts.

### Parts

Parts are similar to block entitities, in what they can do. A simple and concise API is offered:

- Synchronization and saving
  
  - Is done using NBT
  
  - No networking code is required

- Rendering
  
  - Is done using a modern API (With Flywheel)
  
  - Can be accomplished per-frame (for animations)
  
  - Is very fast
  
  - Is not roundabout (no black boxes anywhere)

### Cell Parts

Cell Parts are special Part implementations. They offer an API which can be used to create electrical simulations:

- Graph Access
  
  - Automatic graph acccess serialization

- Connection system
  
  - Using planar, inner, wrapped connections

- Automatic Cell lifetime management

### Connections

#### Planar

Planar connections happen between cell containers that are
 placed in the same plane, and are adjacent. A part may establish this 
connection with another part (from another multipart), or another 
implementor of the cell container (a machine)

#### Inner

Inner connections happen between parts that exist inside 
the same multipart. It can be thought of as a connection going inside 
the corner of the block. Inner connections cannot form between parts 
that are on parallel faces.

#### Wrapped

Wrapped connections happen between cell containers that 
are adjacent to the same block, and on perpendicular faces of the block.
 It can be thought of as a connection wrapped around the corner of a 
substrate block.

Parts are notified of any connections by the multipart. 
The multipart will also provide the connection mode. It is very useful 
for rendering, because some parts have a model variant for each mode. 
Example: wires. A wire model must be made for every mode and every 
connection set.

### Placement and shape

Parts have 2 important placement characteristics:

- Face
  
  - The face of the multipart where the part was placed.

- Facing
  
  - The rotation of the part.
  
  - May be used to orient a part towards a desired direction
  
  - May be changed using wrenches

Parts may define a custom shape, or use an automatically 
generated one (from width, height, depth). The shape is used for 
collisions and block highlighting.

All part APIs use localization by the above 
characteristics. This is handled internally, and APIs are exposed to 
interact with the part:

- Relative rotation API
  
  - Can be used to get the relative rotation, from a global direction

- Volume API
  
  - Model, Grid and World bounding boxes
  
  - Is used for part picking

### Rendering

Parts are rendered using a Part Renderer. They are created
 per part instance. The renderer must implement the following contract:

- Rendering Setup
  
  - All models and assets are initialized here.

- Per-frame work
  
  - The model instance may be modified here

- Light updates
  
  - All instances are re-lit here (when the light level changes)

- Cleanup
  
  - All instance resources are released here

The model instance is created from a model. Models may be 
loaded from resources (JSON models, OBJ, ...). The instance encapsulates
 all the data required to render the model. This includes the transform.

The transform and textures may be changed per frame.

Custom shaders are supported.

Rendering is dispatched in parallel. This potentially 
improves performance considerably (though no performance issues were 
observed with the current models, which are very simple). This can, of 
course, be useful, if any complex logic is performed per frame.



# Restructuring and cleanup

- Removed unused classes

- Fixed code formatting issues

- Better package structure
  
  - Foundational classes go to a foundation package
  
  - Content and registries in the base package
  
  - Moved some classes to new packages in anticipation of future work (e.g. `integration`)

- Fixed some naming conventions

- Renamed some classes to better names



## What are we fixing?

Our old structure was inadequate for our usage. We ended 
up mixing content with base classes, and, in some cases, undesirable 
workarounds were needed (e.g. `/blocks/block/`)

## What can we expect from the new structure?

The new structure was designed to be cleaner, and this will be necessary when we start adding content.

It also makes more sense to separate foundational classes from content classes.



# Waila API

### What are we fixing?

The old API just used a String-String map. It was cumbersome to use, and not very extensible.

### The new implementation

The new implementation consists of a generic contract, 
which is implemented by any class that wants to export data to the 
tooltip (e.g. parts, cells)

A `TooltipBuilder` is provided. Currently, it supports recording pairs of:

- Text-Text entries
  
  - They are displayed exactly like they are submitted.

- Translation-Text entries
  
  - The key is translated using the mod's language file, but the value is not affected.

- Translation-Translation entries
  
  - The key and value are translated using the mod's language file.

- Wrappers for units (with translation)



# Simulation Objects - Single Cell, Multiple Simulations

### Abstract

A Cell is the physical unit of simulation. Such a unit may
 participate in multiple simulations of physical phenomena. For example,
 a wire cell participates in the electrical simulation, but also 
participates in the thermal simulation.

We preset our approach, Simulation Objects. They are 
discrete simulation units that are specialized for one type of 
simulation (Electrical, Thermal or Mechanical).

### Simulation Objects (S.O.)

- Are owned and managed by Cells

- There is only one object per simulation type per cell

- They handle connections using the underlying simulation 
  constructs (e.g. components and pins, in electrical simulations), 
  starting from the Cell-Cell connections.

- They may only connect to *SOs* that are part of the same simulation type.

- Are created once, and live throughout the lifetime of the cell.

#### Connectivity and Simulations

A Cell Graph will perform multiple passes over the cells, 
when the solver is being built. Here, Object-Object connections are 
realized, and the simulations are set up. This may result in many *Simulation Sub-Sets*, which are individual connected components that become a simulation.

The criteria for individuality is a difference in the *Object Set (OS)* of Cells. The OS is the collection of simulation objects a Cell has. Splitting occurs when connected cells *do not share the same SOs*,
 and will form local clusters, or "groups". These will become discrete 
simulations (e.g. for electrical simulations, these groups will result 
in different circuits).

The Cell Graph will track all of these groups, and will 
delegate updates to them. Due to this, two levels of parallelism are 
possible:

- Cell Graph Parallelism
  
  - Parallelism that occurs at the graph level, I.E. parallel updates for the Cell Graphs (which do not share any relationships)

- Simulation Group Parallelism
  
  - Parallelism that occurs at the simulation group level, I.E. parallel
     updates for the individual simulations contained inside a Cell Graph. 
    It may be assumed that individual groups also do not share any 
    relationships.

A lot is possible here. The level of parallelism may be 
increased by leveraging the two degrees of freedom, or just the last 
one.

It is also possible to schedule per-cluster updates, since
 some simulations may require less updates than others, presenting an 
opportunity for optimization. Example: thermal simulations, **reasonably-stable electrical simulations**, ... .

### Implementation Details

The following API is proposed and tested:

#### Cell API

The cell has a special method that creates the OS. All simulation methods were removed, as they are no longer related to Cells.

#### Object API

Objects undergo 2 main lifecycle changes, based on user interaction:

- Simulation Changes
  
  - Occur when other objects are added to the simulation

- Destruction
  
  - Occurs when the user destroys the device associated with the object (Cell)

Currently, only Electrical Objects are implemented. The following contract must be implemented:

- Component Acquisition (Offering)
  
  - This is used by electrical objects (remote or otherwise) to get a connection candidate. One *Component-Pin* pair may be described per connection (neighbor). Currently, the underlying *LibAGE* component is provided, and a pin to connect to. This is all that is needed to create a connection.

- Circuit Acquisition
  
  - This is where the component is registered with *LibAGE*. The target *Circuit* is available at this step. One may simply add the components encapsulated in the Electrical Object to the circuit.

- Circuit Renewal
  
  - This is where the components must be prepared for a new circuit. 
    Currently, a new instance of the internal components is created here. 
    After this step, Circuit Acquisition happens.

The test implementation is a resistor object:

- Component Acquisition
  
  - The LibAGE resistor is returned, and the pin is just the index of 
    the connection. This works because the resistor is a non-polar component
     (unlike diodes). **Connection Directions are provided**, 
    though, for future polar component implementations. It is also assumed 
    that the Cell Provider only allows up to 2 connections to occur, which 
    is the case in the test implementation.

- Circuit Acquisition
  
  - The LibAGE resistor is simply added to the circuit here.

- Circuit Renewal
  
  - The LibAGE resistor is re-created here.

When a solver re-build is requested (the physical network changed), the following strategy is executed:

- The Object-Object (OO) connections are cleared, and, in the case of Electrical Objects, *Circuit Renewal* happens.

- The OO connections are found. Internally, this just 
  records the connection sets for all objects. This is done using the 
  Cell-Cell connections, which were realized externally, by the *Cell Connection Manager*.
   The criteria for a connection is that two candidate cells share at 
  least a common simulation object. This is done for every type of 
  Simulation Object, and the concrete type is passed down. For example, 
  Electrical Objects are notified of other Electrical Objects, and the 
  origin direction, and they will record these connections.

- The solver-specific unit operations are performed (*Build*). For example, the Electrical Object will create Component-Component connections here, using *Component Acquisition*

- The simulation clusters are realized, by traversing all 
  connected components. Once this is done, objects finalize simulation 
  set-up. For example, the Electrical Object will perform Circuit 
  Acquisition here.
  
  - The individual clusters are fully realized and they are recorded to an internal set.

After this is done, simulation may start.

There is an optimization opportunity here. As outlined 
above, the OO connections are completely re-formed. This may be replaced
 with an algorithm that progressively realizes connections, when 
individual Cells are added/removed. *This was not done, because it 
presents the same algorithmic and theroetical issues that Cell-Cell 
connections do. When performance becomes a problem, this can be fixed.*

This flow may be updated with more passes in the future. 
Currently, the Electrical Simulations can be realized by the flow 
described above. It was not projected that Thermal Simulations will be 
different.

#### Component Access

By implementing Simulation Objects, we have introduced 
additional levels of indirection to data access. For example, a game 
object (Part, Block Entity) will need to "jump trough hoops" to access 
simulation data:

- Cell Access.

- Object Access.

- Underlying component access (the simulation construct)

In the future, updates will be fully parallelized. This must be considered when designing the data access architecture.

Because of this, adding content at this stage is not taken
 into consideration. Parallelism will result in huge architectural 
changes, which will cause all content to be invalid. This was observed **when Objects were implemented: all of the old content was lost**.

An exception is the test resistor, which was implemented 
in order to ascertain what needs to be done in order to successfully 
implement content in the future.

#### WAILA

Cells implement the Waila Provider contract. By 
themselves, they do not add any information to the tooltip. They do, 
however, delegate this to their *SOs*. As such, the object may implement the Waila Provider contract.

**Exceptions are implicitly handled at the top level**.
 As such, exceptions may be thrown anywhere down this call chain. This 
is because it has been observed that accessing the LibAGE data (e.g. 
Current) will result in an exception if the simulation is in an unready 
state. Without implicit handling, every WAILA contract implementation 
would have to do its own error handling, which would just be the same 
everywhere.
