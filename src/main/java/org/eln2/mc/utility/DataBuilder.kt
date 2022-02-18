package org.eln2.mc.utility

import net.minecraft.network.FriendlyByteBuf
import org.eln2.mc.extensions.ByteBufferExtensions.readString
import org.eln2.mc.extensions.ByteBufferExtensions.writeString
import kotlin.math.abs

open class DataBuilder {
    open class Entry(open val label : String, open val value : String){
        constructor(buffer: FriendlyByteBuf) : this(buffer.readString(), buffer.readString())

        fun serialize(buffer : FriendlyByteBuf) : FriendlyByteBuf{
            buffer.writeString(label)
            buffer.writeString(value)

            return buffer
        }
    }

    val entries = ArrayList<Entry>()

    fun entry(label : String, value : String) : DataBuilder{
        entries.add(Entry(label, value))
        return this
    }

    fun entry(entry: Entry) : DataBuilder{
        entries.add(entry)
        return this
    }

    fun build() : String {
        if(entries.isEmpty()){
            return ""
        }

        val sb = StringBuilder()

        entries.forEach{entry ->
            sb.append(entry.label)
            sb.append(": ")
            sb.append(entry.value)
        }

        return sb.toString()
    }
}

