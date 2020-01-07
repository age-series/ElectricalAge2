# development

Some tidbits about development.

## Language(s)

Eln2 is to be written in entirely **Kotlin**, except for where it is not possible. There are a few pieces of code that are still Java, but these will be going away relatively soon.

Tutorials on Kotlin can be found online if you are familiar with Java. It is not too much different, but if you don't know what a functional programming language is, I'd suggest [reading at least part of this book](http://learnyouahaskell.com/learnyouahaskell.pdf) ([amazon](https://www.amazon.com/Learn-You-Haskell-Great-Good-dp-1593272839/dp/1593272839/)) before starting development.

Things like this:

```kotlin
val fruits = ["bananas", "oranges", "apples"]
val fruits2 = fruits.filter{"e" in it}.map{it.toUpperCase()}
fruits2.forEach{println(it)}
```

will possibly make a lot more sense after reading that book.

## Project Structure

* `org.eln2`
    * `math`: all sorts of mathy stuff
    * `mc`: any Minecrafty code, main mod class
        * `block`: all regular minecrafty blocks (ores, ..) that are not TE's
        * `item`: all items
        * `simplenode`: all single block TE's
        * `sixnode`: all sixnode (6 sides of a block) TE's
        * `multinode`: all multi block TE's
    * `nbt`: all NBT serializations of the sim code
    * `sim`: all sorts of simulation code (mna, electrical, thermal)
* `cam72cam.mod`: UniversalModLib (Library code)
    
**NOTE**: DO NOT PUT MINECRAFT IMPORTS IN THE SIM CODE

INSTEAD, CREATE A CLASS IN `org.eln2.nbt` TO EXTEND THE FUNCTIONALITY

All Sim Code that has to store some kind of state has the `open` modifier to allow extending the code.

## Ideas

* It may be interesting to experiment having a serialization layer on top of NBT so that we can use the same serialization
outside of Minecraft for the same code. If that becomes feasible, the NBT folder should go away and serialization should
be handled transparently for sim code.

## Developer Lock

Developer Lock is designed to prevent the general public from running dev jars until the public alpha release.

Buggy games are not fun, and we don't want to hear that what we're working on is utter trash and corrupted your world.

If you are deemed to benefit from an authentication code for dev purposes, a developer will contact you.

## Phase 1: UniversalModLib

- [ ] configure UniversalModLib
    - [ ] fix UniiversalModLib
    - [ ] package UniversalModLib
- [ ] developer lockdown feature

### Verification:
 
- [ ] running `./gradlew build` in the main workspace will prepare libraries and compile our mod to a functional jar
- [ ] works in TravisCI
- [ ] Developer Lock enabled

## Phase 2: SingleNode

I say models and textures, but these are basic as almost all of these items will be removed/edited later.

- [ ] SingleCable: a current based uninsulated cable that connects similarly to RF pipes
    - [ ] Electrical Sim
    - [ ] Thermal Sim
    - [ ] Inventory Texture
    - [ ] Model (likely NOT a blender model, this will be OpenGL calls I think)
- [ ] SingleSource: a simple voltage source block
    - [ ] Electrical Sim
    - [ ] Inventory Texture
    - [ ] Model
- [ ] SingleGround: a simple ground pin
    - [ ] Electrical Sim
    - [ ] Inventory Texture
    - [ ] Model
- [ ] CreativeHeater: a simple thermal cable heater
    - [ ] Thermal Sim (creates heat at a predefined rate of energy)
    - [ ] Inventory Texture
    - [ ] Model
- [ ] Passive Cooler: a simple heatsink with no fan
    - [ ] Thermal Sim
    - [ ] Inventory Texture
    - [ ] Model (use existing 1.7.10?)
- [ ] Active Cooler: an simple heatsink with a fan (12v, 24W)
    - [ ] Electrical Sim
    - [ ] Thermal Sim
    - [ ] Inventory Texture
    - [ ] Model (use existing 1.7.10?)
- [ ] SingleSwitch: a current based uninsulated throw switch
    - [ ] Electrical Sim
    - [ ] Thermal Sim (similar to a cable, except when open we may reduce the thermal transfer)
    - [ ] Inventory Texture
    - [ ] Model
- [ ] SingleCapacitor: a simple capacitor
    - [ ] Electrical Sim
    - [ ] Thermal Sim?
    - [ ] Inventory Texture
    - [ ] Model
- [ ] SingleInductor: a simple inductor
    - [ ] Electrical Sim
    - [ ] Thermal Sim?
    - [ ] Magnetism Sim?
    - [ ] Inventory Texture
    - [ ] Model
- [ ] Basic 12v Battery
    - [ ] Electrical Sim
    - [ ] Thermal Sim
    - [ ] Inventory Texture
    - [ ] Model (use existing 1.7.10?)
    
### Validation

- [ ] Verify Electrical Sim works and that reasonable currents are being moved. Get Matrix Size to the user
- [ ] Verify Thermal Sim works and that heat transfers through items properly
- [ ] Verify that WAILA-style integrations work with other mods.
- [ ] Verify that Capacitors work as they should IRL
- [ ] Verify that Inductors work as they should IRL
- [ ] Verify that Batteries work as they should IRL

Note: Beyond this point, checkboxes have not been determined. They will be added later as we see fit.

## Phase 3: MultiNode

Add a block such as the autominer.

## Phase 4: SixNode

Add cables, switches, resistors, etc.

Remove Single* or at least delist it (shadow registry) from normal builds.

## Phase 5: Some basic content

12v, 200v, 480v? 16kV, 125kV power transmission. Pole update material.

Basic lamp sockets and lamp supplies?

API v1.0 has to be completed and verified

## Phase 6: Alpha Release and debugging

***Remove Developer Lockdown***

Release to public and initial rounds of bugfixing

Stable API must be developed before this point.

<!-- This phase missing is an inside joke -->

## Phase 8: Shaft Networks and Machines

Build larger amounts of energy, generators, turbines, etc.

Methods for making steam and oil processing will happen here.

## Phase 9: Survival Crafting and Balance

Everything else.

