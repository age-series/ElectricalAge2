# development

Just some development notes:

## Project Structure

* org.eln2
    * math: all sorts of mathy stuff
    * mc: any basic Minecrafty code
    * nbt: all NBT versions of the sim code
    * sim: all sorts of simulation code (mna, electrical, thermal)
    
**NOTE**: DO NOT PUT MINECRAFT IMPORTS IN THE SIM CODE

INSTEAD, CREATE A CLASS IN `org.eln2.nbt` TO EXTEND THE FUNCTIONALITY

In the future, there may be other dirs such as:

* org.eln2.render

* org.eln2.mc
    * item
    * block
    * singleenode
    * sixnode
    * multinode

## Ideas

It may be interesting to experiment having a serialization layer on top of NBT so that we can use the same serialization
outside of Minecraft for the same code. If that becomes feasible, the NBT folder should go away and serialization should
be handled transparently for sim code.