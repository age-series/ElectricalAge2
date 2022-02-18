package org.eln2.mc.extensions

import net.minecraft.network.FriendlyByteBuf
import org.eln2.mc.Eln2
import org.eln2.mc.extensions.ByteBufferExtensions.writeDataEntry
import org.eln2.mc.utility.DataBuilder
import org.eln2.mc.utility.McColor

object ByteBufferExtensions {
    fun FriendlyByteBuf.writeString(string: String) : FriendlyByteBuf{
        return this.writeByteArray(string.toByteArray(charset = Charsets.UTF_8))
    }

    fun FriendlyByteBuf.readString() : String{
        return this.readByteArray().toString(charset = Charsets.UTF_8)
    }

    fun FriendlyByteBuf.readColor() : McColor{
        return McColor.deserialize(this)
    }

    fun FriendlyByteBuf.writeColor(color : McColor) : FriendlyByteBuf{
        return color.serialize(this)
    }

    fun FriendlyByteBuf.writeDataEntry(entry : DataBuilder.Entry) : FriendlyByteBuf{
        return entry.serialize(this)
    }

    fun FriendlyByteBuf.readDataEntry() : DataBuilder.Entry{
        return DataBuilder.Entry(this)
    }

    fun FriendlyByteBuf.writeDataLabelBuilder(builder: DataBuilder) : FriendlyByteBuf{
        this.writeInt(builder.entries.count())

        if(builder.entries.isEmpty()){
            return this
        }

        builder.entries.forEach{entry ->
            writeDataEntry(entry)
        }

        return this
    }

    fun FriendlyByteBuf.readDataLabelBuilder() : DataBuilder {
        val count = this.readInt()

        Eln2.LOGGER.info("Builder size: $count")

        val builder = DataBuilder()

        if(count == 0){
            return builder
        }

        for (i in 0 until count){
            builder.entry(this.readDataEntry())
        }

        return builder
    }


}
