package org.eln2.mc.common.parts.foundation

enum class PartUpdateType(val id : Int) {
    Add(1),
    Remove(2);

    companion object{
        fun fromId(id : Int) : PartUpdateType {
            return when(id){
                Add.id -> Add
                Remove.id -> Remove
                else -> error("Invalid part update type id $id")
            }
        }
    }
}
